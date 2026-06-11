# PrivateCloudDisk-web

企业级私有云盘前端应用，基于 Vue 3 + Vite + Tailwind CSS 构建的单页面应用 (SPA)。

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue 3 | 3.5 | 前端框架 (Composition API) |
| Vite | 7.3 | 构建工具 |
| Tailwind CSS | 3.4 | 原子化 CSS 框架 |
| Element Plus | 2.14 | UI 组件库 (辅助) |
| Pinia | 3.0 | 状态管理 |
| Vue Router | 4.5 | 路由管理 |
| Axios | 1.16 | HTTP 客户端 |
| GSAP | 3.15 | 高级动画引擎 |
| Three.js | 0.184 | 3D 渲染 (登录页背景) |

---

## 项目结构

```
src/
├── api/                          # API 接口层
│   ├── index.js                  # 统一导出
│   └── modules/
│       ├── users.js              # 用户相关 API (登录/注册/信息/头像)
│       ├── files.js              # 文件操作 API (CRUD/移动/重命名)
│       ├── nodes.js              # 目录节点 API (查询/创建/分页)
│       ├── uploads.js            # 上传 API (会话/分片/合并)
│       ├── downloads.js          # 下载 API (凭证/内容/Range)
│       ├── quotas.js             # 配额 API
│       └── preview.js            # 预览 API
│
├── components/                   # 通用组件
│   ├── common/                   # 基础组件
│   │   ├── EmptyState.vue        # 空状态占位
│   │   ├── LoadingSpinner.vue    # 加载动画
│   │   ├── PageState.vue         # 页面状态 (加载/错误/空)
│   │   └── ToastNotification.vue # Toast 通知
│   │
│   ├── layout/                   # 布局组件
│   │   ├── Layout.vue            # 主布局 (侧边栏 + 内容区)
│   │   ├── Sidebar.vue           # 侧边栏导航
│   │   ├── UserDropdown.vue      # 用户下拉菜单
│   │   └── NotificationCenter.vue# 通知中心
│   │
│   ├── file/                     # 文件操作组件
│   │   ├── FileListView.vue      # 列表视图
│   │   ├── FileGridView.vue      # 网格视图
│   │   ├── PathNavigator.vue     # 路径导航 (面包屑)
│   │   ├── RenameDialog.vue      # 重命名对话框
│   │   ├── MoveCopyDialog.vue    # 移动/复制对话框
│   │   ├── TreeFolderPicker.vue  # 分栏目录选择器
│   │   ├── BatchActionsBar.vue   # 批量操作栏
│   │   ├── StorageInfo.vue       # 存储空间信息
│   │   └── FileDetailDrawer.vue  # 文件详情抽屉
│   │
│   ├── upload/                   # 上传组件
│   │   └── UploadProgressPanel.vue # 上传进度面板
│   │
│   ├── preview/                  # 文件预览组件
│   │   ├── FilePreview.vue       # 预览入口组件
│   │   ├── ImagePreview.vue      # 图片预览
│   │   ├── VideoPreview.vue      # 视频预览
│   │   ├── AudioPreview.vue      # 音频预览
│   │   ├── PdfPreview.vue        # PDF 预览
│   │   ├── CodePreview.vue       # 代码/文本预览 (语法高亮)
│   │   ├── TextPreview.vue       # 纯文本预览
│   │   └── OfficePreview.vue     # Office 文档预览
│   │
│   ├── modals/                   # 模态框
│   │   ├── CreateFolderModal.vue # 新建文件夹
│   │   ├── LoginModal.vue        # 登录弹窗
│   │   ├── UploadConfirmModal.vue# 上传确认
│   │   └── DownloadConfirmModal.vue# 下载确认
│   │
│   ├── share/                    # 分享组件
│   │   ├── CreateShareDialog.vue # 创建分享
│   │   └── ShareLinkItem.vue     # 分享链接项
│   │
│   ├── trash/                    # 回收站组件
│   │   └── TrashItem.vue         # 回收站项
│   │
│   ├── dashboard/                # 工作台组件
│   │   └── WorkspaceOverview.vue # 工作区概览
│   │
│   └── FileUploader.vue          # 文件上传器 (核心)
│
├── stores/                       # Pinia 状态管理 (14 个 Store)
│   ├── authStore.js              # 认证状态 (JWT Token 管理)
│   ├── userStore.js              # 用户信息状态
│   ├── fileBrowserStore.js       # 文件浏览状态 (目录导航/搜索/排序)
│   ├── uploaderStore.js          # 上传状态 (分片/并发/进度/速度)
│   ├── downloaderStore.js        # 下载状态 (凭证/并发/进度)
│   ├── previewStore.js           # 文件预览状态
│   ├── selectionStore.js         # 文件多选状态
│   ├── trashStore.js             # 回收站状态
│   ├── starred.js                # 收藏状态
│   ├── shareStore.js             # 分享状态
│   ├── storageStore.js           # 存储配额状态
│   ├── notificationStore.js      # 通知状态
│   ├── toastStore.js             # Toast 消息状态
│   └── transferHistoryStore.js   # 传输历史状态
│
├── views/                        # 页面视图
│   ├── LoginView.vue             # 登录页 (Three.js 3D 背景 + 动画切换)
│   ├── RegisterView.vue          # 注册页 (与登录页联动动画)
│   ├── DashboardView.vue         # 我的网盘 (主文件浏览页)
│   ├── StarredView.vue           # 收藏文件
│   ├── TrashView.vue             # 回收站
│   ├── SharesView.vue            # 我的分享
│   ├── TransfersView.vue         # 传输记录
│   ├── NotificationsView.vue     # 通知中心
│   ├── ProfileView.vue           # 个人中心 (企业级 UI)
│   └── FilePreviewView.vue       # 文件预览页
│
├── composables/                  # 组合式函数
│   └── useFilePreview.js         # 文件预览逻辑 Hook
│
├── utils/                        # 工具函数
│   ├── request.js                # Axios 实例 (拦截器/错误处理)
│   ├── helpers.js                # 通用辅助函数 (SHA-256/格式化)
│   ├── fileIcon.js               # 文件图标映射
│   ├── constants.js              # 常量配置
│   └── previewHelper.js          # 预览辅助函数
│
├── router/
│   └── index.js                  # 路由配置 (路由守卫)
│
├── App.vue                       # 根组件
├── main.js                       # 入口文件
└── style.css                     # 全局样式
```

