package org.project.automation.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Runtime 对整个预处理入口链的终态结果。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuntimeChainResult(
        String status,
        @JsonProperty("content_modified") boolean contentModified,
        @JsonProperty("candidate_id") String candidateId,
        @JsonProperty("candidate_checksum") String candidateChecksum,
        @JsonProperty("candidate_size") Long candidateSize,
        @JsonProperty("completed_entrypoints") int completedEntrypoints,
        @JsonProperty("failure_code") String failureCode,
        @JsonProperty("failure_summary") String failureSummary
) {
}

