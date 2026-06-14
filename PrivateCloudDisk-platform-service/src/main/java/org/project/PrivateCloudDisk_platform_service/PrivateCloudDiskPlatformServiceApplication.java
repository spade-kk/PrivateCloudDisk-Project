package org.project.PrivateCloudDisk_platform_service;

import org.mybatis.spring.annotation.MapperScan;
import org.project.config.properties.AppMailProperties;
import org.project.config.properties.SmsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableCaching
@EnableConfigurationProperties({
        AppMailProperties.class,
        SmsProperties.class
})
@ComponentScan(basePackages = "org.project.*")
@MapperScan(basePackages = "org.project.mapper")
@EnableScheduling
@SpringBootApplication
public class PrivateCloudDiskPlatformServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrivateCloudDiskPlatformServiceApplication.class, args);
    }

}
