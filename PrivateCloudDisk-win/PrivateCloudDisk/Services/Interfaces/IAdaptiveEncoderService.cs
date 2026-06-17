// ============================================================
// IAdaptiveEncoderService.cs — 自适应编码服务接口
// 根据网络质量计算最优编码参数。
// 后端对应：org.project.im.server.signaling.optimizer.AdaptiveVideoOptimizer
// ============================================================

using PrivateCloudDisk.Models;

namespace PrivateCloudDisk.Services.Interfaces;

/// <summary>自适应编码服务接口</summary>
public interface IAdaptiveEncoderService
{
    /// <summary>根据网络质量快照计算最优编码参数</summary>
    EncoderParams CalculateOptimalParams(NetworkQualitySnapshot snapshot, EncoderParams currentParams);

    /// <summary>获取指定质量等级的预设参数</summary>
    EncoderParams GetPreset(int qualityLevel);
}