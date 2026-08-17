package org.project.privateclouddiskgatewayservice.filter.global;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebCrypto 本地插件客户端签名兼容测试。
 *
 * <p>需求对应：Web 运行时产生 P-256 P1363 签名，网关必须在不改变 macOS DER 协议的前提下验证。
 */
class DeviceIdentityFilterSignatureTest {

    @Test
    void shouldConvertP1363SignatureToDerAndVerify() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var pair = generator.generateKeyPair();
        byte[] payload = "GET\n/api/v1/plugins/local/distribution\nclient\n1\nnonce\n"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] derSignature = sign(payload, pair.getPrivate());
        byte[] p1363Signature = derToP1363(derSignature);
        byte[] normalized = DeviceIdentityFilter.normalizeEcdsaSignature(p1363Signature);

        var verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(pair.getPublic());
        verifier.update(payload);
        assertTrue(verifier.verify(normalized));
    }

    @Test
    void shouldKeepExistingDerSignatureUnchanged() {
        byte[] existing = new byte[]{0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x02};
        assertArrayEquals(existing, DeviceIdentityFilter.normalizeEcdsaSignature(existing));
    }

    private static byte[] sign(byte[] payload, java.security.PrivateKey privateKey) throws Exception {
        var signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign((ECPrivateKey) privateKey);
        signature.update(payload);
        return signature.sign();
    }

    private static byte[] derToP1363(byte[] der) {
        int cursor = 2;
        int rLength = der[cursor + 1] & 0xff;
        byte[] r = Arrays.copyOfRange(der, cursor + 2, cursor + 2 + rLength);
        cursor += 2 + rLength;
        int sLength = der[cursor + 1] & 0xff;
        byte[] s = Arrays.copyOfRange(der, cursor + 2, cursor + 2 + sLength);
        byte[] result = new byte[64];
        copyUnsigned(r, result, 0);
        copyUnsigned(s, result, 32);
        return result;
    }

    private static void copyUnsigned(byte[] source, byte[] destination, int offset) {
        int sourceOffset = source.length > 32 && source[0] == 0 ? 1 : 0;
        int length = source.length - sourceOffset;
        System.arraycopy(source, sourceOffset, destination, offset + 32 - length, length);
    }
}
