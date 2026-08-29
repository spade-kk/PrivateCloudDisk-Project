// ============================================================
// messageCache.ts — 消息本地缓存服务（IndexedDB）
// ============================================================
// 企业级消息本地持久化，支持：
// - 消息按会话存储（发送/接收的消息都缓存）
// - 发送失败消息本地保存，方便重试
// - 消息同步状态追踪
// - 离线消息队列
// - 消息自动清理（最多保留 30 天或 10000 条/会话）
//
// 数据库结构：
//   Database: pcd_message_cache
//   ObjectStore: messages (keyPath: [conversationId, messageId])
//   Indexes: byConversation, byStatus, byTimestamp
// ============================================================

import { toRaw } from 'vue'
import type { ChatMessage } from '@/stores/notificationStore'

// ==================== 常量 ====================

const DB_NAME = 'pcd_message_cache'
const DB_VERSION = 1
const STORE_NAME = 'messages'
const MAX_MESSAGES_PER_CONVERSATION = 10000
const MAX_AGE_MS = 30 * 24 * 60 * 60 * 1000 // 30 天

// ==================== 数据库 ====================

let dbPromise: Promise<IDBDatabase> | null = null

function openDB(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise

  dbPromise = new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)

    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, {
          keyPath: ['conversationId', 'id'],
        })
        store.createIndex('byConversation', 'conversationId', { unique: false })
        store.createIndex('byStatus', 'status', { unique: false })
        store.createIndex('byTimestamp', 'created_at', { unique: false })
      }
    }

    request.onsuccess = () => resolve(request.result)
    request.onerror = () => {
      dbPromise = null
      reject(request.error)
    }
  })

  return dbPromise
}

// ==================== 持久化存储结构 ====================

interface CachedMessage {
  conversationId: string
  id: string
  sender: string
  senderName?: string
  senderAvatar?: string
  type: string
  content: string
  file_id?: string
  share_url?: string
  extra?: string
  /** 类型化消息负载；只保存可被 IndexedDB structured clone 接受的纯数据。 */
  payload?: Record<string, unknown>
  created_at: number
  status: string
  serverSeq?: number
  replyTo?: string
  /** 是否为发送失败待重试的消息 */
  isPending?: boolean
  /** 消息同步状态 */
  syncStatus: 'synced' | 'syncing' | 'failed' | 'pending'
  /** 本地缓存时间 */
  cachedAt: number
}

// ==================== 公开 API ====================

/**
 * 保存消息到本地缓存
 */
export async function saveMessage(conversationId: string, message: ChatMessage): Promise<void> {
  try {
    const db = await openDB()
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)

    const cached = toCachedMessage(
      conversationId,
      message,
      message.status === 'sending' ? 'syncing'
        : message.status === 'failed' ? 'failed'
        : message.status === 'local' ? 'pending'
        : 'synced',
    )

    store.put(cached)
    // IDBRequest 的 DataCloneError 可能在 put() 之后通过事务异步报告，必须等待事务完成，
    // 才能让调用方的错误日志准确反映实际写入结果。
    await waitForTransaction(tx)

    // 清理过期消息
    await cleanupOldMessages(conversationId, db)
  } catch (e) {
    console.warn('[MessageCache] 保存消息失败:', e)
  }
}

/**
 * 批量保存消息
 */
export async function saveMessages(conversationId: string, messages: ChatMessage[]): Promise<void> {
  try {
    const db = await openDB()
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)

    for (const msg of messages) {
      const cached = toCachedMessage(conversationId, msg, 'synced')
      store.put(cached)
    }

    await waitForTransaction(tx)
    await cleanupOldMessages(conversationId, db)
  } catch (e) {
    console.warn('[MessageCache] 批量保存失败:', e)
  }
}

/**
 * 加载会话的所有消息
 */
export async function loadMessages(conversationId: string): Promise<ChatMessage[]> {
  try {
    const db = await openDB()
    const tx = db.transaction(STORE_NAME, 'readonly')
    const store = tx.objectStore(STORE_NAME)
    const index = store.index('byConversation')
    const range = IDBKeyRange.only(conversationId)

    return new Promise((resolve, reject) => {
      const request = index.getAll(range)
      request.onsuccess = () => {
        const messages = (request.result as CachedMessage[])
          .sort((a, b) => a.created_at - b.created_at)
          .map(toChatMessage)
        resolve(messages)
      }
      request.onerror = () => reject(request.error)
    })
  } catch (e) {
    console.warn('[MessageCache] 加载消息失败:', e)
    return []
  }
}

