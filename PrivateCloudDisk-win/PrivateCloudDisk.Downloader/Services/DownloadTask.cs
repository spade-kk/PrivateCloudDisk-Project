using System;
using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace PrivateCloudDisk.Downloader.Services;

/// <summary>
/// 下载任务状态枚举
/// </summary>
public enum DownloadTaskStatus
{
    Pending,
    Downloading,
    Paused,
    Completed,
    Verifying,
    Failed,
    Cancelled
}

/// <summary>
/// 下载任务模型
/// </summary>
public class DownloadTask : INotifyPropertyChanged
{
    private string _id = Guid.NewGuid().ToString("N")[..8];
    private string _url = string.Empty;
    private string _savePath = string.Empty;
    private string _fileName = string.Empty;
    private long _totalSize;
    private long _downloadedSize;
    private DownloadTaskStatus _status;
    private string? _errorMessage;
    private string? _expectedHash;
    private DateTime _createdAt = DateTime.UtcNow;

    public string Id
    {
        get => _id;
        set { _id = value; OnPropertyChanged(); }
    }

    public string Url
    {
        get => _url;
        set { _url = value; OnPropertyChanged(); }
    }

    public string SavePath
    {
        get => _savePath;
        set { _savePath = value; OnPropertyChanged(); }
    }

    public string FileName
    {
        get => _fileName;
        set { _fileName = value; OnPropertyChanged(); }
    }

    public string FullPath => Path.Combine(SavePath, FileName);

    public long TotalSize
    {
        get => _totalSize;
        set { _totalSize = value; OnPropertyChanged(); OnPropertyChanged(nameof(SizeDisplay)); }
    }

    public long DownloadedSize
    {
        get => _downloadedSize;
        set { _downloadedSize = value; OnPropertyChanged(); OnPropertyChanged(nameof(ProgressPercent)); OnPropertyChanged(nameof(DownloadedDisplay)); }
    }

    public double ProgressPercent => TotalSize > 0 ? (double)DownloadedSize / TotalSize : 0;

    public DownloadTaskStatus Status
    {
        get => _status;
        set { _status = value; OnPropertyChanged(); OnPropertyChanged(nameof(IsDownloading)); OnPropertyChanged(nameof(IsCompleted)); }
    }

    public bool IsDownloading => Status == DownloadTaskStatus.Downloading;
    public bool IsCompleted => Status == DownloadTaskStatus.Completed;

    public string? ErrorMessage
    {
        get => _errorMessage;
        set { _errorMessage = value; OnPropertyChanged(); }
    }

    public string? ExpectedHash
    {
        get => _expectedHash;
        set { _expectedHash = value; OnPropertyChanged(); }
    }

    public DateTime CreatedAt
    {
        get => _createdAt;
        set { _createdAt = value; OnPropertyChanged(); }
    }

    public string SizeDisplay => FormatFileSize(TotalSize);
    public string DownloadedDisplay => FormatFileSize(DownloadedSize);

    private static string FormatFileSize(long bytes)
    {
        if (bytes < 1024) return $"{bytes} B";
        if (bytes < 1024 * 1024) return $"{bytes / 1024.0:F1} KB";
        if (bytes < 1024 * 1024 * 1024) return $"{bytes / (1024.0 * 1024):F1} MB";
        return $"{bytes / (1024.0 * 1024 * 1024):F2} GB";
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    protected void OnPropertyChanged([CallerMemberName] string? propertyName = null)
    {
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
    }
}

/// <summary>
/// 下载进度数据
/// </summary>
public class DownloadProgressData
{
    public string TaskId { get; set; } = string.Empty;
    public long DownloadedSize { get; set; }
    public long TotalSize { get; set; }
    public double Speed { get; set; }
    public TimeSpan ETA { get; set; }
    public double ProgressPercent => TotalSize > 0 ? (double)DownloadedSize / TotalSize : 0;
}

/// <summary>
/// 下载器应用配置
/// </summary>
public static class AppConfig
{
    public const string PlatformBaseUrl = "http://localhost:8080";
    public const string AppName = "PrivateCloudDisk";
    public const string AppVersion = "1.0.0";
}