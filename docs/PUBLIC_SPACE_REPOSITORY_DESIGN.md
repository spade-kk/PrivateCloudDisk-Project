# 公开空间（仓库）重构设计与审计报告

> 版本：v1.0.0（2026-07-29）  
> 范围：PrivateCloudDisk-web、PrivateCloudDisk-platform-service、PrivateCloudDisk-db  
> 变更原则：保留原有分享链接与控制台文件业务；公开空间新增为独立的、登录后可访问的仓库产品。

## 1. 审计结论（编码前）

### 1.1 已确认的数据流

```text
登录用户 → router.beforeEach(meta.requiresAuth)
        → /app Layout → Sidebar → SpaceSelector → spaceStore → X-Space-Id
        → NodeController/FileController → platform-service → space/file/node 表

分享链接 → /share/:token → ShareAccessView → ShareController 的 public/share API
          → 独立的分享访问令牌；可匿名、可密码、可过期，不使用空间切换上下文
```

现有 `SpaceController` 已提供空间 CRUD、发现接口和成员接口，`SpaceEntity` 已有 `space_type=public` 与 `space_visibility=public`，文件和目录表已具备 `file_space_id/node_space_id`。但是公开空间目前仍被当成普通空间：

* `SpaceSelector` 会把 `public` 空间放入工作空间切换列表；这与“公开仓库不属于工作空间”冲突。
* `SpaceServiceImpl` 的公开空间发现接口没有登录门槛，也没有 `allow_public_browse/download/upload` 细分权限。
* 控制台 `/app` 下只有通用空间管理与团队协作页，没有独立仓库布局、用户主页、探索页。
* `NodeController` 的目录查询依赖当前空间上下文，公开仓库页面没有独立的只读仓库 API 适配层。
* `ShareAccessView`、`ShareController` 和 `shares.ts` 已形成完整的临时分享链路，应保持原路径和令牌模型，不与仓库复用页面或权限。

旧 `/business/spaces/public/*` 路径仍保留以兼容已有 SDK，但已补充登录请求头并复用 `allow_public_browse` 过滤；匿名访问只允许走 `/share/:token`。
通用空间更新接口对 `space_type=public` 强制保持 `space_visibility=public`，防止旧 DTO 绕过仓库边界。

### 1.2 组件/路由清理审计

|对象|引用结论|处理|
|---|---|---|
|`SpaceSelector.vue`|由 `Sidebar.vue` 使用|保留；过滤公开仓库并保留个人/团队/企业空间切换|
|`FileBrowser` 及其文件组件|控制台主文件页使用|保留；公开仓库不复用|
|`ShareAccessView.vue`、`components/share/*`、`shares.ts`|分享访问/管理页面使用|保留；不得迁移到仓库|
|`SpaceManagementView.vue`、`SpaceManageDialog.vue`、`CreateSpaceDialog.vue`|空间管理及创建流程使用|保留；仅补充公开仓库配置字段，不删除|
|`TeamView.vue`|`/app/team` 唯一路由引用，但当前为静态演示数据|保留路由以兼容旧入口；本次不把公开仓库塞入团队页，后续可独立接入真实成员 API|
|旧公开空间页面|全局未发现独立仓库实现|无需删除；新增独立 `/repo`、`/user`、`/explore` 页面|

因此本次没有满足“全局无引用后可删除”条件的空间文件；贸然删除会破坏现有导航或分享功能。清理动作改为删除重复的公开空间入口语义（SpaceSelector 中的 public 选项、创建/管理对话框中的成员相关选项）而不是删除被引用文件。

### 1.3 全栈审计重点（Full-stack Audit）

