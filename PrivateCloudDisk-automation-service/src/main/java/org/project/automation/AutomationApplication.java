package org.project.automation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Automation Service 启动入口。
 *
 * <p>本服务只做事件 Inbox、触发匹配、Runtime 调度和 processed Outbox，
 * 不在进程内执行任何用户代码，也不直接读写 Platform/Plugin 数据库。</p>
 */
@EnableScheduling
@MapperScan("org.project.automation.repository")
@SpringBootApplication
public class AutomationApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutomationApplication.class, args);
    }
}

