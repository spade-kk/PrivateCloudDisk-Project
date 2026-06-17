using System.Collections.ObjectModel;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.ViewModels;

/// <summary>
/// 主 ViewModel — 管理全局导航状态 (侧边栏、当前页面、配额)
/// </summary>
public class MainViewModel : ObservableObject
{
    private readonly IAuthService _auth;
    private readonly IQuotaService _quotaService;

    private string _currentPage = "home";
    private UserProfile? _currentUser;
    private QuotaInfo? _quota;
    private bool _isSidebarCollapsed;
    private bool _isLoading;

    public MainViewModel(IAuthService auth, IQuotaService quotaService)
    {
        _auth = auth;
        _quotaService = quotaService;
        NavigateCommand = new RelayCommand<string>(Navigate, _ => true);
        ToggleSidebarCommand = new RelayCommand(ToggleSidebar);
        LogoutCommand = new AsyncRelayCommand(LogoutAsync);
    }

    // ── 属性 ────────────────────────────────────────────
    public string CurrentPage
    {
        get => _currentPage;
        set => SetProperty(ref _currentPage, value);
    }

    public UserProfile? CurrentUser
    {
        get => _currentUser;
        set => SetProperty(ref _currentUser, value);
    }

    public QuotaInfo? Quota
    {
        get => _quota;
        set => SetProperty(ref _quota, value);
    }

    public bool IsSidebarCollapsed
    {
        get => _isSidebarCollapsed;
        set => SetProperty(ref _isSidebarCollapsed, value);
    }

    public bool IsLoading
    {
        get => _isLoading;
        set => SetProperty(ref _isLoading, value);
    }

    public bool IsAuthenticated => _auth.IsAuthenticated;

    public string UserDisplayName => CurrentUser?.DisplayName ?? "未登录";

    public string QuotaDisplay => Quota != null
        ? $"{Helpers.FileSizeHelper.Format(Quota.UsedCapacity)} / {Helpers.FileSizeHelper.Format(Quota.TotalCapacity)}"
        : "加载中...";

    public double QuotaPercent => Quota?.UsagePercent ?? 0;

    // ── 命令 ────────────────────────────────────────────
    public RelayCommand<string> NavigateCommand { get; }
    public RelayCommand ToggleSidebarCommand { get; }
    public AsyncRelayCommand LogoutCommand { get; }

    private void Navigate(string? page)
    {
        if (!string.IsNullOrEmpty(page))
            CurrentPage = page;
    }

    private void ToggleSidebar()
    {
        IsSidebarCollapsed = !IsSidebarCollapsed;
    }

    private async Task LogoutAsync()
    {
        await _auth.LogoutAsync();
        CurrentPage = "login";
    }

    // ── 初始化 ──────────────────────────────────────────
    public async Task InitializeAsync()
    {
        IsLoading = true;
        try
        {
            if (_auth.IsAuthenticated)
            {
                CurrentUser = await _auth.GetCurrentUserAsync();
                Quota = await _quotaService.GetQuotaAsync();
                OnPropertyChanged(nameof(QuotaDisplay));
                OnPropertyChanged(nameof(QuotaPercent));
            }
        }
        catch { /* 静默失败 */ }
        finally
        {
            IsLoading = false;
        }
    }
}