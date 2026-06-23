using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using PrivateCloudDisk.Services.Implementations;
using PrivateCloudDisk.Services.Interfaces;
using PrivateCloudDisk.Services.VirtualDisk;
using PrivateCloudDisk.ViewModels;
using PrivateCloudDisk.Views;
using System;
using System.IO;
using System.Net.Http;
using System.Threading.Tasks;
using Windows.Storage;

namespace PrivateCloudDisk;

/// <summary>
/// 应用程序入口 — 负责 DI 容器初始化、服务注册和窗口导航
/// 
/// 启动流程:
/// 1. SplashScreen (品牌展示 + 后台初始化)
/// 2. 已登录 → MainWindow (文件浏览)
///    未登录 → LoginPage (登录/注册)
/// </summary>
public partial class App : Application
{
    private Window? _mainWindow;

    public static IServiceProvider Services { get; private set; } = null!;

    public static new App Current => (App)Application.Current;

    public App()
    {
        InitializeComponent();
        ConfigureServices();
    }

    private void ConfigureServices()
    {
        var services = new ServiceCollection();

        // ── HttpClient ──────────────────────────────────────
        services.AddHttpClient("PlatformService", client =>
        {
            client.BaseAddress = new Uri(AppConfig.PlatformBaseUrl);
            client.Timeout = TimeSpan.FromSeconds(30);
            client.DefaultRequestHeaders.Add("Accept", "application/json");
        });

        services.AddHttpClient("FileService", client =>
        {
            client.BaseAddress = new Uri(AppConfig.FileServiceBaseUrl);
            client.Timeout = TimeSpan.FromSeconds(120); // 上传下载超时更长
        });

        // ── 设置 ────────────────────────────────────────────
        services.AddSingleton<ISettingsService, SettingsService>();

        // ── 认证 ────────────────────────────────────────────
        services.AddSingleton<IAuthService, AuthService>();
        services.AddSingleton<IAuthTokenStore, AuthTokenStore>();

        // ── Windows 平台增强服务 ────────────────────────────
        // 注意: Window.Current 在构造函数中为 null，使用延迟初始化
        services.AddSingleton<ToastNotificationService>();
        services.AddSingleton<ToastNotificationActivationHandler>();
        services.AddSingleton<TaskbarProgressService>(sp =>
        {
            // 延迟获取 Window 引用，通过静态属性在运行时注入
            return new TaskbarProgressService(null!);
        });
        services.AddSingleton<JumpListService>();
        services.AddSingleton<NetworkMonitorService>();
        services.AddSingleton<SystemTrayService>(sp =>
        {
            // 延迟初始化，窗口引用在 OnLaunched 中设置
            return new SystemTrayService(
                null!,
                () => _mainWindow?.Activate(),
                () => { /* 隐藏窗口 */ },
                async () => await sp.GetRequiredService<IAuthService>().LogoutAsync());
        });
        services.AddSingleton<ShareTargetService>();
        services.AddSingleton<ThumbnailService>();
        services.AddSingleton<SearchIndexService>(sp =>
            new SearchIndexService(
                Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "PrivateCloudDisk", "Cache")));
        services.AddSingleton<CredentialManagerService>();

        // ── 业务服务 ────────────────────────────────────────
        services.AddSingleton<IFileService, FileService>();
        services.AddSingleton<INodeService, NodeService>();
        services.AddSingleton<ITrashService, TrashService>();
        services.AddSingleton<IStarService, StarService>();
        services.AddSingleton<IUploadService, UploadService>();
        services.AddSingleton<IDownloadService, DownloadService>();
        services.AddSingleton<ITaskService, TaskService>();
        services.AddSingleton<IQuotaService, QuotaService>();
        services.AddSingleton<IUserService, UserService>();

        // ── 虚拟磁盘服务 ────────────────────────────────────
        services.AddSingleton<CloudFilesSyncEngine>();
        services.AddSingleton<VirtualDiskService>();

