using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using System.Security.Cryptography;

namespace PrivateCloudDisk.Server.Controllers;

/// <summary>
/// 版本更新 API 控制器
/// POST /api/v1/version/check — 检查更新
/// </summary>
[ApiController]
[Route("api/v1/version")]
[Authorize]
public class VersionController : ControllerBase
{
    private readonly IConfiguration _config;
    private readonly ILogger<VersionController> _logger;

    public VersionController(IConfiguration config, ILogger<VersionController> logger)
    {
        _config = config;
        _logger = logger;
    }

    /// <summary>
    /// 检查更新
    /// </summary>
    [HttpPost("check")]
    public async Task<ActionResult<ApiResponse<UpdateCheckResponse>>> CheckUpdate(
        [FromBody] VersionCheckRequest request)
    {
        try
        {
            _logger.LogInformation(
                "[VersionCheck] 客户端版本检查: v{Version}, 平台: {Platform}, 渠道: {Channel}",
                request.CurrentVersion, request.Platform, request.Channel);

            // 根据平台获取最新版本
            var latestVersion = GetLatestVersion(request.Platform, request.Channel);
            if (latestVersion == null)
            {
                return Ok(ApiResponse<UpdateCheckResponse>.Success(new UpdateCheckResponse
                {
                    HasUpdate = false
                }));
            }

            // 比较版本号
            var compareResult = VersionHelper.Compare(request.CurrentVersion, latestVersion.Version);
            var hasUpdate = compareResult < 0;

            if (!hasUpdate)
            {
                return Ok(ApiResponse<UpdateCheckResponse>.Success(new UpdateCheckResponse
                {
                    HasUpdate = false
                }));
            }

            // 判断更新类型
            var updateType = DetermineUpdateType(request.CurrentVersion, latestVersion.Version);

            // 构建下载 URL
            var downloadUrl = $"{GetServerBaseUrl()}/api/v1/version/download/{latestVersion.Version}";

            var response = new UpdateCheckResponse
            {
                HasUpdate = true,
                LatestVersion = latestVersion.Version,
                UpdateType = updateType,
                ForceUpdate = IsForceUpdate(request.CurrentVersion, latestVersion.Version),
                DownloadUrl = downloadUrl,
                ReleaseNotes = latestVersion.ReleaseNotes,
                PackageSize = latestVersion.PackageSize,
                PackageHash = latestVersion.PackageHash,
                PublishedAt = latestVersion.PublishedAt
            };

            _logger.LogInformation(
                "[VersionCheck] 返回更新: v{Version}, 类型: {Type}, 强制: {Force}",
                latestVersion.Version, updateType, response.ForceUpdate);

            return Ok(ApiResponse<UpdateCheckResponse>.Success(response));
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "[VersionCheck] 版本检查异常");
            return StatusCode(500, ApiResponse<UpdateCheckResponse>.Error("版本检查服务异常"));
        }
    }

    /// <summary>
    /// 下载更新包
    /// </summary>
    [HttpGet("download/{version}")]
    [AllowAnonymous]
    public async Task<IActionResult> DownloadUpdate(string version)
    {
        var updateDir = _config.GetValue<string>("Update:PackageDirectory") ?? "updates";
        var filePath = Path.Combine(updateDir, $"PrivateCloudDisk-{version}.pcdpkg");

        if (!System.IO.File.Exists(filePath))
        {
            return NotFound(new { message = "更新包不存在" });
        }

        var fileStream = new FileStream(filePath, FileMode.Open, FileAccess.Read);
        var contentType = "application/octet-stream";

        return File(fileStream, contentType, Path.GetFileName(filePath));
    }

    // ==================== 辅助方法 ====================

    private AppVersionInfo? GetLatestVersion(string platform, string channel)
    {
        // 从配置或数据库获取最新版本信息
        var section = _config.GetSection($"Update:Versions:{platform}:{channel}");
        if (!section.Exists())
        {
            section = _config.GetSection($"Update:Versions:{platform}:stable");
        }

        if (!section.Exists()) return null;

        return new AppVersionInfo
        {
            Version = section.GetValue<string>("Version") ?? "0.0.0",
            ReleaseNotes = section.GetValue<string>("ReleaseNotes") ?? "",
            PackageSize = section.GetValue<long>("PackageSize"),
            PackageHash = section.GetValue<string>("PackageHash") ?? "",
            PublishedAt = section.GetValue<DateTime>("PublishedAt")
        };
    }

    private static string DetermineUpdateType(string currentVersion, string latestVersion)
    {
        var cur = currentVersion.Split('.').Select(int.Parse).ToArray();
        var lat = latestVersion.Split('.').Select(int.Parse).ToArray();

        if (lat[0] > cur[0]) return "major";
        if (lat[1] > cur[1]) return "minor";
        return "patch";
    }

    private static bool IsForceUpdate(string currentVersion, string latestVersion)
    {
        // 主版本号落后超过1个版本，强制更新
        var cur = currentVersion.Split('.').Select(int.Parse).ToArray();
        var lat = latestVersion.Split('.').Select(int.Parse).ToArray();

        return lat[0] - cur[0] >= 2;
    }

    private string GetServerBaseUrl()
    {
        return _config.GetValue<string>("Server:BaseUrl") ??
               $"{Request.Scheme}://{Request.Host}";
    }
}

// ==================== 请求/响应模型 ====================

/// <summary>
/// 版本检查请求
/// </summary>
public class VersionCheckRequest
{
    public string CurrentVersion { get; set; } = string.Empty;
    public string Platform { get; set; } = "windows";
    public string Arch { get; set; } = "x64";
    public string Channel { get; set; } = "stable";
}

/// <summary>
/// 版本检查响应
/// </summary>
public class UpdateCheckResponse
{
    public bool HasUpdate { get; set; }
    public string LatestVersion { get; set; } = string.Empty;
    public string UpdateType { get; set; } = "patch";
    public bool ForceUpdate { get; set; }
    public string DownloadUrl { get; set; } = string.Empty;
    public string ReleaseNotes { get; set; } = string.Empty;
    public long PackageSize { get; set; }
    public string PackageHash { get; set; } = string.Empty;
    public DateTime PublishedAt { get; set; }
}

/// <summary>
/// 应用版本信息
/// </summary>
public class AppVersionInfo
{
    public string Version { get; set; } = string.Empty;
    public string ReleaseNotes { get; set; } = string.Empty;
    public long PackageSize { get; set; }
    public string PackageHash { get; set; } = string.Empty;
    public DateTime PublishedAt { get; set; }
}