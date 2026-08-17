package org.project.plugin.service;

import lombok.RequiredArgsConstructor;
import org.project.plugin.client.ClientRegistrationClient;
import org.project.plugin.client.PlatformAuthorizationClient;
import org.project.plugin.config.PluginProperties;
import org.project.plugin.exception.PluginApiException;
import org.project.plugin.model.ClientBindingResponse;
import org.project.plugin.model.LocalPluginDistributionItem;
import org.project.plugin.model.LocalPluginDistributionRow;
import org.project.plugin.model.PluginDownloadGrantRow;
import org.project.plugin.repository.PluginManagementMapper;
import org.project.plugin.storage.PluginPackageHandle;
import org.project.plugin.storage.PluginStoragePort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 本地插件按用户、空间、设备、平台四重边界生成短时分发清单。 */
@Service
@RequiredArgsConstructor
public class LocalPluginDistributionService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final PluginManagementMapper mapper;
    private final ClientRegistrationClient clientRegistrationClient;
    private final PlatformAuthorizationClient platformAuthorizationClient;
    private final PluginStoragePort storage;
    private final PluginProperties properties;

    @Transactional
    public List<LocalPluginDistributionItem> distribute(
            String userId,
            String spaceId,
            String clientId,
            String platform,
            String clientType,
            String appVersion
    ) {
        requireUuid(userId);
        if (spaceId != null && !spaceId.isBlank()) {
            requireUuid(spaceId);
        }
        ClientBindingResponse binding =
                clientRegistrationClient.requireBinding(clientId, userId);
        requireBindingMatch(binding, platform, clientType, appVersion);
        if (!platformAuthorizationClient.canExecuteWorkflowCapability(userId, spaceId)) {
            throw forbidden("当前用户无权访问所选空间");
        }
        long ttl = Math.max(30, Math.min(properties.localDownloadGrantTtlSeconds(), 300));
        Instant expiresAt = Instant.now().plusSeconds(ttl);
        LocalDateTime expiresAtDb = LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC);
        return mapper.listLocalDistributions(
                        userId,
                        blankToNull(spaceId),
                        binding.platform().toLowerCase(Locale.ROOT),
                        binding.clientType().toLowerCase(Locale.ROOT)
                ).stream()
                .map(row -> issueGrant(row, userId, blankToNull(spaceId), clientId,
                        expiresAt, expiresAtDb))
                .toList();
    }

    @Transactional
    public PluginPackageHandle consume(
            String opaqueToken,
            String userId,
            String clientId
    ) {
        requireUuid(userId);
        clientRegistrationClient.requireBinding(clientId, userId);
        String digest = sha256(opaqueToken);
        if (mapper.consumeDownloadGrant(digest, userId, clientId) != 1) {
            throw new PluginApiException(
                    "PLG-DOWNLOAD-GRANT-INVALID",
                    HttpStatus.GONE,
                    "插件下载授权已过期或已使用，请刷新插件列表"
            );
        }
        PluginDownloadGrantRow grant =
                mapper.findConsumedDownloadGrant(digest, userId, clientId);
        if (grant == null) {
            throw new PluginApiException(
                    "PLG-PACKAGE-NOT-FOUND",
                    HttpStatus.NOT_FOUND,
                    "插件版本不存在或已撤销"
            );
        }
        try {
            return storage.openImmutable(
                    grant.objectKey(), grant.packageSha256(), grant.packageSize()
            );
        } catch (PluginApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PluginApiException(
                    "PLG-PACKAGE-STORAGE-FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "无法读取插件包"
            );
        }
    }

    private LocalPluginDistributionItem issueGrant(
            LocalPluginDistributionRow row,
            String userId,
            String spaceId,
            String clientId,
            Instant expiresAt,
            LocalDateTime expiresAtDb
    ) {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        mapper.insertDownloadGrant(
                UUID.randomUUID().toString(),
                sha256(token),
                row.versionId(),
                userId,
                spaceId,
                clientId,
                expiresAtDb
        );
        return new LocalPluginDistributionItem(
                row.installationId(),
                row.installationScope(),
                row.pluginId(),
                row.pluginName(),
                row.pluginDescription(),
                row.versionId(),
                row.version(),
                row.runtime(),
                row.entrypoint(),
                row.permissionConfig(),
                row.configJson(),
                row.packageSha256(),
                row.packageSize(),
                row.signature() == null ? "" : row.signature().replaceAll("\\s+", ""),
                row.signingKeyId(),
                "/api/v1/plugins/local/packages/" + token,
                expiresAt
        );
    }

    private static void requireBindingMatch(
            ClientBindingResponse binding,
            String platform,
            String clientType,
            String appVersion
    ) {
        boolean matches = binding != null
                && "active".equalsIgnoreCase(binding.status())
                && binding.platform().equalsIgnoreCase(platform)
                && binding.clientType().equalsIgnoreCase(clientType)
                && binding.appVersion().equals(appVersion);
        if (!matches) {
            throw forbidden("客户端上报信息与已绑定身份不一致");
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static void requireUuid(String value) {
        try {
            UUID.fromString(value);
        } catch (Exception exception) {
            throw new PluginApiException(
                    "PLG-REQUEST-INVALID",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "用户或空间标识无效"
            );
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static PluginApiException forbidden(String message) {
        return new PluginApiException(
                "PLG-CLIENT-DISTRIBUTION-FORBIDDEN",
                HttpStatus.FORBIDDEN,
                message
        );
    }
}
