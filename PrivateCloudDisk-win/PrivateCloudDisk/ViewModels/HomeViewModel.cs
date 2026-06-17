using System.Collections.ObjectModel;
using PrivateCloudDisk.Helpers;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.ViewModels;

/// <summary>
/// 首页 (文件浏览) ViewModel — 管理文件列表/文件夹导航/CRUD 操作
/// </summary>
public class HomeViewModel : ObservableObject
{
    private readonly INodeService _nodeService;
    private readonly IFileService _fileService;
    private readonly IStarService _starService;
    private readonly IUploadService _uploadService;
    private readonly IDownloadService _downloadService;

    private ObservableCollection<NodeItem> _items = new();
    private NodeItem? _selectedItem;
    private string _currentNodeId = "root";
    private List<NodeItem> _breadcrumb = new();
    private bool _isLoading;
    private bool _isGridView = false;
    private string _searchText = string.Empty;
    private int _currentPage = 1;
    private long _totalItems;
    private const int PageSize = 50;

    // 弹窗状态
    private bool _isRenameDialogOpen;
    private string _renameText = string.Empty;
    private NodeItem? _renameTarget;

    private bool _isMoveDialogOpen;
    private bool _isCreateFolderDialogOpen;
    private string _createFolderText = string.Empty;

    private bool _isUploadDialogOpen;
    private double _uploadProgress;
    private string _uploadStatus = string.Empty;
    private int _uploadTotalFiles;
    private int _uploadCompletedFiles;

    public HomeViewModel(INodeService nodeService, IFileService fileService,
        IStarService starService, IUploadService uploadService, IDownloadService downloadService)
    {
        _nodeService = nodeService;
        _fileService = fileService;
        _starService = starService;
        _uploadService = uploadService;
        _downloadService = downloadService;

        // 命令
        LoadCommand = new AsyncRelayCommand(LoadDataAsync);
        NavigateCommand = new AsyncRelayCommand<string>(NavigateToAsync);
        NavigateBreadcrumbCommand = new AsyncRelayCommand<NodeItem>(NavigateToNodeAsync);
        RefreshCommand = new AsyncRelayCommand(LoadDataAsync);
        ItemDoubleClickCommand = new AsyncRelayCommand<NodeItem>(OnItemDoubleClickAsync);

        // CRUD
        CreateFolderCommand = new AsyncRelayCommand(CreateFolderAsync);
        StartRenameCommand = new RelayCommand<NodeItem>(StartRename);
        ConfirmRenameCommand = new AsyncRelayCommand(ConfirmRenameAsync);
        CancelRenameCommand = new RelayCommand(() => IsRenameDialogOpen = false);
        DeleteCommand = new AsyncRelayCommand<NodeItem>(DeleteItemAsync);
        MoveCommand = new AsyncRelayCommand(StartMoveAsync);

        // 收藏
        ToggleStarCommand = new AsyncRelayCommand<NodeItem>(ToggleStarAsync);

        // 下载
        DownloadCommand = new AsyncRelayCommand<NodeItem>(DownloadItemAsync);

        // 上传
        OpenUploadDialogCommand = new RelayCommand(() => IsUploadDialogOpen = true);
        CloseUploadDialogCommand = new RelayCommand(() => IsUploadDialogOpen = false);
        SelectFilesCommand = new AsyncRelayCommand(SelectAndUploadFilesAsync);

        // 视图切换
        ToggleViewCommand = new RelayCommand(() => IsGridView = !IsGridView);

        // 搜索
        SearchCommand = new AsyncRelayCommand(SearchAsync);
    }

    // ── 属性 ────────────────────────────────────────────
    public ObservableCollection<NodeItem> Items
    {
        get => _items;
        set => SetProperty(ref _items, value);
    }

    public NodeItem? SelectedItem
    {
        get => _selectedItem;
        set => SetProperty(ref _selectedItem, value);
    }

    public string CurrentNodeId
    {
        get => _currentNodeId;
        set => SetProperty(ref _currentNodeId, value);
    }

    public List<NodeItem> Breadcrumb
    {
        get => _breadcrumb;
        set => SetProperty(ref _breadcrumb, value);
    }

    public bool IsLoading
    {
        get => _isLoading;
        set => SetProperty(ref _isLoading, value);
    }

    public bool IsGridView
    {
        get => _isGridView;
        set => SetProperty(ref _isGridView, value);
    }

    public string SearchText
    {
        get => _searchText;
        set => SetProperty(ref _searchText, value);
    }

    public int CurrentPage
    {
        get => _currentPage;
        set => SetProperty(ref _currentPage, value);
    }

