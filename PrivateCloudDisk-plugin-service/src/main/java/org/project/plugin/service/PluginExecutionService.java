package org.project.plugin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.project.plugin.client.PlatformAuthorizationClient;
import org.project.plugin.model.CursorPage;
import org.project.plugin.model.ExecutionAuditInput;
import org.project.plugin.model.ExecutionLogInput;
import org.project.plugin.model.ExecutionObservabilityRequest;
import org.project.plugin.model.ExecutionRecordRequest;
import org.project.plugin.model.PluginExecutionAccessScope;
import org.project.plugin.model.PluginExecutionAuditTrail;
import org.project.plugin.model.PluginExecutionAuditTrailRow;
import org.project.plugin.model.PluginExecutionDetail;
import org.project.plugin.model.PluginExecutionDetailRow;
import org.project.plugin.model.PluginExecutionLogLine;
import org.project.plugin.model.PluginExecutionRow;
import org.project.plugin.model.PluginExecutionStats;
import org.project.plugin.repository.PluginExecutionMapper;
import org.project.plugin.exception.PluginApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 执行历史、统计和内部幂等记录服务。 */
@Service
@RequiredArgsConstructor
public class PluginExecutionService {
    private final PluginExecutionMapper mapper;
    private final PlatformAuthorizationClient platformAuthorizationClient;
    private final ExecutionObservabilitySanitizer sanitizer;
    private final ObjectMapper objectMapper;

    public void record(List<ExecutionRecordRequest> requests) {
        for (ExecutionRecordRequest request : requests) {
            mapper.insertIgnore(request);
            // 原行为只写摘要；新行为为同一 execution 初始化行号游标，保留原摘要幂等语义。
            mapper.ensureObservabilityCursor(request.executionId());
        }
    }

    /** 本地 Runtime 上报时以网关注入身份覆盖客户端自报身份，防止替其他用户写日志。 */
    public void recordLocal(
            ExecutionRecordRequest request,
            String authenticatedUserId,
            String authenticatedClientId,
            String spaceId
    ) {
        if (!"LOCAL".equals(request.triggerSource())
                || mapper.countAccessibleLocalInstallation(
                        request.installationId(),
                        request.pluginId(),
                        request.versionId(),
                        authenticatedUserId,
                        blankToNull(spaceId)
                ) != 1) {
            throw new PluginApiException(
                    "PLG-LOCAL-EXECUTION-FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "本地插件执行记录与当前安装不匹配"
            );
        }
        ExecutionRecordRequest trusted = new ExecutionRecordRequest(
                request.executionId(),
                request.pluginId(),
                request.versionId(),
                request.installationId(),
                authenticatedUserId,
                blankToNull(spaceId),
                authenticatedClientId,
                request.triggerEvent(),
                "LOCAL",
                request.status(),
                request.startedAt(),
                request.endedAt(),
                request.outputSummary(),
                request.errorCode(),
                request.correlationId(),
                request.causationId()
        );
        mapper.insertIgnore(trusted);
        mapper.ensureObservabilityCursor(trusted.executionId());
    }

    public List<PluginExecutionRow> list(
            String pluginId, String userId, String spaceId, String status, int page, int size
    ) {
        String ownerId = mapper.findPluginOwner(pluginId);
        if (ownerId == null) throw notFound();
        if (userId.equals(ownerId)) {
            return mapper.listOwned(pluginId, userId, status == null ? "" : status, (page - 1) * size, size);
        }
        assertMayManageSpace(userId, spaceId);
        return mapper.listManagedSpace(pluginId, spaceId, status == null ? "" : status, (page - 1) * size, size);
    }

    public PluginExecutionStats stats(String pluginId, String userId, String spaceId) {
        String ownerId = mapper.findPluginOwner(pluginId);
        if (ownerId == null) throw notFound();
        if (userId.equals(ownerId)) return mapper.statsOwned(pluginId, userId);
        assertMayManageSpace(userId, spaceId);
        return mapper.statsManagedSpace(pluginId, spaceId);
    }

