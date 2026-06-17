// ============================================================
// im/index.ts — IM 模块统一导出
// ============================================================

// 类型定义
export * from './types'

// WebSocket 客户端 SDK
export {
  ImWebSocketClient,
  ConnectionState,
  getImClient,
  destroyImClient,
} from './ImWebSocketClient'
export type {
  ImClientConfig,
  MessageHandler,
  StatusHandler,
  ErrorHandler,
} from './ImWebSocketClient'

// HTTP REST API
export * from './imApi'