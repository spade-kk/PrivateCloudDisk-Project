package org.project.model.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class FileSearchVo {
    private long total;  // 命中总数
    private List<Map<String, Object>> hits; // 搜索结果
    private Map<String, Map<String, Long>> aggregations; // 聚合结果
    private String searchAfter; // 下一页 search_after 值
}