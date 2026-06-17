using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.ViewModels;

/// <summary>
/// 登录页面 ViewModel
/// </summary>
public class LoginViewModel : ObservableObject
{
    private readonly IAuthService _auth;

    private string _account = string.Empty;
    private string _password = string.Empty;
    private string _phoneNumber = string.Empty;
    private bool _isLoading;
    private string? _errorMessage;
    private bool _isRegisterMode;

    // 注册字段
    private string _registerAccount = string.Empty;
    private string _registerUserName = string.Empty;
    private string _registerPassword = string.Empty;
    private string _registerConfirmPassword = string.Empty;

    public LoginViewModel(IAuthService auth)
    {
        _auth = auth;
        LoginCommand = new AsyncRelayCommand(LoginAsync, () => !IsLoading);
        RegisterCommand = new AsyncRelayCommand(RegisterAsync, () => !IsLoading);
        SwitchModeCommand = new RelayCommand(SwitchMode);
    }

    // ── 登录属性 ────────────────────────────────────────
    public string Account
    {
        get => _account;
        set { SetProperty(ref _account, value); ErrorMessage = null; }
    }

    public string Password
    {
        get => _password;
        set { SetProperty(ref _password, value); ErrorMessage = null; }
    }

    public string PhoneNumber
    {
        get => _phoneNumber;
        set => SetProperty(ref _phoneNumber, value);
    }

    public bool IsLoading
    {
        get => _isLoading;
        set { SetProperty(ref _isLoading, value); (LoginCommand as AsyncRelayCommand)?.RaiseCanExecuteChanged(); }
    }

    public string? ErrorMessage
    {
        get => _errorMessage;
        set => SetProperty(ref _errorMessage, value);
    }

    public bool IsRegisterMode
    {
        get => _isRegisterMode;
        set => SetProperty(ref _isRegisterMode, value);
    }

    // ── 注册属性 ────────────────────────────────────────
    public string RegisterAccount
    {
        get => _registerAccount;
        set => SetProperty(ref _registerAccount, value);
    }

    public string RegisterUserName
    {
        get => _registerUserName;
        set => SetProperty(ref _registerUserName, value);
    }

    public string RegisterPassword
    {
        get => _registerPassword;
        set => SetProperty(ref _registerPassword, value);
    }

    public string RegisterConfirmPassword
    {
        get => _registerConfirmPassword;
        set => SetProperty(ref _registerConfirmPassword, value);
    }

    // ── 命令 ────────────────────────────────────────────
    public AsyncRelayCommand LoginCommand { get; }
    public AsyncRelayCommand RegisterCommand { get; }
    public RelayCommand SwitchModeCommand { get; }

    // ── 事件 ────────────────────────────────────────────
    public event Action? LoginSucceeded;

    private async Task LoginAsync()
    {
        if (string.IsNullOrWhiteSpace(Account) || string.IsNullOrWhiteSpace(Password))
        {
            ErrorMessage = "请输入账号和密码";
            return;
        }

        IsLoading = true;
        ErrorMessage = null;
        try
        {
            await _auth.LoginAsync(new LoginRequest
            {
                Account = Account,
                Password = Password,
                PhoneNumber = string.IsNullOrEmpty(PhoneNumber) ? null : PhoneNumber
            });
            LoginSucceeded?.Invoke();
        }
        catch (ApiException ex)
        {
            ErrorMessage = ex.Message;
        }
        catch (Exception ex)
        {
            ErrorMessage = $"网络错误: {ex.Message}";
        }
        finally
        {
            IsLoading = false;
        }
    }

    private async Task RegisterAsync()
    {
        if (string.IsNullOrWhiteSpace(RegisterAccount) || string.IsNullOrWhiteSpace(RegisterPassword))
        {
            ErrorMessage = "请输入账号和密码";
            return;
        }

        if (RegisterPassword != RegisterConfirmPassword)
        {
            ErrorMessage = "两次输入的密码不一致";
            return;
        }

        IsLoading = true;
        ErrorMessage = null;
        try
        {
            await _auth.RegisterAsync(new RegisterRequest
            {
                Account = RegisterAccount,
                UserName = RegisterUserName,
                Password = RegisterPassword
            });
            LoginSucceeded?.Invoke();
        }
        catch (ApiException ex)
        {
            ErrorMessage = ex.Message;
        }
        catch (Exception ex)
        {
            ErrorMessage = $"网络错误: {ex.Message}";
        }
        finally
        {
            IsLoading = false;
        }
    }

    private void SwitchMode()
    {
        IsRegisterMode = !IsRegisterMode;
        ErrorMessage = null;
    }
}