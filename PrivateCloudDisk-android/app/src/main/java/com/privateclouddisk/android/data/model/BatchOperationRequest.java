package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 批量操作请求
 */
public class BatchOperationRequest {

    @SerializedName("ids")
    private java.util.List<String> ids;

    public BatchOperationRequest(java.util.List<String> ids) {
        this.ids = ids;
    }
}