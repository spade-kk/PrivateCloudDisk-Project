// ============================================================
// im/types.ts — IM 协议类型定义
// ============================================================
// 与后端 im-common 模块协议完全对齐的 TypeScript 类型定义。
// 包含枚举、DTO、通信协议、事件对象等所有类型。
//
// 后端对应包：
//   org.project.im.common.enums.*
//   org.project.im.common.dto.*
//   org.project.im.common.protocol.v2.IMProtocolV2（V2 二进制协议）
//   org.project.im.common.event.*
//   org.project.im.common.constant.ImConstants
//
// v2.0 变更：
//   - 移除 JSON 协议作为主通信协议
//   - 新增 V2 二进制协议类型（IMEnvelope、Payload 类型）
//   - 保留 MessageProtocol 作为兼容层（用于内部状态管理）
//   - 新增事件回调类型：onRead
// ============================================================

// ==================== 枚举 ====================

/** 消息类型 */
export enum MessageType {
  TEXT = 1,
  IMAGE = 2,
  FILE = 3,
  VOICE = 4,
  VIDEO = 5,
  STICKER = 10,
  LOCATION = 11,
  REPLY = 12,
  VOICE_CALL = 13,
  VIDEO_CALL = 14,
  SYSTEM_NOTICE = 50,
  READ_RECEIPT = 51,
  TYPING = 52,
  HEARTBEAT = 90,
  ERROR = 92,
  RECEIPT = 93,
  CUSTOM = 100,
}

/**
 * REST/数据库消息状态。
 *
 * AUDIT FIX [11.1,14.25] / IM-WEB-ENTERPRISE-20260809：原枚举错误复用了 V2
 * 传输态（SENDING/SENT/DELIVERED/READ/FAILED），导致 REST 返回的 1 被显示为“已发送”而
 * 实际后端含义是“已送达”。新行为严格对应 MessageStatus.java 四态；撤回/删除仍保留
 * 服务端可见性状态值。V2 传输态继续使用 protocol/protoSchema.ts 的 IMMessageStatus。
 */
export enum MessageStatus {
  PREPARING = 0,
  DELIVERED = 1,
  READ = 2,
  FAILED = 3,
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

  // WebRTC 通话信令（必须与 im_protocol_v2.proto 的 IMCommandType 保持一致）
  // AUDIT FIX [IM-PROTO-20260810]：原前端类型只覆盖基础命令，调用方只能写裸数字，
  // 其中 CALL_ICE_SERVERS 曾误用旧 Java 枚举值 2601。统一暴露 V2 合法值，避免
  // IMEnvelope.verify 报告 "command: enum value expected"。
  CALL_INVITE = 2001,
  CALL_ACCEPT = 2002,
  CALL_REJECT = 2003,
  CALL_CANCEL = 2004,
  CALL_HANGUP = 2005,
  CALL_BUSY = 2006,
  CALL_TIMEOUT = 2007,
  SIGNALING_OFFER = 2101,
  SIGNALING_ANSWER = 2102,
  SIGNALING_ICE = 2103,
  CALL_QUALITY_REPORT = 2201,
  CALL_SCREEN_SHARE_START = 2301,
  CALL_SCREEN_SHARE_STOP = 2302,
  CALL_MUTE_TOGGLE = 2303,
  CALL_CAMERA_TOGGLE = 2304,
  CALL_SWITCH_TO_VOICE = 2305,
  CALL_SWITCH_TO_VIDEO = 2306,
  CALL_ROOM_CREATE = 2401,
  CALL_ROOM_JOIN = 2402,
  CALL_ROOM_LEAVE = 2403,
  CALL_ICE_SERVERS = 2501,
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

/**
 * 富媒体负载的前端统一输入形态。字段名采用 protobufjs camelCase 规则，
 * 由 ImWebSocketClient 按 messageType 编码为后端 IMProtocolV2 对应 Payload。
 */
export type MessagePayload = Record<string, unknown>

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
  /** 由好友关系/群成员资格动态计算，解除关系后保留历史但禁用输入框。 */
  canSend?: boolean
  sessionStatus?: 'ACTIVE' | 'FRIEND_REMOVED' | 'GROUP_LEFT'
  totalMessages?: number
  status?: number
  createTime?: string
  updateTime?: string
}

