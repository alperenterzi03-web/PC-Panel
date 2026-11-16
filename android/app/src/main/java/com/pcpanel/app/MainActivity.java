package com.pcpanel.app;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * PC'den gelen CPU, GPU, RAM, Disk, sıcaklık, ağ ve saat/tarih verilerini dairesel halkalarla
 * gösteren ana tam ekran aktivitesi. Android 4.2.2 (API 17) ile tam uyumludur.
 */
public class MainActivity extends Activity {

    private static final long REFRESH_INTERVAL_MS = 1000L;
    private static final long STALE_THRESHOLD_MS = 5000L;
    private static final String DATA_RELATIVE_PATH = "pcpanel/data.json";

    private TextView tvTimeMain;
    private TextView tvTimeSec;
    private TextView tvDate;
    private TextView tvBattery;
    private TextView tvNet;
    private TextView tvStatus;
    private TextView tvLastUpdate;
    private View viewStatusDot;
    private Button btnLock;

    private CircularRingView ringCpu;
    private CircularRingView ringGpu;
    private CircularRingView ringRam;
    private CircularRingView ringDisk;

    private View rootContainer;
    private View cardStatus;
    private View cardCpu;
    private View cardGpu;
    private View cardRam;
    private View cardDisk;
    private View layoutWaiting;
    private View viewSleepBlack;

