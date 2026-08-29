# Git 类型公开仓库页面 UI/UX 审计与交付说明

> 审计日期：2026-08-16  
> 需求标识：`REQ-GIT-UIUX-20260816`  
> 范围：仅 `resource_type=git` 的公开空间页面和为该页面补齐的 Git Service 查询/流式接口。普通 `file` 空间继续使用原有文件浏览器。

## 审计范围与原始基线

审计覆盖下列路由、组件、前端 API 和 Git Service 端点：

- 路由：[PrivateCloudDisk-web/src/router/index.ts](/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-web/src/router/index.ts)，公开仓库入口为 `/repo/:spaceId`。
- 空间分派：[PublicSpaceView.vue](/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-web/src/views/public-space/PublicSpaceView.vue)，仅当 `resource_type=git` 时挂载 Git 面板。
- 原页面：[GitRepositoryPanel.vue](/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-web/src/views/public-space/GitRepositoryPanel.vue)，原来将目录、文本内容和线性提交列表堆叠在同一页面中。
- 前端契约：[git.ts](/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-web/src/api/modules/git.ts)。
- Git 查询与授权：[repository_content.go](/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-git-service/internal/api/repository_content.go)、[query.go](/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-git-service/internal/gitrepo/query.go)、[authorization.go](/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-git-service/internal/auth/authorization.go)。

原实现已有树、Blob、README、提交、Diff、Blame、分支/标签、合并请求和工作流绑定的基础接口；但存在以下体验和契约缺口：

1. 桌面端没有常驻目录树，阅读文件时丢失仓库上下文。
2. Blob 以 JSON 字符串返回，不能正确处理图片、PDF、媒体和二进制文件；大文件也容易超过 API 输出限制。
3. 提交只按列表展示，未利用已有 `parents` 数据形成拓扑关系。
4. 缺少文件历史联动、代码行锚点、按文件查历史、下载 ZIP 和真实仓库统计。
5. 页面存在浅色硬编码，移动端、键盘操作、加载/错误状态和路由上下文保持不完整。

## 落地设计

### 页面结构与响应式行为

`GitRepositoryPanel` 现在只负责公开空间到 Git 仓库的协调、Tab、URL 状态和权限相关操作；代码浏览、文件树、文件预览、提交图被拆到独立组件：

| 组件 | 责任 |
| --- | --- |
| [GitCodeWorkspace.vue](/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-web/src/components/git/GitCodeWorkspace.vue) | ≥1024px 左树右预览；小于该断点回退为上下布局；记忆左右/上下偏好 |
| [GitFileTree.vue](/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-web/src/components/git/GitFileTree.vue) | 延迟加载树节点、搜索、键盘导航、虚拟窗口、折叠/右键操作 |
| [GitFileViewer.vue](/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-web/src/components/git/GitFileViewer.vue) | 路径面包屑、代码/Markdown/图片/PDF/媒体/二进制分流、行号、Blame、下载、全屏 |
| [GitCommitGraph.vue](/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-web/src/components/git/GitCommitGraph.vue) | 提交拓扑、筛选、分页加载、提交详情、文件 Diff |
| [gitCommitGraph.ts](/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-web/src/utils/gitCommitGraph.ts) | 根据真实父提交关系计算 lane 和 edge，不伪造分支关系 |

浏览器路由会保存 `tab`、`ref`、`path` 和 `line`。因此分支切换、文件定位、行锚点、前进/后退和分享链接都可恢复相同上下文；无效 ref 则在实际分支/标签加载后回退到默认分支。

### 内容、下载与权限数据流

```text
PublicSpaceView(resource_type=git)
  -> GitRepositoryPanel
     -> GitCodeWorkspace -> tree / blob-preview
                           -> raw file (image/PDF/media/download)
     -> GitCommitGraph  -> commits / diff / blame
     -> README / MR / CloudFlow binding / insights
  -> Git Service
     Metadata: browse tree, preview, commits, insights
     Fetch:    raw stream and ZIP archive
```

`allow_public_browse` 仅允许元数据浏览；`allow_public_download` 与浏览权限共同决定匿名 Fetch。原始文件和 ZIP 端点强制使用 `auth.Fetch`，不会因前端预览增加而绕过空间的下载限制。`ReadBlobPreview` 会先查询 Git 对象大小与 MIME，再返回安全的文本预览元数据；图片、PDF、音视频及下载通过原始流端点取得 `Blob`。`GIT_MAX_RAW_FILE_BYTES`（默认 128 MiB）限制单次原始 API 输出，超限返回 `413`。

ZIP 使用原生 `git archive` 流式生成。归档前先验证 ref，避免原来的无效引用在已开始写 HTTP 200 后才失败；流中断仍由 Git 客户端或浏览器安全重试。

