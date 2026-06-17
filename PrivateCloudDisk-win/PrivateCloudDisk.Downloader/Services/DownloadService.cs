using System;
using System.Collections.Concurrent;
using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Security.Cryptography;
using System.Threading;
using System.Threading.Tasks;

namespace PrivateCloudDisk.Downloader.Services;

/// <summary>
/// 企业级文件下载服务 — 支持断点续传、多线程分片下载、哈希校验
/// 参考百度网盘下载器设计
/// </summary>
public class DownloadService
{
    private readonly HttpClient _httpClient;
    private readonly ConcurrentDictionary<string, DownloadTask> _tasks = new();
    private readonly ConcurrentDictionary<string, CancellationTokenSource> _cancellations = new();

    private const int BufferSize = 8192;
    private const int MaxConcurrentChunks = 4;
    private const long ChunkSize = 4 * 1024 * 1024; // 4MB per chunk

    public event EventHandler<DownloadTask>? TaskStatusChanged;
    public event EventHandler<DownloadProgressData>? ProgressChanged;

    public DownloadService(HttpClient httpClient)
    {
        _httpClient = httpClient;
        _httpClient.DefaultRequestHeaders.Add("User-Agent",
            "PrivateCloudDisk-Downloader/1.0");
        _httpClient.Timeout = TimeSpan.FromMinutes(30);
    }

    /// <summary>
    /// 创建下载任务
    /// </summary>
    public DownloadTask CreateTask(string url, string savePath, string fileName,
        long totalSize, string? expectedHash = null)
    {
        var task = new DownloadTask
        {
            Url = url,
            SavePath = savePath,
            FileName = fileName,
            TotalSize = totalSize,
            ExpectedHash = expectedHash,
            Status = DownloadTaskStatus.Pending
        };

        _tasks[task.Id] = task;
        return task;
    }

    /// <summary>
    /// 开始下载（支持断点续传）
    /// </summary>
    public async Task StartDownloadAsync(string taskId)
    {
        if (!_tasks.TryGetValue(taskId, out var task))
            return;

        var cts = new CancellationTokenSource();
        _cancellations[taskId] = cts;

        try
        {
            task.Status = DownloadTaskStatus.Downloading;
            TaskStatusChanged?.Invoke(this, task);

            var filePath = Path.Combine(task.SavePath, task.FileName);
            var tempPath = filePath + ".pcddownload";

            // 检查已有文件（断点续传）
            long existingLength = 0;
            if (File.Exists(tempPath))
            {
                existingLength = new FileInfo(tempPath).Length;
                task.DownloadedSize = existingLength;
            }

            // 如果文件已存在且大小匹配，跳过下载
            if (File.Exists(filePath) && new FileInfo(filePath).Length == task.TotalSize)
            {
                task.DownloadedSize = task.TotalSize;
                await VerifyAndCompleteAsync(task, filePath);
                return;
            }

            // 判断是否使用多线程下载
            if (task.TotalSize > ChunkSize * 2 && existingLength == 0)
            {
                await DownloadMultiChunkAsync(task, tempPath, cts.Token);
            }
            else
            {
                await DownloadSingleStreamAsync(task, tempPath, existingLength, cts.Token);
            }

            // 验证哈希
            if (cts.Token.IsCancellationRequested) return;
            await VerifyAndCompleteAsync(task, filePath, tempPath);
        }
        catch (OperationCanceledException)
        {
            task.Status = DownloadTaskStatus.Paused;
            TaskStatusChanged?.Invoke(this, task);
        }
        catch (Exception ex)
        {
            task.Status = DownloadTaskStatus.Failed;
            task.ErrorMessage = ex.Message;
            TaskStatusChanged?.Invoke(this, task);
        }
    }