    private Handler handler;
    private File dataFile;
    private SimpleDateFormat dateFormat;
    private Locale trLocale;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            readAndUpdate();
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    // MonitorService'ten gelen UYKU / AKTİF durum değişimlerini dinleyen alıcı
    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) {
                return;
            }
            int state = intent.getIntExtra(
                    MonitorService.EXTRA_STATE, MonitorService.STATE_ACTIVE);
            if (state == MonitorService.STATE_SLEEP) {
                enterSleepMode();
            } else {
                enterActiveMode();
            }
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryUi(intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        applyWindowFlags();

        setContentView(R.layout.activity_main);
        hideSystemBars();

        tvTimeMain = (TextView) findViewById(R.id.tvTimeMain);
        tvTimeSec = (TextView) findViewById(R.id.tvTimeSec);
        tvDate = (TextView) findViewById(R.id.tvDate);
        tvBattery = (TextView) findViewById(R.id.tvBattery);
        tvNet = (TextView) findViewById(R.id.tvNet);
        tvStatus = (TextView) findViewById(R.id.tvStatus);
        tvLastUpdate = (TextView) findViewById(R.id.tvLastUpdate);
        viewStatusDot = findViewById(R.id.viewStatusDot);
        btnLock = (Button) findViewById(R.id.btnLock);

        ringCpu = (CircularRingView) findViewById(R.id.ringCpu);
        ringGpu = (CircularRingView) findViewById(R.id.ringGpu);
        ringRam = (CircularRingView) findViewById(R.id.ringRam);
        ringDisk = (CircularRingView) findViewById(R.id.ringDisk);

        rootContainer = findViewById(R.id.rootContainer);
        cardStatus = findViewById(R.id.cardStatus);
        cardCpu = findViewById(R.id.cardCpu);
        cardGpu = findViewById(R.id.cardGpu);
        cardRam = findViewById(R.id.cardRam);
        cardDisk = findViewById(R.id.cardDisk);
        layoutWaiting = findViewById(R.id.layoutWaiting);
        viewSleepBlack = findViewById(R.id.viewSleepBlack);

        btnLock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lockTabletNow();
            }
        });

        rootContainer.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return true;
            }
        });

        handler = new Handler(Looper.getMainLooper());
        dataFile = new File(Environment.getExternalStorageDirectory(), DATA_RELATIVE_PATH);
        trLocale = new Locale("tr", "TR");
        dateFormat = new SimpleDateFormat("EEEE, d MMMM", trLocale);

        IntentFilter filter = new IntentFilter();
        filter.addAction(MonitorService.ACTION_STATE_CHANGED);
        registerReceiver(stateReceiver, filter);

        // Sürekli izleme servisinin çalıştığından emin ol
        startService(new Intent(this, MonitorService.class));

        Intent stickyBattery = registerReceiver(
                batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (stickyBattery != null) {
            updateBatteryUi(stickyBattery);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyWindowFlags();
        hideSystemBars();
        applyTheme();
        handler.removeCallbacks(updateRunnable);
        handler.post(updateRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(stateReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void enterActiveMode() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
        if (viewSleepBlack != null) {
            viewSleepBlack.setVisibility(View.GONE);
        }
    }

    private void enterSleepMode() {
        // Activity finish() olmaz; sadece KEEP_SCREEN_ON kaldırılır ve parlaklık düşürülür
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = 0.01f;
        getWindow().setAttributes(lp);

        // Device Admin izni yoksa tam siyah ekran göster
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, PcAdminReceiver.class);
        boolean hasAdmin = dpm != null && dpm.isAdminActive(admin);
        if (viewSleepBlack != null) {
            viewSleepBlack.setVisibility(hasAdmin ? View.GONE : View.VISIBLE);
        }
    }

    private void applyWindowFlags() {
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );
    }

    private void hideSystemBars() {
        View decorView = getWindow().getDecorView();
        if (decorView != null) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
    }

    private void readAndUpdate() {
        if (dataFile == null || !dataFile.exists() || !dataFile.canRead()) {
            showWaitingState();
            return;
        }

        long ageMs = System.currentTimeMillis() - dataFile.lastModified();
        if (ageMs > STALE_THRESHOLD_MS) {
            showWaitingState();
            return;
        }

        String jsonText = readFileContent(dataFile);
        if (jsonText == null || jsonText.length() == 0) {
            showWaitingState();
            return;
        }

        try {
            JSONObject obj = new JSONObject(jsonText);
            int cpu = clampPercent(obj.optInt("cpu", -1));
            int ram = clampPercent(obj.optInt("ram", -1));
            int disk = clampPercent(obj.optInt("disk", -1));
            String time = obj.optString("time", "--:--:--");
            long ts = obj.optLong("ts", 0L);

            if (cpu < 0 || ram < 0 || disk < 0 || ts <= 0L) {
                showWaitingState();
                return;
            }

            // Saat (HH:mm) ve saniye (:ss) ayrımı
            if (time.length() >= 8) {
                tvTimeMain.setText(time.substring(0, 5));
                tvTimeSec.setText(time.substring(5, 8));
            } else {
                tvTimeMain.setText(time);
                tvTimeSec.setText("");
            }

            String dateStr = obj.optString("date", "");
            if (dateStr.length() == 0) {
                dateStr = dateFormat.format(new Date(ts * 1000L));
            }
            tvDate.setText(dateStr.toUpperCase(trLocale));

            layoutWaiting.setVisibility(View.GONE);
            tvStatus.setText("P C   B A Ğ L I");
            tvStatus.setTextColor(Color.parseColor("#E2E8F0"));
            viewStatusDot.setBackgroundResource(R.drawable.status_dot_ok);
            tvLastUpdate.setText("Son Güncelleme: " + time);

            String netDown = obj.optString("net_down", "0 KB/s");
            String netUp = obj.optString("net_up", "0 KB/s");
            tvNet.setText("↓ " + netDown + "   ↑ " + netUp);

            // CPU Halkası
            ringCpu.setProgress(cpu);
            ringCpu.setRingColor(getUsageColor(cpu, "#22E3E8"));
            ringCpu.setSubText(obj.optString("cpu_freq", ""));

            // GPU Halkası
            int gpu = clampPercent(obj.optInt("gpu", 0));
            int gpuTemp = obj.optInt("gpu_temp", -1);
            ringGpu.setProgress(gpu);
            ringGpu.setRingColor(getUsageColor(gpu, "#A855F7"));
            ringGpu.setSubText(gpuTemp >= 0 ? (gpuTemp + "°C") : obj.optString("gpu_name", ""));

            // RAM Halkası
            ringRam.setProgress(ram);
            ringRam.setRingColor(getUsageColor(ram, "#FBBF24"));
            ringRam.setSubText(obj.optString("ram_info", ""));

            // DİSK Halkası
            ringDisk.setProgress(disk);
            ringDisk.setRingColor(getUsageColor(disk, "#FF5A5F"));
            ringDisk.setSubText(obj.optString("disk_info", ""));
        } catch (Exception e) {
            showWaitingState();
        }
    }

    private int getUsageColor(int percent, String defaultHex) {
        if (percent >= 85) {
            return Color.parseColor("#FF5A5F");
        }
        return Color.parseColor(defaultHex);
    }

    private void showWaitingState() {
        layoutWaiting.setVisibility(View.VISIBLE);
        tvStatus.setText("B A Ğ L A N T I   Y O K");
        tvStatus.setTextColor(getResources().getColor(R.color.status_warn));
        viewStatusDot.setBackgroundResource(R.drawable.status_dot_warn);
    }

    private int clampPercent(int value) {
        if (value < 0) {
            return -1;
        }
        return Math.min(value, 100);
    }

    private void updateBatteryUi(Intent intent) {
        if (intent == null || tvBattery == null) {
            return;
        }
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        if (level < 0 || scale <= 0) {
            tvBattery.setText("Pil --%");
            return;
        }
        int pct = Math.max(0, Math.min(100, (level * 100) / scale));
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        tvBattery.setText(charging ? ("Pil %" + pct + " ⚡") : ("Pil %" + pct));

        if (pct <= 20 && !charging) {
            tvBattery.setTextColor(getResources().getColor(R.color.status_warn));
        } else if (pct < 50 && !charging) {
            tvBattery.setTextColor(getResources().getColor(R.color.temp_warm));
        } else {
            tvBattery.setTextColor(getResources().getColor(R.color.status_ok));
        }
    }

    private void lockTabletNow() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, PcAdminReceiver.class);
        if (dpm != null && dpm.isAdminActive(admin)) {
            dpm.lockNow();
        } else {
            Toast.makeText(this, "Ekranı kilitlemek için Cihaz Yöneticisi izni verin.", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explain));
            startActivity(intent);
        }
    }

    private void applyTheme() {
        int themeId = Prefs.getThemeId(this);
        int bgColor;
        int cardColor;
        int borderColor;
        int trackColor;
        int waitingColor;

        if (themeId == Prefs.THEME_AMOLED) {
            bgColor = Color.parseColor("#000000");
            cardColor = Color.parseColor("#0A0D12");
            borderColor = Color.parseColor("#1E242E");
            trackColor = Color.parseColor("#181D26");
            waitingColor = Color.parseColor("#EE000000");
        } else if (themeId == Prefs.THEME_MIDNIGHT) {
            bgColor = Color.parseColor("#06101E");
            cardColor = Color.parseColor("#0D1B2E");
            borderColor = Color.parseColor("#1B314E");
            trackColor = Color.parseColor("#172A45");
            waitingColor = Color.parseColor("#EE06101E");
        } else if (themeId == Prefs.THEME_CRIMSON) {
            bgColor = Color.parseColor("#12090C");
            cardColor = Color.parseColor("#1C0F13");
            borderColor = Color.parseColor("#381D24");
            trackColor = Color.parseColor("#2B171D");
            waitingColor = Color.parseColor("#EE12090C");
        } else {
            bgColor = Color.parseColor("#080C12");
            cardColor = Color.parseColor("#0E1520");
            borderColor = Color.parseColor("#1C2738");
            trackColor = Color.parseColor("#1E2838");
            waitingColor = Color.parseColor("#EE080C12");
        }

        rootContainer.setBackgroundColor(bgColor);
        layoutWaiting.setBackgroundColor(waitingColor);
        ringCpu.setTrackColor(trackColor);
        ringGpu.setTrackColor(trackColor);
        ringRam.setTrackColor(trackColor);
        ringDisk.setTrackColor(trackColor);

        applyCardStyle(cardCpu, cardColor, borderColor);
        applyCardStyle(cardGpu, cardColor, borderColor);
        applyCardStyle(cardRam, cardColor, borderColor);
        applyCardStyle(cardDisk, cardColor, borderColor);
        applyCardStyle(cardStatus, cardColor, borderColor);
        applyCardStyle(tvBattery, cardColor, borderColor);
        applyCardStyle(btnLock, cardColor, borderColor);
    }

    private void applyCardStyle(View view, int fillColor, int borderColor) {
        if (view == null) {
            return;
        }
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(22f);
        gd.setColor(fillColor);
        gd.setStroke(2, borderColor);
        view.setBackground(gd);
    }

    private String readFileContent(File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
