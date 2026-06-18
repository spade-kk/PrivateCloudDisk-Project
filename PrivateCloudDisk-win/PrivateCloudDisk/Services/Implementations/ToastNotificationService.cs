using Microsoft.Toolkit.Uwp.Notifications;
using Microsoft.UI.Xaml;
using Windows.UI.Notifications;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// Windows Toast 通知服务
///
/// 使用 Windows 10/11 原生 Toast 通知 API 实现：
///   - 上传/下载完成通知
///   - 同步冲突通知
///   - 存储配额警告
///   - 分享通知
///   - 新版本更新通知
///
/// 通知支持：
///   - 内联操作按钮（如"打开文件"、"解决冲突"）
///   - 进度条（用于长时间上传/下载）
///   - 分组和标记（便于替换/更新通知）
///   - 操作中心持久化
/// </summary>
public class ToastNotificationService
{
    private const string AppId = "PrivateCloudDisk";
    private const string UploadGroup = "Uploads";
    private const string DownloadGroup = "Downloads";
    private const string SyncGroup = "Sync";
    private const string SystemGroup = "System";

    private readonly Dictionary<string, ToastNotification> _activeNotifications = new();
    private int _notificationId;

    /// <summary>
    /// 初始化 Toast 通知（注册应用 ID）
    /// </summary>
    public static void Initialize()
    {
        // 确保 ToastNotificationManagerCompat 已初始化
        // 在应用启动时调用一次
    }

    // ── 上传通知 ──────────────────────────────────────────

    /// <summary>
    /// 显示上传完成通知
    /// </summary>
    public string ShowUploadComplete(string fileName, string filePath)
    {
        var tag = $"upload-{Interlocked.Increment(ref _notificationId)}";

        new ToastContentBuilder()
            .AddArgument("action", "openFile")
            .AddArgument("filePath", filePath)
            .AddText("上传完成")
            .AddText(fileName)
            .AddAppLogoOverride(new Uri("ms-appx:///Resources/Icons/app.ico"),
                ToastGenericAppLogoCrop.Circle)
            .AddButton(new ToastButton()
                .SetContent("打开文件")
                .AddArgument("action", "openFile")
                .AddArgument("filePath", filePath))
            .AddButton(new ToastButton()
                .SetContent("打开文件夹")
                .AddArgument("action", "openFolder")
                .AddArgument("filePath", filePath))
            .SetToastDuration(ToastDuration.Short)
            .Show(toast =>
            {
                toast.Tag = tag;
                toast.Group = UploadGroup;
                toast.ExpirationTime = DateTime.Now.AddDays(1);
            });

        return tag;
    }

    /// <summary>
    /// 显示上传进度通知（长时间上传）
    /// </summary>
    public string ShowUploadProgress(string fileName, long uploadedBytes, long totalBytes)
    {
        var tag = $"upload-progress-{fileName}";
        var progress = totalBytes > 0 ? (double)uploadedBytes / totalBytes : 0;

        // 如果已完成，移除进度通知
        if (progress >= 1.0)
        {
            RemoveNotification(tag);
            return ShowUploadComplete(fileName, "");
        }

        new ToastContentBuilder()
            .AddArgument("action", "cancelUpload")
            .AddArgument("fileName", fileName)
            .AddText("正在上传...")
            .AddText(fileName)
            .AddProgressBar()
            .AddButton(new ToastButton()
                .SetContent("取消")
                .AddArgument("action", "cancelUpload")
                .AddArgument("fileName", fileName))
            .Show(toast =>
            {
                toast.Tag = tag;
                toast.Group = UploadGroup;
                toast.SuppressPopup = true; // 不弹出，仅更新操作中心
                toast.Data = new NotificationData(
                    new Dictionary<string, string>
                    {
                        ["progress"] = progress.ToString("F2"),
                        ["status"] = $"{FormatBytes(uploadedBytes)} / {FormatBytes(totalBytes)}"
                    });
            });

        return tag;
    }

    /// <summary>
    /// 显示上传失败通知
    /// </summary>
    public void ShowUploadFailed(string fileName, string error)
    {
        new ToastContentBuilder()
            .AddArgument("action", "retryUpload")
            .AddArgument("fileName", fileName)
            .AddText("上传失败")
            .AddText(fileName)
            .AddText($"错误: {error}")
            .AddButton(new ToastButton()
                .SetContent("重试")
                .AddArgument("action", "retryUpload")
                .AddArgument("fileName", fileName))
            .SetToastDuration(ToastDuration.Long)
            .Show(toast =>
            {
                toast.Group = UploadGroup;
            });
    }

    // ── 下载通知 ──────────────────────────────────────────

