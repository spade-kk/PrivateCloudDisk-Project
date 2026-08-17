package org.project.scheduler.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 启用 Scheduler 类型安全配置。 */
@Configuration
@EnableConfigurationProperties(SchedulerProperties.class)
public class SchedulerConfig {
}
