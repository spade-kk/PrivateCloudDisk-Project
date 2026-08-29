# AI 助手 Markdown 渲染审计与修复记录

更新日期：2026-08-29

## 1. 审计结论

本次审计覆盖 `AiAssistantView`、AI Agent 任务块、AI SSE/任务快照投影、Markdown 工具和前端构建配置。AI Agent 的数据链路本身继续保持为：

```text
POST /ai/conversations/{id}/runs
  -> authenticated fetch + SSE
  -> useAiAgentStore.applyStreamEvent
  -> useAgentTaskStore.handleSSEEvent
  -> AiAssistantView / AgentTaskView
  -> AiMarkdownRenderer
```

发现的主要问题：

| 位置 | 原问题 | 影响 | 修复 |
| --- | --- | --- | --- |
| `src/utils/safeMarkdown.ts` | 仅使用正则替换标题、列表、粗体和代码 | 表格、嵌套列表、引用、链接、任务列表、公式和 Mermaid 无法正确解析 | 改为调用 MarkdownIt + GFM 任务列表 + Emoji 的完整渲染器 |
| `src/views/AiAssistantView.vue` | 普通助手消息直接调用旧的正则渲染并使用 `v-html` | Markdown 结构不完整，缺少统一操作层 | 使用 `AiMarkdownRenderer`，只挂载经 DOMPurify 净化的 HTML |
| `OutputBlock`、`SummaryBlock`、`ThinkingBlock` | 各自重复调用旧渲染函数 | 同一回复在不同任务块中表现不一致 | 统一使用 `AiMarkdownRenderer` |
| `src/stores/agentTaskStore.ts` | `output` 增量事件只读取 `output_text`，当旧 Provider 将片段放在字符串 `delta` 时会丢内容 | 流式回复可能显示为空或不完整 | 同时兼容 `delta: true + output_text` 和 `delta: string` |
| Vite `rollupOptions.external` | Markdown/高亮/公式/图表包被声明为运行时 CDN 依赖 | 离线部署或 CDN 不可用时 AI 回复退化 | AI 渲染所需依赖改由 npm 安装并进入构建 chunk |

当前没有提供可访问的生产 URL，因此本记录中的浏览器真机观察项使用源码、构建产物和契约测试验证；未把未观察到的生产网络行为当作已通过。

## 2. 渲染边界

AI 原文只作为 Markdown 输入，不作为 HTML 输入。渲染顺序为：

1. MarkdownIt 解析 Markdown，关闭原始 HTML，启用换行、自动链接和排版修正。
2. `markdown-it-emoji` 解析标准 Emoji shortcode，`markdown-it-task-lists` 解析 GFM 任务列表。
3. 高亮使用本地 `highlight.js/lib/common`，代码语言未知时保留安全的转义文本。
4. 代码围栏和表格由统一 renderer 输出带 `data-*` 标记的安全操作节点。
5. DOMPurify 在挂载前净化 HTML；只允许 Markdown 需要的标签、属性和安全 URL 协议。
6. 公式和 Mermaid 仅在 DOM 挂载后按需动态加载。KaTeX 使用 `trust: false`；Mermaid 使用 `securityLevel: 'strict'`，失败时保留源码。

原始模型内容不会经过 `v-html` 直接插入页面。组件中存在的 `v-html` 只接收 `renderAiMarkdown`、`sanitizeAiMarkdownHtml` 或 `sanitizeMermaidSvg` 的输出。

## 3. 已支持的 Agent 回复能力

- 一级到六级标题、标题层级、段落、换行、粗体、斜体、删除线、行内代码。
- 有序列表、无序列表、嵌套列表、任务列表、链接、自动识别 URL、引用和分隔线。
- GFM 表格：横向滚动、复制为 TSV、居中模态框放大预览、模态框内复制。
- 代码块：常用语言高亮、语言标签、横向滚动、复制按钮；未知语言安全回退为纯文本。
- 数学公式：行内 `$...$` / `\\(...\\)`，块级 `$$...$$` / `\\[...\\]`，KaTeX 加载失败保留公式源码。
- Mermaid 图表：`mermaid` / `mmd` fenced block；渲染失败显示原始 Mermaid 源码和状态提示。
- 图片和外部链接保持 Markdown 结构；外部链接自动设置 `target="_blank"` 与 `rel="noreferrer noopener"`。
- 普通助手消息和 Agent 任务输出、执行思考、最终总结使用同一渲染组件。
- 回复完成后提供“复制回复”；任务文档完成后在任务底部提供“复制回复”；阶段输出和总结仍保留各自复制入口。
- 流式输出期间，每个 Markdown 块随 `output_text` 增量更新；渲染组件只对当前内容重新生成安全 HTML。
- 移动端调整字号、表格弹窗尺寸和触控按钮；暗色主题提供代码、表格、引用和链接的适配样式。

## 4. 依赖与构建策略

AI 渲染依赖通过 `PrivateCloudDisk-web/package.json` 管理：

- `markdown-it`：Markdown 解析。
- `markdown-it-emoji`：Emoji shortcode。
- `markdown-it-task-lists`：GFM 任务列表。
- `highlight.js`：代码高亮。
- `katex`：数学公式，按需动态加载。
- `mermaid`：图表，检测到 Mermaid fenced block 后按需动态加载。
- `dompurify`：HTML/SVG 净化。

Vite 不再把上述 AI 依赖声明为 bare-module external。Mermaid 和 KaTeX 仍通过动态 `import()` 分割为按需 chunk，因此首次打开普通文本回复不会同步加载图表和公式的全部运行时代码。原有 Markdown 文件预览的 CDN loader 保留给该页面的历史兼容路径；AI 助手不依赖 CDN。

## 5. 验证记录

在 `PrivateCloudDisk-web` 目录执行：

```bash
npm run test:ai-agent
npx vue-tsc --noEmit
npm run build -- --logLevel error
```

结果：

- `npm run test:ai-agent`：通过，包含原有 AI Agent 契约和新增 Markdown 渲染契约测试。
- `npm run build -- --logLevel error`：通过，Vite 完成生产构建，AI Markdown 依赖被正确解析，未出现 bare-module external 错误。
- `npx vue-tsc --noEmit`：仓库当前仍有与本次改动无关的既有类型错误；新增 `AiMarkdownRenderer`、`aiMarkdown`、AI 任务块、`AiAssistantView` 和流式增量兼容修改未产生新的筛选命中错误。后续应单独治理现有 API 导出、公共组件泛型和旧页面类型错误。
- npm 安装时报告依赖树存在 14 个 audit advisory（12 moderate、2 high）。这不是本次渲染功能的运行时失败，但应在发布前单独执行依赖升级与安全复核，不能将该项标记为已完成。

## 6. 验收清单

- [x] 普通 AI 回复进入统一 Markdown 渲染器。
- [x] Agent 阶段输出、思考、最终总结进入统一 Markdown 渲染器。
- [x] 原始 HTML 默认禁用并进行 DOMPurify 净化。
- [x] 代码块显示语言并支持复制。
- [x] 表格支持横向查看、复制和居中放大。
- [x] 回复底部提供复制回复。
- [x] Mermaid 与 KaTeX 按需增强，失败可回退源码。
- [x] 外部链接使用安全的新标签页属性。
- [x] 流式 `output` 增量内容不因事件字段差异丢失。
- [x] 暗色主题、移动端尺寸和键盘 ESC 关闭表格弹窗。
- [x] SSE、任务快照恢复和服务端审批接口未改动。
