using System.Text.Json.Serialization;

namespace PrivateCloudDisk.Models;

/// <summary>
/// 用户信息 (对应后端 UserVO)
/// </summary>
public class UserProfile
{
    [JsonPropertyName("user_id")]
    public string UserId { get; set; } = string.Empty;

    [JsonPropertyName("account")]
    public string Account { get; set; } = string.Empty;

    [JsonPropertyName("user_name")]
    public string UserName { get; set; } = string.Empty;

    [JsonPropertyName("phone_number")]
    public string? PhoneNumber { get; set; }

    [JsonPropertyName("email")]
    public string? Email { get; set; }

    [JsonPropertyName("avatar_url")]
    public string? AvatarUrl { get; set; }

    public string DisplayName => string.IsNullOrWhiteSpace(UserName) ? Account : UserName;
}

/// <summary>
/// 登录请求 (对应后端 LoginRequest)
/// </summary>
public class LoginRequest
{
    /// <summary>账号 (必填)</summary>
    [JsonPropertyName("account")]
    public string Account { get; set; } = string.Empty;

    /// <summary>手机号 (可选)</summary>
    [JsonPropertyName("phone_number")]
    public string? PhoneNumber { get; set; }

    /// <summary>密码 (必填)</summary>
    [JsonPropertyName("password")]
    public string Password { get; set; } = string.Empty;
}

/// <summary>
/// 登录响应 (对应后端 LoginResponse)
/// </summary>
public class LoginResponse
{
    [JsonPropertyName("token")]
    public string Token { get; set; } = string.Empty;

    [JsonPropertyName("user_id")]
    public string UserId { get; set; } = string.Empty;

    [JsonPropertyName("user_name")]
    public string UserName { get; set; } = string.Empty;
}

/// <summary>
/// 注册请求 (对应后端 RegisterRequest)
/// </summary>
public class RegisterRequest
{
    [JsonPropertyName("account")]
    public string Account { get; set; } = string.Empty;

    [JsonPropertyName("user_name")]
    public string UserName { get; set; } = string.Empty;

    [JsonPropertyName("password")]
    public string Password { get; set; } = string.Empty;

    [JsonPropertyName("phone_number")]
    public string? PhoneNumber { get; set; }

    [JsonPropertyName("email")]
    public string? Email { get; set; }
}

/// <summary>
/// 更新用户信息请求 (对应后端 UpdateUserInfoRequest)
/// </summary>
public class UpdateUserInfoRequest
{
    [JsonPropertyName("new_name")]
    public string? NewName { get; set; }

    [JsonPropertyName("new_phone_number")]
    public string? NewPhoneNumber { get; set; }

    [JsonPropertyName("new_email")]
    public string? NewEmail { get; set; }
}

/// <summary>
/// 修改密码请求 (对应后端 ChangePasswordRequest)
/// </summary>
public class ChangePasswordRequest
{
    [JsonPropertyName("user_password")]
    public string UserPassword { get; set; } = string.Empty;

    [JsonPropertyName("new_password")]
    public string NewPassword { get; set; } = string.Empty;
}