---

## 页面路由

| 路径 | 页面 | 认证要求 | 说明 |
|------|------|----------|------|
| `/login` | LoginView | 游客 | 登录页面 |
| `/register` | RegisterView | 游客 | 注册页面 |
| `/` | DashboardView | 需登录 | 我的网盘（主文件浏览页） |
| `/starred` | StarredView | 需登录 | 收藏文件 |
| `/trash` | TrashView | 需登录 | 回收站 |
| `/shares` | SharesView | 需登录 | 我的分享 |
| `/transfers` | TransfersView | 需登录 | 传输记录 |
| `/notifications` | NotificationsView | 需登录 | 通知中心 |
| `/profile` | ProfileView | 需登录 | 个人中心 |

---

## 核心功能详解

### 登录/注册动画
- 使用 **GSAP** 实现页面切换的高阶过渡动画，非简单路由跳转
- 使用 **Three.js** 渲染动态 3D 几何体作为登录页背景
- Vue Router 保证浏览器 URL 同步变化
- 支持 Cloudflare Turnstile 人机验证

### 文件浏览器
- **双视图切换**：列表视图 / 网格视图
- **面包屑导航**：PathNavigator 组件，点击路径任意层级跳转
- **搜索过滤**：关键词搜索 + 文件类型过滤 + 多维度排序
- **分栏目录选择器**：TreeFolderPicker 组件，分栏展示目录层级，支持横向滚动

### 分片上传
- 前端计算文件 SHA-256 校验值
- 创建上传会话 → 并发分片上传 → 通知合并
- 实时进度条 + 上传速度显示
- 支持暂停/取消/断点续传
- 分片大小和并发数可配置

