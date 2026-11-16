import atexit
import ctypes
import json
import os
import shutil
import signal
import subprocess
import sys
import tempfile
import time
from datetime import datetime

import psutil

REMOTE_DIR = "/sdcard/pcpanel"
REMOTE_TMP = "/sdcard/pcpanel/data.json.tmp"
REMOTE_FILE = "/sdcard/pcpanel/data.json"
INTERVAL_SEC = 1.0

# Windows'ta arka planda çalışırken her alt işlemde cmd penceresi açılmasını önler
CREATE_NO_WINDOW = 0x08000000 if sys.platform == "win32" else 0


def find_executable(name, extra_candidates):
    """PATH veya verilen aday yollardan çalıştırılabilir dosyayı bulur."""
    in_path = shutil.which(name)
    if in_path:
        return in_path
    for path in extra_candidates:
        if path and os.path.isfile(path):
            return path
    return name


ADB_BIN = find_executable(
    "adb",
    [
        r"C:\platform-tools\adb.exe",
        os.path.join(
            os.environ.get("LOCALAPPDATA", ""),
            "Android",
            "Sdk",
            "platform-tools",
            "adb.exe",
        ),
        r"C:\adb\adb.exe",
    ],
)

NVIDIA_SMI_BIN = find_executable(
    "nvidia-smi",
    [
        r"C:\Windows\System32\nvidia-smi.exe",
        r"C:\Program Files\NVIDIA Corporation\NVSMI\nvidia-smi.exe",
    ],
)

_last_net_io = None
_last_net_time = None
_stop_requested = False
_off_sent = False
_console_handler_ref = None


def run_adb(args, timeout=10):
    """ADB komutunu pencere açmadan çalıştırır ve (başarılı_mı, çıktı) döner."""
    try:
        proc = subprocess.run(
            [ADB_BIN] + args,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=timeout,
            creationflags=CREATE_NO_WINDOW,
        )
        return proc.returncode == 0, proc.stdout.strip()
    except (subprocess.SubprocessError, OSError):
        return False, ""


def is_device_connected():
    """adb devices çıktısında yetkili ve bağlı bir cihaz olup olmadığını kontrol eder."""
    ok, out = run_adb(["devices"], timeout=10)
    if not ok or not out:
        return False
    for line in out.splitlines()[1:]:
        parts = line.strip().split()
        if len(parts) >= 2 and parts[1] == "device":
            return True
    return False


def ensure_remote_dir():
    """Tablette /sdcard/pcpanel klasörünün var olduğundan emin olur."""
    ok, _ = run_adb(["shell", "mkdir", "-p", REMOTE_DIR], timeout=10)
    return ok


def format_speed(bytes_per_sec):
    """Bayt/saniye hızını okunabilir KB/s veya MB/s metnine çevirir."""
    if bytes_per_sec >= 1024 * 1024:
        return f"{bytes_per_sec / (1024 * 1024):.1f} MB/s"
    return f"{bytes_per_sec / 1024:.0f} KB/s"


def format_uptime():
    """Sistemin açık kalma süresini okunabilir Türkçe metin olarak döner."""
    boot = psutil.boot_time()
    seconds = max(0, int(time.time() - boot))
    days, rem = divmod(seconds, 86400)
    hours, rem = divmod(rem, 3600)
    minutes, _ = divmod(rem, 60)
    if days > 0:
        return f"{days}g {hours}sa {minutes}dk"
    if hours > 0:
        return f"{hours}sa {minutes}dk"
    return f"{minutes}dk"


