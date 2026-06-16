package org.project.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件夹下载文件信息 DTO
 * 用于递归获取文件夹下所有文件的元数据（含存储路径）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FolderFileInfo {
    /** 文件 ID */
    private String fileId;
    /** 文件名称 */
    private String fileName;
    /** 文件大小 (bytes) */
    private Long fileSize;
    /** 文件在存储服务中的路径 */
    private String storagePath;
}