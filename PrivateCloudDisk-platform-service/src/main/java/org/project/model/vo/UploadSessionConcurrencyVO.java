package org.project.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 当前用户/空间范围内的上传会话并发状态。
 */
@Data
public class UploadSessionConcurrencyVO {
    private Integer max_concurrent_sessions;
    private Integer active_session_count;
    private Integer remaining_concurrent_sessions;
    private List<UploadSessionSummaryVO> sessions;
}
