using PrivateCloudDisk.Helpers;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.ViewModels;

/// <summary>
/// 更新页面 ViewModel — 管理更新检查、下载、安装流程
/// </summary>
public class UpdateViewModel : ObservableObject
{
    private readonly IUpdateService _updateService;
    private readonly IVersionCheckService _versionCheckService;

    private UpdateStatus _currentStatus = UpdateStatus.NotChecked;
    private UpdateInfo? _updateInfo;
    private UpdateDownloadProgress? _downloadProgress;
    private string _statusMessage = "点击按钮检查更新";
    private string _statusDetail = string.Empty;
    private bool _isChecking;
    private bool _isDownloading;
    private bool _isInstalling;
    private bool _hasUpdate;
    private bool _isUpToDate;
    private bool _hasError;
    private string _errorMessage = string.Empty;
    private string _currentVersion = AppConfig.AppVersion;
    private string _latestVersion = "--";
    private string _releaseNotes = string.Empty;
    private string _packageSize = "--";
    private bool _autoCheckEnabled;
    private bool _autoDownloadEnabled;
    private bool _hotUpdateEnabled;
    private string _channel = "stable";
    private int _checkIntervalHours = 24;

    public UpdateViewModel(IUpdateService updateService, IVersionCheckService versionCheckService)
    {
        _updateService = updateService;
        _versionCheckService = versionCheckService;

        _updateService.StatusChanged += OnStatusChanged;
        _updateService.UpdateAvailable += OnUpdateAvailable;
        _updateService.DownloadProgressChanged += OnDownloadProgressChanged;

        // 命令
        CheckNowCommand = new AsyncRelayCommand(CheckNowAsync);
        DownloadCommand = new AsyncRelayCommand(DownloadAsync);
        InstallCommand = new AsyncRelayCommand(InstallAsync);
        CancelCommand = new RelayCommand(CancelDownload);
        SkipVersionCommand = new RelayCommand(SkipVersion);
        SaveSettingsCommand = new RelayCommand(SaveSettings);

        // 加载设置
        LoadSettings();
    }

    #region 属性

    public UpdateStatus CurrentStatus
    {
        get => _currentStatus;
        set
        {
            SetProperty(ref _currentStatus, value);
            UpdateDerivedProperties();
        }
    }

    public string StatusMessage
    {
        get => _statusMessage;
        set => SetProperty(ref _statusMessage, value);
    }

    public string StatusDetail
    {
        get => _statusDetail;
        set => SetProperty(ref _statusDetail, value);
    }

    public bool IsChecking
    {
        get => _isChecking;
        set => SetProperty(ref _isChecking, value);
    }

    public bool IsDownloading
    {
        get => _isDownloading;
        set => SetProperty(ref _isDownloading, value);
    }

    public bool IsInstalling
    {
        get => _isInstalling;
        set => SetProperty(ref _isInstalling, value);
    }

    public bool HasUpdate
    {
        get => _hasUpdate;
        set => SetProperty(ref _hasUpdate, value);
    }

    public bool IsUpToDate
    {
        get => _isUpToDate;
        set => SetProperty(ref _isUpToDate, value);
    }

    public bool HasError
    {
        get => _hasError;
        set => SetProperty(ref _hasError, value);
    }

    public string ErrorMessage
    {
        get => _errorMessage;
        set => SetProperty(ref _errorMessage, value);
    }

    public string CurrentVersion
    {
        get => _currentVersion;
        set => SetProperty(ref _currentVersion, value);
    }

    public string LatestVersion
    {
        get => _latestVersion;
        set => SetProperty(ref _latestVersion, value);
    }

    public string ReleaseNotes
    {
        get => _releaseNotes;
        set => SetProperty(ref _releaseNotes, value);
    }

    public string PackageSize
    {
        get => _packageSize;
        set => SetProperty(ref _packageSize, value);
    }

    public double DownloadProgressPercent =>
        _downloadProgress?.ProgressPercent ?? 0;

    public string DownloadSpeedDisplay =>
        _downloadProgress != null
            ? FormatSpeed(_downloadProgress.SpeedBytesPerSecond)
            : "--";

    public string DownloadETADisplay =>
        _downloadProgress?.EstimatedTimeRemaining != null
            ? FormatTimeSpan(_downloadProgress.EstimatedTimeRemaining)
            : "--";

