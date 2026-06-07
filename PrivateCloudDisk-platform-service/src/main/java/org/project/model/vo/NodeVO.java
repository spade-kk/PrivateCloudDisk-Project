package org.project.model.vo;

import lombok.Data;
import org.project.model.entity.NodeEntity;

@Data
public class NodeVO {
    private String node_id;
    private NodeEntity.NodeType node_type;
    private String node_name;
    private Long node_size;
}
