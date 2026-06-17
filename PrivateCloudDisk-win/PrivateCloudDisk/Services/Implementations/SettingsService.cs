using System.Text.Json;
using Windows.Storage;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// 本地设置存储 — 使用 ApplicationData.LocalSettings
/// </summary>
public class SettingsService : ISettingsService
{
    private readonly ApplicationDataContainer _settings;

    public SettingsService()
    {
        _settings = ApplicationData.Current.LocalSettings;
    }

    public T GetValue<T>(string key, T defaultValue = default!)
    {
        if (_settings.Values.TryGetValue(key, out var value))
        {
            if (value is string json && typeof(T) != typeof(string))
            {
                try { return JsonSerializer.Deserialize<T>(json) ?? defaultValue; }
                catch { return defaultValue; }
            }
            return (T)Convert.ChangeType(value, typeof(T));
        }
        return defaultValue;
    }

    public void SetValue<T>(string key, T value)
    {
        if (value is string s)
            _settings.Values[key] = s;
        else if (value is not null && value.GetType().IsValueType)
            _settings.Values[key] = value;
        else
            _settings.Values[key] = JsonSerializer.Serialize(value);
    }

    public bool ContainsKey(string key) => _settings.Values.ContainsKey(key);

    public void Remove(string key) => _settings.Values.Remove(key);

    public void Clear() => _settings.Values.Clear();

    // ── 常用设置 ────────────────────────────────────────
    public string? ServerAddress
    {
        get => GetValue<string>("ServerAddress");
        set => SetValue("ServerAddress", value!);
    }

    public bool AutoStart
    {
        get => GetValue("AutoStart", false);
        set => SetValue("AutoStart", value);
    }

    public bool MinimizeToTray
    {
        get => GetValue("MinimizeToTray", true);
        set => SetValue("MinimizeToTray", value);
    }

    public string? LastDownloadPath
    {
        get => GetValue<string>("LastDownloadPath");
        set => SetValue("LastDownloadPath", value!);
    }
}