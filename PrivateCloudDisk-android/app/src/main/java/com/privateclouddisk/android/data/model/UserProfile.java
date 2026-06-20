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