package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 用户信息（对应后端 UserVO）
 */
public class UserProfile {

    @SerializedName("user_id")
    private String userId;

    @SerializedName("account")
    private String account;

    @SerializedName("user_name")
    private String userName;

    @SerializedName("phone_number")
    private String phoneNumber;

    @SerializedName("email")
    private String email;

    @SerializedName("avatar_url")
    private String avatarUrl;

    // ── Getters & Setters ──
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    /** 显示名称：优先用 userName，否则用 account */
    public String getDisplayName() {
        return (userName != null && !userName.isEmpty()) ? userName : account;
    }
}

/**
 * 登录请求
 */
class LoginRequest {

    @SerializedName("account")
    private String account;

    @SerializedName("phone_number")
    private String phoneNumber;

    @SerializedName("password")
    private String password;

    public LoginRequest(String account, String password) {
        this.account = account;
        this.password = password;
    }

    public String getAccount() { return account; }
    public String getPassword() { return password; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}

/**
 * 登录响应
 */
class LoginResponse {

    @SerializedName("token")
    private String token;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("user_name")
    private String userName;

    public String getToken() { return token; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
}

/**
 * 注册请求
 */
class RegisterRequest {

    @SerializedName("account")
    private String account;

    @SerializedName("user_name")
    private String userName;

    @SerializedName("password")
    private String password;

    public RegisterRequest(String account, String userName, String password) {
        this.account = account;
        this.userName = userName;
        this.password = password;
    }

    public String getAccount() { return account; }
    public String getUserName() { return userName; }
    public String getPassword() { return password; }
}

/**
 * Token 验证请求
 */
class TokenValidationRequest {

    @SerializedName("token")
    private String token;

    public TokenValidationRequest(String token) {
        this.token = token;
    }
}

/**
 * Token 刷新请求
 */
class TokenRefreshRequest {

    @SerializedName("refresh_token")
    private String refreshToken;

    public TokenRefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}

/**
 * Token 刷新响应
 */
class TokenRefreshResponse {

    @SerializedName("token")
    private String token;

    @SerializedName("refresh_token")
    private String refreshToken;

    public String getToken() { return token; }
    public String getRefreshToken() { return refreshToken; }
}