package org.project.workflow.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** 服务间 HTTP 客户端统一设置超时与内部身份。 */
@Configuration
@EnableConfigurationProperties({WorkflowProperties.class, CloudFlowRuntimeProperties.class})
public class WorkflowConfig {
    @Bean
    RestClient.Builder workflowRestClientBuilder(WorkflowProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .defaultHeader("X-PCD-Service-Token", properties.internalServiceToken())
                .requestFactory(factory);
    }
}
