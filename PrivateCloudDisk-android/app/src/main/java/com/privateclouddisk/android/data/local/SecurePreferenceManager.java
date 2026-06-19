package com.privateclouddisk.android.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import timber.log.Timber;

/**
 * 安全偏好设置管理器实现
 * 使用 AndroidX Security Crypto 加密存储敏感数据
 */
@Singleton
public class SecurePreferenceManager implements PreferenceManager {

    private static final String PREF_NAME = "privateclouddisk_secure_prefs";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_AVATAR = "user_avatar";
    private static final String KEY_AUTO_SYNC = "auto_sync";
    private static final String KEY_WIFI_ONLY = "wifi_only_upload";
    private static final String KEY_BIOMETRIC = "biometric_enabled";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_CACHE_SIZE = "cache_size";

    private final SharedPreferences prefs;

    @Inject
    public SecurePreferenceManager(@ApplicationContext Context context) {
        SharedPreferences sharedPrefs;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            sharedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            Timber.e(e, "Failed to create EncryptedSharedPreferences, falling back to regular");
            sharedPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
        this.prefs = sharedPrefs;
    }

    // ── Token 管理 ──

    @Override
    public void saveAuthToken(String token) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply();
    }

    @Override
    public String getAuthToken() {
        return prefs.getString(KEY_AUTH_TOKEN, null);
    }

    @Override
    public void clearAuthToken() {
        prefs.edit().remove(KEY_AUTH_TOKEN).apply();
    }

    @Override
    public void saveRefreshToken(String token) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply();
    }

    @Override
    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    @Override
    public void clearRefreshToken() {
        prefs.edit().remove(KEY_REFRESH_TOKEN).apply();
    }

    // ── 用户信息 ──

    @Override
    public void saveUserId(String userId) {
        prefs.edit().putString(KEY_USER_ID, userId).apply();
    }

    @Override
    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    @Override
    public void saveUserName(String userName) {
        prefs.edit().putString(KEY_USER_NAME, userName).apply();
    }

    @Override
    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, null);
    }

    @Override
    public void saveUserAvatar(String avatarUrl) {
        prefs.edit().putString(KEY_USER_AVATAR, avatarUrl).apply();
    }

    @Override
    public String getUserAvatar() {
        return prefs.getString(KEY_USER_AVATAR, null);
    }

    // ── 应用设置 ──

    @Override
    public void setAutoSyncEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply();
    }

    @Override
    public boolean isAutoSyncEnabled() {
        return prefs.getBoolean(KEY_AUTO_SYNC, true);
    }

    @Override
    public void setWifiOnlyUpload(boolean wifiOnly) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply();
    }

    @Override
    public boolean isWifiOnlyUpload() {
        return prefs.getBoolean(KEY_WIFI_ONLY, false);
    }

    @Override
    public void setBiometricEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply();
    }

    @Override
    public boolean isBiometricEnabled() {
        return prefs.getBoolean(KEY_BIOMETRIC, false);
    }

    @Override
    public void setDarkModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply();
    }

    @Override
    public boolean isDarkModeEnabled() {
        return prefs.getBoolean(KEY_DARK_MODE, true);
    }

    @Override
    public void setCacheSize(int megabytes) {
        prefs.edit().putInt(KEY_CACHE_SIZE, megabytes).apply();
    }

    @Override
    public int getCacheSize() {
        return prefs.getInt(KEY_CACHE_SIZE, 500);
    }

    // ── 清除 ──

    @Override
    public void clearAll() {
        prefs.edit().clear().apply();
    }

    @Override
    public void clearSessionData() {
        clearAuthToken();
        clearRefreshToken();
    }
}