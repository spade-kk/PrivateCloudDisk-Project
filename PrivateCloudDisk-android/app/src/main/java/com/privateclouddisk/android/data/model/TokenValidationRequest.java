package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Token 验证请求
 */
public class TokenValidationRequest {

    @SerializedName("token")
    private String token;

    public TokenValidationRequest(String token) {
        this.token = token;
    }
}