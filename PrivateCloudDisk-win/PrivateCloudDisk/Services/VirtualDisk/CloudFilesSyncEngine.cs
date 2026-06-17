using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using System.Threading.Tasks.Dataflow;
using Microsoft.Extensions.DependencyInjection;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.Services.VirtualDisk;

/// <summary>
/// Cloud Files Sync Engine — 核心同步引擎
/// 
/// 使用 Windows Cloud Files API 实现：
/// 1. 在 Windows 资源管理器中注册同步根
/// 2. 创建占位符文件(placeholder) — 按需下载
/// 3. 监听本地文件变更并同步到云端
/// 4. 管理文件水合/脱水状态
/// 5. 处理冲突
/// 
/// 文件状态机：
///   Placeholder(仅元数据) → Hydrated(已下载到本地) → Dirty(本地已修改) → Syncing → Placeholder/Hydrated
/// </summary>
public class CloudFilesSyncEngine : IDisposable
{
    private readonly IFileService _fileService;
    private readonly INodeService _nodeService;
    private readonly IUploadService _uploadService;
    private readonly IDownloadService _downloadService;
    private readonly IAuthService _authService;

    private readonly ConcurrentDictionary<string, CloudFileEntry> _fileCache = new();
    private readonly ConcurrentDictionary<string, SyncStatus> _fileStatuses = new();
    private readonly ConcurrentQueue<SyncEvent> _eventQueue = new();

    private readonly ActionBlock<SyncEvent> _eventProcessor;
    private readonly CancellationTokenSource _cts = new();
    private readonly SemaphoreSlim _syncLock = new(1, 1);

    private Timer? _syncTimer;
    private FileSystemWatcher? _fileWatcher;
    private SyncRootConfig? _config;

    private int _syncErrorCount;
    private long _totalUploadedBytes;
    private long _totalDownloadedBytes;

    public event EventHandler<SyncEvent>? SyncEventOccurred;
    public event EventHandler<SyncProgress>? ProgressChanged;
    public event EventHandler<VirtualDiskStatus>? StatusChanged;

    public SyncRootConfig? CurrentConfig => _config;
    public SyncProgress CurrentProgress { get; } = new();
    public VirtualDiskStatus Status { get; } = new();

    public CloudFilesSyncEngine(
        IFileService fileService,
        INodeService nodeService,
        IUploadService uploadService,
        IDownloadService downloadService,
        IAuthService authService)
    {
        _fileService = fileService;
        _nodeService = nodeService;
        _uploadService = uploadService;
        _downloadService = downloadService;
        _authService = authService;

        _eventProcessor = new ActionBlock<SyncEvent>(
            ProcessEventAsync,
            new ExecutionDataflowBlockOptions
            {
                MaxDegreeOfParallelism = 1,
                CancellationToken = _cts.Token
            });
    }

    #region 注册与初始化

