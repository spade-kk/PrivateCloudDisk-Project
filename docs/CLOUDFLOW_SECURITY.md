# CloudFlow 安全白皮书（编译器与执行引擎）

> 实现状态：2026-08-21 落地回写（需求 19.13/19.14/19.16/19.17/19.19/19.20/19.21/19.25/19.26/19.27/19.28/19.29）。
> 本文档是 CloudFlow 编译器与执行引擎面安全措施的**单一事实源**；平台级安全（JWT、内部服务
> 凭证、限流、预览/下载授权）见 `docs/security.md`。

## 1. 威胁面与总体策略

CloudFlow 编译器处理**用户提供的 DSL/YAML 文本**，执行引擎处理**用户提供的 IR 与动作参数**。
安全策略按层收敛：

| 层 | 威胁 | 对策 | 事实源 |
|---|---|---|---|
| YAML 解析 | 超大文件 / 超深嵌套 / 别名炸弹（资源耗尽） | 三道资源护栏 + libyaml 双保险 | `src/yaml/convert.rs` |
| 表达式解析 | 超长表达式 / 极端嵌套（栈溢出、内存） | 长度 + 嵌套 O(n) 预扫描防线 | `crates/cloudflow-engine-core/src/expression/parser.rs` |
| 表达式求值 | 任意代码执行 | 白名单纯函数沙箱 | `crates/cloudflow-engine-core/src/expression/eval.rs` |
| 编译诊断 | 内部路径 / 堆栈泄露 | 固定文案 + 仅回显用户输入片段 | `src/diagnostic.rs`、`src/lib.rs` |
| 编译缓存 | 陈旧结果（含文件读取的 include） | 仅默认文件名请求参与缓存 | `src/compile_cache.rs` |
| 动作执行 | 越权能力调用 | Agent 统一出口 + 权限校验 + 审计 | `cloudflow-agent` |

## 2. YAML 解析资源护栏（19.9/19.10/19.25）

`parse_yaml_detailed`（YAML 编译唯一入口）按序执行三道护栏，超限立即返回单一致命诊断，
不进入 Schema 校验与 AST 转换：

| 护栏 | 常量 | 上限 | 错误码 | 拦截目标 |
|---|---|---|---|---|
| 源码字节数 | `MAX_YAML_SOURCE_BYTES` | 1 MiB（先于 libyaml） | CFY-SCHEMA-1005 | 超大文件；与 HTTP 请求体上限 `MAX_COMPILE_BODY_BYTES` 一致 |
| 嵌套深度 | `MAX_YAML_DEPTH` | 100 层 | CFY-SCHEMA-1006 | 嵌套炸弹；libyaml 自身约 121 层报错（CFY-1001）构成第二层保险 |
| 节点总数 | `MAX_YAML_NODES` | 100,000 | CFY-SCHEMA-1007 | 锚点/别名在事件期展开为完整值树，以总数约束别名放大（billion-laughs 变体） |

YAML 值树本身是**纯数据**（无 `!!binary`/对象构造执行语义），解析过程不产生代码执行。
边界测试：`tests/cloudflow_security_bounds.rs`（1.2MiB 注释源 / 120 层 / 300 层 /
1000×200 别名放大 / 合法小文档不误伤）。

## 3. 表达式系统沙箱（19.11/19.12/19.16/19.26）

- **白名单求值**：`call_builtin` 仅实现 19 个纯函数（列表见 `CLOUDFLOW_EXPRESSION.md` §4）；
  任何未登记函数（`system`/`exec`/`fetch`/…）在求值期返回错误，无动态代码路径。
- **长度防线**（`CFY-EXPR-103`）：表达式文本 > 16K 字符（`MAX_EXPRESSION_CHARS`）先于 pest 报错。
- **嵌套防线**（`CFY-EXPR-104`）：PEG 递归解析无内置嵌套限制，5000 层括号会直接栈溢出
  （已实测复现）；O(n) 预扫描（跳过字符串字面量）约束平衡括号最大深度与三元符 `?` 总数，
  上限 `MAX_EXPRESSION_NESTING` = 512（512 层 pest 递归栈消耗约 1MB，线程堆保留边际）。