### 流式下载
- 操作凭证签发 → Range 请求分段下载
- 并发下载控制
- 下载进度跟踪

### 企业级 UI 风格
- **Tailwind CSS** 原子化设计，自定义主题色 `primary: #165DFF`
- 统一的 `shadow-card` / `shadow-hover` 阴影系统
- `responsive-panel` 响应式面板样式
- 自定义滚动条、边缘渐隐遮罩
- 移动端适配（响应式断点，侧边栏折叠等）

---

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `VITE_API_BASE_URL` | API 基础路径 | `/api/v1` |
| `VITE_TURNSTILE_SITE_KEY` | Turnstile 站点密钥 | - |
| `VITE_CHUNK_SIZE` | 上传分片大小 (字节) | `5242880` (5MB) |
| `VITE_MAX_CONCURRENT_UPLOADS` | 上传最大并发数 | `3` |
| `VITE_MAX_CONCURRENT_DOWNLOADS` | 下载最大并发数 | `4` |
| `VITE_UPLOAD_THRESHOLD` | 分片上传阈值 (字节) | `10485760` (10MB) |

---

## 开发指南

### 安装依赖
```bash
npm install
```

### 启动开发服务器
```bash
npm run dev
```
开发服务器默认运行在 `http://localhost:5500`，API 请求通过 Vite proxy 转发到 `http://127.0.0.1:8080`。

### 构建生产版本
```bash
npm run build
```
构建产物输出到 `dist/` 目录。

### Docker 部署
```bash
docker build -t privateclouddisk-web .
docker run -p 80:80 privateclouddisk-web
```
生产环境使用 Nginx 作为静态文件服务器，配置参见 `nginx/default.conf`。

---

## 组件交互时序图

### 登录/注册动画切换流程

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant LoginV as LoginView.vue
    participant RegV as RegisterView.vue
    participant Router as Vue Router
    participant GSAP as GSAP 引擎
    participant ThreeJS as Three.js 引擎

    Note over LoginV,ThreeJS: === 从登录页切换到注册页 ===

    User->>LoginV: 点击 "还没有账号？立即注册"
    LoginV->>GSAP: 触发退出动画<br/>tl.to('.login-form', { opacity: 0, x: -50 })
    LoginV->>GSAP: 3D 背景旋转 + 缩放<br/>tl.to('.three-bg', { scale: 0.8, rotation: 0.5 })
    LoginV->>GSAP: 动画完成回调 → onComplete

    GSAP-->>LoginV: 动画完成
    LoginV->>Router: $router.push({ name: 'Register', query: { transition: 'slide-right' } })
    Router->>RegV: 激活 RegisterView 组件
    Router->>RegV: 注入 query: { transition: 'slide-right' }

    RegV->>GSAP: 播放入场动画<br/>tl.from('.register-form', { opacity: 0, x: 50 })
    RegV->>GSAP: 3D 背景恢复<br/>tl.to('.three-bg', { scale: 1, rotation: 0 })
    RegV->>ThreeJS: 重建 3D 场景几何体

    Note over LoginV,ThreeJS: === 从注册页切换回登录页 ===

    User->>RegV: 点击 "已有账号？立即登录"
    RegV->>GSAP: 反向动画<br/>tl.to('.register-form', { opacity: 0, x: 50 })
    RegV->>Router: $router.push({ name: 'Login', query: { transition: 'slide-left' } })

    LoginV->>GSAP: 入场动画<br/>tl.from('.login-form', { opacity: 0, x: -50 })
    LoginV->>ThreeJS: 重建 3D 场景

    User-->>User: URL 变为 /login ← 浏览器历史记录同步
