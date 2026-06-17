using System.Text.Json.Serialization;

namespace PrivateCloudDisk.Models;

/// <summary>
/// 后端统一 API 响应包装
/// 后端 Spring Boot 统一返回: { code, data, message }
/// </summary>
public class ApiResponse<T>
{
    [JsonPropertyName("code")]
    public int Code { get; set; }

    [JsonPropertyName("data")]
    public T? Data { get; set; }

    [JsonPropertyName("message")]
    public string? Message { get; set; }

    [JsonIgnore]
    public bool IsSuccess => Code == 200;

    /// <summary>获取 data，若失败则抛出异常</summary>
    public T GetDataOrThrow()
    {
        if (!IsSuccess || Data == null)
            throw new ApiException(Code, Message ?? "请求失败");
        return Data;
    }
}

/// <summary>
/// API 异常
/// </summary>
public class ApiException : Exception
{
    public int Code { get; }

    public ApiException(int code, string message) : base(message)
    {
        Code = code;
    }
}

/// <summary>
/// 分页响应
/// </summary>
public class PagedResult<T>
{
    [JsonPropertyName("items")]
    public List<T> Items { get; set; } = new();

    [JsonPropertyName("total")]
    public long Total { get; set; }

    [JsonPropertyName("page")]
    public int Page { get; set; }

    [JsonPropertyName("size")]
    public int Size { get; set; }
}