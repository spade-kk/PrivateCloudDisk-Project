package org.project.plugin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.project.plugin.config.PluginProperties;

import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginPackageSignerTest {
    @TempDir
    Path tempDir;

    @Test
    void 本地插件签名应能由公开公钥验证() throws Exception {
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        PluginProperties properties = new PluginProperties(
                tempDir,
                "local",
                1024,
                10,
                4096,
                "http://runtime",
                "http://platform",
                "http://workflow",
                "http://client",
                Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                "test-key",
                120,
                "internal-token"
        );
        PluginPackageSigner signer = new PluginPackageSigner(properties);
        String signature = signer.sign(
                "11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222",
                "1.0.0",
                "a".repeat(64),
                1024
        );

        var publicKey = KeyFactory.getInstance("Ed25519").generatePublic(
                new X509EncodedKeySpec(pair.getPublic().getEncoded())
        );
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(PluginPackageSigner.canonicalPayload(
                "11111111-1111-1111-1111-111111111111",
                "22222222-2222-2222-2222-222222222222",
                "1.0.0",
                "a".repeat(64),
                1024
        ));
        assertTrue(verifier.verify(Base64.getDecoder().decode(signature)));
    }
}