```

### 文件浏览器导航流程

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Dashboard as DashboardView.vue
    participant Store as fileBrowserStore
    participant PathNav as PathNavigator.vue
    participant FileList as FileListView.vue
    participant API as API Layer (axios)
    participant Server as 🚪 Gateway

    User->>Dashboard: 进入页面 / URL参数 ?folder=xxx
    Dashboard->>Store: initFromRoute(route)

    alt 有 folder 参数
        Store->>API: GET /files/{folder_id}
        API-->>Store: 文件元数据
        Store->>Store: 构建面包屑路径<br/>breadcrumb = [root, ..., currentFolder]
    else 默认根目录
        Store->>API: GET /nodes/root/children/paged
        API-->>Store: 根目录内容
    end

    Store-->>Dashboard: currentItems, breadcrumb, loading

    Dashboard->>PathNav: props: { breadcrumb, canGoBack }
    Dashboard->>FileList: props: { items, viewMode, loading }

    User->>PathNav: 点击面包屑 "我的文档"
    PathNav->>Store: navigateToFolder('folder-uuid')
    Store->>Store: 更新 currentPath, push breadcrumb
    Store->>API: GET /nodes/{folder_id}/children/paged
    API-->>Store: folderContents
    Store->>Store: setItems(folderContents)
    Store-->>FileList: 响应式刷新列表

    User->>PathNav: 点击后退按钮 ←
    PathNav->>Store: goBack()
    Store->>Store: pop breadcrumb, 加载父目录
    Store->>API: GET /nodes/{parent_id}/children/paged
    API-->>Store: parentFolderContents
    Store-->>FileList: 响应式刷新

    User->>Dashboard: 切换视图模式 (列表 ↔ 网格)
    Dashboard->>Store: setViewMode('grid')
    Store->>Store: viewMode = 'grid'
    Store-->>Dashboard: 响应式切换 FileGridView.vue 渲染
```

### 文件搜索与排序流程

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Dashboard as DashboardView.vue
    participant Store as fileBrowserStore
    participant API as API Layer
    participant Server as 🚪 Gateway

    User->>Dashboard: 在搜索框输入 "report"

    Dashboard->>Store: setSearchKeyword('report')
    Store->>Store: 防抖 300ms

    Store->>API: GET /nodes/{currentNode}/children/paged?keyword=report&page=1
    API-->>Store: 搜索结果 { items: [...], total: 5 }

    Store->>Store: setItems(searchResults)
    Store->>Store: setSearchActive(true)
    Store-->>Dashboard: 可观察搜索模式激活

    User->>Dashboard: 选择文件类型过滤: PDF
    Dashboard->>Store: setFileTypeFilter('pdf')

    Store->>API: GET /nodes/{currentNode}/children/paged?keyword=report&fileType=pdf
    API-->>Store: 过滤后结果

    User->>Dashboard: 点击列头 "大小" 排序
    Dashboard->>Store: setSortBy('size'), setSortOrder('desc')

    Store->>API: GET /nodes/{currentNode}/children/paged?keyword=report&fileType=pdf&sortBy=size&sortOrder=desc
    API-->>Store: 排序后结果
    Store-->>Dashboard: 列表更新

    User->>Dashboard: 清除搜索
    Dashboard->>Store: clearSearch()
    Store->>Store: keyword='', fileTypeFilter='', searchActive=false
    Store->>API: GET /nodes/{currentNode}/children/paged (无过滤)
    API-->>Store: 完整列表
