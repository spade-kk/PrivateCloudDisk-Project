# PrivateCloudDisk 文档中心

本目录记录当前仓库的架构、接口、数据库、开发、安全和扩展平台说明。文档以代码、配置、迁移脚本和事件契约为准；未经过实际部署或压测验证的指标不写入文档。

## 导航

| 文档 | 内容 |
| --- | --- |
| [架构设计](./architecture.md) | 技术栈、服务边界、通信方式、部署与性能设计 |
| [API 概览](./api-overview.md) | 网关前缀、认证、文件/空间/分享/扩展接口族 |
| [数据库设计](./database.md) | 用户、目录、文件、空间、分享、标签、扩展数据 |
| [开发指南](./development.md) | 本地环境、Compose、服务启动和验证 |
| [安全设计](./security.md) | JWT、内部服务凭证、限流、预览/下载授权 |
| [密码加密](./password-encryption.md) | 注册、登录和密码处理链路 |
| [空间集成审计](./SPACE_FULL_INTEGRATION_AUDIT.md) | 空间上下文、资源范围和生命周期集成记录 |
| [插件自动化平台设计](./PLUGIN_AUTOMATION_PLATFORM_DESIGN.md) | 插件、Runtime、自动化、工作流、调度和市场 |
| [插件开发指南](./PLUGIN_DEVELOPER_GUIDE.md) | 插件包、权限、SDK、工作流 DSL 与发布 |
| [插件 Runtime UDS 安全架构](./PLUGIN_RUNTIME_UNIX_SOCKET_ARCHITECTURE.md) | 每实例 Unix Socket、Session/Token、Capability Hub 授权、审计与部署门禁 |
| [插件 Runtime UDS 性能基线](./PLUGIN_RUNTIME_UDS_PERFORMANCE.md) | 本机并发 RPC 与 1,000 Socket Session 生命周期基线、Linux 压测门槛 |
| [Cloud AI Agent Runtime 设计](./CLOUD_AI_AGENT_SERVICE_DESIGN.md) | Agent 边界、Gateway 签名身份、Tool Registry、Capability Hub、SSE、审批与迁移 |
| [Cloud AI Agent 15.x 能力对照矩阵](./AI_AGENT_CAPABILITY_MATRIX.md) | 15.1–15.40 逐项实现状态、验证入口和未接通的上游能力 |
| [Cloud AI Agent API](./AI_AGENT_API.md) | 会话、Run、SSE、审批恢复及浏览器集成契约 |
| [Cloud AI Agent Task Execution UI V2](./AI_AGENT_TASK_EXECUTION_UI.md) | Codex 风格任务文档块、动态计划、工具 `output_data`、SSE V2、恢复、验收和时序图 |
| [Cloud AI Agent 安全与运维](./AI_AGENT_SECURITY.md) | 信任边界、权限、脱敏、限流、配置与威胁模型 |
| [Cloud AI Agent 部署指南](./AI_AGENT_DEPLOYMENT.md) | Compose automation profile、密钥、探针、监控与排障 |
| [CloudFlow MCP Server 架构与审计](./CLOUDFLOW_MCP_SERVER_DESIGN.md) | 服务端 MCP 定位、Capability Hub 边界、工具导出、C4 图和验收追踪 |
| [CloudFlow MCP 协议](./CLOUDFLOW_MCP_PROTOCOL.md) | Streamable HTTP、JSON-RPC、工具调用、SSE 与 OAuth 元数据契约 |
| [CloudFlow MCP 安全](./CLOUDFLOW_MCP_SECURITY.md) | Gateway 签名上下文、Hub 最终授权、多租户隔离、审计和上线门禁 |
| [CloudFlow MCP 部署](./CLOUDFLOW_MCP_DEPLOYMENT.md) | Compose/Kubernetes、健康检查、Prometheus、OTLP 与发布流程 |
| [CloudFlow MCP Agent 接入](./CLOUDFLOW_MCP_AGENT_GUIDE.md) | Codex/Claude Code/Cursor 等第三方 Agent 的通用接入原则 |
| [CloudFlow MCP 测试报告](./CLOUDFLOW_MCP_TEST_REPORT.md) | 已执行定向测试、覆盖范围与真实环境待验收项 |
| [CloudFlow 编译器指南](./CLOUDFLOW_COMPILER_GUIDE.md) | Rust Compiler CLI、Workflow IR、Runtime HTTP 契约和验证命令 |
| [CloudFlow DSL 规范](./CLOUDFLOW_DESIGN.md) | `.flow` 严格语法、AST、表达式和控制流 |
| [CloudFlow IR 规范](./CLOUDFLOW_IR_DESIGN.md) | `workflow.cloudflow.io/v1` 机器契约与 DAG |
| [CloudFlow 诊断规范](./CLOUDFLOW_ERROR_DESIGN.md) | CF 错误码、Span、CLI/HTTP 结构化诊断 |
| [CloudFlow 合规审计](./CLOUDFLOW_COMPLIANCE_AUDIT.md) | 实现追踪、验证证据、风险与上线门禁 |
| [CloudFlow YAML 设计](./CLOUDFLOW_YAML_DESIGN.md) | 第二前端语言：YAML Syntax + Workflow Schema + Expression System + Validation + Domain Semantics 五维设计、CFY 错误码与落地回写 |
| [CloudFlow YAML 示例设计](./CLOUDFLOW_YAML_DEMO_DESIGN.md) | YAML 20 个示例（含旧版 weekly_sales_report 转化）与 IR 等价 / 端到端执行测试设计 |
| [CloudFlow YAML GitHub-Actions 对齐](./CLOUDFLOW_YAML_GITHUB_ACTIONS_ALIGN.md) | `${{ }}` 表达式/插值、`plugin:<id>:<fn>@<v>` 能力引用、`with`/`if`/`retry`/`timeout` 语义对齐 |
| [CloudFlow 表达式子系统](./CLOUDFLOW_EXPRESSION.md) | `expr.cloudflow.io/v1`：pest 词法、解析、Domain 表达式 AST、19 个白名单函数与求值（DSL 与 YAML 前端共用） |
| [CloudFlow 安全白皮书](./CLOUDFLOW_SECURITY.md) | 编译器/执行引擎安全：YAML 资源护栁、表达式沙箱与嵌套防线、诊断防泄露、依赖扫描与安全报告（19.x） |
| [CloudFlow DSL 示例](./CLOUDFLOW_DEMO_DESIGN.md) | `.flow` 示例工作流（销售周报等）与 DSL 语法对照 |
| [CloudFlow 开发调试执行入口](./CLOUDFLOW_DEV_EXECUTE.md) | Dev-Execute：统一执行引擎（`cloudflow-engine-core` 统一调度驱动 `engine` + `execution_core` 控制流语义，生产面 `execution` / 调试面 `dev_exec` 经 `EngineDeps` 注入区分）+ 统一 IR 契约校验器（CFI-7xxx）+ 纯内存执行引擎（CFD-81xx）+ CLI/HTTP 调试入口（`profile: inmem\|agent` 生产仿真）与测试报告 |
| [CloudFlow V1.2 扩展](./CLOUDFLOW_V1.2_DSL_EXTENSION.md) | switch/for/管道/trigger 详配等 V1.2 新语法的实现与审计 |
| [CloudFlow 语言扩展流程](./CLOUDFLOW_LANGUAGE_EXTENSION_GUIDE.md) | 新增关键字从 grammar 到 Runtime 的唯一工程流程 |
| [CloudFlow 统一语法高亮](./CLOUDFLOW_SYNTAX_HIGHLIGHT.md) | 以 grammar+AST 生成 VS Code/Monaco/Highlight.js 高亮规则 |
| [CloudFlow 统一代码补全](./CLOUDFLOW_COMPLETION.md) | 以 grammar+AST+config 生成 VS Code/Monaco 补全与结构提示 |
| [事件契约](../contracts/README.md) | 文件生命周期和插件清单 Schema |

## 阅读顺序

1. 从根目录 [README](../README.md) 了解产品能力、技术栈和服务职责。
2. 阅读 [架构设计](./architecture.md) 理解浏览器、网关、业务、存储和扩展边界。
3. 需要接入接口时阅读 [API 概览](./api-overview.md)，再以对应 Controller、前端 API 模块和 Gateway 路由核对路径。
4. 修改数据或异步链路时同时阅读 [数据库设计](./database.md) 和 [事件契约](../contracts/README.md)。
5. 涉及插件或工作流时，必须阅读平台设计、开发指南和 Runtime 部署说明。

## 当前文档约定

- “支持”表示仓库已有实现入口，不代表所有部署配置默认启用。
- “按配置”表示能力受环境变量、服务 profile、存储后端或客户端平台影响。
- 不使用虚构的客户数量、性能、SLA、认证结果或商业价格。
- 变更 API、数据库、消息或权限时，应同步更新根 README、服务 README、docs 和 contracts。
