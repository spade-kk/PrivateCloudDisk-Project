> 按照**设计 CloudFlow DSL（工作流领域语言）规范文档**的方式整理。
>
> 目标：
>
> - 给 Coder / 架构设计使用
> - 作为 DSL 语言设计稿
> - 指导 Rust Parser、AST、Compiler、Runtime 开发
> - 类似 Kubernetes YAML Specification / Terraform HCL Specification 的语言设计清单
>

------

# **CloudFlow DSL 语言设计规范 V1.0**

## 0. 规范收敛声明（2026-08-02）

> 本节是 V1.0 的规范性总纲；后续章节中的教学性伪代码仅用于解释概念。若示例与本节、`CLOUDFLOW_DEMO_DESIGN.md`或 Rust 契约测试冲突，以本节和契约测试为准。

- 文件扩展名固定为 `.flow`，根结构固定为 `workflow "name" { ... }`。
- 所有关键字大小写敏感且只允许小写。顶层只允许 `metadata`/`variables`/`trigger`/`runtime`/`steps`/`handlers`；任意命名块必须产生 `CF1201` 诊断。
- 字符串字面量使用双引号；多行说明使用三双引号。变量引用仅接受 `vars.<name>` 与 `steps.<step_id>.output`。
- 编译链路固定为 Pest PEG 解析 → AST → 语义分析 → `workflow.cloudflow.io/v1` IR；不存在 YAML 兼容解析器。
- 语义分析是强制阶段，至少覆盖重复步骤、未定义依赖、DAG 环、变量引用、action/plugin 命名及内置表达式函数白名单。
- `steps.<id>.output` 是对外规范语法；历史 Demo 中的 `<id>.output` 仅作过渡兼容输入，编译后必须归一化为 `$ref`，新文档和 IDE 不再生成旧形式。

### 0.2 V1.1 控制流与变量契约（2026-08-08）

> **需求关联：CloudFlow 语法覆盖、Runtime 动态执行、变量系统审计。** 本节补足 V1.0
> 对强类型变量、动态控制流和覆盖测试的可执行约束；本节优先级高于后文的历史教学片段。

**变量声明。** `variables` 同时支持外部输入、显式类型本地值、隐式推断本地值和未初始化变量：

```cloudflow
variables {
    source = input.string(required = true)
    retries: number = 3
    enabled = true
    targets: array = ["pdf", "xlsx"]
    options: object = {"archive": true}
    deferred: string
}
```

- 合法标识符为 `[A-Za-z_][A-Za-z0-9_-]*`，不允许变量名包含点号；点号只用于成员访问。
- number 字面量支持整数、小数和科学计数法（如 `10`、`3.14`、`1.25e3`），IR 保留 JSON number。
- `input.<type>(...)` 是唯一可由启动 API 覆盖的声明；本地声明在编译时确定类型，运行时不允许
  调用方静默覆盖。未初始化变量必须有显式类型，且只能被运行时受控写入。
- `ValueNode` 区分 `Literal`、`VariableRef` 和 `Expression`：JSON 数字/布尔/数组/对象不得被降级为
  字符串；引用固定为 `{ "$ref": "..." }`；表达式固定为结构化 `{ "$expr": ... }`。
- 语义阶段维护作用域和类型表，检查赋值、引用、表达式操作数与 `steps.<id>.output` 的步骤存在性；
  action 输出的具体字段在没有 Capability Schema 时按 `unknown` 处理，但仍必须先验证步骤存在。
- `foreach item` 的 `item` 只在对应 loop body 可见；`catch error` 的 `error` 只在 catch body 可见。
  将它们引用到外部会返回 `CF2002`，不会因名称收集或 Runtime 空值而被静默接受。