    public string DownloadSizeDisplay =>
        _downloadProgress != null
            ? $"{FormatBytes(_downloadProgress.DownloadedBytes)} / {FormatBytes(_downloadProgress.TotalBytes)}"
            : "--";

    // 设置属性
    public bool AutoCheckEnabled
    {
        get => _autoCheckEnabled;
        set => SetProperty(ref _autoCheckEnabled, value);
    }

    public bool AutoDownloadEnabled
    {
        get => _autoDownloadEnabled;
        set => SetProperty(ref _autoDownloadEnabled, value);
    }

    public bool HotUpdateEnabled
    {
        get => _hotUpdateEnabled;
        set => SetProperty(ref _hotUpdateEnabled, value);
    }

    public string Channel
    {
        get => _channel;
        set => SetProperty(ref _channel, value);
    }

    public int CheckIntervalHours
    {
        get => _checkIntervalHours;
        set => SetProperty(ref _checkIntervalHours, value);
    }

    #endregion

    #region 命令

    public AsyncRelayCommand CheckNowCommand { get; }
    public AsyncRelayCommand DownloadCommand { get; }
    public AsyncRelayCommand InstallCommand { get; }
    public RelayCommand CancelCommand { get; }
    public RelayCommand SkipVersionCommand { get; }
    public RelayCommand SaveSettingsCommand { get; }

    private async Task CheckNowAsync()
    {
        try
        {
            IsChecking = true;
            HasError = false;
            StatusMessage = "正在检查更新...";
            StatusDetail = "正在连接更新服务器";

            var result = await _versionCheckService.CheckNowAsync();

            if (result.Success && result.UpdateInfo?.HasUpdate == true)
            {
                HasUpdate = true;
                LatestVersion = result.UpdateInfo.LatestVersion;
                ReleaseNotes = result.UpdateInfo.ReleaseNotes;
                PackageSize = FormatBytes(result.UpdateInfo.PackageSize);
                StatusMessage = $"发现新版本 v{result.UpdateInfo.LatestVersion}";
                StatusDetail = result.UpdateInfo.IsMajorUpdate
                    ? "这是一个大版本更新，建议更新以获得最佳体验"
                    : "新版本可用，包含功能改进和修复";
            }
            else if (result.Success)
            {
                IsUpToDate = true;
                StatusMessage = "已是最新版本";
                StatusDetail = $"当前版本 v{AppConfig.AppVersion} 是最新的";
            }
            else
            {
                HasError = true;
                ErrorMessage = result.ErrorMessage ?? "检查失败";
                StatusMessage = "检查更新失败";
                StatusDetail = ErrorMessage;
            }
        }
        catch (Exception ex)
        {
            HasError = true;
            ErrorMessage = ex.Message;
            StatusMessage = "检查更新失败";
            StatusDetail = ex.Message;
        }
        finally
        {
            IsChecking = false;
        }
    }

    private async Task DownloadAsync()
    {
        try
        {
            IsDownloading = true;
            HasError = false;
            StatusMessage = "正在下载更新...";

            var progress = new Progress<UpdateDownloadProgress>(p =>
            {
                _downloadProgress = p;
                OnPropertyChanged(nameof(DownloadProgressPercent));
                OnPropertyChanged(nameof(DownloadSpeedDisplay));
                OnPropertyChanged(nameof(DownloadETADisplay));
                OnPropertyChanged(nameof(DownloadSizeDisplay));
                StatusDetail = $"{p.ProgressPercent:F1}% - {FormatSpeed(p.SpeedBytesPerSecond)}/s";
            });

            await _updateService.DownloadUpdateAsync(progress);
        }
        catch (Exception ex)
        {
            HasError = true;
            ErrorMessage = ex.Message;
            StatusMessage = "下载失败";
            StatusDetail = ex.Message;
        }
        finally
        {
            IsDownloading = false;
        }
    }

    private async Task InstallAsync()
    {
        try
        {
            IsInstalling = true;
            StatusMessage = "正在安装更新...";
            StatusDetail = "请不要关闭应用";

            await _updateService.InstallUpdateAsync();
        }
        catch (Exception ex)
        {
            HasError = true;
            ErrorMessage = ex.Message;
            StatusMessage = "安装失败";
        }
        finally
        {
            IsInstalling = false;
        }
    }

