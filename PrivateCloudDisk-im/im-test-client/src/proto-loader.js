// ============================================================
// proto-loader.js — Protobuf 消息类加载器
// ============================================================
// 使用 protobufjs 加载 .proto 文件，提供延迟初始化的消息类型。
// 与前端 protoSchema.ts 对齐，确保编解码逻辑一致。
// ============================================================

import protobuf from 'protobufjs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

/** proto 文件路径 */
const PROTO_PATH = resolve(__dirname, '../proto/im_protocol_v2.proto')

/** 已加载的 Root 实例（延迟初始化） */
let root = null

/** 消息类型缓存 */
const typeCache = new Map()

/**
 * 加载 .proto 文件并返回 Root 实例
 * 首次调用时同步加载，后续调用返回缓存
 * @returns {protobuf.Root} protobufjs Root 实例
 */
function getRoot() {
  if (!root) {
    root = protobuf.loadSync(PROTO_PATH)
  }
  return root
}

/**
 * 获取指定消息类型（带缓存）
 * @param {string} typeName - 消息类型名（如 'IMEnvelope', 'TextPayload'）
 * @returns {protobuf.Type} protobufjs Type 实例
 */
export function getMessageType(typeName) {
  if (!typeCache.has(typeName)) {
    const type = getRoot().lookupType(`pcd.im.v2.${typeName}`)
    if (!type) {
      throw new Error(`Protobuf 消息类型未找到: ${typeName}`)
    }
    typeCache.set(typeName, type)
  }
  return typeCache.get(typeName)
}

/**
 * 序列化消息对象为 protobuf 字节
 * @param {string} typeName - 消息类型名
 * @param {object} message - 消息对象（纯 JS 对象）
 * @returns {Uint8Array} 序列化后的字节
 */
export function encodeMessage(typeName, message) {
  const Type = getMessageType(typeName)
  const errMsg = Type.verify(message)
  if (errMsg) {
    throw new Error(`${typeName} 校验失败: ${errMsg}`)
  }
  const instance = Type.create(message)
  return Type.encode(instance).finish()
}

/**
 * 反序列化 protobuf 字节为消息对象
 * @param {string} typeName - 消息类型名
 * @param {Uint8Array} bytes - protobuf 字节
 * @returns {object} 反序列化后的纯 JS 对象
 */
export function decodeMessage(typeName, bytes) {
  const Type = getMessageType(typeName)
  const message = Type.decode(bytes)
  return Type.toObject(message, {
    longs: Number,
    enums: Number,
    bytes: Uint8Array,
    defaults: true,
  })
}

// ==================== 枚举值常量 ====================
// 与后端 im_protocol_v2.proto 对齐，便于代码中引用

export const CommandType = {
  LOGIN: 101,
  LOGOUT: 102,
  HEARTBEAT: 103,
  SEND_MESSAGE: 201,
  RECALL_MESSAGE: 203,
  READ_MESSAGE: 204,
  TYPING: 205,
  CREATE_CONVERSATION: 301,
  GET_CONVERSATIONS: 302,
  DELETE_CONVERSATION: 303,
  TOP_CONVERSATION: 304,
  GET_HISTORY: 305,
  CREATE_GROUP: 401,
  JOIN_GROUP: 402,
  LEAVE_GROUP: 403,
  KICK_MEMBER: 404,
  MUTE_MEMBER: 405,
  DISSOLVE_GROUP: 406,
  GET_GROUP_MEMBERS: 407,
  SYSTEM_NOTIFY: 901,
  ERROR_NOTIFY: 902,
  SYNC_OFFLINE: 1001,
}

export const MessageType = {
  TEXT: 1,
  IMAGE: 2,
  FILE: 3,
  VOICE: 4,
  VIDEO: 5,
  STICKER: 10,
  LOCATION: 11,
  REPLY: 12,
  SYSTEM_NOTICE: 50,
  READ_RECEIPT: 51,
  MSG_TYPING: 52,
  MSG_HEARTBEAT: 90,
  ERROR: 92,
  RECEIPT: 93,
  CUSTOM: 100,
}

export const ConversationType = {
  PRIVATE: 1,
  GROUP: 2,
  SYSTEM: 3,
  BROADCAST: 4,
}

export const MessageStatus = {
  SENDING: 1,
  SENT: 2,
  DELIVERED: 3,
  READ: 4,
  FAILED: 5,
  RECALLED: 6,
}

export const DeviceType = {
  DESKTOP: 0,
  MOBILE: 1,
  WEB: 2,
  SERVER: 3,
  IOT: 4,
}

/**
 * 消息类型 → Payload 类型名映射
 * 用于根据 envelope.messageType 选择正确的 Payload 解码类型
 */
export const PAYLOAD_TYPE_MAP = {
  [MessageType.TEXT]: 'TextPayload',
  [MessageType.IMAGE]: 'ImagePayload',
  [MessageType.FILE]: 'FilePayload',
  [MessageType.SYSTEM_NOTICE]: 'SystemPayload',
  [MessageType.READ_RECEIPT]: 'ReadReceiptPayload',
  [MessageType.MSG_TYPING]: 'TypingPayload',
  [MessageType.RECEIPT]: 'ReceiptPayload',
  [MessageType.CUSTOM]: 'CustomPayload',
}

/**
 * 消息类型编号 → Codec TypeName 映射
 *
 * <p>与后端 MessageTypeDispatcher.PayloadCodec.typeName 对齐，
 * 用于 Layer 2 密钥派生：HKDF(sessionKey, "pcd-im-v2-derived:" + typeName)</p>
 */
export const CODEC_TYPE_NAME_MAP = {
  [MessageType.TEXT]: 'TEXT',
  [MessageType.IMAGE]: 'IMAGE',
  [MessageType.FILE]: 'FILE',
  [MessageType.VOICE]: 'VOICE',
  [MessageType.VIDEO]: 'VIDEO',
  [MessageType.STICKER]: 'STICKER',
  [MessageType.LOCATION]: 'LOCATION',
  [MessageType.REPLY]: 'REPLY',
  [MessageType.SYSTEM_NOTICE]: 'SYSTEM_NOTICE',
  [MessageType.READ_RECEIPT]: 'READ_RECEIPT',
  [MessageType.MSG_TYPING]: 'TYPING',
  [MessageType.ERROR]: 'ERROR',
  [MessageType.RECEIPT]: 'RECEIPT',
  [MessageType.CUSTOM]: 'CUSTOM',
}

/**
 * 根据消息类型获取 Payload 类型名
 * @param {number} messageType - IMMessageType 枚举值
 * @returns {string|null} Payload 类型名，未知类型返回 null
 */
export function getPayloadTypeName(messageType) {
  return PAYLOAD_TYPE_MAP[messageType] || null
}
