using System.Diagnostics;
using System.IO.Compression;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text.Json;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;
using Windows.Storage;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// 企业级更新服务 — 统一管理版本检查、热更新、大版本更新
///
/// 更新策略:
/// 1. 热修复(patch) → 应用内静默更新，下载补丁替换 DLL/资源文件，无需重启
/// 2. 小版本(minor) → 后台下载，下次启动时安装
/// 3. 大版本(major) → 弹出通知，引导用户下载安装包
/// 4. 强制更新 → 阻止使用，必须更新
/// </summary>
public class UpdateService : IUpdateService, IDisposable
{
    private readonly IHttpClientFactory _httpFactory;
    private readonly ISettingsService _settings;
    private readonly IAuthService _auth;
    private readonly SemaphoreSlim _lock = new(1, 1);

    private UpdateStatus _status = UpdateStatus.NotChecked;
    private UpdateInfo? _latestUpdateInfo;
    private UpdateDownloadProgress? _downloadProgress;
    private UpdateSettings _updateSettings;
    private CancellationTokenSource? _downloadCts;
    private Timer? _autoCheckTimer;
    private bool _disposed;

    private const string CheckEndpoint = "/api/v1/version/check";
    private const string LatestEndpoint = "/api/v1/version/latest";
    private const string HotfixEndpoint = "/api/v1/version/hotfix";
    private const string DownloadEndpoint = "/api/v1/version/download";

    // ── 事件 ──
    public event EventHandler<UpdateStatus>? StatusChanged;
    public event EventHandler<UpdateInfo>? UpdateAvailable;
    public event EventHandler<UpdateDownloadProgress>? DownloadProgressChanged;
    public event EventHandler<HotPatchInfo>? HotPatchApplied;

    public UpdateStatus CurrentStatus => _status;
    public UpdateInfo? LatestUpdateInfo => _latestUpdateInfo;
    public UpdateDownloadProgress? DownloadProgress => _downloadProgress;
    public UpdateSettings Settings => _updateSettings;

    public UpdateService(IHttpClientFactory httpFactory, ISettingsService settings, IAuthService auth)
    {
        _httpFactory = httpFactory;
        _settings = settings;
        _auth = auth;

        _updateSettings = LoadSettings();
    }

    // ==================== 版本检查 ====================

    /// <summary>
    /// 检查更新
    /// </summary>
    public async Task<UpdateInfo?> CheckForUpdatesAsync(CancellationToken ct = default)
    {
        await _lock.WaitAsync(ct);
        try
        {
            SetStatus(UpdateStatus.Checking);

            var client = _httpFactory.CreateClient("PlatformService");

            var request = new VersionCheckRequest
            {
                CurrentVersion = AppConfig.AppVersion,
                Platform = GetPlatformName(),
                Arch = GetArchitecture(),
                Channel = _updateSettings.Channel
            };

            var response = await client.PostAsJsonAsync(CheckEndpoint, request, ct);
            var apiResp = await ParseResponseAsync<UpdateInfo>(response);

            if (apiResp.IsSuccess && apiResp.Data != null)
            {
                _latestUpdateInfo = apiResp.Data;
                _latestUpdateInfo.CurrentVersion = AppConfig.AppVersion;

                _updateSettings.LastCheckTime = DateTime.UtcNow;
                SaveSettings();

                if (_latestUpdateInfo.HasUpdate)
                {
                    // 判断是否跳过此版本
                    if (_updateSettings.SkippedVersion == _latestUpdateInfo.LatestVersion
                        && !_latestUpdateInfo.ForceUpdate)
                    {
                        SetStatus(UpdateStatus.Skipped);
                        return _latestUpdateInfo;
                    }

                    SetStatus(UpdateStatus.UpdateAvailable);
                    UpdateAvailable?.Invoke(this, _latestUpdateInfo);

                    // 自动下载(如果开启)
                    if (_updateSettings.AutoDownload && !_latestUpdateInfo.IsMajorUpdate)
                    {
                        _ = DownloadUpdateAsync(ct: ct);
                    }

                    return _latestUpdateInfo;
                }

                SetStatus(UpdateStatus.UpToDate);
                return _latestUpdateInfo;
            }

            SetStatus(UpdateStatus.Failed);
            return null;
        }
        catch (OperationCanceledException)
        {
            SetStatus(UpdateStatus.NotChecked);
            return null;
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[UpdateService] 检查更新失败: {ex.Message}");
            SetStatus(UpdateStatus.Failed);
            return null;
        }
        finally
        {
            _lock.Release();
        }
    }