**新增/收敛关键字。** V1.1 支持 `while { <expr> } { ... }`、`assert { <expr> }` 与三元表达式
`condition ? when_true : when_false`。当前 V1.1 为每个 `while` IR 节点写入 `maxIterations=1000`，
Runtime 还会强制执行 10,000 的硬上限以防止无限循环；用户可配置的 `max_iterations` 语法尚未开放。
`assert` 失败产生可捕获的 `CF2202`。`match/case` 已预留 AST/IR 扩展位，
当前 V1.1 编译器明确拒绝。跨文件 `include "relative.flow"` 已支持受限模块复用：仅 CLI/受信任文件
编译模式可读取入口工作流目录内的相对 `.flow` 文件，循环或路径逃逸返回 `CF3103/CF3104`；导入模块只能
贡献 variables、steps、控制流和 handlers，不能覆盖入口 metadata、trigger 或 runtime。HTTP/IDE 内联源码
模式拒绝 include，防止任意文件系统读取。

**动态执行语义。** `foreach` 为每个元素独立调用步骤执行链，并最多按 `runtime.max_parallel` 并发；
每次调用均写入步骤 attempt 检查点。`while` 每轮按相同方式执行，受 maxIterations 保护。`try` 的 try 分支失败后只执行
catch 分支，finally 始终执行；异常以 `vars.<catch_binding>` 受限对象注入。`wait approval` 将实例持久化为
`WAITING_APPROVAL`，仅可由受内部鉴权保护的 resume API 附带审批结果恢复为 `READY`。

**顶层顺序边界。** Parser 保留顶层 `FlowNode` 的源码顺序。普通相邻 `step` 继续由显式
`depends_on` 决定并行性；当控制节点（`if`、`foreach`、`while`、`parallel`、`try`、`wait`、`assert`）
位于两个顶层节点之间时，Compiler 在 `spec.graph.edges` 写入顺序屏障。因此 `wait approval {}` 后的
step 不会在审批恢复前执行，也不会因为 AST 的分类存储而被提前调度。

**覆盖基线。** `PrivateCloudDisk-cloudflow-runtime/examples/coverage/` 是规范测试资产：每个 `.flow`
必须能编译为 JSON Schema 合法的 V1 IR，覆盖脚本既检查编译退出码，也检查节点、边、`$ref/$expr` 与
控制流映射。该脚本与 `cargo test`、fmt、clippy 一起属于 CI 发布门禁。

### 0.1 规范 AST

```rust
WorkflowNode {
    name: String,
    metadata: MetadataNode,
    variables: Vec<VariableDecl>,
    trigger: TriggerNode,
    runtime: RuntimeConfig,
    flow: Vec<FlowNode>, // 保留顶层源码顺序，Compiler 的唯一顺序输入
    steps: Vec<StepNode>,
    controls: Vec<FlowNode>,
    handlers: Vec<HandlerNode>,
    span: Span,
}

enum FlowNode {
    Step(Box<StepNode>),
    Condition(ConditionNode),
    Loop(LoopNode),
    While(WhileNode),
    Parallel(ParallelNode),
    TryCatch(TryCatchNode),
    Wait(WaitNode),
    Assert(AssertNode),
}
```

`VariableDecl.initializer` 和 action 参数使用 `ValueNode`区分字符串、数字、布尔、数组、对象、`VariableRef`与 `Expression`；每个源码节点保留 UTF-8 byte offset 及行列信息，供 CLI、HTTP 和 Monaco 统一定位。

------

# **1. DSL 总体定位**

## **1.1 语言类型**

CloudFlow DSL 是：

面向企业自动化流程编排的领域特定语言（Domain Specific Language）

用途：

- 文件自动处理
- 企业审批流程
- 数据分析流程
- AI 自动化任务
- 插件调用编排
- 定时任务
- 企业空间自动化

------

# **2. DSL 文件结构**

一个 CloudFlow 文件：

```
xxx.flow
```

基本结构：

```cloudflow
workflow "name" {

    metadata {}

    variables {}

    trigger {}

    runtime {}

    steps {}

    handlers {}

}
```

------

# **3. 一级语法结构（Top Level Grammar）**