/**
 * 获取待重试的失败消息
 */
export async function getPendingMessages(conversationId: string): Promise<ChatMessage[]> {
  try {
    const db = await openDB()
    const tx = db.transaction(STORE_NAME, 'readonly')
    const store = tx.objectStore(STORE_NAME)
    const index = store.index('byConversation')
    const range = IDBKeyRange.only(conversationId)

    return new Promise((resolve, reject) => {
      const request = index.getAll(range)
      request.onsuccess = () => {
        const messages = (request.result as CachedMessage[])
          .filter(m => m.status === 'failed' || m.status === 'local' || m.syncStatus === 'pending')
          .sort((a, b) => a.created_at - b.created_at)
          .map(toChatMessage)
        resolve(messages)
      }
      request.onerror = () => reject(request.error)
    })
  } catch (e) {
    console.warn('[MessageCache] 获取待重试消息失败:', e)
    return []
  }
}

/**
 * 更新消息状态
 */
export async function updateMessageStatus(
  conversationId: string,
  messageId: string,
  status: string,
  syncStatus?: 'synced' | 'syncing' | 'failed' | 'pending',
): Promise<void> {
  try {
    const db = await openDB()
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)

    const getRequest = store.get([conversationId, messageId])
    getRequest.onsuccess = () => {
      const cached = getRequest.result as CachedMessage | undefined
      if (cached) {
        cached.status = status
        if (syncStatus) cached.syncStatus = syncStatus
        store.put(cached)
      }
    }
  } catch (e) {
    console.warn('[MessageCache] 更新消息状态失败:', e)
  }
}

/**
 * 删除单条消息
 */
export async function deleteMessage(conversationId: string, messageId: string): Promise<void> {
  try {
    const db = await openDB()
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    store.delete([conversationId, messageId])
  } catch (e) {
    console.warn('[MessageCache] 删除消息失败:', e)
  }
}

/**
 * 删除会话的所有消息
 */
export async function deleteConversationMessages(conversationId: string): Promise<void> {
  try {
    const db = await openDB()
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    const index = store.index('byConversation')
    const range = IDBKeyRange.only(conversationId)

    const request = index.openCursor(range)
    request.onsuccess = () => {
      const cursor = request.result
      if (cursor) {
        cursor.delete()
        cursor.continue()
      }
    }
  } catch (e) {
    console.warn('[MessageCache] 删除会话消息失败:', e)
  }
}

/**
 * 获取所有会话的未发送/失败消息（用于启动时重试）
 */
export async function getAllUnsentMessages(): Promise<CachedMessage[]> {
  try {
    const db = await openDB()
    const tx = db.transaction(STORE_NAME, 'readonly')
    const store = tx.objectStore(STORE_NAME)

    return new Promise((resolve, reject) => {
      const request = store.getAll()
      request.onsuccess = () => {
        const messages = (request.result as CachedMessage[])
          .filter(m => m.status === 'failed' || m.status === 'local' || m.syncStatus !== 'synced')
        resolve(messages)
      }
      request.onerror = () => reject(request.error)
    })
  } catch (e) {
    console.warn('[MessageCache] 获取未发送消息失败:', e)
    return []
  }
}

// ==================== 辅助函数 ====================

function toChatMessage(cached: CachedMessage): ChatMessage {
  return {
    id: cached.id,
    sender: cached.sender,
    senderName: cached.senderName,
    senderAvatar: cached.senderAvatar,
    type: cached.type,
    content: cached.content,
    file_id: cached.file_id,
    share_url: cached.share_url,
    extra: cached.extra,
    payload: cached.payload,
    created_at: cached.created_at,
    status: cached.status,
    serverSeq: cached.serverSeq,
    replyTo: cached.replyTo,
  }
}

/**
 * 将消息转换为 IndexedDB 的稳定数据结构。
 *
 * AUDIT FIX [IM-CACHE-20260810]：旧实现通过 `{ ...message }` 直接持久化 Vue 响应式消息，
 * 其中可能包含 Proxy、protobuf 负载对象或其他非 structured-clone 类型，批量 put() 会抛出
 * DataCloneError。新行为只复制明确字段，并递归转换 payload；不影响消息在线收发，只约束本地缓存
 * 的持久化边界，避免缓存异常反向影响消息中心。
 */
