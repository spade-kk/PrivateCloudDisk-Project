# 空间多人协作平台前端与后端专项审计及实施设计

> 需求范围：空间切换 URL 同步、空间发现/申请加入、成员管理、权限矩阵、空间设置、审批管理、用户公开资料和全链路前端组件。
>
> 审计基线：2026-07-29 工作区现状。本文档先于本专项代码改动创建，作为需求追踪、迁移和回归测试基线。

## 1. 审计结论

### 1.1 前端现状

| 模块 | 当前实现 | 风险 | 严重程度 |
| --- | --- | --- | --- |
| 空间 URL | `router/index.ts` 仅同步 `?space`，切换组件不更新 URL | 刷新后空间上下文丢失，且浏览器前进/后退不可靠 | S1 |
| 空间状态 | `spaceStore.setCurrentSpaceFromUrl` 直接接受任意 ID，未校验成员关系 | 可出现 URL、下拉框和请求头不一致 | S1 |
| 请求头 | `request.ts` 已统一注入 `X-Space-Id` | 基础能力可复用，需保证 URL/store 的唯一来源 | S2 |
| 团队协作 | `TeamView.vue` 为静态演示数据 | 搜索、加入申请、申请记录均不可用 | S1 |
| 空间管理 | 可 CRUD、配额和旧版成员管理 | 缺少独立成员、设置、审批页面及细粒度权限 | S1 |
| API SDK | 只有旧版角色和 6 个布尔权限 | 无空间搜索、申请取消、邀请、公开资料、权限矩阵接口 | S1 |
| 响应式/反馈 | 基础 Tailwind/Toast 已存在 | 新页面需要统一 loading、错误、二次确认和移动端布局 | S2 |

必须保留 `SpaceSelector` 和 `FileBrowser`。`FileBrowser` 继续通过请求拦截器获取空间上下文；团队协作页不直接复用文件浏览器，避免社交发现和网盘操作耦合。

### 1.2 后端现状

| 模块 | 当前实现 | 缺口 |
| --- | --- | --- |
| 空间表 | 已有 `space_type`、`space_visibility`、公开仓库字段 | 没有 `join_policy`；旧 visibility 值为 private/public/whitelist/blacklist |
| 成员表 | 已有 owner/admin/editor/viewer、唯一空间成员约束 | 没有自定义角色权限快照和成员资料聚合查询 |
| 权限表 | `can_read/can_write/can_delete/can_share/can_invite/can_manage` | 缺少 view/download/upload/edit/manage_members/manage_plugins/manage_settings |
| 加入申请 | 已有 pending/approved/rejected 和审批接口 | 唯一键阻止被拒后再次申请；没有取消申请、邀请绕过规则 |
| SpacePermissionService | 已接入 `X-Space-Id` 和旧操作枚举 | 需要扩展操作维度，并让自定义权限生效 |
| 用户接口 | 有按 ID 查询用户信息的内部业务接口 | 缺少安全的公开资料和带限流的用户名/邮箱前缀搜索 |
| Controller/Service | 旧成员/申请逻辑集中在 `SpaceController/SpaceServiceImpl` | 新能力应放入协作服务，旧接口保持兼容 |

### 1.3 现有实现边界

- 公开空间仓库（`/repo/:spaceId`）是独立页面，visibility 为公开仓库语义；不加入团队成员列表。
- 分享链接仍是已有资源引用机制，本专项不修改其 URL、有效期、密码和下载链路。
- 企业/团队/私有空间属于协作空间，使用成员、加入策略和权限矩阵。
- 个人空间固定为 hidden + invite_only，不可退出、不可被搜索。

## 2. 目标架构

```text
URL ?space_id  <->  Pinia spaceStore  <->  axios X-Space-Id
       |                                      |
       +-- router guard 校验成员/默认空间 ------+

/teamwork                       /space/:id/members
  发现、预览、申请、我的空间       成员、邀请、权限矩阵、批量操作

/space/:id/settings             /space/:id/members/approvals
  名称/描述/visibility/join       加入申请审批与历史
```

请求上下文规则：

1. URL 参数只使用 `space_id`；短期兼容读取旧参数 `space`，进入页面后立即规范化为 `space_id`。
2. 页面初始化优先读取 URL；URL 缺失使用个人空间；URL 无效或无权限自动回退个人空间并 `router.replace`。
3. `spaceStore.switchSpace` 在后端切换成功后同时更新 store 和 query；浏览器后退/前进由路由守卫重新解析。
4. `request.ts` 从 store 注入 `X-Space-Id`。公开空间/预览页面不注入协作空间头，避免污染分享与仓库访问。

## 3. 数据模型与迁移设计

### 3.1 空间字段

新增迁移 `PrivateCloudDisk-db/010_space_collaboration.sql`：

- `pcd_space_table.join_policy ENUM('open','approval_required','invite_only') NOT NULL DEFAULT 'invite_only'`。
- 将 `space_visibility` 扩展兼容 `visible/hidden`；保留旧值以支持无停机迁移。应用层统一映射：`public`（公开仓库）视为 visible，`private/whitelist/blacklist` 视为 hidden。
- 追加 `space_type='private'` 兼容私有协作空间。
- 数据回填：personal → hidden/invite_only；enterprise/team → visible/approval_required；public → public/invite_only；其他旧 private → hidden/invite_only。

