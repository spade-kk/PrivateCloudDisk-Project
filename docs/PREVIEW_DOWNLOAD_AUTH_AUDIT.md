# 文件预览与下载授权、分享资源及空间隔离审计报告

> 审计范围：`PrivateCloudDisk-storage-service`、`PrivateCloudDisk-platform-service`、`PrivateCloudDisk-web`、数据库迁移与 Gateway 路由。
> 需求编号按“文件预览与下载授权接口扩展及空间越权修复需求清单”记录；本报告不替代生产发布前的真实 MySQL/RabbitMQ/Redis 联调。

## 1. 审计结论

本次修改将普通网盘文件和分享资源的授权链路彻底分开：普通文件继续使用原有 `file_id` + `X-Space-Id` 申请 Preview/Download Grant；分享文件只接受 `share_resource_id`（UUID 或加密虚拟 ID）+ `X-Share-Access-Token`，由平台服务在内部解析并校验 `(owner_id, space_id, file_id)` 三元组，存储服务只将脱敏后的元数据写入短期 Grant。客户端不再接收 `file_id`、`storage_path`，也不再打开主业务服务直接返回的文件实体。

分享链路没有复制普通文件的内容读取业务：`app/core/share_access.py` 只负责虚拟资源解析、分享令牌和空间边界；`app/core/file_delivery.py` 统一负责普通/分享下载与预览的路径边界、Range 解析、异步分块读取和文件响应。差异被限定在授权入口与响应脱敏出口，后续新增格式只需接入一次普通预览资源核心。

无提取码分享仍必须调用 `/business/public/shares/{token}/verify`，使用空字符串换取临时访问令牌；资源列表、文件夹子节点和元数据必须带该令牌。实际文件内容（预览、下载、视频 HLS、Office/压缩包资源）还需要登录后申请对应 Grant。

## 2. 原实现问题清单与严重度

| 编号 | 问题 | 严重度 | 处理结果 |
| --- | --- | --- | --- |
| P-01 | 分享下载接口返回 `FileEntity`，可能泄露 `storage_path` 和真实文件 ID | Critical | 删除公开下载 Controller/Service 定义，改为存储服务 Grant 链路 |
| P-02 | 分享资源没有专用 Preview/Download Grant | High | 新增 `/files/share/{share_token}/...` 四类接口，并复用普通 Grant 引擎 |
| P-03 | 普通 Grant 资源定位可能缺少 space 三元组 | Critical | 公共 `validateFileInSpace`、严格 Mapper 查询、内部接口权限动作校验 |
| P-04 | 分享虚拟 ID 仅解密后查文件，未验证完整分享范围 | Critical | `countFileInShare` + space 校验，文件夹后代使用 closure 表 |
| P-05 | 无密码分享可绕过 verify 直接访问资源 | High | 前端无论是否有密码均调用 verify；内容/子目录仍要求分享访问令牌 |
| P-06 | 下载事件无法区分空间下载与分享下载 | High | MQ 事件增加 `accessSource/shareResourceId`；最近访问增加来源字段 |
| P-07 | 视频雪碧图、字幕直接按 file_id 扫本地目录 | High | 先调用业务服务做空间归属校验，再读取目录 |
| P-08 | HLS Token 未绑定空间且分享令牌可能暴露真实文件 ID | Critical | 普通 Token 增加 `space_id`；分享 HLS 改为 Redis opaque token，台账查询使用 space 维度 |
| P-09 | 专用预览资源接口无法使用分享资源 ID | High | 新增分享 Office/PDF 缩略图、压缩包树、预览资源列表和分享 HLS 路由 |
| P-10 | MyBatis 迁移后的 XML 存在未转义 `<`，应用上下文启动失败 | Critical | 修复 `SpaceInvitationMapper.xml`，全量 XML 解析通过 |

## 3. API 契约

### 3.1 普通网盘链路（兼容旧客户端）

