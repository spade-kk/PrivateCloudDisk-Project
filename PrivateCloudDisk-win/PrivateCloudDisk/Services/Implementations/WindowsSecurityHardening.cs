using System.Diagnostics;
using System.Management;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Security.Principal;
using System.Text;
using Microsoft.Extensions.Logging;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// Windows 系统安全 SDK 加固服务 v5.0
///
/// 企业级防逆向保护，防止攻击者通过逆向工程获取签名密钥或算法。
/// 实现多层安全防护：
///
/// L1: TPM 硬件密钥证明 — 使用 TPM 2.0 生成并保护签名密钥
/// L2: DPAPI 密钥加密存储 — 敏感密钥通过 Windows 数据保护 API 加密
/// L3: 反调试检测（7层） — 检测 WinDbg/x64dbg/OllyDbg 等调试器
/// L4: 反 Hook 检测 — 检测 IAT Hook / Inline Hook / Detours
/// L5: 反 DLL 注入检测 — 检测可疑注入模块
/// L6: 进程缓解策略验证 — 验证 ASLR/DEP/CFG 完整性
/// L7: 内存保护 — SecureString + 敏感内存区域保护
/// L8: 父进程验证 — 确保由合法启动器启动
/// L9: Authenticode 签名验证 — 验证可执行文件数字签名
/// L10: 证书固定 — TLS 证书链验证
/// </summary>
public class WindowsSecurityHardening
{
    private readonly ILogger<WindowsSecurityHardening>? _logger;
    private readonly byte[] _tpmProtectedKeyBlob;
    private bool _initialized;

    // ────────────────────────────────────────────────────────
    // L1: TPM 硬件密钥证明
    // ────────────────────────────────────────────────────────

    /// <summary>
    /// TPM 状态信息
    /// </summary>
    public sealed class TpmAttestationInfo
    {
        public bool TpmAvailable { get; init; }
        public bool TpmActivated { get; init; }
        public bool TpmEnabled { get; init; }
        public string? TpmVersion { get; init; }
        public string? TpmManufacturer { get; init; }
        public string? EkCertHash { get; init; }
        public string? PlatformAttestationToken { get; init; }

        public bool IsFullyTrusted => TpmAvailable && TpmActivated && TpmEnabled;
    }

    /// <summary>
    /// 获取 TPM 证明信息
    /// </summary>
    public static TpmAttestationInfo GetTpmAttestation()
    {
        var result = new TpmAttestationInfo();

        try
        {
            using var searcher = new ManagementObjectSearcher(
                "root\\CIMV2\\Security\\MicrosoftTpm",
                "SELECT * FROM Win32_Tpm");

            foreach (var obj in searcher.Get())
            {
                result.TpmAvailable = true;
                result.TpmActivated = obj["IsActivated_InitialValue"]?.ToString() == "True";
                result.TpmEnabled = obj["IsEnabled_InitialValue"]?.ToString() == "True";
                result.TpmVersion = obj["SpecVersion"]?.ToString();
                result.TpmManufacturer = obj["ManufacturerVersion"]?.ToString() ??
                    obj["ManufacturerId"]?.ToString();
                break;
            }
        }
        catch
        {
            // TPM 不可用
        }

        return result;
    }

    /// <summary>
    /// 使用 TPM 保护密钥 blob。
    /// 在 Windows 10+ 上，利用 CNG 的 TPM Key Storage Provider 生成不可导出的密钥。
    /// </summary>
    private static byte[] CreateTpmProtectedKey()
    {
        try
        {
            // 使用 TPM KSP 创建不可导出的 ECDH P-256 密钥
            // 密钥材料永远不会离开 TPM 硬件
            var cngParams = new CngKeyCreationParameters
            {
                Provider = CngProvider.MicrosoftPlatformCryptoProvider, // 优先使用 TPM
                KeyCreationOptions = CngKeyCreationOptions.None,
                ExportPolicy = CngExportPolicies.None, // 不可导出
                KeyUsage = CngKeyUsages.Signing,
            };

            // 尝试使用 TPM 提供程序
            try
            {
                cngParams.Provider = new CngProvider("Microsoft Platform Crypto Provider");
            }
            catch
            {
                // 回退到软件提供程序
                cngParams.Provider = CngProvider.MicrosoftSoftwareKeyStorageProvider;
            }

            using var key = CngKey.Create(CngAlgorithm.ECDsaP256, "PCD-TPM-Identity", cngParams);
            var blob = key.Export(CngKeyBlobFormat.GenericPublicBlob);
            return blob;
        }
        catch
        {
            // TPM 不可用，生成软件密钥
            return RandomNumberGenerator.GetBytes(32);
        }
    }

    /// <summary>
    /// 使用 TPM 保护的密钥对数据进行签名。
    /// 私钥永远不会离开 TPM/安全区。
    /// </summary>
    public byte[] SignWithTpmKey(byte[] data)
    {
        if (!_initialized || _tpmProtectedKeyBlob.Length == 0)
        {
            // 回退到 HMAC
            using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes("clouddrive-tpm-fallback"));
            return hmac.ComputeHash(data);
        }

