using System.Text.Json.Serialization;

namespace PrivateCloudDisk.Models;

#region 更新检查相关模型

/// <summary>
/// 更新信息 — 从后端 /api/v1/version/check 返回
/// </summary>
public class UpdateInfo
{
    /// <summary>是否有可用更新</summary>
    [JsonPropertyName("has_update")]
    public bool HasUpdate { get; set; }

    /// <summary>最新版本号</summary>
    [JsonPropertyName("latest_version")]
    public string LatestVersion { get; set; } = string.Empty;

    /// <summary>当前版本号</summary>
    [JsonPropertyName("current_version")]
    public string CurrentVersion { get; set; } = string.Empty;

    /// <summary>更新类型: major(大版本)/minor(小版本)/patch(热修复)</summary>
    [JsonPropertyName("update_type")]
    public string UpdateType { get; set; } = "patch";

    /// <summary>是否强制更新</summary>
    [JsonPropertyName("force_update")]
    public bool ForceUpdate { get; set; }

    /// <summary>更新包下载地址</summary>
    [JsonPropertyName("download_url")]
    public string DownloadUrl { get; set; } = string.Empty;

    /// <summary>更新包大小(字节)</summary>
    [JsonPropertyName("package_size")]
    public long PackageSize { get; set; }

    /// <summary>更新包 SHA256 哈希</summary>
    [JsonPropertyName("package_hash")]
    public string PackageHash { get; set; } = string.Empty;

    /// <summary>更新日志</summary>
    [JsonPropertyName("release_notes")]
    public string ReleaseNotes { get; set; } = string.Empty;

    /// <summary>发布时间</summary>
    [JsonPropertyName("published_at")]
    public DateTime PublishedAt { get; set; }

    /// <summary>最低支持的客户端版本（低于此版本必须更新）</summary>
    [JsonPropertyName("min_supported_version")]
    public string MinSupportedVersion { get; set; } = string.Empty;

    // ── 计算属性 ──

    /// <summary>是否为大版本更新</summary>
    [JsonIgnore]
    public bool IsMajorUpdate => UpdateType == "major";

    /// <summary>是否为热修复更新</summary>
    [JsonIgnore]
    public bool IsHotfix => UpdateType == "patch";

    /// <summary>是否为小版本更新</summary>
    [JsonIgnore]
    public bool IsMinorUpdate => UpdateType == "minor";

    /// <summary>是否需要提示用户</summary>
    [JsonIgnore]
    public bool ShouldNotifyUser => IsMajorUpdate || ForceUpdate;
}

/// <summary>
/// 热修复补丁信息
/// </summary>
public class HotPatchInfo
{
    /// <summary>补丁 ID</summary>
    [JsonPropertyName("patch_id")]
    public string PatchId { get; set; } = string.Empty;

    /// <summary>补丁版本号</summary>
    [JsonPropertyName("patch_version")]
    public string PatchVersion { get; set; } = string.Empty;

    /// <summary>补丁下载地址</summary>
    [JsonPropertyName("download_url")]
    public string DownloadUrl { get; set; } = string.Empty;

    /// <summary>补丁大小</summary>
    [JsonPropertyName("patch_size")]
    public long PatchSize { get; set; }

    /// <summary>补丁 SHA256</summary>
    [JsonPropertyName("patch_hash")]
    public string PatchHash { get; set; } = string.Empty;

    /// <summary>补丁描述</summary>
    [JsonPropertyName("description")]
    public string Description { get; set; } = string.Empty;

    /// <summary>应用补丁后是否需要重启</summary>
    [JsonPropertyName("requires_restart")]
    public bool RequiresRestart { get; set; }

    /// <summary>补丁类型: script/js/css/dll/resource</summary>
    [JsonPropertyName("patch_type")]
    public string PatchType { get; set; } = "dll";

    /// <summary>目标文件列表（增量更新时需要替换的文件）</summary>
    [JsonPropertyName("target_files")]
    public List<string> TargetFiles { get; set; } = new();
}

/// <summary>
/// 版本检查请求
/// </summary>
public class VersionCheckRequest
{
    /// <summary>当前版本号</summary>
    [JsonPropertyName("current_version")]
    public string CurrentVersion { get; set; } = string.Empty;

    /// <summary>平台: windows/macos/linux/android/ios/cli</summary>
    [JsonPropertyName("platform")]
    public string Platform { get; set; } = string.Empty;

