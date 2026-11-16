package com.pcpanel.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Uygulama ayarlarını SharedPreferences üzerinde saklayan yardımcı sınıf.
 */
public final class Prefs {

    private static final String PREF_NAME = "pc_panel_prefs";
    private static final String KEY_SLEEP_TIMEOUT_SEC = "sleep_timeout_sec";
    private static final String KEY_WAKE_DELAY_SEC = "wake_delay_sec";
    private static final String KEY_THEME_ID = "ui_theme_id";

    public static final int DEFAULT_SLEEP_TIMEOUT_SEC = 15;
    public static final int DEFAULT_WAKE_DELAY_SEC = 3;

    public static final int THEME_DARK = 0;
    public static final int THEME_AMOLED = 1;
    public static final int THEME_MIDNIGHT = 2;
    public static final int THEME_CRIMSON = 3;

    private Prefs() {
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Veri kesildikten kaç saniye sonra UYKU durumuna geçileceğini döner.
     */
    public static int getSleepTimeoutSec(Context context) {
        return Math.max(3, getPrefs(context).getInt(KEY_SLEEP_TIMEOUT_SEC, DEFAULT_SLEEP_TIMEOUT_SEC));
    }

    public static void setSleepTimeoutSec(Context context, int seconds) {
        getPrefs(context).edit().putInt(KEY_SLEEP_TIMEOUT_SEC, Math.max(3, seconds)).apply();
    }

    /**
     * Taze veri kesintisiz kaç saniye gelince AKTİF durumuna geçileceğini döner.
     */
    public static int getWakeDelaySec(Context context) {
        return Math.max(1, getPrefs(context).getInt(KEY_WAKE_DELAY_SEC, DEFAULT_WAKE_DELAY_SEC));
    }

    public static void setWakeDelaySec(Context context, int seconds) {
        getPrefs(context).edit().putInt(KEY_WAKE_DELAY_SEC, Math.max(1, seconds)).apply();
    }

    /**
     * Seçili arayüz tema kimliğini döner (0: Koyu, 1: AMOLED, 2: Gece Mavisi, 3: Kızıl).
     */
    public static int getThemeId(Context context) {
        return getPrefs(context).getInt(KEY_THEME_ID, THEME_DARK);
    }

    public static void setThemeId(Context context, int themeId) {
        getPrefs(context).edit().putInt(KEY_THEME_ID, themeId).apply();
    }
}
