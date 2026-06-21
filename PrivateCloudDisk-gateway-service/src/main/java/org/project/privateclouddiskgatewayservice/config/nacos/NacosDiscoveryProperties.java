package org.project.privateclouddiskgatewayservice.config.nacos;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Nacos 服务发现配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "nacos.discovery")
public class NacosDiscoveryProperties {

    /** Nacos 服务器地址，如 127.0.0.1:8848 */
    private String serverAddr = "127.0.0.1:8848";

    /** 命名空间 ID（public 命名空间留空） */
    private String namespace = "";

    /** 分组名 */
    private String group = "DEFAULT_GROUP";

    /** 集群名 */
    private String clusterName = "DEFAULT";

    /** 当前服务名 */
    private String service = "PrivateCloudDisk-gateway-service";

    /** 当前服务 IP（自动检测） */
    private String ip;

    /** 当前服务端口 */
    private int port = 8080;

    /** 服务权重 */
    private float weight = 1.0f;

    /** 是否启用服务注册 */
    private boolean registerEnabled = true;

    /** 元数据 */
    private java.util.Map<String, String> metadata = new java.util.HashMap<>();

    /** 用户名 */
    private String username;

    /** 密码 */
    private String password;

    /** 日志级别 */
    private String logLevel;
}