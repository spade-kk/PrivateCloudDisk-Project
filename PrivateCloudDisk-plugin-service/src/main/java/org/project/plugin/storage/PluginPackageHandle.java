package org.project.plugin.storage;

import java.io.IOException;
import java.io.InputStream;

/** 内部包流句柄；调用方负责关闭输入流。 */
public record PluginPackageHandle(
        InputStream inputStream,
        long size,
        String sha256
) implements AutoCloseable {
    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
