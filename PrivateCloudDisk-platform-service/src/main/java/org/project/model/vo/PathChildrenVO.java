package org.project.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 路径查询子节点响应 VO。
 * 包含解析后的目标节点 node_id 和其子节点列表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PathChildrenVO {
    /** 目标文件夹节点 ID（供客户端保存） */
    private String node_id;
    /** 目标文件夹的子节点列表 */
    private List<NodeVO> children;
}