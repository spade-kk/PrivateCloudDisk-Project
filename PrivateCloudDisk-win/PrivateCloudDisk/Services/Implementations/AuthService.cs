using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using PrivateCloudDisk.Models;
using Microsoft.Extensions.DependencyInjection;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// 认证服务 — 处理登录/注册/Token 管理/用户信息
/// </summary>
public class AuthService : IAuthService
{
    private readonly IHttpClientFactory _httpFactory;
    private readonly IAuthTokenStore _tokenStore;
    private readonly SemaphoreSlim _lock = new(1, 1);

    private string? _token;
    private string? _userId;
    private UserProfile? _cachedProfile;

    public bool IsAuthenticated => !string.IsNullOrEmpty(_token);
    public string? CurrentUserId => _userId;
    public string? CurrentToken => _token;

    public AuthService(IHttpClientFactory httpFactory, IAuthTokenStore tokenStore)
    {
        _httpFactory = httpFactory;
        _tokenStore = tokenStore;
    }

    // ── 登录 ────────────────────────────────────────────
    public async Task<UserProfile> LoginAsync(LoginRequest request)
    {
        var client = _httpFactory.CreateClient("PlatformService");
        var response = await client.PostAsJsonAsync("/users/login", request);
        var apiResp = await ParseResponseAsync<LoginResponse>(response);

        var loginData = apiResp.GetDataOrThrow();
        await SetSessionAsync(loginData.Token, loginData.UserId);

        _cachedProfile = new UserProfile
        {
            UserId = loginData.UserId,
            UserName = loginData.UserName,
            Account = request.Account
        };
        return _cachedProfile;
    }

    // ── 注册 ────────────────────────────────────────────
    public async Task<UserProfile> RegisterAsync(RegisterRequest request)
    {
        var client = _httpFactory.CreateClient("PlatformService");
        var response = await client.PostAsJsonAsync("/users/register", request);
        var apiResp = await ParseResponseAsync<LoginResponse>(response);

        var loginData = apiResp.GetDataOrThrow();
        await SetSessionAsync(loginData.Token, loginData.UserId);

        _cachedProfile = new UserProfile
        {
            UserId = loginData.UserId,
            UserName = request.UserName,
            Account = request.Account
        };
        return _cachedProfile;
    }

    // ── 退出 ────────────────────────────────────────────
    public async Task LogoutAsync()
    {
        _token = null;
        _userId = null;
        _cachedProfile = null;
        await _tokenStore.ClearTokenAsync();
    }

    // ── 恢复会话 ────────────────────────────────────────
    public async Task<bool> TryRestoreSessionAsync()
    {
        var stored = await _tokenStore.LoadTokenAsync();
        if (stored == null) return false;

        _token = stored.Value.token;
        _userId = stored.Value.userId;
        return true;
    }

    // ── 获取当前用户信息 ────────────────────────────────
    public async Task<UserProfile> GetCurrentUserAsync()
    {
        if (_cachedProfile != null) return _cachedProfile;

        var client = _httpFactory.CreateClient("PlatformService");
        ApplyAuthHeader(client);
        var response = await client.GetAsync($"/users/{_userId}");
        var apiResp = await ParseResponseAsync<UserProfile>(response);

        _cachedProfile = apiResp.GetDataOrThrow();
        return _cachedProfile;
    }

    // ── 更新用户信息 ────────────────────────────────────
    public async Task<UserProfile> UpdateProfileAsync(UpdateUserInfoRequest request)
    {
        var client = _httpFactory.CreateClient("PlatformService");
        ApplyAuthHeader(client);
        var content = new StringContent(
            JsonSerializer.Serialize(request), Encoding.UTF8, "application/json");
        var response = await client.PatchAsync("/users/me", content);
        var apiResp = await ParseResponseAsync<UserProfile>(response);

        _cachedProfile = apiResp.GetDataOrThrow();
        return _cachedProfile;
    }

    // ── 修改密码 ────────────────────────────────────────
    public async Task ChangePasswordAsync(ChangePasswordRequest request)
    {
        var client = _httpFactory.CreateClient("PlatformService");
        ApplyAuthHeader(client);
        var response = await client.PostAsJsonAsync("/users/me/password", request);
        await ParseResponseAsync<object>(response);
    }

    // ── 上传头像 ────────────────────────────────────────
    public async Task<string> UploadAvatarAsync(Stream fileStream, string fileName)
    {
        var client = _httpFactory.CreateClient("PlatformService");
        ApplyAuthHeader(client);
        using var content = new MultipartFormDataContent();
        var streamContent = new StreamContent(fileStream);
        streamContent.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("image/png");
        content.Add(streamContent, "avator_file", fileName);

        var response = await client.PutAsync($"/users/{_userId}/avatar", content);
        var apiResp = await ParseResponseAsync<Dictionary<string, string>>(response);
        var data = apiResp.GetDataOrThrow();
        return data.GetValueOrDefault("avatar_url", string.Empty);
    }

    // ── 内部方法 ────────────────────────────────────────
    private async Task SetSessionAsync(string token, string userId)
    {
        _token = token;
        _userId = userId;
        await _tokenStore.SaveTokenAsync(token, userId);
    }

    private void ApplyAuthHeader(HttpClient client)
    {
        if (!string.IsNullOrEmpty(_token))
            client.DefaultRequestHeaders.Authorization =
                new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", _token);
    }

    private static async Task<ApiResponse<T>> ParseResponseAsync<T>(HttpResponseMessage response)
    {
        var json = await response.Content.ReadAsStringAsync();
        var result = JsonSerializer.Deserialize<ApiResponse<T>>(json,
            new JsonSerializerOptions { PropertyNameCaseInsensitive = true });
        if (result == null)
            throw new ApiException((int)response.StatusCode, "响应解析失败");
        if (!result.IsSuccess)
            throw new ApiException(result.Code, result.Message ?? "请求失败");
        return result;
    }
}