    public long TotalItems
    {
        get => _totalItems;
        set => SetProperty(ref _totalItems, value);
    }

    // ── 重命名弹窗 ──────────────────────────────────────
    public bool IsRenameDialogOpen
    {
        get => _isRenameDialogOpen;
        set => SetProperty(ref _isRenameDialogOpen, value);
    }

    public string RenameText
    {
        get => _renameText;
        set => SetProperty(ref _renameText, value);
    }

    // ── 移动弹窗 ────────────────────────────────────────
    public bool IsMoveDialogOpen
    {
        get => _isMoveDialogOpen;
        set => SetProperty(ref _isMoveDialogOpen, value);
    }

    // ── 创建文件夹弹窗 ──────────────────────────────────
    public bool IsCreateFolderDialogOpen
    {
        get => _isCreateFolderDialogOpen;
        set => SetProperty(ref _isCreateFolderDialogOpen, value);
    }

    public string CreateFolderText
    {
        get => _createFolderText;
        set => SetProperty(ref _createFolderText, value);
    }

    // ── 上传弹窗 ────────────────────────────────────────
    public bool IsUploadDialogOpen
    {
        get => _isUploadDialogOpen;
        set => SetProperty(ref _isUploadDialogOpen, value);
    }

    public double UploadProgress
    {
        get => _uploadProgress;
        set => SetProperty(ref _uploadProgress, value);
    }

    public string UploadStatus
    {
        get => _uploadStatus;
        set => SetProperty(ref _uploadStatus, value);
    }

    public int UploadTotalFiles
    {
        get => _uploadTotalFiles;
        set => SetProperty(ref _uploadTotalFiles, value);
    }

    public int UploadCompletedFiles
    {
        get => _uploadCompletedFiles;
        set => SetProperty(ref _uploadCompletedFiles, value);
    }

    // ── 命令 ────────────────────────────────────────────
    public AsyncRelayCommand LoadCommand { get; }
    public AsyncRelayCommand<string> NavigateCommand { get; }
    public AsyncRelayCommand<NodeItem> NavigateBreadcrumbCommand { get; }
    public AsyncRelayCommand RefreshCommand { get; }
    public AsyncRelayCommand<NodeItem> ItemDoubleClickCommand { get; }
    public AsyncRelayCommand CreateFolderCommand { get; }
    public RelayCommand<NodeItem> StartRenameCommand { get; }
    public AsyncRelayCommand ConfirmRenameCommand { get; }
    public RelayCommand CancelRenameCommand { get; }
    public AsyncRelayCommand<NodeItem> DeleteCommand { get; }
    public AsyncRelayCommand MoveCommand { get; }
    public AsyncRelayCommand<NodeItem> ToggleStarCommand { get; }
    public AsyncRelayCommand<NodeItem> DownloadCommand { get; }
    public RelayCommand OpenUploadDialogCommand { get; }
    public RelayCommand CloseUploadDialogCommand { get; }
    public AsyncRelayCommand SelectFilesCommand { get; }
    public RelayCommand ToggleViewCommand { get; }
    public AsyncRelayCommand SearchCommand { get; }

    // ── 方法 ────────────────────────────────────────────