- `POST /api/v1/files/preview-grants`：原请求体 `{file_id}`、响应结构保持不变；`X-Space-Id` 可选，缺失时由平台解析个人空间。
- `POST /api/v1/files/download-grants`：原请求体 `{file_id}`、响应结构保持不变；令牌写入 `spaceId`，消费时不依赖客户端再次传 file_id。
- 原有 `/files/files/{file_id}/preview-content` 和 `/files/files/{file_id}/content` 保留，普通令牌不与分享令牌混用。

### 3.2 分享授权链路

申请接口均要求登录用户（网关注入 `X-User-Id`）和已通过 verify 的 `X-Share-Access-Token`：

```http
POST /api/v1/files/share/{share_token}/preview-grants
X-Share-Access-Token: <share access token>
Content-Type: application/json

{"share_resource_id":"<uuid-or-encrypted-id>"}
```

```http
POST /api/v1/files/share/{share_token}/download-grants
X-Share-Access-Token: <share access token>
Content-Type: application/json

{"share_resource_id":"<uuid-or-encrypted-id>"}
```

成功响应与普通 Grant 相同，并附带 `share_resource_id`；不返回真实 `file_id`、物理路径或平台 FileEntity。

```http
GET /api/v1/files/share/{token}/resources/{resource}/preview-content
X-Preview-Grant: <preview grant>
```

```http
GET /api/v1/files/share/{token}/resources/{resource}/content
X-Download-Grant: <download grant>
```

两类内容接口支持单 `bytes` Range、上限和 206/416 边界响应；下载完成沿用原 MQ 事件释放机制。

Preview/Download Grant 的释放接口仍复用普通生命周期：
`POST /api/v1/files/preview-grants/release`、
`POST /api/v1/files/download-grants/release`。分享 Grant 在 Redis 中带有
`grantSource=share`、`shareToken` 和 `shareResourceId`，释放时按用户校验并清理
配额，不会因为分享访问误写普通文件下载事件。

### 3.3 专用资源

- Office/PDF：`.../preview-resources`、`.../document-thumbnail`。
- 压缩包：`.../archive-preview-status`、`.../archive-tree`。
- 视频：`.../video/info`、`.../video/token`、`master.m3u8`、分辨率播放列表、TS 分片、雪碧图、VTT、首帧缩略图、播放进度读写。

这些 URL 只接受分享资源标识和分享访问令牌；响应中的台账字段已剔除 `file_id/user_id/space_id/storage_path`。

### 3.4 复用边界（本轮重构）

| 层 | 普通网盘请求 | 分享请求 | 是否复用 |
| --- | --- | --- | --- |
| 资源身份 | `file_id` + `X-Space-Id` | `share_resource_id` + `X-Share-Access-Token` | 入口适配器不同 |
| 业务授权 | 普通用户/空间权限 | 分享生命周期、资源范围、源空间三元组 | 授权策略不同 |
| Grant 引擎 | `issue_*_grant` | 同一个 `issue_*_grant`，附加分享绑定字段 | 是 |
| 文件内容 | `/files/files/{file_id}/...` | `/files/share/{token}/resources/{id}/...` | `serve_authorized_file` 是 |
| HLS/台账 | `preview_resource_service` | 同一台账，使用解析后的内部 ID | 是 |
| 输出身份 | 普通接口可返回 `file_id` | 只返回 `share_resource_id` | 脱敏出口不同 |

这张边界表是后续代码审查的约束：分享模块不得再实现一套 Range、路径或文件流
读取逻辑，也不得在 JSON 响应中回显真实 `file_id`。

## 4. 授权与数据流

```text
分享页 info（匿名）
      ↓
verify 空密码/提取码 → Share Access Token（短期）
      ↓（登录后）
share_resource_id + access token
      ↓
Platform ShareService：token 生命周期 + 资源范围 + space 三元组
      ↓（服务间 X-PCD-Service-Token）
Storage Grant：短期、用户绑定、分享绑定、并发/请求次数限制
      ↓
Range 内容/HLS/Office/Archive 资源
      ↓
Download Grant release → file_downloaded(accessSource=share) → 最近访问来源区分
```

## 5. 空间越权修复说明

