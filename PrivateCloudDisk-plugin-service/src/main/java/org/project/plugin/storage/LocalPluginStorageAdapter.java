package org.project.plugin.storage;

import lombok.RequiredArgsConstructor;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.project.plugin.config.PluginProperties;
import org.project.plugin.exception.PluginApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 本地不可变插件包存储，包含 Zip Slip、链接、压缩炸弹和危险文件拦截。 */
@Component
@RequiredArgsConstructor
public class LocalPluginStorageAdapter implements PluginStoragePort {
    private static final Set<String> FORBIDDEN_SUFFIXES = Set.of(
            ".exe", ".dll", ".so", ".dylib", ".class", ".jar", ".pyc", ".pyd"
    );
    private static final Set<String> FORBIDDEN_NAMES = Set.of(
            ".env", "id_rsa", "id_ed25519", "credentials", "credentials.json"
    );
    private static final long ENTRYPOINT_MAX_BYTES = 1024L * 1024L;

    private final PluginProperties properties;

    @Override
    public StoredPluginPackage putImmutable(
            String pluginType,
            UUID pluginId,
            String version,
            String entrypoint,
            InputStream inputStream
    ) throws IOException {
        if (!"local".equalsIgnoreCase(properties.storageBackend())) {
            throw new PluginApiException(
                    "PLG-STORAGE-BACKEND-UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "当前节点未启用本地插件存储后端"
            );
        }

        Path root = properties.storagePath().toAbsolutePath().normalize();
        Path quarantine = root.resolve("quarantine").resolve(UUID.randomUUID().toString());
        Files.createDirectories(quarantine);
        Path upload = quarantine.resolve("package.upload");
        MessageDigest digest = sha256();
        long packageBytes = copyBounded(inputStream, upload, digest, properties.packageMaxBytes());

        Inspection inspection;
        try {
            inspection = inspect(upload, entrypoint);
        } catch (RuntimeException | IOException exception) {
            Files.deleteIfExists(upload);
            Files.deleteIfExists(quarantine);
            throw exception;
        }

        String sha256 = HexFormat.of().formatHex(digest.digest());
        String typeDirectory = switch (pluginType) {
            case "CLOUD_PLUGIN" -> "cloud";
            case "LOCAL_PLUGIN" -> "local";
            case "WORKFLOW_PLUGIN" -> "workflow";
            default -> throw new PluginApiException(
                    "PLG-TYPE-INVALID", HttpStatus.UNPROCESSABLE_ENTITY, "插件类型不受支持"
            );
        };
        String shard = pluginId.toString().substring(0, 2);
        String objectKey = "packages/%s/%s/%s/%s/%s/plugin.pcdpkg".formatted(
                typeDirectory, shard, pluginId, version, sha256
        );
        Path destination = root.resolve(objectKey).normalize();
        ensureInside(root, destination);
        Files.createDirectories(destination.getParent());

        if (Files.exists(destination)) {
            if (Files.size(destination) != packageBytes) {
                throw new PluginApiException(
                        "PLG-VERSION-CONFLICT", HttpStatus.CONFLICT, "相同哈希的不可变包大小不一致"
                );
            }
        } else {
            fsync(upload);
            atomicMove(upload, destination);
            fsync(destination.getParent());
        }
        Files.deleteIfExists(upload);
        Files.deleteIfExists(quarantine);
        return new StoredPluginPackage(
                objectKey,
                sha256,
                packageBytes,
                inspection.fileCount(),
                inspection.expandedBytes(),
                inspection.manifestYaml(),
                inspection.entrypointSource()
        );
    }

