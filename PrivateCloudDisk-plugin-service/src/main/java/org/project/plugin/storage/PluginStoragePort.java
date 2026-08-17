package org.project.plugin.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** 插件包不可变存储端口；S3/本地后端必须遵守相同哈希与不可覆盖语义。 */
public interface PluginStoragePort {
    StoredPluginPackage putImmutable(
            String pluginType,
            UUID pluginId,
            String version,
            String entrypoint,
            InputStream inputStream
    ) throws IOException;

    /** 仅供校验服务读取已封存包内入口源码，不暴露宿主物理路径。 */
    String readTextEntry(String objectKey, String entrypoint, long maxBytes) throws IOException;

    /** Runtime 通过服务间认证流式获取不可变包。 */
    PluginPackageHandle openImmutable(
            String objectKey,
            String expectedSha256,
            long expectedSize
    ) throws IOException;

    /** 删除未发布草稿旧版本的物理包；发布后的 immutable 包禁止调用此能力。 */
    void deleteObject(String objectKey) throws IOException;
}
