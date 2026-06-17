// ============================================================
// CallHistoryViewModel.cs — 通话历史 ViewModel
// 管理通话记录列表、分页、过滤。
// ============================================================

using System.Collections.ObjectModel;
using Microsoft.Extensions.Logging;
using PrivateCloudDisk.Helpers;
using PrivateCloudDisk.Models;

namespace PrivateCloudDisk.ViewModels;

public class CallHistoryViewModel : ObservableObject
{
    private readonly ILogger<CallHistoryViewModel> _logger;

    private ObservableCollection<CallRecord> _records = new();
    private bool _isLoading;
    private int _currentPage = 1;
    private int _totalPages = 1;
    private string _filterType = "all";

    public ObservableCollection<CallRecord> Records
    {
        get => _records;
        set => SetProperty(ref _records, value);
    }

    public bool IsLoading
    {
        get => _isLoading;
        set => SetProperty(ref _isLoading, value);
    }

    public int CurrentPage
    {
        get => _currentPage;
        set => SetProperty(ref _currentPage, value);
    }

    public int TotalPages
    {
        get => _totalPages;
        set => SetProperty(ref _totalPages, value);
    }

    public string FilterType
    {
        get => _filterType;
        set => SetProperty(ref _filterType, value);
    }

    public RelayCommand LoadHistoryCommand { get; }
    public RelayCommand<string> FilterCommand { get; }
    public RelayCommand<string> DeleteRecordCommand { get; }

    public CallHistoryViewModel(ILogger<CallHistoryViewModel> logger)
    {
        _logger = logger;
        LoadHistoryCommand = new RelayCommand(LoadHistory);
        FilterCommand = new RelayCommand<string>(Filter);
        DeleteRecordCommand = new RelayCommand<string>(DeleteRecord);
    }

    private async void LoadHistory()
    {
        IsLoading = true;
        try
        {
            // 从后端 API 拉取通话记录
            // GET /api/v1/business/im/call-records?page=1&size=20&type=all
            await Task.CompletedTask;
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "[CallHistory] Load failed");
        }
        finally
        {
            IsLoading = false;
        }
    }

    private async void Filter(string? type)
    {
        FilterType = type ?? "all";
        CurrentPage = 1;
        await Task.CompletedTask;
    }

    private async void DeleteRecord(string? callId)
    {
        if (string.IsNullOrEmpty(callId)) return;
        try
        {
            // DELETE /api/v1/business/im/call-records/{callId}
            var record = Records.FirstOrDefault(r => r.CallId == callId);
            if (record != null)
                Records.Remove(record);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "[CallHistory] Delete failed");
        }
    }
}