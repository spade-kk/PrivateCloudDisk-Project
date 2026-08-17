package org.project.model.vo;

import lombok.Data;

/**
 * 创建上传会话响应。
 *
 * 保留 uploads_id，同时返回并发槽位快照，旧客户端可继续读取 data.uploads_id；
 * 前端新客户端使用 remaining_concurrent_sessions 做队列观测，不把它当作强一致配额。
 */
@Data
public class UploadSessionCreateVO {
    private String uploads_id;
    private Integer max_concurrent_sessions;
    private Integer active_session_count;
    private Integer remaining_concurrent_sessions;
}
