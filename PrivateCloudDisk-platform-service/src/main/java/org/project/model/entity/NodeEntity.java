package org.project.model.entity;
import lombok.Data;

import java.util.UUID;

/**
 * 节点数据类
 */
@Data
public class NodeEntity {
    /**
     * 节点类型枚举
     */
    public enum NodeType {
        FOLDER, FILE
    }
    /**
     * 节点ID
     */
    private UUID node_id;
     /**
     * 节点类型
     */
    private NodeType node_type;
     /**
     * 节点名称
     */
    private String node_name;
    /**
     * 节点大小
     */
    private Long node_size;
}
