package org.project.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.project.workflow.client.PlatformAuthorizationClient;
import org.project.workflow.exception.WorkflowApiException;
import org.project.workflow.model.WorkflowModels.AgentCapabilityInvocation;
import org.project.workflow.model.WorkflowModels.CapabilityAuditEntry;
import org.project.workflow.model.WorkflowModels.CapabilityInvocation;
import org.project.workflow.model.WorkflowModels.CapabilityResult;
import org.project.workflow.model.WorkflowModels.CapabilityRow;
import org.project.workflow.model.WorkflowModels.McpCapabilityInvocation;
import org.project.workflow.model.WorkflowModels.McpCapabilityListRequest;
import org.project.workflow.model.WorkflowModels.McpCapabilityPage;
import org.project.workflow.model.WorkflowModels.McpProtocolAuditEntry;
import org.project.workflow.repository.CapabilityAuditMapper;
import org.project.workflow.repository.CapabilityMapper;
import org.project.workflow.repository.CapabilityInvocationMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** 能力调用中心：注册、Schema 前置校验、路由、权限边界、审计与统一错误边界。 */
@Service
public class CapabilityHubService {
    private static final int MAX_TEXT_LENGTH = 65_536;
    private final CapabilityMapper mapper;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final PluginCapabilityClient pluginCapabilityClient;
    private final CapabilityInvocationMapper invocationMapper;
    private final PlatformAuthorizationClient authorizationClient;
    private final CapabilitySchemaValidator schemaValidator;
    private final ApiCapabilityInvoker apiCapabilityInvoker;
    private final CapabilityAuditMapper auditMapper;

    public CapabilityHubService(
            CapabilityMapper mapper,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            PluginCapabilityClient pluginCapabilityClient,
            CapabilityInvocationMapper invocationMapper,
            PlatformAuthorizationClient authorizationClient,
            CapabilitySchemaValidator schemaValidator,
            ApiCapabilityInvoker apiCapabilityInvoker,
            CapabilityAuditMapper auditMapper
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.pluginCapabilityClient = pluginCapabilityClient;
        this.invocationMapper = invocationMapper;
        this.authorizationClient = authorizationClient;
        this.schemaValidator = schemaValidator;
        this.apiCapabilityInvoker = apiCapabilityInvoker;
        this.auditMapper = auditMapper;
    }

    public List<CapabilityRow> search(String sourceType, String query, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        return mapper.search(blank(sourceType), blank(query), safeSize, (Math.max(page, 1) - 1) * safeSize);
    }

    public CapabilityRow get(String key) {
        return mapper.findByKey(key);
    }

    /**
     * 已认证用户的能力发现入口，供 Web IDE 与 CloudFlow LS 使用。
     *
     * <p>[CLOUDFLOW-LS-AUTH-001] Registry 是控制面事实来源，不是匿名目录。查询先从
     * Gateway 注入的用户身份取得空间授权快照，再按能力声明权限和 availability policy
     * 筛选；解析错误采用 deny-by-default，避免畸形策略意外放行能力。</p>
     */
    public List<CapabilityRow> searchVisibleTo(
            String userId, String tenantId, String spaceId,
            String sourceType, String query, int page, int size
    ) {
        String principal = requirePrincipal(userId);
        List<CapabilityRow> candidates = search(sourceType, query, page, size);
        List<String> granted = candidates.stream()
                .anyMatch(capability -> !readStringList(capability.requiredPermissionsJson()).isEmpty())
                ? authorizationClient.resolveGrantedPermissions(principal, blank(spaceId))
                : List.of();
        HashSet<String> grants = new HashSet<>(safeList(granted));
        return candidates.stream()
                .filter(capability -> isVisibleTo(capability, grants, blank(tenantId), blank(spaceId)))
                .toList();
    }

    /** 对单个能力同样走与列表完全一致的用户/租户/空间门禁，避免详情越权。 */
    public CapabilityRow getVisibleTo(String userId, String tenantId, String spaceId, String key) {
        String principal = requirePrincipal(userId);
        CapabilityRow capability = get(key);
        if (capability == null) {
            return null;
        }
        List<String> granted = readStringList(capability.requiredPermissionsJson()).isEmpty()
                ? List.of()
                : authorizationClient.resolveGrantedPermissions(principal, blank(spaceId));
        return isVisibleTo(capability, new HashSet<>(safeList(granted)), blank(tenantId), blank(spaceId))
                ? capability : null;
    }

