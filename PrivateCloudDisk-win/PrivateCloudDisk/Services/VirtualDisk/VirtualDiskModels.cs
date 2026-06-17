using System;
using System.Collections.Generic;
using System.Text.Json.Serialization;

namespace PrivateCloudDisk.Services.VirtualDisk;

#region 同步引擎数据模型

/// <summary>
/// 同步根配置
/// </summary>
public class SyncRootConfig
{
    public string Id { get; set; } = string.Empty;
    public string DisplayName { get; set; } = "PrivateCloudDisk";
    public string Path { get; set; } = string.Empty;
    public string RootNodeId { get; set; } = "root";
    public SyncPolicy Policy { get; set; } = new();
    public SyncRootStatus Status { get; set; } = SyncRootStatus.Disconnected;
    public DateTime LastSyncTime { get; set; }
}

/// <summary>
/// 同步策略
/// </summary>
public class SyncPolicy
{
    /// <summary>自动同步间隔(秒)</summary>
    public int SyncIntervalSeconds { get; set; } = 300;

    /// <summary>是否启用实时同步</summary>
    public bool RealTimeSync { get; set; } = true;

    /// <summary>按需下载(placeholder)，还是全量下载</summary>
    public bool OnDemandDownload { get; set; } = true;

    /// <summary>最大缓存大小(MB)</summary>
    public long MaxCacheSizeMB { get; set; } = 10240;

    /// <summary>文件冲突策略</summary>
    public ConflictResolution ConflictResolution { get; set; } = ConflictResolution.KeepBoth;

    /// <summary>排除的文件扩展名</summary>
    public List<string> ExcludedExtensions { get; set; } = new();
}

/// <summary>
/// 文件冲突解决策略
/// </summary>
public enum ConflictResolution
{
    /// <summary>保留两者（重命名本地文件）</summary>
    KeepBoth,
    /// <summary>本地版本覆盖远程</summary>
    LocalWins,
    /// <summary>远程版本覆盖本地</summary>
    RemoteWins
}

/// <summary>
/// 同步根状态
/// </summary>
public enum SyncRootStatus
{
    Disconnected,
    Connecting,
    Connected,
    Syncing,
    Error,
    Paused
}

/// <summary>
/// 同步状态
/// </summary>
public enum SyncStatus
{
    Idle,
    Downloading,
    Uploading,
    Syncing,
    Error,
    Paused
}

/// <summary>
/// 云端文件条目（映射后端 NodeItem）
/// </summary>
public class CloudFileEntry
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string ParentId { get; set; } = string.Empty;
    public bool IsDirectory { get; set; }
    public long Size { get; set; }
    public string? FileType { get; set; }
    public string? Checksum { get; set; }
    public DateTime? LastModified { get; set; }
    public bool IsPlaceholder { get; set; } = true;
    public string? LocalPath { get; set; }
    public double? DownloadProgress { get; set; }
}

/// <summary>
/// 同步事件类型
/// </summary>
public enum SyncEventType
{
    FileCreated,
    FileModified,
    FileRenamed,
    FileDeleted,
    FileDownloaded,
    FileUploaded,
    SyncStarted,
    SyncCompleted,
    SyncError,
    ConflictDetected
}

/// <summary>
/// 同步事件
/// </summary>
public class SyncEvent
{
    public SyncEventType Type { get; set; }
    public string? FilePath { get; set; }
    public string? FileId { get; set; }
    public string? Message { get; set; }
    public Exception? Exception { get; set; }
    public DateTime Timestamp { get; set; } = DateTime.UtcNow;
}

/// <summary>
/// 同步进度
/// </summary>
public class SyncProgress
{
    public SyncStatus Status { get; set; }
    public int TotalFiles { get; set; }
    public int ProcessedFiles { get; set; }
    public long TotalBytes { get; set; }
    public long TransferredBytes { get; set; }
    public double SpeedBytesPerSecond { get; set; }
    public string? CurrentFile { get; set; }
    public int ErrorCount { get; set; }
}

