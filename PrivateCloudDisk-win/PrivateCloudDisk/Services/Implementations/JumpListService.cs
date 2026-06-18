using Windows.UI.StartScreen;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// Windows Jump List 跳转列表服务
///
/// 在任务栏右键菜单中显示：
///   - 最近访问的文件
///   - 常用文件夹
///   - 快速操作（上传、新建文件夹等）
///
/// 使用 Windows 10/11 JumpList API：
///   - JumpList.IsSupported 检查支持
///   - JumpList.LoadCurrentAsync 加载当前列表
///   - JumpListItem 创建跳转项
/// </summary>
public class JumpListService
{
    private const int MaxRecentFiles = 10;
    private const int MaxFrequentFolders = 5;

    private JumpList? _jumpList;
    private readonly List<JumpListItem> _recentFiles = new();
    private readonly List<JumpListItem> _frequentFolders = new();
    private readonly List<JumpListItem> _quickActions = new();

    /// <summary>
    /// 初始化 Jump List
    /// </summary>
    public async Task InitializeAsync()
    {
        if (!JumpList.IsSupported())
            return;

        try
        {
            _jumpList = await JumpList.LoadCurrentAsync();
            _jumpList.SystemGroupKind = JumpListSystemGroupKind.Frequent;

            // 创建快速操作组
            _quickActions.Clear();
            _quickActions.Add(JumpListItem.CreateWithArguments(
                "action=upload", "上传文件"));
            _quickActions.Add(JumpListItem.CreateWithArguments(
                "action=newFolder", "新建文件夹"));
            _quickActions.Add(JumpListItem.CreateWithArguments(
                "action=openSyncFolder", "打开同步文件夹"));
            _quickActions.Add(JumpListItem.CreateWithArguments(
                "action=openSettings", "设置"));

            _jumpList.Items.Clear();
            _jumpList.Items.AddRange(_quickActions);

            await _jumpList.SaveAsync();
        }
        catch { }
    }

    /// <summary>
    /// 添加最近访问的文件
    /// </summary>
    public async Task AddRecentFileAsync(string fileName, string fileId,
        string? filePath = null, string? groupName = "最近文件")
    {
        if (_jumpList == null) return;

        try
        {
            var item = JumpListItem.CreateWithArguments(
                $"action=openFile&fileId={fileId}",
                Path.GetFileName(fileName));

            item.Description = fileName;
            item.GroupName = groupName ?? "最近文件";

            if (!string.IsNullOrEmpty(filePath))
            {
                item.Logo = new Uri($"file://{filePath}");
            }

            _recentFiles.Insert(0, item);

            // 限制数量
            while (_recentFiles.Count > MaxRecentFiles)
                _recentFiles.RemoveAt(_recentFiles.Count - 1);

            RebuildJumpList();
            await _jumpList.SaveAsync();
        }
        catch { }
    }

    /// <summary>
    /// 添加常用文件夹
    /// </summary>
    public async Task AddFrequentFolderAsync(string folderName, string folderId)
    {
        if (_jumpList == null) return;

        try
        {
            var item = JumpListItem.CreateWithArguments(
                $"action=openFolder&folderId={folderId}",
                folderName);

            item.Description = folderName;
            item.GroupName = "常用文件夹";

            _frequentFolders.Insert(0, item);

            while (_frequentFolders.Count > MaxFrequentFolders)
                _frequentFolders.RemoveAt(_frequentFolders.Count - 1);

            RebuildJumpList();
            await _jumpList.SaveAsync();
        }
        catch { }
    }

    /// <summary>
    /// 清除最近文件
    /// </summary>
    public async Task ClearRecentFilesAsync()
    {
        _recentFiles.Clear();
        if (_jumpList != null)
        {
            RebuildJumpList();
            await _jumpList.SaveAsync();
        }
    }

    /// <summary>
    /// 重建 Jump List 项目列表
    /// </summary>
    private void RebuildJumpList()
    {
        if (_jumpList == null) return;

        _jumpList.Items.Clear();
        _jumpList.Items.AddRange(_quickActions);

        if (_recentFiles.Count > 0)
            _jumpList.Items.AddRange(_recentFiles);

        if (_frequentFolders.Count > 0)
            _jumpList.Items.AddRange(_frequentFolders);
    }
}