    private void CancelDownload()
    {
        _updateService.CancelDownload();
        StatusMessage = "下载已取消";
    }

    private void SkipVersion()
    {
        _updateService.SkipCurrentVersion();
        StatusMessage = "已跳过此版本";
        HasUpdate = false;
    }

    private void SaveSettings()
    {
        _updateService.Settings.AutoCheckEnabled = AutoCheckEnabled;
        _updateService.Settings.AutoDownload = AutoDownloadEnabled;
        _updateService.Settings.HotUpdateEnabled = HotUpdateEnabled;
        _updateService.Settings.Channel = Channel;
        _updateService.Settings.CheckIntervalHours = CheckIntervalHours;
        _updateService.SaveSettings();

        // 更新定时检查
        if (AutoCheckEnabled)
            _versionCheckService.StartPeriodicCheck();
        else
            _versionCheckService.StopPeriodicCheck();
    }

    #endregion

    #region 事件处理

    private void OnStatusChanged(object? sender, UpdateStatus status)
    {
        _ = Windows.ApplicationModel.Core.CoreApplication.MainView?.DispatcherQueue?.TryEnqueue(() =>
        {
            CurrentStatus = status;
        });
    }

    private void OnUpdateAvailable(object? sender, UpdateInfo info)
    {
        _ = Windows.ApplicationModel.Core.CoreApplication.MainView?.DispatcherQueue?.TryEnqueue(() =>
        {
            _updateInfo = info;
            HasUpdate = true;
            LatestVersion = info.LatestVersion;
            ReleaseNotes = info.ReleaseNotes;
            PackageSize = FormatBytes(info.PackageSize);
            StatusMessage = $"发现新版本 v{info.LatestVersion}";
        });
    }

    private void OnDownloadProgressChanged(object? sender, UpdateDownloadProgress progress)
    {
        _ = Windows.ApplicationModel.Core.CoreApplication.MainView?.DispatcherQueue?.TryEnqueue(() =>
        {
            _downloadProgress = progress;
            OnPropertyChanged(nameof(DownloadProgressPercent));
            OnPropertyChanged(nameof(DownloadSpeedDisplay));
            OnPropertyChanged(nameof(DownloadETADisplay));
            OnPropertyChanged(nameof(DownloadSizeDisplay));
        });
    }

    #endregion

    #region 辅助方法

    private void UpdateDerivedProperties()
    {
        IsChecking = _currentStatus == UpdateStatus.Checking;
        IsDownloading = _currentStatus == UpdateStatus.Downloading;
        IsInstalling = _currentStatus == UpdateStatus.Installing;
        IsUpToDate = _currentStatus == UpdateStatus.UpToDate;
    }

    private void LoadSettings()
    {
        var settings = _updateService.Settings;
        AutoCheckEnabled = settings.AutoCheckEnabled;
        AutoDownloadEnabled = settings.AutoDownload;
        HotUpdateEnabled = settings.HotUpdateEnabled;
        Channel = settings.Channel;
        CheckIntervalHours = settings.CheckIntervalHours;
    }

    private static string FormatBytes(long bytes) => bytes switch
    {
        < 1024 => $"{bytes} B",
        < 1024 * 1024 => $"{bytes / 1024.0:F1} KB",
        < 1024 * 1024 * 1024 => $"{bytes / (1024.0 * 1024):F1} MB",
        _ => $"{bytes / (1024.0 * 1024 * 1024):F2} GB"
    };

    private static string FormatSpeed(double bytesPerSecond) => bytesPerSecond switch
    {
        < 1024 => $"{bytesPerSecond:F0} B",
        < 1024 * 1024 => $"{bytesPerSecond / 1024:F1} KB",
        < 1024 * 1024 * 1024 => $"{bytesPerSecond / (1024 * 1024):F1} MB",
        _ => $"{bytesPerSecond / (1024 * 1024 * 1024):F2} GB"
    };

    private static string FormatTimeSpan(TimeSpan ts)
    {
        if (ts.TotalHours >= 1) return $"{(int)ts.TotalHours}时{ts.Minutes}分";
        if (ts.TotalMinutes >= 1) return $"{ts.Minutes}分{ts.Seconds}秒";
        return $"{ts.Seconds}秒";
    }

    #endregion
}