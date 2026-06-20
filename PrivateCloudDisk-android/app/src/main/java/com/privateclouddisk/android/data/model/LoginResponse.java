package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 登录响应
 */
public class LoginResponse {

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