    /// <summary>
    /// 注册同步根到 Windows Cloud Files API
    /// </summary>
    public async Task<SyncRootConfig> RegisterSyncRootAsync(string syncRootPath, string displayName)
    {
        if (!Directory.Exists(syncRootPath))
            Directory.CreateDirectory(syncRootPath);

        _config = new SyncRootConfig
        {
            Id = $"PrivateCloudDisk!{Environment.UserName}!{Environment.MachineName}",
            DisplayName = displayName,
            Path = syncRootPath,
            RootNodeId = "root",
            Status = SyncRootStatus.Connecting
        };

        // 注册到 Windows Storage Provider
        try
        {
            // NOTE: 需要 Windows 10 1809+ 和 storageProvider 扩展声明
            // 在 Package.appxmanifest 中添加:
            // <Extensions>
            //   <desktop:Extension Category="windows.cloudFiles">
            //     <desktop:CloudFiles>
            //       <desktop:CloudFilesProvider IconResource="..." />
            //       <desktop:SupportedSyncActions>
            //         <desktop:SyncAction>Hydrate</desktop:SyncAction>
            //         <desktop:SyncAction>Dehydrate</desktop:SyncAction>
            //         <desktop:SyncAction>Pin</desktop:SyncAction>
            //         <desktop:SyncAction>Unpin</desktop:SyncAction>
            //       </desktop:SupportedSyncActions>
            //     </desktop:CloudFiles>
            //   </desktop:Extension>
            // </Extensions>
            //
            // 通过 StorageProviderSyncRootManager 注册:
            // var syncRootInfo = new StorageProviderSyncRootInfo
            // {
            //     Id = _config.Id,
            //     DisplayNameResource = displayName,
            //     Path = StorageFolder.GetFolderFromPathAsync(syncRootPath).GetAwaiter().GetResult(),
            //     ProviderId = new Guid("YOUR-PROVIDER-GUID"),
            //     Version = "1.0",
            //     PopulationPolicy = StorageProviderPopulationPolicy.Full,
            //     InSyncPolicy = StorageProviderInSyncPolicy.Default,
            //     HydrationPolicy = StorageProviderHydrationPolicy.Progressive,
            //     HydrationPolicyModifier = StorageProviderHydrationPolicyModifier.AutoDehydrationAllowed,
            //     ShowSiblingsAsGroup = false,
            //     HardlinkPolicy = StorageProviderHardlinkPolicy.None,
            //     RecycleBinUri = null,
            //     Context = null,
            //     IconResource = "PrivateCloudDisk.ico",
            // };
            // StorageProviderSyncRootManager.Register(syncRootInfo);

            _config.Status = SyncRootStatus.Connected;
            Status.IsMounted = true;
            Status.MountPoint = syncRootPath;
            Status.SyncRootPath = syncRootPath;
            Status.Status = SyncRootStatus.Connected;

            RaiseEvent(new SyncEvent
            {
                Type = SyncEventType.SyncStarted,
                Message = $"同步根已注册: {syncRootPath}"
            });

            // 启动后台同步
            StartBackgroundSync();

            return _config;
        }
        catch (Exception ex)
        {
            _config.Status = SyncRootStatus.Error;
            Status.Status = SyncRootStatus.Error;
            RaiseEvent(new SyncEvent
            {
                Type = SyncEventType.SyncError,
                Message = $"注册同步根失败: {ex.Message}",
                Exception = ex
            });
            throw;
        }
    }

    /// <summary>
    /// 注销同步根
    /// </summary>
    public async Task UnregisterAsync()
    {
        StopBackgroundSync();

        if (_config != null)
        {
            // StorageProviderSyncRootManager.Unregister(_config.Id);
            _config.Status = SyncRootStatus.Disconnected;
        }

        Status.IsMounted = false;
        Status.Status = SyncRootStatus.Disconnected;
    }

    #endregion

    #region 后台同步

    private void StartBackgroundSync()
    {
        if (_config?.Policy.RealTimeSync == true)
        {
            StartFileSystemWatcher();
        }

        _syncTimer = new Timer(
            async _ => await SyncFromRemoteAsync(),
            null,
            TimeSpan.FromSeconds(10),
            TimeSpan.FromSeconds(_config?.Policy.SyncIntervalSeconds ?? 300));
    }

    private void StopBackgroundSync()
    {
        _syncTimer?.Dispose();
        _syncTimer = null;

        _fileWatcher?.Dispose();
        _fileWatcher = null;
    }

