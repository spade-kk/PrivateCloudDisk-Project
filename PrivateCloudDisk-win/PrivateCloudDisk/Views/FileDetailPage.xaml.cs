using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using PrivateCloudDisk.ViewModels;

namespace PrivateCloudDisk.Views;

public sealed partial class FileDetailPage : Page
{
    public FileDetailViewModel ViewModel { get; }

    public FileDetailPage()
    {
        ViewModel = App.Services.GetRequiredService<FileDetailViewModel>();
        InitializeComponent();

        ViewModel.PropertyChanged += (s, e) =>
        {
            if (e.PropertyName == nameof(ViewModel.FileDetail))
                DetailPanel.Visibility = ViewModel.FileDetail != null
                    ? Visibility.Visible : Visibility.Collapsed;
        };
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        if (e.Parameter is string fileId)
            _ = ViewModel.LoadCommand.ExecuteAsync(fileId);
    }
}