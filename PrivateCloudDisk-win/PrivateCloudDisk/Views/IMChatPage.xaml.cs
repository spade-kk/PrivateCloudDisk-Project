// ============================================================
// IMChatPage.xaml.cs — IM 聊天页面代码后置
// ============================================================

using Microsoft.UI.Xaml.Controls;
using PrivateCloudDisk.ViewModels;

namespace PrivateCloudDisk.Views;

public sealed partial class IMChatPage : Page
{
    private IMChatViewModel ViewModel => (IMChatViewModel)DataContext;

    public IMChatPage()
    {
        InitializeComponent();
        DataContext = App.Services.GetRequiredService<IMChatViewModel>();
    }
}