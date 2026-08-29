// ============================================================
// useImClient.ts — IM 客户端组合式函数
// ============================================================
// 提供 IM 客户端单例，在多个 composable 之间共享同一个连接。
// 与 notificationStore 中的 ImWebSocketClient 共享同一个实例。
// ============================================================

import { shallowRef, type Ref } from 'vue'
import { getImClient, type ImWebSocketClient } from '@/api/im/ImWebSocketClient'

/** 全局 IM 客户端引用（单例） */
let globalClient: ImWebSocketClient | null = null

export function useImClient() {
  // 注意：使用 shallowRef 而非 ref，避免 Vue 对类实例进行深度响应式解包
  // （ref 会对类实例的 readonly 属性产生 UnwrapRef 类型不匹配问题）
  const client: Ref<ImWebSocketClient | null> = shallowRef(globalClient)

  /**
   * 设置 IM 客户端实例（在 notificationStore 初始化后调用）
   */
  function setClient(c: ImWebSocketClient | null): void {
    globalClient = c
    client.value = c
  }

  return { client, setClient }
}