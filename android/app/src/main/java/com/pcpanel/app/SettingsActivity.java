package com.pcpanel.app;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/**
 * Veri kesilme uyku süresi, uyanma gecikmesi, Cihaz Yöneticisi izni ve
 * arayüz renk temasının yönetildiği ayarlar ekranı.
 */
public class SettingsActivity extends Activity {

    private static final int REQUEST_CODE_ENABLE_ADMIN = 1001;

    private TextView tvSleepTimeoutVal;
    private TextView tvWakeDelayVal;
    private TextView tvAdminStatus;
    private Button btnDeviceAdmin;

    private Button btnThemeDark;
    private Button btnThemeAmoled;
    private Button btnThemeMidnight;
    private Button btnThemeCrimson;

    private DevicePolicyManager dpm;
    private ComponentName adminComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_settings);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        adminComponent = new ComponentName(this, PcAdminReceiver.class);

        tvSleepTimeoutVal = (TextView) findViewById(R.id.tvSleepTimeoutVal);
        tvWakeDelayVal = (TextView) findViewById(R.id.tvWakeDelayVal);
        tvAdminStatus = (TextView) findViewById(R.id.tvAdminStatus);
        btnDeviceAdmin = (Button) findViewById(R.id.btnDeviceAdmin);

        btnThemeDark = (Button) findViewById(R.id.btnThemeDark);
        btnThemeAmoled = (Button) findViewById(R.id.btnThemeAmoled);
        btnThemeMidnight = (Button) findViewById(R.id.btnThemeMidnight);
        btnThemeCrimson = (Button) findViewById(R.id.btnThemeCrimson);

        Button btnSleepMinus = (Button) findViewById(R.id.btnSleepMinus);
        Button btnSleepPlus = (Button) findViewById(R.id.btnSleepPlus);
        Button btnWakeMinus = (Button) findViewById(R.id.btnWakeMinus);
        Button btnWakePlus = (Button) findViewById(R.id.btnWakePlus);
        Button btnBack = (Button) findViewById(R.id.btnBack);

        btnSleepMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int current = Prefs.getSleepTimeoutSec(SettingsActivity.this);
                Prefs.setSleepTimeoutSec(SettingsActivity.this, Math.max(3, current - 1));
                refreshValues();
            }
        });

        btnSleepPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int current = Prefs.getSleepTimeoutSec(SettingsActivity.this);
                Prefs.setSleepTimeoutSec(SettingsActivity.this, Math.min(120, current + 1));
                refreshValues();
            }
        });

        btnWakeMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int current = Prefs.getWakeDelaySec(SettingsActivity.this);
                Prefs.setWakeDelaySec(SettingsActivity.this, Math.max(1, current - 1));
                refreshValues();
            }
        });

        btnWakePlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int current = Prefs.getWakeDelaySec(SettingsActivity.this);
                Prefs.setWakeDelaySec(SettingsActivity.this, Math.min(30, current + 1));
                refreshValues();
            }
        });

        btnDeviceAdmin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dpm != null && dpm.isAdminActive(adminComponent)) {
                    dpm.removeActiveAdmin(adminComponent);
                    refreshValues();
                } else {
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            getString(R.string.admin_explain));
                    startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN);
                }
            }
        });

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        setupThemeButtons();
        refreshValues();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            refreshValues();
        }
    }

    private void refreshValues() {
        tvSleepTimeoutVal.setText(Prefs.getSleepTimeoutSec(this) + " sn");
        tvWakeDelayVal.setText(Prefs.getWakeDelaySec(this) + " sn");

        boolean adminActive = dpm != null && dpm.isAdminActive(adminComponent);
        if (adminActive) {
            tvAdminStatus.setText("Aktif — uyku modunda ekran otomatik kilitlenir");
            tvAdminStatus.setTextColor(getResources().getColor(R.color.status_ok));
            btnDeviceAdmin.setText("Yetkiyi Kaldır");
        } else {
            tvAdminStatus.setText("İzin verilmedi — uyku modunda siyah ekran gösterilir");
            tvAdminStatus.setTextColor(getResources().getColor(R.color.text_secondary));
            btnDeviceAdmin.setText("İzin İste");
        }
    }

    private void setupThemeButtons() {
        View.OnClickListener themeListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = Prefs.THEME_DARK;
                if (v == btnThemeAmoled) {
                    id = Prefs.THEME_AMOLED;
                } else if (v == btnThemeMidnight) {
                    id = Prefs.THEME_MIDNIGHT;
                } else if (v == btnThemeCrimson) {
                    id = Prefs.THEME_CRIMSON;
                }
                Prefs.setThemeId(SettingsActivity.this, id);
                refreshThemeButtons();
            }
        };
        btnThemeDark.setOnClickListener(themeListener);
        btnThemeAmoled.setOnClickListener(themeListener);
        btnThemeMidnight.setOnClickListener(themeListener);
        btnThemeCrimson.setOnClickListener(themeListener);
        refreshThemeButtons();
    }

    private void refreshThemeButtons() {
        int current = Prefs.getThemeId(this);
        styleThemeButton(btnThemeDark, "#0D1117", "#58A6FF", current == Prefs.THEME_DARK);
        styleThemeButton(btnThemeAmoled, "#000000", "#58A6FF", current == Prefs.THEME_AMOLED);
        styleThemeButton(btnThemeMidnight, "#0A1628", "#64FFDA", current == Prefs.THEME_MIDNIGHT);
        styleThemeButton(btnThemeCrimson, "#140C0E", "#FF6B6B", current == Prefs.THEME_CRIMSON);
    }

    private void styleThemeButton(Button btn, String fillHex, String accentHex, boolean selected) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(16f);
        gd.setColor(Color.parseColor(fillHex));
        gd.setStroke(selected ? 4 : 2, Color.parseColor(selected ? accentHex : "#30363D"));
        btn.setBackground(gd);
    }
}
