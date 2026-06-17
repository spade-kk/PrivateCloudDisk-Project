using System;
using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Threading.Tasks;
using System.Windows.Input;
using Microsoft.UI.Dispatching;
using PrivateCloudDisk.Downloader.Services;

namespace PrivateCloudDisk.Downloader.ViewModels;

/// <summary>
/// 下载器主 ViewModel
/// </summary>
public class DownloaderViewModel : INotifyPropertyChanged
{
    private readonly DownloadService _downloadService;
    private readonly InstallService _installService;
    private readonly DispatcherQueue _dispatcher;

    private string _statusMessage = "准备下载...";
    private string _statusDetail = "正在连接服务器获取最新版本信息";
    private double _progressPercent;
    private string _speedDisplay = "--";
    private string _etaDisplay = "--";
    private string _sizeDisplay = "--";
    private string _downloadedDisplay = "--";
    private bool _isDownloading;
    private bool _isInstalling;
    private bool _isVerifying;
    private bool _isCompleted;
    private bool _hasError;
    private string _errorMessage = string.Empty;
    private int _currentStep; // 0=连接, 1=下载, 2=验证, 3=安装, 4=完成
    private string _appVersion = "1.0.0";
    private string _appSize = "156 MB";
    private string _releaseNotes = "• 全新的 WinUI 3 界面设计\n• 虚拟磁盘挂载功能\n• 分片上传与断点续传\n• 企业级安全加密\n• 多端实时同步";

    // 下载任务
    private DownloadTask? _currentTask;

    public DownloaderViewModel(DownloadService downloadService, InstallService installService)
    {
        _downloadService = downloadService;
        _installService = installService;
        _dispatcher = DispatcherQueue.GetForCurrentThread();

        _downloadService.TaskStatusChanged += OnTaskStatusChanged;
        _downloadService.ProgressChanged += OnProgressChanged;

        StartDownloadCommand = new AsyncRelayCommand(StartDownloadAsync);
        PauseResumeCommand = new RelayCommand(OnPauseResume);
        CancelCommand = new RelayCommand(OnCancel);
        InstallCommand = new AsyncRelayCommand(InstallAsync);
        LaunchCommand = new RelayCommand(() => _installService.LaunchApp());
    }

    #region 属性

    public string StatusMessage
    {
        get => _statusMessage;
        set { _statusMessage = value; OnPropertyChanged(); }
    }

    public string StatusDetail
    {
        get => _statusDetail;
        set { _statusDetail = value; OnPropertyChanged(); }
    }

    public double ProgressPercent
    {
        get => _progressPercent;
        set { _progressPercent = value; OnPropertyChanged(); }
    }

    public string SpeedDisplay
    {
        get => _speedDisplay;
        set { _speedDisplay = value; OnPropertyChanged(); }
    }

    public string ETADisplay
    {
        get => _etaDisplay;
        set { _etaDisplay = value; OnPropertyChanged(); }
    }

    public string SizeDisplay
    {
        get => _sizeDisplay;
        set { _sizeDisplay = value; OnPropertyChanged(); }
    }

    public string DownloadedDisplay
    {
        get => _downloadedDisplay;
        set { _downloadedDisplay = value; OnPropertyChanged(); }
    }

    public bool IsDownloading
    {
        get => _isDownloading;
        set { _isDownloading = value; OnPropertyChanged(); }
    }

    public bool IsInstalling
    {
        get => _isInstalling;
        set { _isInstalling = value; OnPropertyChanged(); }
    }

    public bool IsVerifying
    {
        get => _isVerifying;
        set { _isVerifying = value; OnPropertyChanged(); }
    }

    public bool IsCompleted
    {
        get => _isCompleted;
        set { _isCompleted = value; OnPropertyChanged(); }
    }

    public bool HasError
    {
        get => _hasError;
        set { _hasError = value; OnPropertyChanged(); }
    }

    public string ErrorMessage
    {
        get => _errorMessage;
        set { _errorMessage = value; OnPropertyChanged(); }
    }

    public int CurrentStep
    {
        get => _currentStep;
        set { _currentStep = value; OnPropertyChanged(); OnPropertyChanged(nameof(IsStep1)); OnPropertyChanged(nameof(IsStep2)); OnPropertyChanged(nameof(IsStep3)); OnPropertyChanged(nameof(IsStep4)); OnPropertyChanged(nameof(IsStep5)); }
    }

    public bool IsStep1 => CurrentStep >= 0;
    public bool IsStep2 => CurrentStep >= 1;
    public bool IsStep3 => CurrentStep >= 2;
    public bool IsStep4 => CurrentStep >= 3;
    public bool IsStep5 => CurrentStep >= 4;

    public string AppVersion
    {
        get => _appVersion;
        set { _appVersion = value; OnPropertyChanged(); }
    }

    public string AppSize
    {
        get => _appSize;
        set { _appSize = value; OnPropertyChanged(); }
    }

    public string ReleaseNotes
    {
        get => _releaseNotes;
        set { _releaseNotes = value; OnPropertyChanged(); }
    }

    #endregion

    #region 命令

    public ICommand StartDownloadCommand { get; }
    public ICommand PauseResumeCommand { get; }
    public ICommand CancelCommand { get; }
    public ICommand InstallCommand { get; }
    public ICommand LaunchCommand { get; }