    /**
     * MCP 的能力发现只返回审核过的导出子集；列表仍按用户、租户、空间的实时授权和
     * availability policy 过滤。offset 是私网 Hub↔Adapter 细节，MCP Client 只能看到
     * Adapter 生成的不可猜测 cursor，不能借此遍历内部 Capability Registry。
     */
    public McpCapabilityPage listMcpVisible(McpCapabilityListRequest query) {
        String principal = requirePrincipal(query.userId());
        int limit = Math.max(1, Math.min(query.limit(), 100));
        List<CapabilityRow> candidates = mapper.search(null, null, limit, query.offset());
        List<String> granted = candidates.stream()
                .anyMatch(capability -> !readStringList(capability.requiredPermissionsJson()).isEmpty())
                ? authorizationClient.resolveGrantedPermissions(principal, blank(query.spaceId()))
                : List.of();
        HashSet<String> grants = new HashSet<>(safeList(granted));
        List<CapabilityRow> visible = candidates.stream()
                .filter(capability -> "ACTIVE".equals(capability.status()))
                .filter(capability -> McpCapabilityExportPolicy.isExportable(capability.capabilityKey()))
                .filter(capability -> isVisibleTo(
                        capability, grants, blank(query.tenantId()), blank(query.spaceId())
                ))
                .toList();
        Integer nextOffset = candidates.size() == limit ? query.offset() + candidates.size() : null;
        return new McpCapabilityPage(visible, nextOffset);
    }

    /**
     * MCP 调用的最终授权和幂等边界。
     *
     * <p>即使一个进程持有内部服务凭证，也不能借该端点调用未审核的 admin/delete/plugin
     * 能力：导出 allow-list、ACTIVE 状态、用户权限、租户/空间 availability policy、输入
     * Schema 和数据面二次权限校验全部在这里执行。</p>
     */
    @Transactional
    public CapabilityResult invokeMcp(McpCapabilityInvocation command) {
        long startedAt = System.currentTimeMillis();
        String principal = requirePrincipal(command.userId());
        if (!CapabilityKeyValidator.isValid(command.capabilityKey())
                || !McpCapabilityExportPolicy.isExportable(command.capabilityKey())) {
            return CapabilityResult.failure("WF-CAPABILITY-NOT-FOUND", "能力不存在或当前不可用");
        }
        String executionId = UUID.nameUUIDFromBytes(
                ("cloudflow-mcp:" + command.idempotencyKey()).getBytes(StandardCharsets.UTF_8)
        ).toString();
        int claimed = invocationMapper.claim(
                command.idempotencyKey(), executionId, "mcp", 1,
                command.capabilityKey(), command.userId(), blank(command.spaceId()), command.traceId()
        );
        if (claimed == 0) {
            String claimedCapabilityKey = invocationMapper.claimedCapabilityKey(command.idempotencyKey());
            if (claimedCapabilityKey != null && !claimedCapabilityKey.equals(command.capabilityKey())) {
                CapabilityResult conflict = CapabilityResult.failure(
                        "WF-CAPABILITY-IDEMPOTENCY-CONFLICT", "幂等键已绑定其他能力调用"
                );
                recordAudit(mcpAsInvocation(command), conflict, null, startedAt,
                        "cloudflow-mcp-server", command.traceId());
                return conflict;
            }
            String completed = invocationMapper.completedResult(command.idempotencyKey());
            if (completed == null) {
                return CapabilityResult.retryableFailure(
                        "WF-CAPABILITY-IN-PROGRESS", "相同 MCP 工具调用正在处理中"
                );
            }
            try {
                CapabilityResult result = objectMapper.readValue(completed, CapabilityResult.class);
                recordAudit(mcpAsInvocation(command), result, null, startedAt,
                        "cloudflow-mcp-server", command.traceId());
                return result;
            } catch (Exception exception) {
                return CapabilityResult.failure(
                        "WF-CAPABILITY-RESULT-CORRUPTED", "历史 MCP 工具调用结果无法读取"
                );
            }
        }
        CapabilityResult result;
        String targetService = null;
        try {
            CapabilityRow capability = mapper.findByKey(command.capabilityKey());
            if (capability == null || !"ACTIVE".equals(capability.status())
                    || !McpCapabilityExportPolicy.isExportable(capability.capabilityKey())) {
                result = CapabilityResult.failure("WF-CAPABILITY-NOT-FOUND", "能力不存在或当前不可用");
            } else {
                List<String> granted = readStringList(capability.requiredPermissionsJson()).isEmpty()
                        ? List.of()
                        : authorizationClient.resolveGrantedPermissions(principal, blank(command.spaceId()));
                if (!isVisibleTo(
                        capability, new HashSet<>(safeList(granted)), blank(command.tenantId()), blank(command.spaceId())
                )) {
                    // Avoid leaking an internal capability or policy distinction to external Agents.
                    result = CapabilityResult.failure("WF-CAPABILITY-NOT-FOUND", "能力不存在或当前不可用");
                } else {
                    CapabilityInvocation invocation = mcpAsInvocation(command);
                    List<String> schemaErrors = schemaValidator.validate(
                            capability.inputSchemaJson(), invocation.input()
                    );
                    if (!schemaErrors.isEmpty()) {
                        result = CapabilityResult.failure(
                                "WF-CAPABILITY-INPUT", String.join("；", schemaErrors)
                        );
                    } else {
                        result = dispatch(capability, invocation);
                    }
                    targetService = capabilityTarget(capability, command.capabilityKey());
                }
            }
        } catch (WorkflowApiException exception) {
            result = exception.status().is5xxServerError()
                    ? CapabilityResult.retryableFailure(exception.code(), exception.getMessage())
                    : CapabilityResult.failure(exception.code(), exception.getMessage());
        } catch (RuntimeException exception) {
            result = CapabilityResult.retryableFailure(
                    "WF-CAPABILITY-MCP-FAILED", sanitize(exception.getMessage())
            );
        }
        try {
            invocationMapper.complete(command.idempotencyKey(), objectMapper.writeValueAsString(result));
        } catch (Exception exception) {
            throw new IllegalStateException("MCP 能力调用结果持久化失败", exception);
        }
        recordAudit(mcpAsInvocation(command), result, targetService, startedAt,
                "cloudflow-mcp-server", command.traceId());
        return result;
    }

