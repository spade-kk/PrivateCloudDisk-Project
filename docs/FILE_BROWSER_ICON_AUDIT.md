# 文件浏览器图标与缩略图审计报告

<!-- AUDIT FIX [1.1-1.12/9.1-9.12]：本报告记录本次实际代码审计与落地范围。 -->

## 审计结论

审计对象为 Web 端文件浏览器的网格视图、列表视图、缩略图缓存和预览服务接口：

- `PrivateCloudDisk-web/src/components/file/FileGridView.vue`：原来在节点内直接拼接 Font Awesome 类名；图片、视频、PDF、Office 进入 `ThumbnailImage`，其他文件另走旧图标函数。
- `PrivateCloudDisk-web/src/components/file/FileListView.vue`：存在另一份重复的文件图标分支，和网格视图没有共享类型描述。
- `PrivateCloudDisk-web/src/components/file/TreeFolderPicker.vue`：列式文件夹选择器原来仍直接使用通用 Font Awesome 文件/文件夹图标，本次同步接入统一组件。
- `PrivateCloudDisk-web/src/components/file/ThumbnailImage.vue`：请求异常时虽有占位分支，但占位逻辑把字符串类名按数组下标读取，且 `<img>` 没有处理浏览器解码失败；空 Blob/错误 MIME 可能形成空白节点。
- `PrivateCloudDisk-web/src/utils/imageCache.ts`：已有鉴权 Blob、LRU、请求去重、指数重试和 15 秒超时；本次补充空响应/JSON/HTML 响应拒绝，并支持淘汰已确认损坏的 object URL。
- `PrivateCloudDisk-storage-service/app/api/v1/endpoints/files.py`：普通图片缩略图支持资源台账、磁盘和动态生成回退，最终可能返回 404/500。
- `PrivateCloudDisk-storage-service/app/api/v1/endpoints/video_stream.py`：视频首帧走独立接口，预生成或 ffmpeg 失败时可能不可用。
- `PrivateCloudDisk-storage-service/app/api/v1/endpoints/preview_resources.py`：Office/PDF 封面依赖 `office_thumbnail` 资源台账，资源未生成时返回 404。

因此，空白问题的根因不是布局容器，而是“异步缩略图失败”和“旧图标映射”两条链路没有共享一个可靠的兜底组件。

## 当前可复用能力

| 能力 | 现状 | 本次处理 |
| --- | --- | --- |
| 缩略图鉴权 | `imageCache` 使用 Axios Blob 并附带 Token | 保留 |
| 请求去重/LRU | 已存在 | 保留并增加坏 Blob 淘汰 |
| 图片/视频/文档接口 | 已分离 | 保留，组件统一接收失败 |
| Font Awesome | 页面全局 CDN 已加载 | 非文件浏览器旧组件继续兼容；文件类型图标不再依赖它 |
| VS Code Icons | 原来未引入成熟文件图标库 | 使用 npm 安装的 `@iconify-json/vscode-icons/icons.json`，通过 `@iconify/vue/offline` 完整离线注册 |
| 主题 | Tailwind `.dark` 与页面变量 | 新 SVG 使用颜色变量和暗色调整 |
| 网格/列表布局 | 已有固定父容器和响应式尺寸 | 不修改布局规则 |

## 新的解析优先级

1. 特殊目录名 → VS Code Icons 专属目录图标。
2. 普通目录 → VS Code Icons 的 `default-folder`。
3. 完整文件名/路径规则 → VS Code Icons 的 Docker、Git、CI、IDE、包管理、许可证等。
4. MIME 类型 → 图片、视频、音频、PDF、Office、压缩包、文本。
5. 后缀映射 → VS Code Icons 的代码、配置、媒体、文档、压缩包、二进制等。
6. 未知后缀 → 最终兜底使用稳定哈希颜色的动态 SVG，显示最多 3 个大写字符；无后缀显示 `FILE`。

## 覆盖范围

实际运行时数量为 257 个扩展名、176 个特殊文件名、160 个特殊目录名，由 `FILE_ICON_EXTENSION_COUNT`、`FILE_ICON_SPECIAL_FILE_COUNT`、`FILE_ICON_SPECIAL_DIRECTORY_COUNT` 导出并在 `tests/file-type-icon-contract.test.mjs` 校验。映射集中在 `src/utils/fileTypeIcons.ts`，后续新增类型只需更新映射数组，不改组件核心。

VS Code Icons 来源为 npm 包 `@iconify-json/vscode-icons`（MIT）的 `icons.json`。`vscodeFileIconRegistry.ts` 直接导入并通过 `@iconify/vue/offline` 注册完整图标集，不复制第二份数据文件，也不访问 Iconify CDN。`FileTypeIcon.vue` 对 Iconify 输出的真实 SVG 施加 `1em` 和父容器边界约束，因此现有字号、宽高和 Tailwind `text-*` 类可以继续控制图标尺寸。完整映射预览页 `/dev/file-icons` 按“文件后缀 / 特殊文件名 / 特殊目录名”分类，可搜索文件名、路径和图标名。

覆盖类别包括：Python/Jupyter、JavaScript/TypeScript/React、Java/Go/Rust、C/C++/C#、PHP/Ruby/Swift/Kotlin/Scala、Shell/PowerShell、Vue/Svelte、SQL、配置文件、Terraform、Kubernetes/Helm、CloudFlow、图片/视频/音频、PDF/Word/Excel/PPT、压缩包、二进制文件，以及 Docker/GitHub Actions/VS Code/IDEA/systemd/依赖包/许可证等特殊文件。

## 交付与回归

- 新增 `FileTypeIcon.vue`，网格和列表统一接入；已识别语言/工具类型使用本地 VS Code Icons，动态 SVG 仅用于未知后缀。
- `ThumbnailImage.vue` 在请求失败、空响应、错误 MIME、浏览器解码失败时稳定显示类型图标，不再空白。
- `imageCache` 保留已有请求超时与重试，并淘汰坏 object URL。
- 新增开发模式 `/dev/file-icons` 完整图标映射预览页。
- 新增 `tests/file-type-icon-contract.test.mjs`，覆盖优先级、媒体/Office/目录、Iconify 图标命中、`.ts` 类型冲突、动态 SVG 稳定性和安全转义。

### 尚需在真实环境确认的项目

后端缩略图生成依赖文件资源台账、ffmpeg、Office 转换器和存储目录；前端已对 404/500/超时做类型图标回退，但真实部署仍需在图片、视频、Office、音频、PDF 各准备一份资源进行视觉验收。布局快照也应在 320px、1024px、1440px 三个尺寸执行，确保本次仅替换图标而未改变父容器尺寸。
