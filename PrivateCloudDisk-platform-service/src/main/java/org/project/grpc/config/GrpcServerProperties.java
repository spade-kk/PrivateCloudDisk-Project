package org.project.grpc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * gRPC 服务端配置属性。
 *
 * <p>从 application.properties 读取 grpc.server.* 配置项。
 * 绑定前缀: {@code grpc.server}
 *
 * <h3>配置示例</h3>
 * <pre>
 * grpc.server.port=9090
 * grpc.server.enabled=true
 * grpc.server.internal-auth-token=your-secret-token
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "grpc.server")
public class GrpcServerProperties {

    /** gRPC 服务端监听端口，默认 9090 */
    private int port = 9090;

    /** 是否启用 gRPC 服务端，默认 true */
    private boolean enabled = true;

    /** 内部服务认证 Token（用于服务间 mTLS 之外的额外认证层） */
    private String internalAuthToken = "";

    /** 是否启用 TLS/mTLS，默认 false（开发环境） */
    private boolean tlsEnabled = false;

    /** TLS 证书链文件路径（PEM 格式） */
    private String certChainPath = "";

    /** TLS 私钥文件路径（PEM 格式） */
    private String privateKeyPath = "";

    /** 是否启用反射服务（方便 grpcurl 等工具调试），默认 true */
    private boolean reflectionEnabled = true;
}