    /** MCP 服务仅能提交已脱敏的参数摘要；Hub 负责持久化到统一 capability audit 表。 */
    public void recordMcpProtocolAudit(McpProtocolAuditEntry entry) {
        String method = entry.method() == null ? "unknown" : entry.method().trim().toLowerCase(Locale.ROOT);
        if (!method.matches("[a-z0-9._/-]{1,128}")) {
            throw new WorkflowApiException("WF-MCP-AUDIT-INVALID", HttpStatus.BAD_REQUEST, "MCP 审计方法格式非法");
        }
        CapabilityAuditEntry audit = new CapabilityAuditEntry(
                "mcp:" + method.replace('/', '.'),
                "cloudflow-mcp-server",
                null,
                entry.agentId() == null || entry.agentId().isBlank() ? "mcp" : entry.agentId(),
                requirePrincipal(entry.userId()),
                blank(entry.spaceId()),
                entry.traceId(),
                paramSummary(entry.parameterSummary()),
                entry.success(),
                entry.resultCode(),
                "capability-hub",
                entry.durationMs()
        );
        auditMapper.insert(audit);
    }

    /**
     * 能力统一调用入口（需求六 6.1-6.10）：能力键格式校验 → 注册表查询 → Schema 校验 →
     * 权限校验 → 按来源分发。所有调用（含内置）都经过本统一解析管线，禁止绕过。
     */
    public CapabilityResult invoke(CapabilityInvocation invocation) {
        long startedAt = System.currentTimeMillis();
        CapabilityResult result;
        String targetService = null;
        try {
            if (!CapabilityKeyValidator.isValid(invocation.capabilityKey())) {
                result = CapabilityResult.failure("WF-CAPABILITY-KEY", "能力键格式非法");
                return result;
            }
            CapabilityRow capability = mapper.findByKey(invocation.capabilityKey());
            if (capability == null || "DISABLED".equals(capability.status())) {
                result = CapabilityResult.failure("WF-CAPABILITY-NOT-FOUND", "能力不存在或已下架");
                return result;
            }
            List<String> schemaErrors = schemaValidator.validate(capability.inputSchemaJson(), invocation.input());
            if (!schemaErrors.isEmpty()) {
                result = CapabilityResult.failure("WF-CAPABILITY-INPUT", String.join("；", schemaErrors));
                return result;
            }
            CapabilityResult denied = enforceCapabilityPermission(capability, invocation);
            if (denied != null) {
                result = denied;
                return result;
            }
            result = dispatch(capability, invocation);
            targetService = switch (capability.sourceType()) {
                case "API" -> apiTarget(capability.capabilityKey());
                case "BUILTIN" -> "builtin";
                case "PLUGIN" -> "plugin-runtime";
                default -> null;
            };
        } catch (RuntimeException exception) {
            result = CapabilityResult.failure("WF-CAPABILITY-FAILED", sanitize(exception.getMessage()));
        }
        recordAudit(invocation, result, targetService, startedAt, "workflow-service");
        return result;
    }

