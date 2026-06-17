using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using PrivateCloudDisk.ViewModels;

namespace PrivateCloudDisk.Views;

public sealed partial class ProfilePage : Page
{
    public ProfileViewModel ViewModel { get; }

    public ProfilePage()
    {
        ViewModel = App.Services.GetRequiredService<ProfileViewModel>();
        InitializeComponent();
        Loaded += async (s, e) => await ViewModel.LoadCommand.ExecuteAsync(null);
    }

    private void CurPassBox_PasswordChanged(object sender, RoutedEventArgs e)
    {
        ViewModel.CurrentPassword = ((PasswordBox)sender).Password;
    }

    private void NewPassBox_PasswordChanged(object sender, RoutedEventArgs e)
    {
        ViewModel.NewPassword = ((PasswordBox)sender).Password;
    }

    private void ConfirmPassBox_PasswordChanged(object sender, RoutedEventArgs e)
    {
        ViewModel.ConfirmPassword = ((PasswordBox)sender).Password;
    }
}