|类别|结果|影响/处理|
|---|---|---|
|鉴权|控制台有 `meta.requiresAuth`；分享页明确匿名|新增仓库/主页/探索路由全部 `requiresAuth`，后端要求 `X-User-Id`|
|访问控制|旧 public 发现只按 `space_type/visibility`|新增仓库服务校验 active、visible、浏览/下载/上传开关及所有者设置权限|
|数据隔离|文件/目录已有 space 字段，查询通过 SpaceContext|仓库 API 显式绑定 `space_id`，拒绝跨空间 node/file|
|输入校验|空间 CRUD 有 DTO 校验，公开配置尚无字段|新增布尔字段 DTO；名称/描述沿用长度校验；所有 UUID 做格式校验|
|分享边界|分享链接有独立 token、密码和有效期|保持不变；仓库不生成分享令牌，不允许匿名绕过登录|
|缓存/性能|空间列表和文件节点为普通查询|新增公开仓库查询索引；前端列表懒加载，避免一次性递归加载全部目录|
|响应式/无障碍|控制台已有响应式基础类|仓库使用独立 CSS，桌面双栏、平板折叠右栏、移动单栏；按钮提供 aria-label|
|错误处理|统一 `JsonResult`|仓库 API 使用 401/403/404 语义码映射，并在前端提供重试与空状态|
|可观测性|后端已有请求日志|仓库接口记录 space_id、user_id、操作与耗时；不记录敏感令牌|

## 2. 产品边界：仓库与分享链接

|维度|公开空间（仓库）|分享链接|
|---|---|---|
|资源归属|独立、持久的 space 存储区域|引用已有个人/团队/企业空间资源|
|入口|`/repo/:spaceId`、用户主页、探索、空间搜索|`/share/:token`|
|登录|所有详情、主页、探索均要求登录|按原设计可匿名，密码由分享访问令牌保护|
|生命周期|所有者主动维护，不自动过期|可设置有效期、撤销、密码|
|能力|文件浏览、README、下载、可选上传、设置、统计预留|查看/下载为主，不具备仓库管理能力|
|权限|所有者统一配置 browse/download/upload，无成员概念|分享创建者配置密码、有效期和下载能力|

## 3. 数据模型与迁移

在 `pcd_space_table` 增加：

* `allow_public_browse TINYINT(1) NOT NULL DEFAULT 1`；
* `allow_public_download TINYINT(1) NOT NULL DEFAULT 1`；
* `allow_public_upload TINYINT(1) NOT NULL DEFAULT 0`；
* `idx_public_repository (space_type, space_visibility, space_status, space_updated_at)`。

仅 `space_type=public` 使用上述字段；创建时强制 `space_visibility=public`，`join_policy` 语义固定为 `invite_only`（当前表未单独存储 join_policy，服务层拒绝公开空间加入/成员操作）。历史 public 记录迁移为 browse/download=true、upload=false。个人/团队/企业空间字段默认值不改变原有逻辑。

文件/目录查询必须同时满足 `space_id` 与 active 状态；仓库详情返回脱敏的 owner 信息和权限开关，不返回物理存储路径、内部成员表或下载令牌。

## 4. API 契约

统一前缀 `/business/public-spaces`，所有接口要求登录（网关注入 `X-User-Id`）：

|方法|路径|说明|
|---|---|---|
|GET|`/{spaceId}`|仓库详情、owner、权限摘要、统计|
|GET|`/{spaceId}/root`|根目录节点|
|GET|`/{spaceId}/nodes/{nodeId}/children`|当前目录一级子节点（懒加载）|
|GET|`/{spaceId}/readme`|根目录 README 文件 ID；前端使用预览授权接口读取原文，不返回物理路径|
|POST|`/{spaceId}/uploads`|在 `allow_public_upload=true` 时创建登录用户上传会话，复用既有分片合并流程|
|PATCH|`/{spaceId}`|仅 owner 修改名称、描述和三项公开开关|
|GET|`/users/{username}`|用户主页及其公开仓库列表|
|GET|`/explore`|关键词、推荐/最新公开仓库|
|GET|`/search`|全局“空间”搜索结果|

下载不另造一套文件格式：前端在通过仓库 API 校验后，继续调用已有文件下载授权接口并携带 `X-Space-Id`。上传使用仓库专用会话入口完成首次仓库权限校验，随后复用既有分片/合并接口；被调用的文件服务再次校验仓库开关，形成纵深防御。禁止从仓库 API 返回物理路径。

错误码约定：`401` 未登录、`403` 仓库不可浏览/下载/上传或非 owner、`404` 仓库/节点不存在、`409` 名称冲突、`422` 字段校验失败。

