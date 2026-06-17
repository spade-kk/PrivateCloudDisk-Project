using PrivateCloudDisk.Services.VirtualDisk;
using PrivateCloudDisk.Services.Interfaces;
using System;
using System.Collections.ObjectModel;
using System.Threading.Tasks;
using System.Windows.Input;

namespace PrivateCloudDisk.ViewModels;

/// <summary>
/// 虚拟磁盘管理 ViewModel
/// </summary>
public class VirtualDiskViewModel : ObservableObject
{
    private readonly VirtualDiskService _virtualDisk;
    private readonly ISettingsService _settings;

    private bool _isMounted;
    private string? _syncRootPath;
    private string? _driveLetter;
    private string _displayName = "PrivateCloudDisk";
    private SyncRootStatus _status = SyncRootStatus.Disconnected;
    private SyncProgress? _currentProgress;
    private ObservableCollection<SyncEvent> _syncLog = new();
    private string _selectedTab = "overview";

    // 配额信息
    private long _totalSpace;
    private long _usedSpace;
    private long _freeSpace;
    private int _fileCount;
    private int _folderCount;

    // 同步策略
    private bool _realTimeSync = true;
    private bool _onDemandDownload = true;
    private int _syncInterval = 300;
    private long _maxCacheSize = 10240;
    private int _selectedConflictResolution;
    private string _excludedExtensions = string.Empty;

    public VirtualDiskViewModel(VirtualDiskService virtualDisk, ISettingsService settings)
    {
        _virtualDisk = virtualDisk;
        _settings = settings;

        _virtualDisk.StatusChanged += OnStatusChanged;
        _virtualDisk.SyncEventOccurred += OnSyncEvent;

        MountCommand = new AsyncRelayCommand(MountAsync);
        UnmountCommand = new AsyncRelayCommand(UnmountAsync);
        SyncNowCommand = new AsyncRelayCommand(SyncNowAsync);
        PauseCommand = new RelayCommand(() => _virtualDisk.PauseSync());
        ResumeCommand = new RelayCommand(() => _virtualDisk.ResumeSync());
        SaveSettingsCommand = new RelayCommand(SaveSettings);
        BrowseSyncPathCommand = new AsyncRelayCommand(BrowseSyncPathAsync);

        LoadSettings();
    }

    #region 属性

    public bool IsMounted
    {
        get => _isMounted;
        set => SetProperty(ref _isMounted, value);
    }

    public string? SyncRootPath
    {
        get => _syncRootPath;
        set => SetProperty(ref _syncRootPath, value);
    }

    public string? DriveLetter
    {
        get => _driveLetter;
        set => SetProperty(ref _driveLetter, value);
    }

    public string DisplayName
    {
        get => _displayName;
        set => SetProperty(ref _displayName, value);
    }

    public SyncRootStatus Status
    {
        get => _status;
        set => SetProperty(ref _status, value);
    }

    public SyncProgress? CurrentProgress
    {
        get => _currentProgress;
        set => SetProperty(ref _currentProgress, value);
    }

    public ObservableCollection<SyncEvent> SyncLog
    {
        get => _syncLog;
        set => SetProperty(ref _syncLog, value);
    }

    public string SelectedTab
    {
        get => _selectedTab;
        set => SetProperty(ref _selectedTab, value);
    }

    public long TotalSpace
    {
        get => _totalSpace;
        set => SetProperty(ref _totalSpace, value);
    }

    public long UsedSpace
    {
        get => _usedSpace;
        set => SetProperty(ref _usedSpace, value);
    }

    public long FreeSpace
    {
        get => _freeSpace;
        set => SetProperty(ref _freeSpace, value);
    }

    public int FileCount
    {
        get => _fileCount;
        set => SetProperty(ref _fileCount, value);
    }

    public int FolderCount
    {
        get => _folderCount;
        set => SetProperty(ref _folderCount, value);
    }

    public bool RealTimeSync
    {
        get => _realTimeSync;
        set => SetProperty(ref _realTimeSync, value);
    }

    public bool OnDemandDownload
    {
        get => _onDemandDownload;
        set => SetProperty(ref _onDemandDownload, value);
    }

    public int SyncInterval
    {
        get => _syncInterval;
        set => SetProperty(ref _syncInterval, value);
    }

    public long MaxCacheSize
    {
        get => _maxCacheSize;
        set => SetProperty(ref _maxCacheSize, value);
    }

    public int SelectedConflictResolution
    {
        get => _selectedConflictResolution;
        set => SetProperty(ref _selectedConflictResolution, value);
    }

    public string ExcludedExtensions
    {
        get => _excludedExtensions;
        set => SetProperty(ref _excludedExtensions, value);
    }

    public string StatusDisplay => Status switch
    {
        SyncRootStatus.Disconnected => "未连接",
        SyncRootStatus.Connecting => "连接中...",
        SyncRootStatus.Connected => "已连接",
        SyncRootStatus.Syncing => "同步中...",
        SyncRootStatus.Error => "错误",
        SyncRootStatus.Paused => "已暂停",
        _ => "未知"
    };

