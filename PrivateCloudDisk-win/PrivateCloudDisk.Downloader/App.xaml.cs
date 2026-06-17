using Microsoft.UI.Xaml;
using Microsoft.Extensions.DependencyInjection;
using PrivateCloudDisk.Downloader.Services;
using PrivateCloudDisk.Downloader.ViewModels;
using System;
using System.Net.Http;

namespace PrivateCloudDisk.Downloader;

public partial class App : Application
{
    public static IServiceProvider Services { get; private set; } = null!;

    private Window? _window;

    public App()
    {
        InitializeComponent();
        ConfigureServices();
    }

    private void ConfigureServices()
    {
        var services = new ServiceCollection();

        // HttpClient
        services.AddHttpClient("Downloader", client =>
        {
            client.Timeout = TimeSpan.FromMinutes(30);
            client.DefaultRequestHeaders.Add("User-Agent", "PrivateCloudDisk-Downloader/1.0");
        });

        services.AddTransient<DownloadService>();
        services.AddSingleton<InstallService>();
        services.AddSingleton<DownloaderViewModel>();

        Services = services.BuildServiceProvider();
    }

    protected override void OnLaunched(Microsoft.UI.Xaml.LaunchActivatedEventArgs args)
    {
        _window = new MainWindow();
        _window.Activate();
    }
}