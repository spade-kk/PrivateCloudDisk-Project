# IM 好友管理：前后端契约与迁移说明

## 审计结论

- 消息中心原联系人区只渲染 `friendId`，没有用户资料、申请列表、备注、黑名单或独立页面。
- IM Business 原有的接受申请流程已经在单一数据库事务中完成：申请置为已接受、写入双方好友关系、调用 `ensureConversationForParticipants` 创建共享单聊会话。该流程继续保留，未引入 MQ。
- IM Server/Router 的 V2 Protobuf 协议目前没有“好友申请/关系变更”事件。因此前端以 30 秒低频 HTTP 轮询更新申请红点；不能发送未定义的二进制帧，否则会干扰现有消息收发协议。
- USER-DIRECTORY-20260810：IM 不再查询 `pcd_user_info_table`，好友搜索、好友资料、群成员资料和消息发送者公开资料统一调用主业务服务用户目录接口；IM 只负责关系、群组和消息数据。

## 数据迁移

先执行 [migration_friend_management_20260810.sql](/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-im/sql/migration_friend_management_20260810.sql)。迁移为 `pcd_im_friendship` 增加：

- `remark`：当前用户私有备注。
- `is_starred`：当前用户星标状态。
- `pcd_im_blacklist`：消息与好友申请拒绝所依赖的持久化黑名单。
- `pcd_im_friend_request_block`：拒绝申请时的“拒收此人后续申请”规则。

现有 `pcd_im_conversation` 不变。删除好友/退出关系后，会话和聊天记录保留，发送权限仍由既有会话查询逻辑计算。

## HTTP API

所有接口走网关 `/api/v1` 前缀；下表为 IM Platform 实际路径。当前网关未向 IM Platform 注入认证用户主体，所有请求必须携带 `userId`（创建申请为 `requesterId`）。后续统一认证上下文后可在服务端替换该来源，不应由前端伪造任何 WebSocket 事件。

| 动作 | 方法与路径 | 关键参数 |
|---|---|---|
| 搜索用户 | `GET /business/users/search` | 主业务公共接口：`q, page, size`（保留 `limit`）；账号/用户名/邮箱匹配，仅返回公开资料 |
| 发送申请 | `POST /im/friends/requests` | JSON：`requesterId, recipientId, message`（最多 50 字） |
| 收到/发出申请 | `GET /im/friends/requests/incoming` / `outgoing` | `userId, page, size` |
| 申请红点 | `GET /im/friends/requests/pending/count` | `userId` |
| 同意/拒绝/撤销 | `PUT /requests/{id}/accept`、`PUT /requests/{id}/reject`、`DELETE /requests/{id}` | `userId`；拒绝 JSON 可传 `blockFuture` |
| 好友列表和详情 | `GET /im/friends`、`GET /im/friends/{friendId}` | `userId` |
| 备注与星标 | `PUT /{friendId}/remark`、`PUT /{friendId}/star` | JSON：`remark` / `starred` |
| 删除、拉黑、取消拉黑 | `DELETE /{friendId}`、`POST /{friendId}/block`、`DELETE /{friendId}/block` | `userId` |
| 黑名单 | `GET /im/friends/blacklist` | `userId` |

旧的 Query 参数申请、接受、拒绝、好友列表和待处理申请端点仍保留兼容。

好友搜索不再提供 `/im/friends/users/search`；前端直接复用主业务用户目录接口。主业务接口的响应仍为公开资料列表，好友关系状态由前端结合 IM 好友/申请/黑名单接口本地组装，避免把 IM 关系模型反向写入主业务用户服务。

## 前端入口

- `/im/add-friend`：300ms 防抖搜索、分页滚动、最近搜索、验证信息、键盘导航、亮暗主题和移动端底部弹层。
- `/im/friend-requests`：收/发 Tab、同意、拒绝、拒收后续申请、撤销、批量操作及“去聊天”。
- 消息中心右侧“联系人”：搜索、星标、黑名单、备注、删除、拉黑/取消拉黑，以及申请数量入口。

推荐、扫一扫、个人名片为明确禁用的预留入口，未用虚构 API 伪造成功状态。
