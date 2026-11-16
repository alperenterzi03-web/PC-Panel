package com.pcpanel.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Cihaz açıldığında veri izleme servisini (MonitorService) başlatan alıcı.
 * Güç olaylarını (POWER_CONNECTED / POWER_DISCONNECTED) dinlemez.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            Intent serviceIntent = new Intent(context, MonitorService.class);
            context.startService(serviceIntent);
        }
    }
}