    /**
     * Rust gRPC Agent 的最终权限与幂等边界。
     *
     * <p>[CLOUDFLOW-RUNTIME-AGENT-001] 原 Java Worker 可直接调用能力；新行为由 Rust Runtime
     * 调度，Capability Hub 每次重新确认空间执行权限、能力声明权限，并以 step attempt 做持久化去重。</p>
     */
    @Transactional
    public CapabilityResult invokeAgent(AgentCapabilityInvocation command) {
        // Validate before the idempotency claim.  A malformed key must never
        // create a ledger row, and must not be able to replay the result of a
        // previous request that happened to reuse the same idempotency key.
        if (!CapabilityKeyValidator.isValid(command.capabilityKey())) {
            return CapabilityResult.failure("WF-CAPABILITY-KEY", "能力键格式非法");
        }
        long startedAt = System.currentTimeMillis();
        int claimed = invocationMapper.claim(
                command.idempotencyKey(), command.executionId(), command.stepId(), command.attempt(),
                command.capabilityKey(), command.userId(), blank(command.spaceId()), command.traceId()
        );
        if (claimed == 0) {
            String claimedCapabilityKey = invocationMapper.claimedCapabilityKey(command.idempotencyKey());
            if (claimedCapabilityKey != null
                    && !claimedCapabilityKey.equals(command.capabilityKey())) {
                CapabilityResult conflict = CapabilityResult.failure(
                        "WF-CAPABILITY-IDEMPOTENCY-CONFLICT",
                        "幂等键已绑定其他能力调用，必须为新的能力生成新的幂等键");
                recordAudit(commandAsInvocation(command), conflict, null, startedAt,
                        "cloudflow-runtime-agent");
                return conflict;
            }
            String completed = invocationMapper.completedResult(command.idempotencyKey());
            if (completed == null) {
                return CapabilityResult.retryableFailure(
                        "WF-CAPABILITY-IN-PROGRESS", "相同能力调用正在处理中");
            }
            try {
                CapabilityResult result = objectMapper.readValue(completed, CapabilityResult.class);
                recordAudit(commandAsInvocation(command), result, null, startedAt, "cloudflow-runtime-agent");
                return result;
            } catch (Exception exception) {
                return CapabilityResult.failure(
                        "WF-CAPABILITY-RESULT-CORRUPTED", "历史能力调用结果无法读取");
            }
        }
        CapabilityResult result;
        String targetService = null;
        try {
            if (command.spaceId() != null && !command.spaceId().isBlank()) {
                authorizationClient.requireExecute(command.userId(), command.spaceId());
            }
            CapabilityRow capability = mapper.findByKey(command.capabilityKey());
            if (capability == null || "DISABLED".equals(capability.status())) {
                result = CapabilityResult.failure("WF-CAPABILITY-NOT-FOUND", "能力不存在或已下架");
            } else {
                // 统一 Schema 前置校验（6.10）：与 invoke() 管线一致，非法参数拒绝。
                List<String> schemaErrors = schemaValidator.validate(
                        capability.inputSchemaJson(), command.input());
                if (!schemaErrors.isEmpty()) {
                    result = CapabilityResult.failure(
                            "WF-CAPABILITY-INPUT", String.join("；", schemaErrors));
                } else {
                    List<String> required = readStringList(capability.requiredPermissionsJson());
                    var declared = new HashSet<>(safeList(command.declaredPermissions()));
                    var granted = new HashSet<>(safeList(command.grantedPermissions()));
                    // [CLOUDFLOW-SEC-004] 权限必须按“声明权限 ∩ 当前授权权限”计算有效集合。
                    var effective = new HashSet<>(declared);
                    effective.retainAll(granted);
                    if (!effective.containsAll(required)) {
                        result = CapabilityResult.failure(
                                "WF-CAPABILITY-FORBIDDEN", "工作流声明权限与当前授权的最小交集不足");
                    } else {
                        result = dispatch(capability, commandAsInvocation(command));
                    }
                }
            }
            targetService = capabilityTarget(capability, command.capabilityKey());
        } catch (WorkflowApiException exception) {
            result = exception.status().is5xxServerError()
                    ? CapabilityResult.retryableFailure(exception.code(), exception.getMessage())
                    : CapabilityResult.failure(exception.code(), exception.getMessage());
        } catch (IllegalArgumentException exception) {
            result = CapabilityResult.failure("WF-CAPABILITY-KEY", "能力键格式非法");
        } catch (RuntimeException exception) {
            result = CapabilityResult.retryableFailure(
                    "WF-CAPABILITY-AGENT-FAILED", sanitize(exception.getMessage())
            );
        }
        try {
            invocationMapper.complete(command.idempotencyKey(), objectMapper.writeValueAsString(result));
        } catch (Exception exception) {
            throw new IllegalStateException("能力调用结果持久化失败", exception);
        }
        recordAudit(commandAsInvocation(command), result, targetService, startedAt, "cloudflow-runtime-agent");
        return result;
    }

