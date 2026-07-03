package org.project.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 懒上传会话创建响应 — 返回上传会话 ID 和最终确定的 node_id。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LazyUploadSessionResponse {
    /** 上传会话 ID */
    private String uploads_id;

    /** 最终文件所在的节点 ID（懒创建后的目标文件夹） */
    private String node_id;

    public static LazyUploadSessionResponse of(String uploadsId, String nodeId) {
        return new LazyUploadSessionResponse(uploadsId, nodeId);
    }
}