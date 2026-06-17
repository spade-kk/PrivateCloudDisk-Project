using PrivateCloudDisk.Models;

namespace PrivateCloudDisk.Services.Interfaces;

/// <summary>
/// 上传服务接口 — 对接后端 UploadsController + FastAPI 文件服务
/// </summary>
public interface IUploadService
{
    /// <summary>创建上传会话 POST /uploads</summary>
    Task<UploadSessionResponse> CreateUploadSessionAsync(CreateUploadSessionRequest request);

    /// <summary>上传分片 PUT /files/uploads/{uploadsId}/chunks</summary>
    Task UploadChunkAsync(string uploadsId, int chunkIndex, byte[] chunkData,
        IProgress<double>? progress = null);

    /// <summary>合并分片 POST /files/uploads/{uploadsId}/merge</summary>
    Task<TaskStatusInfo> MergeChunksAsync(string uploadsId);

    /// <summary>分片上传整个文件 (完整流程)</summary>
    Task<bool> UploadFileAsync(string filePath, string fileName, string nodeId,
        IProgress<(double percent, string status)>? progress = null,
        CancellationToken cancellationToken = default);
}

/// <summary>
/// 下载服务接口 — 对接 FastAPI 文件服务
/// </summary>
public interface IDownloadService
{
    /// <summary>申请操作凭证 POST /files/operation-tokens</summary>
    Task<OperationTokenResponse> RequestOperationTokenAsync(string fileId, string operationType);

    /// <summary>下载文件 GET /downloads/files/{fileId}/content?token={token}</summary>
    Task DownloadFileAsync(string fileId, string token, string savePath,
        IProgress<double>? progress = null,
        CancellationToken cancellationToken = default);

    /// <summary>获取缩略图 URL</summary>
    string GetThumbnailUrl(string nodeId, string fileName);

    /// <summary>下载文件完整流程 (获取凭证 + 下载)</summary>
    Task DownloadFileWithTokenAsync(string fileId, string savePath,
        IProgress<(double percent, string status)>? progress = null,
        CancellationToken cancellationToken = default);
}

/// <summary>
/// 任务状态服务接口
/// </summary>
public interface ITaskService
{
    /// <summary>获取任务状态</summary>
    Task<TaskStatusInfo> GetTaskStatusAsync(string taskId);

    /// <summary>等待任务完成 (轮询)</summary>
    Task<TaskStatusInfo> WaitForCompletionAsync(string taskId,
        TimeSpan? pollInterval = null,
        TimeSpan? timeout = null,
        CancellationToken cancellationToken = default);
}