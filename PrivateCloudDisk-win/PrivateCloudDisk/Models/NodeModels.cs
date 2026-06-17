using System.Text.Json.Serialization;

namespace PrivateCloudDisk.Models;

/// <summary>
/// 文件/文件夹节点 (对应后端 FileVO / NodeVO)
/// </summary>
public class NodeItem
{
    [JsonPropertyName("id")]
    public string Id { get; set; } = string.Empty;

    [JsonPropertyName("node_id")]
    public string? NodeId { get; set; }

    [JsonPropertyName("file_id")]
    public string? FileId { get; set; }

    [JsonPropertyName("name")]
    public string Name { get; set; } = string.Empty;

    [JsonPropertyName("node_name")]
    public string? NodeName { get; set; }

    [JsonPropertyName("file_name")]
    public string? FileName { get; set; }

    [JsonPropertyName("size")]
    public long Size { get; set; }

    [JsonPropertyName("node_size")]
    public long? NodeSize { get; set; }

    [JsonPropertyName("file_size")]
    public long? FileSize { get; set; }

    [JsonPropertyName("type")]
    public string? Type { get; set; }

    [JsonPropertyName("node_type")]
    public string? NodeType { get; set; }

    [JsonPropertyName("isFile")]
    public bool IsFile { get; set; }

    [JsonPropertyName("file_type")]
    public string? FileType { get; set; }

    [JsonPropertyName("is_folder")]
    public bool? IsFolder { get; set; }

    [JsonPropertyName("uploaded_time")]
    public DateTime? UploadedTime { get; set; }

    [JsonPropertyName("created_time")]
    public DateTime? CreatedTime { get; set; }

    [JsonPropertyName("updated_time")]
    public DateTime? UpdatedTime { get; set; }

    // ── 计算属性 ────────────────────────────────────────
    [JsonIgnore]
    public string EffectiveId => Id ?? NodeId ?? FileId ?? string.Empty;

    [JsonIgnore]
    public string EffectiveName => Name ?? NodeName ?? FileName ?? string.Empty;

    [JsonIgnore]
    public long EffectiveSize => Size > 0 ? Size : (NodeSize ?? FileSize ?? 0);

    [JsonIgnore]
    public bool IsDirectory => !IsFile && (!IsFolder.HasValue || IsFolder.Value);

    [JsonIgnore]
    public DateTime EffectiveTime => UploadedTime ?? CreatedTime ?? UpdatedTime ?? DateTime.MinValue;
}

/// <summary>
/// 文件详情 (对应后端 FileVO)
/// </summary>
public class FileDetail
{
    [JsonPropertyName("file_id")]
    public string FileId { get; set; } = string.Empty;

    [JsonPropertyName("file_name")]
    public string FileName { get; set; } = string.Empty;

    [JsonPropertyName("file_size")]
    public long FileSize { get; set; }

    [JsonPropertyName("file_type")]
    public string? FileType { get; set; }

    [JsonPropertyName("file_checksum")]
    public string? FileChecksum { get; set; }

    [JsonPropertyName("uploaded_time")]
    public DateTime? UploadedTime { get; set; }

    [JsonPropertyName("node_id")]
    public string? NodeId { get; set; }

    [JsonPropertyName("is_starred")]
    public bool IsStarred { get; set; }
}

/// <summary>
/// 创建文件夹请求 (对应后端 CreateFolderRequest)
/// </summary>
public class CreateFolderRequest
{
    [JsonPropertyName("folder_name")]
    public string FolderName { get; set; } = string.Empty;

    [JsonPropertyName("node_id")]
    public string NodeId { get; set; } = string.Empty;
}

/// <summary>
/// 重命名文件请求 (对应后端 RenameFileRequest)
/// </summary>
public class RenameFileRequest
{
    [JsonPropertyName("file_new_name")]
    public string FileNewName { get; set; } = string.Empty;
}

/// <summary>
/// 重命名节点请求 (对应后端 RenameNodeRequest)
/// </summary>
public class RenameNodeRequest
{
    [JsonPropertyName("new_node_name")]
    public string NewNodeName { get; set; } = string.Empty;
}

/// <summary>
/// 移动文件请求 (对应后端 MoveFileRequest)
/// </summary>
public class MoveFileRequest
{
    [JsonPropertyName("target_node_id")]
    public string TargetNodeId { get; set; } = string.Empty;
}

/// <summary>
/// 移动节点请求 (对应后端 MoveNodeRequest)
/// </summary>
public class MoveNodeRequest
{
    [JsonPropertyName("target_position")]
    public string TargetPosition { get; set; } = string.Empty;
}