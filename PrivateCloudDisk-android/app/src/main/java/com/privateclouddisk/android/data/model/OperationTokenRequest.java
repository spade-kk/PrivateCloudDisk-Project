package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 操作凭证请求
 */
public class OperationTokenRequest {

    @SerializedName("file_id")
    private String fileId;

    @SerializedName("operation_type")
    private String operationType;

    public OperationTokenRequest(String fileId, String operationType) {
        this.fileId = fileId;
        this.operationType = operationType;
    }
}