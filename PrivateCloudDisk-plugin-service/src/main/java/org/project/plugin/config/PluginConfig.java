package org.project.plugin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** Plugin Service 基础依赖配置。 */
@Configuration
@EnableConfigurationProperties(PluginProperties.class)
public class PluginConfig {
    @Bean
    RestClient.Builder internalRestClientBuilder(PluginProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .defaultHeader("X-PCD-Service-Token", properties.internalServiceToken())
                .requestFactory(factory);
    }
}

