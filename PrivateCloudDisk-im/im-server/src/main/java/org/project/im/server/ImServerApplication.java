package org.project.im.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * IM WebSocket 长连接推送服务 — 启动类
 * <p>
 * 基于 Netty 的高性能 WebSocket 服务，提供：
 * <ul>
 *   <li>WebSocket 长连接管理（建立、心跳、断开）</li>
 *   <li>消息实时推送（单聊、群聊、系统通知）</li>
 *   <li>用户在线状态管理（上线、下线、多端）</li>
 *   <li>离线消息同步</li>
 *   <li>连接限流与安全认证</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class ImServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImServerApplication.class, args);
    }
}