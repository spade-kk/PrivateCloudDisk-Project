package org.project.model.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 文件搜索响应 VO
 */
@Data
public class FileSearchVO {
    private long total;
    private List<Map<String, Object>> hits;
    private Map<String, Map<String, Long>> aggregations;
    private String searchAfter;
}