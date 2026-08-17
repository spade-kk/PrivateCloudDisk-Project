下面我按照**设计 CloudFlow DSL（工作流领域语言）规范文档**的方式整理。

目标：

- 给 Coder / 架构设计使用
- 作为 DSL 语言设计稿
- 指导 Rust Parser、AST、Compiler、Runtime 开发
- 类似 Kubernetes YAML Specification / Terraform HCL Specification 的语言设计清单

------

# **CloudFlow DSL 语言设计规范 V1.0**

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

    steps Vec<StepNode>

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
${step.result.count > 0}
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

"id":"weekly_report",

"nodes":[


{

"id":"collect",

"type":"task",

"action":"file.list"


},


{

"id":"generate",

"type":"plugin",

"action":"report.generate",

"depends":[

"collect"

]


}

]


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