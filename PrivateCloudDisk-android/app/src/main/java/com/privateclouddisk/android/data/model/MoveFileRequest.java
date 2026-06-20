package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 移动文件请求
 */
public class MoveFileRequest {

    @SerializedName("target_parent_id")
    private String targetParentId;

    public MoveFileRequest(String targetParentId) {
        this.targetParentId = targetParentId;
    }
}