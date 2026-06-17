package org.project.im.server.signaling.optimizer;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.project.im.server.signaling.model.CallSession;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业级 WebRTC 视频编码自适应优化器
 * <p>
 * 基于网络质量实时反馈，动态调整视频编码参数（码率/分辨率/帧率），
 * 类似腾讯会议的网络自适应策略，确保在各种网络条件下获得最佳通话体验。
 * <p>
 * 核心算法：
 * <ul>
 *   <li><b>网络质量评估</b>：综合 RTT、丢包率、抖动、带宽估算</li>
 *   <li><b>分层降级策略</b>：4 级网络质量对应 4 套编码参数</li>
 *   <li><b>平滑过渡</b>：避免频繁切换导致画面抖动</li>
 *   <li><b>内容感知</b>：屏幕共享时优先保证清晰度，人脸通话时优先保证流畅度</li>
 *   <li><b>上行/下行分离</b>：独立调节发送和接收策略</li>
 * </ul>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Service
public class AdaptiveVideoOptimizer {

    /** 每个参与者的历史质量快照 */
    private final ConcurrentHashMap<String, Deque<NetworkQualitySnapshot>> qualityHistory = new ConcurrentHashMap<>();

    /** 历史快照保留数量 */
    private static final int HISTORY_SIZE = 10;

    /** 参数切换冷却时间（毫秒），避免频繁切换 */
    private static final long SWITCH_COOLDOWN_MS = 3000;

    /** 每个参与者的上次切换时间 */
    private final ConcurrentHashMap<String, Long> lastSwitchTime = new ConcurrentHashMap<>();

    // ==================== 编码参数配置表 ====================

    /**
     * 按网络质量等级和内容类型定义的编码参数配置
     * <p>
     * 网络质量等级：
     * <ul>
     *   <li>0 - 优秀 (Excellent): 带宽充足，低延迟</li>
     *   <li>1 - 良好 (Good): 带宽稳定，偶尔波动</li>
     *   <li>2 - 一般 (Fair): 带宽受限，有一定丢包</li>
     *   <li>3 - 差 (Poor): 带宽严重不足</li>
     *   <li>4 - 极差 (Very Poor): 基本不可用，建议降级为纯语音</li>
     * </ul>
     */
    private static final EncoderParams[] VIDEO_PARAMS = {
            // 0 - 优秀：1080p 30fps 3Mbps
            EncoderParams.builder().quality(0).width(1920).height(1080).fps(30)
                    .maxBitrate(3000).minBitrate(2000).targetBitrate(2500)
                    .scaleResolutionDownBy(1.0).description("1080p 高清").build(),
            // 1 - 良好：720p 25fps 1.5Mbps
            EncoderParams.builder().quality(1).width(1280).height(720).fps(25)
                    .maxBitrate(1500).minBitrate(800).targetBitrate(1200)
                    .scaleResolutionDownBy(1.0).description("720p 标清").build(),
            // 2 - 一般：540p 20fps 800Kbps
            EncoderParams.builder().quality(2).width(960).height(540).fps(20)
                    .maxBitrate(800).minBitrate(400).targetBitrate(600)
                    .scaleResolutionDownBy(1.0).description("540p 流畅").build(),
            // 3 - 差：360p 15fps 400Kbps
            EncoderParams.builder().quality(3).width(640).height(360).fps(15)
                    .maxBitrate(400).minBitrate(150).targetBitrate(250)
                    .scaleResolutionDownBy(1.0).description("360p 省流").build(),
            // 4 - 极差：180p 10fps 150Kbps
            EncoderParams.builder().quality(4).width(320).height(180).fps(10)
                    .maxBitrate(150).minBitrate(50).targetBitrate(100)
                    .scaleResolutionDownBy(2.0).description("180p 极速").build(),
    };