    /// <summary>
    /// 显示下载完成通知
    /// </summary>
    public string ShowDownloadComplete(string fileName, string localPath)
    {
        var tag = $"download-{Interlocked.Increment(ref _notificationId)}";

        new ToastContentBuilder()
            .AddArgument("action", "openFile")
            .AddArgument("filePath", localPath)
            .AddText("下载完成")
            .AddText(fileName)
            .AddAppLogoOverride(new Uri("ms-appx:///Resources/Icons/app.ico"),
                ToastGenericAppLogoCrop.Circle)
            .AddButton(new ToastButton()
                .SetContent("打开文件")
                .AddArgument("action", "openFile")
                .AddArgument("filePath", localPath))
            .AddButton(new ToastButton()
                .SetContent("打开文件夹")
                .AddArgument("action", "openFolder")
                .AddArgument("filePath", localPath))
            .SetToastDuration(ToastDuration.Short)
            .Show(toast =>
            {
                toast.Tag = tag;
                toast.Group = DownloadGroup;
            });

        return tag;
    }

    /// <summary>
    /// 显示下载进度通知
    /// </summary>
    public string ShowDownloadProgress(string fileName, long downloadedBytes, long totalBytes)
    {
        var tag = $"download-progress-{fileName}";
        var progress = totalBytes > 0 ? (double)downloadedBytes / totalBytes : 0;

        if (progress >= 1.0)
        {
            RemoveNotification(tag);
            return ShowDownloadComplete(fileName, "");
        }

        new ToastContentBuilder()
            .AddArgument("action", "cancelDownload")
            .AddArgument("fileName", fileName)
            .AddText("正在下载...")
            .AddText(fileName)
            .AddProgressBar()
            .AddButton(new ToastButton()
                .SetContent("取消")
                .AddArgument("action", "cancelDownload")
                .AddArgument("fileName", fileName))
            .Show(toast =>
            {
                toast.Tag = tag;
                toast.Group = DownloadGroup;
                toast.SuppressPopup = true;
                toast.Data = new NotificationData(
                    new Dictionary<string, string>
                    {
                        ["progress"] = progress.ToString("F2"),
                        ["status"] = $"{FormatBytes(downloadedBytes)} / {FormatBytes(totalBytes)}"
                    });
            });

        return tag;
    }

    // ── 同步通知 ──────────────────────────────────────────

    /// <summary>
    /// 显示同步冲突通知
    /// </summary>
    public void ShowSyncConflict(string fileName, string conflictId)
    {
        new ToastContentBuilder()
            .AddArgument("action", "resolveConflict")
            .AddArgument("conflictId", conflictId)
            .AddText("同步冲突")
            .AddText($"文件 {fileName} 存在冲突")
            .AddText("本地和远程版本均已修改，请选择保留哪个版本。")
            .AddButton(new ToastButton()
                .SetContent("保留本地")
                .AddArgument("action", "keepLocal")
                .AddArgument("conflictId", conflictId))
            .AddButton(new ToastButton()
                .SetContent("保留远程")
                .AddArgument("action", "keepRemote")
                .AddArgument("conflictId", conflictId))
            .AddButton(new ToastButton()
                .SetContent("保留两者")
                .AddArgument("action", "keepBoth")
                .AddArgument("conflictId", conflictId))
            .SetToastDuration(ToastDuration.Long)
            .SetToastScenario(ToastScenario.Reminder)
            .Show(toast =>
            {
                toast.Group = SyncGroup;
                toast.ExpirationTime = DateTime.Now.AddDays(3);
            });
    }

    /// <summary>
    /// 显示同步完成通知
    /// </summary>
    public void ShowSyncComplete(int filesCount, long totalBytes)
    {
        new ToastContentBuilder()
            .AddArgument("action", "viewSyncStatus")
            .AddText("同步完成")
            .AddText($"已同步 {filesCount} 个文件 ({FormatBytes(totalBytes)})")
            .SetToastDuration(ToastDuration.Short)
            .Show(toast =>
            {
                toast.Group = SyncGroup;
            });
    }

    /// <summary>
    /// 显示同步错误通知
    /// </summary>
    public void ShowSyncError(string error, int errorCount)
    {
        new ToastContentBuilder()
            .AddArgument("action", "viewSyncErrors")
            .AddText("同步错误")
            .AddText($"{errorCount} 个文件同步失败")
            .AddText(error)
            .AddButton(new ToastButton()
                .SetContent("查看详情")
                .AddArgument("action", "viewSyncErrors"))
            .SetToastDuration(ToastDuration.Long)
            .Show(toast =>
            {
                toast.Group = SyncGroup;
            });
    }

    // ── 系统通知 ──────────────────────────────────────────