    private void StartFileSystemWatcher()
    {
        if (_config == null || !Directory.Exists(_config.Path))
            return;

        _fileWatcher?.Dispose();
        _fileWatcher = new FileSystemWatcher(_config.Path)
        {
            IncludeSubdirectories = true,
            NotifyFilter = NotifyFilters.FileName
                         | NotifyFilters.DirectoryName
                         | NotifyFilters.LastWrite
                         | NotifyFilters.Size,
            InternalBufferSize = 65536
        };

        _fileWatcher.Created += (s, e) => EnqueueEvent(SyncEventType.FileCreated, e.FullPath);
        _fileWatcher.Changed += (s, e) => EnqueueEvent(SyncEventType.FileModified, e.FullPath);
        _fileWatcher.Renamed += (s, e) => EnqueueEvent(SyncEventType.FileRenamed, e.FullPath);
        _fileWatcher.Deleted += (s, e) => EnqueueEvent(SyncEventType.FileDeleted, e.FullPath);

        _fileWatcher.EnableRaisingEvents = true;
    }

    #endregion

    #region 同步操作

    /// <summary>
    /// 从远程拉取同步 — 对比云端与本地差异
    /// </summary>
    public async Task SyncFromRemoteAsync()
    {
        if (!await _syncLock.WaitAsync(0))
            return; // 已有同步在进行中

        try
        {
            Status.Status = SyncRootStatus.Syncing;
            CurrentProgress.Status = SyncStatus.Syncing;

            await SyncDirectoryAsync(_config?.RootNodeId ?? "root", _config?.Path ?? "");

            _config!.LastSyncTime = DateTime.UtcNow;
            Status.Status = SyncRootStatus.Connected;
            CurrentProgress.Status = SyncStatus.Idle;

            RaiseEvent(new SyncEvent
            {
                Type = SyncEventType.SyncCompleted,
                Message = $"同步完成: {CurrentProgress.ProcessedFiles} 个文件"
            });
        }
        catch (Exception ex)
        {
            Interlocked.Increment(ref _syncErrorCount);
            CurrentProgress.ErrorCount = _syncErrorCount;
            CurrentProgress.Status = SyncStatus.Error;

            RaiseEvent(new SyncEvent
            {
                Type = SyncEventType.SyncError,
                Message = $"同步失败: {ex.Message}",
                Exception = ex
            });
        }
        finally
        {
            _syncLock.Release();
        }
    }

