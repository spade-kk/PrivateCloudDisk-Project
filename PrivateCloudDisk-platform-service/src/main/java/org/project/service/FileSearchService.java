package org.project.service;

import java.util.List;
import java.util.Map;

public interface FileSearchService {
    /**
     * 全文搜索文件
     * <p>接口层负责从 Request DTO 提取参数后传入，业务层不依赖任何 Request DTO。</p>
     *
     * @param keyword 搜索关键词
     * @param page 页码
     * @param size 每页大小
     * @param sortField 排序字段
     * @param asc 是否升序
     * @param userId 用户ID（权限过滤）
     * @param filters 其他过滤条件
     * @param highlightFields 高亮字段
     * @param searchAfter 深分页游标
     * @return 搜索结果 VO
     */
    FileSearchVO search(String keyword, int page, int size, String sortField, boolean asc,
                        String userId, Map<String, String> filters,
                        List<String> highlightFields, String searchAfter);
}