- **解析缓存**（19.3）：全局 `Mutex<HashMap>`（各 1024 条，超容整体清空），只缓存 rebase 前的
  相对坐标结果，结果与无缓存逐节点等价；锁守卫限定单个临时对象内释放（get/put 分开两次加锁，
  不可重入）。

## 4. 诊断不泄露内部信息（19.13）

- 编译诊断只含 `filename`（用户传入）/ 源码片段 / 修复建议；**不出现宿主绝对路径、堆栈或
  Rust Debug 文本**。HTTP 响应体同样适用。
- include 越权读取报错（CF3103/CF3104/CF3105）只回显用户书写的**相对 include 路径**；
  循环 include 诊断已于 2026-08-21 修复（此前会输出 canonicalize 后的绝对路径）。
- HTTP 超大请求体拒绝（CF1104）返回固定文案；测试断言响应体不含宿主路径
  （`tests/cloudflow_security_bounds.rs::http_rejects_oversized_compile_body_without_leaking_paths`）。

## 5. 编译产物缓存安全（19.17）

见 `CLOUDFLOW_COMPILER_GUIDE.md`「编译产物缓存」：仅默认文件名（`<request>`）请求参与缓存
——此情形 include 无物理根目录必然被拒（CF3103），结果完全由请求内容决定；携带 `.flow`
路径的请求禁用缓存，避免本地模块文件变更被旧缓存遮蔽。缓存不落盘、不跨进程，无一致性攻击面。

## 6. 解析器纯度与线程安全（19.14/19.27）

- 全部解析器（pest 词法、DSL/YAML/表达式解析、Schema 校验、IR 契约校验）为纯函数：
  相同输入产生逐字节相同输出，无全局可变状态（表达式解析缓存为唯一受控例外，见 §3）。
- 共享缓存/计数器均使用 `Mutex`/`AtomicU64`，编译与调试入口支持并发调用。

## 7. 动作执行隔离

所有动作经 Capability Agent（`cloudflow-agent`，gRPC）统一出口：能力解析、权限校验、
审计记录、路由分发。执行引擎核心不直接访问数据库、文件系统或其他微服务；调试面
`MockActionExecutor` 零网络。详见 `CLOUDFLOW_DEV_EXECUTE.md` 与 `security.md`。

### 7.1 能力中心（Capability Hub）统一门禁（2026-08-21）

Rust Agent 仅做**透传**，不解析能力注册表、不缓存能力键、不访问数据库；Java 能力中心
（`workflow-service`）是唯一的能力解析/校验/分发/审计入口：

- **服务身份认证**：`InternalServiceFilter` 常量时间比较 `X-PCD-Service-Token`
  （`pcd.internal-service-token`），缺失/伪造返回 `AUTH-UNAUTHENTICATED`（401），不暴露能力存在性。
- **防能力键注入**：`CapabilityKeyValidator` 白名单正则（`builtin/api/plugin/local_plugin` +
  安全字符集），拒绝空格、引号、`${}`、反斜杠等（`WF-CAPABILITY-KEY`）。
- **参数防注入**：`CapabilitySchemaValidator` 按注册表 JSON Schema 校验类型/长度/范围/枚举/数组/
  UUID 正则，一次收集多条错误（`WF-CAPABILITY-INPUT`）。
- **权限最小化**：`invoke()` 用「必需权限 ⊆ 实时授权」校验；`invokeAgent()` 用「有效权限 =
  声明 ∩ 授权 ⊇ 必需权限」校验（CLOUDFLOW-SEC-004），`WF-CAPABILITY-FORBIDDEN` 默认拒绝。
- **防 SSRF**：平台目标路径来自代码内白名单路由表，基址（`platformUrl`/`storageUrl`）来自配置，
  调用方无法指定目标地址。
- **数据面二次鉴权（防横向越权）**：Platform 每个 `/business/internal/capability/*` 端点重新
  `resolveContext(uid, spaceId)` → `requireOperation(READ/SHARE)` →
  `requireFileInCurrentSpace(...)`；`api:file.content.get` 仅文本类型且 ≤1MiB；
  `api:user.info` 脱敏（不返回手机号/邮箱）；`api:file.scan` 仅限有权文件。