    public string SpaceDisplay
    {
        get
        {
            if (TotalSpace <= 0) return "-- / --";
            var usedStr = UsedSpace switch
            {
                < 1024 * 1024 => $"{UsedSpace / 1024.0:F1} KB",
                < 1024 * 1024 * 1024 => $"{UsedSpace / (1024.0 * 1024):F1} MB",
                _ => $"{UsedSpace / (1024.0 * 1024 * 1024):F2} GB"
            };
            var totalStr = TotalSpace switch
            {
                < 1024 * 1024 * 1024 => $"{TotalSpace / (1024.0 * 1024):F1} MB",
                _ => $"{TotalSpace / (1024.0 * 1024 * 1024):F2} GB"
            };
            return $"{usedStr} / {totalStr}";
        }
    }

    public double SpacePercent => TotalSpace > 0 ? (double)UsedSpace / TotalSpace * 100 : 0;

    #endregion

    #region 命令

    public ICommand MountCommand { get; }
    public ICommand UnmountCommand { get; }
    public ICommand SyncNowCommand { get; }
    public ICommand PauseCommand { get; }
    public ICommand ResumeCommand { get; }
    public ICommand SaveSettingsCommand { get; }
    public ICommand BrowseSyncPathCommand { get; }

    private async Task MountAsync()
    {
        if (string.IsNullOrEmpty(SyncRootPath))
        {
            SyncRootPath = System.IO.Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
                "PrivateCloudDisk");
        }

        await _virtualDisk.MountCloudFilesAsync(SyncRootPath);
    }

    private async Task UnmountAsync()
    {
        await _virtualDisk.UnmountAsync();
    }

    private async Task SyncNowAsync()
    {
        await _virtualDisk.SyncNowAsync();
    }

    private void SaveSettings()
    {
        _settings.Set("VirtualDisk.SyncRootPath", SyncRootPath ?? "");
        _settings.Set("VirtualDisk.DisplayName", DisplayName);
        _settings.Set("VirtualDisk.RealTimeSync", RealTimeSync);
        _settings.Set("VirtualDisk.OnDemandDownload", OnDemandDownload);
        _settings.Set("VirtualDisk.SyncInterval", SyncInterval);
        _settings.Set("VirtualDisk.MaxCacheSize", MaxCacheSize);
        _settings.Set("VirtualDisk.ConflictResolution", SelectedConflictResolution);
        _settings.Set("VirtualDisk.ExcludedExtensions", ExcludedExtensions);
        _settings.Save();
    }

    private void LoadSettings()
    {
        SyncRootPath = _settings.Get("VirtualDisk.SyncRootPath",
            System.IO.Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.UserProfile),
                "PrivateCloudDisk"));
        DisplayName = _settings.Get("VirtualDisk.DisplayName", "PrivateCloudDisk");
        RealTimeSync = _settings.Get("VirtualDisk.RealTimeSync", true);
        OnDemandDownload = _settings.Get("VirtualDisk.OnDemandDownload", true);
        SyncInterval = _settings.Get("VirtualDisk.SyncInterval", 300);
        MaxCacheSize = _settings.Get("VirtualDisk.MaxCacheSize", 10240L);
        SelectedConflictResolution = _settings.Get("VirtualDisk.ConflictResolution", 0);
        ExcludedExtensions = _settings.Get("VirtualDisk.ExcludedExtensions", "");
        IsMounted = _settings.Get("VirtualDisk.IsMounted", false);
    }

    private async Task BrowseSyncPathAsync()
    {
        // 使用 WinUI 文件夹选择器
        var picker = new Windows.Storage.Pickers.FolderPicker
        {
            SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.Desktop
        };
        picker.FileTypeFilter.Add("*");

        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(
            App.Current.Windows.First());
        WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);

        var folder = await picker.PickSingleFolderAsync();
        if (folder != null)
        {
            SyncRootPath = folder.Path;
        }
    }

    #endregion

    private void OnStatusChanged(object? sender, VirtualDiskStatus status)
    {
        DispatcherQueue.TryEnqueue(() =>
        {
            Status = status.Status;
            IsMounted = status.IsMounted;
            SyncRootPath = status.SyncRootPath;
            CurrentProgress = status.CurrentProgress;
            TotalSpace = status.TotalSpace;
            UsedSpace = status.UsedSpace;
            FreeSpace = status.FreeSpace;
            FileCount = status.FileCount;
            FolderCount = status.FolderCount;

            OnPropertyChanged(nameof(StatusDisplay));
            OnPropertyChanged(nameof(SpaceDisplay));
            OnPropertyChanged(nameof(SpacePercent));
        });
    }

    private void OnSyncEvent(object? sender, SyncEvent evt)
    {
        DispatcherQueue.TryEnqueue(() =>
        {
            SyncLog.Insert(0, evt);
            if (SyncLog.Count > 100)
                SyncLog.RemoveAt(SyncLog.Count - 1);
        });
    }
}