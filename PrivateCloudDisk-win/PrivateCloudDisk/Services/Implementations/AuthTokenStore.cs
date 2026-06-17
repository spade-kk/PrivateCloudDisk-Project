using System.Security.Cryptography;
using System.Text.Json;
using Windows.Storage;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// Token 持久化存储 — 使用 Windows 本地加密存储
/// </summary>
public class AuthTokenStore : IAuthTokenStore
{
    private const string TokenFileName = "auth_token.dat";
    private const string EntropyKey = "PrivateCloudDisk.Secure";

    private static readonly byte[] Entropy = SHA256.HashData(
        System.Text.Encoding.UTF8.GetBytes(EntropyKey));

    public async Task SaveTokenAsync(string token, string userId)
    {
        var payload = JsonSerializer.Serialize(new { token, userId, savedAt = DateTime.UtcNow });
        var plainBytes = System.Text.Encoding.UTF8.GetBytes(payload);
        var protectedBytes = ProtectedData.Protect(plainBytes, Entropy, DataProtectionScope.CurrentUser);

        var folder = ApplicationData.Current.LocalFolder;
        var file = await folder.CreateFileAsync(TokenFileName, CreationCollisionOption.ReplaceExisting);
        await File.WriteAllBytesAsync(file.Path, protectedBytes);
    }

    public async Task<(string token, string userId)?> LoadTokenAsync()
    {
        try
        {
            var folder = ApplicationData.Current.LocalFolder;
            var file = await folder.GetFileAsync(TokenFileName);
            var protectedBytes = await File.ReadAllBytesAsync(file.Path);

            var plainBytes = ProtectedData.Unprotect(protectedBytes, Entropy, DataProtectionScope.CurrentUser);
            var json = System.Text.Encoding.UTF8.GetString(plainBytes);
            var doc = JsonDocument.Parse(json);

            var token = doc.RootElement.GetProperty("token").GetString()!;
            var userId = doc.RootElement.GetProperty("userId").GetString()!;
            return (token, userId);
        }
        catch (FileNotFoundException)
        {
            return null;
        }
        catch
        {
            return null;
        }
    }

    public async Task ClearTokenAsync()
    {
        try
        {
            var folder = ApplicationData.Current.LocalFolder;
            var file = await folder.GetFileAsync(TokenFileName);
            await file.DeleteAsync();
        }
        catch { /* 忽略文件不存在 */ }
    }
}