    /** 执行详情、日志、审计全部走同一权限门禁，避免 ID 枚举导致跨空间泄漏。 */
    public PluginExecutionDetail detail(String executionId, String userId) {
        assertMayView(executionId, userId);
        PluginExecutionDetailRow row = mapper.findDetail(executionId);
        if (row == null) throw notFound();
        return new PluginExecutionDetail(
                row.executionId(), row.pluginId(), row.pluginName(), row.versionId(), row.version(),
                row.runtime(), row.entrypoint(), row.installationId(), row.userId(), row.spaceId(),
                row.triggerEvent(), row.triggerSource(), row.executionStatus(), row.startedAt(), row.endedAt(),
                row.durationMs(), sanitizer.text(row.outputSummary(), 4000), row.errorCode(), row.correlationId(),
                row.logLineCount(), row.auditCallCount(), readLimits(row.manifestJson())
        );
    }

    public CursorPage<PluginExecutionLogLine> logs(
            String executionId, String userId, String cursor, int limit, String order,
            Instant startAt, Instant endAt, String level, String source
    ) {
        assertMayView(executionId, userId);
        int safeLimit = Math.max(1, Math.min(limit, 500));
        Long parsedCursor = parseCursor(cursor);
        String safeLevel = allowed(level, "DEBUG", "INFO", "WARN", "ERROR");
        String safeSource = allowed(source, "STDOUT", "STDERR", "PYCLOUDSDK", "SYSTEM", "RUNNER");
        boolean asc = !"desc".equalsIgnoreCase(order);
        List<PluginExecutionLogLine> rows = asc
                ? mapper.listLogsAsc(executionId, parsedCursor, safeLimit + 1, startAt, endAt, safeLevel, safeSource)
                : mapper.listLogsDesc(executionId, parsedCursor, safeLimit + 1, startAt, endAt, safeLevel, safeSource);
        return cursorPage(rows, safeLimit, PluginExecutionLogLine::sequenceNo);
    }

    public CursorPage<PluginExecutionAuditTrail> auditTrails(
            String executionId, String userId, String cursor, int limit, String capabilityType, String status
    ) {
        assertMayView(executionId, userId);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<PluginExecutionAuditTrailRow> rows = mapper.listAudits(
                executionId, parseCursor(cursor), safeLimit + 1,
                allowed(capabilityType, "BUILTIN", "PLATFORM_API", "PLUGIN"),
                allowed(status, "SUCCESS", "FAILED", "TIMEOUT", "RUNNING", "SKIPPED")
        );
        List<PluginExecutionAuditTrail> mapped = rows.stream().map(this::toAudit).toList();
        return cursorPage(mapped, safeLimit, PluginExecutionAuditTrail::sequenceNo);
    }

    public PluginExecutionAuditTrail auditDetail(String auditId, String userId) {
        PluginExecutionAuditTrailRow row = mapper.findAudit(auditId);
        if (row == null) throw new PluginApiException(
                "PLG-EXECUTION-AUDIT-NOT-FOUND", HttpStatus.NOT_FOUND, "审计记录不存在"
        );
        // 审计 ID 不携带空间上下文，必须先回查 execution_id 后复用标准授权逻辑。
        PluginExecutionAccessScope scope = mapper.findAccessScopeForAudit(auditId);
        if (scope == null) throw new PluginApiException(
                "PLG-EXECUTION-AUDIT-NOT-FOUND", HttpStatus.NOT_FOUND, "审计记录不存在"
        );
        assertMayView(scope.executionId(), userId);
        return toAudit(row);
    }

