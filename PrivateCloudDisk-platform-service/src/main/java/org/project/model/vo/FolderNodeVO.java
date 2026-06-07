package org.project.model.vo;

import lombok.Data;

@Data
public class FolderNodeVO {
    private String node_id;
    private String parent_id;
    private String name;
    private String create_time;
}