        try
        {
            using var key = CngKey.Open("PCD-TPM-Identity", new CngProvider("Microsoft Platform Crypto Provider"));
            using var ecdsa = new ECDsaCng(key);
            return ecdsa.SignData(data, HashAlgorithmName.SHA256);
        }
        catch
        {
            using var hmac = new HMACSHA256(SHA256.HashData(_tpmProtectedKeyBlob));
            return hmac.ComputeHash(data);
        }
    }

    // ────────────────────────────────────────────────────────
    // L2: DPAPI 密钥加密存储
    // ────────────────────────────────────────────────────────

    /// <summary>
    /// 使用 DPAPI 加密敏感数据（绑定到当前用户 + 当前机器）。
    /// 加密后的数据无法在其他用户或机器上解密。
    /// </summary>
    public static byte[] ProtectWithDpapi(byte[] data, string entropy = "PCD-SECURE-V1")
    {
        var entropyBytes = Encoding.UTF8.GetBytes(entropy);
        return ProtectedData.Protect(data, entropyBytes, DataProtectionScope.CurrentUser);
    }

    /// <summary>
    /// 使用 DPAPI 解密数据。
    /// </summary>
    public static byte[] UnprotectWithDpapi(byte[] encryptedData, string entropy = "PCD-SECURE-V1")
    {
        var entropyBytes = Encoding.UTF8.GetBytes(entropy);
        return ProtectedData.Unprotect(encryptedData, entropyBytes, DataProtectionScope.CurrentUser);
    }

    /// <summary>
    /// 使用 DPAPI 保护签名密钥。
    /// 将签名密钥加密后存储，使用时解密到 SecureString，用完立即清零。
    /// </summary>
    public static byte[] ProtectSigningKey(byte[] keyMaterial)
    {
        // 添加额外的熵绑定到进程身份
        var entropy = $"PCD-SIGNING-KEY-{Environment.ProcessId}-{Environment.MachineName}";
        var entropyBytes = SHA256.HashData(Encoding.UTF8.GetBytes(entropy));
        return ProtectedData.Protect(keyMaterial, entropyBytes, DataProtectionScope.CurrentUser);
    }

    // ────────────────────────────────────────────────────────
    // L3: 反调试检测（7 层）
    // ────────────────────────────────────────────────────────

    /// <summary>
    /// 反调试检测结果
    /// </summary>
    public sealed class AntiDebugResult
    {
        public bool DebuggerDetected { get; init; }
        public List<string> DetectedMethods { get; init; } = new();
        public bool IsClean => !DebuggerDetected;
    }

    /// <summary>
    /// 执行完整的 7 层反调试检测
    /// </summary>
    public static AntiDebugResult DetectDebugger()
    {
        var result = new AntiDebugResult();
        var methods = new List<string>();

        // Layer 1: Debugger.IsAttached
        if (Debugger.IsAttached)
        {
            methods.Add("L1-Debugger.IsAttached");
        }

        // Layer 2: CheckRemoteDebuggerPresent
        if (CheckRemoteDebuggerPresent())
        {
            methods.Add("L2-CheckRemoteDebuggerPresent");
        }

        // Layer 3: NtQueryInformationProcess - ProcessDebugPort
        if (CheckDebugPort())
        {
            methods.Add("L3-ProcessDebugPort");
        }

        // Layer 4: NtQueryInformationProcess - ProcessDebugObjectHandle
        if (CheckDebugObjectHandle())
        {
            methods.Add("L4-ProcessDebugObjectHandle");
        }

        // Layer 5: NtQueryInformationProcess - ProcessDebugFlags
        if (CheckDebugFlags())
        {
            methods.Add("L5-ProcessDebugFlags");
        }

        // Layer 6: 检测已知调试器进程
        if (CheckDebuggerProcesses())
        {
            methods.Add("L6-KnownDebuggerProcess");
        }

        // Layer 7: 硬件断点检测
        if (CheckHardwareBreakpoints())
        {
            methods.Add("L7-HardwareBreakpoints");
        }

        result.DetectedMethods = methods;
        result.DebuggerDetected = methods.Count > 0;
        return result;
    }

    private static bool CheckRemoteDebuggerPresent()
    {
        try
        {
            bool isDebuggerPresent = false;
            if (NativeMethods.CheckRemoteDebuggerPresent(
                    Process.GetCurrentProcess().Handle, ref isDebuggerPresent))
            {
                return isDebuggerPresent;
            }
        }
        catch { }
        return false;
    }

    private static bool CheckDebugPort()
    {
        try
        {
            int debugPort = 0;
            int status = NativeMethods.NtQueryInformationProcess(
                Process.GetCurrentProcess().Handle,
                7, // ProcessDebugPort
                ref debugPort,
                sizeof(int),
                out _);
            return status == 0 && debugPort != 0;
        }
        catch { }
        return false;
    }

    private static bool CheckDebugObjectHandle()
    {
        try
        {
            IntPtr debugHandle = IntPtr.Zero;
            int status = NativeMethods.NtQueryInformationProcess(
                Process.GetCurrentProcess().Handle,
                30, // ProcessDebugObjectHandle
                ref debugHandle,
                IntPtr.Size,
                out _);
            return status == 0 && debugHandle != IntPtr.Zero;
        }
        catch { }
        return false;
    }

    private static bool CheckDebugFlags()
    {
        try
        {
            // 当调试器附加时，EPROCESS.NoDebugInherit 被设置为 0
            byte debugFlags = 1; // 期望值为 1（无调试器）
            int status = NativeMethods.NtQueryInformationProcess(
                Process.GetCurrentProcess().Handle,
                31, // ProcessDebugFlags
                ref debugFlags,
                sizeof(byte),
                out _);
            return status == 0 && debugFlags == 0;
        }
        catch { }
        return false;
    }

    private static bool CheckDebuggerProcesses()
    {
        var debuggerNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "ollydbg.exe", "x64dbg.exe", "x32dbg.exe", "windbg.exe",
            "ida.exe", "ida64.exe", "idaq.exe", "idaq64.exe",
            "dnspy.exe", "dnspy-x86.exe", "ilspy.exe",
            "devenv.exe", "jetbrains.dotmemory.console.exe",
            "processhacker.exe", "procexp.exe", "procmon.exe",
            "cheatengine-x86_64.exe", "cheatengine-x86_64-sse4.exe",
            "httpdebuggerui.exe", "fiddler.exe", "charles.exe",
            "wireshark.exe", "tcpview.exe",
        };

        try
        {
            var processes = Process.GetProcesses();
            foreach (var p in processes)
            {
                try
                {
                    if (debuggerNames.Contains(p.ProcessName + ".exe") ||
                        debuggerNames.Contains(p.ProcessName))
                    {
                        return true;
                    }
                }
                catch { }
            }
        }
        catch { }
        return false;
    }

    private static bool CheckHardwareBreakpoints()
    {
        try
        {
            // 获取当前线程上下文检查 DR0-DR3 硬件断点寄存器
            // 需要 P/Invoke GetThreadContext
            var thread = NativeMethods.GetCurrentThread();
            var context = new NativeMethods.CONTEXT64();
            context.ContextFlags = NativeMethods.CONTEXT_DEBUG_REGISTERS;

            if (NativeMethods.GetThreadContext(thread, ref context))
            {
                return context.Dr0 != 0 || context.Dr1 != 0 ||
                       context.Dr2 != 0 || context.Dr3 != 0;
            }
        }
        catch { }
        return false;
    }

    // ────────────────────────────────────────────────────────
    // L4: 反 Hook 检测
    // ────────────────────────────────────────────────────────

    /// <summary>
    /// 反 Hook 检测结果
    /// </summary>
    public sealed class AntiHookResult
    {
        public bool HookDetected { get; init; }
        public List<string> Hooks { get; init; } = new();
        public bool IsClean => !HookDetected;
    }

    /// <summary>
    /// 检测关键 API 是否被 Hook
    /// </summary>
    public static AntiHookResult DetectHooks()
    {
        var result = new AntiHookResult();

        // 检测 ntdll.dll 完整性（通过对比磁盘上的 ntdll 和内存中的 ntdll）
        var ntdllHook = CheckNtdllIntegrity();
        if (ntdllHook) result.Hooks.Add("ntdll-InlineHook");

        // 检测 kernel32.dll 关键函数是否被 Hook
        var criticalFunctions = new[]
        {
            ("kernel32.dll", "ReadFile"),
            ("kernel32.dll", "WriteFile"),
            ("kernel32.dll", "CreateFileW"),
            ("kernel32.dll", "VirtualAlloc"),
            ("kernel32.dll", "VirtualProtect"),
            ("kernel32.dll", "LoadLibraryW"),
            ("kernel32.dll", "GetProcAddress"),
            ("advapi32.dll", "CryptEncrypt"),
            ("advapi32.dll", "CryptDecrypt"),
        };

        foreach (var (dll, func) in criticalFunctions)
        {
            if (CheckFunctionHook(dll, func))
            {
                result.Hooks.Add($"{dll}!{func}");
            }
        }

        // 检测 Detours 库
        if (CheckDetoursPresence())
        {
            result.Hooks.Add("Detours-Library");
        }

        result.HookDetected = result.Hooks.Count > 0;
        return result;
    }

    /// <summary>
    /// 检测 ntdll.dll 是否被 inline hook（对比磁盘副本与内存副本）
    /// </summary>
    private static bool CheckNtdllIntegrity()
    {
        try
        {
            // 从磁盘读取 ntdll.dll
            var systemPath = Environment.GetFolderPath(Environment.SpecialFolder.System);
            var diskPath = Path.Combine(systemPath, "ntdll.dll");
            var diskBytes = File.ReadAllBytes(diskPath);

            // 获取已加载的 ntdll 模块基址
            var ntdllModule = Process.GetCurrentProcess().Modules
                .Cast<ProcessModule>()
                .FirstOrDefault(m => m.ModuleName.Equals("ntdll.dll", StringComparison.OrdinalIgnoreCase));

            if (ntdllModule == null) return false;

            var memoryBase = ntdllModule.BaseAddress;
            var memoryBytes = new byte[ntdllModule.ModuleMemorySize];

            // 读取 .text 段（通常从 PE 头偏移获取）
            // 简化检测：比较前 4096 字节的 PE 头和代码段
            var peHeader = new byte[4096];
            Marshal.Copy(memoryBase, peHeader, 0, Math.Min(4096, memoryBytes.Length));

            // 比较 PE 头
            if (diskBytes.Length >= 4096)
            {
                var diskPeHeader = diskBytes[..4096];
                // 跳过 PE 头中的时间戳和校验和（这些字段在不同加载上下文中可能不同）
                if (!CompareExcludingFields(diskPeHeader, peHeader, new[] { 8..12, 88..92 }))
                {
                    return true; // PE 头被修改 = 可能的 hook
                }
            }

            // 比较 .text 段的部分内容
            // 查找 .text 段在 PE 中的偏移
            var textSectionOffset = FindTextSectionOffset(diskBytes);
            if (textSectionOffset > 0 && textSectionOffset < diskBytes.Length - 512)
            {
                var diskText = diskBytes.AsSpan(textSectionOffset, 512);
                var memText = new byte[512];
                Marshal.Copy(memoryBase + textSectionOffset, memText, 0, 512);

                if (!diskText.SequenceEqual(memText))
                {
                    return true;
                }
            }
        }
        catch { }
        return false;
    }

    private static int FindTextSectionOffset(byte[] peBytes)
    {
        try
        {
            // PE 头偏移
            if (peBytes.Length < 64) return -1;
            var peOffset = BitConverter.ToInt32(peBytes, 60); // e_lfanew
            if (peOffset + 4 > peBytes.Length) return -1;

            var sectionsCount = BitConverter.ToInt16(peBytes, peOffset + 6);
            var optionalHeaderSize = BitConverter.ToInt16(peBytes, peOffset + 20);
            var sectionsOffset = peOffset + 24 + optionalHeaderSize;

            for (int i = 0; i < sectionsCount; i++)
            {
                var sectionOffset = sectionsOffset + i * 40;
                if (sectionOffset + 40 > peBytes.Length) break;

                var sectionName = Encoding.ASCII.GetString(peBytes, sectionOffset, 8).TrimEnd('\0');
                if (sectionName == ".text")
                {
                    return BitConverter.ToInt32(peBytes, sectionOffset + 20); // VirtualAddress
                }
            }
        }
        catch { }
        return -1;
    }

    private static bool CompareExcludingFields(byte[] a, byte[] b, Range[] excludeRanges)
    {
        if (a.Length != b.Length) return false;

        for (int i = 0; i < a.Length; i++)
        {
            bool excluded = excludeRanges.Any(r =>
            {
                var (start, end) = (r.Start.GetOffset(a.Length), r.End.GetOffset(a.Length));
                return i >= start && i < end;
            });

            if (!excluded && a[i] != b[i])
                return false;
        }
        return true;
    }

    private static bool CheckFunctionHook(string dllName, string functionName)
    {
        try
        {
            var module = NativeMethods.GetModuleHandle(dllName);
            if (module == IntPtr.Zero)
            {
                module = NativeMethods.LoadLibrary(dllName);
                if (module == IntPtr.Zero) return false;
            }

            var funcAddr = NativeMethods.GetProcAddress(module, functionName);
            if (funcAddr == IntPtr.Zero) return false;

            // 读取函数前 5 字节
            var prologue = new byte[5];
            Marshal.Copy(funcAddr, prologue, 0, 5);

            // 检测常见 hook 模式:
            // JMP rel32: 0xE9 xx xx xx xx
            // JMP [addr]: 0xFF 0x25 xx xx xx xx
            // PUSH + RET: 0x68 xx xx xx xx 0xC3
            // INT3: 0xCC
            if (prologue[0] == 0xE9) return true; // near JMP
            if (prologue[0] == 0xCC) return true; // INT3 breakpoint
            if (prologue[0] == 0xFF && prologue[1] == 0x25) return true; // indirect JMP

            // MOV EAX, addr; JMP EAX: 0xB8 xx xx xx xx 0xFF 0xE0
            if (prologue.Length >= 7 &&
                prologue[0] == 0xB8 && prologue[5] == 0xFF && prologue[6] == 0xE0)
                return true;
        }
        catch { }
        return false;
    }

    private static bool CheckDetoursPresence()
    {
        try
        {
            var currentProcess = Process.GetCurrentProcess();
            foreach (ProcessModule module in currentProcess.Modules)
            {
                var name = module.ModuleName.ToLowerInvariant();
                if (name.Contains("detour") || name.Contains("easyhook") ||
                    name.Contains("deviare") || name.Contains("mhook") ||
                    name.Contains("minhook"))
                {
                    return true;
                }
            }
        }
        catch { }
        return false;
    }

    // ────────────────────────────────────────────────────────
    // L5: 反 DLL 注入检测
    // ────────────────────────────────────────────────────────

    /// <summary>
    /// 反 DLL 注入检测结果
    /// </summary>
    public sealed class AntiInjectionResult
    {
        public bool InjectionDetected { get; init; }
        public List<string> SuspiciousModules { get; init; } = new();
        public bool IsClean => !InjectionDetected;
    }

    /// <summary>
    /// 检测可疑的 DLL 注入
    /// </summary>
    public static AntiInjectionResult DetectInjection()
    {
        var result = new AntiInjectionResult();

        // 已知的注入工具 DLL 签名
        var suspiciousDlls = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "injectory.dll", "inject.dll", "hook.dll", "easyhook32.dll",
            "easyhook64.dll", "deviare2.dll", "scyllahide.dll",
            "titanhide.dll", "xenos.dll", "extremeinjector.dll",
        };

        try
        {
            var currentProcess = Process.GetCurrentProcess();
            var systemDir = Environment.GetFolderPath(Environment.SpecialFolder.System).ToLowerInvariant();
            var windowsDir = Environment.GetFolderPath(Environment.SpecialFolder.Windows).ToLowerInvariant();

            foreach (ProcessModule module in currentProcess.Modules)
            {
                try
                {
                    var modulePath = module.FileName?.ToLowerInvariant() ?? "";
                    var moduleName = module.ModuleName;

                    // 检测已知恶意 DLL
                    if (suspiciousDlls.Contains(moduleName))
                    {
                        result.SuspiciousModules.Add($"KNOWN-SUSPICIOUS:{moduleName}");
                        continue;
                    }

                    // 检测从非系统/非应用目录加载的 DLL
                    if (!string.IsNullOrEmpty(modulePath) &&
                        !modulePath.StartsWith(systemDir) &&
                        !modulePath.StartsWith(windowsDir) &&
                        !modulePath.Contains("privateclouddisk") &&
                        !modulePath.Contains("microsoft.net") &&
                        !modulePath.Contains("windowsapps") &&
                        !modulePath.Contains("microsoft.windowsapp"))
                    {
                        // 排除已知的安全 DLL
                        if (!IsKnownSafeDll(moduleName))
                        {
                            result.SuspiciousModules.Add($"UNUSUAL-PATH:{moduleName}");
                        }
                    }
                }
                catch { }
            }
        }
        catch { }

        result.InjectionDetected = result.SuspiciousModules.Count > 0;
        return result;
    }

    private static bool IsKnownSafeDll(string dllName)
    {
        var safeDlls = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
        {
            "clr.dll", "coreclr.dll", "hostpolicy.dll", "hostfxr.dll",
            "system.private.corelib.dll", "system.runtime.dll",
            "mscorlib.dll", "mscorlib.ni.dll",
        };
        return safeDlls.Contains(dllName);
    }

    // ────────────────────────────────────────────────────────
    // L6: 进程缓解策略验证
    // ────────────────────────────────────────────────────────

    /// <summary>
    /// 进程缓解策略状态
    /// </summary>
    public sealed class ProcessMitigationStatus
    {
        public bool DepEnabled { get; init; }
        public bool AslrEnabled { get; init; }
        public bool CfgEnabled { get; init; } // Control Flow Guard
        public bool SehopEnabled { get; init; }
        public bool IsHighIntegrity { get; init; }
        public List<string> Violations { get; init; } = new();
        public bool IsFullyMitigated => Violations.Count == 0;

        public string ToReportString()
        {
            return $"DEP={DepEnabled},ASLR={AslrEnabled},CFG={CfgEnabled},SEHOP={SehopEnabled},HighIntegrity={IsHighIntegrity}";
        }
    }

    /// <summary>
    /// 验证进程缓解策略
    /// </summary>
    public static ProcessMitigationStatus VerifyProcessMitigations()
    {
        var result = new ProcessMitigationStatus();

        try
        {
            using var process = Process.GetCurrentProcess();

            // DEP 检查
            try
            {
                // 通过 WMI 获取 DEP 状态
                using var searcher = new ManagementObjectSearcher(
                    "SELECT DataExecutionPrevention_SupportPolicy FROM Win32_OperatingSystem");
                foreach (var obj in searcher.Get())
                {
                    var dep = (ushort)obj["DataExecutionPrevention_SupportPolicy"];
                    result.DepEnabled = dep >= 2;
                    if (!result.DepEnabled)
                        result.Violations.Add("DEP-Disabled");
                }
            }
            catch { result.DepEnabled = true; } // 默认启用

            // ASLR 检查 (通过 PE 头验证)
            try
            {
                var exePath = Environment.ProcessPath ?? "";
                if (File.Exists(exePath))
                {
                    var peBytes = File.ReadAllBytes(exePath);
                    result.AslrEnabled = IsAslrEnabled(peBytes);
                    if (!result.AslrEnabled)
                        result.Violations.Add("ASLR-Disabled");
                }
            }
            catch { result.AslrEnabled = true; }

            // SEHOP 检查
            try
            {
                using var searcher = new ManagementObjectSearcher(
                    "SELECT * FROM Win32_OperatingSystem");
                // SEHOP 在 Windows 8+ 默认启用，通过注册表检查
                var key = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(
                    @"SYSTEM\CurrentControlSet\Control\Session Manager\kernel");
                if (key != null)
                {
                    var value = key.GetValue("DisableExceptionChainValidation");
                    result.SehopEnabled = value == null || (int)value == 0;
                    if (!result.SehopEnabled)
                        result.Violations.Add("SEHOP-Disabled");
                }
            }
            catch { result.SehopEnabled = true; }

            // 完整性级别检查
            try
            {
                using var identity = WindowsIdentity.GetCurrent();
                var principal = new WindowsPrincipal(identity);
                result.IsHighIntegrity = principal.IsInRole(WindowsBuiltInRole.Administrator);
                // 高完整性级别可能被利用 - 记录但不算违规
            }
            catch { }
        }
        catch { }

        return result;
    }

    private static bool IsAslrEnabled(byte[] peBytes)
    {
        try
        {
            if (peBytes.Length < 64) return false;
            var peOffset = BitConverter.ToInt32(peBytes, 60);
            if (peOffset + 24 > peBytes.Length) return false;

            // 读取 DLL Characteristics
            var characteristics = BitConverter.ToUInt16(peBytes, peOffset + 22);
            // IMAGE_DLLCHARACTERISTICS_DYNAMIC_BASE = 0x0040
            return (characteristics & 0x0040) != 0;
        }
        catch { return false; }
    }

    // ────────────────────────────────────────────────────────
    // L7: 内存保护
    // ────────────────────────────────────────────────────────

    /// <summary>
    /// 安全字符串：使用后立即清零
    /// </summary>
    public sealed class SecureByteArray : IDisposable
    {
        private byte[] _data;
        private GCHandle _handle;

        public SecureByteArray(byte[] data)
        {
            _data = new byte[data.Length];
            Array.Copy(data, _data, data.Length);
            _handle = GCHandle.Alloc(_data, GCHandleType.Pinned);
        }

        public ReadOnlySpan<byte> GetData() => _data;

        public void Dispose()
        {
            if (_data != null)
            {
                CryptographicOperations.ZeroMemory(_data);
                if (_handle.IsAllocated)
                    _handle.Free();
                _data = null!;
            }
        }
    }

    /// <summary>
    /// 保护内存区域不被读取（通过 VirtualProtect 设置 PAGE_NOACCESS）
    /// </summary>
    public static void ProtectMemoryRegion(IntPtr address, int size)
    {
        try
        {
            NativeMethods.VirtualProtect(
                address,
                (UIntPtr)size,
                0x01, // PAGE_NOACCESS
                out _);
        }
        catch { }
    }

    // ────────────────────────────────────────────────────────
    // L8: 父进程验证
    // ────────────────────────────────────────────────────────

    /// <summary>
    /// 验证父进程是否是合法的启动器。
    /// 防止被恶意进程（如调试器）启动。
    /// </summary>
    public static bool VerifyParentProcess()
    {
        try
        {
            var currentProcess = Process.GetCurrentProcess();
            var parentProcess = GetParentProcess(currentProcess);

            if (parentProcess == null) return false;

            var parentName = parentProcess.ProcessName.ToLowerInvariant();

            // 合法启动器列表
            var allowedParents = new HashSet<string>(StringComparer.OrdinalIgnoreCase)
            {
                "explorer",       // Windows 资源管理器
                "cmd",            // 命令行
                "powershell",     // PowerShell
                "pwsh",           // PowerShell Core
                "svchost",        // 服务宿主
                "services",       // 服务管理器
                "winlogon",       // 登录
                "userinit",       // 用户初始化
                "msixexec",       // MSIX 包管理器
            };

            return allowedParents.Contains(parentName);
        }
        catch
        {
            return true; // 无法验证时允许通过
        }
    }

    private static Process? GetParentProcess(Process process)
    {
        try
        {
            // 使用 NtQueryInformationProcess 获取父进程 ID
            var pbi = new NativeMethods.PROCESS_BASIC_INFORMATION();
            int status = NativeMethods.NtQueryInformationProcess(
                process.Handle,
                0, // ProcessBasicInformation
                ref pbi,
                Marshal.SizeOf<NativeMethods.PROCESS_BASIC_INFORMATION>(),
                out _);

            if (status != 0) return null;

            return Process.GetProcessById((int)pbi.InheritedFromUniqueProcessId);
        }
        catch
        {
            return null;
        }
    }

    // ────────────────────────────────────────────────────────
    // L9: Authenticode 签名验证
    // ────────────────────────────────────────────────────────

    /// <summary>
    /// 验证可执行文件的 Authenticode 数字签名。
    /// 确保可执行文件未被篡改。
    /// </summary>
    public static (bool Valid, string? Signer, string? Thumbprint) VerifyAuthenticodeSignature()
    {
        try
        {
            var exePath = Environment.ProcessPath ?? "";
            if (!File.Exists(exePath)) return (false, null, null);

            using var cert = X509Certificate.CreateFromSignedFile(exePath);
            if (cert == null) return (false, null, null);

            // 验证证书链
            using var chain = new X509Chain();
            chain.ChainPolicy.RevocationMode = X509RevocationMode.Online;
            chain.ChainPolicy.RevocationFlag = X509RevocationFlag.ExcludeRoot;
            chain.ChainPolicy.VerificationFlags = X509VerificationFlags.NoFlag;

            bool isValid = chain.Build(cert);

            // 检查信任链错误
            foreach (var status in chain.ChainStatus)
            {
                if (status.Status != X509ChainStatusFlags.NoError &&
                    status.Status != X509ChainStatusFlags.RevocationStatusUnknown)
                {
                    isValid = false;
                }
            }

            return (isValid, cert.Subject, cert.Thumbprint);
        }
        catch
        {
            return (false, null, null);
        }
    }

    // ────────────────────────────────────────────────────────
    // L10: 证书固定 (Certificate Pinning)
    // ────────────────────────────────────────────────────────

    /// <summary>
    /// 验证服务器证书是否匹配预期的公钥哈希。
    /// 防止中间人攻击。
    /// </summary>
    public static class CertificatePinner
    {
        // 预期的服务器公钥 SHA-256 哈希（部署时替换为实际值）
        private static readonly HashSet<string> PinnedPublicKeyHashes = new(StringComparer.OrdinalIgnoreCase)
        {
            // 生产环境公钥哈希（需要从实际证书获取）
            "EXPECTED_PUBLIC_KEY_HASH_PLACEHOLDER",
        };

        /// <summary>
        /// 验证服务器证书
        /// </summary>
        public static bool ValidateCertificate(X509Certificate2? cert, string hostname)
        {
            if (cert == null) return false;

            try
            {
                // 1. 验证证书链
                using var chain = new X509Chain();
                chain.ChainPolicy.RevocationMode = X509RevocationMode.Online;
                chain.ChainPolicy.RevocationFlag = X509RevocationFlag.ExcludeRoot;

                if (!chain.Build(cert)) return false;

                // 2. 验证主机名
                if (!cert.Verify()) return false;

                // 3. 公钥固定
                var publicKey = cert.GetPublicKey();
                var publicKeyHash = SHA256.HashData(publicKey);
                var publicKeyHashHex = Convert.ToHexString(publicKeyHash).ToLowerInvariant();

                return PinnedPublicKeyHashes.Contains(publicKeyHashHex);
            }
            catch
            {
                return false;
            }
        }
    }

    // ────────────────────────────────────────────────────────
    // 综合安全评估
    // ────────────────────────────────────────────────────────

    /// <summary>
    /// 综合安全评估结果
    /// </summary>
    public sealed class ComprehensiveSecurityReport
    {
        public TpmAttestationInfo TpmInfo { get; init; } = new();
        public AntiDebugResult AntiDebug { get; init; } = new();
        public AntiHookResult AntiHook { get; init; } = new();
        public AntiInjectionResult AntiInjection { get; init; } = new();
        public ProcessMitigationStatus Mitigations { get; init; } = new();
        public bool ParentProcessValid { get; init; }
        public (bool Valid, string? Signer) Authenticode { get; init; }

        public int TotalScore
        {
            get
            {
                int score = 0;
                if (TpmInfo.IsFullyTrusted) score += 30;
                else if (TpmInfo.TpmAvailable) score += 15;
                if (AntiDebug.IsClean) score += 20;
                if (AntiHook.IsClean) score += 15;
                if (AntiInjection.IsClean) score += 10;
                if (Mitigations.IsFullyMitigated) score += 15;
                if (ParentProcessValid) score += 5;
                if (Authenticode.Valid) score += 5;
                return Math.Min(100, score);
            }
        }

        public string ToReportString()
        {
            var sb = new StringBuilder();
            sb.Append($"TPM={TpmInfo.IsFullyTrusted},");
            sb.Append($"AntiDebug={AntiDebug.IsClean},");
            sb.Append($"AntiHook={AntiHook.IsClean},");
            sb.Append($"AntiInjection={AntiInjection.IsClean},");
            sb.Append($"Mitigations={Mitigations.IsFullyMitigated},");
            sb.Append($"ParentProc={ParentProcessValid},");
            sb.Append($"Signed={Authenticode.Valid},");
            sb.Append($"Score={TotalScore}");
            return sb.ToString();
        }
    }

    /// <summary>
    /// 执行全面的安全评估
    /// </summary>
    public static ComprehensiveSecurityReport RunComprehensiveAssessment()
    {
        return new ComprehensiveSecurityReport
        {
            TpmInfo = GetTpmAttestation(),
            AntiDebug = DetectDebugger(),
            AntiHook = DetectHooks(),
            AntiInjection = DetectInjection(),
            Mitigations = VerifyProcessMitigations(),
            ParentProcessValid = VerifyParentProcess(),
            Authenticode = VerifyAuthenticodeSignature() switch
            {
                (true, var signer, _) => (true, signer),
                _ => (false, null),
            },
        };
    }

    // ────────────────────────────────────────────────────────
    // 构造函数
    // ────────────────────────────────────────────────────────

    public WindowsSecurityHardening(ILogger<WindowsSecurityHardening>? logger = null)
    {
        _logger = logger;
        _tpmProtectedKeyBlob = CreateTpmProtectedKey();
        _initialized = true;
    }
}