function toCachedMessage(
  conversationId: string,
  message: ChatMessage,
  syncStatus: CachedMessage['syncStatus'],
): CachedMessage {
  const source = toRaw(message) as ChatMessage
  const payload = cloneForIndexedDb(source.payload)
  const cached: CachedMessage = {
    conversationId: String(conversationId),
    id: String(source.id),
    sender: String(source.sender || ''),
    senderName: source.senderName,
    senderAvatar: source.senderAvatar,
    type: String(source.type || 'text'),
    content: String(source.content || ''),
    file_id: source.file_id,
    share_url: source.share_url,
    extra: source.extra,
    created_at: Number.isFinite(Number(source.created_at)) ? Number(source.created_at) : Date.now(),
    status: String(source.status || 'sent'),
    serverSeq: source.serverSeq,
    replyTo: source.replyTo,
    syncStatus,
    cachedAt: Date.now(),
  }

  if (isPlainRecord(payload)) {
    cached.payload = payload
  }

  return cached
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  if (value === null || typeof value !== 'object') return false
  const prototype = Object.getPrototypeOf(value)
  return prototype === Object.prototype || prototype === null
}

/**
 * 递归生成 structured-clone 安全值。
 * 业务消息负载目前是 JSON/Protobuf 风格对象；对未知 class 实例和函数采取丢弃策略，
 * 防止某个扩展字段使整批消息缓存失败。Uint8Array 保留，用于图片/文件等二进制元数据。
 */
function cloneForIndexedDb(value: unknown, seen = new WeakSet<object>()): unknown {
  if (value === null || typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return value
  }
  if (value === undefined || typeof value === 'function' || typeof value === 'symbol') {
    return undefined
  }

  const raw = toRaw(value as object) as unknown
  if (raw === null || typeof raw !== 'object') return raw
  if (raw instanceof Date) return new Date(raw.getTime())
  if (raw instanceof Uint8Array) return new Uint8Array(raw)
  if (raw instanceof ArrayBuffer) return raw.slice(0)
  if (typeof Blob !== 'undefined' && raw instanceof Blob) return raw
  if (Array.isArray(raw)) {
    if (seen.has(raw)) return undefined
    seen.add(raw)
    const result = raw.map(item => cloneForIndexedDb(item, seen))
    seen.delete(raw)
    return result
  }

  if (!isPlainRecord(raw)) return undefined
  if (seen.has(raw)) return undefined
  seen.add(raw)
  const result: Record<string, unknown> = {}
  for (const [key, item] of Object.entries(raw)) {
    const cloned = cloneForIndexedDb(item, seen)
    if (cloned !== undefined) result[key] = cloned
  }
  seen.delete(raw)
  return result
}

function waitForTransaction(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error || new Error('IndexedDB transaction failed'))
    tx.onabort = () => reject(tx.error || new Error('IndexedDB transaction aborted'))
  })
}

async function cleanupOldMessages(conversationId: string, db: IDBDatabase): Promise<void> {
  const tx = db.transaction(STORE_NAME, 'readwrite')
  const store = tx.objectStore(STORE_NAME)
  const index = store.index('byConversation')
  const range = IDBKeyRange.only(conversationId)

  const request = index.getAll(range)
  request.onsuccess = () => {
    const messages = request.result as CachedMessage[]
    const now = Date.now()

    // 按时间排序
    const sorted = messages.sort((a, b) => a.created_at - b.created_at)

    // 删除过期消息
    const expired = sorted.filter(m => now - m.created_at > MAX_AGE_MS)
    for (const msg of expired) {
      store.delete([conversationId, msg.id])
    }

    // 超出数量限制，删除最旧的消息
    const remaining = sorted.filter(m => now - m.created_at <= MAX_AGE_MS)
    if (remaining.length > MAX_MESSAGES_PER_CONVERSATION) {
      const toDelete = remaining.slice(0, remaining.length - MAX_MESSAGES_PER_CONVERSATION)
      for (const msg of toDelete) {
        store.delete([conversationId, msg.id])
      }
    }
  }
}
