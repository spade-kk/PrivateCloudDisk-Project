using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using PrivateCloudDisk.ViewModels;
using PrivateCloudDisk.Views;

namespace PrivateCloudDisk;

/// <summary>
/// 主窗口 — 管理侧边栏导航和页面路由
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
        ["profile"] = typeof(ProfilePage),
        ["settings"] = typeof(SettingsPage),
    };

    public MainWindow()
    {
        ViewModel = App.Services.GetRequiredService<MainViewModel>();
        InitializeComponent();
        Title = AppConfig.AppName;

        // 初始化认证状态
        Loaded += async (s, e) =>
        {
            if (ViewModel.IsAuthenticated)
            {
                await ViewModel.InitializeAsync();
                NavigateTo("home");
            }
            else
            {
                RootFrame.Navigate(typeof(LoginPage));
            }
        };

        // 监听认证状态变化
        ViewModel.PropertyChanged += (s, e) =>
        {
            if (e.PropertyName == nameof(ViewModel.CurrentPage))
            {
                if (ViewModel.CurrentPage == "login")
                {
                    RootFrame.Navigate(typeof(LoginPage));
                }
                else
                {
                    NavigateTo(ViewModel.CurrentPage);
                }
            }
        };
    }

    private void NavigateTo(string pageName)
    {
        if (_pageMap.TryGetValue(pageName, out var pageType))
            RootFrame.Navigate(pageType, null);
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