    /// <summary>
    /// 单流下载（支持断点续传）
    /// </summary>
    private async Task DownloadSingleStreamAsync(DownloadTask task, string tempPath,
        long startOffset, CancellationToken ct)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, task.Url);

        if (startOffset > 0)
            request.Headers.Range = new System.Net.Http.Headers.RangeHeaderValue(
                startOffset, null);

        using var response = await _httpClient.SendAsync(request,
            HttpCompletionOption.ResponseHeadersRead, ct);
        response.EnsureSuccessStatusCode();

        using var stream = await response.Content.ReadAsStreamAsync(ct);
        using var fileStream = new FileStream(tempPath, FileMode.Append,
            FileAccess.Write, FileShare.None, BufferSize, true);

        var buffer = new byte[BufferSize];
        var stopwatch = Stopwatch.StartNew();
        long lastBytes = task.DownloadedSize;
        long lastReportBytes = 0;
        var lastReportTime = DateTime.UtcNow;

        while (true)
        {
            ct.ThrowIfCancellationRequested();
            var read = await stream.ReadAsync(buffer, 0, buffer.Length, ct);
            if (read == 0) break;

            await fileStream.WriteAsync(buffer, 0, read, ct);
            task.DownloadedSize += read;

            // 计算速度（每秒报告一次）
            var elapsed = DateTime.UtcNow - lastReportTime;
            if (elapsed.TotalMilliseconds >= 500)
            {
                var bytesSinceLast = task.DownloadedSize - lastReportBytes;
                var speed = bytesSinceLast / elapsed.TotalSeconds;
                var remaining = task.TotalSize - task.DownloadedSize;
                var eta = speed > 0 ? TimeSpan.FromSeconds(remaining / speed) : TimeSpan.Zero;

                ReportProgress(task, speed, eta);
                lastReportBytes = task.DownloadedSize;
                lastReportTime = DateTime.UtcNow;
            }
        }
    }

    /// <summary>
    /// 多线程分片下载
    /// </summary>
    private async Task DownloadMultiChunkAsync(DownloadTask task, string tempPath,
        CancellationToken ct)
    {
        var chunkCount = Math.Min(MaxConcurrentChunks,
            (int)Math.Ceiling((double)task.TotalSize / ChunkSize));
        var chunkSize = task.TotalSize / chunkCount;

        var semaphore = new SemaphoreSlim(MaxConcurrentChunks);
        var chunkTasks = new List<Task>();

        var stopwatch = Stopwatch.StartNew();
        long totalDownloaded = 0;
        var lastReportTime = DateTime.UtcNow;
        long lastReportBytes = 0;

        for (int i = 0; i < chunkCount; i++)
        {
            var chunkIndex = i;
            var start = chunkIndex * chunkSize;
            var end = (chunkIndex == chunkCount - 1) ? task.TotalSize - 1 : start + chunkSize - 1;
            var chunkFile = $"{tempPath}.part{chunkIndex}";

            await semaphore.WaitAsync(ct);

            var chunkTask = Task.Run(async () =>
            {
                try
                {
                    using var request = new HttpRequestMessage(HttpMethod.Get, task.Url);
                    request.Headers.Range = new System.Net.Http.Headers.RangeHeaderValue(start, end);

                    using var response = await _httpClient.SendAsync(request,
                        HttpCompletionOption.ResponseHeadersRead, ct);

                    using var stream = await response.Content.ReadAsStreamAsync(ct);
                    using var fileStream = File.Create(chunkFile, BufferSize);

                    var buffer = new byte[BufferSize];
                    while (true)
                    {
                        ct.ThrowIfCancellationRequested();
                        var read = await stream.ReadAsync(buffer, 0, buffer.Length, ct);
                        if (read == 0) break;
                        await fileStream.WriteAsync(buffer, 0, read, ct);

                        var downloaded = Interlocked.Add(ref totalDownloaded, read);
                        task.DownloadedSize = downloaded;

                        var elapsed = DateTime.UtcNow - lastReportTime;
                        if (elapsed.TotalMilliseconds >= 500)
                        {
                            var bytesSinceLast = downloaded - lastReportBytes;
                            var speed = bytesSinceLast / elapsed.TotalSeconds;
                            var remaining = task.TotalSize - downloaded;
                            var eta = speed > 0 ? TimeSpan.FromSeconds(remaining / speed) : TimeSpan.Zero;

                            ReportProgress(task, speed, eta);
                            lastReportBytes = downloaded;
                            lastReportTime = DateTime.UtcNow;
                        }
                    }
                }
                finally
                {
                    semaphore.Release();
                }
            }, ct);

            chunkTasks.Add(chunkTask);
        }

        await Task.WhenAll(chunkTasks);

        // 合并分片
        using (var output = File.Create(tempPath))
        {
            for (int i = 0; i < chunkCount; i++)
            {
                var chunkFile = $"{tempPath}.part{i}";
                using (var input = File.OpenRead(chunkFile))
                {
                    await input.CopyToAsync(output, ct);
                }
                File.Delete(chunkFile);
            }
        }
    }

    /// <summary>
    /// 哈希校验并完成下载
    /// </summary>
    private async Task VerifyAndCompleteAsync(DownloadTask task, string filePath,
        string? tempPath = null)
    {
        task.Status = DownloadTaskStatus.Verifying;
        TaskStatusChanged?.Invoke(this, task);

        var sourcePath = tempPath ?? filePath;

        if (!string.IsNullOrEmpty(task.ExpectedHash))
        {
            var actualHash = await ComputeSha256Async(sourcePath);
            if (!string.Equals(actualHash, task.ExpectedHash, StringComparison.OrdinalIgnoreCase))
            {
                task.Status = DownloadTaskStatus.Failed;
                task.ErrorMessage = "文件哈希校验失败，文件可能已损坏";
                TaskStatusChanged?.Invoke(this, task);

                if (task.RetryCount < DownloadTask.MaxRetryCount)
                {
                    task.RetryCount++;
                    task.DownloadedSize = 0;
                    File.Delete(sourcePath);
                    await StartDownloadAsync(task.Id);
                }
                return;
            }
        }

        // 移动临时文件到最终位置
        if (tempPath != null && File.Exists(tempPath))
        {
            if (File.Exists(filePath))
                File.Delete(filePath);
            File.Move(tempPath, filePath);
        }

        task.Status = DownloadTaskStatus.Completed;
        task.CompletedAt = DateTime.UtcNow;
        TaskStatusChanged?.Invoke(this, task);
    }

    private static async Task<string> ComputeSha256Async(string filePath)
    {
        using var sha256 = SHA256.Create();
        using var stream = File.OpenRead(filePath);
        var hash = await sha256.ComputeHashAsync(stream);
        return BitConverter.ToString(hash).Replace("-", "").ToLowerInvariant();
    }

    private void ReportProgress(DownloadTask task, double speed, TimeSpan eta)
    {
        ProgressChanged?.Invoke(this, new DownloadProgressData
        {
            ProgressPercent = task.Progress,
            BytesDownloaded = task.DownloadedSize,
            TotalBytes = task.TotalSize,
            SpeedBytesPerSecond = speed,
            EstimatedTimeRemaining = eta,
            CurrentFile = task.FileName
        });
    }

    /// <summary>
    /// 暂停下载
    /// </summary>
    public void PauseDownload(string taskId)
    {
        if (_cancellations.TryGetValue(taskId, out var cts))
        {
            cts.Cancel();
            _cancellations.TryRemove(taskId, out _);
        }
    }

    /// <summary>
    /// 取消下载
    /// </summary>
    public void CancelDownload(string taskId)
    {
        if (_cancellations.TryGetValue(taskId, out var cts))
        {
            cts.Cancel();
            _cancellations.TryRemove(taskId, out _);
        }

        if (_tasks.TryGetValue(taskId, out var task))
        {
            task.Status = DownloadTaskStatus.Cancelled;
            TaskStatusChanged?.Invoke(this, task);

            // 清理临时文件
            var tempPath = Path.Combine(task.SavePath, task.FileName) + ".pcddownload";
            try { if (File.Exists(tempPath)) File.Delete(tempPath); } catch { }
        }
    }
}