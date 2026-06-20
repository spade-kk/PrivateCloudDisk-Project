package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 操作凭证响应
 */
public class OperationTokenResponse {

    @SerializedName("operation_token")
    private String operationToken;

    public String getOperationToken() { return operationToken; }
}