## 5. 前端信息架构

公开仓库完全独立于控制台布局，仅复用底层 Markdown 渲染、请求封装和文件图标工具：

```text
PublicRepositoryLayout
├─ RepositoryHeader（owner、仓库名、Public、Star/Fork 预留、设置）
├─ RepositoryTabs（Files / README / Settings）
├─ RepositoryFiles（独立树 + 当前目录列表 + 面包屑 + 上传/下载）
├─ RepositoryReadme（README.md 渲染）
├─ RepositorySettings（owner-only 三项开关、名称、描述）
└─ RepositoryAbout（描述、权限摘要、统计占位）

UserProfileView（左用户卡 + 右仓库列表）
ExplorePublicSpacesView（搜索、推荐/最新仓库）
```

路由：`/repo/:spaceId`、`/repo/:spaceId/settings`、`/user/:username`、`/explore`，均配置 `meta.requiresAuth=true`；不嵌套 `/app`，不渲染侧栏/控制台顶栏。SpaceSelector 不展示 public 类型，只保留工作空间。

响应式：`≥1280px` 显示树/列表/关于三栏；`768-1279px` 折叠关于栏；`<768px` 目录树改为抽屉，列表卡片化，所有操作触控尺寸≥44px。

## 6. 交互与状态

页面级状态使用 `ref/computed`，不污染 `spaceStore`（仓库不是当前工作空间）。打开仓库时按顺序请求详情→root→children；目录点击只刷新当前列表并更新 breadcrumb。保存设置采用乐观禁用按钮、成功 Toast、返回 Files 自动刷新；离开页面不修改控制台当前空间。

下载、上传、预览按钮在权限关闭时隐藏并提供原因提示。加载使用骨架/Spinner，404/403 使用 PageState，网络失败提供“重试”。所有用户可见文本为中文，接口字段保持后端命名规范。

## 7. 与分享链接的隔离约束

不得在仓库页调用 `getShareInfoApi`、`verifySharePasswordApi`、`getShareContentApi` 或 `ShareAccessView`；不得在分享页注入 `X-Space-Id` 作为访问凭证。分享路由仍是唯一匿名入口，仓库路由永远重定向登录页。

## 8. 测试与验收矩阵

1. 路由：匿名访问四个仓库路由均重定向 `/login`；已登录访问成功；分享链接匿名行为不变。
2. 隔离：不同 space_id 的同名/同 node_id 不可交叉读取；非 owner 修改设置返回 403。
3. 开关：browse=false 禁止目录/README；download=false 隐藏下载并由后端拒绝；upload=true 仅登录用户显示上传并由后端校验。
4. 文件：懒加载目录、README 缺失、空仓库、超长文件名、移动端抽屉、返回/刷新。
5. 搜索/发现：仅返回 active + visible + public；团队/企业空间不进入结果；用户主页只显示本人公开仓库。
6. 回归：SpaceSelector、FileBrowser、`/app/team`、`/app/shares`、`ShareAccessView`、文件下载/上传全量回归。
7. 构建：`npm run type-check`、`npm run build`、后端 `./gradlew test`；`git diff --check`。

## 9. 实施顺序与变更对比

1. 先迁移字段并扩展 SpaceEntity/Mapper；
2. 增加公共仓库 Service/Controller 与前端 SDK；
3. 增加独立仓库布局、用户主页、探索页、路由守卫；
4. 调整 SpaceSelector、创建/管理对话框、全局搜索 Tab 和侧栏探索入口；
5. 完成测试与旧功能回归。

|改造前|改造后|
|---|---|
|public 空间混入工作空间下拉|public 作为仓库独立入口，不进入 SpaceSelector|
|只有通用 SpaceManagement/静态 Team 页面|新增 GitHub 风格独立仓库、主页、探索|
|发现接口缺少细粒度开关|三项公开权限字段由 owner 配置并由后端二次校验|
|分享与公开空间概念容易混淆|保留 `/share/:token` 原链路，仓库使用 `/repo/:spaceId` 且必须登录|
