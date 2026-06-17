using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.ViewModels;

/// <summary>
/// 个人信息页 ViewModel
/// </summary>
public class ProfileViewModel : ObservableObject
{
    private readonly IAuthService _auth;

    private UserProfile? _profile;
    private bool _isLoading;
    private bool _isEditing;
    private bool _isChangingPassword;

    // 编辑字段
    private string _editName = string.Empty;
    private string _editPhone = string.Empty;
    private string _editEmail = string.Empty;

    // 密码修改字段
    private string _currentPassword = string.Empty;
    private string _newPassword = string.Empty;
    private string _confirmPassword = string.Empty;

    private string? _avatarUrl;

    public ProfileViewModel(IAuthService auth)
    {
        _auth = auth;

        LoadCommand = new AsyncRelayCommand(LoadAsync);
        StartEditCommand = new RelayCommand(StartEdit);
        SaveCommand = new AsyncRelayCommand(SaveAsync);
        CancelEditCommand = new RelayCommand(CancelEdit);
        ChangePasswordCommand = new AsyncRelayCommand(ChangePasswordAsync);
        UploadAvatarCommand = new AsyncRelayCommand(UploadAvatarAsync);
    }

    public UserProfile? Profile
    {
        get => _profile;
        set => SetProperty(ref _profile, value);
    }

    public bool IsLoading
    {
        get => _isLoading;
        set => SetProperty(ref _isLoading, value);
    }

    public bool IsEditing
    {
        get => _isEditing;
        set => SetProperty(ref _isEditing, value);
    }

    public bool IsChangingPassword
    {
        get => _isChangingPassword;
        set => SetProperty(ref _isChangingPassword, value);
    }

    public string EditName
    {
        get => _editName;
        set => SetProperty(ref _editName, value);
    }

    public string EditPhone
    {
        get => _editPhone;
        set => SetProperty(ref _editPhone, value);
    }

    public string EditEmail
    {
        get => _editEmail;
        set => SetProperty(ref _editEmail, value);
    }

    public string CurrentPassword
    {
        get => _currentPassword;
        set => SetProperty(ref _currentPassword, value);
    }

    public string NewPassword
    {
        get => _newPassword;
        set => SetProperty(ref _newPassword, value);
    }

    public string ConfirmPassword
    {
        get => _confirmPassword;
        set => SetProperty(ref _confirmPassword, value);
    }

    public string? AvatarUrl
    {
        get => _avatarUrl;
        set => SetProperty(ref _avatarUrl, value);
    }

    public AsyncRelayCommand LoadCommand { get; }
    public RelayCommand StartEditCommand { get; }
    public AsyncRelayCommand SaveCommand { get; }
    public RelayCommand CancelEditCommand { get; }
    public AsyncRelayCommand ChangePasswordCommand { get; }
    public AsyncRelayCommand UploadAvatarCommand { get; }

    private async Task LoadAsync()
    {
        IsLoading = true;
        try
        {
            Profile = await _auth.GetCurrentUserAsync();
            AvatarUrl = Profile?.AvatarUrl;
        }
        catch { }
        finally { IsLoading = false; }
    }

    private void StartEdit()
    {
        if (Profile == null) return;
        EditName = Profile.UserName;
        EditPhone = Profile.PhoneNumber ?? string.Empty;
        EditEmail = Profile.Email ?? string.Empty;
        IsEditing = true;
    }

    private async Task SaveAsync()
    {
        try
        {
            var request = new UpdateUserInfoRequest();
            bool hasChanges = false;

            if (EditName != Profile?.UserName)
            {
                request.NewName = EditName;
                hasChanges = true;
            }
            if (EditPhone != (Profile?.PhoneNumber ?? string.Empty))
            {
                request.NewPhoneNumber = string.IsNullOrEmpty(EditPhone) ? null : EditPhone;
                hasChanges = true;
            }
            if (EditEmail != (Profile?.Email ?? string.Empty))
            {
                request.NewEmail = string.IsNullOrEmpty(EditEmail) ? null : EditEmail;
                hasChanges = true;
            }

            if (!hasChanges) { IsEditing = false; return; }

            Profile = await _auth.UpdateProfileAsync(request);
            IsEditing = false;
        }
        catch { }
    }

    private void CancelEdit()
    {
        IsEditing = false;
    }

    private async Task ChangePasswordAsync()
    {
        if (NewPassword != ConfirmPassword) return;
        try
        {
            await _auth.ChangePasswordAsync(new ChangePasswordRequest
            {
                UserPassword = CurrentPassword,
                NewPassword = NewPassword
            });
            CurrentPassword = string.Empty;
            NewPassword = string.Empty;
            ConfirmPassword = string.Empty;
            IsChangingPassword = false;
        }
        catch { }
    }

    private async Task UploadAvatarAsync()
    {
        try
        {
            var picker = new Windows.Storage.Pickers.FileOpenPicker();
            picker.ViewMode = Windows.Storage.Pickers.PickerViewMode.Thumbnail;
            picker.FileTypeFilter.Add(".png");
            picker.FileTypeFilter.Add(".jpg");
            picker.FileTypeFilter.Add(".jpeg");

            var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.Current.Windows.First());
            WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);

            var file = await picker.PickSingleFileAsync();
            if (file == null) return;

            using var stream = await file.OpenReadAsync();
            var dotNetStream = stream.AsStreamForRead();
            var url = await _auth.UploadAvatarAsync(dotNetStream, file.Name);
            AvatarUrl = url;
        }
        catch { }
    }
}