    // ==================== 自动检查 ====================

    public void StartAutoCheck()
    {
        StopAutoCheck();

        if (!_updateSettings.AutoCheckEnabled) return;

        var interval = TimeSpan.FromHours(_updateSettings.CheckIntervalHours);

        _autoCheckTimer = new Timer(async _ =>
        {
            try
            {
                await CheckForUpdatesAsync();
            }
            catch { /* 静默失败 */ }
        }, null, TimeSpan.FromSeconds(30), interval); // 启动后30秒首次检查

        Debug.WriteLine($"[UpdateService] 自动检查已启动, 间隔: {interval.TotalHours}h");
    }

    public void StopAutoCheck()
    {
        _autoCheckTimer?.Dispose();
        _autoCheckTimer = null;
    }

    // ==================== 下载更新 ====================

    public async Task DownloadUpdateAsync(IProgress<UpdateDownloadProgress>? progress = null,
        CancellationToken ct = default)
    {
        if (_latestUpdateInfo == null) return;

        _downloadCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        var token = _downloadCts.Token;

        try
        {
            SetStatus(UpdateStatus.Downloading);

            var downloadPath = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "PrivateCloudDisk", "Updates");

            Directory.CreateDirectory(downloadPath);

            var fileName = _latestUpdateInfo.IsHotfix
                ? $"hotfix_{_latestUpdateInfo.LatestVersion}.pcdpatch"
                : $"setup_{_latestUpdateInfo.LatestVersion}.pcdpkg";

            var savePath = Path.Combine(downloadPath, fileName);

            _downloadProgress = new UpdateDownloadProgress
            {
                UpdateId = _latestUpdateInfo.LatestVersion,
                TotalBytes = _latestUpdateInfo.PackageSize,
                Status = "downloading"
            };

            var client = _httpFactory.CreateClient("FileService");
            client.Timeout = TimeSpan.FromHours(2); // 大文件下载超时

            using var response = await client.GetAsync(
                _latestUpdateInfo.DownloadUrl,
                HttpCompletionOption.ResponseHeadersRead, token);
            response.EnsureSuccessStatusCode();

            await using var stream = await response.Content.ReadAsStreamAsync(token);
            await using var fileStream = new FileStream(savePath, FileMode.Create,
                FileAccess.Write, FileShare.None, 8192, useAsync: true);

            var buffer = new byte[8192];
            long totalRead = 0;
            var stopwatch = Stopwatch.StartNew();
            var lastReportTime = DateTime.UtcNow;
            long lastReportBytes = 0;

            while (true)
            {
                token.ThrowIfCancellationRequested();
                var read = await stream.ReadAsync(buffer, 0, buffer.Length, token);
                if (read == 0) break;

                await fileStream.WriteAsync(buffer, 0, read, token);
                totalRead += read;

                var elapsed = DateTime.UtcNow - lastReportTime;
                if (elapsed.TotalMilliseconds >= 500)
                {
                    var bytesSinceLast = totalRead - lastReportBytes;
                    var speed = bytesSinceLast / elapsed.TotalSeconds;
                    var remaining = _latestUpdateInfo.PackageSize - totalRead;
                    var eta = speed > 0 ? TimeSpan.FromSeconds(remaining / speed) : TimeSpan.Zero;

                    _downloadProgress.DownloadedBytes = totalRead;
                    _downloadProgress.SpeedBytesPerSecond = speed;
                    _downloadProgress.EstimatedTimeRemaining = eta;

                    DownloadProgressChanged?.Invoke(this, _downloadProgress);
                    progress?.Report(_downloadProgress);

                    lastReportBytes = totalRead;
                    lastReportTime = DateTime.UtcNow;
                }
            }

            stopwatch.Stop();

            // 验证哈希
            _downloadProgress.Status = "verifying";
            DownloadProgressChanged?.Invoke(this, _downloadProgress);

            var actualHash = await ComputeSha256Async(savePath);
            if (!string.Equals(actualHash, _latestUpdateInfo.PackageHash, StringComparison.OrdinalIgnoreCase))
            {
                _downloadProgress.Status = "failed";
                _downloadProgress.ErrorMessage = "更新包校验失败，文件可能已损坏";
                DownloadProgressChanged?.Invoke(this, _downloadProgress);
                SetStatus(UpdateStatus.Failed);
                return;
            }

            _downloadProgress.Status = "completed";
            _downloadProgress.DownloadedBytes = _latestUpdateInfo.PackageSize;
            DownloadProgressChanged?.Invoke(this, _downloadProgress);

            SetStatus(UpdateStatus.Downloaded);

            // 保存下载路径
            _settings.Set("Update.DownloadedPackage", savePath);
            _settings.Set("Update.DownloadedVersion", _latestUpdateInfo.LatestVersion);
            _settings.Save();
        }
        catch (OperationCanceledException)
        {
            _downloadProgress!.Status = "cancelled";
            SetStatus(UpdateStatus.UpdateAvailable);
        }
        catch (Exception ex)
        {
            _downloadProgress!.Status = "failed";
            _downloadProgress.ErrorMessage = ex.Message;
            DownloadProgressChanged?.Invoke(this, _downloadProgress);
            SetStatus(UpdateStatus.Failed);
        }
    }

    public void CancelDownload()
    {
        _downloadCts?.Cancel();
        _downloadCts = null;
    }

    // ==================== 安装更新 ====================

    public async Task InstallUpdateAsync()
    {
        if (_latestUpdateInfo == null) return;

        SetStatus(UpdateStatus.Installing);

        if (_latestUpdateInfo.IsMajorUpdate || _latestUpdateInfo.ForceUpdate)
        {
            // 大版本更新 → 启动 PrivateCloudDisk.Downloader 安装器
            await LaunchInstallerAsync();
        }
        else if (_latestUpdateInfo.IsHotfix)
        {
            // 热修复 → 替换文件
            var savedPackage = _settings.Get<string>("Update.DownloadedPackage");
            if (!string.IsNullOrEmpty(savedPackage))
            {
                await ApplyHotPatchFromPackageAsync(savedPackage);
            }
        }
        else
        {
            // 小版本 → 下次启动安装
            SetStatus(UpdateStatus.PendingRestart);
        }
    }

    /// <summary>
    /// 启动独立安装器
    /// </summary>
    private async Task LaunchInstallerAsync()
    {
        var installerPath = Path.Combine(
            AppDomain.CurrentDomain.BaseDirectory, "PrivateCloudDisk.Downloader.exe");

        if (File.Exists(installerPath))
        {
            var savedPackage = _settings.Get<string>("Update.DownloadedPackage");
            var args = string.IsNullOrEmpty(savedPackage)
                ? "--check-update"
                : $"--install \"{savedPackage}\"";

            Process.Start(new ProcessStartInfo
            {
                FileName = installerPath,
                Arguments = args,
                UseShellExecute = true
            });

            // 退出当前应用
            await Task.Delay(500);
            Environment.Exit(0);
        }
    }

    /// <summary>
    /// 从下载的补丁包应用热修复
    /// </summary>
    private async Task ApplyHotPatchFromPackageAsync(string packagePath)
    {
        try
        {
            if (!File.Exists(packagePath)) return;

            var extractDir = Path.Combine(
                Path.GetDirectoryName(packagePath)!, "temp_patch");

            if (Directory.Exists(extractDir))
                Directory.Delete(extractDir, true);

            // 解压补丁包
            await Task.Run(() => ZipFile.ExtractToDirectory(packagePath, extractDir));

            var appDir = AppDomain.CurrentDomain.BaseDirectory;

            // 替换文件
            foreach (var file in Directory.GetFiles(extractDir, "*", SearchOption.AllDirectories))
            {
                var relativePath = Path.GetRelativePath(extractDir, file);
                var targetPath = Path.Combine(appDir, relativePath);
                var targetDir = Path.GetDirectoryName(targetPath);

                if (!string.IsNullOrEmpty(targetDir) && !Directory.Exists(targetDir))
                    Directory.CreateDirectory(targetDir);

                // 备份原文件
                if (File.Exists(targetPath))
                {
                    var backupPath = targetPath + ".backup";
                    File.Copy(targetPath, backupPath, true);
                }

                File.Copy(file, targetPath, true);
            }

            // 清理临时文件
            Directory.Delete(extractDir, true);
            File.Delete(packagePath);

            SetStatus(UpdateStatus.PendingRestart);
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[UpdateService] 热修复应用失败: {ex.Message}");
            SetStatus(UpdateStatus.Failed);
        }
    }

    // ==================== 热补丁 ====================

    public async Task<bool> ApplyHotPatchAsync(HotPatchInfo patch,
        IProgress<UpdateDownloadProgress>? progress = null,
        CancellationToken ct = default)
    {
        try
        {
            SetStatus(UpdateStatus.Downloading);

            var downloadPath = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "PrivateCloudDisk", "Hotfixes");

            Directory.CreateDirectory(downloadPath);

            var patchFile = Path.Combine(downloadPath, $"patch_{patch.PatchId}.pcdpatch");

            // 下载补丁
            var client = _httpFactory.CreateClient("FileService");
            using var response = await client.GetAsync(patch.DownloadUrl,
                HttpCompletionOption.ResponseHeadersRead, ct);
            response.EnsureSuccessStatusCode();

            await using var stream = await response.Content.ReadAsStreamAsync(ct);
            await using var fileStream = File.Create(patchFile);
            await stream.CopyToAsync(fileStream, ct);

            // 验证哈希
            var hash = await ComputeSha256Async(patchFile);
            if (!string.Equals(hash, patch.PatchHash, StringComparison.OrdinalIgnoreCase))
            {
                File.Delete(patchFile);
                return false;
            }

            // 应用补丁
            SetStatus(UpdateStatus.Installing);

            var result = await ApplyPatchToAppAsync(patchFile, patch);
            if (result)
            {
                HotPatchApplied?.Invoke(this, patch);

                if (patch.RequiresRestart)
                    SetStatus(UpdateStatus.PendingRestart);
                else
                    SetStatus(UpdateStatus.UpToDate);
            }
            else
            {
                SetStatus(UpdateStatus.Failed);
            }

            return result;
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[UpdateService] 热补丁失败: {ex.Message}");
            SetStatus(UpdateStatus.Failed);
            return false;
        }
    }

    /// <summary>
    /// 将补丁应用到应用程序目录
    /// </summary>
    private async Task<bool> ApplyPatchToAppAsync(string patchPath, HotPatchInfo patch)
    {
        try
        {
            var appDir = AppDomain.CurrentDomain.BaseDirectory;
            var extractDir = Path.Combine(Path.GetDirectoryName(patchPath)!, "patch_extract");
            if (Directory.Exists(extractDir)) Directory.Delete(extractDir, true);

            await Task.Run(() => ZipFile.ExtractToDirectory(patchPath, extractDir));

            switch (patch.PatchType)
            {
                case "dll":
                    // 替换 DLL 文件
                    foreach (var dll in Directory.GetFiles(extractDir, "*.dll"))
                    {
                        var targetName = Path.GetFileName(dll);
                        var targetPath = Path.Combine(appDir, targetName);
                        if (File.Exists(targetPath))
                        {
                            File.Copy(targetPath, targetPath + ".bak", true);
                            File.Copy(dll, targetPath, true);
                        }
                    }
                    break;

                case "script":
                    // 复制脚本/资源文件
                    foreach (var file in patch.TargetFiles)
                    {
                        var sourceFile = Path.Combine(extractDir, file);
                        var targetFile = Path.Combine(appDir, file);
                        if (File.Exists(sourceFile))
                        {
                            var targetDir = Path.GetDirectoryName(targetFile);
                            if (!string.IsNullOrEmpty(targetDir) && !Directory.Exists(targetDir))
                                Directory.CreateDirectory(targetDir);
                            File.Copy(sourceFile, targetFile, true);
                        }
                    }
                    break;

                case "resource":
                    // 替换资源文件
                    foreach (var file in Directory.GetFiles(extractDir, "*", SearchOption.AllDirectories))
                    {
                        var relativePath = Path.GetRelativePath(extractDir, file);
                        var targetPath = Path.Combine(appDir, relativePath);
                        var targetDir = Path.GetDirectoryName(targetPath);
                        if (!string.IsNullOrEmpty(targetDir) && !Directory.Exists(targetDir))
                            Directory.CreateDirectory(targetDir);
                        File.Copy(file, targetPath, true);
                    }
                    break;
            }

            // 清理
            Directory.Delete(extractDir, true);
            File.Delete(patchPath);

            return true;
        }
        catch (Exception ex)
        {
            Debug.WriteLine($"[UpdateService] 应用补丁失败: {ex.Message}");
            return false;
        }
    }

    // ==================== 设置管理 ====================

    public void SkipCurrentVersion()
    {
        if (_latestUpdateInfo != null)
        {
            _updateSettings.SkippedVersion = _latestUpdateInfo.LatestVersion;
            SaveSettings();
            SetStatus(UpdateStatus.Skipped);
        }
    }

    public void SaveSettings()
    {
        _settings.Set("Update.AutoCheck", _updateSettings.AutoCheckEnabled);
        _settings.Set("Update.CheckInterval", _updateSettings.CheckIntervalHours);
        _settings.Set("Update.AutoDownload", _updateSettings.AutoDownload);
        _settings.Set("Update.HotUpdate", _updateSettings.HotUpdateEnabled);
        _settings.Set("Update.Channel", _updateSettings.Channel);
        _settings.Set("Update.SkippedVersion", _updateSettings.SkippedVersion ?? "");
        _settings.Set("Update.LastCheckTime", _updateSettings.LastCheckTime?.ToString("O") ?? "");
        _settings.Save();
    }

    private UpdateSettings LoadSettings()
    {
        return new UpdateSettings
        {
            AutoCheckEnabled = _settings.Get("Update.AutoCheck", true),
            CheckIntervalHours = _settings.Get("Update.CheckInterval", 24),
            AutoDownload = _settings.Get("Update.AutoDownload", false),
            HotUpdateEnabled = _settings.Get("Update.HotUpdate", true),
            Channel = _settings.Get("Update.Channel", "stable"),
            SkippedVersion = _settings.Get<string>("Update.SkippedVersion"),
            LastCheckTime = ParseDateTime(_settings.Get<string>("Update.LastCheckTime"))
        };
    }

    // ==================== 辅助方法 ====================

    private void SetStatus(UpdateStatus newStatus)
    {
        if (_status == newStatus) return;
        _status = newStatus;
        StatusChanged?.Invoke(this, newStatus);
    }

    private static async Task<string> ComputeSha256Async(string filePath)
    {
        using var sha256 = SHA256.Create();
        await using var stream = File.OpenRead(filePath);
        var hash = await sha256.ComputeHashAsync(stream);
        return BitConverter.ToString(hash).Replace("-", "").ToLowerInvariant();
    }

    private static async Task<ApiResponse<T>> ParseResponseAsync<T>(HttpResponseMessage response)
    {
        var json = await response.Content.ReadAsStringAsync();
        return JsonSerializer.Deserialize<ApiResponse<T>>(json,
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true })
            ?? new ApiResponse<T> { Code = 500, Message = "响应解析失败" };
    }

    private static string GetPlatformName()
    {
        if (OperatingSystem.IsWindows()) return "windows";
        if (OperatingSystem.IsMacOS()) return "macos";
        if (OperatingSystem.IsLinux()) return "linux";
        return "unknown";
    }

    private static string GetArchitecture()
    {
        return System.Runtime.InteropServices.RuntimeInformation.ProcessArchitecture.ToString().ToLower();
    }

    private static DateTime? ParseDateTime(string? value)
    {
        if (string.IsNullOrEmpty(value)) return null;
        return DateTime.TryParse(value, out var dt) ? dt : null;
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        StopAutoCheck();
        _downloadCts?.Cancel();
        _downloadCts?.Dispose();
        _lock.Dispose();
    }
}