# IDE 移动端与平板响应式布局说明

> 关联需求：`IDE-RESP-2026-08`。本说明覆盖 CloudFlow 工作流、云插件和本地插件三种 Web IDE。
> 本次仅新增布局、交互承载和视觉样式；插件保存、校验、发布、CloudFlow Runtime 校验和 `X-Space-Id`
> 上下文注入均沿用原有调用链。

## 1. 审计结论与影响范围

审计对象：

- `PrivateCloudDisk-web/src/views/workflows/WorkflowEditorView.vue`
- `PrivateCloudDisk-web/src/components/workflows/WorkflowIdeShell.vue`
- `PrivateCloudDisk-web/src/components/workflows/WorkflowCanvas.vue`
- `PrivateCloudDisk-web/src/components/workflows/WorkflowNodeLibrary.vue`
- `PrivateCloudDisk-web/src/views/plugins/PluginIdeView.vue`
- `PrivateCloudDisk-web/src/components/plugins/ide/IdeShell.vue`
- `PrivateCloudDisk-web/src/components/plugins/ide/MonacoEditorWrapper.vue`
- `PrivateCloudDisk-web/src/components/plugins/ide/BottomPanel.vue`

审计前，三类页面已具备桌面多面板结构，但在窄屏上存在以下共性：

1. `WorkflowIdeShell` 和 `IdeShell` 只将侧栏改成绝对定位，未在断点切换时自动收起，首次进入手机页面会挤占画布或编辑区。
2. 面板没有统一遮罩层，打开侧栏后无法通过点击工作区关闭。
3. 工作流的移动工具栏缺少输出面板入口；平板进入抽屉布局时可见性与操作入口不一致。
4. Monaco 始终开启 minimap；小屏代码区被压缩。
5. 工具栏、节点库、属性面板和底部输出的触控目标与安全区适配不统一。

## 2. 实现边界

响应式实现放在：

- `PrivateCloudDisk-web/src/assets/ide-responsive.css`

该文件使用 `.pcd-ide-responsive`、`.pcd-plugin-ide` 和 `.pcd-workflow-ide` 作为隔离根，避免污染普通控制台与预览页面。文件使用 CSS 自定义属性管理间距、工具栏高度、抽屉尺寸、阴影、圆角、过渡与 z-index。格式化后超过 400 行，按断点分组。

组件中仅增加了必须由 JavaScript 控制的状态：

- 在跨越 1024px 断点时收起或还原面板默认状态。
- 通过遮罩关闭移动端抽屉。
- 手机端从“更多”底部抽屉访问未在顶栏显示的保存、测试、发布与导出操作。
- Monaco 运行时关闭 minimap 并保持 14px 字体、自动换行。

## 3. 断点与面板行为

| 宽度 | 模式 | 左侧 | 右侧 | 底部 | 顶部 |
| --- | --- | --- | --- | --- | --- |
| 大于 1366px | 大桌面 | 固定可调 | 固定可调 | 固定可调 | 完整工具栏 |
| 1024px–1366px | 小桌面 | 紧凑多栏 | 紧凑多栏 | 固定可调 | 精简文字但保留按钮 |
| 768px–1024px | 平板 | 左侧抽屉 | 右侧抽屉 | 底部抽屉 | 显示面板切换入口 |
| 480px–768px | 大手机 | 85% 宽抽屉 | 85% 宽抽屉 | 50% 视口底部抽屉 | 保留核心动作，更多操作收纳 |
| 小于 480px | 小手机 | 全屏优先抽屉 | 全屏优先抽屉 | 54% 视口底部抽屉 | 工作流工具栏两行布局 |

抽屉仅在小屏显示遮罩，遮罩层低于工具栏、高于画布。点击遮罩会关闭当前抽屉，不会修改桌面端用户保存的宽度或高度偏好。

## 4. 页面级体验

### 工作流 IDE

- Vue Flow 画布在手机和平板上仍保留捏合缩放、单指平移、节点选择和可视化 DSL 投影。
- 节点库点击后直接添加节点；小屏会自动收起节点库并打开属性抽屉，替代依赖鼠标拖拽的桌面流程。
- 节点与连线在小屏保持真实 SVG 形状和语义颜色，不使用旋转矩形伪造菱形。
- 小地图尺寸缩小；小手机默认收起内容区域，仅显示展开按钮，减少遮挡。
- 终端、问题、执行日志与 DSL 预览改为横向可滚动标签栏，且支持独立全屏查看。

### 云插件 IDE / 本地插件 IDE

- 两者继续共用 `PluginIdeView.vue`，通过路由类型决定 Python 或 JavaScript/TypeScript，不产生两套分叉布局。
- 文件树、片段库和模板库在平板/手机变为侧边抽屉；选择文件后自动回到编辑区。
- 手机顶栏将保存、校验、测试、发布及版本收纳到“更多操作”底部抽屉，未删除原有 API 操作。
- Monaco 在小于 768px 时关闭 minimap、保持 14px 字号和自动换行，避免编辑区横向拥挤。

## 5. 视觉与可访问性规则

- 交互按钮在手机断点采用最小 44×44px 命中区；输入控件最小高度 44px、字体 16px，避免 iOS 自动缩放。
- 抽屉、底部菜单和工具栏使用轻量阴影、12px 圆角、0.2 秒过渡和安全区 padding。
- `prefers-reduced-motion` 下禁用平移与抽屉动画。
- `hover: none` 下将悬停反馈改为按压缩放与选中轮廓，避免触控设备粘滞 hover。
- 继续保留浏览器缩放能力；未使用 `user-scalable=no`，以满足无障碍放大需求。

## 6. 验证矩阵

| 检查项 | 验证方式 |
| --- | --- |
| CSS 集中管理 | `wc -l src/assets/ide-responsive.css`，应大于 400 行 |
| 工作流断点状态 | 在 375、600、820、1200、1440px 观察工具栏、抽屉与画布 |
| 插件断点状态 | 云插件与本地插件分别检查文件树、属性、底部日志和 Monaco |
| DSL 兼容性 | `npm run test:cloudflow` |
| 生产编译 | `npm run build` |
| 真实交互 | 需以已登录测试帐号完成 Runtime 校验、保存和发布联调 |

## 7. 已知运行环境限制

本仓库的工作流和插件 IDE 路由均需要登录。无有效测试会话时，浏览器会按路由守卫跳转登录页；因此构建与静态/单元契约验证不能替代已登录触控真机 E2E。后续验收应覆盖 iOS Safari、Android Chrome 和平板横竖屏。
