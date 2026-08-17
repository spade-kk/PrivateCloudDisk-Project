package org.project.plugin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.plugin.client.PlatformAuthorizationClient;
import org.project.plugin.client.RuntimeValidationClient;
import org.project.plugin.client.RuntimeTestExecutionClient;
import org.project.plugin.exception.PluginApiException;
import org.project.plugin.model.CreatePluginRequest;
import org.project.plugin.model.CreatePluginVersionRequest;
import org.project.plugin.model.CapabilityProjectionRow;
import org.project.plugin.model.PluginCapabilitySpec;
import org.project.plugin.model.PluginEntrypointSpec;
import org.project.plugin.model.PluginInstallRequest;
import org.project.plugin.model.PluginInstallationRow;
import org.project.plugin.model.PluginRow;
import org.project.plugin.model.PluginVersionRow;
import org.project.plugin.model.PluginTestRequest;
import org.project.plugin.repository.PluginTestTaskMapper;
import org.project.plugin.model.RuntimeValidationResponse;
import org.project.plugin.model.UpdatePluginRequest;
import org.project.plugin.repository.PluginManagementMapper;
import org.project.plugin.storage.PluginStoragePort;
import org.project.plugin.storage.StoredPluginPackage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 插件、不可变版本、安装和空间绑定的应用服务。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginManagementService {
    private static final String READY_EVENT = "pcd.file.content.ready.v1";
    private static final String AVAILABLE_EVENT = "pcd.file.available.v1";
    private static final String WRITE_PRE_ACTIVATION = "file.content.write_pre_activation";
    private static final Set<String> SUPPORTED_PERMISSIONS = Set.of(
            "file.content.read_staging",
            WRITE_PRE_ACTIVATION,
            "file.content.read",
            "file.metadata.read",
            "file.metadata.write",
            "file.location.move",
            "file.create",
            "space.context.read",
            "space.members.read",
            "notification.send",
            "plugin.log.write",
            // 第二阶段本地插件权限仅表示客户端 SDK 能力，仍需客户端逐项授权。
            "client.file.read",
            "client.file.upload",
            "client.ui.show",
            "client.ai.call",
            "client.clipboard.write",
            "client.system.notify",
            "client.camera.read",
            "client.location.read"
    );
    private static final Set<String> SUPPORTED_PLATFORMS = Set.of(
            "web", "windows", "macos", "linux", "ios", "android"
    );
    private static final Set<String> SUPPORTED_CLIENT_TYPES = Set.of(
            "web", "desktop", "mobile"
    );

    private final PluginManagementMapper mapper;
    private final PluginStoragePort storage;
    private final RuntimeValidationClient runtimeValidationClient;
    private final RuntimeTestExecutionClient runtimeTestExecutionClient;
    private final PluginTestTaskMapper pluginTestTaskMapper;
    private final PlatformAuthorizationClient platformAuthorizationClient;
    private final PluginPackageSigner packageSigner;
    private final ObjectMapper objectMapper;

    @Transactional
    public PluginRow create(String userId, CreatePluginRequest request) {
        requireUuid(userId, "用户身份无效");
        String pluginId = UUID.randomUUID().toString();
        try {
            mapper.insertPlugin(
                    pluginId,
                    userId,
                    request.name().trim(),
                    request.slug(),
                    request.description(),
                    request.type(),
                    request.visibility()
            );
        } catch (DuplicateKeyException exception) {
            throw conflict("PLG-SLUG-CONFLICT", "当前账号下已存在相同标识的插件");
        }
        return requireOwned(pluginId, userId);
    }

    public List<PluginRow> list(String userId, String spaceId, int page, int size) {
        requireUuid(userId, "用户身份无效");
        if (spaceId != null && !spaceId.isBlank()) {
            requireUuid(spaceId, "空间标识无效");
        }
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(page, 1);
        return mapper.listAccessible(userId, blankToNull(spaceId), safeSize, (safePage - 1) * safeSize);
    }

    public List<PluginInstallationRow> listInstallations(String userId, String spaceId) {
        requireUuid(userId, "用户身份无效");
        if (spaceId != null && !spaceId.isBlank()) {
            requireUuid(spaceId, "空间标识无效");
            if (!platformAuthorizationClient.canExecuteWorkflowCapability(userId, spaceId)) {
                throw new PluginApiException(
                        "SPACE-PLUGIN-READ-DENIED",
                        HttpStatus.FORBIDDEN,
                        "当前账号无权查看该空间的插件"
                );
            }
        }
        return mapper.listInstallations(userId, blankToNull(spaceId));
    }

    public PluginRow getOwned(String pluginId, String userId) {
        requireUuid(pluginId, "插件标识无效");
        return requireOwned(pluginId, userId);
    }

    @Transactional
    public PluginRow update(
            String pluginId,
            String userId,
            long expectedVersion,
            UpdatePluginRequest request
    ) {
        requireOwned(pluginId, userId);
        int changed = mapper.updateDraft(
                pluginId,
                userId,
                request.name(),
                request.description(),
                request.visibility(),
                expectedVersion
        );
        if (changed != 1) {
            throw conflict("PLG-DRAFT-CONFLICT", "草稿已被其他会话修改，请刷新后重试");
        }
        return requireOwned(pluginId, userId);
    }

    @Transactional
    public void delete(String pluginId, String userId) {
        if (mapper.softDelete(pluginId, userId) != 1) {
            throw notFound();
        }
    }

    @Transactional
    public PluginVersionRow createVersion(
            String pluginId,
            String userId,
            CreatePluginVersionRequest request
    ) {
        PluginRow plugin = requireOwned(pluginId, userId);
        validateRuntime(plugin.pluginType(), request.runtime());
        validatePermissionSet(request.permissions());
        validateEntrypoints(request.permissions(), request.entrypoints());
        validateDistributionTargets(
                plugin.pluginType(), request.supportedPlatforms(), request.clientTypes()
        );

        String versionId = UUID.randomUUID().toString();
        try {
            mapper.insertVersion(
                    versionId,
                    pluginId,
                    request.version(),
                    request.runtime(),
                    request.entrypoint(),
                    json(request.manifest() == null ? Map.of() : request.manifest()),
                    json(request.permissions()),
                    json(request.supportedPlatforms()),
                    json(request.clientTypes())
            );
            for (PluginEntrypointSpec entrypoint : safeList(request.entrypoints())) {
                mapper.insertEntrypoint(
                        UUID.randomUUID().toString(),
                        versionId,
                        entrypoint.event(),
                        entrypoint.functionName(),
                        entrypoint.priority() == null ? 100 : entrypoint.priority(),
                        json(entrypoint.conditions() == null ? Map.of() : entrypoint.conditions()),
                        json(entrypoint.permissions())
                );
            }
            for (PluginCapabilitySpec capability : safeList(request.capabilities())) {
                validatePermissionSet(capability.permissions() == null
                        ? List.of() : capability.permissions());
                mapper.insertCapability(
                        UUID.randomUUID().toString(),
                        versionId,
                        capability.name(),
                        capability.description(),
                        json(capability.inputSchema() == null ? Map.of() : capability.inputSchema()),
                        json(capability.outputSchema() == null ? Map.of() : capability.outputSchema()),
                        json(capability.permissions() == null ? List.of() : capability.permissions())
                );
            }
        } catch (DuplicateKeyException exception) {
            throw conflict("PLG-VERSION-CONFLICT", "该插件版本已存在");
        }
        return requireOwnedVersion(pluginId, userId, request.version());
    }

    public List<PluginVersionRow> listVersions(String pluginId, String userId) {
        requireOwned(pluginId, userId);
        return mapper.listVersions(pluginId);
    }

    @Transactional
    public StoredPluginPackage uploadPackage(
            String pluginId,
            String version,
            String userId,
            MultipartFile multipartFile
    ) {
        PluginRow plugin = requireOwned(pluginId, userId);
        PluginVersionRow versionRow = requireOwnedVersion(pluginId, userId, version);
        /*
         * [IDE-API-PLUGIN-UPLOAD] 原行为要求版本在上传前已经 PASSED 且已有源码包，
         * 与“上传源码包 → Runtime 校验 → 发布”的实际生命周期相反，导致 Web IDE
         * 创建首个版本时永远无法上传。新行为只在 immutable/已存在源码包时拒绝覆盖，
         * 允许 PENDING/FAILED 草稿先上传；同一未发布版本再次保存时原子切换源码包，
         * 旧对象在数据库指针切换后回收；校验仍由 validate() 作为发布前事实源完成。
         * 影响范围：仅未发布版本的源码上传，不改变已发布版本不可变约束。
         */
        if (versionRow.immutable()) {
            throw conflict("PLG-VERSION-CONFLICT", "已发布版本不可覆盖");
        }
        try {
            String previousObjectKey = versionRow.packageObjectKey();
            StoredPluginPackage stored = storage.putImmutable(
                    plugin.pluginType(),
                    UUID.fromString(pluginId),
                    version,
                    versionRow.entrypoint(),
                    multipartFile.getInputStream()
            );
            validateManifestIdentity(stored.manifestYaml(), pluginId, version, plugin.pluginType());
            int attached = previousObjectKey == null
                    ? mapper.attachPackage(versionRow.versionId(), stored.objectKey(), stored.sha256(), stored.packageBytes())
                    : mapper.replacePackage(versionRow.versionId(), stored.objectKey(), stored.sha256(), stored.packageBytes());
            if (attached != 1) {
                throw conflict("PLG-VERSION-CONFLICT", "该版本已经上传源码包");
            }
            if (previousObjectKey != null && !previousObjectKey.equals(stored.objectKey())) {
                try {
                    storage.deleteObject(previousObjectKey);
                } catch (IOException cleanupException) {
                    // 数据库已切换到新对象；旧对象由后台存储清理任务按对象引用回收，不能回滚新包。
                }
            }
            return stored;
        } catch (PluginApiException exception) {
            throw exception;
        } catch (IOException exception) {
            log.warn("PLG-PACKAGE-STORAGE-FAILED:{}", exception.getMessage());
            throw new PluginApiException(
                    "PLG-PACKAGE-STORAGE-FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "插件包保存失败"
            );
        }
    }

    @Transactional
    public RuntimeValidationResponse validate(
            String pluginId,
            String version,
            String userId
    ) {
        PluginVersionRow versionRow = requireOwnedVersion(pluginId, userId, version);
        if (versionRow.packageObjectKey() == null) {
            throw new PluginApiException(
                    "PLG-PACKAGE-NOT-FOUND",
                    HttpStatus.CONFLICT,
                    "请先上传插件源码包"
            );
        }
        String source;
        try {
            source = storage.readTextEntry(
                    versionRow.packageObjectKey(), versionRow.entrypoint(), 1024L * 1024L
            );
        } catch (IOException exception) {
            throw new PluginApiException(
                    "PLG-PACKAGE-STORAGE-FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "无法读取待校验的插件包"
            );
        }
        RuntimeValidationResponse response = runtimeValidationClient.validate(
                versionRow.runtime(),
                source,
                versionRow.entrypoint(),
                readStringList(versionRow.permissionConfig())
        );
        mapper.updateValidation(
                versionRow.versionId(),
                response.valid() ? "PASSED" : "FAILED",
                json(response)
        );
        if (response.valid() && "PYTHON_3_11".equals(versionRow.runtime())) {
            // [PLUGIN-METADATA-001] Runtime AST 只负责发现，Plugin Service 负责归属校验和入库。
            // 这样能力/测试入口不会依赖人工重复填写，也不会把源码执行权交给控制面 JVM。
            syncRuntimeMarkers(versionRow, response);
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private void syncRuntimeMarkers(PluginVersionRow versionRow, RuntimeValidationResponse response) {
        Map<String, Object> metrics = response.metrics() == null ? Map.of() : response.metrics();
        Object rawCapabilities = metrics.get("capabilities");
        if (rawCapabilities instanceof List<?> capabilityList) {
            for (Object raw : capabilityList) {
                if (!(raw instanceof Map<?, ?> value)) continue;
                Object rawName = value.get("name");
                Object rawFunction = value.get("function");
                String name = rawName == null ? "" : String.valueOf(rawName);
                String function = rawFunction == null ? "" : String.valueOf(rawFunction);
                if (!name.matches("^[a-z][a-z0-9_.-]{1,127}$") || !function.matches("^[a-zA-Z_][a-zA-Z0-9_]{0,127}$")) {
                    throw invalid("@capability 名称或函数名格式无效");
                }
                mapper.insertCapability(UUID.randomUUID().toString(), versionRow.versionId(), name,
                        "源码声明能力：" + function, "{}", "{}", versionRow.permissionConfig());
            }
        }
        Object rawTests = metrics.get("test_entrypoints");
        if (rawTests instanceof List<?> testList) {
            for (Object raw : testList) {
                if (!(raw instanceof Map<?, ?> value)) continue;
                Object rawFunction = value.get("name");
                String function = rawFunction == null ? "" : String.valueOf(rawFunction);
                if (!function.matches("^[a-zA-Z_][a-zA-Z0-9_]{0,127}$")) {
                    throw invalid("@test 函数名格式无效");
                }
                mapper.insertTestEntrypoint(UUID.randomUUID().toString(), versionRow.versionId(), function, json(value));
            }
        }
    }

    /** 创建开发测试任务；测试执行必须使用当前用户/空间上下文并经过 Runtime Sandbox。 */
    public Map<String, Object> createTestExecution(
            String pluginId,
            String version,
            String userId,
            String spaceId,
            PluginTestRequest request
    ) {
        PluginRow plugin = requireOwned(pluginId, userId);
        PluginVersionRow versionRow = requireOwnedVersion(pluginId, userId, version);
        if (!"CLOUD_PLUGIN".equals(plugin.pluginType()) || versionRow.immutable()) {
            throw new PluginApiException(
                    "PLG-TEST-NOT-ALLOWED", HttpStatus.CONFLICT,
                    "只有未发布的云插件草稿允许在线测试"
            );
        }
        if (request.testEntrypoint() == null || request.testEntrypoint().isBlank()) {
            throw invalid("必须指定 @test 测试入口函数");
        }
        if (mapper.countTestEntrypoint(versionRow.versionId(), request.testEntrypoint()) != 1) {
            throw new PluginApiException("PLG-TEST-ENTRYPOINT-NOT-FOUND", HttpStatus.UNPROCESSABLE_ENTITY,
                    "测试入口未在最近一次静态校验报告中声明为 @test");
        }
        String executionId = UUID.randomUUID().toString();
        pluginTestTaskMapper.insertPending(executionId, pluginId, versionRow.versionId(), userId,
                blankToNull(spaceId), request.testEntrypoint());
        try {
            return runtimeTestExecutionClient.create(
                    executionId, pluginId, versionRow.versionId(), userId, blankToNull(spaceId), request
            );
        } catch (PluginApiException exception) {
            pluginTestTaskMapper.updateStatus(executionId, userId, "FAILED", "{}",
                    exception.code(), exception.getMessage(), "", "");
            throw exception;
        }
    }

    /** 查询测试状态时以 Plugin Service 的归属校验为准，再同步 Runtime 的短期状态。 */
    public Map<String, Object> getTestExecution(String taskId, String userId) {
        var task = pluginTestTaskMapper.findOwned(taskId, userId);
        if (task == null) throw new PluginApiException("PLG-TEST-NOT-FOUND", HttpStatus.NOT_FOUND, "测试任务不存在");
        Map<String, Object> status = runtimeTestExecutionClient.get(taskId);
        pluginTestTaskMapper.updateStatus(taskId, userId,
                textOr(status.get("status"), task.status()),
                json(status.get("result")), blankToNull(textOr(status.get("error_code"), "")),
                blankToNull(textOr(status.get("error_summary"), "")),
                blankToNull(textOr(status.get("started_at"), "")),
                blankToNull(textOr(status.get("ended_at"), "")));
        return status;
    }

    public Map<String, Object> cancelTestExecution(String taskId, String userId) {
        var task = pluginTestTaskMapper.findOwned(taskId, userId);
        if (task == null) throw new PluginApiException("PLG-TEST-NOT-FOUND", HttpStatus.NOT_FOUND, "测试任务不存在");
        return runtimeTestExecutionClient.cancel(taskId);
    }

    @Transactional
    public PluginVersionRow publish(String pluginId, String version, String userId) {
        PluginRow plugin = requireOwned(pluginId, userId);
        PluginVersionRow versionRow = requireOwnedVersion(pluginId, userId, version);
        /*
         * 第二阶段本地插件：
         * 原行为只保存 SHA-256，客户端无法证明包确由平台发布；
         * 新行为在版本进入 immutable 状态前签署规范化摘要，客户端需同时校验哈希和签名。
         */
        if ("LOCAL_PLUGIN".equals(plugin.pluginType())) {
            String signature = packageSigner.sign(
                    pluginId,
                    versionRow.versionId(),
                    versionRow.version(),
                    versionRow.packageSha256(),
                    versionRow.packageSize()
            );
            if (mapper.attachPackageSignature(
                    versionRow.versionId(), signature, packageSigner.keyId()
            ) != 1) {
                throw conflict("PLG-VERSION-CONFLICT", "本地插件版本已发布或无法签名");
            }
        }
        if (mapper.publishVersion(versionRow.versionId()) != 1) {
            throw new PluginApiException(
                    "PLG-VALIDATION-FAILED",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "只有校验通过且已上传源码包的版本才能发布"
            );
        }
        mapper.markPluginPublished(pluginId, userId, versionRow.versionId());
        // 能力函数注册使用同库 Outbox，避免“插件已发布但 Capability Hub 未注册”的跨服务双写。
        for (CapabilityProjectionRow capability : mapper.listCapabilities(versionRow.versionId())) {
            int major = parseMajor(capability.version());
            String capabilityKey = "plugin:" + capability.pluginId() + ":"
                    + capability.capabilityName() + "@" + major;
            Map<String, Object> availability = Map.of(
                    "runtime", capability.runtime(),
                    "module_path", capability.modulePath(),
                    "function_name", capability.capabilityName(),
                    "timeout_seconds", 120,
                    "sandbox_required", true
            );
            Map<String, Object> projection = new java.util.LinkedHashMap<>();
            projection.put("capabilityKey", capabilityKey);
            projection.put("sourceType", "PLUGIN");
            projection.put("sourceId", capability.pluginId());
            projection.put("sourceVersion", capability.versionId());
            projection.put("displayName", capability.capabilityName());
            projection.put(
                    "description", capability.description() == null ? "" : capability.description()
            );
            projection.put("inputSchemaJson", capability.inputSchemaJson());
            projection.put("outputSchemaJson", capability.outputSchemaJson());
            projection.put("requiredPermissionsJson", capability.permissionJson());
            projection.put("availabilityPolicyJson", json(availability));
            projection.put("status", "ACTIVE");
            projection.put("revision", System.currentTimeMillis());
            mapper.insertOutbox(
                    UUID.randomUUID().toString(),
                    versionRow.versionId(),
                    "pcd.plugin.capability.published.v1",
                    json(projection)
            );
        }
        return requireOwnedVersion(pluginId, userId, version);
    }

    @Transactional
    public String installForUser(
            String pluginId,
            String userId,
            PluginInstallRequest request
    ) {
        PluginVersionRow version = requireInstallable(pluginId, request.version(), userId, null);
        validateGrants(version, request.grantedPermissions());
        String installationId = UUID.randomUUID().toString();
        mapper.installForUser(
                installationId,
                userId,
                pluginId,
                version.versionId(),
                json(request.config() == null ? Map.of() : request.config()),
                json(request.grantedPermissions()),
                policy(request.autoUpdatePolicy())
        );
        return installationId;
    }

    @Transactional
    public String installForSpace(
            String pluginId,
            String userId,
            String spaceId,
            PluginInstallRequest request
    ) {
        requireUuid(spaceId, "安装到空间时必须提供有效 X-Space-Id");
        if (!platformAuthorizationClient.canManagePlugins(userId, spaceId)) {
            throw new PluginApiException(
                    "SPACE-PLUGIN-MANAGE-DENIED",
                    HttpStatus.FORBIDDEN,
                    "当前账号无权管理该空间的插件"
            );
        }
        PluginVersionRow version = requireInstallable(
                pluginId, request.version(), userId, spaceId
        );
        validateGrants(version, request.grantedPermissions());
        String installationId = UUID.randomUUID().toString();
        mapper.installForSpace(
                installationId,
                spaceId,
                userId,
                pluginId,
                version.versionId(),
                json(request.config() == null ? Map.of() : request.config()),
                json(request.grantedPermissions()),
                policy(request.autoUpdatePolicy())
        );
        return installationId;
    }

    public void setUserInstallationEnabled(
            String installationId,
            String userId,
            boolean enabled
    ) {
        if (mapper.updateUserInstallation(installationId, userId, enabled) != 1) {
            throw notFound();
        }
    }

    public void uninstallForUser(String installationId, String userId) {
        if (mapper.uninstallForUser(installationId, userId) != 1) {
            throw notFound();
        }
    }

    @Transactional
    public void setSpaceInstallationEnabled(
            String installationId,
            String userId,
            String spaceId,
            boolean enabled
    ) {
        requireUuid(installationId, "安装记录标识无效");
        requireUuid(spaceId, "空间标识无效");
        if (!platformAuthorizationClient.canManagePlugins(userId, spaceId)
                || mapper.updateSpaceInstallation(installationId, spaceId, enabled) != 1) {
            throw new PluginApiException(
                    "SPACE-PLUGIN-MANAGE-DENIED",
                    HttpStatus.FORBIDDEN,
                    "当前账号无权管理该空间插件"
            );
        }
    }

    @Transactional
    public void uninstallForSpace(String installationId, String userId, String spaceId) {
        requireUuid(installationId, "安装记录标识无效");
        requireUuid(spaceId, "空间标识无效");
        if (!platformAuthorizationClient.canManagePlugins(userId, spaceId)
                || mapper.uninstallForSpace(installationId, spaceId) != 1) {
            throw new PluginApiException(
                    "SPACE-PLUGIN-MANAGE-DENIED",
                    HttpStatus.FORBIDDEN,
                    "当前账号无权卸载该空间插件"
            );
        }
    }

    private PluginRow requireOwned(String pluginId, String userId) {
        requireUuid(pluginId, "插件标识无效");
        requireUuid(userId, "用户身份无效");
        PluginRow row = mapper.findOwned(pluginId, userId);
        if (row == null) {
            throw notFound();
        }
        return row;
    }

    private PluginVersionRow requireOwnedVersion(
            String pluginId,
            String userId,
            String version
    ) {
        PluginVersionRow row = mapper.findOwnedVersion(pluginId, userId, version);
        if (row == null) {
            throw notFound();
        }
        return row;
    }

    private PluginVersionRow requireInstallable(
            String pluginId,
            String version,
            String userId,
            String spaceId
    ) {
        PluginVersionRow row = mapper.findInstallableVersion(
                pluginId, version, userId, blankToNull(spaceId)
        );
        if (row == null) {
            throw notFound();
        }
        return row;
    }

    private void validateEntrypoints(
            List<String> declaredPermissions,
            List<PluginEntrypointSpec> entrypoints
    ) {
        Set<String> declared = new HashSet<>(declaredPermissions);
        Set<String> identities = new HashSet<>();
        for (PluginEntrypointSpec entrypoint : safeList(entrypoints)) {
            validatePermissionSet(entrypoint.permissions());
            if (!declared.containsAll(entrypoint.permissions())) {
                throw invalid("入口函数权限必须是插件版本声明权限的子集");
            }
            if (!identities.add(entrypoint.event() + ":" + entrypoint.functionName())) {
                throw invalid("同一事件下的入口函数不能重复");
            }
            if (READY_EVENT.equals(entrypoint.event())
                    && !entrypoint.permissions().contains(WRITE_PRE_ACTIVATION)) {
                throw invalid("内容预处理入口必须声明激活前写入权限");
            }
            if (AVAILABLE_EVENT.equals(entrypoint.event())
                    && entrypoint.permissions().contains(WRITE_PRE_ACTIVATION)) {
                throw invalid("文件可用后的入口禁止声明原始内容写入权限");
            }
        }
    }

    private void validateGrants(PluginVersionRow version, List<String> granted) {
        validatePermissionSet(granted);
        Set<String> declared = new HashSet<>(readStringList(version.permissionConfig()));
        if (!declared.containsAll(granted)) {
            throw new PluginApiException(
                    "PLG-PERMISSION-NOT-GRANTED",
                    HttpStatus.FORBIDDEN,
                    "安装授权不能超出插件版本声明的权限"
            );
        }
    }

    private static void validatePermissionSet(List<String> permissions) {
        if (permissions == null || !SUPPORTED_PERMISSIONS.containsAll(permissions)) {
            throw invalid("插件包含平台未开放的权限");
        }
    }

    private static void validateDistributionTargets(
            String pluginType,
            List<String> platforms,
            List<String> clientTypes
    ) {
        if (platforms == null || !SUPPORTED_PLATFORMS.containsAll(platforms)) {
            throw invalid("插件包含不受支持的运行平台");
        }
        if (clientTypes == null || !SUPPORTED_CLIENT_TYPES.containsAll(clientTypes)) {
            throw invalid("插件包含不受支持的客户端类型");
        }
        if ("LOCAL_PLUGIN".equals(pluginType)
                && platforms.stream().anyMatch("web"::equals)
                && !clientTypes.contains("web")) {
            throw invalid("Web 平台本地插件必须声明 web 客户端类型");
        }
    }

    private static int parseMajor(String version) {
        try {
            return Integer.parseInt(version.split("\\.", 2)[0]);
        } catch (RuntimeException exception) {
            throw invalid("插件版本必须以数字 major 开头");
        }
    }

    private static void validateRuntime(String pluginType, String runtime) {
        boolean valid = switch (pluginType) {
            case "CLOUD_PLUGIN" -> "PYTHON_3_11".equals(runtime);
            case "LOCAL_PLUGIN" -> "JAVASCRIPT_ES2022".equals(runtime);
            case "WORKFLOW_PLUGIN" -> "PCD_WORKFLOW_V1".equals(runtime);
            default -> false;
        };
        if (!valid) {
            throw invalid("插件类型与运行时不匹配");
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateManifestIdentity(
            String manifestYaml,
            String pluginId,
            String version,
            String pluginType
    ) {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(10);
        options.setNestingDepthLimit(20);
        options.setCodePointLimit(1024 * 1024);
        Object loaded = new Yaml(new SafeConstructor(options)).load(manifestYaml);
        if (!(loaded instanceof Map<?, ?> root)
                || !(root.get("plugin") instanceof Map<?, ?> plugin)) {
            throw invalid("manifest.yaml 缺少 plugin 节点");
        }
        if (!pluginId.equals(String.valueOf(plugin.get("id")))
                || !version.equals(String.valueOf(plugin.get("version")))
                || !pluginType.equals(String.valueOf(plugin.get("type")))) {
            throw invalid("manifest.yaml 的插件 ID、类型或版本与草稿不一致");
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("数据库中的插件权限快照损坏", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("插件配置序列化失败", exception);
        }
    }

    private static String policy(String value) {
        return value == null || value.isBlank() ? "MANUAL" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String textOr(Object value, String fallback) {
        return value == null || "null".equals(value) ? fallback : String.valueOf(value);
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private static void requireUuid(String value, String message) {
        try {
            UUID.fromString(value);
        } catch (Exception exception) {
            throw new PluginApiException(
                    "PLG-REQUEST-INVALID", HttpStatus.UNPROCESSABLE_ENTITY, message
            );
        }
    }

    private static PluginApiException invalid(String message) {
        return new PluginApiException(
                "PLG-REQUEST-INVALID", HttpStatus.UNPROCESSABLE_ENTITY, message
        );
    }

    private static PluginApiException conflict(String code, String message) {
        return new PluginApiException(code, HttpStatus.CONFLICT, message);
    }

    private static PluginApiException notFound() {
        return new PluginApiException(
                "PLG-NOT-FOUND", HttpStatus.NOT_FOUND, "插件或版本不存在"
        );
    }
}