```

### 分片上传进度追踪流程

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Dashboard as DashboardView.vue
    participant Uploader as FileUploader.vue
    participant Store as uploaderStore
    participant Panel as UploadProgressPanel.vue
    participant API as API Layer
    participant GW as 🚪 Gateway
    participant BS as ⚙ Business

    User->>Dashboard: 拖拽文件 / 点击上传按钮
    Dashboard->>Uploader: 触发 file input

    Uploader->>Uploader: FileReader 读取文件<br/>计算 SHA-256 checksum
    Uploader->>Store: addUploadTask(file)

    Store->>Store: 生成 taskId, 切片 (5MB/chunk)
    Store-->>Panel: 响应式: 显示上传进度条 (0%)

    Note over Uploader,BS: === 创建上传会话 ===
    Store->>API: POST /uploads/<br/>{total_chunks, file_size, checksum, file_name, ...}
    API->>GW: POST /api/v1/business/uploads/
    GW->>BS: 转发
    BS-->>API: 200 { data: "uploads_id" }
    Store->>Store: 保存 uploadsId

    Note over Uploader,BS: === 申请操作凭证 ===
    Store->>API: POST /operation-tokens<br/>{file_id: uploadsId, operation_type: "upload"}
    API-->>Store: 200 { operation_token }

    Note over Uploader,Panel: === 并发分片上传 ===
    Store->>Store: 创建上传队列 (concurrency: 3)

    par 并发上传 (3路)
        Store->>API: POST /files/uploads/{id}/chunks<br/>X-Chunk-Index: 1, Body: chunk1
        API-->>Store: 200 { status: "uploaded" }
        Store->>Store: chunksCompleted[1] = true
        Store->>Store: progress = completedChunks / totalChunks * 100
        Store->>Store: 计算上传速度 (bytes/sec)
        Store-->>Panel: 实时刷新进度 + 速度 + ETA

    and
        Store->>API: X-Chunk-Index: 2, Body: chunk2
        API-->>Store: 200 { status: "uploaded" }
        Store->>Store: chunksCompleted[2] = true

    and
        Store->>API: X-Chunk-Index: 3, Body: chunk3
        API-->>Store: 200 { status: "uploaded" }
        Store->>Store: chunksCompleted[3] = true
    end

    Note over Uploader,Panel: === 异常处理：断点续传 ===
    alt 网络中断 (chunk 5 上传失败)
        Store->>Store: chunksCompleted[5] = false
        Store->>Store: 暂停上传, 保留已完成 chunks
        Store-->>Panel: 显示 "上传暂停，点击继续"

        User->>Panel: 点击 "继续上传"
        Store->>API: POST /files/uploads/{id}/chunks<br/>X-Chunk-Index: 5, Body: chunk5 (重试)
        API-->>Store: 200 { status: "uploaded" }
        Store->>Store: chunksCompleted[5] = true
    end

    Note over Uploader,BS: === 通知合并完成 ===
    Store->>API: POST /uploads/{id}/complete
    API->>BS: 通知业务服务
    BS->>BS: INSERT 文件记录 + 更新配额
    BS-->>API: 200 OK
    Store->>Store: task.status = 'completed'
    Store-->>Panel: ✅ 上传完成 (绿色对勾)
    Store-->>User: Toast: "xxx.pdf 上传成功"
```

### 上传进度面板交互

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Panel as UploadProgressPanel.vue
    participant Store as uploaderStore
    participant Dashboard as DashboardView.vue

    Note over Panel,Store: 批量上传场景: 同时上传3个文件

    User->>Dashboard: 拖入3个文件 → 开始上传
    Dashboard->>Store: addUploadTask(file1, file2, file3)

    Store->>Store: 初始化3个任务 (taskA, taskB, taskC)
    Store-->>Panel: tasks = [taskA(5%), taskB(0%), taskC(0%)]

    Panel->>Panel: 渲染3个进度条

    Store->>Store: taskA: chunk1完成 → 5% → 10% → ...
    Store-->>Panel: taskA.progress = 45%

    User->>Panel: 点击 taskB 的暂停按钮 ⏸
    Panel->>Store: pauseTask('taskB')
    Store->>Store: taskB.status = 'paused'
    Store-->>Panel: taskB 显示 "已暂停"

    Store->>Store: taskA 上传完成 (100%)
    Store-->>Panel: taskA.status = 'completed' ✅
    Store->>Store: 启动 taskB 的后续 chunks
    Store-->>Panel: taskC 并发数变为2

    User->>Panel: 点击 taskC 的取消按钮 ✕
    Panel->>Panel: 弹出确认对话框
    User->>Panel: 确认取消
    Panel->>Store: cancelTask('taskC')
    Store->>Store: 中止 taskC 的 pending HTTP 请求
    Store->>Store: removeTask('taskC')
    Store-->>Panel: tasks = [taskA(done), taskB(60%)]

    Store->>Store: taskB 完成
    Store-->>Panel: tasks = [taskA(done), taskB(done)]

    Panel->>Panel: 所有任务完成，显示 "3个文件上传完成"
    Panel->>Dashboard: @minimize 面板最小化
