package com.pcpanel.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

/**
 * Sürekli arka planda çalışarak pcpanel/data.json dosyasını izleyen ve
 * UYKU / AKTİF durum makinesini yöneten ön plan (foreground) servisi.
 * API 17 (Android 4.2.2) ile tam uyumludur.
 */
public class MonitorService extends Service {

    public static final String ACTION_STATE_CHANGED = "com.pcpanel.app.ACTION_STATE_CHANGED";
    public static final String EXTRA_STATE = "state";
    public static final int STATE_SLEEP = 0;
    public static final int STATE_ACTIVE = 1;

    private static final int NOTIFICATION_ID = 101;
    private static final long CHECK_INTERVAL_MS = 1000L;
    private static final long FRESH_THRESHOLD_MS = 5000L;
    private static final String DATA_RELATIVE_PATH = "pcpanel/data.json";

    private Handler handler;
    private File dataFile;
    private PowerManager.WakeLock wakeLock;
    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    // Durum makinesi ve kararlılık sayaçları
    private int currentState = STATE_SLEEP;
    private int freshStreakSec = 0;
    private int staleStreakSec = 0;

    private final Runnable checkRunnable = new Runnable() {
        @Override
        public void run() {
            evaluateDataFlow();
            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        dataFile = new File(Environment.getExternalStorageDirectory(), DATA_RELATIVE_PATH);
        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, PcAdminReceiver.class);

        startForegroundNotification();
        handler.post(checkRunnable);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Servis sistem tarafından sonlandırılırsa yeniden başlatılır
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacks(checkRunnable);
        }
        releaseWakeLock();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * API 17 uyumlu küçük bir ön plan bildirimi başlatır.
     */
    private void startForegroundNotification() {
        Intent tapIntent = new Intent(this, MainActivity.class);
        tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, tapIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(this)
                .setContentTitle("PC Panel")
                .setContentText("PC veri akışı izleniyor")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    /**
     * Her 1 saniyede data.json durumunu değerlendirir ve durum geçişlerini yönetir.
     */
    private void evaluateDataFlow() {
        DataStatus status = inspectDataFile();

        if (status == DataStatus.OFF) {
            // {"state":"off"} görüldüğünde beklemeden hemen UYKU'ya geç
            freshStreakSec = 0;
            staleStreakSec = 0;
            if (currentState != STATE_SLEEP) {
                transitionToSleep();
            }
            return;
        }

        if (status == DataStatus.FRESH) {
            staleStreakSec = 0;
            freshStreakSec++;

            if (currentState == STATE_SLEEP) {
                int wakeDelay = Prefs.getWakeDelaySec(this);
                if (freshStreakSec >= wakeDelay) {
                    transitionToActive();
                }
            }
        } else {
            // STALE (eski veya yok)
            freshStreakSec = 0;
            staleStreakSec++;

            if (currentState == STATE_ACTIVE) {
                int sleepTimeout = Prefs.getSleepTimeoutSec(this);
                if (staleStreakSec >= sleepTimeout) {
                    transitionToSleep();
                }
            }
        }
    }

    private enum DataStatus {
        FRESH,
        STALE,
        OFF
    }

    private DataStatus inspectDataFile() {
        if (dataFile == null || !dataFile.exists() || !dataFile.canRead()) {
            return DataStatus.STALE;
        }

        long ageMs = System.currentTimeMillis() - dataFile.lastModified();
        String content = readFileContent(dataFile);
        if (content == null || content.length() == 0) {
            return DataStatus.STALE;
        }

        try {
            JSONObject obj = new JSONObject(content);
            if ("off".equalsIgnoreCase(obj.optString("state", ""))) {
                return DataStatus.OFF;
            }
            // Dosya son 5 saniye içinde güncellenmiş olmalı
            if (ageMs <= FRESH_THRESHOLD_MS && obj.optLong("ts", 0L) > 0L) {
                return DataStatus.FRESH;
            }
        } catch (Exception e) {
            return DataStatus.STALE;
        }
        return DataStatus.STALE;
    }

    @SuppressWarnings("deprecation")
    private void transitionToActive() {
        currentState = STATE_ACTIVE;
        freshStreakSec = 0;
        staleStreakSec = 0;

        // Ekranı WakeLock ile uyandır
        acquireWakeLock();

        // Ana Activity'yi öne getir
        Intent activityIntent = new Intent(this, MainActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(activityIntent);

        broadcastState(STATE_ACTIVE);
    }

    private void transitionToSleep() {
        currentState = STATE_SLEEP;
        freshStreakSec = 0;
        staleStreakSec = 0;

        releaseWakeLock();
        broadcastState(STATE_SLEEP);

        // Cihaz Yöneticisi izni verilmişse ekranı kilitle; Activity finish() olmaz
        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            dpm.lockNow();
        }
    }

    private void broadcastState(int state) {
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATE, state);
        sendBroadcast(intent);
    }

    @SuppressWarnings("deprecation")
    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK
                            | PowerManager.ACQUIRE_CAUSES_WAKEUP
                            | PowerManager.ON_AFTER_RELEASE,
                    "PCPanel:WakeLock");
            wakeLock.acquire(10000L);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
            } catch (Exception ignored) {
            }
        }
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
