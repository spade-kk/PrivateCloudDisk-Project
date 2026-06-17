package org.project.im.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * WebRTC 配置类
 * <p>
 * 从 application.yml 读取 STUN/TURN 服务器配置、通话参数等。
 * 支持企业级 ICE 服务器配置，确保各种网络环境下的连通性。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "webrtc")
public class WebRTCConfig {

    /** ICE 服务器配置 */
    private IceConfig ice = new IceConfig();

    /** 通话邀请超时（秒） */
    private int callInviteTimeout = 60;

    /** 空闲通话清理（分钟） */
    private int idleCallCleanup = 30;

    /** 自适应编码参数 */
    private AdaptiveEncoderConfig adaptiveEncoder = new AdaptiveEncoderConfig();

    @Data
    public static class IceConfig {
        /** STUN 服务器列表 */
        private List<String> stunServers = new ArrayList<>();

        /** TURN 服务器配置列表 */
        private List<TurnServerConfig> turnServers = new ArrayList<>();

        /** ICE 传输策略 */
        private String transportPolicy = "all";

        /** ICE Candidate 池大小 */
        private int candidatePoolSize = 2;
    }

    @Data
    public static class TurnServerConfig {
        /** TURN 服务器 URL 列表 */
        private List<String> urls = new ArrayList<>();

        /** 用户名 */
        private String username;

        /** 密码/凭证 */
        private String credential;
    }

    @Data
    public static class AdaptiveEncoderConfig {
        /** 参数切换冷却时间（毫秒） */
        private long switchCooldownMs = 3000;

        /** 历史质量快照保留数量 */
        private int historySize = 10;

        /** 网络质量上报间隔（毫秒） */
        private int qualityReportInterval = 2000;
    }
}