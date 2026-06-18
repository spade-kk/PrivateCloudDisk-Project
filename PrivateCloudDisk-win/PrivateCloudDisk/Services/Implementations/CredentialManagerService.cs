using System.Runtime.InteropServices;
using System.Text;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// Windows Credential Manager 集成服务
///
/// 使用 Windows 原生凭据管理器安全存储敏感数据（Token、密码等）。
/// 比 DPAPI 更适合凭据存储，因为：
///   1. 凭据与用户帐户绑定，支持漫游（通过 Active Directory）
///   2. 凭据在锁屏后被加密保护
///   3. 支持凭据备份和还原
///   4. 凭据管理器 UI 可查看/管理存储的凭据
///   5. 支持持久化（Persist=LocalMachine）或会话级（Persist=Session）
///
/// 安全特性：
///   - 凭据数据使用 AES-256 加密
///   - 绑定到当前用户会话
///   - 支持凭据类型区分（Generic / DomainPassword）
/// </summary>
public class CredentialManagerService : IDisposable
{
    private const string CredentialTargetPrefix = "PrivateCloudDisk";
    private const string TokenCredentialName = "AuthToken";
    private const string RefreshTokenCredentialName = "RefreshToken";
    private const string UserIdCredentialName = "UserId";

    // ── 凭据 CRUD ─────────────────────────────────────────

    /// <summary>
    /// 存储凭据到 Windows 凭据管理器
    /// </summary>
    /// <param name="name">凭据名称</param>
    /// <param name="value">凭据值（敏感数据）</param>
    /// <param name="persistence">持久化类型</param>
    public static void StoreCredential(string name, string value,
        CredentialPersistence persistence = CredentialPersistence.LocalMachine)
    {
        var targetName = $"{CredentialTargetPrefix}:{name}";

        var credential = new CredNativeMethods.CREDENTIAL
        {
            TargetName = targetName,
            CredentialBlob = Encoding.Unicode.GetBytes(value),
            CredentialBlobSize = (uint)(Encoding.Unicode.GetByteCount(value)),
            Type = CredNativeMethods.CRED_TYPE_GENERIC,
            Persist = (uint)persistence,
            UserName = Environment.UserName,
        };

        bool success = CredNativeMethods.CredWriteW(ref credential, 0);
        if (!success)
        {
            var error = Marshal.GetLastWin32Error();
            throw new InvalidOperationException(
                $"CredWrite 失败: 错误码 {error} (0x{error:X8})");
        }
    }

    /// <summary>
    /// 读取凭据
    /// </summary>
    public static string? ReadCredential(string name)
    {
        var targetName = $"{CredentialTargetPrefix}:{name}";

        bool success = CredNativeMethods.CredReadW(
            targetName,
            CredNativeMethods.CRED_TYPE_GENERIC,
            0,
            out IntPtr credPtr);

        if (!success)
        {
            var error = Marshal.GetLastWin32Error();
            if (error == 1168) // ERROR_NOT_FOUND
                return null;
            throw new InvalidOperationException(
                $"CredRead 失败: 错误码 {error} (0x{error:X8})");
        }

        try
        {
            var credential = Marshal.PtrToStructure<CredNativeMethods.CREDENTIAL>(credPtr);
            if (credential.CredentialBlobSize > 0 && credential.CredentialBlob != IntPtr.Zero)
            {
                return Marshal.PtrToStringUni(
                    credential.CredentialBlob,
                    (int)credential.CredentialBlobSize / 2);
            }
            return null;
        }
        finally
        {
            CredNativeMethods.CredFree(credPtr);
        }
    }

    /// <summary>
    /// 删除凭据
    /// </summary>
    public static void DeleteCredential(string name)
    {
        var targetName = $"{CredentialTargetPrefix}:{name}";
        CredNativeMethods.CredDeleteW(targetName, CredNativeMethods.CRED_TYPE_GENERIC, 0);
    }

