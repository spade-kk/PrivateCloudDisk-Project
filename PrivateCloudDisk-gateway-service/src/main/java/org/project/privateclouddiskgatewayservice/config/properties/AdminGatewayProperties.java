package org.project.privateclouddiskgatewayservice.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "gateway.admin")
public class AdminGatewayProperties {

    /**
     * 管理员 API 前缀
     */
    private String pathPrefix = "/api/v1/business/admin/";

    /**
     * 管理员接口 IP 白名单
     */
    private List<String> allowedIps = List.of("127.0.0.1");

    /**
     * 管理员密钥（用于验证请求来源）
     * 生产环境请通过环境变量或密钥管理服务注入
     */
    private String adminKey = "changeme-admin-key";

    /**
     * 管理员登录接口路径（相对路径，不含前缀）
     */
    private String loginPath = "/api/v1/business/admin/auth/login";

    /**
     * 是否启用管理员安全过滤
     */
    private boolean enabled = true;
}