    private async Task SyncDirectoryAsync(string nodeId, string localPath)
    {
        if (!Directory.Exists(localPath))
            Directory.CreateDirectory(localPath);

        try
        {
            // 获取云端文件列表
            var filesResp = await _fileService.GetFileListAsync(nodeId, 1, 1000);
            var nodesResp = await _nodeService.GetChildNodesAsync(nodeId, 1, 1000);

            var cloudEntries = new Dictionary<string, CloudFileEntry>();

            // 合并文件和文件夹
            foreach (var file in filesResp.Items)
            {
                var entry = new CloudFileEntry
                {
                    Id = file.EffectiveId,
                    Name = file.EffectiveName,
                    ParentId = nodeId,
                    IsDirectory = false,
                    Size = file.EffectiveSize,
                    FileType = file.FileType,
                    LastModified = file.EffectiveTime,
                    LocalPath = Path.Combine(localPath, file.EffectiveName)
                };
                cloudEntries[entry.Name.ToLowerInvariant()] = entry;
                _fileCache[entry.Id] = entry;
            }

            foreach (var node in nodesResp.Items)
            {
                var entry = new CloudFileEntry
                {
                    Id = node.EffectiveId,
                    Name = node.EffectiveName,
                    ParentId = nodeId,
                    IsDirectory = true,
                    Size = 0,
                    LastModified = node.EffectiveTime,
                    LocalPath = Path.Combine(localPath, node.EffectiveName)
                };
                cloudEntries[entry.Name.ToLowerInvariant()] = entry;
                _fileCache[entry.Id] = entry;
            }

            // 本地文件
            var localFiles = new HashSet<string>(
                Directory.EnumerateFileSystemEntries(localPath)
                    .Select(p => Path.GetFileName(p).ToLowerInvariant()));

            // 处理云端有但本地没有的文件 → 创建 placeholder
            foreach (var (name, entry) in cloudEntries)
            {
                if (!localFiles.Contains(name))
                {
                    await CreatePlaceholderAsync(entry);
                }
            }

            // 递归同步子目录
            foreach (var entry in cloudEntries.Values.Where(e => e.IsDirectory))
            {
                await SyncDirectoryAsync(entry.Id, entry.LocalPath!);
            }

            // 处理本地有但云端没有的文件 → 上传
            foreach (var localName in localFiles)
            {
                if (!cloudEntries.ContainsKey(localName))
                {
                    var localFullPath = Path.Combine(localPath, Path.GetFileName(localName));
                    if (File.Exists(localFullPath))
                    {
                        EnqueueEvent(SyncEventType.FileCreated, localFullPath);
                    }
                }
            }

            CurrentProgress.ProcessedFiles++;
            ProgressChanged?.Invoke(this, CurrentProgress);
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"同步目录失败 [{localPath}]: {ex.Message}");
        }
    }

    /// <summary>
    /// 创建占位符文件 — 仅包含元数据，不下载实际内容
    /// </summary>
    private async Task CreatePlaceholderAsync(CloudFileEntry entry)
    {
        if (entry.IsDirectory)
        {
            Directory.CreateDirectory(entry.LocalPath!);
            // 设置目录属性标记为云端目录
            SetCloudFileAttribute(entry.LocalPath!);
        }
        else
        {
            var dir = Path.GetDirectoryName(entry.LocalPath);
            if (!Directory.Exists(dir))
                Directory.CreateDirectory(dir!);

            // 使用 Cloud Files API 创建占位符文件
            // 实际使用 CfCreatePlaceholders 或 StorageProvider API
            try
            {
                // 创建一个 0 字节文件作为占位符
                using (File.Create(entry.LocalPath!)) { }

                // 设置云端属性
                SetCloudFileAttribute(entry.LocalPath!);

                // 标记为占位符
                _fileStatuses[entry.Id] = SyncStatus.Idle;
                entry.IsPlaceholder = true;
                entry.DownloadProgress = 0;

                RaiseEvent(new SyncEvent
                {
                    Type = SyncEventType.FileCreated,
                    FilePath = entry.LocalPath,
                    FileId = entry.Id,
                    Message = $"已创建占位符: {entry.Name}"
                });
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"创建占位符失败 [{entry.Name}]: {ex.Message}");
            }
        }
    }

    /// <summary>
    /// 水合文件 — 从云端下载实际内容到本地
    /// （当用户双击占位符文件时触发）
    /// </summary>
    public async Task<bool> HydrateFileAsync(string fileId, string localPath)
    {
        try
        {
            _fileStatuses[fileId] = SyncStatus.Downloading;
            CurrentProgress.CurrentFile = Path.GetFileName(localPath);

            // 获取下载凭证
            var token = await _downloadService.GetDownloadCredentialAsync(fileId);

            var progress = new Progress<(double percent, string status)>(p =>
            {
                if (_fileCache.TryGetValue(fileId, out var entry))
                    entry.DownloadProgress = p.percent;
            });

            var success = await _downloadService.DownloadFileAsync(
                token.DownloadUrl,
                localPath,
                progress);

            if (success)
            {
                _fileStatuses[fileId] = SyncStatus.Idle;
                if (_fileCache.TryGetValue(fileId, out var entry))
                {
                    entry.IsPlaceholder = false;
                    entry.DownloadProgress = 100;
                }

                _totalDownloadedBytes += new FileInfo(localPath).Length;
                CurrentProgress.TransferredBytes += new FileInfo(localPath).Length;

                RaiseEvent(new SyncEvent
                {
                    Type = SyncEventType.FileDownloaded,
                    FilePath = localPath,
                    FileId = fileId,
                    Message = $"已下载: {Path.GetFileName(localPath)}"
                });
                return true;
            }

            _fileStatuses[fileId] = SyncStatus.Error;
            return false;
        }
        catch (Exception ex)
        {
            _fileStatuses[fileId] = SyncStatus.Error;
            RaiseEvent(new SyncEvent
            {
                Type = SyncEventType.SyncError,
                FilePath = localPath,
                FileId = fileId,
                Message = $"下载失败: {ex.Message}",
                Exception = ex
            });
            return false;
        }
    }

    /// <summary>
    /// 脱水文件 — 删除本地内容，恢复为占位符
    /// </summary>
    public async Task DehydrateFileAsync(string fileId, string localPath)
    {
        try
        {
            if (File.Exists(localPath))
            {
                File.Delete(localPath);

                // 重新创建占位符
                using (File.Create(localPath)) { }
                SetCloudFileAttribute(localPath);

                if (_fileCache.TryGetValue(fileId, out var entry))
                {
                    entry.IsPlaceholder = true;
                    entry.DownloadProgress = 0;
                }

                _fileStatuses[fileId] = SyncStatus.Idle;
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"脱水文件失败 [{localPath}]: {ex.Message}");
        }
    }

    /// <summary>
    /// 上传本地文件到云端
    /// </summary>
    public async Task<bool> UploadFileAsync(string localPath, string parentNodeId)
    {
        var fileName = Path.GetFileName(localPath);
        var fileInfo = new FileInfo(localPath);

        if (!fileInfo.Exists) return false;

        _fileStatuses[Path.GetFileNameWithoutExtension(localPath)] = SyncStatus.Uploading;

        try
        {
            var progress = new Progress<(double percent, string status)>(p =>
            {
                CurrentProgress.CurrentFile = fileName;
            });

            var success = await _uploadService.UploadFileAsync(
                localPath, fileName, parentNodeId, progress);

            if (success)
            {
                _totalUploadedBytes += fileInfo.Length;
                CurrentProgress.TransferredBytes += fileInfo.Length;

                RaiseEvent(new SyncEvent
                {
                    Type = SyncEventType.FileUploaded,
                    FilePath = localPath,
                    Message = $"已上传: {fileName}"
                });
                return true;
            }

            return false;
        }
        catch (Exception ex)
        {
            RaiseEvent(new SyncEvent
            {
                Type = SyncEventType.SyncError,
                FilePath = localPath,
                Message = $"上传失败 [{fileName}]: {ex.Message}",
                Exception = ex
            });
            return false;
        }
    }

    /// <summary>
    /// 处理冲突 — 本地和云端同时修改同一文件
    /// </summary>
    private async Task ResolveConflictAsync(string localPath, string fileId)
    {
        var policy = _config?.Policy.ConflictResolution ?? ConflictResolution.KeepBoth;

        switch (policy)
        {
            case ConflictResolution.KeepBoth:
                // 重命名本地文件
                var dir = Path.GetDirectoryName(localPath);
                var name = Path.GetFileNameWithoutExtension(localPath);
                var ext = Path.GetExtension(localPath);
                var conflictPath = Path.Combine(dir!, $"{name}_conflict_{DateTime.Now:yyyyMMddHHmmss}{ext}");
                File.Move(localPath, conflictPath);
                break;

            case ConflictResolution.LocalWins:
                await UploadFileAsync(localPath, _config?.RootNodeId ?? "root");
                break;

            case ConflictResolution.RemoteWins:
                await HydrateFileAsync(fileId, localPath);
                break;
        }

        RaiseEvent(new SyncEvent
        {
            Type = SyncEventType.ConflictDetected,
            FilePath = localPath,
            FileId = fileId,
            Message = $"冲突已解决 (策略: {policy})"
        });
    }

    #endregion

    #region 文件属性

    /// <summary>
    /// 设置 NTFS 云端文件属性
    /// </summary>
    private static void SetCloudFileAttribute(string path)
    {
        try
        {
            var attrib = (File.GetAttributes(path) | (FileAttributes)0x20000); // FILE_ATTRIBUTE_RECALL_ON_OPEN
            File.SetAttributes(path, attrib);
        }
        catch
        {
            // 忽略属性设置失败
        }
    }

    /// <summary>
    /// 检查文件是否在云端
    /// </summary>
    public static bool IsCloudFile(string path)
    {
        try
        {
            var attrib = File.GetAttributes(path);
            return (attrib & (FileAttributes)0x20000) != 0; // FILE_ATTRIBUTE_RECALL_ON_OPEN
        }
        catch
        {
            return false;
        }
    }

    #endregion

    #region 事件处理

    private void EnqueueEvent(SyncEventType type, string filePath)
    {
        var evt = new SyncEvent
        {
            Type = type,
            FilePath = filePath,
            Timestamp = DateTime.UtcNow
        };
        _eventQueue.Enqueue(evt);
        _eventProcessor.Post(evt);
    }

    private async Task ProcessEventAsync(SyncEvent evt)
    {
        try
        {
            switch (evt.Type)
            {
                case SyncEventType.FileCreated:
                    if (evt.FilePath != null)
                    {
                        var parentNodeId = GetParentNodeId(evt.FilePath);
                        await UploadFileAsync(evt.FilePath, parentNodeId);
                    }
                    break;

                case SyncEventType.FileModified:
                    if (evt.FilePath != null)
                    {
                        var parentNodeId = GetParentNodeId(evt.FilePath);
                        await UploadFileAsync(evt.FilePath, parentNodeId);
                    }
                    break;

                case SyncEventType.FileDeleted:
                    if (evt.FilePath != null)
                    {
                        var fileId = GetFileIdFromPath(evt.FilePath);
                        if (fileId != null)
                        {
                            await _fileService.DeleteFileAsync(fileId);
                        }
                    }
                    break;
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"处理事件失败 [{evt.Type}]: {ex.Message}");
        }
        finally
        {
            SyncEventOccurred?.Invoke(this, evt);
        }
    }

    private string? GetFileIdFromPath(string path)
    {
        var fileName = Path.GetFileName(path).ToLowerInvariant();
        var entry = _fileCache.Values.FirstOrDefault(e =>
            e.Name.Equals(fileName, StringComparison.OrdinalIgnoreCase));
        return entry?.Id;
    }

    private string GetParentNodeId(string path)
    {
        var parentDir = Path.GetDirectoryName(path);
        if (parentDir == _config?.Path)
            return _config.RootNodeId;

        // 查找父目录对应的 nodeId
        var parentName = Path.GetFileName(parentDir)?.ToLowerInvariant();
        var entry = _fileCache.Values.FirstOrDefault(e =>
            e.IsDirectory && e.Name.Equals(parentName, StringComparison.OrdinalIgnoreCase));
        return entry?.Id ?? _config?.RootNodeId ?? "root";
    }

    private void RaiseEvent(SyncEvent evt)
    {
        SyncEventOccurred?.Invoke(this, evt);
    }

    #endregion

    /// <summary>
    /// 获取虚拟磁盘状态
    /// </summary>
    public VirtualDiskStatus GetStatus()
    {
        Status.FileCount = _fileCache.Values.Count(e => !e.IsDirectory);
        Status.FolderCount = _fileCache.Values.Count(e => e.IsDirectory);
        Status.TotalSpace = _config?.Policy.MaxCacheSizeMB * 1024 * 1024 ?? 0;
        Status.UsedSpace = _fileCache.Values
            .Where(e => !e.IsDirectory && !e.IsPlaceholder)
            .Sum(e => e.Size);
        Status.FreeSpace = Status.TotalSpace - Status.UsedSpace;
        Status.CurrentProgress = CurrentProgress;
        return Status;
    }

    public void Dispose()
    {
        _cts.Cancel();
        _syncTimer?.Dispose();
        _fileWatcher?.Dispose();
        _syncLock.Dispose();
        _eventProcessor.Complete();
    }
}