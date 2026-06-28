using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;
using System.Diagnostics;
using Windows.UI.Notifications;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// 版本自检服务 — 启动时自动检查 + 定期检查 + 智能通知
///
/// 检查策略:
/// 1. 启动时立即检查一次
/// 2. 大版本更新 → 弹出对话框，引导用户下载安装
/// 3. 小版本更新 → 后台静默下载，通知栏提示
/// 4. 热修复 → 自动下载并应用，无需用户干预
/// 5. 强制更新 → 阻止用户继续使用，必须更新
/// </summary>
public class VersionCheckService : IVersionCheckService, IDisposable
{
    private readonly IUpdateService _updateService;
    private readonly ISettingsService _settings;
    private readonly ToastNotificationService? _toastService;
    private Timer? _periodicTimer;
    private bool _disposed;

    public event EventHandler<VersionCheckResult>? CheckCompleted;

    public VersionCheckService(
        IUpdateService updateService,
        ISettingsService settings,
        ToastNotificationService? toastService = null)
    {
        _updateService = updateService;
        _settings = settings;
        _toastService = toastService;
    }

    // ==================== 启动时自检 ====================

    /// <summary>
    /// 启动时执行版本自检
    /// 在应用启动完成、用户登录后调用
    /// </summary>
    public async Task<VersionCheckResult> PerformStartupCheckAsync()
    {
        Debug.WriteLine("[VersionCheck] 执行启动版本自检...");

        var result = new VersionCheckResult();

        try
        {
            var updateInfo = await _updateService.CheckForUpdatesAsync();

            result.Success = updateInfo != null;
            result.UpdateInfo = updateInfo;

            if (updateInfo?.HasUpdate == true)
            {
                Debug.WriteLine($"[VersionCheck] 发现新版本: {updateInfo.LatestVersion} ({updateInfo.UpdateType})");

                // 根据更新类型采取不同策略
                if (updateInfo.ForceUpdate)
                {
                    // 强制更新 → 立即通知用户
                    await HandleForceUpdateAsync(updateInfo);
                }
                else if (updateInfo.IsMajorUpdate)
                {
                    // 大版本更新 → 通知用户
                    await HandleMajorUpdateAsync(updateInfo);
                }
                else if (updateInfo.IsHotfix && _updateService.Settings.HotUpdateEnabled)
                {
                    // 热修复 → 静默下载并应用
                    await HandleHotfixAsync(updateInfo);
                }
                else
                {
                    // 小版本更新 → 后台下载
                    await HandleMinorUpdateAsync(updateInfo);
                }
            }
            else
            {
                Debug.WriteLine("[VersionCheck] 当前已是最新版本");
            }
        }
        catch (Exception ex)
        {
            result.Success = false;
            result.ErrorMessage = ex.Message;
            Debug.WriteLine($"[VersionCheck] 版本检查失败: {ex.Message}");
        }

        CheckCompleted?.Invoke(this, result);
        return result;
    }

    // ==================== 定期检查 ====================

    public void StartPeriodicCheck()
    {
        StopPeriodicCheck();

        if (!_updateService.Settings.AutoCheckEnabled) return;

        var interval = TimeSpan.FromHours(_updateService.Settings.CheckIntervalHours);

        _periodicTimer = new Timer(async _ =>
        {
            try
            {
                var result = await CheckNowAsync();
                Debug.WriteLine($"[VersionCheck] 定期检查完成: HasUpdate={result.UpdateInfo?.HasUpdate}");
            }
            catch { /* 静默 */ }
        }, null, interval, interval);

        Debug.WriteLine($"[VersionCheck] 定期检查已启动, 间隔: {interval.TotalHours}h");
    }

    public void StopPeriodicCheck()
    {
        _periodicTimer?.Dispose();
        _periodicTimer = null;
    }

    public async Task<VersionCheckResult> CheckNowAsync()
    {
        var result = new VersionCheckResult();

        try
        {
            var updateInfo = await _updateService.CheckForUpdatesAsync();
            result.Success = updateInfo != null;
            result.UpdateInfo = updateInfo;

            if (updateInfo?.HasUpdate == true && !updateInfo.ForceUpdate)
            {
                _toastService?.ShowToastNotification(
                    "发现新版本",
                    $"PrivateCloudDisk v{updateInfo.LatestVersion} 可用",
                    "update_available");
            }

            CheckCompleted?.Invoke(this, result);
        }
        catch (Exception ex)
        {
            result.Success = false;
            result.ErrorMessage = ex.Message;
        }

        return result;
    }

    // ==================== 更新处理策略 ====================

    /// <summary>
    /// 处理强制更新 — 阻止继续使用，必须更新
    /// </summary>
    private async Task HandleForceUpdateAsync(UpdateInfo updateInfo)
    {
        // 发送紧急通知
        _toastService?.ShowToastNotification(
            "重要更新",
            $"必须更新到 v{updateInfo.LatestVersion} 才能继续使用",
            "force_update");

        // 自动开始下载
        var progress = new Progress<UpdateDownloadProgress>(p =>
        {
            Debug.WriteLine($"[VersionCheck] 强制更新下载进度: {p.ProgressPercent:F1}%");
        });

        await _updateService.DownloadUpdateAsync(progress);

        // 下载完成后自动安装
        if (_updateService.CurrentStatus == UpdateStatus.Downloaded)
        {
            await _updateService.InstallUpdateAsync();
        }
    }

    /// <summary>
    /// 处理大版本更新 — 弹出通知，引导用户
    /// </summary>
    private async Task HandleMajorUpdateAsync(UpdateInfo updateInfo)
    {
        _toastService?.ShowToastNotification(
            "发现新版本",
            $"PrivateCloudDisk v{updateInfo.LatestVersion}\n{updateInfo.ReleaseNotes}",
            "major_update");

        // 如果开启了自动下载，后台下载
        if (_updateService.Settings.AutoDownload)
        {
            _ = _updateService.DownloadUpdateAsync();
        }
    }

    /// <summary>
    /// 处理热修复 — 静默下载并应用
    /// </summary>
    private async Task HandleHotfixAsync(UpdateInfo updateInfo)
    {
        Debug.WriteLine($"[VersionCheck] 正在静默应用热修复 v{updateInfo.LatestVersion}");

        var progress = new Progress<UpdateDownloadProgress>(p =>
        {
            Debug.WriteLine($"[VersionCheck] 热修复下载: {p.ProgressPercent:F1}%");
        });

        await _updateService.DownloadUpdateAsync(progress);

        if (_updateService.CurrentStatus == UpdateStatus.Downloaded)
        {
            await _updateService.InstallUpdateAsync();

            _toastService?.ShowToastNotification(
                "更新已安装",
                $"热修复 v{updateInfo.LatestVersion} 已应用，部分功能将在下次启动时生效",
                "hotfix_applied");
        }
    }

    /// <summary>
    /// 处理小版本更新 — 后台下载
    /// </summary>
    private async Task HandleMinorUpdateAsync(UpdateInfo updateInfo)
    {
        if (_updateService.Settings.AutoDownload)
        {
            _ = _updateService.DownloadUpdateAsync();

            _toastService?.ShowToastNotification(
                "正在下载更新",
                $"PrivateCloudDisk v{updateInfo.LatestVersion} 正在后台下载",
                "update_downloading");
        }
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        StopPeriodicCheck();
    }
}