### GitHub/IDE 体验映射

| 能力 | 实现 |
| --- | --- |
| 桌面左右分栏/移动上下布局 | 工作区断点和本地偏好；1024–1366px 使用紧凑目录宽度 |
| 大型文件树 | 目录懒加载、搜索、虚拟窗口、键盘/右键操作、可折叠树 |
| 内容类型 | highlight.js 代码高亮，已有 Markdown/PDF/Image 预览组件，二进制安全提示；超 2 MiB 文本保留前 5000 行并提供完整下载 |
| 代码阅读 | 行号、行 URL、复制、自动换行、全屏、Blame、文件历史 |
| 提交图 | 基于 `parents` 的 lane/edge，拓扑或日期排序、作者/分支/关键词过滤、50 条游标分页、Diff |
| README/MR/自动化 | GFM README；MR 状态、评论、审批与合并；`pcd.git.push.completed.v1` 到 CloudFlow 绑定 |
| 仓库操作 | 分支/标签管理、PAT/SSH 克隆说明、原生 ZIP 下载、真实的提交/分支/标签/贡献者/语言统计 |
| 主题与可访问性 | 深浅色变量、加载骨架、错误重试、Tooltip/aria 标签、键盘快捷键（`T`、`Ctrl/Cmd+F`、`Esc`） |

统计不展示数据库中没有事实来源的 Star/Fork 数。提交数、分支、标签、贡献者和语言均从 Git refs、提交与对象树派生，避免虚构指标。

## 后端接口增补

| 方法 | 接口 | 用途 |
| --- | --- | --- |
| GET | `/git/repos/{repoID}/blob` | 安全文本预览：MIME、大小、二进制/截断标记、行数 |
| GET | `/git/repos/{repoID}/raw?ref=&path=&download=1` | 图片/PDF/媒体/原文件流；Fetch 权限 |
| GET | `/git/repos/{repoID}/archive?ref=` | 当前引用 ZIP；Fetch 权限 |
| GET | `/git/repos/{repoID}/commits?path=&author=&since=&until=` | 图形提交记录和文件历史 |
| GET | `/git/repos/{repoID}/insights` | 真实仓库统计、语言和贡献者 |
| GET/POST | `/git/repos/{repoID}/merge-requests/{mrID}/comments` | 合并请求讨论 |
| GET | `/git/repos/{repoID}/merge-requests/{mrID}` | 合并请求详情 |

现有 branch/tag、Diff、Blame、README、MR 审批/合并、工作流绑定接口继续保留，不改变 Git Smart HTTP 或 SSH 的协议路径。

## 验证记录与验收边界

本次在源码工作区完成的可重复验证：

| 验证 | 目的 | 结果 |
| --- | --- | --- |
| `GOCACHE=/tmp/pcd-git-ui-gocache go test ./...` | Git Service 单元/包级回归 | 通过 |
| `npx vue-tsc --noEmit`（筛选本次 Git 目录） | Vue 模板与 TypeScript 类型契约 | 本次 Git 目录无诊断 |
| `npm run build` | 生产构建、组件解析与打包 | 通过 |
| `node --test tests/git-repository-ui-contract.test.mjs` | 工作区/提交图/权限与流式接口防回归契约 | 通过 |

全仓 `npx vue-tsc --noEmit` 仍被现有的非 Git 模块阻断（如通用表格、文件选择器、网站页和既有 API 重复导出），这些文件不在本次改动范围；已单独筛选并确认 `components/git`、`GitRepositoryPanel`、Git API/工具目录无 TypeScript 诊断。

真实 Git HTTP、Storage Broker、Platform 授权、CloudFlow 及浏览器媒体预览需要在已启动的多服务环境进行联调。本次审计时 Git Service 默认 HTTP/SSH 端口 `127.0.0.1:8091`/`127.0.0.1:2222`、Gateway `localhost:8080` 以及 MySQL `127.0.0.1:3306` 均不可连接，因此没有将“真实 clone/push、100 并发 clone、10 万提交滚动、不同浏览器缩放”冒充为已完成的实测数据。部署验收应至少覆盖：匿名浏览与下载禁用、PAT 读写、SSH、二进制/2MB+文本、分叉/合并提交、移动端 320px 和桌面 1366×768/1920×1080。

## 维护注意事项

- 代码高亮和 PDF 复用项目现有的动态加载器；离线或 CDN 不可用时应保留原始文本下载/错误提示，不将远程资源失败视为仓库读取成功。
- 增加新的 Git 内容类型时，先在 `gitRepositoryPresentation.ts` 做分类，再决定是否调用原始流；不要把二进制内容重新塞回 JSON。
- 新增公开空间资源类型必须经 `SpaceResourceProvider` 注册，不能让 Git 页面接管普通文件空间。