## **Workflow 根节点**

关键字：

```
workflow
```

作用：



定义一个工作流。



示例：

```cloudflow
workflow "daily_report" {


}
```

AST:

```rust
WorkflowNode {

    name:String,

    metadata:MetadataNode,

    trigger:TriggerNode,

    steps: Vec<StepNode>

}
```

------

# **4. Metadata 元数据模块**

关键字：

```
metadata
```

作用：



描述工作流基本信息。



语法：

```cloudflow
metadata {

    display_name="销售日报"

    description="自动生成日报"

    version="1.0"

}
```

AST:

```rust
MetadataNode {


 display_name:String,


 description:String,


 version:String


}
```

字段：

| **字段**     | **类型** |
| ------------ | -------- |
| display_name | string   |
| description  | string   |
| version      | string   |
| author       | string   |
| tags         | array    |

------

# **5. Trigger 触发器**

关键字：

```
trigger
```

作用：

定义工作流启动方式。

支持：

- 定时
- 文件事件
- HTTP
- MQ事件
- 手动

------

## **5.1 Schedule**

语法：

```cloudflow
trigger {


 schedule {


    cron="0 8 * * 1"


    timezone="Asia/Shanghai"


 }


}
```

AST：

```rust
ScheduleTrigger {


 cron:String,

 timezone:String


}
```

------

## **5.2 Event Trigger**

例如：

文件上传完成触发。

```cloudflow
trigger {


 event {


    name="FileUploaded"


 }


}
```

AST:

```rust
EventTrigger {


 event_name:String


}
```

------

# **6. Runtime运行配置**

关键字：

```
runtime
```

作用：



定义执行环境。



示例：

```cloudflow
runtime {


 timeout=30m


 max_parallel=5


 retry_policy {


    max_attempts=3

 }


}
```

AST：

```rust
RuntimeConfig {


 timeout:u64,


 max_parallel:u32,


 retry:RetryPolicy


}
```

------

# **7. Variables变量系统**

关键字：

```
variables
```

作用：



定义工作流参数。



示例：

```cloudflow
variables {


 user_id=input.string()


 folder_id=input.string()


}
```

AST:

```rust
VariableDeclaration {


 name:String,


 type:DataType,


 default_value:Value


}
```

支持：

```
string

number

boolean

array

object

file

user

space
```

------

# **8. Step任务节点（核心）**

关键字：

```
step
```

作用：



定义执行节点。



语法：

```cloudflow
step xxx {


}
```

AST：

```rust
StepNode {


 id:String,


 name:String,


 action:ActionNode,


 condition:Expression,


 depends_on:Array,


 retry:RetryPolicy


}
```

------

# **9. Action动作系统**

关键字：

```
action
```

作用：



定义具体执行能力。



格式：

```
action namespace.function
```

例如：

```cloudflow
action file.list
```

AST：

```rust
ActionNode {


 namespace:String,


 function:String,


 arguments:Object


}
```

------

# **10. 内置能力调用**

例如：

文件列表：

```cloudflow
action file.list {


 node=vars.node_id


}
```

AST:

```rust
ActionCall {


 provider:"builtin",


 service:"file",


 method:"list"


}
```

------

# **11. 插件调用**

关键字：

```
plugin
```

示例：

```cloudflow
action plugin.report.generate {


}
```

AST：

```rust
PluginAction {


 plugin_id:String,


 function:String,


 version:String


}
```

------

# **12. 参数输入 with**

关键字：

```
input
```

或者：

```
with
```

示例：

```cloudflow
action file.save {


 input {


   file=id


 }


}
```

AST:

```rust
ArgumentNode {


 key:String,


 value:Expression


}
```

------

# **13. 输出 output**

关键字：

```
output
```

作用：



保存节点结果。



示例：

```cloudflow
output report_file
```

AST：

```rust
OutputNode {


 variable:String


}
```

------

# **14. 依赖关系**

