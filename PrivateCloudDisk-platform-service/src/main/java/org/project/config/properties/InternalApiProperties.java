package org.project.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * 内部接口配置属性
 */
@ConfigurationProperties(prefix = "internal.api")
public class InternalApiProperties {

    // 允许访问的IP列表（默认包含本机IP）
    private List<String> allowedIps = new ArrayList<>(List.of(
            "127.0.0.1",    // 本机IPv4
            "localhost",    // 本地主机名（可能需要DNS解析）
            "0:0:0:0:0:0:0:1" // 本机IPv6
    ));

    // 需要保护的内部接口路径（例如文件存储服务调用的路径）
    private List<String> protectedPaths = List.of(
            "/business/internal/**"   // 文件、插件和工作流内部接口
    );

    /** 服务间共享凭证；生产环境必须通过 Secret 注入，后续可平滑替换为 mTLS 身份。 */
    private String serviceToken = "";

    // 排除拦截的路径（可选）
    private List<String> excludePaths = new ArrayList<>();

    // getter和setter
    public List<String> getAllowedIps() {
        return allowedIps;
    }

    public void setAllowedIps(List<String> allowedIps) {
        this.allowedIps = allowedIps;
    }

    public List<String> getProtectedPaths() {
        return protectedPaths;
    }

    public void setProtectedPaths(List<String> protectedPaths) {
        this.protectedPaths = protectedPaths;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }
}