    /// <summary>
    /// 枚举所有本应用的凭据
    /// </summary>
    public static List<string> EnumerateCredentials()
    {
        var result = new List<string>();
        bool success = CredNativeMethods.CredEnumerateW(
            $"{CredentialTargetPrefix}:*", 0, out int count, out IntPtr credArrayPtr);

        if (!success || count == 0) return result;

        try
        {
            for (int i = 0; i < count; i++)
            {
                var credPtr = Marshal.ReadIntPtr(credArrayPtr, i * IntPtr.Size);
                var credential = Marshal.PtrToStructure<CredNativeMethods.CREDENTIAL>(credPtr);
                result.Add(credential.TargetName ?? $"entry-{i}");
            }
        }
        finally
        {
            CredNativeMethodsCredFree(credArrayPtr);
        }

        return result;
    }

    // ── 便捷方法：Token 管理 ──────────────────────────────

    /// <summary>
    /// 存储认证 Token
    /// </summary>
    public void StoreAuthToken(string token)
    {
        StoreCredential(TokenCredentialName, token);
    }

    /// <summary>
    /// 读取认证 Token
    /// </summary>
    public string? ReadAuthToken()
    {
        return ReadCredential(TokenCredentialName);
    }

    /// <summary>
    /// 存储 Refresh Token
    /// </summary>
    public void StoreRefreshToken(string refreshToken)
    {
        StoreCredential(RefreshTokenCredentialName, refreshToken);
    }

    /// <summary>
    /// 读取 Refresh Token
    /// </summary>
    public string? ReadRefreshToken()
    {
        return ReadCredential(RefreshTokenCredentialName);
    }

    /// <summary>
    /// 存储用户 ID
    /// </summary>
    public void StoreUserId(string userId)
    {
        StoreCredential(UserIdCredentialName, userId);
    }

    /// <summary>
    /// 读取用户 ID
    /// </summary>
    public string? ReadUserId()
    {
        return ReadCredential(UserIdCredentialName);
    }

    /// <summary>
    /// 清除所有凭据（登出时调用）
    /// </summary>
    public void ClearAllCredentials()
    {
        foreach (var name in new[] { TokenCredentialName, RefreshTokenCredentialName, UserIdCredentialName })
        {
            try { DeleteCredential(name); } catch { }
        }
    }

    /// <summary>
    /// 更新 Token 存储（Token 刷新后调用）
    /// </summary>
    public void UpdateTokens(string? accessToken, string? refreshToken, string? userId)
    {
        if (accessToken != null) StoreAuthToken(accessToken);
        if (refreshToken != null) StoreRefreshToken(refreshToken);
        if (userId != null) StoreUserId(userId);
    }

    // ── 清理 ──────────────────────────────────────────────

    public void Dispose()
    {
        // 凭据在 Windows 凭据管理器中持久化，不需要清理
    }
}

/// <summary>
/// 凭据持久化类型
/// </summary>
public enum CredentialPersistence : uint
{
    Session = 1,        // 仅当前登录会话
    LocalMachine = 2,   // 持久化到本地计算机
    Enterprise = 3,     // 持久化到 Active Directory（支持漫游）
}

// ────────────────────────────────────────────────────────
// Credential Manager Native API P/Invoke
// ────────────────────────────────────────────────────────

internal static class CredNativeMethods
{
    public const uint CRED_TYPE_GENERIC = 1;
    public const uint CRED_TYPE_DOMAIN_PASSWORD = 2;

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct CREDENTIAL
    {
        public uint Flags;
        public uint Type;
        public string TargetName;
        public string Comment;
        public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
        public uint CredentialBlobSize;
        public IntPtr CredentialBlob;
        public uint Persist;
        public uint AttributeCount;
        public IntPtr Attributes;
        public string TargetAlias;
        public string UserName;
    }

    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool CredWriteW(ref CREDENTIAL credential, uint flags);

    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool CredReadW(
        string targetName, uint type, uint flags, out IntPtr credential);

    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool CredDeleteW(
        string targetName, uint type, uint flags);

    [DllImport("advapi32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    [return: MarshalAs(UnmanagedType.Bool)]
    public static extern bool CredEnumerateW(
        string filter, uint flags, out int count, out IntPtr credentials);

    [DllImport("advapi32.dll", SetLastError = true)]
    public static extern void CredFree(IntPtr buffer);
}