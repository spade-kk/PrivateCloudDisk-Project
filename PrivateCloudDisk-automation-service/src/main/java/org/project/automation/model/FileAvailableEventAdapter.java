package org.project.automation.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** 把既有 camelCase file.available 兼容适配为 CloudEvents 领域模型。 */
public final class FileAvailableEventAdapter {
    private FileAvailableEventAdapter() {
    }

    public static LifecycleEvent adapt(String payloadJson, ObjectMapper mapper) throws Exception {
        JsonNode legacy = mapper.readTree(payloadJson);
        String eventId = normalizeEventId(legacy.path("eventId").asText(), payloadJson);
        String fileId = legacy.path("fileId").asText();
        String actorUserId = legacy.path("userId").asText();
        if (fileId.isBlank() || actorUserId.isBlank()) {
            throw new IllegalArgumentException("file.available 缺少 fileId/userId");
        }
        ObjectNode data = mapper.createObjectNode();
        data.put("file_id", fileId);
        data.put("name", legacy.path("fileName").asText());
        data.put("size", legacy.path("fileSize").asLong());
        data.put("mime_type", legacy.path("fileType").asText());
        data.put("checksum", legacy.path("checksum").asText());
        data.put("content_revision", legacy.path("contentRevision").asLong());
        data.put("content_modified", legacy.path("contentModified").asBoolean());
        data.put("preprocess_status", legacy.path("preprocessStatus").asText());
        return new LifecycleEvent(
                "1.0",
                eventId,
                "pcd.storage-service",
                "pcd.file.available.v1",
                "files/" + fileId,
                legacy.path("eventTime").asText(Instant.now().toString()),
                actorUserId,
                blankToNull(legacy.path("spaceId").asText()),
                legacy.path("correlationId").asText(),
                legacy.path("uploadsSessionId").asText(),
                data
        );
    }

    private static String normalizeEventId(String value, String payload) {
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException ignored) {
            // 原 file.available 使用过 32 位 UUID hex；统一派生为标准 UUID 供 MySQL 幂等键使用。
            return UUID.nameUUIDFromBytes(
                    ("pcd:file.available:" + value + ":" + payload)
                            .getBytes(StandardCharsets.UTF_8)
            ).toString();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