`SpacePermissionService.validateFileInSpace(userId, spaceId, fileId)` 是唯一三元组入口；`spaceId=null` 只代表解析个人空间，不代表忽略空间条件。常规内部元数据接口先解析上下文、校验操作权限，再调用 `requireFileInCurrentSpace`。分享解析不依赖访问者对源空间的成员关系，但仍使用分享者的源空间和资源范围校验，避免跨空间复用虚拟 ID。

视频 HLS Token、预览资源 Redis key 和数据库查询均携带 `space_id`；字幕、雪碧图不再仅凭 UUID 扫描文件系统。物理路径读取统一限制在上传根目录内。

分享 HLS 不复用普通 URL 载荷型 Token：`share_hls_token.py` 只向客户端返回随机
opaque token，真实 `file_id/user_id/space_id` 保存在短期 Redis 映射中；清单、分片、
雪碧图、VTT 和首帧接口均先验证 token 与虚拟资源 ID 的绑定。

## 6. 数据库迁移

- `011_share_download_permission.sql`：`share_allow_download`，历史分享回填 `1`。
- `012_recent_share_access.sql`：`ra_access_source`、`ra_share_resource_id`，历史记录默认 `space`。
- 初始化脚本同步增加上述列；Mapper 查询/插入/更新同步完成。

## 7. 前端行为

`ShareAccessView.vue` 保留匿名信息、提取码和目录浏览；文件点击/下载前检测登录态，未登录跳转登录并携带 `redirect`。登录后申请专用 Grant，使用 Blob URL 预览或下载，完成后释放 Grant；任何异常均不显示内部路径。创建分享支持 `allow_download`，管理端可独立切换下载权限。

## 8. 测试与验证

已执行：

1. `PrivateCloudDisk-web`: `npm run build` —— 通过（Vite 1996 modules）。
2. `PrivateCloudDisk-platform-service`: `./gradlew compileJava compileTestJava --no-daemon` —— 通过。
3. `PrivateCloudDisk-platform-service`: `./gradlew test --no-daemon` —— 通过，9 tests。
4. Python：`python3 -m compileall -q PrivateCloudDisk-storage-service/app` —— 通过。
5. Python 已安装环境的 unittest：分享授权契约测试 9 项通过；增强流水线/DLQ 测试因本机未安装 `aiomysql` 无法导入，需在服务镜像或完整虚拟环境中执行。
6. 所有 MyBatis XML 使用 Python `xml.etree.ElementTree` 全量解析通过。

发布前必须在带 MySQL/Redis/RabbitMQ 的测试环境补充：跨空间同 file_id、错误 space header、分享 token 复用、下载权限关闭、Range 越界、HLS/Office/Archive 分享资源、重复下载事件幂等和匿名访问矩阵。

## 9. 风险与规避

- 分享下载现在要求登录，属于安全策略变更；前端已提供登录跳转，发布说明需明确。
- Blob 下载占用浏览器内存；大文件仍建议使用分段下载客户端，服务端已保留 Range 限制。
- 专用预览资源依赖数据库台账；台账缺失返回 404，不回退扫描目录，避免多实例漂移。
- 内部解析接口依赖 `PCD_INTERNAL_SERVICE_TOKEN` 或内网 IP 白名单；生产必须配置服务令牌并禁止公网暴露 `/business/internal/**`。

## 10. Full-stack audit scorecard（本次范围）

| 类别 | 结果 | 证据 |
| --- | --- | --- |
| 身份与会话 | PASS | 分享访问令牌、登录后 Grant、401/403 分流 |
| 授权与空间隔离 | PASS | `validateFileInSpace`、closure 范围 SQL、HLS space claim |
| 数据泄露 | PASS | 移除公开 FileEntity 下载接口，分享响应脱敏 |
| 输入与路径安全 | PASS | UUID/虚拟 ID 校验、Range 上限、上传根目录边界 |
| 事件与审计 | PASS | `accessSource/shareResourceId` 贯穿 MQ 与最近访问 |
| 浏览器兼容 | PASS | Blob URL、登录跳转、原分享 UI 保持；Safari 显示修复保留 |
| 可观测性 | PARTIAL | 现有结构化日志已接入；需部署环境验证告警和指标 |
| 自动化测试 | PARTIAL | Java/前端/编译通过；Python 完整依赖环境需补跑 |
