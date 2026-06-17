// ============================================================
// CallPage.xaml.cs — 视频通话页面代码后置
// ============================================================

using Microsoft.UI.Xaml.Controls;
using PrivateCloudDisk.ViewModels;

namespace PrivateCloudDisk.Views;

public sealed partial class CallPage : Page
{
    private CallViewModel ViewModel => (CallViewModel)DataContext;

    public CallPage()
    {
        InitializeComponent();
        DataContext = App.Services.GetRequiredService<CallViewModel>();
    }
}