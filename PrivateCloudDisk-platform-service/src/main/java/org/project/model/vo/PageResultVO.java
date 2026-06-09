package org.project.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 分页结果VO
 */
@Data
public class PageResultVO<T> {
    /** 数据列表 */
    private List<T> items;
    
    /** 总记录数 */
    private Long total;
    
    /** 当前页码 */
    private Integer page;
    
    /** 每页数量 */
    private Integer pageSize;
    
    /** 总页数 */
    private Integer totalPages;
    
    public PageResultVO(List<T> items, Long total, Integer page, Integer pageSize) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = (int) Math.ceil((double) total / pageSize);
    }
}
