package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 创建文件夹请求
 */
public class CreateFolderRequest {

    @SerializedName("parent_id")
    private String parentId;

    @SerializedName("folder_name")
    private String folderName;

    public CreateFolderRequest(String parentId, String folderName) {
        this.parentId = parentId;
        this.folderName = folderName;
    }
}