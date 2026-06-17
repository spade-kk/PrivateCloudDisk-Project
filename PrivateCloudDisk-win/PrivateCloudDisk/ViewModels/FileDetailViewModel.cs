using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.ViewModels;

/// <summary>
/// 文件详情 ViewModel
/// </summary>
public class FileDetailViewModel : ObservableObject
{
    private readonly IFileService _fileService;
    private readonly IStarService _starService;
    private readonly IDownloadService _downloadService;

    private FileDetail? _fileDetail;
    private bool _isLoading;
    private bool _isStarred;
    private string _fileId = string.Empty;

    public FileDetailViewModel(IFileService fileService, IStarService starService,
        IDownloadService downloadService)
    {
        _fileService = fileService;
        _starService = starService;
        _downloadService = downloadService;

        LoadCommand = new AsyncRelayCommand<string>(LoadAsync);
        DownloadCommand = new AsyncRelayCommand(DownloadAsync);
        ToggleStarCommand = new AsyncRelayCommand(ToggleStarAsync);
    }

    public FileDetail? FileDetail
    {
        get => _fileDetail;
        set => SetProperty(ref _fileDetail, value);
    }

    public bool IsLoading
    {
        get => _isLoading;
        set => SetProperty(ref _isLoading, value);
    }

    public bool IsStarred
    {
        get => _isStarred;
        set => SetProperty(ref _isStarred, value);
    }

    public string FileId
    {
        get => _fileId;
        set => SetProperty(ref _fileId, value);
    }

    public string FileSizeDisplay => FileDetail != null
        ? Helpers.FileSizeHelper.Format(FileDetail.FileSize)
        : "未知";

    public string UploadTimeDisplay => FileDetail?.UploadedTime != null
        ? Helpers.DateTimeHelper.FormatFull(FileDetail.UploadedTime)
        : "未知";

    public AsyncRelayCommand<string> LoadCommand { get; }
    public AsyncRelayCommand DownloadCommand { get; }
    public AsyncRelayCommand ToggleStarCommand { get; }

    private async Task LoadAsync(string? fileId)
    {
        if (string.IsNullOrEmpty(fileId)) return;
        FileId = fileId;
        IsLoading = true;
        try
        {
            FileDetail = await _fileService.GetFileDetailAsync(fileId);
            IsStarred = await _starService.IsStarredAsync(fileId);

            OnPropertyChanged(nameof(FileSizeDisplay));
            OnPropertyChanged(nameof(UploadTimeDisplay));
        }
        catch { }
        finally { IsLoading = false; }
    }

    private async Task DownloadAsync()
    {
        if (FileDetail == null) return;
        try
        {
            var savePicker = new Windows.Storage.Pickers.FileSavePicker();
            savePicker.SuggestedFileName = FileDetail.FileName;
            savePicker.FileTypeChoices.Add("All Files", new List<string> { "." });

            var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.Current.Windows.First());
            WinRT.Interop.InitializeWithWindow.Initialize(savePicker, hwnd);

            var file = await savePicker.PickSaveFileAsync();
            if (file == null) return;

            await _downloadService.DownloadFileWithTokenAsync(FileDetail.FileId, file.Path);
        }
        catch { }
    }

    private async Task ToggleStarAsync()
    {
        if (string.IsNullOrEmpty(FileId)) return;
        try
        {
            if (IsStarred)
                await _starService.RemoveStarAsync(FileId);
            else
                await _starService.AddStarAsync(FileId);
            IsStarred = !IsStarred;
        }
        catch { }
    }
}