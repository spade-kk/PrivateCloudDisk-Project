using Microsoft.UI.Xaml;
using Microsoft.UI.Windowing;
using System.Runtime.InteropServices;
using WinRT.Interop;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// Windows 系统托盘 / 后台服务
///
/// 功能：
///   - 最小化到系统托盘而非关闭
///   - 托盘图标右键菜单（快速操作）
///   - 托盘图标悬浮提示（同步状态）
///   - 开机自启注册表管理
///   - 后台同步管家
///
/// 使用 WinUI 3 + Windows App SDK：
///   - NotifyIcon (CommunityToolkit) 或手动 P/Invoke
///   - AppWindow 管理
/// </summary>
public class SystemTrayService : IDisposable
{
    private readonly Window _mainWindow;
    private readonly Action _showWindow;
    private readonly Action _hideWindow;
    private readonly Func<Task> _onExit;

    private bool _minimizeToTray = true;
    private bool _closeToTray = true;
    private bool _startWithWindows;

    private IntPtr _trayIconHandle;
    private uint _trayCallbackMessage;

    // ── 构造函数 ──────────────────────────────────────────

    public SystemTrayService(
        Window mainWindow,
        Action showWindow,
        Action hideWindow,
        Func<Task> onExit)
    {
        _mainWindow = mainWindow;
        _showWindow = showWindow;
        _hideWindow = hideWindow;
        _onExit = onExit;
    }

    // ── 属性 ──────────────────────────────────────────────

    public bool MinimizeToTray
    {
        get => _minimizeToTray;
        set => _minimizeToTray = value;
    }

    public bool CloseToTray
    {
        get => _closeToTray;
        set => _closeToTray = value;
    }

    public bool StartWithWindows
    {
        get => _startWithWindows;
        set
        {
            _startWithWindows = value;
            SetAutoStart(value);
        }
    }

    // ── 窗口管理 ──────────────────────────────────────────

    /// <summary>
    /// 显示主窗口
    /// </summary>
    public void ShowMainWindow()
    {
        _showWindow();
        var hwnd = WindowNative.GetWindowHandle(_mainWindow);
        TrayNativeMethods.SetForegroundWindow(hwnd);
        TrayNativeMethods.ShowWindow(hwnd, TrayNativeMethods.SW_RESTORE);
    }

    /// <summary>
    /// 隐藏到托盘
    /// </summary>
    public void HideToTray()
    {
        _hideWindow();
    }

    /// <summary>
    /// 处理窗口关闭 — 如果启用关闭到托盘，则隐藏而非退出
    /// </summary>
    public bool HandleClosing()
    {
        if (_closeToTray)
        {
            HideToTray();
            return false; // 阻止关闭
        }

        return true; // 允许关闭
    }

    // ── 托盘图标 ──────────────────────────────────────────

    /// <summary>
    /// 创建系统托盘图标
    /// </summary>
    public void CreateTrayIcon()
    {
        try
        {
            var hwnd = WindowNative.GetWindowHandle(_mainWindow);
            _trayCallbackMessage = TrayNativeMethods.RegisterWindowMessage("PrivateCloudDiskTrayCallback");

            var nid = new TrayNativeMethods.NOTIFYICONDATA
            {
                cbSize = (uint)Marshal.SizeOf<TrayNativeMethods.NOTIFYICONDATA>(),
                hWnd = hwnd,
                uID = 1,
                uFlags = TrayNativeMethods.NIF_MESSAGE | TrayNativeMethods.NIF_ICON |
                         TrayNativeMethods.NIF_TIP | TrayNativeMethods.NIF_SHOWTIP,
                uCallbackMessage = _trayCallbackMessage,
                hIcon = LoadTrayIcon(),
                szTip = "PrivateCloudDisk — 已就绪"
            };

            TrayNativeMethods.Shell_NotifyIcon(TrayNativeMethods.NIM_ADD, ref nid);
            TrayNativeMethods.Shell_NotifyIcon(TrayNativeMethods.NIM_SETVERSION, ref nid);
        }
        catch { }
    }

    /// <summary>
    /// 更新托盘图标提示文本
    /// </summary>
    public void UpdateTrayTooltip(string status)
    {
        try
        {
            var hwnd = WindowNative.GetWindowHandle(_mainWindow);
            var nid = new TrayNativeMethods.NOTIFYICONDATA
            {
                cbSize = (uint)Marshal.SizeOf<TrayNativeMethods.NOTIFYICONDATA>(),
                hWnd = hwnd,
                uID = 1,
                uFlags = TrayNativeMethods.NIF_TIP,
                szTip = $"PrivateCloudDisk — {status}"
            };

            TrayNativeMethods.Shell_NotifyIcon(TrayNativeMethods.NIM_MODIFY, ref nid);
        }
        catch { }
    }

