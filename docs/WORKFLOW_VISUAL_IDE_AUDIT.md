# CloudFlow 可视化工作流 IDE 审计与增强记录

> 范围：`PrivateCloudDisk-web` 的 CloudFlow 工作流创建/编辑路由、Vue Flow
> 画布、节点库、属性面板、Pinia 编辑状态、DSL 生成器，以及 Runtime 编译校验调用。
> 本文只记录已在当前代码库验证的事实；不把未接通的后端能力描述为已上线能力。

## 1. 审计结论

| 层面 | 审计发现 | 改造决定 |
| --- | --- | --- |
| 路由 | 编辑入口为 `/app/workflows/new` 与 `/app/workflows/:workflowId/edit`；开发者入口同样复用工作流编辑视图。 | 保持路由与鉴权不变，避免影响插件、市场和历史版本页面。 |
| DSL 真源 | `cloudflowVisualDsl.ts` 已能将画布生成 CloudFlow V1.1 源码；源码回投只接受 CloudFlow Runtime IR。 | 继续以 DSL 为唯一发布事实，图形布局、颜色和边样式仅保存到 `graphJson`。 |
| 画布 | 使用 `@vue-flow/core`、`@vue-flow/background`、`@vue-flow/controls`；锁定版本没有可用 MiniMap 运行时导出。 | 保留 Vue Flow，使用同一节点/视口数据构建交互式投影小地图，不维护第二张图。 |
| 节点渲染 | 所有节点走单一 `workflow` 模板；原条件节点将整个矩形 `rotate(45deg)`。 | 条件、触发器、并行和断言使用真实 SVG 轮廓，文本、Handle 和命中区域不旋转。 |
| 连线 | 原实现仅有部分语义颜色，固定为 smoothstep，缺少属性编辑和多选状态。 | 边保留 `kind` 作为 DSL 语义，新增仅图形用的路由、线型、颜色、标签与说明。 |
| 属性面板 | 已覆盖能力、变量、运行时、重试和控制流的基础字段，但不能编辑边。 | 增加连线编辑态、变量引用选择器、节点颜色/图标与连线映射编辑。 |
| 运行时校验 | `validateWorkflowApi` 发送字符串 DSL；响应的结构化 issue 同时用于问题面板和节点错误状态。 | 不在浏览器实现 DSL 解析器；保存与发布仍以前端调用 Runtime 编译校验为前置条件。 |
| 导出 | 没有画布导出能力。 | 由当前图模型生成转义后的独立 SVG，支持 SVG/PNG 下载和浏览器打印对话框“保存为 PDF”。 |

## 2. CloudFlow 语法映射边界

已映射且可由画布生成：`trigger`、`task`、`plugin`、`api`、`if/else`、
`foreach`、`while`、`parallel`、`try/catch/finally`、`wait approval`、`assert`、
变量、表达式、`retry` 与 `timeout`。

`group` 与 `note` 是纯画布组织元素，不进入 DSL。`match/case`、多 `catch`、
独立 Output 节点尚未出现在当前 CloudFlow Runtime V1.1 的可编译语法中，因此 IDE
不得伪造对应节点；最终输出仍使用 task 的 `output` 字段。这一限制是防止可视化编辑器
产出 Runtime 无法接受的 DSL，而不是删除未来扩展点。

## 3. 本轮实现内容

### 3.1 节点、连线与交互

- 条件节点改为 SVG diamond path；触发器、并行、断言也有独立几何轮廓。
- 控制流端口维持 CloudFlow 语义：条件真/假、循环体、并行体、try/catch/finally。
- 边支持 `smoothstep`、贝塞尔、直线，实线/虚线/点线、语义默认颜色、标签和协作说明。
- Vue Flow 框选结果进入 Pinia；复制、粘贴、删除支持多节点及其内部连线。
- 右键菜单分别处理画布、节点与连线，避免把所有操作堆入属性面板。

### 3.2 画布导航与导出

- 小地图按实际 viewport、画布尺寸与 zoom 计算视口矩形；可点击、拖拽、双击聚焦、折叠。
- 在不引入未审计依赖的前提下导出 SVG/PNG；生成 SVG 前转义所有图数据文本。
- “打印 / 保存为 PDF”使用浏览器原生打印路径，允许用户获得设备支持的矢量 PDF。
- 保留零尺寸挂载保护：`ResizeObserver` 只有在父容器有宽高后才挂载 Vue Flow。

### 3.3 IDE 布局与新手引导

- 既有可拖拽左、中、右、下布局与每工作流 `localStorage` 持久化保持不变。
- 新增首次打开引导：依次突出节点库、画布、属性面板和 Runtime 编译校验工具栏；完成状态保存在 `pcd.workflow-ide.onboarding.v1`。
- 保留亮/暗主题变量、专注模式、全屏、离线禁用编辑、自动保存、命令面板与快捷键。

## 4. 关键数据流

```text
Vue Flow interaction
  -> workflowIdeStore (history <= 50 / graphJson UI state)
  -> serializeProjectToCloudFlow()
  -> POST workflow validation API
  -> CloudFlow Runtime compiler
  -> structured diagnostics + normalized IR
  -> Monaco markers / problems / node errorKeys
```

画布布局和显示属性绝不替代 DSL：发布、保存与 Runtime 执行使用的仍是 CloudFlow
源码及其编译后的 IR。

## 5. 已验证与待联调项目

### 已验证

- `npm run test:cloudflow`：10/10 通过，覆盖 DSL 映射、Runtime 校验调用、真实菱形节点、
  小地图交互入口、安全导出和新手指引的源码契约。
- `npm run build`：Vite 生产构建通过。

### 需在联调环境验证

- Runtime `/api/v1/compile` 可用时，复杂 DSL → IR → 画布 → DSL 的无损往返。
- 浏览器下载权限、Pop-up 策略以及“打印 / 保存为 PDF”实际设备行为。
- 100+ 节点实际交互帧率与更大图的 SVG/PNG 内存占用。
- Runtime 暂未支持的 `match/case`、多 catch、独立 Output 节点完成语言规范后，再以 DSL
  扩展为先导增加可视化节点，不能先在 UI 中构造不可编译模型。

## 6. 后续扩展约束

1. 新增任何节点前，先扩展 CloudFlow AST、IR、Runtime 与编译诊断，再扩展节点库、
   属性面板和 `serializeProjectToCloudFlow`。
2. 边的视觉字段不得成为控制流真源；控制语义只能来自 `kind`、`parentId` 和 `branch`。
3. 引入 dagre、PDF 库、官方 Vue Flow MiniMap 等第三方包前，需完成版本、许可证、包体积与
   200+ 节点性能审计；不得以未验证包替换现有稳定路径。