关键字：

```
depends_on
```

示例：

```cloudflow
depends_on collect_files
```

AST:

```rust
DependencyNode {


 dependencies:Vec<String>


}
```

编译：



生成 DAG。



例如：

```
A

|

B

|

C
```

------

# **15. 条件判断**

关键字：

```
if
else
```

示例：

```cloudflow
if {


 sales > 10000


}
```

AST：

```rust
ConditionNode {


 expression:Expression,


 true_branch:Vec<Node>,


 false_branch:Vec<Node>


}
```

支持：



比较：

```
>

<

==

!=
```

逻辑：

```
&&

||

!
```

------

# **16. 循环**

关键字：

```
foreach
```

示例：

```cloudflow
foreach file in files {


 step process {


 }


}
```

AST：

```rust
LoopNode {


 iterator:String,


 collection:Expression,


 body:Array<Node>


}
```

------

# **17. 并行**

关键字：

```
parallel
```

示例：

```cloudflow
parallel {


 step pdf {}

 step excel {}

}
```

AST：

```rust
ParallelNode {


 branches:Array<Node>


}
```

------

# **18. 错误处理**

关键字：

```
try

catch

finally
```

示例：

```cloudflow
try {


 step generate {}



}

catch {


 step notify {}

}
```

AST：

```rust
ExceptionNode {


 try_nodes,


 catch_nodes,


 finally_nodes


}
```

------

# **19. Retry 重试**

关键字：

```
retry
```

示例：

```cloudflow
retry {


 max_attempts=3


 strategy="exponential"


}
```

AST：

```rust
RetryNode {


 count:u32,


 strategy:String


}
```

------

# **20. Wait等待**

关键字：

```
wait
```

例如：



等待人工审批。

```cloudflow
wait approval {


 timeout=24h


}
```

AST:

```rust
WaitNode {


 type:String,


 timeout:u64


}
```

------

# **21. 表达式系统 Expression**

例如：

```cloudflow
steps.result.output > 0
```

解析：

Expression AST

支持：

## **Literal**

```text
100
"abc"
true
```

AST：

```rust
LiteralNode
```

------

## **Variable**

```text
vars.xxx
```

AST：

```rust
VariableNode
```

------

## **Function Call**

```text
size(files)
```

AST：

```rust
CallNode
```

------

## **Binary Expression**

```text
a > b
```

AST：

```rust
BinaryExpression
```

------

# **22. AST完整结构设计**

Rust:

```rust
enum ASTNode {


 Workflow(WorkflowNode),


 Step(StepNode),


 Action(ActionNode),


 Condition(ConditionNode),


 Loop(LoopNode),


 Parallel(ParallelNode),


 Retry(RetryNode),


 Expression(ExpressionNode)


}
```

------

# **23. Compiler流程**

```
CloudFlow Source


        |

        ↓


Lexer


        |

        ↓


Parser


        |

        ↓


AST


        |

        ↓


Semantic Check


        |

        ↓


Workflow IR


        |

        ↓


Runtime Scheduler
```

------

# **24. Workflow IR设计**

最终生成：

```json
{
  "apiVersion": "workflow.cloudflow.io/v1",
  "kind": "Workflow",
  "metadata": { "name": "weekly_report" },
  "spec": {
    "trigger": { "type": "manual" },
    "variables": {},
    "graph": {
      "nodes": [
        {
          "id": "collect",
          "type": "task",
          "action": {
            "provider": "builtin",
            "service": "file",
            "method": "list",
            "arguments": {}
          },
          "dependsOn": []
        },
        {
          "id": "generate",
          "type": "plugin",
          "action": {
            "provider": "plugin",
            "pluginId": "report",
            "function": "generate",
            "arguments": {}
          },
          "dependsOn": ["collect"]
        }
      ],
      "edges": [{ "from": "collect", "to": "generate" }]
    },
    "outputs": {}
  },
  "runtime": {},
  "security": {},
  "extensions": {}
}
```

