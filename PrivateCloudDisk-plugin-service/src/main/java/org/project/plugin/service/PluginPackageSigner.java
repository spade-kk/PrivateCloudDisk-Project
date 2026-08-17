package org.project.plugin.service;

import org.project.plugin.config.PluginProperties;
import org.project.plugin.exception.PluginApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** 使用平台 Ed25519 发布密钥签署本地插件不可变包摘要。 */
@Service
public class PluginPackageSigner {
    private final PluginProperties properties;

    public PluginPackageSigner(PluginProperties properties) {
        this.properties = properties;
    }

    public String sign(
            String pluginId,
            String versionId,
            String version,
            String packageSha256,
            long packageSize
    ) {
        requireConfigured();
        try {
            KeyFactory factory = KeyFactory.getInstance("Ed25519");
            PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(
                    Base64.getDecoder().decode(properties.signingPrivateKeyBase64())
            ));
            PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(
                    Base64.getDecoder().decode(properties.signingPublicKeyBase64())
            ));
            byte[] payload = canonicalPayload(
                    pluginId, versionId, version, packageSha256, packageSize
            );
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(payload);
            byte[] signed = signer.sign();

            // 配置错误时在发布阶段失败，绝不把无法由已公开公钥验证的包发给客户端。
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(payload);
            if (!verifier.verify(signed)) {
                throw configurationError();
            }
            return Base64.getEncoder().encodeToString(signed);
        } catch (PluginApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw configurationError();
        }
    }

    public String publicKeyBase64(String requestedKeyId) {
        requireConfigured();
        if (!properties.signingKeyId().equals(requestedKeyId)) {
            throw new PluginApiException(
                    "PLG-SIGNING-KEY-NOT-FOUND",
                    HttpStatus.NOT_FOUND,
                    "插件签名公钥不存在"
            );
        }
        return properties.signingPublicKeyBase64();
    }

    public String keyId() {
        requireConfigured();
        return properties.signingKeyId();
    }

    public static byte[] canonicalPayload(
            String pluginId,
            String versionId,
            String version,
            String packageSha256,
            long packageSize
    ) {
        return String.join(
                "\n",
                "PCD-PLUGIN-PACKAGE-V1",
                pluginId,
                versionId,
                version,
                packageSha256.toLowerCase(java.util.Locale.ROOT),
                Long.toString(packageSize)
        ).getBytes(StandardCharsets.UTF_8);
    }

    private void requireConfigured() {
        if (blank(properties.signingPrivateKeyBase64())
                || blank(properties.signingPublicKeyBase64())
                || blank(properties.signingKeyId())) {
            throw configurationError();
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static PluginApiException configurationError() {
        return new PluginApiException(
                "PLG-SIGNING-KEY-UNAVAILABLE",
                HttpStatus.SERVICE_UNAVAILABLE,
                "本地插件发布签名服务尚未正确配置"
        );
    }
}
