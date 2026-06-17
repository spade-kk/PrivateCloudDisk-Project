// ============================================================
// CallHistoryPage.xaml.cs — 通话记录页面代码后置
// ============================================================

using Microsoft.UI.Xaml.Controls;
using PrivateCloudDisk.ViewModels;

namespace PrivateCloudDisk.Views;

public sealed partial class CallHistoryPage : Page
{
    private CallHistoryViewModel ViewModel => (CallHistoryViewModel)DataContext;

    public CallHistoryPage()
    {
        InitializeComponent();
        DataContext = App.Services.GetRequiredService<CallHistoryViewModel>();
    }
}