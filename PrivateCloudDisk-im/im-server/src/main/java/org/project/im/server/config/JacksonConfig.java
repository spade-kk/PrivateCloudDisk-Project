package org.project.im.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 注册 Java 8 date/time 模块，支持 LocalDateTime 等类型的序列化/反序列化
        mapper.registerModule(new JavaTimeModule());
        // 禁用将日期序列化为时间戳数组（[2026, 7, 16, ...]），改为 ISO-8601 字符串
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