        // ── WebRTC / IM 服务 ────────────────────────────────
        services.AddSingleton<IIMWebSocketService, IMWebSocketService>();
        services.AddSingleton<IWebRTCSignalingService, WebRTCSignalingService>();
        services.AddSingleton<IWebRTCMediaService, WebRTCMediaService>();
        services.AddSingleton<IAdaptiveEncoderService, AdaptiveEncoderService>();

        // ── ViewModels ──────────────────────────────────────
        services.AddTransient<SplashViewModel>();
        services.AddTransient<LoginViewModel>();
        services.AddTransient<HomeViewModel>();
        services.AddTransient<FileDetailViewModel>();
        services.AddTransient<FavoritesViewModel>();
        services.AddTransient<TrashViewModel>();
        services.AddTransient<SearchViewModel>();
        services.AddTransient<ProfileViewModel>();
        services.AddTransient<SettingsViewModel>();
        services.AddTransient<VirtualDiskViewModel>();
        services.AddSingleton<MainViewModel>();

        // ── WebRTC / IM ViewModels ──────────────────────────
        services.AddSingleton<CallViewModel>();
        services.AddSingleton<IMChatViewModel>();
        services.AddTransient<CallHistoryViewModel>();

        // ── Views ───────────────────────────────────────────
        services.AddTransient<SplashScreen>();
        services.AddTransient<LoginPage>();
        services.AddTransient<HomePage>();
        services.AddTransient<FileDetailPage>();
        services.AddTransient<FavoritesPage>();
        services.AddTransient<TrashPage>();
        services.AddTransient<SearchPage>();
        services.AddTransient<ProfilePage>();
        services.AddTransient<SettingsPage>();
        services.AddTransient<VirtualDiskPage>();

        // ── WebRTC / IM Views ───────────────────────────────
        services.AddTransient<CallPage>();
        services.AddTransient<IMChatPage>();
        services.AddTransient<CallHistoryPage>();

        Services = services.BuildServiceProvider();
    }

    protected override async void OnLaunched(LaunchActivatedEventArgs args)
    {
        // 创建主窗口
        _mainWindow = new MainWindow();

        // 延迟注入窗口引用到需要窗口句柄的服务
        if (_mainWindow != null)
        {
            var taskbarService = Services.GetRequiredService<TaskbarProgressService>();
            taskbarService.SetWindow(_mainWindow);

            var trayService = Services.GetRequiredService<SystemTrayService>();
            trayService.SetWindow(_mainWindow);
        }

        // 判断是否首次启动
        var settings = Services.GetRequiredService<ISettingsService>();
        var isFirstRun = !settings.Get("App.HasLaunched", false);

        if (isFirstRun)
        {
            settings.Set("App.HasLaunched", true);
            settings.Save();
        }

        _mainWindow.Activate();

        // 后台初始化认证状态
        await Task.Run(async () =>
        {
            var authService = Services.GetRequiredService<IAuthService>();
            var restored = await authService.TryRestoreSessionAsync();

            if (restored)
            {
                var wsService = Services.GetRequiredService<IIMWebSocketService>();
                await wsService.ConnectAsync();
            }
        });
    }
}

/// <summary>
/// 应用配置常量
/// </summary>
public static class AppConfig
{
    /// <summary>Spring Boot 业务服务地址</summary>
    public const string PlatformBaseUrl = "http://localhost:8080/api/v1/business";

    /// <summary>FastAPI 文件服务地址</summary>
    public const string FileServiceBaseUrl = "http://localhost:8000";

    /// <summary>分片上传每片大小 (5MB)</summary>
    public const int ChunkSize = 5 * 1024 * 1024;

    /// <summary>应用名称</summary>
    public const string AppName = "PrivateCloudDisk";

    /// <summary>应用版本</summary>
    public const string AppVersion = "1.0.0";
}