using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using PrivateCloudDisk.ViewModels;

namespace PrivateCloudDisk.Views;

/// <summary>
/// 登录/注册页面
/// </summary>
public sealed partial class LoginPage : Page
{
    public LoginViewModel ViewModel { get; }

    public LoginPage()
    {
        ViewModel = App.Services.GetRequiredService<LoginViewModel>();
        InitializeComponent();

        ViewModel.PropertyChanged += (s, e) =>
        {
            if (e.PropertyName == nameof(ViewModel.ErrorMessage))
            {
                if (!string.IsNullOrEmpty(ViewModel.ErrorMessage))
                {
                    ErrorBar.Message = ViewModel.ErrorMessage;
                    ErrorBar.Visibility = Visibility.Visible;
                }
                else
                {
                    ErrorBar.Visibility = Visibility.Collapsed;
                }
            }
        };

        ViewModel.LoginSucceeded += OnLoginSucceeded;
    }

    private async void OnLoginSucceeded()
    {
        DispatcherQueue.TryEnqueue(async () =>
        {
            var mainViewModel = App.Services.GetRequiredService<MainViewModel>();
            _ = mainViewModel.InitializeAsync();
            mainViewModel.CurrentPage = "home";

            // 建立 IM WebSocket 连接
            var wsService = App.Services.GetRequiredService<Services.Interfaces.IIMWebSocketService>();
            await wsService.ConnectAsync();
        });
    }

    private void PasswordBox_PasswordChanged(object sender, RoutedEventArgs e)
    {
        ViewModel.Password = ((PasswordBox)sender).Password;
    }

    private void RegPasswordBox_PasswordChanged(object sender, RoutedEventArgs e)
    {
        ViewModel.RegisterPassword = ((PasswordBox)sender).Password;
    }

    private void RegConfirmPasswordBox_PasswordChanged(object sender, RoutedEventArgs e)
    {
        ViewModel.RegisterConfirmPassword = ((PasswordBox)sender).Password;
    }
}