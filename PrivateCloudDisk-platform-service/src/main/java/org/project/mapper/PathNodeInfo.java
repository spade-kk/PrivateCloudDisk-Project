package org.project.mapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 路径节点信息，用于计算文件相对路径
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PathNodeInfo {
    /** 后代节点ID */
    private UUID descendantId;
    /** 祖先节点ID */
    private UUID ancestorId;
    /** 节点名称 */
    private String nodeName;
    /** 深度 */
    private int depth;
}