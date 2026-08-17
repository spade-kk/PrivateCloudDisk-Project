package org.project.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 公开仓库目录项，统一文件夹/文件显示字段。 */
@Data
public class PublicSpaceNodeVO {
    private String id;
    private String name;
    private String type;
    private Long size;
    private String fileType;
    private LocalDateTime updatedAt;
}
