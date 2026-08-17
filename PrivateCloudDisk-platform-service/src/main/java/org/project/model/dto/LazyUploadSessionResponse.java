package org.project.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 懒上传会话创建响应 — 返回上传会话 ID、最终 node_id 和并发槽位快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LazyUploadSessionResponse {
    /** 上传会话 ID */
    private String uploads_id;

    /** 最终文件所在的节点 ID（懒创建后的目标文件夹） */
    private String node_id;

    private Integer max_concurrent_sessions;
    private Integer active_session_count;
    private Integer remaining_concurrent_sessions;

    public static LazyUploadSessionResponse of(String uploadsId, String nodeId) {
        return new LazyUploadSessionResponse(uploadsId, nodeId, null, null, null);
    }

    public static LazyUploadSessionResponse of(String uploadsId, String nodeId,
                                                Integer maxConcurrentSessions,
                                                Integer activeSessionCount,
                                                Integer remainingConcurrentSessions) {
        return new LazyUploadSessionResponse(uploadsId, nodeId, maxConcurrentSessions,
                activeSessionCount, remainingConcurrentSessions);
    }
}
