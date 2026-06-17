using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.ViewModels;

namespace PrivateCloudDisk.Views;

/// <summary>
/// 文件浏览主页
/// </summary>
public sealed partial class HomePage : Page
{
    public HomeViewModel ViewModel { get; }

    public HomePage()
    {
        ViewModel = App.Services.GetRequiredService<HomeViewModel>();
        InitializeComponent();

        Loaded += async (s, e) => await ViewModel.LoadDataAsync();

        ViewModel.PropertyChanged += (s, e) =>
        {
            switch (e.PropertyName)
            {
                case nameof(ViewModel.IsRenameDialogOpen):
                    if (ViewModel.IsRenameDialogOpen)
                        _ = DispatcherQueue.TryEnqueue(() => RenameDialog.ShowAsync());
                    else
                        _ = DispatcherQueue.TryEnqueue(() => RenameDialog.Hide());
                    break;
                case nameof(ViewModel.IsCreateFolderDialogOpen):
                    if (ViewModel.IsCreateFolderDialogOpen)
                        _ = DispatcherQueue.TryEnqueue(() => CreateFolderDialog.ShowAsync());
                    else
                        _ = DispatcherQueue.TryEnqueue(() => CreateFolderDialog.Hide());
                    break;
                case nameof(ViewModel.IsUploadDialogOpen):
                    if (ViewModel.IsUploadDialogOpen)
                        _ = DispatcherQueue.TryEnqueue(() => UploadDialog.ShowAsync());
                    else
                        _ = DispatcherQueue.TryEnqueue(() => UploadDialog.Hide());
                    break;
            }
        };
    }

    private void FileList_DoubleTapped(object sender, DoubleTappedRoutedEventArgs e)
    {
        if (ViewModel.SelectedItem != null)
            ViewModel.ItemDoubleClickCommand.Execute(ViewModel.SelectedItem);
    }

    private void FileList_RightTapped(object sender, RightTappedRoutedEventArgs e)
    {
        // 右键菜单
        var flyout = new MenuFlyout();

        if (ViewModel.SelectedItem != null)
        {
            if (!ViewModel.SelectedItem.IsDirectory)
            {
                flyout.Items.Add(new MenuFlyoutItem
                {
                    Text = "下载",
                    Icon = new FontIcon { Glyph = "\uE90F" },
                    Command = ViewModel.DownloadCommand,
                    CommandParameter = ViewModel.SelectedItem
                });
            }

            flyout.Items.Add(new MenuFlyoutItem
            {
                Text = "重命名",
                Icon = new FontIcon { Glyph = "\uE8C8" },
                Command = ViewModel.StartRenameCommand,
                CommandParameter = ViewModel.SelectedItem
            });

            flyout.Items.Add(new MenuFlyoutSeparator());

            flyout.Items.Add(new MenuFlyoutItem
            {
                Text = "删除",
                Icon = new FontIcon { Glyph = "\uE74D" },
                Command = ViewModel.DeleteCommand,
                CommandParameter = ViewModel.SelectedItem,
                Foreground = new SolidColorBrush(Windows.UI.Color.FromArgb(255, 234, 67, 53))
            });
        }

        flyout.ShowAt(sender as FrameworkElement, e.GetPosition(sender as FrameworkElement));
    }

    private void SearchBox_QuerySubmitted(AutoSuggestBox sender, AutoSuggestBoxQuerySubmittedEventArgs args)
    {
        ViewModel.SearchCommand.Execute(null);
    }

    private void DownloadButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is NodeItem item)
            ViewModel.DownloadCommand.Execute(item);
    }

    private void RenameButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is NodeItem item)
            ViewModel.StartRenameCommand.Execute(item);
    }

    private void DeleteButton_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button btn && btn.Tag is NodeItem item)
            ViewModel.DeleteCommand.Execute(item);
    }
}