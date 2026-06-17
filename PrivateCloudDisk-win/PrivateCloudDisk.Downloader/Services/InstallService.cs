using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Threading.Tasks;

namespace PrivateCloudDisk.Downloader.Services;

/// <summary>
/// 安装服务 — 负责应用安装、卸载、更新
/// </summary>
public class InstallService
{
    private readonly string _installDir;

    public InstallService()
    {
        _installDir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "PrivateCloudDisk");
    }

    public string InstallDirectory => _installDir;

    /// <summary>
    /// 安装应用
    /// </summary>
    public async Task<bool> InstallAsync(string packagePath, IProgress<string>? progress = null)
    {
        try
        {
            progress?.Report("正在准备安装...");

            // 创建安装目录
            if (!Directory.Exists(_installDir))
                Directory.CreateDirectory(_installDir);

            // 解压安装包
            progress?.Report("正在解压文件...");
            var extractDir = Path.Combine(_installDir, "app");
            if (Directory.Exists(extractDir))
                Directory.Delete(extractDir, true);

            await Task.Run(() => ZipFile.ExtractToDirectory(packagePath, extractDir));

            progress?.Report("正在注册应用...");

            // 创建快捷方式
            CreateDesktopShortcut();
            CreateStartMenuShortcut();

            // 注册卸载信息
            RegisterUninstallInfo();

            // 写入安装信息
            await WriteInstallInfoAsync();

            progress?.Report("安装完成！");

            // 启动应用
            LaunchApp();

            return true;
        }
        catch (Exception ex)
        {
            progress?.Report($"安装失败: {ex.Message}");
            return false;
        }
    }

    /// <summary>
    /// 卸载应用
    /// </summary>
    public async Task<bool> UninstallAsync()
    {
        try
        {
            // 停止运行中的进程
            var processes = Process.GetProcessesByName("PrivateCloudDisk");
            foreach (var p in processes)
            {
                p.Kill();
                p.WaitForExit(3000);
            }

            // 删除快捷方式
            DeleteShortcuts();

            // 删除安装目录
            await Task.Run(() =>
            {
                if (Directory.Exists(_installDir))
                    Directory.Delete(_installDir, true);
            });

            // 删除卸载注册表
            RemoveUninstallInfo();

            return true;
        }
        catch
        {
            return false;
        }
    }

    /// <summary>
    /// 启动主应用
    /// </summary>
    public void LaunchApp()
    {
        var appExe = Path.Combine(_installDir, "app", "PrivateCloudDisk.exe");
        if (File.Exists(appExe))
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = appExe,
                UseShellExecute = true
            });
        }
    }

    /// <summary>
    /// 检查是否已安装
    /// </summary>
    public bool IsInstalled()
    {
        return File.Exists(Path.Combine(_installDir, "app", "PrivateCloudDisk.exe"));
    }

    /// <summary>
    /// 获取已安装版本
    /// </summary>
    public string? GetInstalledVersion()
    {
        var infoFile = Path.Combine(_installDir, "install.json");
        if (File.Exists(infoFile))
        {
            var json = File.ReadAllText(infoFile);
            // 简单解析版本号
            var versionTag = "\"version\":\"";
            var idx = json.IndexOf(versionTag);
            if (idx >= 0)
            {
                idx += versionTag.Length;
                var end = json.IndexOf('"', idx);
                if (end >= 0)
                    return json.Substring(idx, end - idx);
            }
        }
        return null;
    }

    private void CreateDesktopShortcut()
    {
        // 使用 Windows Shell API 创建快捷方式
        // 实际实现需要 COM interop 引用 Windows Script Host Object Model
        try
        {
            var desktopPath = Environment.GetFolderPath(Environment.SpecialFolder.Desktop);
            var shortcutPath = Path.Combine(desktopPath, "PrivateCloudDisk.lnk");
            var targetPath = Path.Combine(_installDir, "app", "PrivateCloudDisk.exe");

            CreateShortcut(shortcutPath, targetPath, "PrivateCloudDisk - 企业私有云盘");
        }
        catch { }
    }

    private void CreateStartMenuShortcut()
    {
        try
        {
            var startMenuPath = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.Programs),
                "PrivateCloudDisk");
            if (!Directory.Exists(startMenuPath))
                Directory.CreateDirectory(startMenuPath);

            var shortcutPath = Path.Combine(startMenuPath, "PrivateCloudDisk.lnk");
            var targetPath = Path.Combine(_installDir, "app", "PrivateCloudDisk.exe");

            CreateShortcut(shortcutPath, targetPath, "PrivateCloudDisk");
            CreateShortcut(
                Path.Combine(startMenuPath, "卸载 PrivateCloudDisk.lnk"),
                Path.Combine(_installDir, "app", "PrivateCloudDisk.Uninstaller.exe"),
                "卸载 PrivateCloudDisk");
        }
        catch { }
    }

    private void DeleteShortcuts()
    {
        try
        {
            var desktopPath = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.Desktop),
                "PrivateCloudDisk.lnk");
            if (File.Exists(desktopPath)) File.Delete(desktopPath);

            var startMenuPath = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.Programs),
                "PrivateCloudDisk");
            if (Directory.Exists(startMenuPath))
                Directory.Delete(startMenuPath, true);
        }
        catch { }
    }

    private void CreateShortcut(string shortcutPath, string targetPath, string description)
    {
        // Windows Script Host COM 方式创建 .lnk 快捷方式
        var shell = new IWshRuntimeLibrary.WshShell();
        var shortcut = (IWshRuntimeLibrary.IWshShortcut)shell.CreateShortcut(shortcutPath);
        shortcut.TargetPath = targetPath;
        shortcut.Description = description;
        shortcut.WorkingDirectory = Path.GetDirectoryName(targetPath);
        shortcut.Save();
    }

    private void RegisterUninstallInfo()
    {
        // 在注册表中注册卸载信息
        // HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\PrivateCloudDisk
        try
        {
            var key = Microsoft.Win32.Registry.CurrentUser.CreateSubKey(
                @"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\PrivateCloudDisk");
            key.SetValue("DisplayName", "PrivateCloudDisk");
            key.SetValue("UninstallString",
                Path.Combine(_installDir, "app", "PrivateCloudDisk.Uninstaller.exe"));
            key.SetValue("DisplayIcon",
                Path.Combine(_installDir, "app", "PrivateCloudDisk.exe"));
            key.SetValue("Publisher", "PrivateCloudDisk");
            key.SetValue("DisplayVersion", "1.0.0");
            key.SetValue("InstallLocation", _installDir);
            key.Close();
        }
        catch { }
    }

    private void RemoveUninstallInfo()
    {
        try
        {
            Microsoft.Win32.Registry.CurrentUser.DeleteSubKeyTree(
                @"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\PrivateCloudDisk", false);
        }
        catch { }
    }

    private async Task WriteInstallInfoAsync()
    {
        var info = new
        {
            version = "1.0.0",
            installDate = DateTime.UtcNow.ToString("O"),
            installPath = _installDir
        };
        await File.WriteAllTextAsync(
            Path.Combine(_installDir, "install.json"),
            System.Text.Json.JsonSerializer.Serialize(info));
    }
}