package org.project.model.dto;

import lombok.Data;

/**
 * 节点查询条件DTO
 */
@Data
public class NodeQueryDTO {
    /** 父节点ID */
    private String parentId;
    
    /** 搜索关键词 */
    private String keyword;
    
    /** 文件类型过滤 */
    private String fileType;
    
    /** 排序字段：name-名称, size-大小, time-时间 */
    private String sortBy;
    
    /** 排序方向：asc-升序, desc-降序 */
    private String sortOrder;
    
    /** 页码 */
    private Integer page = 1;
    
    /** 每页数量 */
    private Integer pageSize = 20;
}
