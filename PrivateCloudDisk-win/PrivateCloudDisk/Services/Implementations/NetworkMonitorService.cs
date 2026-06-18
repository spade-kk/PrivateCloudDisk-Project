using System.Runtime.InteropServices;
using Windows.Networking.Connectivity;

namespace PrivateCloudDisk.Services.Implementations;

/// <summary>
/// Windows 网络监控服务
///
/// 监控网络状态变化，实现智能同步策略：
///   - 检测网络连接类型（WiFi / Ethernet / 移动数据）
///   - 检测按流量计费网络（Metered Connection）
///   - 按流量计费网络暂停同步
///   - 网络断开时暂停传输
///   - 网络恢复时自动恢复传输
///   - 带宽估算和节流
///
/// 使用 Windows.Networking.Connectivity API：
///   - NetworkInformation.NetworkStatusChanged 事件
///   - ConnectionProfile.GetNetworkConnectivityLevel
///   - ConnectionProfile.GetConnectionCost
/// </summary>
public class NetworkMonitorService : IDisposable
{
    // ── 事件 ──────────────────────────────────────────────

    /// <summary>网络状态变化事件</summary>
    public event EventHandler<NetworkStateChangedEventArgs>? NetworkStateChanged;

    /// <summary>连接恢复事件</summary>
    public event EventHandler? ConnectionRestored;

    /// <summary>连接丢失事件</summary>
    public event EventHandler? ConnectionLost;

    /// <summary>按流量计费网络连接事件</summary>
    public event EventHandler? MeteredNetworkDetected;

    // ── 属性 ──────────────────────────────────────────────

    public NetworkConnectionType CurrentConnectionType { get; private set; }
    public NetworkConnectivityLevel CurrentConnectivityLevel { get; private set; }
    public bool IsMeteredConnection { get; private set; }
    public bool IsConnected { get; private set; }
    public bool IsInternetAvailable { get; private set; }
    public double EstimatedBandwidthMbps { get; private set; }
    public string? ConnectedNetworkName { get; private set; }

    private bool _wasConnected;

    public NetworkMonitorService()
    {
        // 初始化当前状态
        RefreshNetworkState();
        _wasConnected = IsConnected;

        // 注册网络状态变化事件
        NetworkInformation.NetworkStatusChanged += OnNetworkStatusChanged;
    }

    // ── 网络状态刷新 ──────────────────────────────────────

    private void RefreshNetworkState()
    {
        try
        {
            var profile = NetworkInformation.GetInternetConnectionProfile();
            if (profile == null)
            {
                IsConnected = false;
                IsInternetAvailable = false;
                IsMeteredConnection = false;
                CurrentConnectionType = NetworkConnectionType.None;
                CurrentConnectivityLevel = NetworkConnectivityLevel.None;
                ConnectedNetworkName = null;
                return;
            }

            IsConnected = true;
            CurrentConnectivityLevel = profile.GetNetworkConnectivityLevel();
            IsInternetAvailable = CurrentConnectivityLevel == NetworkConnectivityLevel.InternetAccess;
            ConnectedNetworkName = profile.ProfileName;

            // 检测连接类型
            CurrentConnectionType = profile.IsWlanConnectionProfile
                ? NetworkConnectionType.WiFi
                : profile.IsWwanConnectionProfile
                    ? NetworkConnectionType.Mobile
                    : NetworkConnectionType.Ethernet;

            // 检测按流量计费
            var cost = profile.GetConnectionCost();
            IsMeteredConnection = cost.NetworkCostType != NetworkCostType.Unrestricted
                && cost.NetworkCostType != NetworkCostType.Unknown;

            // 估算带宽
            EstimateBandwidth(profile);
        }
        catch
        {
            IsConnected = false;
            IsInternetAvailable = false;
        }
    }