    public void upsertProjection(CapabilityRow capability) {
        mapper.upsert(
                capability.capabilityKey(),
                capability.sourceType(),
                capability.sourceId(),
                capability.sourceVersion(),
                capability.displayName(),
                capability.description(),
                capability.inputSchemaJson(),
                capability.outputSchemaJson(),
                capability.requiredPermissionsJson(),
                capability.availabilityPolicyJson(),
                capability.status(),
                capability.revision()
        );
    }

    /** 统一分发（需求六 6.11）：内置走本地处理器，API 走数据面调用器，插件走 Plugin Runtime。 */
    private CapabilityResult dispatch(CapabilityRow capability, CapabilityInvocation invocation) {
        return switch (capability.sourceType()) {
            case "BUILTIN" -> invokeBuiltin(invocation);
            case "API" -> {
                CapabilityResult routed = apiCapabilityInvoker.invoke(invocation, capability.availabilityPolicyJson());
                yield routed != null ? routed : invokeLegacyPlatformApi(invocation);
            }
            case "PLUGIN" -> pluginCapabilityClient.invoke(invocation, capability);
            case "LOCAL_PLUGIN" -> CapabilityResult.failure(
                    "WF-LOCAL-CLIENT-OFFLINE",
                    "该步骤需要兼容的本地客户端在线，当前没有可用客户端"
            );
            default -> CapabilityResult.failure("WF-CAPABILITY-SOURCE", "能力来源不受支持");
        };
    }

    private CapabilityResult invokeBuiltin(CapabilityInvocation invocation) {
        return switch (invocation.capabilityKey()) {
            case "builtin:date.now" -> {
                String zone = Objects.toString(invocation.input().getOrDefault("timezone", "UTC"));
                yield CapabilityResult.success(Map.of(
                        "iso", ZonedDateTime.now(ZoneId.of(zone)).toString(),
                        "timezone", zone
                ));
            }
            case "builtin:text.transform" -> {
                String text = Objects.toString(invocation.input().getOrDefault("text", ""));
                if (text.length() > MAX_TEXT_LENGTH) {
                    yield CapabilityResult.failure("WF-CAPABILITY-INPUT", "文本长度超过 65536 字符");
                }
                String operation = Objects.toString(invocation.input().getOrDefault("operation", "trim"));
                String output = switch (operation) {
                    case "upper" -> text.toUpperCase(Locale.ROOT);
                    case "lower" -> text.toLowerCase(Locale.ROOT);
                    case "trim" -> text.trim();
                    default -> throw new IllegalArgumentException("不支持的文本转换操作");
                };
                yield CapabilityResult.success(Map.of("text", output));
            }
            default -> CapabilityResult.failure("WF-CAPABILITY-NOT-IMPLEMENTED", "内置能力尚未实现");
        };
    }

