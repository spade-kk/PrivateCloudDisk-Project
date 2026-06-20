package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 上传会话响应
 */
public class UploadSessionResponse {

    @SerializedName("uploads_id")
    private String uploadsId;

    public String getUploadsId() { return uploadsId; }
}