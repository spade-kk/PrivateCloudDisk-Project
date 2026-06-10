package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

/**
 * 文件夹节点数据类
 */
@Data
public class FolderNodeEntity implements Serializable {
    public enum NodeStatus {
        lock,
        active,
        pending,
        deleted,
        trashed
    }
    public enum NodeEvent {
        Lock,
        Unlock,
        Active,
        Delete,
        Trash,
        Restore
    }

    private UUID node_id;
    private UUID user_id;
    private UUID parent_id;
    private String name;
    private String create_time;
    private NodeStatus status;
}