```

### 分栏目录选择器 (TreeFolderPicker) 交互

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant Dialog as MoveCopyDialog.vue
    participant Picker as TreeFolderPicker.vue
    participant API as API Layer
    participant Server as 🚪 Gateway

    User->>Dialog: 右键文件 → 移动到...

    Dialog->>Dialog: mode='move', selectedFileIds=[id1, id2]
    Dialog->>API: GET /nodes/root/children/paged
    API-->>Dialog: rootChildren: [{id, type:folder}, ...]
    Dialog->>Picker: props: { folderTree: [[...rootChildren]] }

    Picker->>Picker: 渲染第1列 (根目录)
    Picker->>Picker: 第1列 header: "根目录" (count: 5)

    User->>Picker: 点击第1列 "文档" 文件夹
    Picker->>Dialog: @select-node('doc-node-id', colIndex=0)

    Dialog->>Dialog: 记录选中的节点路径
    Dialog->>API: GET /nodes/doc-node-id/children/paged
    API-->>Dialog: docChildren: [{id, type:folder}, {id, type:file}, ...]
    Dialog->>Picker: folderTree.push(docChildren) → 追加第2列

    Picker->>Picker: 渲染第2列
    Picker->>Picker: column-header: "文档" (count: 12)
    Picker->>Picker: autoScrollToColumn(1) 平滑滚动

    User->>Picker: 点击第2列 "项目" 文件夹
    Picker->>Dialog: @select-node('project-node-id', colIndex=1)
    Dialog->>API: GET /nodes/project-node-id/children/paged
    API-->>Dialog: projectChildren: [...]
    Dialog->>Picker: folderTree.push(projectChildren) → 追加第3列

    Picker->>Picker: 渲染第3列 (column-active)
    Picker->>Picker: autoScrollToColumn(2)
    Picker->>Picker: 显示右侧渐隐遮罩 (列数 > 可视区)

    User->>Picker: 横向滚动查看前面列
    Picker->>Picker: 检测滚动位置 → 显示左/右渐隐遮罩

    User->>Picker: 第3列选中 "project" → 点击确定
    Picker->>Dialog: @confirm('project-node-id')

    Dialog->>API: PATCH /files/{file_id}/position<br/>{target_node_id: 'project-node-id'}
    API-->>Dialog: 200 OK
    Dialog->>Dialog: close() + emit('success')
    Dialog-->>User: ✅ 文件移动成功
```

### 个人中心编辑流程

```mermaid
sequenceDiagram
    actor User as 👤 用户
    participant ProfileV as ProfileView.vue
    participant API as API Layer
    participant Store as userStore
    participant Server as 🚪 Server

    Note over ProfileV,Server: === 加载个人资料 ===
    ProfileV->>API: GET /users/me
    API-->>ProfileV: { name, email, phone, account, image }

    ProfileV->>Store: updateProfile(data)
    ProfileV->>ProfileV: 渲染只读模式

    User->>ProfileV: 点击 "编辑资料" 按钮
    ProfileV->>ProfileV: isEditing = true
    ProfileV->>ProfileV: editForm = { name, email, phone }
    ProfileV->>ProfileV: 渲染编辑表单

    User->>ProfileV: 修改昵称 + 邮箱
    User->>ProfileV: 点击 "保存" 按钮

    ProfileV->>ProfileV: saving = true
    ProfileV->>API: PATCH /users/me<br/>{new_username, new_email, new_phone_number}
    API-->>ProfileV: 200 OK

    ProfileV->>API: GET /users/me (刷新)
    API-->>ProfileV: 更新后的用户数据
    ProfileV->>Store: updateProfile(freshData)
    ProfileV->>ProfileV: isEditing = false, saving = false
    ProfileV-->>User: Toast: "资料更新成功"

    Note over ProfileV,Server: === 头像上传 ===
    User->>ProfileV: 点击头像 → 选择图片
    ProfileV->>ProfileV: handleAvatarUpload(file)
    ProfileV->>API: PUT /users/me/avatar<br/>Content-Type: multipart/form-data<br/>Body: { avator_file }
    API-->>ProfileV: 200 OK
    ProfileV->>ProfileV: 刷新头像显示

    Note over ProfileV,Server: === 修改密码 ===
    User->>ProfileV: 安全设置 → 修改密码
    ProfileV->>ProfileV: showPasswordSheet = true

    User->>ProfileV: 输入原密码 + 新密码 + 确认
    ProfileV->>API: POST /users/me/password<br/>{old_password, new_password}
    API-->>ProfileV: 200 OK
    ProfileV->>ProfileV: showPasswordSheet = false
    ProfileV-->>User: Toast: "密码修改成功"
```

