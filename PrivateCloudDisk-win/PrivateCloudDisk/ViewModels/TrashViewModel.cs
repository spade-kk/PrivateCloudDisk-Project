using System.Collections.ObjectModel;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.ViewModels;

/// <summary>
/// 回收站 ViewModel
/// </summary>
public class TrashViewModel : ObservableObject
{
    private readonly ITrashService _trashService;

    private ObservableCollection<TrashItem> _items = new();
    private bool _isLoading;
    private int _currentPage = 1;
    private long _totalItems;
    private const int PageSize = 50;

    public TrashViewModel(ITrashService trashService)
    {
        _trashService = trashService;

        LoadCommand = new AsyncRelayCommand(LoadDataAsync);
        RestoreCommand = new AsyncRelayCommand<TrashItem>(RestoreAsync);
        PermanentDeleteCommand = new AsyncRelayCommand<TrashItem>(PermanentDeleteAsync);
        EmptyTrashCommand = new AsyncRelayCommand(EmptyTrashAsync);
    }

    public ObservableCollection<TrashItem> Items
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
    public AsyncRelayCommand<TrashItem> RestoreCommand { get; }
    public AsyncRelayCommand<TrashItem> PermanentDeleteCommand { get; }
    public AsyncRelayCommand EmptyTrashCommand { get; }

    public async Task LoadDataAsync()
    {
        IsLoading = true;
        try
        {
            var result = await _trashService.GetTrashListAsync(CurrentPage, PageSize);
            Items = new ObservableCollection<TrashItem>(result.Items);
            TotalItems = result.Total;
        }
        catch { }
        finally { IsLoading = false; }
    }

    private async Task RestoreAsync(TrashItem? item)
    {
        if (item == null) return;
        try
        {
            await _trashService.RestoreAsync(item.TrashId);
            Items.Remove(item);
            TotalItems--;
        }
        catch { }
    }

    private async Task PermanentDeleteAsync(TrashItem? item)
    {
        if (item == null) return;
        try
        {
            await _trashService.PermanentDeleteAsync(item.TrashId);
            Items.Remove(item);
            TotalItems--;
        }
        catch { }
    }

    private async Task EmptyTrashAsync()
    {
        try
        {
            await _trashService.EmptyTrashAsync();
            Items.Clear();
            TotalItems = 0;
        }
        catch { }
    }
}