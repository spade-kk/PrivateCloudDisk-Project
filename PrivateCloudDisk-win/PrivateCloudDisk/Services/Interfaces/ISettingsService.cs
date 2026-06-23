namespace PrivateCloudDisk.Services.Interfaces;

/// <summary>
/// 本地设置存储服务接口
/// </summary>
public interface ISettingsService
{
    // ── 通用方法 ────────────────────────────────────────
    T GetValue<T>(string key, T defaultValue = default!);
    void SetValue<T>(string key, T value);
    bool ContainsKey(string key);
    void Remove(string key);
    void Clear();

    // ── 简写方法（兼容旧代码） ──────────────────────────
    T Get<T>(string key, T defaultValue = default!) => GetValue(key, defaultValue);
    void Set<T>(string key, T value) => SetValue(key, value);
    void Save() { /* ApplicationData 自动保存，此处为兼容接口 */ }

    // ── 常用设置 ────────────────────────────────────────
    string? ServerAddress { get; set; }
    bool AutoStart { get; set; }
    bool MinimizeToTray { get; set; }
    string? LastDownloadPath { get; set; }
}