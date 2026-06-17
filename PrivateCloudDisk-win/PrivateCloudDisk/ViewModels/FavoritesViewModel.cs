using System.Collections.ObjectModel;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.ViewModels;

/// <summary>
/// 收藏夹 ViewModel
/// </summary>
public class FavoritesViewModel : ObservableObject
{
    private readonly IStarService _starService;
    private readonly IFileService _fileService;

    private ObservableCollection<StarItem> _items = new();
    private bool _isLoading;
    private int _currentPage = 1;
    private long _totalItems;
    private const int PageSize = 50;

    public FavoritesViewModel(IStarService starService, IFileService fileService)
    {
        _starService = starService;
        _fileService = fileService;

        LoadCommand = new AsyncRelayCommand(LoadDataAsync);
        RemoveStarCommand = new AsyncRelayCommand<StarItem>(RemoveStarAsync);
        NavigateDetailCommand = new RelayCommand<StarItem>(NavigateDetail);
    }

    public ObservableCollection<StarItem> Items
    {
        get => _items;
        set => SetProperty(ref _items, value);
    }

    public bool IsLoading
    {
        get => _isLoading;
        set => SetProperty(ref _isLoading, value);
    }

    public long TotalItems
    {
        get => _totalItems;
        set => SetProperty(ref _totalItems, value);
    }

    public AsyncRelayCommand LoadCommand { get; }
    public AsyncRelayCommand<StarItem> RemoveStarCommand { get; }
    public RelayCommand<StarItem> NavigateDetailCommand { get; }

    public event Action<string>? NavigateToFileDetail;

    public async Task LoadDataAsync()
    {
        IsLoading = true;
        try
        {
            var result = await _starService.GetStarListAsync(CurrentPage, PageSize);
            // 为每个收藏项获取文件详情
            foreach (var star in result.Items)
            {
                try
                {
                    var detail = await _fileService.GetFileDetailAsync(star.FileId);
                    star.FileName = detail.FileName;
                    star.FileSize = detail.FileSize;
                    star.FileType = detail.FileType;
                }
                catch { /* 忽略获取失败的项 */ }
            }
            Items = new ObservableCollection<StarItem>(result.Items);
            TotalItems = result.Total;
        }
        catch { }
        finally { IsLoading = false; }
    }

    private async Task RemoveStarAsync(StarItem? item)
    {
        if (item == null) return;
        try
        {
            await _starService.RemoveStarAsync(item.FileId);
            Items.Remove(item);
            TotalItems--;
        }
        catch { }
    }

    private void NavigateDetail(StarItem? item)
    {
        if (item != null)
            NavigateToFileDetail?.Invoke(item.FileId);
    }
}