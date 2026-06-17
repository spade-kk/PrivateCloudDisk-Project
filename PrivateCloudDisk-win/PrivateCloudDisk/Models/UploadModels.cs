using System.Text.Json.Serialization;

namespace PrivateCloudDisk.Models;

/// <summary>
/// 创建上传会话请求 (对应后端 CreateUploadSessionRequest)
/// </summary>
public class CreateUploadSessionRequest
{
    [JsonPropertyName("total_chunks")]
    public int TotalChunks { get; set; }

    [JsonPropertyName("file_size")]
    public long FileSize { get; set; }

    [JsonPropertyName("file_checksum")]
    public string FileChecksum { get; set; } = string.Empty;

    [JsonPropertyName("chunks_max_size")]
    public int ChunksMaxSize { get; set; }

    [JsonPropertyName("file_name")]
    public string FileName { get; set; } = string.Empty;

    [JsonPropertyName("file_type")]
    public string FileType { get; set; } = string.Empty;

    [JsonPropertyName("node_id")]
    public string NodeId { get; set; } = string.Empty;
}

/// <summary>
/// 上传会话响应
/// </summary>
public class UploadSessionResponse
{
    [JsonPropertyName("uploads_id")]
    public string UploadsId { get; set; } = string.Empty;
}

/// <summary>
/// 操作凭证请求 (对应后端 OperationTokenRequest)
/// </summary>
public class OperationTokenRequest
{
    [JsonPropertyName("file_id")]
    public string FileId { get; set; } = string.Empty;

    [JsonPropertyName("operation_type")]
    public string OperationType { get; set; } = string.Empty; // "download" / "preview"
}

/// <summary>
/// 操作凭证响应
/// </summary>
public class OperationTokenResponse
{
    [JsonPropertyName("operation_token")]
    public string OperationToken { get; set; } = string.Empty;
}

/// <summary>
/// 任务状态 (对应后端 TaskStatusVO)
/// </summary>
public class TaskStatusInfo
{
    [JsonPropertyName("task_id")]
    public string TaskId { get; set; } = string.Empty;

    [JsonPropertyName("status")]
    public string Status { get; set; } = string.Empty; // "PENDING" / "PROCESSING" / "SUCCESS" / "FAILED"

    [JsonPropertyName("progress")]
    public double Progress { get; set; }

    [JsonPropertyName("result")]
    public string? Result { get; set; }

    [JsonPropertyName("error")]
    public string? Error { get; set; }

    [JsonIgnore]
    public bool IsCompleted => Status == "SUCCESS" || Status == "FAILED";

    [JsonIgnore]
    public bool IsSuccess => Status == "SUCCESS";
}

/// <summary>
/// 高级搜索请求 (对应后端 FileSearchRequest)
/// </summary>
public class SearchRequest
{
    [JsonPropertyName("keyword")]
    public string Keyword { get; set; } = string.Empty;

    [JsonPropertyName("page")]
    public int Page { get; set; } = 1;

    [JsonPropertyName("size")]
    public int Size { get; set; } = 20;

    [JsonPropertyName("sortField")]
    public string? SortField { get; set; }

    [JsonPropertyName("asc")]
    public bool Asc { get; set; }

    [JsonPropertyName("filters")]
    public Dictionary<string, string>? Filters { get; set; }

    [JsonPropertyName("highlightFields")]
    public List<string>? HighlightFields { get; set; }
}

/// <summary>
/// 搜索结果响应 (对应后端 FileSearchVo)
/// </summary>
public class SearchResult
{
    [JsonPropertyName("total")]
    public long Total { get; set; }

    [JsonPropertyName("hits")]
    public List<SearchHit> Hits { get; set; } = new();

    [JsonPropertyName("aggregations")]
    public Dictionary<string, object>? Aggregations { get; set; }
}

/// <summary>
/// 搜索命中项
/// </summary>
public class SearchHit
{
    [JsonPropertyName("_id")]
    public string? Id { get; set; }

    [JsonPropertyName("_source")]
    public SearchSource? Source { get; set; }

    [JsonPropertyName("highlight")]
    public Dictionary<string, List<string>>? Highlight { get; set; }
}

/// <summary>
/// 搜索源文档
/// </summary>
public class SearchSource
{
    [JsonPropertyName("file_id")]
    public string? FileId { get; set; }

    [JsonPropertyName("file_name")]
    public string? FileName { get; set; }

    [JsonPropertyName("file_size")]
    public long? FileSize { get; set; }

    [JsonPropertyName("file_type")]
    public string? FileType { get; set; }

    [JsonPropertyName("uploaded_time")]
    public DateTime? UploadedTime { get; set; }
}