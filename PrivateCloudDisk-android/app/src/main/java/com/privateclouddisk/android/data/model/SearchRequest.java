package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 搜索请求
 */
public class SearchRequest {

    @SerializedName("keyword")
    private String keyword;

    @SerializedName("file_type")
    private String fileType;

    @SerializedName("page")
    private int page;

    @SerializedName("page_size")
    private int pageSize;

    public SearchRequest(String keyword, String fileType, int page, int pageSize) {
        this.keyword = keyword;
        this.fileType = fileType;
        this.page = page;
        this.pageSize = pageSize;
    }
}