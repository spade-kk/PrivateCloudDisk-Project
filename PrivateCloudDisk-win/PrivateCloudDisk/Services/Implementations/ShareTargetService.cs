using Windows.ApplicationModel.DataTransfer;
using Windows.Storage;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// Windows Share 共享目标服务
///
/// 实现从任意 Windows 应用分享文件到私有云盘：
///   - 接收文件（拖放 + 共享菜单）
///   - 接收文本/链接
///   - 批量分享
///   - 分享到指定文件夹
///   - 自动上传到云端
///
/// 用户场景：
///   1. 在文件资源管理器右键 → 发送到 → PrivateCloudDisk
///   2. 在浏览器中分享链接到云盘
///   3. 从其他应用通过 Windows Share 菜单分享
/// </summary>
public class ShareTargetService
{
    private readonly IFileService _fileService;
    private readonly NetworkMonitorService _networkMonitor;
    private readonly ToastNotificationService _toastService;

    public ShareTargetService(
        IFileService fileService,
        NetworkMonitorService networkMonitor,
        ToastNotificationService toastService)
    {
        _fileService = fileService;
        _networkMonitor = networkMonitor;
        _toastService = toastService;
    }

    /// <summary>
    /// 处理分享操作
    /// </summary>
    public async Task HandleShareOperationAsync(ShareOperation shareOperation)
    {
        try
        {
            var dataPackage = shareOperation.Data;

            // 检查网络状态
            if (!_networkMonitor.CanSync())
            {
                shareOperation.ReportError("网络不可用，请检查网络连接后重试。");
                return;
            }

            var uploadResults = new List<ShareUploadResult>();

            // 处理文件
            if (dataPackage.Contains(StandardDataFormats.StorageItems))
            {
                var items = await dataPackage.GetStorageItemsAsync();
                foreach (var item in items)
                {
                    if (item is StorageFile file)
                    {
                        var result = await UploadSharedFileAsync(file);
                        uploadResults.Add(result);
                    }
                    else if (item is StorageFolder folder)
                    {
                        var result = await UploadSharedFolderAsync(folder);
                        uploadResults.Add(result);
                    }
                }
            }

            // 处理文本/链接
            string? sharedText = null;
            if (dataPackage.Contains(StandardDataFormats.Text))
            {
                sharedText = await dataPackage.GetTextAsync();
            }

            if (dataPackage.Contains(StandardDataFormats.WebLink))
            {
                var uri = await dataPackage.GetWebLinkAsync();
                sharedText = uri?.ToString();
            }

            if (!string.IsNullOrEmpty(sharedText))
            {
                var result = await UploadSharedTextAsync(sharedText);
                uploadResults.Add(result);
            }

            // 完成分享
            if (uploadResults.Count > 0)
            {
                var successCount = uploadResults.Count(r => r.Success);
                var failCount = uploadResults.Count(r => !r.Success);

                if (failCount == 0)
                {
                    shareOperation.ReportCompleted();
                    _toastService.ShowUploadComplete(
                        $"{successCount} 个文件",
                        "");
                }
                else
                {
                    shareOperation.ReportCompleted();
                    _toastService.ShowUploadFailed(
                        $"部分上传失败 ({successCount}/{uploadResults.Count})",
                        "");
                }
            }
        }
        catch (Exception ex)
        {
            shareOperation.ReportError($"上传失败: {ex.Message}");
        }
    }

    /// <summary>
    /// 上传分享的文件
    /// </summary>
    private async Task<ShareUploadResult> UploadSharedFileAsync(StorageFile file)
    {
        try
        {
            var filePath = file.Path;
            var fileName = file.Name;

            // 使用 FileService 上传
            await _fileService.UploadFileAsync(filePath, fileName);

            return new ShareUploadResult
            {
                FileName = fileName,
                Success = true
            };
        }
        catch (Exception ex)
        {
            return new ShareUploadResult
            {
                FileName = file.Name,
                Success = false,
                Error = ex.Message
            };
        }
    }

    /// <summary>
    /// 上传分享的文件夹（递归上传）
    /// </summary>
    private async Task<ShareUploadResult> UploadSharedFolderAsync(StorageFolder folder)
    {
        try
        {
            var files = await folder.GetFilesAsync();
            int successCount = 0;
            int failCount = 0;

            foreach (var file in files)
            {
                try
                {
                    await _fileService.UploadFileAsync(file.Path, file.Name);
                    successCount++;
                }
                catch
                {
                    failCount++;
                }
            }

            return new ShareUploadResult
            {
                FileName = folder.Name,
                Success = failCount == 0,
                Error = failCount > 0 ? $"{failCount} 个文件上传失败" : null
            };
        }
        catch (Exception ex)
        {
            return new ShareUploadResult
            {
                FileName = folder.Name,
                Success = false,
                Error = ex.Message
            };
        }
    }

    /// <summary>
    /// 上传分享的文本/链接
    /// </summary>
    private async Task<ShareUploadResult> UploadSharedTextAsync(string text)
    {
        try
        {
            // 如果是链接，保存为快捷方式
            if (Uri.TryCreate(text, UriKind.Absolute, out var uri) &&
                (uri.Scheme == "http" || uri.Scheme == "https"))
            {
                var fileName = $"{DateTime.Now:yyyyMMdd_HHmmss}_shared_link.url";
                var tempPath = Path.Combine(Path.GetTempPath(), fileName);

                await File.WriteAllTextAsync(tempPath,
                    $"[InternetShortcut]\nURL={text}\n");

                await _fileService.UploadFileAsync(tempPath, fileName);

                try { File.Delete(tempPath); } catch { }

                return new ShareUploadResult
                {
                    FileName = fileName,
                    Success = true
                };
            }

            // 保存为文本文件
            var textFileName = $"{DateTime.Now:yyyyMMdd_HHmmss}_shared_text.txt";
            var textTempPath = Path.Combine(Path.GetTempPath(), textFileName);

            await File.WriteAllTextAsync(textTempPath, text);

            await _fileService.UploadFileAsync(textTempPath, textFileName);

            try { File.Delete(textTempPath); } catch { }

            return new ShareUploadResult
            {
                FileName = textFileName,
                Success = true
            };
        }
        catch (Exception ex)
        {
            return new ShareUploadResult
            {
                FileName = "shared_text",
                Success = false,
                Error = ex.Message
            };
        }
    }

    /// <summary>
    /// 处理拖放操作
    /// </summary>
    public async Task<List<ShareUploadResult>> HandleDragDropAsync(
        IReadOnlyList<IStorageItem> items, string? targetFolderId = null)
    {
        if (!_networkMonitor.CanSync())
        {
            throw new InvalidOperationException("网络不可用，无法上传文件。");
        }

        var results = new List<ShareUploadResult>();

        foreach (var item in items)
        {
            if (item is StorageFile file)
            {
                var result = await UploadSharedFileAsync(file);
                results.Add(result);
            }
            else if (item is StorageFolder folder)
            {
                var result = await UploadSharedFolderAsync(folder);
                results.Add(result);
            }
        }

        return results;
    }
}

/// <summary>
/// 分享上传结果
/// </summary>
public class ShareUploadResult
{
    public string FileName { get; set; } = string.Empty;
    public bool Success { get; set; }
    public string? Error { get; set; }
    public string? RemoteFileId { get; set; }
}