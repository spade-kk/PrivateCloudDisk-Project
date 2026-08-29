// ============================================================
// protocol/protoSchema.ts — Protobuf Schema 加载与消息类型注册
// ============================================================
// 加载 im_protocol_v2.proto 并通过 protobufjs 解析为 Root，
// 导出各消息类型供协议编解码器使用。
//
// 设计要点：
//   - 使用 Vite `?raw` 后缀导入 .proto 文件为字符串（构建期内联）
//   - 使用 protobufjs/minimal 减小 bundle 体积（旧实现；仅适合已生成代码，不能解析 .proto）
//   - 使用完整 protobufjs 提供 Root/parse 运行时能力（IM-PROTO-20260810 修复后的实现）
//   - 解析只执行一次（模块级单例）
//   - 失败时抛出致命错误，因为协议层无法工作
// ============================================================

// AUDIT FIX [IM-PROTO-20260810]：旧实现从 protobufjs/minimal 导入后调用 Root/parse，
// 但 minimal 构建不包含 schema parser，导致“protobuf.Root is not a constructor”，进而心跳编码
// 与所有二进制消息解码一起失败。新行为改用完整 protobufjs，与 im-test-client 的 proto-loader.js
// 保持一致；仅修复 schema 初始化方式，不改变 IM v2 wire format 或消息编号。
import protobuf, { type Root, type Type } from 'protobufjs'
// Vite ?raw 导入：将 .proto 文件内容作为字符串内联到 bundle 中
import protoSchemaText from '../proto/im_protocol_v2.proto?raw'

// ============================================================
// 协议包名常量
// ============================================================
const PACKAGE_NAME = 'pcd.im.v2'

// ============================================================
// 单例 Root：解析 .proto schema
// ============================================================
let _root: Root | null = null

/**
 * 获取 protobufjs Root 单例
 *
 * <p>使用懒加载模式，首次调用时解析 .proto schema 字符串。
 * 解析失败会直接抛出异常，因为协议层无法继续工作。</p>
 *
 * @returns protobufjs Root 实例
 * @throws Error 如果 .proto schema 解析失败
 */
export function getRoot(): Root {
  if (_root !== null) return _root
  const root = new protobuf.Root()
  try {
    protobuf.parse(protoSchemaText, root)
  } catch (e) {
    throw new Error(`[IM Proto] 解析 .proto schema 失败: ${(e as Error).message}`)
  }
  _root = root
  return root
}

/**
 * 查找指定消息类型
 *
 * @param typeName 消息类型简名（不含包名前缀），如 'IMEnvelope'
 * @returns protobufjs Type 实例
 */
export function lookupType(typeName: string): Type {
  return getRoot().lookupType(`${PACKAGE_NAME}.${typeName}`)
}

// ============================================================
// 常用消息类型导出（懒加载 getter）
// ============================================================

/** IMEnvelope — 协议外层信封 */
export const IMEnvelopeType = (): Type => lookupType('IMEnvelope')

/** TextPayload — 文本消息负载 */
export const TextPayloadType = (): Type => lookupType('TextPayload')

/** ImagePayload — 图片消息负载 */
export const ImagePayloadType = (): Type => lookupType('ImagePayload')

/** FilePayload — 文件消息负载 */
export const FilePayloadType = (): Type => lookupType('FilePayload')

/** VoicePayload — 语音消息负载 */
export const VoicePayloadType = (): Type => lookupType('VoicePayload')

/** VideoPayload — 视频消息负载 */
export const VideoPayloadType = (): Type => lookupType('VideoPayload')

/** StickerPayload — 表情包消息负载 */
export const StickerPayloadType = (): Type => lookupType('StickerPayload')

/** LocationPayload — 位置消息负载 */
export const LocationPayloadType = (): Type => lookupType('LocationPayload')

/** ReplyPayload — 引用/回复消息负载 */
export const ReplyPayloadType = (): Type => lookupType('ReplyPayload')

/** CallPayload — 通话/信令负载 */
export const CallPayloadType = (): Type => lookupType('CallPayload')

/** WebRTCSignalingPayload — WebRTC 信令负载 */
export const WebRTCSignalingPayloadType = (): Type => lookupType('WebRTCSignalingPayload')

/** SystemPayload — 系统通知负载 */
export const SystemPayloadType = (): Type => lookupType('SystemPayload')

/** ReadReceiptPayload — 已读回执负载 */
export const ReadReceiptPayloadType = (): Type => lookupType('ReadReceiptPayload')

/** ReceiptPayload — 推送回执负载 */
export const ReceiptPayloadType = (): Type => lookupType('ReceiptPayload')

/** TypingPayload — 正在输入负载 */
export const TypingPayloadType = (): Type => lookupType('TypingPayload')