    /** 旧版平台能力（api:user.notify）保留，经同一统一分发调用，供既有 YAML/DSL 向后兼容。 */
    private CapabilityResult invokeLegacyPlatformApi(CapabilityInvocation invocation) {
        if ("api:user.notify".equals(invocation.capabilityKey())) {
            return invokePlatformApi(invocation);
        }
        return CapabilityResult.failure("WF-CAPABILITY-NOT-IMPLEMENTED", "平台能力尚未实现");
    }

    private CapabilityResult invokePlatformApi(CapabilityInvocation invocation) {
        Map<String, Object> event = Map.of(
                "event_id", UUID.randomUUID().toString(),
                "event_type", "system_notify",
                "user_id", invocation.userId(),
                "space_id", invocation.spaceId() == null ? "" : invocation.spaceId(),
                "data", Map.of(
                        "title", Objects.toString(invocation.input().get("title"), ""),
                        "body", Objects.toString(invocation.input().get("body"), ""),
                        "source", "workflow",
                        "workflow_execution_id", invocation.executionId()
                )
        );
        rabbitTemplate.convertAndSend("pcd.notification.exchange", "notification.push", event);
        return CapabilityResult.success(Map.of("accepted", true, "event_id", event.get("event_id")));
    }

    /**
     * 集中式权限门禁（需求五 5.4/5.6/6.9 / 5.18 集中实现）。
     * 仅当能力声明了权限要求时，向数据面（Platform）重新解析当前授权快照并求子集。
     * 返回 null 表示放行。
     */
    private CapabilityResult enforceCapabilityPermission(CapabilityRow capability, CapabilityInvocation invocation) {
        List<String> required = readStringList(capability.requiredPermissionsJson());
        if (required.isEmpty()) {
            return null;
        }
        try {
            List<String> granted = authorizationClient.resolveGrantedPermissions(
                    invocation.userId(), invocation.spaceId()
            );
            if (new HashSet<>(safeList(granted)).containsAll(required)) {
                return null;
            }
        } catch (WorkflowApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return CapabilityResult.retryableFailure(
                    "WF-CAPABILITY-AUTH-UNAVAILABLE", "权限服务暂时不可用，请稍后重试");
        }
        return CapabilityResult.failure("WF-CAPABILITY-FORBIDDEN", "当前用户缺少该能力的授权权限");
    }

    private boolean isVisibleTo(
            CapabilityRow capability,
            HashSet<String> granted,
            String tenantId,
            String spaceId
    ) {
        if (!granted.containsAll(readStringList(capability.requiredPermissionsJson()))) {
            return false;
        }
        return availabilityAllows(capability.availabilityPolicyJson(), tenantId, spaceId);
    }

    /**
     * 对发现阶段有意义的 policy 字段只限 enabled / tenant_ids / space_ids（同时兼容
     * camelCase）。执行超时、重试等其他 policy 字段仍由 ApiCapabilityInvoker 使用。
     */
    private boolean availabilityAllows(String policyJson, String tenantId, String spaceId) {
        if (policyJson == null || policyJson.isBlank()) {
            return true;
        }
        try {
            JsonNode policy = objectMapper.readTree(policyJson);
            if (!policy.isObject()) {
                return false;
            }
            if (policy.has("enabled") && !policy.path("enabled").asBoolean(true)) {
                return false;
            }
            return policyListAllows(policy, "tenant_ids", "tenantIds", tenantId)
                    && policyListAllows(policy, "space_ids", "spaceIds", spaceId);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean policyListAllows(JsonNode policy, String snakeCase, String camelCase, String value) {
        JsonNode allowed = policy.has(snakeCase) ? policy.get(snakeCase) : policy.get(camelCase);
        if (allowed == null || allowed.isNull()) {
            return true;
        }
        if (!allowed.isArray() || value == null || value.isBlank()) {
            return false;
        }
        for (JsonNode candidate : allowed) {
            if (value.equals(candidate.asText())) {
                return true;
            }
        }
        return false;
    }

    private static String requirePrincipal(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new WorkflowApiException(
                    "WF-CAPABILITY-UNAUTHENTICATED", HttpStatus.UNAUTHORIZED,
                    "能力查询需要已认证的用户身份"
            );
        }
        return userId.trim();
    }

    /**
     * Drops all context-bearing fields supplied by an Agent and injects the trusted signed
     * space context.  Schema-compatible {@code space_id} is supplied server-side so MCP tools
     * never ask an LLM to invent or switch tenant/space identity in their argument object.
     */
    private static CapabilityInvocation mcpAsInvocation(McpCapabilityInvocation command) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (command.input() != null) {
            command.input().forEach((key, value) -> {
                if (!isMcpInternalParameter(key)) {
                    input.put(key, value);
                }
            });
        }
        if (command.spaceId() != null && !command.spaceId().isBlank()) {
            input.put("space_id", command.spaceId().trim());
        }
        return new CapabilityInvocation(
                command.capabilityKey(), null, "mcp", command.userId(), blank(command.spaceId()), input
        );
    }

