package org.project.privateclouddiskgatewayservice.config.nacos;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Nacos 服务发现自动配置
 * <p>
 * 当 {@code nacos.discovery.server-addr} 配置存在时自动激活。
 * 提供：
 * <ul>
 *   <li>{@link NamingService} — Nacos 原生命名服务客户端</li>
 *   <li>{@link ReactiveDiscoveryClient} — Spring Cloud 服务发现接口</li>
 *   <li>{@link NacosServiceRegistry} — 服务自动注册/注销</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(NacosDiscoveryProperties.class)
@ConditionalOnProperty(name = "nacos.discovery.server-addr")
public class NacosDiscoveryAutoConfiguration {

    /**
     * 创建 Nacos NamingService 客户端
     */
    @Bean
    public NamingService nacosNamingService(NacosDiscoveryProperties properties) throws NacosException {
        Properties nacosProps = new Properties();
        nacosProps.setProperty("serverAddr", properties.getServerAddr());
        nacosProps.setProperty("namespace", properties.getNamespace() != null ? properties.getNamespace() : "");

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            nacosProps.setProperty("username", properties.getUsername());
        }
        if (properties.getPassword() != null && !properties.getPassword().isBlank()) {
            nacosProps.setProperty("password", properties.getPassword());
        }
        if (properties.getLogLevel() != null && !properties.getLogLevel().isBlank()) {
            nacosProps.setProperty("logLevel", properties.getLogLevel());
        }

        log.info("创建 Nacos NamingService: serverAddr={} namespace={}",
                properties.getServerAddr(),
                properties.getNamespace() != null && !properties.getNamespace().isEmpty()
                        ? properties.getNamespace() : "public");

        return NacosFactory.createNamingService(nacosProps);
    }

    /**
     * 注册 ReactiveDiscoveryClient
     */
    @Bean
    public ReactiveDiscoveryClient nacosReactiveDiscoveryClient(
            NamingService namingService,
            NacosDiscoveryProperties properties) {
        return new NacosReactiveDiscoveryClient(namingService, properties.getGroup());
    }

    /**
     * 注册 NacosServiceRegistry 并自动注册服务
     */
    @Bean
    public NacosServiceRegistry nacosServiceRegistry(
            NamingService namingService,
            NacosDiscoveryProperties properties) {

        NacosServiceRegistry registry = new NacosServiceRegistry(namingService, properties);

        // 在注册 Bean 的同时注册服务到 Nacos
        // 使用异步方式避免阻塞启动
        new Thread(() -> {
            try {
                // 等待 Spring 完全启动
                Thread.sleep(3000);
                registry.register();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "nacos-register-thread").start();

        return registry;
    }
}