package org.project.privateclouddiskgatewayservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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

@SpringBootApplication
public class PrivateCloudDiskGatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrivateCloudDiskGatewayServiceApplication.class, args);
    }

}
