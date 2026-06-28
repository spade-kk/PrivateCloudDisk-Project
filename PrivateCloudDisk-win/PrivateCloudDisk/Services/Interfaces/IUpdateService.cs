using PrivateCloudDisk.Models;

namespace PrivateCloudDisk.Services.Interfaces;

/// <summary>
/// 更新服务接口 — 负责版本检查、热更新、大版本更新
/// </summary>
public interface IUpdateService
{
    /// <summary>当前更新状态</summary>
    UpdateStatus CurrentStatus { get; }

    /// <summary>最新的更新信息</summary>
    UpdateInfo? LatestUpdateInfo { get; }

    /// <summary>更新下载进度</summary>
    UpdateDownloadProgress? DownloadProgress { get; }

    /// <summary>更新设置</summary>
    UpdateSettings Settings { get; }

    // ── 事件 ──

    /// <summary>更新状态变化</summary>
    event EventHandler<UpdateStatus>? StatusChanged;

    /// <summary>有可用更新</summary>
    event EventHandler<UpdateInfo>? UpdateAvailable;

    /// <summary>下载进度变化</summary>
    event EventHandler<UpdateDownloadProgress>? DownloadProgressChanged;

    /// <summary>热补丁已应用</summary>
    event EventHandler<HotPatchInfo>? HotPatchApplied;

    // ── 版本检查 ──

    /// <summary>检查更新（手动触发）</summary>
    Task<UpdateInfo?> CheckForUpdatesAsync(CancellationToken ct = default);

    /// <summary>启动自动检查（定时任务）</summary>
    void StartAutoCheck();

    /// <summary>停止自动检查</summary>
    void StopAutoCheck();

    // ── 下载更新 ──

    /// <summary>下载更新包</summary>
    Task DownloadUpdateAsync(IProgress<UpdateDownloadProgress>? progress = null,
        CancellationToken ct = default);

    /// <summary>取消下载</summary>
    void CancelDownload();

    // ── 安装更新 ──

    /// <summary>安装更新（大版本需要启动下载器）</summary>
    Task InstallUpdateAsync();

    /// <summary>应用热补丁</summary>
    Task<bool> ApplyHotPatchAsync(HotPatchInfo patch,
        IProgress<UpdateDownloadProgress>? progress = null,
        CancellationToken ct = default);

    // ── 设置 ──

    /// <summary>跳过当前版本</summary>
    void SkipCurrentVersion();

    /// <summary>保存更新设置</summary>
    void SaveSettings();
}

/// <summary>
/// 版本自检服务接口 — 启动时自动检查 + 定期检查
/// </summary>
public interface IVersionCheckService
{
    /// <summary>启动时执行版本自检</summary>
    Task<VersionCheckResult> PerformStartupCheckAsync();

    /// <summary>启动定期检查</summary>
    void StartPeriodicCheck();

    /// <summary>停止定期检查</summary>
    void StopPeriodicCheck();

    /// <summary>手动检查</summary>
    Task<VersionCheckResult> CheckNowAsync();

    /// <summary>版本自检结果事件</summary>
    event EventHandler<VersionCheckResult>? CheckCompleted;
}

/// <summary>
/// 版本自检结果
/// </summary>
public class VersionCheckResult
{
    /// <summary>是否成功检查</summary>
    public bool Success { get; set; }

    /// <summary>更新信息(有更新时不为null)</summary>
    public UpdateInfo? UpdateInfo { get; set; }

    /// <summary>检查时间</summary>
    public DateTime CheckTime { get; set; } = DateTime.UtcNow;

    /// <summary>错误信息</summary>
    public string? ErrorMessage { get; set; }

    /// <summary>是否需要用户干预</summary>
    public bool RequiresUserAction =>
        UpdateInfo != null &&
        (UpdateInfo.IsMajorUpdate || UpdateInfo.ForceUpdate);

    /// <summary>是否可静默更新(热更新)</summary>
    public bool CanSilentUpdate =>
        UpdateInfo != null &&
        UpdateInfo.IsHotfix &&
        !UpdateInfo.ForceUpdate;
}