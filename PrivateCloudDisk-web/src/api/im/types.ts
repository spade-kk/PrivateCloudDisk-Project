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

// ============================================================
// WebRTC 视频通话信令类型
// ============================================================

/** 通话类型 */
export enum CallType {
  /** 语音通话 */
  VOICE = 1,
  /** 视频通话 */
  VIDEO = 2,
}

/** 通话状态 */
export enum CallStatus {
  /** 等待接听 */
  RINGING = 0,
  /** 通话中 */
  ACTIVE = 1,
  /** 已拒绝 */
  REJECTED = 2,
  /** 已取消 */
  CANCELLED = 3,
  /** 已挂断 */
  ENDED = 4,
  /** 超时 */
  TIMEOUT = 5,
  /** 忙线 */
  BUSY = 6,
}

/** 通话模式 */
export enum CallMode {
  /** P2P 一对一通话 */
  P2P = 1,
  /** 群组通话 */
  GROUP = 2,
}

/** 网络质量等级 */
export enum NetworkQuality {
  /** 优秀 */
  EXCELLENT = 0,
  /** 良好 */
  GOOD = 1,
  /** 一般 */
  FAIR = 2,
  /** 差 */
  POOR = 3,
  /** 极差 */
  VERY_POOR = 4,
}

// ==================== WebRTC 通话相关 DTO ====================

/** 通话会话信息 */
export interface CallSession {
  /** 通话唯一 ID */
  callId: string
  /** 通话房间 ID */
  roomId?: string
  /** 通话类型 */
  callType: CallType
  /** 通话模式 */
  callMode: CallMode
  /** 发起者 ID */
  callerId: string
  /** 发起者名称 */
  callerName?: string
  /** 发起者头像 */
  callerAvatar?: string
  /** 被叫者 ID */
  calleeId?: string
  /** 被叫者名称 */
  calleeName?: string
  /** 被叫者头像 */
  calleeAvatar?: string
  /** 通话状态 */
  status: CallStatus
  /** 开始时间 */
  startTime?: string
  /** 结束时间 */
  endTime?: string
  /** 持续时间（秒） */
  duration?: number
  /** 参与者列表 */
  participants?: string[]
  /** 是否启用视频 */
  videoEnabled: boolean
  /** 是否启用音频 */
  audioEnabled: boolean
  /** 是否启用屏幕共享 */
  screenShareEnabled: boolean
  /** 当前编码参数 */
  encoderParams?: EncoderParams
  /** 网络质量等级 */
  networkQuality: NetworkQuality
}

/** 编码参数 */
export interface EncoderParams {
  /** 质量等级 */
  quality: number
  /** 宽度 */
  width: number
  /** 高度 */
  height: number
  /** 帧率 */
  fps: number
  /** 最大码率（kbps） */
  maxBitrate: number
  /** 最小码率（kbps） */
  minBitrate: number
  /** 目标码率（kbps） */
  targetBitrate: number
  /** 分辨率缩小比例 */
  scaleResolutionDownBy: number
  /** 描述 */
  description: string
}

/** 网络质量快照 */
export interface NetworkQualitySnapshot {
  /** 往返时延（ms） */
  rtt: number
  /** 丢包率（百分比 0-100） */
  packetLoss: number
  /** 抖动（ms） */
  jitter: number
  /** 估算带宽（kbps） */
  estimatedBandwidth: number
  /** 是否正在屏幕共享 */
  isScreenShare: boolean
  /** 时间戳 */
  timestamp: number
  /** 综合质量等级 */
  qualityLevel: number
}

/** ICE 服务器配置 */
export interface IceServerConfig {
  /** ICE 服务器列表 */
  iceServers: RTCIceServer[]
  /** ICE 传输策略 */
  iceTransportPolicy: RTCIceTransportPolicy
  /** ICE Candidate 池大小 */
  iceCandidatePoolSize: number
}

/** 通话记录 DTO */
export interface CallRecordDTO {
  callId: string
  roomId?: string
  callType: CallType
  callMode: CallMode
  callerId: string
  callerName?: string
  callerAvatar?: string
  calleeId?: string
  calleeName?: string
  calleeAvatar?: string
  status: CallStatus
  startTime?: string
  endTime?: string
  duration?: number
  rejectReason?: string
  participants?: string[]
  videoEnabled: boolean
  screenShareEnabled: boolean
  hangupBy?: string
  createTime?: string
}

// ==================== WebRTC 信令 Payload 类型 ====================

/** 通话邀请 Payload */
export interface CallInvitePayload {
  callId: string
  callerId: string
  callerName: string
  callerAvatar: string
  callType: CallType
  timestamp: number
}

/** SDP Offer/Answer Payload */
export interface SdpPayload {
  callId: string
  sdp: RTCSessionDescriptionInit
}

/** ICE Candidate Payload */
export interface IceCandidatePayload {
  callId: string
  candidate: RTCIceCandidateInit
}

/** 网络质量上报 Payload */
export interface QualityReportPayload {
  callId: string
  rtt: number
  packetLoss: number
  jitter: number
  estimatedBandwidth: number
  isScreenShare: boolean
  qualityLevel: number
}

/** 编码参数调整 Payload */
export interface EncoderAdjustPayload {
  callId: string
  encoderParams: EncoderParams
}

/** 群组通话房间 Payload */
export interface RoomPayload {
  roomId: string
  roomName?: string
  creatorId: string
  participants: string[]
  callType: CallType
}