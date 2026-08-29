package org.project.automation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.List;

/**
 * Runtime 对整个预处理入口链的终态结果。
 *
 * <p>与 plugin-runtime-service 的 {@code model.RuntimeChainResult} JSON 契约一一对应
 * （field 名与 omitempty 语义一致）。{@code output}/{@code logs} 仅供可观测性与审计，
 * 不参与候选内容提交语义：{@code output} 为最后一个已执行入口函数的序列化返回值
 * （可能有 {@code null}），{@code logs} 为容器 stdout/stderr 的脱敏文本（保留换行，
 * ≤64 KiB，含插件 print/pycloud.log/runner.py、restricted.py 输出与退出信息）。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeChainResult(
        String status,
        @JsonProperty("content_modified") boolean contentModified,
        @JsonProperty("candidate_id") String candidateId,
        @JsonProperty("candidate_checksum") String candidateChecksum,
        @JsonProperty("candidate_size") Long candidateSize,
        @JsonProperty("completed_entrypoints") int completedEntrypoints,
        @JsonProperty("failure_code") String failureCode,
        @JsonProperty("failure_summary") String failureSummary,
        @JsonProperty("output") Map<String, Object> output,
        @JsonProperty("logs") String logs,
        /**
         * 由受信 Runtime Agent 在 Unix Socket 请求入口与响应出口产生并已脱敏的能力调用事实；
         * 空列表兼容旧 Runtime。原实现描述为“Runtime SDK 产生”，但 SDK 位于不可信插件容器侧，
         * 因此改为 Agent 统一记录，以满足 UDS 多租户隔离与审计不可伪造要求。
         */
        @JsonProperty("audit_trails") List<RuntimeAuditRecord> auditTrails
) {
}
