// ============================================================
// AdaptiveEncoderService.cs — 自适应编码服务实现
// 根据网络质量动态计算最优编码参数。
// 实现企业级视频编码自适应优化：
//   1. 5 种质量预设（1080p → 360p → 纯音频）
//   2. 基于多指标综合评分（RTT、丢包率、抖动、带宽）
//   3. 平滑处理，避免频繁切换
//   4. 冷却期机制，防止乒乓效应
// 
// 后端对应：
//   org.project.im.server.signaling.optimizer.AdaptiveVideoOptimizer
// 
// 视频编码理论：
//   - 码率(Bitrate) = 分辨率 × 帧率 × 压缩比
//   - H.264/H.265 压缩比一般 50:1 ~ 200:1
//   - 网络带宽 < 目标码率时，需要降级
//   - RTT > 100ms 时，需要降低帧率减少卡顿
//   - 丢包率 > 2% 时，需要降低码率
// ============================================================

using Microsoft.Extensions.Logging;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.Services.Implementations;

public class AdaptiveEncoderService : IAdaptiveEncoderService
{
    private readonly ILogger<AdaptiveEncoderService> _logger;

    // 质量预设表（从高到低）
    private static readonly EncoderParams[] QualityPresets =
    {
        new() // 0: 1080p 高清
        {
            Quality = 0, Width = 1920, Height = 1080, Fps = 30,
            MaxBitrate = 4000, MinBitrate = 800, TargetBitrate = 2500,
            ScaleResolutionDownBy = 1.0, Description = "1080p 高清"
        },
        new() // 1: 720p 高清
        {
            Quality = 1, Width = 1280, Height = 720, Fps = 25,
            MaxBitrate = 2500, MinBitrate = 500, TargetBitrate = 1500,
            ScaleResolutionDownBy = 1.0, Description = "720p 高清"
        },
        new() // 2: 540p 标清
        {
            Quality = 2, Width = 960, Height = 540, Fps = 20,
            MaxBitrate = 1500, MinBitrate = 300, TargetBitrate = 800,
            ScaleResolutionDownBy = 2.0, Description = "540p 标清"
        },
        new() // 3: 360p 低清
        {
            Quality = 3, Width = 640, Height = 360, Fps = 15,
            MaxBitrate = 800, MinBitrate = 150, TargetBitrate = 400,
            ScaleResolutionDownBy = 3.0, Description = "360p 低清"
        },
        new() // 4: 240p 极低（保底）
        {
            Quality = 4, Width = 426, Height = 240, Fps = 10,
            MaxBitrate = 400, MinBitrate = 80, TargetBitrate = 200,
            ScaleResolutionDownBy = 4.0, Description = "240p 极低"
        }
    };

    // 历史快照（用于平滑）
    private readonly Queue<NetworkQualitySnapshot> _historyBuffer = new();
    private const int MaxHistorySize = 10;
    private const int MinHistoryForSmoothing = 3;

    // 切换冷却期（防止乒乓效应）
    private DateTime _lastSwitchTime = DateTime.MinValue;
    private static readonly TimeSpan SwitchCooldown = TimeSpan.FromSeconds(3);

    // 质量评分权重
    private const double RttWeight = 0.30;
    private const double PacketLossWeight = 0.35;
    private const double JitterWeight = 0.15;
    private const double BandwidthWeight = 0.20;

    // 阈值
    private const double ExcellentRtt = 50;
    private const double GoodRtt = 100;
    private const double FairRtt = 200;
    private const double PoorRtt = 400;

    private const double ExcellentLoss = 0.5;
    private const double GoodLoss = 2.0;
    private const double FairLoss = 5.0;
    private const double PoorLoss = 10.0;

    public AdaptiveEncoderService(ILogger<AdaptiveEncoderService> logger)
    {
        _logger = logger;
    }

    // ── 核心算法 ────────────────────────────────────────────

    public EncoderParams CalculateOptimalParams(NetworkQualitySnapshot snapshot, EncoderParams currentParams)
    {
        // 记录历史
        _historyBuffer.Enqueue(snapshot);
        while (_historyBuffer.Count > MaxHistorySize)
            _historyBuffer.Dequeue();

        // 冷却期检查
        if (DateTime.UtcNow - _lastSwitchTime < SwitchCooldown)
            return currentParams;

        // 计算质量评分
        var qualityScore = CalculateQualityScore(snapshot);
        var smoothedScore = SmoothQualityScore(qualityScore);
        var targetPresetIndex = DetermineTargetPreset(smoothedScore, currentParams);

        var optimal = QualityPresets[targetPresetIndex];
        if (currentParams.Quality == optimal.Quality)
            return currentParams;

        _lastSwitchTime = DateTime.UtcNow;
        _logger.LogInformation(
            "[AdaptiveEncoder] Switching: {Desc} " +
            "(RTT={Rtt:F0}ms Loss={Loss:F1}% Jitter={Jitter:F1}ms BW={Bw:F0}kbps Score={Score:F2})",
            optimal.Description, snapshot.Rtt, snapshot.PacketLoss,
            snapshot.Jitter, snapshot.EstimatedBandwidth, smoothedScore);

        return optimal;
    }

    public EncoderParams GetPreset(int qualityLevel)
    {
        qualityLevel = Math.Clamp(qualityLevel, 0, QualityPresets.Length - 1);
        return QualityPresets[qualityLevel];
    }

    // ── 质量评分 ────────────────────────────────────────────

    /// <summary>
    /// 计算综合质量评分（0 = 最佳，1 = 最差）
    /// </summary>
    private static double CalculateQualityScore(NetworkQualitySnapshot snapshot)
    {
        // RTT 评分
        var rttScore = snapshot.Rtt / PoorRtt;

        // 丢包率评分
        var lossScore = snapshot.PacketLoss / PoorLoss;

        // 抖动评分
        var jitterScore = snapshot.Jitter / 50.0;

        // 带宽评分（反向：带宽越高越好）
        var bandwidthScore = 1.0 - Math.Min(snapshot.EstimatedBandwidth / 5000.0, 1.0);

        return rttScore * RttWeight
             + lossScore * PacketLossWeight
             + jitterScore * JitterWeight
             + bandwidthScore * BandwidthWeight;
    }

    /// <summary>
    /// 平滑质量评分（使用简单移动平均，避免瞬时波动）
    /// </summary>
    private double SmoothQualityScore(double currentScore)
    {
        if (_historyBuffer.Count < MinHistoryForSmoothing)
            return currentScore;

        var recentScores = _historyBuffer
            .TakeLast(MinHistoryForSmoothing)
            .Select(CalculateQualityScore)
            .Append(currentScore);

        return recentScores.Average();
    }

    /// <summary>
    /// 确定目标预设等级。
    /// 使用滞回机制：升档需要更严格的条件，降档更宽松，防止乒乓。
    /// </summary>
    private static int DetermineTargetPreset(double score, EncoderParams currentParams)
    {
        int target;

        if (score <= 0.25)          target = 0; // 1080p
        else if (score <= 0.45)     target = 1; // 720p
        else if (score <= 0.65)     target = 2; // 540p
        else if (score <= 0.85)     target = 3; // 360p
        else                        target = 4; // 240p

        // 滞回逻辑：升档加分，降档不减分
        if (target < currentParams.Quality)
        {
            // 升档：需要分数显著低于阈值
            if (score > (target + 1) * 0.25 - 0.05)
                target = currentParams.Quality;
        }

        return target;
    }
}