    private async Task StartDownloadAsync()
    {
        try
        {
            IsDownloading = true;
            HasError = false;
            CurrentStep = 1;
            StatusMessage = "正在下载...";
            StatusDetail = "正在从服务器获取文件";

            // 从后端 API 获取最新版本下载地址
            var downloadUrl = $"{AppConfig.PlatformBaseUrl}/api/v1/version/latest/download";
            var savePath = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            var fileName = "PrivateCloudDisk_Setup_1.0.0.pcdpkg";

            _currentTask = new DownloadTask
            {
                Url = downloadUrl,
                SavePath = savePath,
                FileName = fileName,
                TotalSize = 156 * 1024 * 1024 // 156 MB
            };

            SizeDisplay = _currentTask.SizeDisplay;
            await _downloadService.StartDownloadAsync(_currentTask.Id);
        }
        catch (Exception ex)
        {
            HasError = true;
            ErrorMessage = ex.Message;
            StatusMessage = "下载失败";
            IsDownloading = false;
        }
    }

    private void OnPauseResume()
    {
        if (_currentTask == null) return;

        if (_currentTask.Status == DownloadTaskStatus.Downloading)
        {
            _downloadService.PauseDownload(_currentTask.Id);
            StatusMessage = "已暂停";
        }
        else if (_currentTask.Status == DownloadTaskStatus.Paused)
        {
            _ = StartDownloadAsync();
        }
    }

    private void OnCancel()
    {
        if (_currentTask != null)
        {
            _downloadService.CancelDownload(_currentTask.Id);
            IsDownloading = false;
            StatusMessage = "已取消";
        }
    }

    private async Task InstallAsync()
    {
        if (_currentTask == null || _currentTask.Status != DownloadTaskStatus.Completed)
            return;

        IsInstalling = true;
        CurrentStep = 3;
        StatusMessage = "正在安装...";

        var packagePath = System.IO.Path.Combine(_currentTask.SavePath, _currentTask.FileName);
        var progress = new Progress<string>(msg =>
        {
            _dispatcher.TryEnqueue(() => StatusDetail = msg);
        });

        var result = await _installService.InstallAsync(packagePath, progress);

        if (result)
        {
            CurrentStep = 4;
            StatusMessage = "安装完成！";
            StatusDetail = "PrivateCloudDisk 已成功安装到您的计算机";
            IsCompleted = true;
        }
        else
        {
            HasError = true;
            ErrorMessage = "安装失败";
            StatusMessage = "安装失败";
        }

        IsInstalling = false;
    }

    #endregion

    private void OnTaskStatusChanged(object? sender, DownloadTask task)
    {
        _dispatcher.TryEnqueue(() =>
        {
            switch (task.Status)
            {
                case DownloadTaskStatus.Completed:
                    CurrentStep = 2;
                    StatusMessage = "下载完成";
                    StatusDetail = "正在验证文件完整性...";
                    IsDownloading = false;
                    break;
                case DownloadTaskStatus.Failed:
                    HasError = true;
                    ErrorMessage = task.ErrorMessage ?? "下载失败";
                    StatusMessage = "下载失败";
                    IsDownloading = false;
                    break;
                case DownloadTaskStatus.Verifying:
                    IsVerifying = true;
                    StatusMessage = "正在验证...";
                    break;
            }
        });
    }

    private void OnProgressChanged(object? sender, DownloadProgressData progress)
    {
        _dispatcher.TryEnqueue(() =>
        {
            ProgressPercent = progress.ProgressPercent;
            SpeedDisplay = progress.SpeedDisplay;
            ETADisplay = progress.ETADisplay;
            DownloadedDisplay = DownloadTask.FormatSizeStatic(progress.BytesDownloaded);
            StatusDetail = $"已下载 {DownloadedDisplay} / {SizeDisplay}";
        });
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    protected void OnPropertyChanged([CallerMemberName] string? name = null)
        => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}

// 简化版 RelayCommand（避免依赖 CommunityToolkit）
public class RelayCommand : ICommand
{
    private readonly Action _execute;
    private readonly Func<bool>? _canExecute;
    public RelayCommand(Action execute, Func<bool>? canExecute = null)
    { _execute = execute; _canExecute = canExecute; }
    public bool CanExecute(object? p) => _canExecute?.Invoke() ?? true;
    public void Execute(object? p) => _execute();
    public event EventHandler? CanExecuteChanged;
}

public class AsyncRelayCommand : ICommand
{
    private readonly Func<Task> _execute;
    private readonly Func<bool>? _canExecute;
    private bool _isExecuting;
    public AsyncRelayCommand(Func<Task> execute, Func<bool>? canExecute = null)
    { _execute = execute; _canExecute = canExecute; }
    public bool CanExecute(object? p) => !_isExecuting && (_canExecute?.Invoke() ?? true);
    public async void Execute(object? p)
    {
        _isExecuting = true;
        try { await _execute(); }
        finally { _isExecuting = false; }
    }
    public event EventHandler? CanExecuteChanged;
}

public static class AppConfig
{
    public static string PlatformBaseUrl { get; set; } = "http://localhost:8090";
    public static string FileServiceBaseUrl { get; set; } = "http://localhost:8000";
}