    /// <summary>架构: x64/arm64/x86</summary>
    [JsonPropertyName("arch")]
    public string Arch { get; set; } = string.Empty;

    /// <summary>渠道: stable/beta/alpha</summary>
    [JsonPropertyName("channel")]
    public string Channel { get; set; } = "stable";
}

/// <summary>
/// 更新下载进度
/// </summary>
public class UpdateDownloadProgress
{
    public string UpdateId { get; set; } = string.Empty;
    public long DownloadedBytes { get; set; }
    public long TotalBytes { get; set; }
    public double SpeedBytesPerSecond { get; set; }
    public TimeSpan EstimatedTimeRemaining { get; set; }
    public string Status { get; set; } = "downloading"; // downloading/verifying/installing/completed/failed
    public string? ErrorMessage { get; set; }

    public double ProgressPercent => TotalBytes > 0
        ? (double)DownloadedBytes / TotalBytes * 100
        : 0;
}

/// <summary>
/// 更新状态枚举
/// </summary>
public enum UpdateStatus
{
    /// <summary>未检查</summary>
    NotChecked,
    /// <summary>检查中</summary>
    Checking,
    /// <summary>已是最新版本</summary>
    UpToDate,
    /// <summary>有可用更新</summary>
    UpdateAvailable,
    /// <summary>下载中</summary>
    Downloading,
    /// <summary>下载完成，等待安装</summary>
    Downloaded,
    /// <summary>安装中</summary>
    Installing,
    /// <summary>已安装，等待重启</summary>
    PendingRestart,
    /// <summary>更新失败</summary>
    Failed,
    /// <summary>已跳过此版本</summary>
    Skipped
}

/// <summary>
/// 更新设置
/// </summary>
public class UpdateSettings
{
    /// <summary>是否启用自动检查更新</summary>
    public bool AutoCheckEnabled { get; set; } = true;

    /// <summary>自动检查间隔(小时)</summary>
    public int CheckIntervalHours { get; set; } = 24;

    /// <summary>是否自动下载更新</summary>
    public bool AutoDownload { get; set; } = false;

    /// <summary>是否允许热更新</summary>
    public bool HotUpdateEnabled { get; set; } = true;

    /// <summary>更新渠道</summary>
    public string Channel { get; set; } = "stable";

    /// <summary>最后检查时间</summary>
    public DateTime? LastCheckTime { get; set; }

    /// <summary>跳过的版本号(不再提醒)</summary>
    public string? SkippedVersion { get; set; }
}

#endregion

#region 版本比较工具

/// <summary>
/// 语义化版本号比较工具
/// </summary>
public static class VersionHelper
{
    /// <summary>
    /// 解析版本号字符串为 Version 对象
    /// 支持格式: "1.0.0", "1.0.0-beta", "1.0.0.1"
    /// </summary>
    public static Version Parse(string version)
    {
        if (string.IsNullOrWhiteSpace(version))
            return new Version(0, 0, 0);

        // 移除预发布标签
        var dashIdx = version.IndexOf('-');
        var cleanVersion = dashIdx > 0 ? version[..dashIdx] : version;

        try
        {
            return new Version(cleanVersion);
        }
        catch
        {
            return new Version(0, 0, 0);
        }
    }

    /// <summary>
    /// 比较两个版本号
    /// </summary>
    /// <returns>0=相等, >0=v1大于v2, <0=v1小于v2</returns>
    public static int Compare(string v1, string v2)
    {
        return Parse(v1).CompareTo(Parse(v2));
    }

    /// <summary>
    /// v1 是否大于 v2
    /// </summary>
    public static bool IsGreaterThan(string v1, string v2)
    {
        return Compare(v1, v2) > 0;
    }

    /// <summary>
    /// v1 是否小于 v2
    /// </summary>
    public static bool IsLessThan(string v1, string v2)
    {
        return Compare(v1, v2) < 0;
    }

    /// <summary>
    /// 判断更新类型
    /// </summary>
    public static string DetermineUpdateType(string currentVersion, string newVersion)
    {
        var current = Parse(currentVersion);
        var latest = Parse(newVersion);

        if (latest.Major > current.Major)
            return "major"; // 大版本更新

        if (latest.Minor > current.Minor)
            return "minor"; // 小版本更新

        return "patch"; // 热修复
    }
}

#endregion