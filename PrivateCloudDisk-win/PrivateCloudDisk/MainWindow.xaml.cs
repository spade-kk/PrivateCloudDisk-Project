using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media.Animation;
using Microsoft.UI.Xaml.Navigation;
using PrivateCloudDisk.ViewModels;
using PrivateCloudDisk.Views;
using System;

namespace PrivateCloudDisk;

/// <summary>
/// 主窗口 — 管理侧边栏导航和页面路由
/// 
/// 页面生命周期:
/// 1. SplashScreen (品牌展示 + 后台初始化)
/// 2. 已登录 → HomePage (文件浏览)
///    未登录 → LoginPage (登录/注册)
/// </summary>
public sealed partial class MainWindow : Window
{
    public MainViewModel ViewModel { get; }

    private readonly Dictionary<string, Type> _pageMap = new()
    {
        ["home"] = typeof(HomePage),
        ["favorites"] = typeof(FavoritesPage),
        ["trash"] = typeof(TrashPage),
        ["search"] = typeof(SearchPage),
        ["virtualdisk"] = typeof(VirtualDiskPage),
        ["profile"] = typeof(ProfilePage),
        ["settings"] = typeof(SettingsPage),
        ["imchat"] = typeof(IMChatPage),
        ["call"] = typeof(CallPage),
        ["callhistory"] = typeof(CallHistoryPage),
    };

    private bool _splashComplete;

    public MainWindow()
    {
        ViewModel = App.Services.GetRequiredService<MainViewModel>();
        InitializeComponent();
        Title = AppConfig.AppName;

        // 启动时先显示 SplashScreen
        Loaded += OnLoaded;
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        // 显示 SplashScreen
        var splashScreen = App.Services.GetRequiredService<SplashScreen>();
        splashScreen.InitializationCompleted += OnSplashCompleted;
        RootFrame.Navigate(splashScreen.GetType());
    }

    private void OnSplashCompleted()
    {
        _splashComplete = true;
        DispatcherQueue.TryEnqueue(() =>
        {
            // 切换到主内容
            if (ViewModel.IsAuthenticated)
            {
                _ = ViewModel.InitializeAsync();
                RootFrame.Navigate(typeof(HomePage), null, new SuppressNavigationTransitionInfo());
            }
            else
            {
                RootFrame.Navigate(typeof(LoginPage), null, new SuppressNavigationTransitionInfo());
            }
        });
    }

    private void NavigateTo(string pageName)
    {
        if (_pageMap.TryGetValue(pageName, out var pageType))
            RootFrame.Navigate(pageType, null, new EntranceNavigationTransitionInfo());
    }

    private void NavView_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        if (args.SelectedItemContainer is NavigationViewItem item && item.Tag is string tag)
        {
            ViewModel.CurrentPage = tag;
            if (tag == "home")
                ViewModel.NavigateCommand.Execute("home");
            else
                NavigateTo(tag);
        }
    }

    private void Window_SizeChanged(object sender, WindowSizeChangedEventArgs args)
    {
        // 响应式：小窗口自动折叠侧边栏
        if (args.Size.Width < 800)
            ViewModel.IsSidebarCollapsed = true;
    }

    private void UploadButton_Click(object sender, RoutedEventArgs e)
    {
        if (RootFrame.Content is HomePage homePage)
            homePage.ViewModel?.OpenUploadDialogCommand.Execute(null);
    }

    private void CreateFolderButton_Click(object sender, RoutedEventArgs e)
    {
        if (RootFrame.Content is HomePage homePage)
            homePage.ViewModel.IsCreateFolderDialogOpen = true;
    }

    private void ProfileMenuItem_Click(object sender, RoutedEventArgs e)
    {
        NavigateTo("profile");
    }

    private void SettingsMenuItem_Click(object sender, RoutedEventArgs e)
    {
        NavigateTo("settings");
    }

    private async void LogoutMenuItem_Click(object sender, RoutedEventArgs e)
    {
        await ViewModel.LogoutCommand.ExecuteAsync(null);
        RootFrame.Navigate(typeof(LoginPage));
    }
}