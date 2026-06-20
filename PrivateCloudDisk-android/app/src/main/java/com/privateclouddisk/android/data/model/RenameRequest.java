package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 重命名请求
 */
public class RenameRequest {

    @SerializedName("new_name")
    private String newName;

    public RenameRequest(String newName) {
        this.newName = newName;
    }
}