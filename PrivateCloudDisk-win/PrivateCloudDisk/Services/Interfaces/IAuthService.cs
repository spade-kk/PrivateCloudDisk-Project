using PrivateCloudDisk.Models;

namespace PrivateCloudDisk.Services.Interfaces;

/// <summary>
/// 认证服务接口
/// </summary>
public interface IAuthService
{
    /// <summary>当前是否已登录</summary>
    bool IsAuthenticated { get; }

    /// <summary>当前用户 ID</summary>
    string? CurrentUserId { get; }

    /// <summary>当前 Token</summary>
    string? CurrentToken { get; }

    /// <summary>用户登录</summary>
    Task<UserProfile> LoginAsync(LoginRequest request);

    /// <summary>用户注册</summary>
    Task<UserProfile> RegisterAsync(RegisterRequest request);

    /// <summary>退出登录</summary>
    Task LogoutAsync();

    /// <summary>尝试从本地存储恢复会话</summary>
    Task<bool> TryRestoreSessionAsync();

    /// <summary>获取当前用户信息</summary>
    Task<UserProfile> GetCurrentUserAsync();

    /// <summary>更新用户信息</summary>
    Task<UserProfile> UpdateProfileAsync(UpdateUserInfoRequest request);

    /// <summary>修改密码</summary>
    Task ChangePasswordAsync(ChangePasswordRequest request);

    /// <summary>上传头像</summary>
    Task<string> UploadAvatarAsync(Stream fileStream, string fileName);
}

/// <summary>
/// Token 持久化存储接口
/// </summary>
public interface IAuthTokenStore
{
    Task SaveTokenAsync(string token, string userId);
    Task<(string token, string userId)?> LoadTokenAsync();
    Task ClearTokenAsync();
}