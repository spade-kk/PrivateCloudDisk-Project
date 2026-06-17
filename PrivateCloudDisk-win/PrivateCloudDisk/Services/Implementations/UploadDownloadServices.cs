using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using PrivateCloudDisk.Models;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// 上传服务 — 对接后端 UploadsController + FastAPI 文件服务
/// 支持分片上传、断点续传、进度回调
/// </summary>
public class UploadService : BaseApiService, IUploadService
{
    public UploadService(IHttpClientFactory httpFactory, IAuthService authService)
        : base(httpFactory, authService) { }

    public async Task<UploadSessionResponse> CreateUploadSessionAsync(CreateUploadSessionRequest request)
    {
        var client = CreateClient("platform");
        var response = await client.PostAsJsonAsync("/uploads", request);
        var apiResp = await ParseAsync<UploadSessionResponse>(response);
        return apiResp.GetDataOrThrow();
    }

    public async Task UploadChunkAsync(string uploadsId, int chunkIndex, byte[] chunkData,
        IProgress<double>? progress = null)
    {
        var client = CreateClient("file");
        using var content = new MultipartFormDataContent();
        var byteContent = new ByteArrayContent(chunkData);
        byteContent.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream");
        content.Add(byteContent, "file", $"chunk_{chunkIndex}");
        content.Add(new StringContent(chunkIndex.ToString()), "chunk_index");

        var response = await client.PutAsync($"/files/uploads/{uploadsId}/chunks", content);
        response.EnsureSuccessStatusCode();
        progress?.Report(1.0);
    }

    public async Task<TaskStatusInfo> MergeChunksAsync(string uploadsId)
    {
        var client = CreateClient("file");
        var response = await client.PostAsync($"/files/uploads/{uploadsId}/merge", null);
        response.EnsureSuccessStatusCode();
        var json = await response.Content.ReadAsStringAsync();
        var result = JsonSerializer.Deserialize<TaskStatusInfo>(json,
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
        return result ?? throw new ApiException(500, "合并响应解析失败");
    }

    public async Task<bool> UploadFileAsync(string filePath, string fileName, string nodeId,
        IProgress<(double percent, string status)>? progress = null,
        CancellationToken cancellationToken = default)
    {
        var fileInfo = new FileInfo(filePath);
        if (!fileInfo.Exists)
            throw new FileNotFoundException("文件不存在", filePath);

        var totalChunks = (int)Math.Ceiling((double)fileInfo.Length / AppConfig.ChunkSize);
        var fileType = Path.GetExtension(fileName).TrimStart('.').ToLower();

        // 1. 创建上传会话
        progress?.Report((0, "创建上传会话..."));
        var session = await CreateUploadSessionAsync(new CreateUploadSessionRequest
        {
            TotalChunks = totalChunks,
            FileSize = fileInfo.Length,
            FileChecksum = string.Empty,
            ChunksMaxSize = AppConfig.ChunkSize,
            FileName = fileName,
            FileType = string.IsNullOrEmpty(fileType) ? "unknown" : fileType,
            NodeId = nodeId
        });

        // 2. 分片上传
        var buffer = new byte[AppConfig.ChunkSize];
        await using var fs = File.OpenRead(filePath);

        for (int i = 0; i < totalChunks; i++)
        {
            cancellationToken.ThrowIfCancellationRequested();

            var bytesRead = await fs.ReadAsync(buffer, 0, AppConfig.ChunkSize, cancellationToken);
            var chunkData = new byte[bytesRead];
            Array.Copy(buffer, chunkData, bytesRead);

            progress?.Report(((double)i / totalChunks * 0.8, $"上传分片 {i + 1}/{totalChunks}..."));
            await UploadChunkAsync(session.UploadsId, i, chunkData);
        }

        // 3. 合并分片
        progress?.Report((0.85, "合并分片..."));
        var mergeResult = await MergeChunksAsync(session.UploadsId);

        // 4. 轮询任务状态
        var taskService = App.Services.GetRequiredService<ITaskService>();
        var finalStatus = await taskService.WaitForCompletionAsync(mergeResult.TaskId,
            cancellationToken: cancellationToken);

        if (finalStatus.IsSuccess)
        {
            progress?.Report((1.0, "上传完成"));
            return true;
        }

        progress?.Report((1.0, $"上传失败: {finalStatus.Error}"));
        return false;
    }
}

/// <summary>
/// 下载服务 — 对接 FastAPI 文件服务
/// </summary>
public class DownloadService : BaseApiService, IDownloadService
{
    public DownloadService(IHttpClientFactory httpFactory, IAuthService authService)
        : base(httpFactory, authService) { }

