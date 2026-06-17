// ============================================================
// im/types.ts — IM 协议类型定义
// ============================================================
// 与后端 im-common 模块协议完全对齐的 TypeScript 类型定义。
// 包含枚举、DTO、通信协议、事件对象等所有类型。
//
// 后端对应包：
//   org.project.im.common.enums.*
//   org.project.im.common.dto.*
//   org.project.im.common.protocol.MessageProtocol
//   org.project.im.common.event.*
//   org.project.im.common.constant.ImConstants
// ============================================================

// ==================== 枚举 ====================

/** 消息类型 */
export enum MessageType {
  TEXT = 1,
  IMAGE = 2,
  FILE = 3,
  VOICE = 4,
  VIDEO = 5,
  LOCATION = 6,
  SYSTEM_NOTICE = 7,
  CUSTOM = 8,
  REPLY = 9,
  READ_RECEIPT = 10,
  TYPING = 11,
  HEARTBEAT = 99,
}

/** 消息状态 */
export enum MessageStatus {
  SENDING = 0,
  SENT = 1,
  DELIVERED = 2,
  READ = 3,
  FAILED = 4,
  RECALLED = 5,
  DELETED = 6,
}

/** 会话类型 */
export enum ConversationType {
  PRIVATE = 1,
  GROUP = 2,
  SYSTEM = 3,
}

/** 群组角色 */
export enum GroupRole {
  OWNER = 1,
  ADMIN = 2,
  MEMBER = 3,
  MUTED = 4,
}

/** 命令类型（WebSocket 协议命令字） */
export enum CommandType {
  // 连接管理
  LOGIN = 101,
  LOGOUT = 102,
  HEARTBEAT = 103,

  // 消息收发
  SEND_MESSAGE = 201,
  MESSAGE_ACK = 202,
  RECALL_MESSAGE = 203,
  READ_MESSAGE = 204,
  TYPING = 205,

  // 会话管理
  CREATE_CONVERSATION = 301,
  GET_CONVERSATIONS = 302,
  DELETE_CONVERSATION = 303,
  TOP_CONVERSATION = 304,
  GET_HISTORY = 305,

  // 群组管理
  CREATE_GROUP = 401,
  JOIN_GROUP = 402,
  LEAVE_GROUP = 403,
  KICK_MEMBER = 404,
  MUTE_MEMBER = 405,
  DISSOLVE_GROUP = 406,
  GET_GROUP_MEMBERS = 407,

  // 系统通知
  SYSTEM_NOTIFY = 901,
  ERROR_NOTIFY = 902,

  // 离线同步
  SYNC_OFFLINE_MESSAGES = 1001,
}

/** 响应码 */
export enum ResponseCode {
  SUCCESS = 200,
  BAD_REQUEST = 400,
  UNAUTHORIZED = 401,
  FORBIDDEN = 403,
  NOT_FOUND = 404,
  CONFLICT = 409,
  TOO_MANY_REQUESTS = 429,
  INTERNAL_ERROR = 500,
  SERVICE_UNAVAILABLE = 503,

  USER_NOT_FOUND = 1001,
  USER_OFFLINE = 1002,
  GROUP_NOT_FOUND = 1003,
  NOT_GROUP_MEMBER = 1004,
  ALREADY_GROUP_MEMBER = 1005,
  GROUP_FULL = 1006,
  NO_PERMISSION = 1007,
  MESSAGE_TOO_LONG = 1008,
  RECALL_TIMEOUT = 1009,
  MUTED_IN_GROUP = 1010,
  CONVERSATION_NOT_FOUND = 1011,
  DUPLICATE_MESSAGE = 1012,

  CONNECTION_LIMIT_EXCEEDED = 2001,
  TOKEN_EXPIRED = 2002,
  TOKEN_INVALID = 2003,
  KICKED_OUT = 2004,
  PROTOCOL_ERROR = 2005,
}

// ==================== DTO ====================

/** 消息 DTO */
export interface MessageDTO {
  messageId?: string
  conversationId: string
  conversationType: ConversationType
  messageType: MessageType
  senderId: string
  senderName?: string
  senderAvatar?: string
  receiverId: string
  content: string
  extra?: string
  status?: MessageStatus
  clientSeq?: number
  serverSeq?: number
  replyTo?: string
  sendTime?: string
  createTime?: string
  updateTime?: string
}

