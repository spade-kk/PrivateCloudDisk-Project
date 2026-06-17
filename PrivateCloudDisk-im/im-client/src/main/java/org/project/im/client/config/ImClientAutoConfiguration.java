package org.project.im.client.config;

import lombok.extern.slf4j.Slf4j;
import org.project.im.client.ImClient;
import org.project.im.client.impl.ImClientHttpImpl;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * IM Client 自动配置
 * <p>
 * 当引入 im-client 依赖后，自动装配 ImClient Bean。
 * 业务模块只需注入 {@code @Autowired private ImClient imClient;} 即可使用。
 * </p>
 * <p>
 * 可通过配置 {@code im.client.enabled=false} 禁用自动装配。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "im.client", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class ImClientAutoConfiguration {

    /**
     * 创建 RestTemplate
     */
    @Bean
    @ConditionalOnMissingBean
    public RestTemplate imRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    /**
     * 创建 ImClient 实现
     */
    @Bean
    @ConditionalOnMissingBean
    public ImClient imClient(RestTemplate imRestTemplate,
                             RabbitTemplate rabbitTemplate,
                             StringRedisTemplate stringRedisTemplate) {
        log.info("IM Client 自动装配完成");
        return new ImClientHttpImpl(imRestTemplate, rabbitTemplate, stringRedisTemplate);
    }
}