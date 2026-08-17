package org.project.plugin.service;

import lombok.RequiredArgsConstructor;
import org.project.plugin.exception.PluginApiException;
import org.project.plugin.model.PluginVersionRow;
import org.project.plugin.repository.PluginManagementMapper;
import org.project.plugin.storage.PluginPackageHandle;
import org.project.plugin.storage.PluginStoragePort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;

/** Runtime 专用不可变包读取服务。 */
@Service
@RequiredArgsConstructor
public class InternalPackageService {
    private final PluginManagementMapper mapper;
    private final PluginStoragePort storage;

    public PluginPackageHandle open(String versionId) {
        PluginVersionRow version = mapper.findRunnableVersion(versionId);
        if (version == null || version.packageObjectKey() == null) {
            throw new PluginApiException(
                    "PLG-NOT-FOUND", HttpStatus.NOT_FOUND, "可执行插件版本不存在"
            );
        }
        try {
            return storage.openImmutable(
                    version.packageObjectKey(),
                    version.packageSha256(),
                    version.packageSize()
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
}
