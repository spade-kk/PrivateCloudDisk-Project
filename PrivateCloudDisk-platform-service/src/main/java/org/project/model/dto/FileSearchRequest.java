package org.project.model.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class FileSearchRequest {
    private String keyword; // 全文搜索关键字
    private int page = 1;
    private int size = 20;
    private String sortField; // 可排序字段
    private boolean asc = true;

    private String tenantId;  // 权限过滤
    private String userId;    // 权限过滤
    private String status;    // AVAILABLE / DELETED 等

    private Map<String, String> filters; // 其他字段过滤，如 fileExt, fileCategory

    private List<String> highlightFields; // 高亮字段

    private String searchAfter; // 用于深分页
}
