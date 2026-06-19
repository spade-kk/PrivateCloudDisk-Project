package com.privateclouddisk.android.data.local;

/**
 * 偏好设置管理器
 * 使用 EncryptedSharedPreferences 安全存储敏感数据
 */
public interface PreferenceManager {

    // ── Token 管理 ──
    void saveAuthToken(String token);
    String getAuthToken();
    void clearAuthToken();

    // ── 刷新 Token ──
    void saveRefreshToken(String token);
    String getRefreshToken();
    void clearRefreshToken();

    // ── 用户信息 ──
    void saveUserId(String userId);
    String getUserId();
    void saveUserName(String userName);
    String getUserName();
    void saveUserAvatar(String avatarUrl);
    String getUserAvatar();

    // ── 应用设置 ──
    void setAutoSyncEnabled(boolean enabled);
    boolean isAutoSyncEnabled();
    void setWifiOnlyUpload(boolean wifiOnly);
    boolean isWifiOnlyUpload();
    void setBiometricEnabled(boolean enabled);
    boolean isBiometricEnabled();
    void setDarkModeEnabled(boolean enabled);
    boolean isDarkModeEnabled();
    void setCacheSize(int megabytes);
    int getCacheSize();

    // ── 清除所有 ──
    void clearAll();
    void clearSessionData();
}