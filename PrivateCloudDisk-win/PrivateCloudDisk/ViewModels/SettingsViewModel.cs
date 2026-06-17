using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.ViewModels;

/// <summary>
/// 设置页面 ViewModel
/// </summary>
public class SettingsViewModel : ObservableObject
{
    private readonly ISettingsService _settings;

    private bool _autoStart;
    private bool _minimizeToTray;
    private string _serverAddress = string.Empty;
    private string _downloadPath = string.Empty;
    private bool _isLoading;

    public SettingsViewModel(ISettingsService settings)
    {
        _settings = settings;

        LoadCommand = new RelayCommand(LoadSettings);
        SaveCommand = new RelayCommand(SaveSettings);
        ResetCommand = new RelayCommand(ResetSettings);
        BrowseDownloadPathCommand = new AsyncRelayCommand(BrowseDownloadPathAsync);
    }

    public bool AutoStart
    {
        get => _autoStart;
        set => SetProperty(ref _autoStart, value);
    }

    public bool MinimizeToTray
    {
        get => _minimizeToTray;
        set => SetProperty(ref _minimizeToTray, value);
    }

    public string ServerAddress
    {
        get => _serverAddress;
        set => SetProperty(ref _serverAddress, value);
    }

    public string DownloadPath
    {
        get => _downloadPath;
        set => SetProperty(ref _downloadPath, value);
    }

    public bool IsLoading
    {
        get => _isLoading;
        set => SetProperty(ref _isLoading, value);
    }

    public RelayCommand LoadCommand { get; }
    public RelayCommand SaveCommand { get; }
    public RelayCommand ResetCommand { get; }
    public AsyncRelayCommand BrowseDownloadPathCommand { get; }

    private void LoadSettings()
    {
        AutoStart = _settings.AutoStart;
        MinimizeToTray = _settings.MinimizeToTray;
        ServerAddress = _settings.ServerAddress ?? AppConfig.PlatformBaseUrl;
        DownloadPath = _settings.LastDownloadPath ??
            Environment.GetFolderPath(Environment.SpecialFolder.UserProfile) + "\\Downloads";
    }

    private void SaveSettings()
    {
        _settings.AutoStart = AutoStart;
        _settings.MinimizeToTray = MinimizeToTray;
        _settings.ServerAddress = ServerAddress;
        _settings.LastDownloadPath = DownloadPath;
    }

    private void ResetSettings()
    {
        _settings.Clear();
        LoadSettings();
    }

    private async Task BrowseDownloadPathAsync()
    {
        var picker = new Windows.Storage.Pickers.FolderPicker();
        picker.SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.Downloads;
        picker.FileTypeFilter.Add("*");

        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.Current.Windows.First());
        WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);

        var folder = await picker.PickSingleFolderAsync();
        if (folder != null)
            DownloadPath = folder.Path;
    }
}