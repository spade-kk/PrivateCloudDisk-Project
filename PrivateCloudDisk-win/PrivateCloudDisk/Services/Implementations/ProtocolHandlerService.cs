using Microsoft.Win32;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// Windows 协议处理器服务
///
/// 注册自定义 URI 协议 pcd:// 实现深度链接：
///   - pcd://open/file/{fileId}     — 在应用中打开文件
///   - pcd://open/folder/{folderId}  — 打开文件夹
///   - pcd://share/{shareToken}     — 打开分享链接
///   - pcd://upload                 — 打开上传对话框
///   - pcd://settings               — 打开设置页面
///   - pcd://login?token={token}    — 自动登录（SSO）
///
/// 注册表路径：
///   HKEY_CURRENT_USER\SOFTWARE\Classes\pcd
/// </summary>
public class ProtocolHandlerService
{
    private const string ProtocolScheme = "pcd";
    private const string ProtocolName = "PrivateCloudDisk Protocol";
    private const string AppFriendlyName = "PrivateCloudDisk";

    /// <summary>
    /// 注册 pcd:// 协议处理
    /// 在应用首次启动时调用
    /// </summary>
    public static void RegisterProtocol()
    {
        try
        {
            var exePath = Environment.ProcessPath ?? "";
            var iconPath = exePath;

            // HKEY_CURRENT_USER\SOFTWARE\Classes\pcd
            using (var key = Registry.CurrentUser.CreateSubKey($@"SOFTWARE\Classes\{ProtocolScheme}"))
            {
                if (key == null) return;
                key.SetValue("", $"URL:{ProtocolName}");
                key.SetValue("URL Protocol", "");
            }

            // HKEY_CURRENT_USER\SOFTWARE\Classes\pcd\DefaultIcon
            using (var iconKey = Registry.CurrentUser.CreateSubKey(
                $@"SOFTWARE\Classes\{ProtocolScheme}\DefaultIcon"))
            {
                iconKey?.SetValue("", $"\"{iconPath}\",1");
            }

            // HKEY_CURRENT_USER\SOFTWARE\Classes\pcd\shell\open\command
            using (var cmdKey = Registry.CurrentUser.CreateSubKey(
                $@"SOFTWARE\Classes\{ProtocolScheme}\shell\open\command"))
            {
                cmdKey?.SetValue("", $"\"{exePath}\" --protocol \"%1\"");
            }

            // 注册应用能力（用于 WinUI 协议激活）
            // 注意：对于 MSIX 打包的应用，需要在 Package.appxmanifest 中声明
            // 对于非打包应用，需要在注册表注册
            using (var appKey = Registry.CurrentUser.CreateSubKey(
                $@"SOFTWARE\Classes\pcd\Application"))
            {
                appKey?.SetValue("ApplicationName", AppFriendlyName);
                appKey?.SetValue("ApplicationIcon", $"\"{iconPath}\",1");
            }
        }
        catch (UnauthorizedAccessException)
        {
            // 无管理员权限 — 注册到 HKCU 失败时静默处理
        }
    }

    /// <summary>
    /// 注销 pcd:// 协议
    /// </summary>
    public static void UnregisterProtocol()
    {
        try
        {
            Registry.CurrentUser.DeleteSubKeyTree(
                $@"SOFTWARE\Classes\{ProtocolScheme}", false);
        }
        catch { }
    }

    /// <summary>
    /// 检查协议是否已注册
    /// </summary>
    public static bool IsProtocolRegistered()
    {
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(
                $@"SOFTWARE\Classes\{ProtocolScheme}");
            return key != null;
        }
        catch
        {
            return false;
        }
    }

    /// <summary>
    /// 解析协议 URI 并返回路由信息
    /// </summary>
    public static ProtocolRoute ParseProtocolUri(string uri)
    {
        try
        {
            if (!uri.StartsWith($"{ProtocolScheme}://", StringComparison.OrdinalIgnoreCase))
                return new ProtocolRoute { Action = "unknown", RawUri = uri };

            var path = uri.Substring($"{ProtocolScheme}://".Length);
            var parts = path.Split('/', StringSplitOptions.RemoveEmptyEntries);

            var route = new ProtocolRoute { RawUri = uri };

            if (parts.Length >= 2)
            {
                route.Action = parts[0] switch
                {
                    "open" => $"open_{parts[1]}",
                    _ => parts[0]
                };
            }
            else if (parts.Length == 1)
            {
                route.Action = parts[0];
            }

            // 解析查询参数
            var queryIndex = uri.IndexOf('?');
            if (queryIndex > 0)
            {
                var query = uri.Substring(queryIndex + 1);
                var queryParams = query.Split('&');
                foreach (var param in queryParams)
                {
                    var kv = param.Split('=', 2);
                    if (kv.Length == 2)
                    {
                        route.Parameters[kv[0]] = Uri.UnescapeDataString(kv[1]);
                    }
                }
            }

            // 提取路径参数
            if (parts.Length >= 3)
            {
                route.ResourceId = parts[2];
            }

            return route;
        }
        catch
        {
            return new ProtocolRoute { Action = "error", RawUri = uri };
        }
    }

    /// <summary>
    /// 构建 pcd:// URI
    /// </summary>
    public static string BuildProtocolUri(string action, string? resourceId = null,
        Dictionary<string, string>? parameters = null)
    {
        var uri = $"{ProtocolScheme}://{action}";

        if (!string.IsNullOrEmpty(resourceId))
            uri += $"/{resourceId}";

        if (parameters != null && parameters.Count > 0)
        {
            var queryParams = parameters
                .Select(kv => $"{Uri.EscapeDataString(kv.Key)}={Uri.EscapeDataString(kv.Value)}");
            uri += "?" + string.Join("&", queryParams);
        }

        return uri;
    }
}

/// <summary>
/// 协议路由结果
/// </summary>
public class ProtocolRoute
{
    public string Action { get; set; } = "unknown";
    public string? ResourceId { get; set; }
    public Dictionary<string, string> Parameters { get; set; } = new();
    public string RawUri { get; set; } = string.Empty;
}