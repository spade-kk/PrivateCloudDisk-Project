/**
 * composables/useNetworkStatus.js - 网络状态监听组合式函数
 *
 * 核心能力:
 * - 实时监听网络状态变化 (WiFi/4G/5G/无网络)
 * - 网络类型检测
 * - 弱网提示
 * - 网络恢复回调
 */
import { ref, onMounted, onUnmounted } from 'vue'

export function useNetworkStatus() {
  const isConnected = ref(true)
  const networkType = ref('unknown') // wifi / 4g / 5g / 3g / 2g / none
  const isWeakNetwork = ref(false)
  const lastChangeTime = ref(0)

  // 回调列表
  const onlineCallbacks = []
  const offlineCallbacks = []

  /**
   * 获取当前网络状态
   */
  function getCurrentStatus() {
    uni.getNetworkType({
      success(res) {
        networkType.value = res.networkType
        isConnected.value = res.networkType !== 'none'
        isWeakNetwork.value = ['2g', '3g'].includes(res.networkType)
      }
    })
  }

  /**
   * 注册网络恢复回调
   */
  function onOnline(fn) {
    onlineCallbacks.push(fn)
    return () => {
      const idx = onlineCallbacks.indexOf(fn)
      if (idx > -1) onlineCallbacks.splice(idx, 1)
    }
  }

  /**
   * 注册断网回调
   */
  function onOffline(fn) {
    offlineCallbacks.push(fn)
    return () => {
      const idx = offlineCallbacks.indexOf(fn)
      if (idx > -1) offlineCallbacks.splice(idx, 1)
    }
  }

  // 网络状态变化监听
  function handleNetworkChange(res) {
    const prevConnected = isConnected.value
    networkType.value = res.networkType
    isConnected.value = res.isConnected
    isWeakNetwork.value = ['2g', '3g'].includes(res.networkType)
    lastChangeTime.value = Date.now()

    if (isConnected.value && !prevConnected) {
      onlineCallbacks.forEach((fn) => fn(res))
    } else if (!isConnected.value && prevConnected) {
      offlineCallbacks.forEach((fn) => fn(res))
    }
  }

  onMounted(() => {
    getCurrentStatus()
    uni.onNetworkStatusChange(handleNetworkChange)
  })

  onUnmounted(() => {
    uni.offNetworkStatusChange(handleNetworkChange)
  })

  return {
    isConnected,
    networkType,
    isWeakNetwork,
    lastChangeTime,
    getCurrentStatus,
    onOnline,
    onOffline
  }
}