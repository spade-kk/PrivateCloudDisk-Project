using System.Security.Cryptography;
using System.Text;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// 安全服务 — 提供客户端密码哈希和传输安全
/// 
/// 安全策略：
/// 1. 密码在客户端进行 SHA-256 预哈希，结合账号作为盐值
/// 2. 后端再进行 bcrypt/argon2 二次哈希
/// 3. 密码明文永不离开客户端，不可逆地保护用户密码
/// </summary>
public static class SecurityHeaderService
{
    /// <summary>
    /// 客户端密码预哈希 — SHA-256(password + ":" + account)
    /// 目的：确保密码明文不通过网络传输，即使 TLS 层被破坏
    /// </summary>
    /// <param name="password">用户明文密码</param>
    /// <param name="account">用户账号（作为盐值的一部分）</param>
    /// <returns>Base64 编码的 SHA-256 哈希</returns>
    public static string HashPasswordForTransport(string password, string account)
    {
        if (string.IsNullOrEmpty(password))
            throw new ArgumentException("密码不能为空", nameof(password));
        if (string.IsNullOrEmpty(account))
            throw new ArgumentException("账号不能为空", nameof(account));

        var combined = $"{password}:{account}:PrivateCloudDisk.ClientHash";
        var hashBytes = SHA256.HashData(Encoding.UTF8.GetBytes(combined));
        return Convert.ToBase64String(hashBytes);
    }

    /// <summary>
    /// 生成安全随机 Token（用于 CSRF 防护等场景）
    /// </summary>
    public static string GenerateSecureToken(int length = 32)
    {
        var bytes = RandomNumberGenerator.GetBytes(length);
        return Convert.ToBase64String(bytes);
    }

    /// <summary>
    /// 生成请求签名（用于 API 请求防篡改）
    /// </summary>
    public static string SignRequest(string method, string path, string timestamp, string body, string secret)
    {
        var signData = $"{method}\n{path}\n{timestamp}\n{body}";
        using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(secret));
        var hash = hmac.ComputeHash(Encoding.UTF8.GetBytes(signData));
        return Convert.ToBase64String(hash);
    }

    /// <summary>
    /// 计算文件 SHA-256 校验和
    /// </summary>
    public static async Task<string> ComputeFileChecksumAsync(string filePath,
        CancellationToken cancellationToken = default)
    {
        using var sha256 = SHA256.Create();
        await using var stream = File.OpenRead(filePath);
        var hash = await sha256.ComputeHashAsync(stream, cancellationToken);
        return BitConverter.ToString(hash).Replace("-", "").ToLowerInvariant();
    }

    /// <summary>
    /// 计算文件 SHA-256 校验和（同步版本，用于小文件）
    /// </summary>
    public static string ComputeFileChecksum(string filePath)
    {
        using var sha256 = SHA256.Create();
        using var stream = File.OpenRead(filePath);
        var hash = sha256.ComputeHash(stream);
        return BitConverter.ToString(hash).Replace("-", "").ToLowerInvariant();
    }
}