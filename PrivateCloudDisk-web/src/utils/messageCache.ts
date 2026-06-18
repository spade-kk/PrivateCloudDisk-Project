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

    const cached: CachedMessage = {
      ...message,
      conversationId,
      syncStatus: message.status === 'sending' ? 'syncing'
        : message.status === 'failed' ? 'failed'
        : message.status === 'local' ? 'pending'
        : 'synced',
      cachedAt: Date.now(),
    }

    store.put(cached)

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
      const cached: CachedMessage = {
        ...msg,
        conversationId,
        syncStatus: 'synced',
        cachedAt: Date.now(),
      }
      store.put(cached)
    }

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
    created_at: cached.created_at,
    status: cached.status,
    serverSeq: cached.serverSeq,
    replyTo: cached.replyTo,
  }
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