    /** 屏幕共享专用参数（优先保证清晰度） */
    private static final EncoderParams[] SCREEN_SHARE_PARAMS = {
            // 0 - 优秀：1440p 15fps 4Mbps
            EncoderParams.builder().quality(0).width(2560).height(1440).fps(15)
                    .maxBitrate(4000).minBitrate(2000).targetBitrate(3000)
                    .scaleResolutionDownBy(1.0).description("2K 屏幕共享").build(),
            // 1 - 良好：1080p 10fps 2Mbps
            EncoderParams.builder().quality(1).width(1920).height(1080).fps(10)
                    .maxBitrate(2000).minBitrate(1000).targetBitrate(1500)
                    .scaleResolutionDownBy(1.0).description("1080p 屏幕共享").build(),
            // 2 - 一般：720p 8fps 1Mbps
            EncoderParams.builder().quality(2).width(1280).height(720).fps(8)
                    .maxBitrate(1000).minBitrate(500).targetBitrate(800)
                    .scaleResolutionDownBy(1.0).description("720p 屏幕共享").build(),
            // 3 - 差：540p 5fps 400Kbps
            EncoderParams.builder().quality(3).width(960).height(540).fps(5)
                    .maxBitrate(400).minBitrate(200).targetBitrate(300)
                    .scaleResolutionDownBy(1.5).description("540p 屏幕共享").build(),
            // 4 - 极差：360p 3fps 150Kbps
            EncoderParams.builder().quality(4).width(640).height(360).fps(3)
                    .maxBitrate(150).minBitrate(50).targetBitrate(100)
                    .scaleResolutionDownBy(2.0).description("360p 屏幕共享").build(),
    };

    // ==================== 公共 API ====================

    /**
     * 上报网络质量并获取推荐的编码参数
     *
     * @param callId   通话 ID
     * @param userId   用户 ID
     * @param snapshot 网络质量快照
     * @return 推荐的编码参数，如果无需调整则返回 null
     */
    public EncoderParams reportQualityAndGetParams(String callId, String userId,
                                                    NetworkQualitySnapshot snapshot) {
        // 记录质量快照
        Deque<NetworkQualitySnapshot> history = qualityHistory.computeIfAbsent(
                userId, k -> new ArrayDeque<>());
        history.addLast(snapshot);
        while (history.size() > HISTORY_SIZE) {
            history.pollFirst();
        }

        // 综合评估网络质量等级
        int qualityLevel = evaluateQuality(history);
        boolean isScreenShare = snapshot.isScreenShare();

        // 检查冷却时间
        Long lastSwitch = lastSwitchTime.get(userId);
        long now = System.currentTimeMillis();
        if (lastSwitch != null && (now - lastSwitch) < SWITCH_COOLDOWN_MS) {
            return null;
        }

        // 获取目标参数
        EncoderParams target = isScreenShare
                ? SCREEN_SHARE_PARAMS[qualityLevel]
                : VIDEO_PARAMS[qualityLevel];

        // 记录切换
        lastSwitchTime.put(userId, now);
        log.info("编码参数调整: userId={}, quality={}, resolution={}x{}, fps={}, bitrate={}kbps, desc={}",
                userId, qualityLevel,
                target.getWidth(), target.getHeight(),
                target.getFps(), target.getTargetBitrate(),
                target.getDescription());

        return target;
    }

    /**
     * 仅根据已有历史评估网络质量（不触发参数调整）
     */
    public int evaluateCurrentQuality(String userId) {
        Deque<NetworkQualitySnapshot> history = qualityHistory.get(userId);
        if (history == null || history.isEmpty()) {
            return 0; // 默认优秀
        }
        return evaluateQuality(history);
    }

    /**
     * 获取指定质量等级的编码参数
     */
    public EncoderParams getParamsForQuality(int qualityLevel, boolean isScreenShare) {
        if (qualityLevel < 0) qualityLevel = 0;
        if (qualityLevel > 4) qualityLevel = 4;
        return isScreenShare ? SCREEN_SHARE_PARAMS[qualityLevel] : VIDEO_PARAMS[qualityLevel];
    }

    /**
     * 建议降级为纯语音（网络极差时）
     */
    public boolean shouldDowngradeToVoice(String userId) {
        Deque<NetworkQualitySnapshot> history = qualityHistory.get(userId);
        if (history == null || history.isEmpty()) return false;
        // 连续 3 次评估为极差 → 建议降级
        int poorCount = 0;
        for (NetworkQualitySnapshot snapshot : history) {
            if (snapshot.getQualityLevel() >= 4) poorCount++;
            else poorCount = 0;
            if (poorCount >= 3) return true;
        }
        return false;
    }

    /**
     * 清理用户数据
     */
    public void cleanup(String userId) {
        qualityHistory.remove(userId);
        lastSwitchTime.remove(userId);
    }

    // ==================== 私有方法 ====================

