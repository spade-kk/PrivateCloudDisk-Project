package com.privateclouddisk.android.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * 分页数据
 */
public class PagedData<T> {

    @SerializedName("items")
    private java.util.List<T> items;

    @SerializedName("total")
    private long total;

    @SerializedName("page")
    private int page;

    @SerializedName("pageSize")
    private int pageSize;

    @SerializedName("totalPages")
    private int totalPages;

    public java.util.List<T> getItems() { return items; }
    public void setItems(java.util.List<T> items) { this.items = items; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public boolean hasMore() {
        return page < totalPages;
    }
}

/**
 * 分页响应
 */
class PagedResponse<T> extends ApiResponse<PagedData<T>> {
}