---

## 状态管理架构

### Pinia Store 依赖关系

```mermaid
graph TB
    subgraph Auth["认证层"]
        AuthStore["authStore<br/>├─ token (JWT)<br/>├─ isAuthenticated<br/>├─ login()<br/>├─ logout()<br/>└─ refreshToken()"]
    end

    subgraph User["用户层"]
        UserStore["userStore<br/>├─ profile<br/>├─ quota<br/>├─ fetchProfile()<br/>└─ updateProfile()"]
    end

    subgraph Core["核心业务层"]
        FileBrowser["fileBrowserStore<br/>├─ currentItems[]<br/>├─ breadcrumb[]<br/>├─ searchKeyword<br/>├─ viewMode<br/>├─ sortBy / sortOrder<br/>├─ navigateTo()<br/>└─ search()"]

        Selection["selectionStore<br/>├─ selectedIds[]<br/>├─ selectAllMode<br/>├─ toggleSelect()<br/>└─ clearSelection()"]
    end

    subgraph Transfer["传输层"]
        Uploader["uploaderStore<br/>├─ tasks[]<br/>├─ activeCount<br/>├─ overallProgress<br/>├─ addTask()<br/>├─ pauseTask()<br/>└─ cancelTask()"]

        Downloader["downloaderStore<br/>├─ downloads[]<br/>├─ startDownload()<br/>├─ pauseDownload()<br/>└─ getProgress()"]
    end

    subgraph Feature["功能层"]
        Trash["trashStore<br/>├─ items[]<br/>├─ fetchTrash()<br/>├─ restore()<br/>└─ permanentDelete()"]

        Starred["starred store<br/>├─ items[]<br/>├─ toggleStar()<br/>└─ isStarred()"]

        Share["shareStore<br/>├─ links[]<br/>├─ createShare()<br/>└─ revokeShare()"]

        Preview["previewStore<br/>├─ currentFile<br/>├─ previewType<br/>└─ openPreview()"]
    end

    subgraph UI["UI 层"]
        Storage["storageStore<br/>├─ total / used / percent<br/>└─ fetchQuota()"]

        Notification["notificationStore<br/>├─ notifications[]<br/>└─ markRead()"]

        Toast["toastStore<br/>├─ messages[]<br/>├─ success()<br/>├─ error()<br/>└─ warning()"]

        TransferHistory["transferHistoryStore<br/>├─ history[]<br/>└─ addRecord()"]
    end

    AuthStore -->|"登录后 fetch"| UserStore
    AuthStore -->|"认证过期"| UI

    FileBrowser -->|"选择文件"| Selection
    FileBrowser -->|"导航变化"| Storage

    Uploader -->|"完成后刷新"| FileBrowser
    Uploader -->|"完成后更新"| Storage
    Uploader -->|"完成后通知"| Toast
    Uploader -->|"记录历史"| TransferHistory

    Downloader -->|"完成后通知"| Toast
    Downloader -->|"记录历史"| TransferHistory

    Trash -->|"恢复成功"| FileBrowser
    Trash -->|"操作结果"| Toast

    Starred -->|"操作结果"| Toast
    Share -->|"操作结果"| Toast

    Preview -->|"打开预览"| Downloader

    style Auth fill:#ffcdd2
    style User fill:#fff9c4
    style Core fill:#c8e6c9
    style Transfer fill:#b3d9ff
    style Feature fill:#e1bee7
    style UI fill:#ffe0b2
```