    /// <summary>加载当前目录数据</summary>
    public async Task LoadDataAsync()
    {
        IsLoading = true;
        try
        {
            // 加载文件和文件夹
            var fileTask = _fileService.GetFileListAsync(CurrentNodeId, CurrentPage, PageSize);
            var nodeTask = _nodeService.GetChildNodesAsync(CurrentNodeId, CurrentPage, PageSize);
            var breadcrumbTask = _nodeService.GetBreadcrumbAsync(CurrentNodeId);

            await Task.WhenAll(fileTask, nodeTask, breadcrumbTask);

            var allItems = new List<NodeItem>();
            allItems.AddRange(nodeTask.Result.Items);
            allItems.AddRange(fileTask.Result.Items);

            Items = new ObservableCollection<NodeItem>(allItems);
            TotalItems = fileTask.Result.Total + nodeTask.Result.Total;
            Breadcrumb = breadcrumbTask.Result;
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"加载数据失败: {ex.Message}");
        }
        finally
        {
            IsLoading = false;
        }
    }

    private async Task NavigateToAsync(string? nodeId)
    {
        if (string.IsNullOrEmpty(nodeId)) return;
        CurrentNodeId = nodeId;
        CurrentPage = 1;
        await LoadDataAsync();
    }

    private async Task NavigateToNodeAsync(NodeItem? node)
    {
        if (node == null) return;
        await NavigateToAsync(node.EffectiveId);
    }

    private async Task OnItemDoubleClickAsync(NodeItem? item)
    {
        if (item == null) return;
        if (item.IsDirectory)
        {
            await NavigateToAsync(item.EffectiveId);
        }
        else
        {
            // 打开文件详情
            // 触发导航到 FileDetailPage
            FileDetailRequested?.Invoke(item.EffectiveId);
        }
    }

    private async Task CreateFolderAsync()
    {
        if (string.IsNullOrWhiteSpace(CreateFolderText)) return;
        try
        {
            await _nodeService.CreateFolderAsync(CreateFolderText.Trim(), CurrentNodeId);
            CreateFolderText = string.Empty;
            IsCreateFolderDialogOpen = false;
            await LoadDataAsync();
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"创建文件夹失败: {ex.Message}");
        }
    }

    private void StartRename(NodeItem? item)
    {
        if (item == null) return;
        _renameTarget = item;
        RenameText = item.EffectiveName;
        IsRenameDialogOpen = true;
    }

    private async Task ConfirmRenameAsync()
    {
        if (_renameTarget == null || string.IsNullOrWhiteSpace(RenameText)) return;
        try
        {
            if (_renameTarget.IsDirectory)
                await _nodeService.RenameNodeAsync(_renameTarget.EffectiveId, RenameText.Trim());
            else
                await _fileService.RenameFileAsync(_renameTarget.EffectiveId, RenameText.Trim());

            IsRenameDialogOpen = false;
            _renameTarget = null;
            await LoadDataAsync();
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"重命名失败: {ex.Message}");
        }
    }

    private async Task DeleteItemAsync(NodeItem? item)
    {
        if (item == null) return;
        try
        {
            if (item.IsDirectory)
                await _nodeService.DeleteNodeAsync(item.EffectiveId);
            else
                await _fileService.DeleteFileAsync(item.EffectiveId);
            await LoadDataAsync();
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"删除失败: {ex.Message}");
        }
    }

    private async Task StartMoveAsync()
    {
        // 移动逻辑需要在 UI 中展示文件夹选择器
        // 此处仅打开弹窗，具体选择逻辑在 VIew 层处理
        IsMoveDialogOpen = true;
    }

    private async Task ToggleStarAsync(NodeItem? item)
    {
        if (item == null || item.IsDirectory) return;
        try
        {
            bool isStarred = await _starService.IsStarredAsync(item.EffectiveId);
            if (isStarred)
                await _starService.RemoveStarAsync(item.EffectiveId);
            else
                await _starService.AddStarAsync(item.EffectiveId);
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"收藏操作失败: {ex.Message}");
        }
    }

    private async Task DownloadItemAsync(NodeItem? item)
    {
        if (item == null || item.IsDirectory) return;
        try
        {
            var savePicker = new Windows.Storage.Pickers.FileSavePicker();
            // 初始化 FileSavePicker 需要窗口句柄
            savePicker.SuggestedFileName = item.EffectiveName;
            savePicker.FileTypeChoices.Add("All Files", new List<string> { "." });

            var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.Current.Windows.First());
            WinRT.Interop.InitializeWithWindow.Initialize(savePicker, hwnd);

            var file = await savePicker.PickSaveFileAsync();
            if (file == null) return;

            await _downloadService.DownloadFileWithTokenAsync(item.EffectiveId, file.Path);
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"下载失败: {ex.Message}");
        }
    }

    private async Task SelectAndUploadFilesAsync()
    {
        var picker = new Windows.Storage.Pickers.FileOpenPicker();
        picker.ViewMode = Windows.Storage.Pickers.PickerViewMode.List;
        picker.FileTypeFilter.Add("*");

        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(App.Current.Windows.First());
        WinRT.Interop.InitializeWithWindow.Initialize(picker, hwnd);

        var files = await picker.PickMultipleFilesAsync();
        if (files == null || files.Count == 0) return;

        UploadTotalFiles = files.Count;
        UploadCompletedFiles = 0;

        foreach (var file in files)
        {
            try
            {
                var progress = new Progress<(double percent, string status)>(p =>
                {
                    UploadProgress = p.percent;
                    UploadStatus = p.status;
                });

                await _uploadService.UploadFileAsync(file.Path, file.Name, CurrentNodeId, progress);
                UploadCompletedFiles++;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"上传失败 [{file.Name}]: {ex.Message}");
            }
        }

        await LoadDataAsync();
    }

    private async Task SearchAsync()
    {
        if (string.IsNullOrWhiteSpace(SearchText)) return;
        // 导航到搜索页
        SearchRequested?.Invoke(SearchText);
    }

    // ── 事件 ────────────────────────────────────────────
    public event Action<string>? FileDetailRequested;
    public event Action<string>? SearchRequested;
}