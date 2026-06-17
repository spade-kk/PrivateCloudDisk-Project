using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using PrivateCloudDisk.Services.Implementations;
using PrivateCloudDisk.Services.Interfaces;
using PrivateCloudDisk.ViewModels;
using PrivateCloudDisk.Views;
using System;
using System.Net.Http;
using System.Threading.Tasks;
using Windows.Storage;

namespace PrivateCloudDisk;

/// <summary>
/// 应用程序入口 — 负责 DI 容器初始化、服务注册和窗口导航
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

        // ── ViewModels ──────────────────────────────────────
        services.AddTransient<LoginViewModel>();
        services.AddTransient<HomeViewModel>();
        services.AddTransient<FileDetailViewModel>();
        services.AddTransient<FavoritesViewModel>();
        services.AddTransient<TrashViewModel>();
        services.AddTransient<SearchViewModel>();
        services.AddTransient<ProfileViewModel>();
        services.AddTransient<SettingsViewModel>();
        services.AddSingleton<MainViewModel>();

        // ── Views ───────────────────────────────────────────
        services.AddTransient<LoginPage>();
        services.AddTransient<HomePage>();
        services.AddTransient<FileDetailPage>();
        services.AddTransient<FavoritesPage>();
        services.AddTransient<TrashPage>();
        services.AddTransient<SearchPage>();
        services.AddTransient<ProfilePage>();
        services.AddTransient<SettingsPage>();

        Services = services.BuildServiceProvider();
    }

    protected override async void OnLaunched(LaunchActivatedEventArgs args)
    {
        _mainWindow = new MainWindow();
        _mainWindow.Activate();

        // 异步初始化认证状态
        await Task.Run(async () =>
        {
            var authService = Services.GetRequiredService<IAuthService>();
            await authService.TryRestoreSessionAsync();
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