### 3.2 权限字段

在 `pcd_space_permission_table` 保留旧字段，并新增：

`can_view`、`can_download`、`can_upload`、`can_edit`、`can_manage_members`、`can_manage_plugins`、`can_manage_settings`。

旧字段不删除，旧接口继续读写旧字段；新接口写入新字段并按兼容映射同步旧字段。空间、用户、节点组合保持唯一键并为 `(space_id,user_id)`、`space_id` 建索引。

### 3.3 加入申请与邀请

- 调整加入申请唯一索引为 `(space_id,user_id,status)`，业务层只限制 pending，允许 rejected 用户再次申请。
- 新增 `pcd_space_invitation_table`：`invitation_id`、`space_id`、`token_hash`、`created_by`、`expires_at`、`max_uses`、`used_count`、`status`、`created_at`，token 只保存 SHA-256，不保存明文。
- 邀请链接在 invite_only 下直接加入，在 approval_required 下可跳过审批；仍校验空间状态、过期时间和使用次数。

## 4. API 契约

接口继续遵循平台统一响应体和 `/business` 控制器规范；网关外部路径由现有路由映射到 `/api/v1`。

### 4.1 协作发现

`GET /business/teamwork/spaces/search?keyword=&page=1&size=20`：仅返回 visibility=visible 且非个人空间。

`GET /business/teamwork/spaces/{spaceId}`：返回空间摘要、owner 公开资料、成员数、join_policy、当前用户是否成员及 pending 状态。

`POST /business/teamwork/spaces/{spaceId}/join`：`{"message":"申请说明","inviteToken":"可选"}`；open 直接加入，approval_required 创建申请，invite_only 无 token 拒绝。

`GET /business/teamwork/my-spaces`、`GET /business/teamwork/my-requests`、`DELETE /business/teamwork/requests/{requestId}`。

### 4.2 成员、权限与设置

- `GET /business/space/{spaceId}/members?keyword=&sort=`
- `PUT /business/space/{spaceId}/members/{userId}/permissions`：角色或 10 维权限列表。
- `DELETE /business/space/{spaceId}/members/{userId}`；支持批量请求体 `{"userIds":[]}`。
- `POST /business/space/{spaceId}/invitations`、`DELETE /business/space/{spaceId}/invitations/{id}`、`POST /business/space/invitations/redeem`。
- `GET /business/space/{spaceId}/settings`、`PATCH /business/space/{spaceId}/settings`。
- `GET /business/space/{spaceId}/members/approvals?status=`、`PUT /business/space/{spaceId}/members/approvals/{requestId}`（approve/reject/reason）。

所有写操作由 `SpacePermissionService` 校验 `X-Space-Id`、资源归属和对应权限；管理员不可被普通权限矩阵降级，owner 转移必须单独确认。

### 4.3 用户中心

- `GET /business/users/{userId}/profile`：只返回用户名、头像、简介、注册时间等公开字段。
- `GET /business/users/search?q=&limit=20`：仅返回可用于邀请的最小资料，按用户/IP限流，禁止返回密码、令牌和敏感字段。

## 5. 前端组件与路由设计

### 5.1 路由

- `/teamwork`：发现、我的空间、我的申请。
- `/teamwork/space/:spaceId`：空间详情预览。
- `/space/manage`（保留 `/app/spaces` 兼容）：空间管理。
- `/space/:spaceId/settings`、`/space/:spaceId/members`、`/space/:spaceId/members/approvals`、`/space/:spaceId/plugins`。
- `/app`、收藏夹、标签、回收站等继续通过 `?space_id` 控制上下文。

旧 `/app/team` 路由保留并重定向到 `/teamwork`，避免书签失效；`SpaceSelector` 和 `FileBrowser` 不删除。

### 5.2 组件树

```text
TeamworkView
 ├─ TeamworkTabs
 ├─ SpaceSearchBar
 ├─ SpaceResultCard
 ├─ MySpaceCard
 └─ JoinRequestTable

SpaceMembersView
 ├─ SpaceContextHeader
 ├─ MemberToolbar
 ├─ MemberTable
 ├─ MemberPermissionDialog
 │   └─ SpacePermissionMatrix
 └─ InvitationDialog

SpaceSettingsView / SpaceApprovalsView
 ├─ SpaceContextHeader
 ├─ SettingsForm / ApprovalTable
 └─ ConfirmDialog / Toast
```

### 5.3 交互与状态

- Pinia `spaceStore.currentSpaceId` 是唯一当前空间状态；路由 query 是可恢复持久化来源。
- 所有请求显示 loading，失败使用统一 Toast；删除、退出、降级角色均二次确认。
- 成功切换后提示“已切换到 {spaceName}”，列表、面包屑、配额通过 store 订阅刷新。
- 桌面端为双栏/三栏布局，平板折叠辅助面板，移动端表格转卡片、按钮保持 44px 触控区域。

## 6. 安全与兼容性

