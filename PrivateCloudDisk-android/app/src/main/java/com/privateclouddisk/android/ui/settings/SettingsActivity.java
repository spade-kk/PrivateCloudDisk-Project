package com.privateclouddisk.android.ui.settings;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.privateclouddisk.android.R;
import com.privateclouddisk.android.data.local.PreferenceManager;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * 设置 Activity
 */
@AndroidEntryPoint
public class SettingsActivity extends AppCompatActivity {

    @Inject PreferenceManager preferenceManager;

    private SwitchCompat switchAutoSync, switchWifiOnly, switchBiometric, switchDarkMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initViews();
        loadSettings();
    }

    private void initViews() {
        switchAutoSync = findViewById(R.id.switch_auto_sync);
        switchWifiOnly = findViewById(R.id.switch_wifi_only);
        switchBiometric = findViewById(R.id.switch_biometric);
        switchDarkMode = findViewById(R.id.switch_dark_mode);

        switchAutoSync.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setAutoSyncEnabled(isChecked);
        });

        switchWifiOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setWifiOnlyUpload(isChecked);
        });

        switchBiometric.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setBiometricEnabled(isChecked);
            if (isChecked) {
                Toast.makeText(this, "生物识别已启用", Toast.LENGTH_SHORT).show();
            }
        });

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setDarkModeEnabled(isChecked);
            Toast.makeText(this, "深色模式将在下次启动时生效", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_clear_cache).setOnClickListener(v -> {
            Toast.makeText(this, "缓存已清除", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btn_about).setOnClickListener(v -> {
            Toast.makeText(this, "私有云盘 v1.0.0", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadSettings() {
        switchAutoSync.setChecked(preferenceManager.isAutoSyncEnabled());
        switchWifiOnly.setChecked(preferenceManager.isWifiOnlyUpload());
        switchBiometric.setChecked(preferenceManager.isBiometricEnabled());
        switchDarkMode.setChecked(preferenceManager.isDarkModeEnabled());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}