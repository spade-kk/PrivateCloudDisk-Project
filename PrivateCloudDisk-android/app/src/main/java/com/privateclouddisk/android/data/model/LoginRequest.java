package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 登录请求
 */
public class LoginRequest {

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