### API 层请求拦截流程

```mermaid
sequenceDiagram
    participant Component as Vue 组件
    participant API as API Module
    participant Axios as request.js (Axios)
    participant GW as 🚪 Gateway
    participant BS as ⚙ Business

    Component->>API: usersApi.login({account, password})
    API->>Axios: axios.post('/users/login', data)

    Note over Axios: === 请求拦截器 ===
    Axios->>Axios: 从 localStorage 获取 token
    alt token 存在
        Axios->>Axios: 注入 Authorization: Bearer {token}
    end
    Axios->>Axios: URL 前缀: /api/v1/business
    Axios->>GW: POST /api/v1/business/users/login

    GW-->>Axios: Response { status, headers, data }

    Note over Axios: === 响应拦截器 ===
    Axios->>Axios: 检查 HTTP status
    alt status === 401
        Axios->>Axios: 清除 token → 跳转 /login
    else status === 429
        Axios->>Axios: Toast: "请求过于频繁"
    else status >= 500
        Axios->>Axios: Toast: "服务器错误"
    end

    Axios->>Axios: 检查 response.data.code
    alt code !== 200
        Axios->>Axios: throw new ApiError(code, message)
    end

    Axios-->>API: response.data
    API-->>Component: 解析后的数据
```

### 路由守卫生命周期

```mermaid
flowchart TD
    Start(["🌐 浏览器导航"]) --> GlobalBefore["router.beforeEach<br/>全局前置守卫"]

    GlobalBefore --> HasToken{"localStorage<br/>有 token?"}

    HasToken -->|"是"| CheckRoute{"目标路由<br/>是登录/注册页?"}
    CheckRoute -->|"是"| RedirectHome["重定向到 /<br/>(已登录用户不需要再登录)"]
    CheckRoute -->|"否"| HasUser{"userStore<br/>已加载用户信息?"}

    HasUser -->|"是"| Allow["✅ 放行"]
    HasUser -->|"否"| FetchUser["API: GET /users/me<br/>加载用户信息"]

    FetchUser --> FetchOK{"请求成功?"}
    FetchOK -->|"是"| Allow
    FetchOK -->|"否 (401)"| ClearToken["清除 localStorage token"]
    ClearToken --> RedirectLogin

    HasToken -->|"否"| NeedAuth{"目标路由<br/>需要认证?"}
    NeedAuth -->|"否<br/>(登录/注册页)"| Allow
    NeedAuth -->|"是"| RedirectLogin["重定向到 /login<br/>query: { redirect: 原路径 }"]

    Allow --> LoadComponent["加载组件<br/>beforeResolve 守卫"]
    LoadComponent --> Render["渲染页面"]
    Render --> AfterEach["router.afterEach<br/>全局后置钩子"]
    AfterEach --> UpdateTitle["document.title 更新"]
    AfterEach --> ScrollToTop["window.scrollTo(0, 0)"]
    AfterEach --> End(["✅ 导航完成"])

    RedirectHome --> End
    RedirectLogin --> End

    style Allow fill:#c8e6c9
    style RedirectLogin fill:#ffcdd2
    style RedirectHome fill:#fff9c4
    style ClearToken fill:#ffcdd2
```

---

## 组件设计原则

- **Composition API**：全部使用 `<script setup>` 语法
- **Pinia Store**：业务逻辑集中在 Store 中，组件保持简洁
- **响应式设计**：所有组件均适配桌面端和移动端
- **错误边界**：统一的 ApiError 类、Toast 通知、Loading/Empty/Error 三态组件
- **可访问性**：语义化 HTML + ARIA 属性