/** CustomPayload — 自定义扩展消息负载 */
export const CustomPayloadType = (): Type => lookupType('CustomPayload')

/** EncryptionMeta — 加密元数据 */
export const EncryptionMetaType = (): Type => lookupType('EncryptionMeta')

/** KeyExchangeRequest — 密钥交换请求 */
export const KeyExchangeRequestType = (): Type => lookupType('KeyExchangeRequest')

/** KeyExchangeResponse — 密钥交换响应 */
export const KeyExchangeResponseType = (): Type => lookupType('KeyExchangeResponse')

/** AuthPayload — 认证负载 */
export const AuthPayloadType = (): Type => lookupType('AuthPayload')

// ============================================================
// 消息类型枚举（与 .proto 定义对齐）
// ============================================================

/**
 * IM 消息类型枚举
 *
 * <p>与后端 IMMessageType 枚举值保持一致，用于 IMEnvelope.message_type 字段。</p>
 */
export const IMMessageType = {
  UNSPECIFIED: 0,
  TEXT: 1,
  IMAGE: 2,
  FILE: 3,
  VOICE: 4,
  VIDEO: 5,
  STICKER: 10,
  LOCATION: 11,
  REPLY: 12,
  VOICE_CALL: 13,
  VIDEO_CALL: 14,
  SYSTEM_NOTICE: 50,
  READ_RECEIPT: 51,
  MSG_TYPING: 52,
  MSG_HEARTBEAT: 90,
  ERROR: 92,
  RECEIPT: 93,
  CUSTOM: 100,
} as const

/**
 * 推送回执状态枚举
 *
 * <p>与后端 ReceiptStatus 枚举值保持一致，用于 ReceiptPayload.status 字段。</p>
 */
export const ReceiptStatus = {
  RECEIPT_STATUS_UNSPECIFIED: 0,
  RECEIPT_DELIVERED: 1,
  RECEIPT_PUSH_FAILED: 2,
  RECEIPT_SEND_FAILED: 3,
} as const

/**
 * IM 命令类型枚举
 *
 * <p>与后端 IMCommandType 枚举值保持一致，用于 IMEnvelope.command 字段。</p>
 */
export const IMCommandType = {
  COMMAND_UNSPECIFIED: 0,
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
  CALL_INVITE: 2001,
  CALL_ACCEPT: 2002,
  CALL_REJECT: 2003,
  CALL_CANCEL: 2004,
  CALL_HANGUP: 2005,
  CALL_BUSY: 2006,
  CALL_TIMEOUT: 2007,
  SIGNALING_OFFER: 2101,
  SIGNALING_ANSWER: 2102,
  SIGNALING_ICE: 2103,
  CALL_QUALITY_REPORT: 2201,
  CALL_SCREEN_SHARE_START: 2301,
  CALL_SCREEN_SHARE_STOP: 2302,
  CALL_MUTE_TOGGLE: 2303,
  CALL_CAMERA_TOGGLE: 2304,
  CALL_SWITCH_TO_VOICE: 2305,
  CALL_SWITCH_TO_VIDEO: 2306,
  CALL_ROOM_CREATE: 2401,
  CALL_ROOM_JOIN: 2402,
  CALL_ROOM_LEAVE: 2403,
  CALL_ICE_SERVERS: 2501,
} as const

/**
 * IM 会话类型枚举
 */
export const IMConversationType = {
  CONVERSATION_UNSPECIFIED: 0,
  PRIVATE: 1,
  GROUP: 2,
  SYSTEM: 3,
  BROADCAST: 4,
} as const

/**
 * IM 消息状态枚举
 */
export const IMMessageStatus = {
  STATUS_UNSPECIFIED: 0,
  SENDING: 1,
  SENT: 2,
  DELIVERED: 3,
  READ: 4,
  FAILED: 5,
  RECALLED: 6,
} as const

/**
 * 设备类型枚举
 */
export const DeviceType = {
  DESKTOP: 0,
  MOBILE: 1,
  WEB: 2,
  SERVER: 3,
  IOT: 4,
} as const

// ============================================================
// TypeScript 类型定义（基于 protobufjs Message）
// ============================================================

/** protobufjs Message 实例类型 */
export type PBMessage = { [k: string]: unknown }

/** IMEnvelope 消息实例类型 */
export type IMEnvelopeMessage = PBMessage & {
  version?: number
  messageId?: string
  command?: number
  messageType?: number
  senderId?: string
  receiverId?: string
  conversationId?: string
  conversationType?: number
  senderName?: string
  senderAvatar?: string
  seq?: number
  serverSeq?: number
  status?: number
  timestamp?: number
  innerEncryption?: PBMessage
  encryptedPayload?: Uint8Array
  replyTo?: string
  expireAt?: number
  extraJson?: string
  senderDeviceType?: number
  senderPlatform?: string
  traceId?: string
  priority?: number
}