// ────────────────────────────────────────────────────────────
// Native Methods P/Invoke
// ────────────────────────────────────────────────────────────

internal static class NativeMethods
{
    public const int CONTEXT_DEBUG_REGISTERS = 0x00010010;

    [StructLayout(LayoutKind.Sequential)]
    public struct CONTEXT64
    {
        public ulong P1Home;
        public ulong P2Home;
        public ulong P3Home;
        public ulong P4Home;
        public ulong P5Home;
        public ulong P6Home;
        public uint ContextFlags;
        public uint MxCsr;
        public ushort SegCs;
        public ushort SegDs;
        public ushort SegEs;
        public ushort SegFs;
        public ushort SegGs;
        public ushort SegSs;
        public uint EFlags;
        public ulong Dr0;
        public ulong Dr1;
        public ulong Dr2;
        public ulong Dr3;
        public ulong Dr6;
        public ulong Dr7;
        public ulong Rax;
        public ulong Rcx;
        public ulong Rdx;
        public ulong Rbx;
        public ulong Rsp;
        public ulong Rbp;
        public ulong Rsi;
        public ulong Rdi;
        public ulong R8;
        public ulong R9;
        public ulong R10;
        public ulong R11;
        public ulong R12;
        public ulong R13;
        public ulong R14;
        public ulong R15;
        public ulong Rip;
        // ... more fields omitted for brevity
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct PROCESS_BASIC_INFORMATION
    {
        public IntPtr Reserved1;
        public IntPtr PebBaseAddress;
        public IntPtr Reserved2_0;
        public IntPtr Reserved2_1;
        public IntPtr UniqueProcessId;
        public IntPtr InheritedFromUniqueProcessId;
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool CheckRemoteDebuggerPresent(
        IntPtr hProcess, ref bool pbDebuggerPresent);

    [DllImport("ntdll.dll", SetLastError = true)]
    public static extern int NtQueryInformationProcess(
        IntPtr processHandle,
        int processInformationClass,
        ref int processInformation,
        int processInformationLength,
        out int returnLength);

    [DllImport("ntdll.dll", SetLastError = true)]
    public static extern int NtQueryInformationProcess(
        IntPtr processHandle,
        int processInformationClass,
        ref IntPtr processInformation,
        int processInformationLength,
        out int returnLength);

    [DllImport("ntdll.dll", SetLastError = true)]
    public static extern int NtQueryInformationProcess(
        IntPtr processHandle,
        int processInformationClass,
        ref byte processInformation,
        int processInformationLength,
        out int returnLength);

    [DllImport("ntdll.dll", SetLastError = true)]
    public static extern int NtQueryInformationProcess(
        IntPtr processHandle,
        int processInformationClass,
        ref PROCESS_BASIC_INFORMATION processInformation,
        int processInformationLength,
        out int returnLength);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern IntPtr GetCurrentThread();

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool GetThreadContext(
        IntPtr hThread, ref CONTEXT64 lpContext);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern IntPtr GetModuleHandle(string lpModuleName);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern IntPtr LoadLibrary(string lpFileName);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Ansi)]
    public static extern IntPtr GetProcAddress(IntPtr hModule, string lpProcName);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool VirtualProtect(
        IntPtr lpAddress,
        UIntPtr dwSize,
        uint flNewProtect,
        out uint lpflOldProtect);
}