    /**
     * Runtime/Automation 的 at-least-once 可观测性投递。
     *
     * <p>[PLUGIN-EXEC-OBS-001] 旧接口声明“完整日志不得上传”以保护公开 API；
     * 新方法仅由内部令牌控制器调用，写入前验证执行记录存在、使用游标锁分配序号，
     * 并对重复 observation_id 无副作用。</p>
     */
    @Transactional
    public void recordObservability(ExecutionObservabilityRequest request) {
        if (mapper.findAccessScope(request.executionId()) == null) {
            throw new PluginApiException(
                    "PLG-EXECUTION-NOT-FOUND", HttpStatus.NOT_FOUND, "执行记录不存在，无法关联可观测性数据"
            );
        }
        String observationId = blankToNull(request.observationId());
        if (observationId == null) observationId = UUID.randomUUID().toString();
        if (mapper.registerObservation(request.executionId(), observationId) == 0) return;
        mapper.ensureObservabilityCursor(request.executionId());

        List<ExecutionLogInput> logs = request.logs() == null ? List.of() : request.logs();
        List<ExecutionAuditInput> audits = request.auditTrails() == null ? List.of() : request.auditTrails();
        Long nextLog = mapper.lockNextLogSequence(request.executionId());
        Long nextAudit = mapper.lockNextAuditSequence(request.executionId());
        if (nextLog == null || nextAudit == null) throw new IllegalStateException("执行可观测性游标未初始化");

        long sequence = nextLog;
        for (ExecutionLogInput log : logs) {
            mapper.insertLogLine(
                    request.executionId(), sequence++, log.timestamp() == null ? Instant.now() : log.timestamp(),
                    log.level(), log.source(), sanitizer.text(log.message(), 65536),
                    log.byteOffset() == null ? 0L : Math.max(0L, log.byteOffset())
            );
        }
        if (!logs.isEmpty()) mapper.updateNextLogSequence(request.executionId(), sequence);

        sequence = nextAudit;
        for (ExecutionAuditInput audit : audits) {
            Map<String, Object> target = sanitizer.map(audit.targetContext());
            Map<String, Object> input = sanitizer.map(audit.inputParams());
            Map<String, Object> output = sanitizer.map(audit.outputResult());
            String template = blankToNull(audit.summaryTemplate());
            mapper.insertAuditTrail(
                    validUuidOrRandom(audit.auditId()), request.executionId(), validUuidOrBlank(audit.parentAuditId()), sequence++,
                    audit.capabilityKey(), audit.capabilityType(), template,
                    auditSummary(audit.capabilityKey(), audit.capabilityType(), target, audit.status()),
                    sanitizer.json(target), sanitizer.json(input), summarizeJson(input), sanitizer.json(output), summarizeJson(output),
                    audit.status(), audit.durationMs(), audit.retryCount() == null ? 0 : Math.max(0, audit.retryCount()),
                    sanitizer.text(audit.errorCode(), 96), sanitizer.text(audit.errorSummary(), 2000),
                    audit.timestamp() == null ? Instant.now() : audit.timestamp()
            );
        }
        if (!audits.isEmpty()) mapper.updateNextAuditSequence(request.executionId(), sequence);
    }

    /** SSE 追尾查询使用相同授权和 sequence cursor，调用方负责输出心跳与连接生命周期。 */
    public List<PluginExecutionLogLine> tailLogs(String executionId, String userId, long afterSequence) {
        assertMayView(executionId, userId);
        return mapper.listLogsAsc(executionId, afterSequence, 500, null, null, "", "");
    }

    private void assertMayView(String executionId, String userId) {
        PluginExecutionAccessScope scope = mapper.findAccessScope(executionId);
        if (scope == null) throw notFound();
        if (userId != null && userId.equals(scope.ownerUserId())) return;
        if (scope.spaceId() != null && !scope.spaceId().isBlank()
                && platformAuthorizationClient.canManagePlugins(userId, scope.spaceId())) return;
        throw new PluginApiException(
                "PLG-EXECUTION-FORBIDDEN", HttpStatus.FORBIDDEN, "无权查看该插件执行记录"
        );
    }

    /** 公开列表的空间上下文只能来自网关注入的 X-Space-Id，不能由请求参数伪造。 */
    private void assertMayManageSpace(String userId, String spaceId) {
        if (spaceId != null && !spaceId.isBlank()
                && platformAuthorizationClient.canManagePlugins(userId, spaceId)) return;
        throw new PluginApiException(
                "PLG-EXECUTION-FORBIDDEN", HttpStatus.FORBIDDEN, "无权查看该空间的插件执行记录"
        );
    }