def get_gpu_metrics():
    """NVIDIA GPU varsa kullanım, sıcaklık ve VRAM bilgisini okur."""
    try:
        proc = subprocess.run(
            [
                NVIDIA_SMI_BIN,
                "--query-gpu=name,temperature.gpu,utilization.gpu,memory.used,memory.total",
                "--format=csv,noheader,nounits",
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=2,
            creationflags=CREATE_NO_WINDOW,
        )
        if proc.returncode != 0 or not proc.stdout.strip():
            return {}
        parts = [p.strip() for p in proc.stdout.strip().splitlines()[0].split(",")]
        if len(parts) < 5:
            return {}

        name = parts[0].replace("NVIDIA GeForce ", "").strip()
        temp = int(round(float(parts[1])))
        util = max(0, min(100, int(round(float(parts[2])))))
        vram_used_gb = float(parts[3]) / 1024.0
        vram_total_gb = float(parts[4]) / 1024.0
        return {
            "gpu": util,
            "gpu_temp": temp,
            "gpu_name": name,
            "vram_info": f"{vram_used_gb:.1f}/{vram_total_gb:.0f} GB",
        }
    except (subprocess.SubprocessError, OSError, ValueError):
        return {}


def collect_metrics():
    """CPU, GPU, RAM, disk, ağ hızı, çalışma süresi ve saat bilgisini toplar."""
    global _last_net_io, _last_net_time

    now = datetime.now()
    cpu = int(round(psutil.cpu_percent(interval=None)))
    freq = psutil.cpu_freq()
    cpu_freq = f"{freq.current / 1000.0:.2f} GHz" if freq and freq.current else "-- GHz"
    cpu_cores = psutil.cpu_count(logical=False) or psutil.cpu_count() or 0

    vm = psutil.virtual_memory()
    ram = int(round(vm.percent))
    ram_used_gb = vm.used / (1024.0 ** 3)
    ram_total_gb = vm.total / (1024.0 ** 3)

    disk_root = os.path.abspath(os.sep)
    du = psutil.disk_usage(disk_root)
    disk = int(round(du.percent))
    disk_free_gb = int(round(du.free / (1024.0 ** 3)))

    # Anlık ağ indirme ve yükleme hızı
    net_now = psutil.net_io_counters()
    t_now = time.monotonic()
    down_bps = 0.0
    up_bps = 0.0
    if _last_net_io is not None and _last_net_time is not None:
        dt = max(0.1, t_now - _last_net_time)
        down_bps = max(0.0, (net_now.bytes_recv - _last_net_io.bytes_recv) / dt)
        up_bps = max(0.0, (net_now.bytes_sent - _last_net_io.bytes_sent) / dt)
    _last_net_io = net_now
    _last_net_time = t_now

    data = {
        "cpu": max(0, min(100, cpu)),
        "cpu_freq": cpu_freq,
        "cpu_cores": cpu_cores,
        "ram": max(0, min(100, ram)),
        "ram_info": f"{ram_used_gb:.1f} / {ram_total_gb:.0f} GB",
        "disk": max(0, min(100, disk)),
        "disk_info": f"{disk_free_gb} GB Boş",
        "net_down": format_speed(down_bps),
        "net_up": format_speed(up_bps),
        "uptime": format_uptime(),
        "time": now.strftime("%H:%M:%S"),
        "ts": int(time.time()),
    }
    data.update(get_gpu_metrics())
    return data


def send_off_signal():
    """Script çıkarken tablete {"state":"off"} göndererek anında uykuya geçmesini sağlar."""
    global _off_sent, _stop_requested
    _stop_requested = True
    if _off_sent:
        return
    _off_sent = True

    try:
        off_tmp = os.path.join(tempfile.gettempdir(), "pcpanel_off.json.tmp")
        with open(off_tmp, "w", encoding="utf-8") as f:
            json.dump({"state": "off", "ts": int(time.time())}, f)
        pushed, _ = run_adb(["push", off_tmp, REMOTE_TMP], timeout=4)
        if pushed:
            run_adb(["shell", "mv", REMOTE_TMP, REMOTE_FILE], timeout=4)
    except Exception:
        pass


def _handle_signal(signum, frame):
    send_off_signal()
    sys.exit(0)


def _setup_exit_handlers():
    """Windows konsol kapanma, oturum kapatma, SIGTERM ve atexit olaylarını yakalar."""
    global _console_handler_ref
    atexit.register(send_off_signal)
    signal.signal(signal.SIGINT, _handle_signal)
    signal.signal(signal.SIGTERM, _handle_signal)

    if sys.platform == "win32":
        try:
            handler_type = ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_uint)

            def _win_ctrl_handler(ctrl_type):
                # CTRL_C(0), CTRL_BREAK(1), CTRL_CLOSE(2), CTRL_LOGOFF(5), CTRL_SHUTDOWN(6)
                if ctrl_type in (0, 1, 2, 5, 6):
                    send_off_signal()
                return False

            _console_handler_ref = handler_type(_win_ctrl_handler)
            ctypes.windll.kernel32.SetConsoleCtrlHandler(_console_handler_ref, True)
        except Exception:
            pass


def main():
    _setup_exit_handlers()
    # İlk CPU ve ağ ölçümü için ısınma çağrısı
    psutil.cpu_percent(interval=None)
    local_tmp = os.path.join(tempfile.gettempdir(), "pcpanel_data.json.tmp")
    dir_ready = False

    while not _stop_requested:
        loop_start = time.monotonic()

        try:
            if not is_device_connected():
                dir_ready = False
                time.sleep(2.0)
                continue

            if not dir_ready:
                dir_ready = ensure_remote_dir()
                if not dir_ready:
                    time.sleep(2.0)
                    continue

            payload = collect_metrics()
            with open(local_tmp, "w", encoding="utf-8") as f:
                json.dump(payload, f, ensure_ascii=False)

            # Önce geçici dosyaya gönder, sonra atomik olarak asıl dosyanın üzerine taşı
            pushed, _ = run_adb(["push", local_tmp, REMOTE_TMP], timeout=10)
            if pushed:
                moved, _ = run_adb(["shell", "mv", REMOTE_TMP, REMOTE_FILE], timeout=10)
                if not moved:
                    dir_ready = False
            else:
                dir_ready = False
        except Exception:
            dir_ready = False

        elapsed = time.monotonic() - loop_start
        sleep_time = max(0.1, INTERVAL_SEC - elapsed)
        time.sleep(sleep_time)


if __name__ == "__main__":
    main()
