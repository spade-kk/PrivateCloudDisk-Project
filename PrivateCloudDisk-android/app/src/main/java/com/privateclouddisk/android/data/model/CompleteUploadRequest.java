package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 完成上传请求
 */
public class CompleteUploadRequest {

    @SerializedName("upload_id")
    private String uploadId;

    @SerializedName("file_name")
    private String fileName;

    @SerializedName("parent_id")
    private String parentId;

    public CompleteUploadRequest(String uploadId, String fileName, String parentId) {
        this.uploadId = uploadId;
        this.fileName = fileName;
        this.parentId = parentId;
    }
}