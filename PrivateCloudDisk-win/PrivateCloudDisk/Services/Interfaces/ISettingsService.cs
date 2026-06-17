namespace PrivateCloudDisk.Services.Interfaces;

/// <summary>
/// 本地设置存储服务接口
/// </summary>
public interface ISettingsService
{
    T GetValue<T>(string key, T defaultValue = default!);
    void SetValue<T>(string key, T value);
    bool ContainsKey(string key);
    void Remove(string key);
    void Clear();

    // ── 常用设置 ────────────────────────────────────────
    string? ServerAddress { get; set; }
    bool AutoStart { get; set; }
    bool MinimizeToTray { get; set; }
    string? LastDownloadPath { get; set; }
}