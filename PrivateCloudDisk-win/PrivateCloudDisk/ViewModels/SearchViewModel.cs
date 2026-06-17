using System.Collections.ObjectModel;
using PrivateCloudDisk.Models;
using PrivateCloudDisk.Services.Interfaces;

namespace PrivateCloudDisk.ViewModels;

/// <summary>
/// 搜索页面 ViewModel
/// </summary>
public class SearchViewModel : ObservableObject
{
    private readonly IFileService _fileService;

    private string _keyword = string.Empty;
    private string _category = string.Empty;
    private string _sortField = "_score";
    private bool _sortAsc;
    private ObservableCollection<SearchHit> _hits = new();
    private bool _isLoading;
    private bool _hasSearched;
    private long _totalHits;
    private int _currentPage = 1;
    private const int PageSize = 20;

    public SearchViewModel(IFileService fileService)
    {
        _fileService = fileService;

        SearchCommand = new AsyncRelayCommand(SearchAsync);
        NavigateDetailCommand = new RelayCommand<SearchHit>(NavigateToDetail);
    }

    public string Keyword
    {
        get => _keyword;
        set => SetProperty(ref _keyword, value);
    }

    public string Category
    {
        get => _category;
        set => SetProperty(ref _category, value);
    }

    public string SortField
    {
        get => _sortField;
        set => SetProperty(ref _sortField, value);
    }

    public bool SortAsc
    {
        get => _sortAsc;
        set => SetProperty(ref _sortAsc, value);
    }

    public ObservableCollection<SearchHit> Hits
    {
        get => _hits;
        set => SetProperty(ref _hits, value);
    }

    public bool IsLoading
    {
        get => _isLoading;
        set => SetProperty(ref _isLoading, value);
    }

    public bool HasSearched
    {
        get => _hasSearched;
        set => SetProperty(ref _hasSearched, value);
    }

    public long TotalHits
    {
        get => _totalHits;
        set => SetProperty(ref _totalHits, value);
    }

    public AsyncRelayCommand SearchCommand { get; }
    public RelayCommand<SearchHit> NavigateDetailCommand { get; }

    public event Action<string>? NavigateToFileDetail;

    public async Task SearchAsync()
    {
        if (string.IsNullOrWhiteSpace(Keyword)) return;
        IsLoading = true;
        HasSearched = true;
        try
        {
            var request = new SearchRequest
            {
                Keyword = Keyword,
                Page = CurrentPage,
                Size = PageSize,
                SortField = SortField,
                Asc = SortAsc,
                HighlightFields = new List<string> { "name", "content" }
            };

            if (!string.IsNullOrEmpty(Category))
                request.Filters = new Dictionary<string, string> { { "fileCategory", Category } };

            var result = await _fileService.AdvancedSearchAsync(request);
            Hits = new ObservableCollection<SearchHit>(result.Hits);
            TotalHits = result.Total;
        }
        catch { }
        finally { IsLoading = false; }
    }

    private void NavigateToDetail(SearchHit? hit)
    {
        var fileId = hit?.Source?.FileId ?? hit?.Id;
        if (!string.IsNullOrEmpty(fileId))
            NavigateToFileDetail?.Invoke(fileId);
    }
}