- 不能仅依据前端按钮控制权限；后端每个接口再次执行空间成员/权限校验。
- 搜索只返回 visible 空间；hidden 空间只能由已加入成员访问。
- 个人空间不可退出、不可修改 visibility/join_policy。
- 请求头缺失仍映射个人空间，旧客户端零改动；请求头与路径 spaceId 不一致直接拒绝。
- 公开仓库接口和分享链接接口保持原授权策略，不复用协作成员权限。

## 7. 实施顺序与测试矩阵

1. 先完成迁移、实体、Mapper、协作 Service/Controller 与权限单测。
2. 修复 URL/store/interceptor，再实现 API SDK。
3. 实现团队协作、成员、设置、审批页面，最后接入插件入口。
4. 单测：URL 回退、角色矩阵、申请状态、个人空间保护、邀请过期/次数、X-Space-Id 隔离。
5. 集成测：open 直接加入、approval 审批、invite_only 邀请、拒绝后再次申请、跨空间 file/node 拒绝、管理员权限转移。
6. 前端 E2E：刷新保持空间、前进/后退、搜索→预览→加入、成员批量操作、权限保存即时生效、移动端布局。
7. 回归：旧 `/app/team`、`/app/spaces`、上传/下载/收藏/标签/回收站、公开仓库和分享链接。

## 8. 变更追踪注释规范

所有新增或改变原逻辑的代码均使用中文注释，格式为：

`// [SPACE-COLLAB-<编号>] 改动原因：...；原行为：...；新行为：...；影响范围：...。`

不删除原注释；当旧字段与新字段并存时明确说明兼容映射和后续清理条件。

## 9. 本轮实施状态（2026-07-29）

已落地：

- `space_id` URL 规范化、旧 `space` 兼容读取、无效空间回退、路由前进/后退恢复和 `X-Space-Id` 请求上下文同步。
- 协作空间迁移脚本（加入策略、private 类型、细粒度权限、可重复申请、邀请链接表）及基础 SQL 同步。
- 协作搜索/预览/加入/我的空间/申请取消、成员权限更新、空间设置、审批、邀请链接兑换和安全公开用户资料接口。
- 团队协作发现页、空间预览页、成员页、权限矩阵、设置页、审批页；保留旧 `/app/team`、`/app/spaces` 路径兼容。
- 路径空间与 `X-Space-Id` 一致性拦截，避免成员/设置写操作跨空间。

仍需在真实环境联调确认：

- 数据库迁移需在一份含历史 `uk_space_user_pending`、旧 visibility 值的真实 MySQL 快照上演练；邀请链接需覆盖并发兑换和 Redis/DB 事务回滚。
- 用户搜索应接入网关/IP 级限流配置；当前 Controller 已限制单次返回 20 条，不能替代网关级防滥用策略。
- 前端 E2E 需要真实登录态、至少两个空间和 owner/admin/editor/viewer 四种角色；本地构建只能验证路由和类型，不等同于权限集成测试。

## 10. 全栈审计检查项与未通过项

本专项按 full-stack audit 的安全、数据、访问控制、错误处理、性能、可访问性、SEO/分享、运维和回归维度检查：

| 检查项 | 结果 | 问题/影响 | 修复或后续动作 |
| --- | --- | --- | --- |
| 路由认证与空间上下文 | PASS | 认证守卫、`space_id` 回退、请求头注入已统一 | 继续做浏览器前进/后退 E2E |
| 后端越权/路径头一致性 | PASS（代码级） | 协作 path 与 `X-Space-Id` 不一致直接 403 | 在真实网关环境验证错误码映射 |
| 角色/权限矩阵 | PASS（单测） | 旧权限字段兼容，新管理维度默认拒绝 | 增加自定义权限数据库集成测试 |
| SQL 迁移/索引 | NOT RUN | 未连接真实 MySQL，无法证明历史 enum/index 迁移可重复 | 备份副本演练 `010_space_collaboration.sql`，再上线 |
| 邀请并发与事务 | NOT RUN | 代码有 token hash、次数和事务，但未压力验证竞态 | 用并发兑换测试验证唯一约束和回滚 |
| 用户搜索限流 | PARTIAL | Controller 限制 20 条；网关限流配置未在本专项重新核验 | 接入统一 IP/用户维度限流并监控 429 |
| 前端单元/E2E | NOT RUN | 项目当前 package 无测试脚本，新页面只能通过构建验证 | 增加 Vitest + Playwright，覆盖清单中的核心路径 |
| 性能/可观测性 | PARTIAL | 列表有 loading/Toast，未建立空间协作指标和慢查询告警 | 增加搜索耗时、审批失败、邀请兑换指标 |
| 可访问性 | PARTIAL | 使用原生表单和按钮，尚未运行 axe/键盘完整巡检 | 补充焦点管理、ARIA、键盘导航测试 |
| 移动端 | PASS（布局代码级） | 页面使用响应式网格和横向表格 | 在 iOS Safari/Android Chrome 真机回归 |

上述 NOT RUN/PARTIAL 项不是被忽略的需求，而是当前工作区无法凭静态构建证明的验证项；部署前必须完成对应动作。