    private PluginExecutionAuditTrail toAudit(PluginExecutionAuditTrailRow row) {
        return new PluginExecutionAuditTrail(
                row.auditId(), row.parentAuditId(), row.sequenceNo(), row.capabilityKey(), row.capabilityType(),
                row.summaryTemplate(), sanitizer.text(row.summary(), 2000), sanitizer.parseJsonObject(row.targetContextJson()),
                sanitizer.parseJsonObject(row.inputParamsJson()), sanitizer.text(row.inputSummary(), 2000),
                sanitizer.parseJsonObject(row.outputResultJson()), sanitizer.text(row.outputSummary(), 2000),
                row.status(), row.durationMs(), row.retryCount(), row.errorCode(), sanitizer.text(row.errorSummary(), 2000),
                row.timestamp()
        );
    }

    private Map<String, Object> readLimits(String manifestJson) {
        if (manifestJson == null || manifestJson.isBlank()) return Map.of();
        try {
            Map<String, Object> manifest = objectMapper.readValue(manifestJson, new TypeReference<>() {});
            Object limits = manifest.get("limits");
            if (!(limits instanceof Map<?, ?> raw)) return Map.of();
            Map<String, Object> mapped = new java.util.LinkedHashMap<>();
            raw.forEach((key, value) -> mapped.put(String.valueOf(key), value));
            return sanitizer.map(mapped);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String auditSummary(String capabilityKey, String capabilityType, Map<String, Object> target, String status) {
        String name = firstText(target, "display_name", "user_name", "space_name", "file_name", "resource_name", "path");
        String template;
        if ("api.user.info".equals(capabilityKey)) template = "读取了用户";
        else if ("api.space.members.list".equals(capabilityKey)) template = "读取了空间成员列表";
        else if (capabilityKey.startsWith("api.file.")) template = "执行了文件操作";
        else if ("PLUGIN".equals(capabilityType)) template = "调用了插件能力";
        else if ("PLATFORM_API".equals(capabilityType)) template = "调用了平台 API";
        else template = "执行了内置能力";
        String suffix = name.isBlank() ? "“" + capabilityKey + "”" : "“" + name + "”";
        return sanitizer.text(template + suffix + ("SUCCESS".equals(status) ? "" : "（" + status + "）"), 2000);
    }

    private String summarizeJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) return "{}";
        return sanitizer.text(value.keySet().stream().limit(8).map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("{}"), 2000);
    }

    private static String firstText(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
        }
        return "";
    }

    private static Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try { return Long.parseLong(cursor); }
        catch (NumberFormatException ignored) {
            throw new PluginApiException("PLG-EXECUTION-CURSOR-INVALID", HttpStatus.UNPROCESSABLE_ENTITY, "日志游标格式无效");
        }
    }

    private static String allowed(String value, String... options) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.toUpperCase(Locale.ROOT);
        for (String option : options) if (option.equals(normalized)) return normalized;
        throw new PluginApiException("PLG-EXECUTION-FILTER-INVALID", HttpStatus.UNPROCESSABLE_ENTITY, "筛选条件无效");
    }

    private static String validUuidOrRandom(String candidate) {
        try { return UUID.fromString(candidate).toString(); }
        catch (Exception ignored) { return UUID.randomUUID().toString(); }
    }

    /** 父调用仅接受 UUID；拒绝将 Runtime 的临时/非 UUID 标识写入 UUID_TO_BIN，避免整批审计失败。 */
    private static String validUuidOrBlank(String candidate) {
        if (candidate == null || candidate.isBlank()) return null;
        try { return UUID.fromString(candidate).toString(); }
        catch (Exception ignored) { return null; }
    }

    private static PluginApiException notFound() {
        return new PluginApiException("PLG-EXECUTION-NOT-FOUND", HttpStatus.NOT_FOUND, "执行记录不存在");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static <T> CursorPage<T> cursorPage(
            List<T> rows, int limit, java.util.function.ToLongFunction<T> sequence
    ) {
        boolean hasMore = rows.size() > limit;
        List<T> items = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        String next = hasMore && !items.isEmpty() ? String.valueOf(sequence.applyAsLong(items.get(items.size() - 1))) : null;
        return new CursorPage<>(items, next, hasMore);
    }
}