/**
 * 私聊在线状态。当前由 IM Platform 读取 IM Server 的 TTL 在线映射，
 * 不扩展既有 Protobuf 枚举，避免浏览器客户端与服务端版本不一致。
 */
export interface PresenceDTO {
  status: 'online' | 'offline' | 'busy' | 'unknown'
}

/** 群组 DTO */
export interface GroupDTO {
  groupId: string
  groupName: string
  avatar?: string
  ownerId: string
  ownerName?: string
  /** 当前请求用户的群内角色；由 IM Business 权限校验后返回。 */
  currentUserRole?: GroupRole
  /** 当前用户的群会话 ID，固定为 group*{groupId}。 */
  conversationId?: string
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

/** 好友关系的可展示资料；邮箱、手机号等敏感账号字段不在此模型中。 */
export interface FriendDTO {
  friendId: string
  username?: string
  account?: string
  avatarPath?: string
  remark?: string
  starred?: boolean
  online?: boolean
  commonSpaceCount?: number
  commonGroupCount?: number
  status: number
  createdAt?: string
}

/**
 * 主业务用户目录返回的公开资料，好友关系状态由 IM 前端本地合并。
 * 该类型不代表 IM 服务端 DTO，避免将用户目录数据模型误归属到 IM。
 */
export interface PublicUserSearchResult {
  userId: string
  username?: string
  account?: string
  avatarPath?: string
  relationshipStatus: 'NONE' | 'FRIEND' | 'PENDING_OUTGOING' | 'PENDING_INCOMING' | 'BLOCKED'
}

export interface FriendRequestDTO {
  requestId: string
  requesterId: string
  recipientId: string
  requesterName?: string
  requesterAccount?: string
  requesterAvatarPath?: string
  recipientName?: string
  recipientAccount?: string
  recipientAvatarPath?: string
  verificationMessage?: string
  /** 0 待处理、1 已接受、2 已拒绝、3 已撤销。 */
  status: number
  createdAt?: string
  updatedAt?: string
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
  hasMore: boolean
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
  /**
   * V2 Envelope 元数据的兼容投影。
   *
   * IM Server 的实时推送先解码 Envelope，再由客户端解码类型化 payload。
   * 即使某种 payload 暂时无法解码，消息中心也可以凭这些元数据把消息先放入
   * 对应会话，而不是因为 payload 解析失败而静默丢消息。
   */
  messageId?: string
  conversationId?: string
  conversationType?: ConversationType
  messageType?: MessageType
  serverSeq?: number
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
  /** 会话 ID（V2 新增：用于路由通话信令到正确会话） */
  conversationId?: string
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

// ============================================================
// V2 二进制协议类型（与 protocol/protoSchema.ts 配合使用）
// ============================================================

/**
 * V2 IMEnvelope 消息对象
 *
 * <p>对应后端 IMProtocolV2.IMEnvelope protobuf 消息。
 * 此类型作为 V2 二进制协议的顶层信封，包含消息元数据与加密负载。</p>
 *
 * <p>注意：messageType、command、status、conversationType 等枚举字段
 * 在传输时使用数字编码，与后端 protobuf 枚举值对齐。</p>
 */
export interface IMEnvelope {
  /** 协议版本（固定为 2） */
  version: number
  /** 消息唯一 ID（雪花算法/UUID） */
  messageId: string
  /** 命令类型（IMCommandType 枚举值） */
  command: number
  /** 消息类型（IMMessageType 枚举值，用于 Layer 2 负载分发） */
  messageType: number
  /** 发送者 ID */
  senderId: string
  /** 接收者 ID（单聊=用户ID，群聊=群组ID） */
  receiverId: string
  /** 会话 ID */
  conversationId: string
  /** 会话类型（IMConversationType 枚举值） */
  conversationType: number
  /** 发送者名称 */
  senderName?: string
  /** 发送者头像 URL */
  senderAvatar?: string
  /** 客户端消息序列号（去重和排序） */
  seq?: number
  /** 服务端序列号（服务端分配） */
  serverSeq?: number
  /** 消息状态（IMMessageStatus 枚举值） */
  status?: number
  /** 时间戳（毫秒） */
  timestamp: number
  /** Layer 2 加密负载（二进制，需二次解密） */
  encryptedPayload?: Uint8Array
  /** 引用消息 ID（回复消息时使用） */
  replyTo?: string
  /** 消息过期时间（Unix 毫秒，0=永不过期） */
  expireAt?: number
  /** 扩展字段（JSON 序列化） */
  extraJson?: string
  /** 发送者设备类型（DeviceType 枚举值） */
  senderDeviceType?: number
  /** 发送者平台 */
  senderPlatform?: string
  /** 链路追踪 ID */
  traceId?: string
  /** 消息优先级（0=普通 1=高 2=紧急） */
  priority?: number
}

// ============================================================
// V2 事件回调类型（v2.0 新增）
// ============================================================

/**
 * 消息已读事件回调参数
 *
 * <p>对应后端 MessageReadEvent。
 * 当接收方标记消息为已读时触发。</p>
 */
export interface ReadEvent {
  /** 用户 ID（标记已读的用户） */
  userId: string
  /** 会话 ID */
  conversationId: string
  /** 已读消息 ID 列表（可选，为空表示整个会话已读） */
  messageIds?: string[]
  /** 已读时间戳（毫秒） */
  readAt: number
}

/**
 * 推送回执状态
 *
 * <p>对应后端 ReceiptStatus 枚举，用于描述消息推送结果。</p>
 */
export enum ReceiptStatus {
  /** 未指定 */
  UNSPECIFIED = 0,
  /** 已送达 */
  DELIVERED = 1,
  /** 推送失败 */
  PUSH_FAILED = 2,
  /** 发送失败 */
  SEND_FAILED = 3,
}

/**
 * 推送回执事件回调参数
 *
 * <p>对应后端 ReceiptPayload。
 * 当本账号在某设备发送的消息被推送（成功或失败）时，
 * 所有登录该账号的连接都会收到此回执，用于多端同步发送状态。</p>
 */
export interface ReceiptEvent {
  /** 原始消息 ID */
  originalMessageId: string
  /** 会话 ID */
  conversationId: string
  /** 发送者 ID */
  senderId: string
  /** 接收者 ID */
  receiverId: string
  /** 推送回执状态 */
  status: ReceiptStatus
  /** 失败码 */
  failCode: number
  /** 失败原因 */
  failReason: string
  /** 回执时间戳（毫秒） */
  receiptAt: number
}

/**
 * 用户上线事件回调参数
 *
 * <p>对应后端 UserOnlineEvent。</p>
 */
export interface UserOnlineEventV2 {
  /** 用户 ID */
  userId: string
  /** 服务端节点 ID */
  serverNodeId: string
  /** 设备类型 */
  deviceType: number
  /** 客户端平台 */
  platform: string
  /** 客户端版本 */
  clientVersion: string
  /** 上线时间戳（毫秒） */
  onlineAt: number
  /** 连接 ID */
  connectionId: string
}

/**
 * 用户离线事件回调参数
 *
 * <p>对应后端 UserOfflineEvent。</p>
 */
export interface UserOfflineEventV2 {
  /** 用户 ID */
  userId: string
  /** 服务端节点 ID */
  serverNodeId: string
  /** 连接 ID */
  connectionId: string
  /** 离线原因（1=正常 2=心跳超时 3=踢出 4=异常） */
  offlineReason: number
  /** 离线时间戳（毫秒） */
  offlineAt: number
}
