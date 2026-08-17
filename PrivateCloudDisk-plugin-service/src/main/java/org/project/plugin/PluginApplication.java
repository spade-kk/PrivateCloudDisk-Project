package org.project.plugin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Plugin Service 启动入口。 */
@EnableScheduling
@MapperScan("org.project.plugin.repository")
@SpringBootApplication
public class PluginApplication {
    public static void main(String[] args) {
        SpringApplication.run(PluginApplication.class, args);
    }
}
