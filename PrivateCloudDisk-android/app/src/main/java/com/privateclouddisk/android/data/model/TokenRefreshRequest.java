package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Token 刷新请求
 */
public class TokenRefreshRequest {

    @SerializedName("refresh_token")
    private String refreshToken;

    public TokenRefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}