    @Override
    public String readTextEntry(String objectKey, String entrypoint, long maxBytes)
            throws IOException {
        Path root = properties.storagePath().toAbsolutePath().normalize();
        Path archive = root.resolve(objectKey).normalize();
        ensureInside(root, archive);
        if (!Files.isRegularFile(archive)) {
            throw new PluginApiException(
                    "PLG-PACKAGE-NOT-FOUND", HttpStatus.NOT_FOUND, "插件包不存在"
            );
        }
        try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
            ZipArchiveEntry entry = zip.getEntry(normalizeEntryName(entrypoint));
            if (entry == null || entry.isDirectory()) {
                throw new PluginApiException(
                        "PLG-PACKAGE-INVALID",
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "插件入口脚本不存在"
                );
            }
            return readEntry(zip, entry, maxBytes);
        }
    }

    @Override
    public PluginPackageHandle openImmutable(
            String objectKey,
            String expectedSha256,
            long expectedSize
    ) throws IOException {
        Path configuredRoot = properties.storagePath().toAbsolutePath().normalize();
        Path root = configuredRoot.toRealPath();
        Path archive = configuredRoot.resolve(objectKey).normalize();
        ensureInside(configuredRoot, archive);
        Path realArchive = archive.toRealPath();
        ensureInside(root, realArchive);
        if (!Files.isRegularFile(realArchive)
                || Files.size(realArchive) != expectedSize) {
            throw new PluginApiException(
                    "PLG-PACKAGE-INTEGRITY-FAILED",
                    HttpStatus.CONFLICT,
                    "插件包完整性校验失败"
            );
        }
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(realArchive)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!MessageDigest.isEqual(
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                expectedSha256.toLowerCase(Locale.ROOT)
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new PluginApiException(
                    "PLG-PACKAGE-INTEGRITY-FAILED",
                    HttpStatus.CONFLICT,
                    "插件包哈希校验失败"
            );
        }
        return new PluginPackageHandle(
                Files.newInputStream(realArchive, StandardOpenOption.READ),
                expectedSize,
                actual
        );
    }

    @Override
    public void deleteObject(String objectKey) throws IOException {
        Path root = properties.storagePath().toAbsolutePath().normalize();
        Path object = root.resolve(objectKey).normalize();
        ensureInside(root, object);
        if (!objectKey.startsWith("packages/")) {
            throw new PluginApiException("PLG-STORAGE-PATH-INVALID", HttpStatus.UNPROCESSABLE_ENTITY, "插件对象路径不受支持");
        }
        Files.deleteIfExists(object);
    }

    private Inspection inspect(Path archive, String entrypoint) throws IOException {
        int fileCount = 0;
        long expandedBytes = 0;
        String manifest = null;
        String source = null;
        try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String name = normalizeEntryName(entry.getName());
                if (entry.isUnixSymlink() || isDangerousMode(entry.getUnixMode())) {
                    reject("插件包禁止包含符号链接、设备文件或特殊文件");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                fileCount++;
                if (fileCount > properties.packageMaxFiles()) {
                    reject("插件包文件数量超过上限");
                }
                long size = entry.getSize();
                if (size < 0) {
                    reject("插件包包含无法确定大小的文件");
                }
                expandedBytes = Math.addExact(expandedBytes, size);
                if (expandedBytes > properties.packageMaxExpandedBytes()) {
                    reject("插件包解压后体积超过上限");
                }
                rejectForbiddenName(name);
                if ("manifest.yaml".equals(name)) {
                    manifest = readEntry(zip, entry, 256 * 1024L);
                }
                if (name.equals(entrypoint)) {
                    source = readEntry(zip, entry, ENTRYPOINT_MAX_BYTES);
                }
            }
        } catch (ArithmeticException exception) {
            reject("插件包解压体积溢出");
        }
        if (manifest == null || manifest.isBlank()) {
            reject("插件包缺少 manifest.yaml");
        }
        if (source == null) {
            reject("插件包缺少声明的入口脚本");
        }
        return new Inspection(fileCount, expandedBytes, manifest, source);
    }

    private static String normalizeEntryName(String rawName) {
        String normalizedSeparators = rawName.replace('\\', '/');
        if (normalizedSeparators.startsWith("/")
                || normalizedSeparators.matches("^[A-Za-z]:.*")) {
            reject("插件包包含绝对路径");
        }
        Path path = Path.of(normalizedSeparators).normalize();
        String normalized = path.toString().replace('\\', '/');
        if (normalized.startsWith("../")
                || "..".equals(normalized)
                || normalized.isBlank()) {
            reject("插件包包含目录穿越路径");
        }
        return normalized;
    }

    private static boolean isDangerousMode(int unixMode) {
        int fileType = unixMode & 0170000;
        return fileType != 0 && fileType != 0100000 && fileType != 0040000;
    }

    private static void rejectForbiddenName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String basename = Path.of(lower).getFileName().toString();
        if (FORBIDDEN_NAMES.contains(basename)
                || FORBIDDEN_SUFFIXES.stream().anyMatch(lower::endsWith)
                || lower.contains("/.git/")) {
            reject("插件包包含禁止分发的凭证或可执行文件");
        }
    }

    private static String readEntry(ZipFile zip, ZipArchiveEntry entry, long maxBytes)
            throws IOException {
        try (InputStream input = zip.getInputStream(entry);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    reject("插件清单或入口脚本超过大小限制");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static long copyBounded(
            InputStream input,
            Path destination,
            MessageDigest digest,
            long maxBytes
    ) throws IOException {
        long total = 0;
        try (var output = Files.newOutputStream(
                destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new PluginApiException(
                            "PLG-PACKAGE-TOO-LARGE",
                            HttpStatus.PAYLOAD_TOO_LARGE,
                            "插件包超过上传大小限制"
                    );
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        return total;
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static void fsync(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                channel.force(true);
            }
            return;
        }
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void ensureInside(Path root, Path candidate) {
        if (!candidate.startsWith(root)) {
            reject("插件包存储路径越界");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256", exception);
        }
    }

    private static void reject(String message) {
        throw new PluginApiException(
                "PLG-PACKAGE-INVALID", HttpStatus.UNPROCESSABLE_ENTITY, message
        );
    }

    private record Inspection(
            int fileCount,
            long expandedBytes,
            String manifestYaml,
            String entrypointSource
    ) {
    }
}
