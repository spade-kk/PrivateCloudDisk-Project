package org.project.privateclouddiskgatewayservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * PrivateCloudDisk API 网关 — 微服务统一入口
 *
 * <h2>架构定位</h2>
 * <p>
 * 本网关是所有客户端请求的<b>唯一入口</b>，负责：
 * <ul>
 *   <li><b>路由转发</b>：根据 URL 前缀将请求路由到对应的微服务</li>
 *   <li><b>认证鉴权</b>：JWT 验证，统一拦截未认证请求</li>
 *   <li><b>限流保护</b>：多维度（IP + 设备指纹 + 用户）限流</li>
 *   <li><b>响应标准化</b>：统一所有上游服务的错误响应格式</li>
 *   <li><b>跨域处理</b>：统一 CORS 配置</li>
 *   <li><b>熔断降级</b>：上游服务不可用时返回友好降级响应</li>
 *   <li><b>服务发现</b>：通过 Nacos 自动发现和负载均衡</li>
 * </ul>
 *
 * <h2>API 路由策略</h2>
 * <pre>{@code
 * 外部请求:  /api/v1/{service-name}/{resource-path}
 * 网关处理:  StripPrefix=2 (剥离 /api/v1)
 * 下游收到:  /{resource-path}
 * }</pre>
 *
 * <h2>过滤器执行链</h2>
 * <pre>
 * Order -100:  AuthGlobalFilter          — JWT 认证（最先执行，未认证直接拦截）
 * Order -95:   AdminGatewayFilter        — 管理员 API 鉴权
 * Order -90:   GatewayRateLimitFilter    — Redis 多维度限流
 * Order -50:   RequestLoggingFilter      — 请求日志
 * Order -2:    GlobalExceptionHandler    — 全局异常兜底
 * Order MAX:   UpstreamResponseNormalizerFilter — 上游响应标准化（最后执行）
 * </pre>
 */
@Slf4j
@SpringBootApplication
@EnableDiscoveryClient
public class PrivateCloudDiskGatewayServiceApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(
                PrivateCloudDiskGatewayServiceApplication.class, args);

        printStartupBanner(context);
    }

    /**
     * 启动成功后打印服务信息
     */
    private static void printStartupBanner(ConfigurableApplicationContext context) {
        Environment env = context.getEnvironment();

        String protocol = "http";
        String hostAddress;
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            hostAddress = "localhost";
        }

        String port = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String appName = env.getProperty("spring.application.name", "Gateway");
        String nacosAddr = env.getProperty("nacos.discovery.server-addr", "127.0.0.1:8848");

        boolean discoveryEnabled = Boolean.parseBoolean(
                env.getProperty("gateway.routes.use-service-discovery", "true"));

        log.info("""
                        
                        ============================================================
                        🚀 {} 启动成功！
                        ============================================================
                        📍 本地地址:    {}://{}:{}{}
                        📋 健康检查:    {}://{}:{}{}/actuator/health
                        🔍 服务发现:    {} (Nacos: {})
                        🔐 认证模式:    JWT (RS256)
                        🛡️ 限流存储:    Redis
                        ⚡ 响应标准化:  已启用 (UpstreamResponseNormalizerFilter)
                        ============================================================
                        """,
                appName,
                protocol, hostAddress, port, contextPath,
                protocol, hostAddress, port, contextPath,
                discoveryEnabled ? "Nacos (lb://)" : "直接 URL 模式",
                nacosAddr
        );
    }
}