/** 会话 DTO */
export interface ConversationDTO {
  conversationId: string
  conversationType: ConversationType
  conversationName?: string
  avatar?: string
  userId: string
  targetId: string
  lastMessage?: string
  lastMessageType?: MessageType
  lastMessageTime?: string
  unreadCount: number
  isTop: boolean
  isMuted: boolean
  totalMessages?: number
  status?: number
  createTime?: string
  updateTime?: string
}

/** 群组 DTO */
export interface GroupDTO {
  groupId: string
  groupName: string
  avatar?: string
  ownerId: string
  ownerName?: string
  announcement?: string
  description?: string
  memberCount: number
  maxMembers: number
  joinMode: number
  isAllMuted: boolean
  status: number
  createTime?: string
  updateTime?: string
}

/** 群组成员 DTO */
export interface GroupMemberDTO {
  id?: number
  groupId: string
  userId: string
  nickname?: string
  avatar?: string
  role: GroupRole
  alias?: string
  muteUntil?: string
  lastReadSeq?: number
  joinTime?: string
}

// ==================== 通信协议 ====================

/** WebSocket 通信协议（顶层消息结构） */
export interface MessageProtocol {
  /** 协议版本 */
  version: number
  /** 命令类型 */
  command: CommandType
  /** 客户端消息序列号（请求-响应匹配） */
  seq?: number
  /** 时间戳（毫秒） */
  timestamp: number
  /** 发送者 ID */
  senderId?: string
  /** 接收者 ID */
  receiverId?: string
  /** 消息体 */
  payload?: unknown
  /** 扩展字段 */
  extra?: Record<string, unknown>
}

// ==================== 事件对象 ====================

/** 消息事件 */
export interface MessageEvent {
  eventId: string
  eventType: 'SEND' | 'RECALL' | 'READ' | 'DELETE'
  messageId: string
  conversationId: string
  conversationType: ConversationType
  messageType: MessageType
  senderId: string
  receiverId: string
  content: string
  serverSeq: number
  eventTime: string
}

/** 用户在线状态变更事件 */
export interface UserOnlineEvent {
  userId: string
  eventType: 'ONLINE' | 'OFFLINE' | 'KICKED'
  clientType: 'WEB' | 'ANDROID' | 'IOS' | 'DESKTOP'
  clientIp?: string
  channelId: string
  eventTime: string
}

// ==================== 统一响应 ====================

/** 统一响应结果 */
export interface Result<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

// ==================== 协议常量 ====================

/** 协议版本 */
export const PROTOCOL_VERSION = 1

/** 协议魔数 */
export const PROTOCOL_MAGIC = 0xabcdef01

/** 心跳间隔（毫秒） */
export const HEARTBEAT_INTERVAL = 30000

/** 心跳超时（毫秒） */
export const HEARTBEAT_TIMEOUT = 90000

/** 重连基础间隔（毫秒） */
export const RECONNECT_BASE_INTERVAL = 1000

/** 最大重连间隔（毫秒） */
export const RECONNECT_MAX_INTERVAL = 30000

/** 最大重连次数 */
export const MAX_RECONNECT_ATTEMPTS = 10

/** 单条消息最大长度 */
export const MAX_MESSAGE_LENGTH = 5000

/** 消息撤回时限（秒） */
export const RECALL_TIMEOUT_SECONDS = 120

/** 单次拉取历史消息最大条数 */
export const MAX_HISTORY_SIZE = 100

/** 默认分页大小 */
export const DEFAULT_PAGE_SIZE = 20

// ==================== 类型守卫 ====================

/** 判断是否为 MessageProtocol */
export function isMessageProtocol(obj: unknown): obj is MessageProtocol {
  return (
    typeof obj === 'object' &&
    obj !== null &&
    'version' in obj &&
    'command' in obj &&
    'timestamp' in obj
  )
}

/** 判断是否为 Result */
export function isResult<T>(obj: unknown): obj is Result<T> {
  return (
    typeof obj === 'object' &&
    obj !== null &&
    'code' in obj &&
    'message' in obj &&
    'timestamp' in obj
  )
}