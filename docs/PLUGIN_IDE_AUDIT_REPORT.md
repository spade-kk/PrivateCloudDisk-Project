# 插件开发 IDE 审计与实施报告

> 关联设计文档：`docs/PLUGIN_AUTOMATION_PLATFORM_DESIGN.md` 第 16.9～16.10 节。
> 本报告记录本次 Web IDE 改造的审计边界、实现结果、验证证据和联调门禁。

## 1. 改造前审计结论

| 范围 | 原实现 | 风险 |
| --- | --- | --- |
| 路由 | 只有 `/app/plugins/new/:type`，复用单文件表单页 | 无编辑 IDE、执行历史和市场详情深链 |
| 插件编辑器 | `PluginEditorView` 维护单个 `code` 字符串 | 无多文件模型、文件树、标签页和文件级 dirty 状态 |
| Monaco | `PluginMonacoEditor` 直接绑定页面表单 | 校验结果只有布尔值，无法统一 Problems、补全和路径上下文 |
| 工作流 | 已有 Vue Flow 与 CloudFlow DSL 切换 | 缺少执行面板、撤销重做、脏状态守卫和沙盒测试入口 |
| API | 只有插件/版本/发布基础接口 | 草稿文件、日志、测试运行接口尚未全部由后端公开 |
| 后端上传 | 上传接口要求版本先有源码包且 validation=PASSED | 首次创建流程形成循环，IDE 无法保存第一个版本 |

## 2. 本次实现

- 更新设计文档，增加 IDE 布局 ASCII 图、区域职责、组件树、Pinia 状态模型、状态机、响应式策略、API 时序和实施门禁。
- 新增 `IdeShell`、`FileTree`/递归节点、`MonacoEditorWrapper`、`BottomPanel`、`SnippetLibrary`、`TemplateLibrary`。
- 新增 `pluginIdeStore`：多文件树、打开标签、文件级 dirty、校验问题、执行日志、保存状态、面板状态和生命周期 reset。
- 新增云/本地插件 IDE：元信息、生命周期入口、权限、平台、沙箱提示、源码打包、后端 AST/安全校验、自动保存、全屏、Ctrl/Cmd+S、Ctrl/Cmd+Shift+P、运行/停止/发布。
- 工作流 IDE 增加沙盒测试、执行日志、问题面板、画布小地图、撤销重做、快捷键和离开页面 dirty guard。
- 插件管理增加 IDE 编辑入口、工作流筛选；新增执行记录路由、插件市场详情、工作流模板详情和空间插件自定义安装入口。
- 插件 API SDK 增加版本、草稿文件、执行记录、日志授权、测试运行、工作流执行/调度等类型化薄封装；缺少后端实现的路径均保留中文注释并禁止静默伪造数据。
- 修复插件服务草稿包生命周期：未发布版本允许先上传，再校验；重复保存可替换源码包并重新进入 `PENDING`，已发布版本仍保持 immutable。

## 3. 验证结果

- `npx vue-tsc --noEmit`：IDE、插件 API、工作流编辑器、市场详情、路由相关过滤项无新增类型错误。
- `npx vite build --outDir /tmp/pcd-web-dist`：成功转换 1973 个模块并完成生产构建；仅有依赖 `@vueuse/core` 的既有 PURE 注释警告。
- `./gradlew test --no-daemon`（Plugin Service）：`BUILD SUCCESSFUL`，4 个 actionable tasks 完成。
- `git diff --check`：通过。
- Storage Worker 契约测试当前环境无法导入，原因是本机缺少 `aiomysql`；已完成 `compileall`，真实 RabbitMQ/MySQL/Redis 联调仍属于部署门禁。

## 4. 联调与发布门禁

1. 后端上线 `/files/{pluginId}/versions/{version}/test-runs`、草稿文件、日志授权和取消接口后，启用对应 SDK 调用；当前页面在 404 时只显示可解释提示，不把正式生命周期执行当作测试回退。
2. 接入真实 Runtime 后验证 Python/JavaScript 沙箱、超时回收、权限最小交集、脱敏日志和 `file.content.ready` 回写原子性。
3. 在带 RabbitMQ、MySQL、Redis 的环境运行 Worker Task Bus 集成测试，覆盖任务重复投递、retry TTL 回流、DLQ 重放、插件失败/超时、Gate fallback 和多进程 SIGTERM。
4. 市场评论、版本历史/差异和服务端多文件草稿接口必须完成契约联调后再开放对应高级操作。

## 5. 风险与回滚

- 老路由仍保留，`/app/plugins/new/:type` 已指向新 IDE；旧 `PluginEditorView` 文件和注释未删除，可通过路由回滚。
- 新 IDE 只新增路由、组件和 store，不改文件浏览核心业务 API；请求头空间上下文继续由全局拦截器注入。
- 源码包替换仅允许 `immutable=0`，发布后不允许覆盖；旧对象清理失败不会回滚数据库新指针，由对象清理任务回收。
- 任何测试运行接口失败均不自动降级为正式写入，避免开发测试影响文件生命周期。
