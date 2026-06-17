using System.Text.Json.Serialization;

namespace PrivateCloudDisk.Models;

/// <summary>
/// 回收站条目 (对应后端 TrashTargetVO)
/// </summary>
public class TrashItem
{
    [JsonPropertyName("trash_id")]
    public string TrashId { get; set; } = string.Empty;

    [JsonPropertyName("target_id")]
    public string? TargetId { get; set; }

    [JsonPropertyName("target_name")]
    public string TargetName { get; set; } = string.Empty;

    [JsonPropertyName("target_type")]
    public string? TargetType { get; set; }

    [JsonPropertyName("target_size")]
    public long? TargetSize { get; set; }

    [JsonPropertyName("deleted_time")]
    public DateTime? DeletedTime { get; set; }

    [JsonPropertyName("original_path")]
    public string? OriginalPath { get; set; }

    [JsonIgnore]
    public bool IsFileItem => string.Equals(TargetType, "FILE", StringComparison.OrdinalIgnoreCase);
}

/// <summary>
/// 收藏条目 (对应后端 FileStarEntity)
/// </summary>
public class StarItem
{
    [JsonPropertyName("star_id")]
    public string StarId { get; set; } = string.Empty;

    [JsonPropertyName("file_id")]
    public string FileId { get; set; } = string.Empty;

    [JsonPropertyName("starred_at")]
    public DateTime? StarredAt { get; set; }

    // 以下字段由前端额外获取 (因后端 FileStarEntity 不包含文件名等信息)
    [JsonIgnore]
    public string? FileName { get; set; }

    [JsonIgnore]
    public long? FileSize { get; set; }

    [JsonIgnore]
    public string? FileType { get; set; }
}

/// <summary>
/// 配额信息 (对应后端 QuotaVO)
/// </summary>
public class QuotaInfo
{
    [JsonPropertyName("total_capacity")]
    public long TotalCapacity { get; set; }

    [JsonPropertyName("used_capacity")]
    public long UsedCapacity { get; set; }

    [JsonIgnore]
    public long AvailableCapacity => TotalCapacity - UsedCapacity;

    [JsonIgnore]
    public double UsagePercent => TotalCapacity > 0
        ? (double)UsedCapacity / TotalCapacity * 100
        : 0;
}