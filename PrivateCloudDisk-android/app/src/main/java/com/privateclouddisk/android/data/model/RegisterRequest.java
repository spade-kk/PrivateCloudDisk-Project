package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 注册请求
 */
public class RegisterRequest {

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