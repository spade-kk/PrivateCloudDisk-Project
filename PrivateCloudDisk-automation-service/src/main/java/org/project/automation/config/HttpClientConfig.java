package org.project.automation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** 内部服务 HTTP 客户端配置；服务 Token 只存在于控制面，不传入沙箱。 */
@Configuration
@EnableConfigurationProperties(AutomationProperties.class)
public class HttpClientConfig {

    @Bean
    RestClient.Builder internalRestClientBuilder(AutomationProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
//        requestFactory.setConnectTimeout(Duration.ofMillis(properties.triggerMatchTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.runtimeTimeoutSeconds() + 5L));
        return RestClient.builder()
                .defaultHeader("X-PCD-Service-Token", properties.internalServiceToken())
                .requestFactory(requestFactory);
    }
}
