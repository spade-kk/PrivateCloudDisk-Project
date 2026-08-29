# 群聊 Web 客户端接口与生命周期

> 关联需求：`GROUP-CHAT-20260810`。本文记录群聊 Web 工作区的接口边界、会话状态和协议限制，供 Web、IM Business 与 IM Server 联调。

## 设计边界

- 群组资料、成员关系与角色由 `im-platform` 的 `/im/groups` REST API 管理。
- 每名群成员都有一条个人会话元数据，ID 固定为 `group*{groupId}`；消息表以该 ID 作为共同 `conversation_id`。
- 创建群和邀请成员在同一个本地数据库事务内同步创建成员关系与会话。它们不等待 MQ 消费，避免新成员在创建成功后无法打开群聊。
- 创建群、邀请/退出/移除成员、角色/禁言及群资料变更会在该事务提交后复用 `SYSTEM_NOTICE=50`
  写入群消息历史并经既有 Router 推送；投递异常只记录告警，不会反向回滚已提交的成员和会话数据。
- 文本、图片、文件和系统通知仍使用原有 IM V2 消息协议与 Router 推送链路；群资料事件没有伪装成未定义的 Protobuf 二进制帧。
- 当前 V2 Protobuf 尚未定义群资料/成员变更事件。因此 Web 在线时每 30 秒刷新群资料和成员概览；实际群消息仍实时通过 WebSocket 收到。

## 核心接口

所有接口经网关时使用 `/api/v1/im/groups` 前缀；以下路径为 IM Business 实际路径。浏览器需携带认证 Token，并显式提供当前 `userId` 或 `operatorId`，以兼容当前网关尚未向 IM Business 注入用户主体的部署方式。

| 用途 | 方法与路径 | 关键参数 |
| --- | --- | --- |
| 创建群聊 | `POST /im/groups` | `ownerId`、`groupName`、`memberIds`、可选 `avatarFileId`、`joinMode` |
| 群列表 | `GET /im/groups` | `userId`、`page`、`size` |
| 群详情 | `GET /im/groups/{groupId}` | `userId` |
| 修改群资料/公告 | `PUT /im/groups/{groupId}` | `operatorId`、可选 `name`、`avatarFileId`、`announcement`、`description`、`joinMode` |
| 邀请成员 | `POST /im/groups/{groupId}/members` | `operatorId`、`userIds` |
| 成员分页 | `GET /im/groups/{groupId}/members` | `userId`、`page`、`size` |
| 移除成员 | `DELETE /im/groups/{groupId}/members/{userId}` | `operatorId` |
| 主动退出 | `DELETE /im/groups/{groupId}/members/self` | `userId` |
| 管理员角色 | `PUT /im/groups/{groupId}/members/{userId}/role` | Body: `operatorId`、`role` (`2` admin / `3` member) |
| 禁言/解除禁言 | `POST` / `DELETE /im/groups/{groupId}/members/{userId}/mute` | `operatorId`、禁言时 `durationMinutes` |
| 解散群聊 | `DELETE /im/groups/{groupId}` | `ownerId` |

`GroupDTO` 会返回 `currentUserRole` 和 `conversationId`，以便客户端无额外计算地决定管理操作与跳转会话。成员资料仅包含公开昵称和头像，不返回邮箱、手机号等敏感字段。

## 权限与状态

| 操作 | 权限 |
| --- | --- |
| 查看群详情、成员 | 当前群成员 |
| 邀请、公告、群资料 | 群主或管理员 |
| 移除/禁言成员 | 群主或管理员；管理员不能处理群主或其他管理员 |
| 设置管理员、解散群 | 仅群主 |
| 退出群 | 非群主成员；群主需先完成所有权转移能力后退出 |

退出、被移除和解散都会保留会话及历史消息用于只读回溯。`ConversationService` 和 `MessageService` 会同时检查成员关系与群状态：输入框会置灰，服务端也拒绝任何绕过前端的继续发送请求。

## Web 功能映射

- 右侧“群组”页签：搜索、置顶/免打扰摘要、未读、群详情、成员、邀请、管理员、禁言、退出与解散。
- 左侧会话：群头像角标、群消息发送者摘要、@ 当前用户的橙色未读标记。
- 群消息区：接收消息显示成员头像/昵称；同一成员五分钟内连续消息折叠头像；系统通知沿用 `SYSTEM_NOTICE=50`。
- 输入框：键入 `@` 可按成员昵称选择并插入文本。当前协议未定义结构化 `mentions` 字段，客户端以文本标记渲染和提醒，未来可在不改变会话/消息 ID 的前提下升级。
- 创建群页面：三步选择好友、设置名称/头像地址、确认创建。群头像目前使用文件服务已返回的可访问地址；独立的群头像上传和裁剪接口需由文件服务提供后再接入，不能复用“上传个人头像”接口以免意外改动用户头像。

## 兼容性

旧版 `POST /im/groups/create`、`/join`、`/leave`、`/kick`、`/mute`、`/unmute`、`/mute-all`、`/dissolve` 和未带 `userId` 的成员列表接口继续保留。新 Web 客户端只调用上述 REST 接口。

旧实现曾将 Snowflake 群 ID 误判为 UUID，现已移除该校验；同时 `MessageDTO.receiverId` 不再强制 UUID，由消息服务按单聊或群聊类型完成正确校验。