    /// <summary>
    /// 移除托盘图标
    /// </summary>
    public void RemoveTrayIcon()
    {
        try
        {
            var hwnd = WindowNative.GetWindowHandle(_mainWindow);
            var nid = new TrayNativeMethods.NOTIFYICONDATA
            {
                cbSize = (uint)Marshal.SizeOf<TrayNativeMethods.NOTIFYICONDATA>(),
                hWnd = hwnd,
                uID = 1
            };

            TrayNativeMethods.Shell_NotifyIcon(TrayNativeMethods.NIM_DELETE, ref nid);
        }
        catch { }
    }

    /// <summary>
    /// 显示托盘气泡提示
    /// </summary>
    public void ShowTrayBalloon(string title, string message, int timeoutMs = 5000)
    {
        try
        {
            var hwnd = WindowNative.GetWindowHandle(_mainWindow);
            var nid = new TrayNativeMethods.NOTIFYICONDATA
            {
                cbSize = (uint)Marshal.SizeOf<TrayNativeMethods.NOTIFYICONDATA>(),
                hWnd = hwnd,
                uID = 1,
                uFlags = TrayNativeMethods.NIF_INFO,
                szInfoTitle = title,
                szInfo = message,
                uTimeout = (uint)timeoutMs,
                dwInfoFlags = TrayNativeMethods.NIIF_INFO
            };

            TrayNativeMethods.Shell_NotifyIcon(TrayNativeMethods.NIM_MODIFY, ref nid);
        }
        catch { }
    }

    private static IntPtr LoadTrayIcon()
    {
        try
        {
            // 从资源加载图标
            return TrayNativeMethods.LoadIcon(IntPtr.Zero, TrayNativeMethods.IDI_APPLICATION);
        }
        catch
        {
            return IntPtr.Zero;
        }
    }

    // ── 开机自启 ──────────────────────────────────────────

    private static void SetAutoStart(bool enable)
    {
        try
        {
            var key = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(
                @"SOFTWARE\Microsoft\Windows\CurrentVersion\Run", true);

            if (key == null) return;

            if (enable)
            {
                var exePath = Environment.ProcessPath ?? "";
                key.SetValue("PrivateCloudDisk", $"\"{exePath}\" --autostart");
            }
            else
            {
                key.DeleteValue("PrivateCloudDisk", false);
            }

            key.Close();
        }
        catch { }
    }

    /// <summary>
    /// 检查是否已设置为开机自启
    /// </summary>
    public static bool IsAutoStartEnabled()
    {
        try
        {
            var key = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(
                @"SOFTWARE\Microsoft\Windows\CurrentVersion\Run");

            if (key == null) return false;

            var value = key.GetValue("PrivateCloudDisk");
            key.Close();
            return value != null;
        }
        catch
        {
            return false;
        }
    }

    public void Dispose()
    {
        RemoveTrayIcon();
    }
}

// ────────────────────────────────────────────────────────
// Shell Tray Icon P/Invoke
// ────────────────────────────────────────────────────────

internal static class TrayNativeMethods
{
    public const int NIM_ADD = 0;
    public const int NIM_MODIFY = 1;
    public const int NIM_DELETE = 2;
    public const int NIM_SETVERSION = 4;

    public const int NIF_MESSAGE = 1;
    public const int NIF_ICON = 2;
    public const int NIF_TIP = 4;
    public const int NIF_INFO = 0x10;
    public const int NIF_SHOWTIP = 0x80;

    public const int NIIF_INFO = 1;
    public const int NIIF_WARNING = 2;
    public const int NIIF_ERROR = 3;

    public const int SW_RESTORE = 9;
    public const int SW_SHOW = 5;
    public const int SW_HIDE = 0;

    public const int IDI_APPLICATION = 32512;
    public const int WM_LBUTTONDOWN = 0x0201;
    public const int WM_RBUTTONUP = 0x0205;

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct NOTIFYICONDATA
    {
        public uint cbSize;
        public IntPtr hWnd;
        public uint uID;
        public uint uFlags;
        public uint uCallbackMessage;
        public IntPtr hIcon;

        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)]
        public string szTip;

        public uint dwState;
        public uint dwStateMask;

        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)]
        public string szInfo;

        public uint uTimeoutOrVersion;

        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 64)]
        public string szInfoTitle;

        public uint dwInfoFlags;
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool Shell_NotifyIcon(uint dwMessage, ref NOTIFYICONDATA lpData);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern IntPtr LoadIcon(IntPtr hInstance, IntPtr lpIconName);

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern uint RegisterWindowMessage(string lpString);
}