    private void OnNetworkStatusChanged(object? sender)
    {
        var previousState = new NetworkState
        {
            IsConnected = IsConnected,
            IsInternetAvailable = IsInternetAvailable,
            IsMeteredConnection = IsMeteredConnection,
            ConnectionType = CurrentConnectionType,
        };

        RefreshNetworkState();

        var newState = new NetworkState
        {
            IsConnected = IsConnected,
            IsInternetAvailable = IsInternetAvailable,
            IsMeteredConnection = IsMeteredConnection,
            ConnectionType = CurrentConnectionType,
        };

        // 触发连接恢复事件
        if (!_wasConnected && IsConnected)
        {
            ConnectionRestored?.Invoke(this, EventArgs.Empty);
        }

        // 触发连接丢失事件
        if (_wasConnected && !IsConnected)
        {
            ConnectionLost?.Invoke(this, EventArgs.Empty);
        }

        // 触发按流量计费网络事件
        if (!previousState.IsMeteredConnection && IsMeteredConnection)
        {
            MeteredNetworkDetected?.Invoke(this, EventArgs.Empty);
        }

        // 触发通用状态变化事件
        NetworkStateChanged?.Invoke(this, new NetworkStateChangedEventArgs(previousState, newState));

        _wasConnected = IsConnected;
    }

    // ── 带宽估算 ──────────────────────────────────────────

    private void EstimateBandwidth(ConnectionProfile profile)
    {
        try
        {
            // 根据连接类型估算带宽
            EstimatedBandwidthMbps = CurrentConnectionType switch
            {
                NetworkConnectionType.Ethernet => 1000, // 假设千兆以太网
                NetworkConnectionType.WiFi => GetWifiSignalStrength(profile) switch
                {
                    >= 4 => 300,   // 信号强
                    >= 3 => 100,   // 信号中等
                    >= 2 => 50,    // 信号弱
                    _ => 10        // 信号极弱
                },
                NetworkConnectionType.Mobile => 20, // 假设 4G
                _ => 1
            };
        }
        catch
        {
            EstimatedBandwidthMbps = 10;
        }
    }

    private static byte GetWifiSignalStrength(ConnectionProfile profile)
    {
        try
        {
            return profile.GetSignalBars() ?? 0;
        }
        catch
        {
            return 0;
        }
    }

    // ── 同步策略查询 ──────────────────────────────────────

    /// <summary>
    /// 检查是否允许同步
    /// </summary>
    public bool CanSync()
    {
        if (!IsConnected || !IsInternetAvailable)
            return false;

        // 按流量计费网络 — 默认不自动同步（可通过设置覆盖）
        if (IsMeteredConnection)
            return false;

        return true;
    }

    /// <summary>
    /// 检查是否允许大文件传输（> 100MB）
    /// </summary>
    public bool CanTransferLargeFiles()
    {
        if (!CanSync()) return false;

        // WiFi 或 Ethernet 才允许大文件
        if (CurrentConnectionType == NetworkConnectionType.Mobile)
            return false;

        return true;
    }

    /// <summary>
    /// 获取建议的并发传输数
    /// </summary>
    public int GetRecommendedConcurrency()
    {
        return CurrentConnectionType switch
        {
            NetworkConnectionType.Ethernet => 8,
            NetworkConnectionType.WiFi => 4,
            NetworkConnectionType.Mobile => 1,
            _ => 1
        };
    }

    public void Dispose()
    {
        NetworkInformation.NetworkStatusChanged -= OnNetworkStatusChanged;
    }
}

// ────────────────────────────────────────────────────────
// 相关类型
// ────────────────────────────────────────────────────────

/// <summary>
/// 网络连接类型
/// </summary>
public enum NetworkConnectionType
{
    None,
    Ethernet,
    WiFi,
    Mobile
}

/// <summary>
/// 网络连接级别
/// </summary>
public enum NetworkConnectivityLevel
{
    None = 0,
    LocalAccess = 1,
    ConstrainedInternetAccess = 2,
    InternetAccess = 3
}

/// <summary>
/// 网络状态快照
/// </summary>
public class NetworkState
{
    public bool IsConnected { get; init; }
    public bool IsInternetAvailable { get; init; }
    public bool IsMeteredConnection { get; init; }
    public NetworkConnectionType ConnectionType { get; init; }
}

/// <summary>
/// 网络状态变化事件参数
/// </summary>
public class NetworkStateChangedEventArgs : EventArgs
{
    public NetworkState PreviousState { get; }
    public NetworkState NewState { get; }

    public NetworkStateChangedEventArgs(NetworkState previous, NetworkState current)
    {
        PreviousState = previous;
        NewState = current;
    }
}