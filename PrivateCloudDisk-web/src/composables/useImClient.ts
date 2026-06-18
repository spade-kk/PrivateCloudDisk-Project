// ============================================================
// useImClient.ts — IM 客户端组合式函数
// ============================================================
// 提供 IM 客户端单例，在多个 composable 之间共享同一个连接。
// 与 notificationStore 中的 ImWebSocketClient 共享同一个实例。
// ============================================================

import { ref, type Ref } from 'vue'
import { getImClient, type ImWebSocketClient } from '@/api/im/ImWebSocketClient'

/** 全局 IM 客户端引用（单例） */
let globalClient: ImWebSocketClient | null = null

export function useImClient() {
  const client: Ref<ImWebSocketClient | null> = ref(globalClient)

  /**
   * 设置 IM 客户端实例（在 notificationStore 初始化后调用）
   */
  function setClient(c: ImWebSocketClient | null): void {
    globalClient = c
    client.value = c
  }

  return { client, setClient }
}