- **审计**：`pcd_capability_audit` 记录能力键、调用方服务、execution/step、用户/空间、trace、
  参数摘要（仅键名/类型/长度）、结果码与耗时；审计失败不阻断调用。
- **故障边界**：能力级熔断（`SimpleCapabilityBreaker`）、超时（注册表 `timeout_seconds`，默认 30s）、
  幂等能力最多 2 次重试；错误消息脱敏并截断。

更多细节见 `docs/CAPABILITY_HUB.md`。

### 7.2 云插件 Unix Domain Socket 入口（CF-PLUGIN-UDS-001）

云插件不是 CloudFlow Agent 的同一进程，也不允许直连 Capability Hub。Plugin Runtime 为每个容器实例创建
独立 Unix Socket，并只挂载为 `/runtime/runtime.sock`。Socket listener 对应不可变 Session；SDK 请求中的实例 ID
必须匹配该 listener，48-byte 单实例 Token 还必须常量时间匹配。用户、空间、插件、安装和执行身份均从 Runtime
Session 注入，SDK 不发送、不能伪造这些字段。

Runtime 先限制 protobuf 帧大小、连接数、请求频率和调用超时，再将**manifest 声明权限**和独立的**安装授权
快照**送至 Capability Hub；Hub 继续以“声明 ∩ 授予”与实时空间权限作最终决定。SDK 不写 `capability-audit.jsonl`；
Agent 在认证后记录 `RUNNING` 和终态事实，Automation/Plugin Service 负责受信存储与展示。详见
[Plugin Runtime UDS architecture](./PLUGIN_RUNTIME_UNIX_SOCKET_ARCHITECTURE.md)。

## 8. 依赖安全（19.20/19.28）

```bash
scripts/security-audit.sh            # 依赖扫描（cargo-audit 可用时）+ 边界测试 + 护栏常量一致性
scripts/security-audit.sh --tests    # 仅边界测试（CI 最低要求）
```

CI 建议接入 rustsec 数据库（`cargo audit`）作为硬性门禁；当前仓库未安装时脚本降级打印
手动指引，不阻塞其余检查。

## 9. 安全报告（2026-08-21，19.29）

**已完成（代码 + 测试证据）**：

- YAML 三道资源护栏（§2）；表达式长度/嵌套防线（§3，栈溢出已复现并封堵）；
- 求值白名单沙箱（`system/exec/drop_table/spawn/fetch` 均不可调用，边界测试覆盖）；
- 诊断路径泄露修复（CF3104 循环 include）与 HTTP 拒绝体路径泄露测试；
- 缓存自锁死锁修复（守卫跨 match 存活 → get/put 分锁，全量编译测试回归保护）；
- 异常输入边界测试 12 项（`tests/cloudflow_security_bounds.rs`），纳入全量回归。

**设计级 / 后续迭代（如实标注）**：

- **模糊测试**（19.19）：以边界测试矩阵覆盖主要畸形输入（嵌套、别名、超长、深括号、
  控制字符）；未接入 `cargo-fuzz` 目标——后续迭代将基于现有护栏常量生成 fuzz 语料。
- **资源监控**（19.8/19.23）：编译侧已提供缓存命中/未命中计数与 `RuntimeMetrics`
  （执行侧成功/失败/重试计数）；进程级 RSS/CPU 采样属部署监控面（Prometheus/日志），
  未内置于编译器。
- **权限/资源声明**（语义 10.17/10.18）：Domain AST 当前无声明节点，能力级授权由 Agent
  执行期强制；前端引入声明语法后以语义规则接入（`CLOUDFLOW_DESIGN.md` V1.3 节）。

**错误码前缀**（与 `CLOUDFLOW_ERROR_DESIGN.md` 一致）：`CF`（DSL 编译）/ `CFY`（YAML 前端 +
共享表达式子系统）/ `CFI-7xxx`（IR 契约）/ `CFD-81xx`（调试运行面）/ `CF2–CF5`（生产运行面）。
