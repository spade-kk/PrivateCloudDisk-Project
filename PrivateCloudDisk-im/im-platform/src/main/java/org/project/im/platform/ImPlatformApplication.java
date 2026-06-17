package org.project.im.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IM 业务平台 — 启动类
 * <p>
 * 提供 IM 业务 HTTP REST API 服务，包括：
 * <ul>
 *   <li>会话管理（创建、查询、删除、置顶、免打扰）</li>
 *   <li>消息历史（分页查询、搜索、撤回）</li>
 *   <li>群组管理（创建、加入、退出、踢人、禁言、解散）</li>
 *   <li>联系人管理（添加、删除、黑名单、在线状态）</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@SpringBootApplication
@MapperScan("org.project.im.platform.mapper")
@EnableAsync
@EnableScheduling
public class ImPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImPlatformApplication.class, args);
    }
}