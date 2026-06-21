package org.project.privateclouddiskgatewayservice.config.nacos;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Nacos 服务自动注册器
 * <p>
 * 在 Spring 容器启动后自动将网关注册到 Nacos，在容器销毁时自动注销。
 * 注册信息包括健康检查端点，Nacos 会定期探测 /actuator/health 来判断服务状态。
 */
@Slf4j
public class NacosServiceRegistry {

    private final NamingService namingService;
    private final NacosDiscoveryProperties properties;
    private boolean registered = false;

    @Value("${server.port:8080}")
    private int serverPort;

    public NacosServiceRegistry(NamingService namingService, NacosDiscoveryProperties properties) {
        this.namingService = namingService;
        this.properties = properties;
    }

    /**
     * 注册服务到 Nacos
     */
    public void register() {
        if (!properties.isRegisterEnabled()) {
            log.info("Nacos 服务注册已禁用");
            return;
        }

        try {
            String ip = resolveIp();
            int port = properties.getPort() > 0 ? properties.getPort() : serverPort;

            Instance instance = new Instance();
            instance.setIp(ip);
            instance.setPort(port);
            instance.setWeight(properties.getWeight());
            instance.setClusterName(properties.getClusterName());
            instance.setEnabled(true);
            instance.setHealthy(true);
            instance.setEphemeral(true); // 临时实例，断开后自动移除

            // 元数据
            instance.getMetadata().putAll(properties.getMetadata());
            instance.getMetadata().put("management.port", String.valueOf(port));
            instance.getMetadata().put("management.context-path", "/actuator");

            namingService.registerInstance(properties.getService(), properties.getGroup(), instance);

            registered = true;
            log.info("Nacos 服务注册成功: service={} ip={} port={} group={} cluster={}",
                    properties.getService(), ip, port, properties.getGroup(), properties.getClusterName());

        } catch (NacosException e) {
            log.error("Nacos 服务注册失败: serverAddr={} service={}",
                    properties.getServerAddr(), properties.getService(), e);
        }
    }

    /**
     * 从 Nacos 注销服务
     */
    @PreDestroy
    public void deregister() {
        if (!registered) return;

        try {
            String ip = resolveIp();
            int port = properties.getPort() > 0 ? properties.getPort() : serverPort;

            namingService.deregisterInstance(properties.getService(), properties.getGroup(), ip, port);
            log.info("Nacos 服务注销成功: service={} ip={} port={}",
                    properties.getService(), ip, port);
        } catch (NacosException e) {
            log.error("Nacos 服务注销失败: service={}", properties.getService(), e);
        }
    }

    private String resolveIp() {
        if (properties.getIp() != null && !properties.getIp().isBlank()) {
            return properties.getIp();
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("无法获取本机 IP，使用 127.0.0.1");
            return "127.0.0.1";
        }
    }
}