package org.project.PrivateCloudDisk_platform_service;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;


@EnableCaching
@ComponentScan(basePackages = "org.project.*")
@MapperScan(basePackages = "org.project.mapper")
@SpringBootApplication
public class PrivateCloudDiskPlatformServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrivateCloudDiskPlatformServiceApplication.class, args);
    }

}