    /// <summary>
    /// 显示存储配额警告
    /// </summary>
    public void ShowQuotaWarning(double usedPercent, long usedBytes, long totalBytes)
    {
        new ToastContentBuilder()
            .AddArgument("action", "manageStorage")
            .AddText("存储空间不足")
            .AddText($"已使用 {usedPercent:F1}% ({FormatBytes(usedBytes)} / {FormatBytes(totalBytes)})")
            .AddText("请清理不需要的文件或升级存储方案。")
            .AddButton(new ToastButton()
                .SetContent("管理存储")
                .AddArgument("action", "manageStorage"))
            .SetToastDuration(ToastDuration.Long)
            .SetToastScenario(ToastScenario.Reminder)
            .Show(toast =>
            {
                toast.Group = SystemGroup;
            });
    }

    /// <summary>
    /// 显示分享通知
    /// </summary>
    public void ShowSharedWithYou(string fileName, string sharedBy)
    {
        new ToastContentBuilder()
            .AddArgument("action", "openSharedFile")
            .AddArgument("fileName", fileName)
            .AddText("收到分享")
            .AddText($"{sharedBy} 分享了文件：{fileName}")
            .AddButton(new ToastButton()
                .SetContent("打开")
                .AddArgument("action", "openSharedFile")
                .AddArgument("fileName", fileName))
            .SetToastDuration(ToastDuration.Long)
            .Show(toast =>
            {
                toast.Group = SystemGroup;
            });
    }

    /// <summary>
    /// 显示新版本可用通知
    /// </summary>
    public void ShowUpdateAvailable(string version, string changelog)
    {
        new ToastContentBuilder()
            .AddArgument("action", "downloadUpdate")
            .AddArgument("version", version)
            .AddText("新版本可用")
            .AddText($"PrivateCloudDisk v{version}")
            .AddText(changelog)
            .AddButton(new ToastButton()
                .SetContent("立即更新")
                .AddArgument("action", "downloadUpdate")
                .AddArgument("version", version))
            .AddButton(new ToastButton()
                .SetContent("稍后提醒")
                .AddArgument("action", "dismissUpdate"))
            .SetToastDuration(ToastDuration.Long)
            .SetToastScenario(ToastScenario.Reminder)
            .Show(toast =>
            {
                toast.Group = SystemGroup;
            });
    }

    // ── 通知管理 ──────────────────────────────────────────

    /// <summary>
    /// 移除指定通知
    /// </summary>
    public void RemoveNotification(string tag)
    {
        try
        {
            ToastNotificationManagerCompat.History.Remove(tag);
        }
        catch { }
    }

    /// <summary>
    /// 移除指定组的所有通知
    /// </summary>
    public void RemoveGroup(string group)
    {
        try
        {
            ToastNotificationManagerCompat.History.RemoveGroup(group);
        }
        catch { }
    }

    /// <summary>
    /// 清除所有通知
    /// </summary>
    public void ClearAll()
    {
        try
        {
            ToastNotificationManagerCompat.History.Clear();
        }
        catch { }
    }

    // ── 辅助方法 ──────────────────────────────────────────

    private static string FormatBytes(long bytes)
    {
        string[] suffixes = { "B", "KB", "MB", "GB", "TB" };
        int order = 0;
        double size = bytes;
        while (size >= 1024 && order < suffixes.Length - 1)
        {
            order++;
            size /= 1024;
        }
        return $"{size:0.##} {suffixes[order]}";
    }
}

/// <summary>
/// Toast 通知激活处理器
/// 处理用户点击通知按钮的操作
/// </summary>
public class ToastNotificationActivationHandler
{
    private readonly IFileService _fileService;
    private readonly IAuthService _authService;

    public ToastNotificationActivationHandler(
        IFileService fileService, IAuthService authService)
    {
        _fileService = fileService;
        _authService = authService;
    }

    /// <summary>
    /// 处理通知激活
    /// </summary>
    public async Task HandleActivationAsync(IReadOnlyDictionary<string, string> arguments)
    {
        var action = arguments.GetValueOrDefault("action", "");

        switch (action)
        {
            case "openFile":
                var filePath = arguments.GetValueOrDefault("filePath", "");
                if (!string.IsNullOrEmpty(filePath) && File.Exists(filePath))
                {
                    System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                    {
                        FileName = filePath,
                        UseShellExecute = true
                    });
                }
                break;

            case "openFolder":
                var folderPath = arguments.GetValueOrDefault("filePath", "");
                if (!string.IsNullOrEmpty(folderPath))
                {
                    var dir = Path.GetDirectoryName(folderPath);
                    if (dir != null)
                    {
                        System.Diagnostics.Process.Start("explorer.exe", $"/select,\"{folderPath}\"");
                    }
                }
                break;

            case "resolveConflict":
                // 导航到冲突解决页面
                break;

            case "manageStorage":
                // 导航到存储管理页面
                break;

            case "downloadUpdate":
                // 启动更新下载
                break;
        }
    }
}