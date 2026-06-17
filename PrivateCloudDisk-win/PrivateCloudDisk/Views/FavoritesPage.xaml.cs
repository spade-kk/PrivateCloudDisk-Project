using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using PrivateCloudDisk.ViewModels;

namespace PrivateCloudDisk.Views;

public sealed partial class FavoritesPage : Page
{
    public FavoritesViewModel ViewModel { get; }

    public FavoritesPage()
    {
        ViewModel = App.Services.GetRequiredService<FavoritesViewModel>();
        InitializeComponent();
        Loaded += async (s, e) => await ViewModel.LoadDataAsync();
    }
}