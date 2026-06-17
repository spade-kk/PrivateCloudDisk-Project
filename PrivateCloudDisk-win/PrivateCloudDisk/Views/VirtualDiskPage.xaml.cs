using Microsoft.UI.Xaml.Controls;
using PrivateCloudDisk.ViewModels;

namespace PrivateCloudDisk.Views;

public sealed partial class VirtualDiskPage : Page
{
    public VirtualDiskViewModel ViewModel { get; }

    public VirtualDiskPage()
    {
        ViewModel = App.Services.GetRequiredService<VirtualDiskViewModel>();
        InitializeComponent();
    }
}