    /**
     * 综合评估网络质量等级
     * <p>
     * 使用加权移动平均算法，综合 RTT、丢包率、抖动、带宽四个维度
     */
    private int evaluateQuality(Deque<NetworkQualitySnapshot> history) {
        if (history.isEmpty()) return 0;

        // 取最近 5 个快照计算加权平均（越新权重越高）
        List<NetworkQualitySnapshot> recent = new ArrayList<>(history);
        double totalWeight = 0;
        double weightedRtt = 0;
        double weightedLoss = 0;
        double weightedJitter = 0;
        double weightedBandwidth = 0;

        int count = Math.min(recent.size(), 5);
        for (int i = recent.size() - count; i < recent.size(); i++) {
            double weight = 1.0 + (i - (recent.size() - count)) * 0.5; // 线性递增权重
            NetworkQualitySnapshot s = recent.get(i);
            weightedRtt += s.getRtt() * weight;
            weightedLoss += s.getPacketLoss() * weight;
            weightedJitter += s.getJitter() * weight;
            weightedBandwidth += s.getEstimatedBandwidth() * weight;
            totalWeight += weight;
        }

        double avgRtt = weightedRtt / totalWeight;
        double avgLoss = weightedLoss / totalWeight;
        double avgJitter = weightedJitter / totalWeight;
        double avgBandwidth = weightedBandwidth / totalWeight;

        // 综合评分逻辑
        // RTT < 50ms 优秀, 50-100ms 良好, 100-200ms 一般, 200-500ms 差, >500ms 极差
        int rttScore = avgRtt < 50 ? 0 : avgRtt < 100 ? 1 : avgRtt < 200 ? 2 : avgRtt < 500 ? 3 : 4;

        // 丢包率 < 0.5% 优秀, 0.5-2% 良好, 2-5% 一般, 5-15% 差, >15% 极差
        int lossScore = avgLoss < 0.5 ? 0 : avgLoss < 2 ? 1 : avgLoss < 5 ? 2 : avgLoss < 15 ? 3 : 4;

        // 抖动 < 10ms 优秀, 10-30ms 良好, 30-50ms 一般, 50-100ms 差, >100ms 极差
        int jitterScore = avgJitter < 10 ? 0 : avgJitter < 30 ? 1 : avgJitter < 50 ? 2 : avgJitter < 100 ? 3 : 4;

        // 带宽 > 3Mbps 优秀, 1.5-3Mbps 良好, 500K-1.5M 一般, 150-500K 差, <150K 极差
        int bandwidthScore = avgBandwidth > 3000 ? 0 : avgBandwidth > 1500 ? 1 :
                avgBandwidth > 500 ? 2 : avgBandwidth > 150 ? 3 : 4;

        // 取最差维度作为综合评级（木桶效应）
        // 丢包率权重最高（丢包对视频质量影响最大）
        int baseScore = Math.max(Math.max(rttScore, jitterScore), bandwidthScore);
        int finalScore = Math.max(baseScore, lossScore);

        // 如果丢包率严重，直接升级一个等级
        if (lossScore >= 4 || (lossScore >= 3 && avgRtt > 300)) {
            finalScore = Math.min(4, finalScore + 1);
        }

        return finalScore;
    }

    // ==================== 内部类 ====================

    /**
     * 网络质量快照
     */
    @Data
    @Builder
    public static class NetworkQualitySnapshot {
        /** 往返时延（ms） */
        private double rtt;
        /** 丢包率（百分比，0-100） */
        private double packetLoss;
        /** 抖动（ms） */
        private double jitter;
        /** 估算带宽（kbps） */
        private double estimatedBandwidth;
        /** 是否正在屏幕共享 */
        private boolean isScreenShare;
        /** 时间戳 */
        private long timestamp;
        /** 综合质量等级（0-4） */
        private int qualityLevel;
    }

    /**
     * 编码参数配置
     */
    @Data
    @Builder
    public static class EncoderParams {
        /** 质量等级 */
        private int quality;
        /** 目标宽度 */
        private int width;
        /** 目标高度 */
        private int height;
        /** 目标帧率 */
        private int fps;
        /** 最大码率（kbps） */
        private int maxBitrate;
        /** 最小码率（kbps） */
        private int minBitrate;
        /** 目标码率（kbps） */
        private int targetBitrate;
        /** 分辨率缩小比例 */
        private double scaleResolutionDownBy;
        /** 描述 */
        private String description;

        /**
         * 转换为客户端可用的 JSON 格式 Map
         */
        public Map<String, Object> toClientParams() {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("quality", quality);
            params.put("width", width);
            params.put("height", height);
            params.put("fps", fps);
            params.put("maxBitrate", maxBitrate);
            params.put("minBitrate", minBitrate);
            params.put("targetBitrate", targetBitrate);
            params.put("scaleResolutionDownBy", scaleResolutionDownBy);
            params.put("description", description);
            return params;
        }
    }
}