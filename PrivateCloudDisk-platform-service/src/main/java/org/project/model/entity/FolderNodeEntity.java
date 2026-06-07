package org.project.model.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 文件夹节点数据类
 */
@Data
public class FolderNodeEntity implements Serializable {
    public enum NodeStatus {
        lock,
        active,
        pending
    }
    public enum NodeEvent {
        Lock,
        Unlock,
        Active
    }

    private String node_id;
    private String user_id;
    private String parent_id;
    private String name;
    private String create_time;
    private NodeStatus status;
}