------

# **25. 第一版必须实现的关键字列表**

## **核心**

```
workflow

metadata

trigger

runtime

variables

steps

handlers

step

action

output

depends_on
```

------

## **控制流**

```
if

else

foreach

parallel

try

catch

finally

wait
```

------

## **执行控制**

```
retry

timeout

limit

condition
```

------

## **数据**

```
input

output

file

user

space

plugin

builtin
```

------

# **26. 开发阶段规划**

## **Phase 1 基础DSL**

实现：

- workflow
- step
- action
- depends_on
- output

生成 DAG

------

## **Phase 2 控制流**

增加：

- if
- foreach
- parallel
- retry

------

## **Phase 3 企业级**

增加：

- 人工审批
- 权限
- RBAC
- 审计
- 插件能力发现
- UI可视化编排

------

## **Phase 4 云平台级**

增加：

- DSL ↔ 图形化双向转换
- AI生成Workflow
- Marketplace能力调用
- 多租户隔离

------

这份结构基本已经接近一个企业级 Workflow DSL 的设计蓝图，可以直接作为 CloudFlow Runtime + Compiler 的第一版设计稿。

---

## V1.2 新语法（2026-08-18）

本版在严格保持 V1.0/V1.1 兼容的前提下新增以下结构（实现见 docs/CLOUDFLOW_V1.2_DSL_EXTENSION.md）：

- `switch <expr> { case <v> => { ... } ... default => { ... } }`：多分支选择，取代原来仅预留在 AST/IR 的 match/case 扩展位。
- `retry_on [Exception, ...]`：步骤指定可重试异常白名单（内置异常类型：TimeoutException、NetworkException、PluginException、StorageException、PermissionException、TransientException、ValidationException、WorkerUnavailableException、GenericException）。
- `timeout { duration = 30s; on_timeout = "fail" }`：超时块，on_timeout 仅允许 fail/continue/retry。
- `delay 5s`：固定延迟步骤。
- `environment { KEY = "production" }`：环境变量声明（区别于 variables，编译期注入、仅供字面量）。
- `namespace com.example.workflows`：工作流命名空间。
- `import "common.flow" as common`：带别名的模块导入（与 include 共用受限文件模式解析）。
- `tag "finance"` 与 `metadata { changelog = "..." }`：分类标签与版本变更记录。

以下 V1.2 结构也已在 2026-08-18 全部实现（实现与错误码对照见 docs/CLOUDFLOW_V1.2_DSL_EXTENSION.md）：

- `for i in range(from, to)` 索引循环与 `for x in <array>` 集合循环，配合 `break` / `continue`。
- `parallel(max_concurrency=N)`：分支级并发数上限。
- `validate { <bool> }` / `expect { <bool> }`：块级与步骤后校验，求值 false 时运行时报错。
- `trigger { interval = 5m }`：周期触发；`trigger { http { path; method } }`：webhook 详配。
- `on_error { ... }`：步骤级错误处理（区别于 try/catch）。
- `notify { channel; to; message }`：内建通知。
- `vars.files | filter(size > 100) | map(name) | reduce(sum)`：map/filter/reduce 集合管道。
- `"hello ${vars.name}"`：字符串模板（运行期变量替换）。
- `audit { level; description }`：工作流级审计注解。
- `step group g { step ... }`：步骤组合（编译期扁平化）。
- `use <alias>` / `with <alias>`：从已导入模块注入默认参数。
- `depends_on A if <bool>`：条件依赖（条件为 false 时豁免等待 A）。
- 步骤级 `return <expr>?`：提前结束当前工作流分支并返回输出。

全部结构的 grammar/AST/IR/语义/Runtime/错误码与测试均已落地；详见 `docs/CLOUDFLOW_V1.2_DSL_EXTENSION.md`
与 `docs/CLOUDFLOW_ERROR_DESIGN.md` 的 CF44xx 错误码表。
