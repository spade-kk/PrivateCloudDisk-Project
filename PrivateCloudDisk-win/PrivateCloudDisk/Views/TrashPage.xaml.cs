using Microsoft.UI.Xaml.Controls;
using PrivateCloudDisk.ViewModels;

namespace PrivateCloudDisk.Views;

public sealed partial class TrashPage : Page
{
    public TrashViewModel ViewModel { get; }

    public TrashPage()
    {
        ViewModel = App.Services.GetRequiredService<TrashViewModel>();
        InitializeComponent();
        Loaded += async (s, e) => await ViewModel.LoadDataAsync();
    }
}