    private static boolean isMcpInternalParameter(String key) {
        if (key == null) {
            return true;
        }
        String normalized = key.replace("-", "_").replace(" ", "").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "user_id", "userid", "tenant_id", "tenantid", "space_id", "spaceid",
                 "permission_context", "permissioncontext", "declared_permissions", "declaredpermissions",
                 "granted_permissions", "grantedpermissions", "execution_id", "executionid",
                 "step_id", "stepid", "trace_id", "traceid", "idempotency_key", "idempotencykey" -> true;
            default -> false;
        };
    }

    private void recordAudit(CapabilityInvocation invocation, CapabilityResult result,
                             String targetService, long startedAt, String callerService) {
        recordAudit(invocation, result, targetService, startedAt, callerService, null);
    }

    private void recordAudit(CapabilityInvocation invocation, CapabilityResult result,
                             String targetService, long startedAt, String callerService, String traceId) {
        try {
            CapabilityAuditEntry entry = new CapabilityAuditEntry(
                    invocation.capabilityKey(),
                    callerService,
                    invocation.executionId(),
                    invocation.stepId(),
                    invocation.userId(),
                    invocation.spaceId(),
                    traceId,
                    paramSummary(invocation.input()),
                    result.success(),
                    result.errorCode(),
                    targetService,
                    System.currentTimeMillis() - startedAt
            );
            auditMapper.insert(entry);
        } catch (RuntimeException ignored) {
            // 审计失败不阻断能力调用本身。
        }
    }

    private static String apiTarget(String capabilityKey) {
        return switch (capabilityKey) {
            case "api:file.content.get" -> "storage";
            case "api:notification.send", "api:user.notify" -> "notification";
            default -> "platform";
        };
    }

    private static String capabilityTarget(CapabilityRow capability, String capabilityKey) {
        if (capability != null && "API".equals(capability.sourceType())) {
            return apiTarget(capabilityKey);
        }
        return capability == null ? null : switch (capability.sourceType()) {
            case "BUILTIN" -> "builtin";
            case "PLUGIN" -> "plugin-runtime";
            default -> null;
        };
    }

    private static CapabilityInvocation commandAsInvocation(AgentCapabilityInvocation command) {
        return new CapabilityInvocation(
                command.capabilityKey(), command.executionId(), command.stepId(),
                command.userId(), command.spaceId(),
                command.input() == null ? Map.of() : command.input()
        );
    }

    /** 参数摘要（5.16/5.17/4.20）：只记键名、类型与长度，不落任何实参值。 */
    private String paramSummary(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "{}";
        }
        Map<String, String> summary = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            if (value == null) {
                summary.put(key, "null");
            } else if (value instanceof String text) {
                summary.put(key, "s(" + text.length() + ")");
            } else if (value instanceof Number || value instanceof Boolean) {
                summary.put(key, value.getClass().getSimpleName().toLowerCase(Locale.ROOT));
            } else if (value instanceof List<?> list) {
                summary.put(key, "array(" + list.size() + ")");
            } else if (value instanceof Map<?, ?>) {
                summary.put(key, "object");
            } else {
                summary.put(key, "unknown");
            }
        });
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "能力执行失败";
        }
        String sanitized = value.replaceAll("(?i)(token|password|secret)=[^\\s,;]+", "$1=[redacted]")
                .replaceAll("[\\r\\n\\t]+", " ");
        return sanitized.length() > 1000 ? sanitized.substring(0, 1000) : sanitized;
    }
}
