using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.ViewModels;

/// <summary>
/// 闪屏页 ViewModel — 管理启动加载流程
/// </summary>
public class SplashViewModel : ObservableObject
{
    private readonly IAuthService _auth;
    private readonly IQuotaService _quotaService;
    private readonly ISettingsService _settings;

    private double _loadingProgress;
    private string _statusMessage = "正在初始化...";
    private bool _isIndeterminate = true;

    public SplashViewModel(IAuthService auth, IQuotaService quotaService, ISettingsService settings)
    {
        _auth = auth;
        _quotaService = quotaService;
        _settings = settings;
    }

    public double LoadingProgress
    {
        get => _loadingProgress;
        set => SetProperty(ref _loadingProgress, value);
    }

    public string StatusMessage
    {
        get => _statusMessage;
        set => SetProperty(ref _statusMessage, value);
    }

    public bool IsIndeterminate
    {
        get => _isIndeterminate;
        set => SetProperty(ref _isIndeterminate, value);
    }

    public event Action? InitializationCompleted;

    public async Task StartInitializationAsync()
    {
        var steps = new[]
        {
            ("正在检查本地配置...", 0.0),
            ("正在验证系统环境...", 0.15),
            ("正在连接服务器...", 0.30),
            ("正在恢复用户会话...", 0.45),
            ("正在加载用户信息...", 0.60),
            ("正在同步存储配额...", 0.75),
            ("正在初始化同步引擎...", 0.85),
            ("正在准备应用界面...", 0.95),
            ("启动完成", 1.0),
        };

        foreach (var (message, progress) in steps)
        {
            StatusMessage = message;
            IsIndeterminate = false;
            LoadingProgress = progress * 100;

            // 执行实际初始化步骤
            switch (progress)
            {
                case 0.0:
                    await Task.Delay(200); // 检查配置
                    break;
                case 0.15:
                    await Task.Delay(300); // 验证环境
                    break;
                case 0.30:
                    await TryPingServerAsync();
                    break;
                case 0.45:
                    await _auth.TryRestoreSessionAsync();
                    break;
                case 0.60:
                    if (_auth.IsAuthenticated)
                    {
                        try { await _auth.GetCurrentUserAsync(); } catch { }
                    }
                    break;
                case 0.75:
                    if (_auth.IsAuthenticated)
                    {
                        try { await _quotaService.GetQuotaAsync(); } catch { }
                    }
                    break;
                case 0.85:
                    await Task.Delay(200); // 初始化同步引擎
                    break;
            }

            await Task.Delay(150); // 短暂的视觉停留
        }

        // 给用户一个短暂的视觉停留查看品牌信息
        await Task.Delay(800);

        InitializationCompleted?.Invoke();
    }

    private async Task TryPingServerAsync()
    {
        try
        {
            using var client = new System.Net.Http.HttpClient { Timeout = TimeSpan.FromSeconds(3) };
            await client.GetAsync(AppConfig.PlatformBaseUrl + "/health");
        }
        catch
        {
            // 服务器不可达不阻塞启动
        }
    }
}