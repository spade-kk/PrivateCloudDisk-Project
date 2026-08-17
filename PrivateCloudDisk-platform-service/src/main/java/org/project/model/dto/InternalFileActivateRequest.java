package org.project.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 文件流水线最终激活内容快照。
 *
 * <p>插件生态生命周期改造：候选内容一旦通过最终 Hash/Scan，Platform 必须在激活
 * 事务中同时切换 storage_path、checksum、size 和 status，避免元数据仍指向插件修改前
 * 的原始对象。请求体可为空以兼容旧 Worker。</p>
 */
public record InternalFileActivateRequest(
        @JsonProperty("storage_path")
        @Size(max = 1024)
        String storagePath,
        @Pattern(regexp = "^[0-9a-fA-F]{64}$")
        String checksum,
        @Min(0)
        Long size,
        @JsonProperty("content_revision")
        @Min(0)
        Long contentRevision,
        @JsonProperty("content_modified")
        Boolean contentModified,
        @JsonProperty("preprocess_status")
        @Size(max = 32)
        String preprocessStatus
) {
}