    public async Task<OperationTokenResponse> RequestOperationTokenAsync(string fileId, string operationType)
    {
        var client = CreateClient("file");
        var body = new OperationTokenRequest { FileId = fileId, OperationType = operationType };
        var response = await client.PostAsJsonAsync("/files/operation-tokens", body);
        response.EnsureSuccessStatusCode();
        var json = await response.Content.ReadAsStringAsync();
        var result = JsonSerializer.Deserialize<OperationTokenResponse>(json,
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
        return result ?? throw new ApiException(500, "凭证响应解析失败");
    }

    public async Task DownloadFileAsync(string fileId, string token, string savePath,
        IProgress<double>? progress = null,
        CancellationToken cancellationToken = default)
    {
        var client = CreateClient("file");
        var url = $"/downloads/files/{fileId}/content?token={Uri.EscapeDataString(token)}";

        using var response = await client.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        response.EnsureSuccessStatusCode();

        var totalBytes = response.Content.Headers.ContentLength ?? -1;
        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        await using var fileStream = new FileStream(savePath, FileMode.Create, FileAccess.Write, FileShare.None,
            8192, useAsync: true);

        var buffer = new byte[8192];
        long totalRead = 0;
        int bytesRead;

        while ((bytesRead = await stream.ReadAsync(buffer, 0, buffer.Length, cancellationToken)) > 0)
        {
            await fileStream.WriteAsync(buffer, 0, bytesRead, cancellationToken);
            totalRead += bytesRead;

            if (totalBytes > 0)
                progress?.Report((double)totalRead / totalBytes);
        }
    }

    public string GetThumbnailUrl(string nodeId, string fileName)
    {
        return $"{AppConfig.FileServiceBaseUrl}/files/nodes/{nodeId}/thumbnails/{Uri.EscapeDataString(fileName)}";
    }

    public async Task DownloadFileWithTokenAsync(string fileId, string savePath,
        IProgress<(double percent, string status)>? progress = null,
        CancellationToken cancellationToken = default)
    {
        progress?.Report((0, "获取下载凭证..."));
        var tokenResp = await RequestOperationTokenAsync(fileId, "download");

        progress?.Report((0.1, "开始下载..."));
        var downloadProgress = new Progress<double>(p =>
            progress?.Report((0.1 + p * 0.9, $"下载中 {p * 100:F0}%")));

        await DownloadFileAsync(fileId, tokenResp.OperationToken, savePath, downloadProgress, cancellationToken);
        progress?.Report((1.0, "下载完成"));
    }
}

/// <summary>
/// 任务状态服务
/// </summary>
public class TaskService : BaseApiService, ITaskService
{
    public TaskService(IHttpClientFactory httpFactory, IAuthService authService)
        : base(httpFactory, authService) { }

    public async Task<TaskStatusInfo> GetTaskStatusAsync(string taskId)
    {
        var client = CreateClient("file");
        var response = await client.GetAsync($"/files/tasks/{taskId}");
        response.EnsureSuccessStatusCode();
        var json = await response.Content.ReadAsStringAsync();
        var result = JsonSerializer.Deserialize<TaskStatusInfo>(json,
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
        return result ?? throw new ApiException(500, "任务状态解析失败");
    }

    public async Task<TaskStatusInfo> WaitForCompletionAsync(string taskId,
        TimeSpan? pollInterval = null,
        TimeSpan? timeout = null,
        CancellationToken cancellationToken = default)
    {
        var interval = pollInterval ?? TimeSpan.FromSeconds(1);
        var deadline = DateTime.UtcNow + (timeout ?? TimeSpan.FromMinutes(10));

        while (DateTime.UtcNow < deadline)
        {
            cancellationToken.ThrowIfCancellationRequested();

            var status = await GetTaskStatusAsync(taskId);
            if (status.IsCompleted)
                return status;

            await Task.Delay(interval, cancellationToken);
        }

        throw new TimeoutException($"任务 {taskId} 超时未完成");
    }
}