/// <summary>
/// 虚拟磁盘状态
/// </summary>
public class VirtualDiskStatus
{
    public bool IsMounted { get; set; }
    public string? MountPoint { get; set; }
    public string? SyncRootPath { get; set; }
    public SyncRootStatus Status { get; set; }
    public SyncProgress? CurrentProgress { get; set; }
    public long TotalSpace { get; set; }
    public long UsedSpace { get; set; }
    public long FreeSpace { get; set; }
    public int FileCount { get; set; }
    public int FolderCount { get; set; }
}

#endregion

#region 下载器数据模型

/// <summary>
/// 下载任务信息
/// </summary>
public class DownloadTask
{
    public string Id { get; set; } = Guid.NewGuid().ToString("N");
    public string Url { get; set; } = string.Empty;
    public string SavePath { get; set; } = string.Empty;
    public string FileName { get; set; } = string.Empty;
    public long TotalSize { get; set; }
    public long DownloadedSize { get; set; }
    public string? ExpectedHash { get; set; }
    public DownloadTaskStatus Status { get; set; } = DownloadTaskStatus.Pending;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime? CompletedAt { get; set; }
    public string? ErrorMessage { get; set; }
    public int RetryCount { get; set; }
    public const int MaxRetryCount = 3;

    public double Progress => TotalSize > 0 ? (double)DownloadedSize / TotalSize * 100 : 0;
    public string ProgressDisplay => $"{Progress:F1}%";
    public string SizeDisplay => FormatSize(TotalSize);
    public string DownloadedDisplay => FormatSize(DownloadedSize);

    private static string FormatSize(long bytes) => bytes switch
    {
        < 1024 => $"{bytes} B",
        < 1024 * 1024 => $"{bytes / 1024.0:F1} KB",
        < 1024 * 1024 * 1024 => $"{bytes / (1024.0 * 1024):F1} MB",
        _ => $"{bytes / (1024.0 * 1024 * 1024):F2} GB"
    };

    public static string FormatSizeStatic(long bytes) => bytes switch
    {
        < 1024 => $"{bytes} B",
        < 1024 * 1024 => $"{bytes / 1024.0:F1} KB",
        < 1024 * 1024 * 1024 => $"{bytes / (1024.0 * 1024):F1} MB",
        _ => $"{bytes / (1024.0 * 1024 * 1024):F2} GB"
    };
}

public enum DownloadTaskStatus
{
    Pending,
    Downloading,
    Paused,
    Verifying,
    Completed,
    Failed,
    Cancelled
}

/// <summary>
/// 下载进度数据
/// </summary>
public class DownloadProgressData
{
    public double ProgressPercent { get; set; }
    public long BytesDownloaded { get; set; }
    public long TotalBytes { get; set; }
    public double SpeedBytesPerSecond { get; set; }
    public TimeSpan EstimatedTimeRemaining { get; set; }
    public string? CurrentFile { get; set; }

    public string SpeedDisplay => SpeedBytesPerSecond switch
    {
        < 1024 => $"{SpeedBytesPerSecond:F0} B/s",
        < 1024 * 1024 => $"{SpeedBytesPerSecond / 1024:F1} KB/s",
        < 1024 * 1024 * 1024 => $"{SpeedBytesPerSecond / (1024 * 1024):F1} MB/s",
        _ => $"{SpeedBytesPerSecond / (1024 * 1024 * 1024):F2} GB/s"
    };

    public string ETADisplay => EstimatedTimeRemaining.TotalSeconds > 0
        ? EstimatedTimeRemaining.TotalHours >= 1
            ? $"{(int)EstimatedTimeRemaining.TotalHours}h {EstimatedTimeRemaining.Minutes}m"
            : $"{EstimatedTimeRemaining.Minutes}m {EstimatedTimeRemaining.Seconds}s"
        : "--";
}

#endregion