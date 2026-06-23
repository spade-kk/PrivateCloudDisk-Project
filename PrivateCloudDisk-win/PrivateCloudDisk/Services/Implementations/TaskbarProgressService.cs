using Microsoft.UI.Xaml;
using WinRT.Interop;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// Windows 任务栏进度指示器
///
/// 使用 ITaskbarList3 COM 接口在任务栏图标上显示进度条。
/// 适用于：
///   - 批量上传/下载进度
///   - 文件夹同步进度
///   - 大文件传输进度
///
/// 进度状态：
///   NoProgress    — 无进度条
///   Indeterminate — 不确定进度（旋转动画）
///   Normal        — 正常进度（绿色）
///   Paused        — 暂停（黄色）
///   Error         — 错误（红色）
/// </summary>
public class TaskbarProgressService : IDisposable
{
    private Window? _mainWindow;
    private ITaskbarList3? _taskbarList;
    private bool _initialized;

    public TaskbarProgressService(Window mainWindow)
    {
        _mainWindow = mainWindow;
    }

    /// <summary>
    /// 延迟设置窗口引用（用于 DI 容器初始化时窗口尚未创建的场景）
    /// </summary>
    public void SetWindow(Window window)
    {
        _mainWindow = window;
    }

    private void EnsureInitialized()
    {
        if (_initialized) return;

        try
        {
            _taskbarList = (ITaskbarList3)new TaskbarList();
            _taskbarList.HrInit();
            _initialized = true;
        }
        catch
        {
            // 任务栏 API 不可用（如 Windows Server Core）
        }
    }

    /// <summary>
    /// 设置进度状态
    /// </summary>
    public void SetProgressState(TaskbarProgressState state)
    {
        EnsureInitialized();
        if (_taskbarList == null) return;

        try
        {
            var hwnd = WindowNative.GetWindowHandle(_mainWindow);
            _taskbarList.SetProgressState(hwnd, (TBPFLAG)state);
        }
        catch { }
    }

    /// <summary>
    /// 设置进度值 (0-100)
    /// </summary>
    public void SetProgressValue(ulong completed, ulong total)
    {
        EnsureInitialized();
        if (_taskbarList == null) return;

        try
        {
            var hwnd = WindowNative.GetWindowHandle(_mainWindow);
            _taskbarList.SetProgressValue(hwnd, completed, total);
        }
        catch { }
    }

    /// <summary>
    /// 显示上传进度
    /// </summary>
    public void ShowUploadProgress(long uploadedBytes, long totalBytes)
    {
        if (totalBytes <= 0)
        {
            SetProgressState(TaskbarProgressState.Indeterminate);
            return;
        }

        SetProgressState(TaskbarProgressState.Normal);
        SetProgressValue((ulong)uploadedBytes, (ulong)totalBytes);
    }

    /// <summary>
    /// 显示下载进度
    /// </summary>
    public void ShowDownloadProgress(long downloadedBytes, long totalBytes)
    {
        if (totalBytes <= 0)
        {
            SetProgressState(TaskbarProgressState.Indeterminate);
            return;
        }

        SetProgressState(TaskbarProgressState.Normal);
        SetProgressValue((ulong)downloadedBytes, (ulong)totalBytes);
    }

    /// <summary>
    /// 显示同步进度
    /// </summary>
    public void ShowSyncProgress(int syncedFiles, int totalFiles)
    {
        if (totalFiles <= 0) return;

        SetProgressState(TaskbarProgressState.Normal);
        SetProgressValue((ulong)syncedFiles, (ulong)totalFiles);
    }

    /// <summary>
    /// 暂停任务栏进度
    /// </summary>
    public void PauseProgress()
    {
        SetProgressState(TaskbarProgressState.Paused);
    }

    /// <summary>
    /// 显示错误状态
    /// </summary>
    public void ShowError()
    {
        SetProgressState(TaskbarProgressState.Error);
    }

    /// <summary>
    /// 清除进度（完成/取消）
    /// </summary>
    public void ClearProgress()
    {
        SetProgressState(TaskbarProgressState.NoProgress);
    }

    /// <summary>
    /// 显示不确定进度（旋转动画）
    /// </summary>
    public void ShowIndeterminate()
    {
        SetProgressState(TaskbarProgressState.Indeterminate);
    }

    public void Dispose()
    {
        ClearProgress();
    }
}

/// <summary>
/// 任务栏进度状态
/// </summary>
public enum TaskbarProgressState
{
    NoProgress = 0,
    Indeterminate = 1,
    Normal = 2,
    Error = 4,
    Paused = 8
}

/// <summary>
/// TBPFLAG (Taskbar Button Progress Flags)
/// </summary>
internal enum TBPFLAG
{
    TBPF_NOPROGRESS = 0,
    TBPF_INDETERMINATE = 0x1,
    TBPF_NORMAL = 0x2,
    TBPF_ERROR = 0x4,
    TBPF_PAUSED = 0x8
}

// ────────────────────────────────────────────────────────
// ITaskbarList3 COM 接口
// ────────────────────────────────────────────────────────

[System.Runtime.InteropServices.ComImport]
[System.Runtime.InteropServices.Guid("ea1afb91-9e28-4b86-90e9-9e9f8a5eefaf")]
[System.Runtime.InteropServices.InterfaceType(
    System.Runtime.InteropServices.ComInterfaceType.InterfaceIsIUnknown)]
internal interface ITaskbarList3
{
    void HrInit();
    void AddTab(IntPtr hwnd);
    void DeleteTab(IntPtr hwnd);
    void ActivateTab(IntPtr hwnd);
    void SetActiveAlt(IntPtr hwnd);

    // ITaskbarList3 methods
    [System.Runtime.InteropServices.PreserveSig]
    void SetProgressState(IntPtr hwnd, TBPFLAG tbpFlags);

    [System.Runtime.InteropServices.PreserveSig]
    void SetProgressValue(IntPtr hwnd, ulong ullCompleted, ulong ullTotal);

    [System.Runtime.InteropServices.PreserveSig]
    void SetOverlayIcon(IntPtr hwnd, IntPtr hIcon,
        [System.Runtime.InteropServices.MarshalAs(
            System.Runtime.InteropServices.UnmanagedType.LPWStr)] string pszDescription);

    [System.Runtime.InteropServices.PreserveSig]
    void SetThumbnailTooltip(IntPtr hwnd,
        [System.Runtime.InteropServices.MarshalAs(
            System.Runtime.InteropServices.UnmanagedType.LPWStr)] string pszTip);

    [System.Runtime.InteropServices.PreserveSig]
    void SetThumbnailClip(IntPtr hwnd, ref System.Drawing.Rectangle prcClip);
}

[System.Runtime.InteropServices.ComImport]
[System.Runtime.InteropServices.Guid("56fdf344-fd6d-11d0-958a-006097c9a090")]
[System.Runtime.InteropServices.ClassInterface(
    System.Runtime.InteropServices.ClassInterfaceType.None)]
internal class TaskbarList { }