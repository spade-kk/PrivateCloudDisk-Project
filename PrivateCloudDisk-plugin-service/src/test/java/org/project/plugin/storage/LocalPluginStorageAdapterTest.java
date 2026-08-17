package org.project.plugin.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.project.plugin.config.PluginProperties;
import org.project.plugin.exception.PluginApiException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPluginStorageAdapterTest {
    @TempDir
    Path tempDir;

    @Test
    void 合法插件包应按哈希不可变封存() throws Exception {
        LocalPluginStorageAdapter adapter = adapter();
        byte[] archive = zip(Map.of(
                "manifest.yaml", """
                        manifest_version: 1
                        plugin:
                          id: 11111111-1111-1111-1111-111111111111
                          type: CLOUD_PLUGIN
                          version: 1.0.0
                        """,
                "src/main.py", "def preprocess(context):\n    return context\n"
        ));

        StoredPluginPackage stored = adapter.putImmutable(
                "CLOUD_PLUGIN",
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "1.0.0",
                "src/main.py",
                new ByteArrayInputStream(archive)
        );

        assertEquals(2, stored.fileCount());
        assertEquals(64, stored.sha256().length());
        assertTrue(Files.isRegularFile(tempDir.resolve(stored.objectKey())));
        assertTrue(stored.entrypointSource().contains("preprocess"));
    }

    @Test
    void 包含目录穿越路径必须被拒绝() throws Exception {
        LocalPluginStorageAdapter adapter = adapter();
        byte[] archive = zip(Map.of(
                "manifest.yaml", "plugin: {}\n",
                "src/main.py", "print('ok')\n",
                "../escape.py", "print('bad')\n"
        ));

        PluginApiException exception = assertThrows(
                PluginApiException.class,
                () -> adapter.putImmutable(
                        "CLOUD_PLUGIN",
                        UUID.randomUUID(),
                        "1.0.0",
                        "src/main.py",
                        new ByteArrayInputStream(archive)
                )
        );
        assertEquals("PLG-PACKAGE-INVALID", exception.code());
        assertTrue(Files.notExists(tempDir.getParent().resolve("escape.py")));
    }

    @Test
    void 超过文件数限制的压缩包必须被拒绝() throws Exception {
        PluginProperties properties = properties(2);
        LocalPluginStorageAdapter adapter = new LocalPluginStorageAdapter(properties);
        byte[] archive = zip(Map.of(
                "manifest.yaml", "plugin: {}\n",
                "src/main.py", "print('ok')\n",
                "README.md", "test"
        ));

        assertThrows(
                PluginApiException.class,
                () -> adapter.putImmutable(
                        "CLOUD_PLUGIN",
                        UUID.randomUUID(),
                        "1.0.0",
                        "src/main.py",
                        new ByteArrayInputStream(archive)
                )
        );
    }

    private LocalPluginStorageAdapter adapter() {
        return new LocalPluginStorageAdapter(properties(16));
    }

    private PluginProperties properties(int maxFiles) {
        return new PluginProperties(
                tempDir,
                "local",
                1024 * 1024,
                maxFiles,
                2 * 1024 * 1024,
                "http://localhost:8090",
                "http://localhost:8081",
                "http://localhost:8087",
                "http://localhost:8089",
                "",
                "",
                "",
                120,
                "test-token"
        );
    }

    private static byte[] zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
