package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Token 刷新响应
 */
public class TokenRefreshResponse {

    @SerializedName("token")
    private String token;

    @SerializedName("refresh_token")
    private String refreshToken;

    public String getToken() { return token; }
    public String getRefreshToken() { return refreshToken; }
}