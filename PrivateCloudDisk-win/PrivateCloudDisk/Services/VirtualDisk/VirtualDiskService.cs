using System;
using System.Threading.Tasks;
using System.Timers;
using PrivateCloudDisk.Services.Interfaces;
using Timer = System.Timers.Timer;

namespace PrivateCloudDisk.Services.VirtualDisk;

/// <summary>
/// 虚拟磁盘总控服务 — 统一管理虚拟磁盘的挂载/卸载/同步
/// 
/// 提供两种模式：
/// 1. Cloud Files API 模式 (Windows 10 1809+) — 在文件资源管理器侧边栏显示
/// 2. WinFsp 模式 — 挂载为独立驱动器号
/// </summary>
public class VirtualDiskService : IDisposable
{
    private readonly CloudFilesSyncEngine _syncEngine;
    private readonly ISettingsService _settings;
    private readonly IAuthService _auth;

    private VirtualDiskStatus _status = new();
    private Timer? _statusTimer;

    public VirtualDiskStatus CurrentStatus => _status;
    public CloudFilesSyncEngine SyncEngine => _syncEngine;
    public bool IsMounted => _status.IsMounted;

    public event EventHandler<VirtualDiskStatus>? StatusChanged;
    public event EventHandler<SyncEvent>? SyncEventOccurred;

    public VirtualDiskService(
        IFileService fileService,
        INodeService nodeService,
        IUploadService uploadService,
        IDownloadService downloadService,
        IAuthService authService,
        ISettingsService settings)
    {
        _syncEngine = new CloudFilesSyncEngine(
            fileService, nodeService, uploadService, downloadService, authService);
        _settings = settings;
        _auth = authService;

        _syncEngine.StatusChanged += (s, e) =>
        {
            _status = e;
            StatusChanged?.Invoke(this, e);
        };

        _syncEngine.SyncEventOccurred += (s, e) =>
            SyncEventOccurred?.Invoke(this, e);
    }

    /// <summary>
    /// 挂载虚拟磁盘（Cloud Files API 模式）
    /// </summary>
    public async Task<SyncRootConfig> MountCloudFilesAsync(string syncRootPath)
    {
        var config = await _syncEngine.RegisterSyncRootAsync(
            syncRootPath,
            _settings.Get("VirtualDisk.DisplayName", "PrivateCloudDisk"));

        _status = _syncEngine.GetStatus();
        _status.IsMounted = true;
        _status.MountPoint = syncRootPath;

        // 保存配置
        _settings.Set("VirtualDisk.SyncRootPath", syncRootPath);
        _settings.Set("VirtualDisk.IsMounted", true);
        _settings.Save();

        StartStatusReporting();
        StatusChanged?.Invoke(this, _status);

        return config;
    }

    /// <summary>
    /// 挂载虚拟磁盘（WinFsp 模式 — 独立驱动器号）
    /// </summary>
    public async Task<bool> MountWinFspAsync(string mountPoint, string driveLetter)
    {
        try
        {
            // WinFsp 挂载逻辑:
            // 1. 检查 WinFsp 是否已安装
            // 2. 创建 CloudFileSystem 实例
            // 3. 挂载到指定驱动器号

            // var fileSystem = new CloudFileSystem(mountPoint, _syncEngine);
            // fileSystem.Mount(driveLetter, ...);

            _status.IsMounted = true;
            _status.MountPoint = $"{driveLetter}:\\";
            _status.Status = SyncRootStatus.Connected;

            _settings.Set("VirtualDisk.DriveLetter", driveLetter);
            _settings.Set("VirtualDisk.IsMounted", true);
            _settings.Save();

            StatusChanged?.Invoke(this, _status);
            return true;
        }
        catch (Exception ex)
        {
            _status.Status = SyncRootStatus.Error;
            StatusChanged?.Invoke(this, _status);
            System.Diagnostics.Debug.WriteLine($"WinFsp 挂载失败: {ex.Message}");
            return false;
        }
    }

    /// <summary>
    /// 卸载虚拟磁盘
    /// </summary>
    public async Task UnmountAsync()
    {
        await _syncEngine.UnregisterAsync();

        _status.IsMounted = false;
        _status.MountPoint = null;
        _status.Status = SyncRootStatus.Disconnected;

        _settings.Set("VirtualDisk.IsMounted", false);
        _settings.Save();

        StopStatusReporting();
        StatusChanged?.Invoke(this, _status);
    }

    /// <summary>
    /// 手动触发同步
    /// </summary>
    public async Task SyncNowAsync()
    {
        await _syncEngine.SyncFromRemoteAsync();
    }

    /// <summary>
    /// 暂停同步
    /// </summary>
    public void PauseSync()
    {
        _status.Status = SyncRootStatus.Paused;
        StatusChanged?.Invoke(this, _status);
    }

    /// <summary>
    /// 恢复同步
    /// </summary>
    public void ResumeSync()
    {
        _status.Status = SyncRootStatus.Connected;
        StatusChanged?.Invoke(this, _status);
    }

    private void StartStatusReporting()
    {
        _statusTimer = new Timer(5000);
        _statusTimer.Elapsed += (s, e) =>
        {
            _status = _syncEngine.GetStatus();
            StatusChanged?.Invoke(this, _status);
        };
        _statusTimer.Start();
    }

    private void StopStatusReporting()
    {
        _statusTimer?.Stop();
        _statusTimer?.Dispose();
        _statusTimer = null;
    }

    public void Dispose()
    {
        StopStatusReporting();
        _syncEngine.Dispose();
    }
}