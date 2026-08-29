# **CloudFlow Workflow IR 设计规范文档 V1.0**

## 0. 规范性约定（2026-08-02）

> 本节收敛早期草案中 `graph`位置和字段命名的不一致。后续所有 Compiler、Runtime、Workflow Service 及 IDE 交互均以此契约为准。

- `graph` 只存在于 `spec.graph`，根层不得重复出现。
- `spec.graph.edges` 是 DAG 调度权威数据；节点上的 `dependsOn` 只是便于读取的同源投影，二者不得相互矛盾。
- JSON 字段使用 camelCase，版本固定为 `apiVersion: workflow.cloudflow.io/v1`、`kind: Workflow`。
- 变量引用生成 `{ "$ref": "vars.name" }` 或 `{ "$ref": "steps.id.output" }`；表达式生成结构化 `$expr`，不用与普通字符串无法区分的占位符。
- `extensions` 只用于版本兼容扩展；异常处理节点保存在 `extensions.handlerGraphs`的独立图中，不得在主 DAG 成功路径中直接调度。

### 0.1 V1.1 强类型值与动态控制流补充（2026-08-08）

> **需求关联：CloudFlow 变量系统审计与 Runtime 动态执行增强。** `spec.variables` 的每一项
> 除 `type`、`required`、`default` 外可带 `value` 与 `source`。`source=input` 表示调用方可提供的
> 输入；`source=local` 表示编译器确定的本地变量，运行时不得被启动请求覆盖；`source=deferred` 表示
> 有显式类型但在启动时无值。

值编码是强制的：`10`、`true`、`[1,2]`、`{"a":1}` 保持原生 JSON 类型；引用为
`{"$ref":"vars.x"}`；表达式为结构化
`{"$expr":{"operator":"+","left":{"$ref":"vars.a"},"right":1}}`。Runtime 必须拒绝将
字符串当作 number/boolean/array/object 的隐式转换。

`loopConfig` 现支持：

```json
{
  "kind": "foreach",
  "iterator": "item",
  "collection": {"$ref": "vars.items"},
  "body": ["process"],
  "maxIterations": 1000
}
```

`kind=while` 时以 `condition` 替代 `collection`/`iterator`。动态子步骤使用稳定 ID
`<control-node-id>[<iteration>].<node-id>` 持久化，例如 `loop-42[3].process`；同一动态实例的
重试才递增其自身 attempt，控制节点输出记录迭代总数。因此并行 foreach 元素可独立审计、重放和关联
输出，不会被误判为同一静态步骤。`assert` 映射为 `type=assert` 与 `condition`，失败代码为 `CF2202`。
`wait approval` 进入 `WAITING_APPROVAL` 子状态，恢复请求的审批值保存到
`variables.__wait.<nodeId>`，再按 `$ref` 注入后续节点。

Compiler 保留顶层 Flow 的源码顺序，并仅在控制节点前后增加顺序边：普通 `step` 的并行性仍由
`dependsOn`/`edges` 决定；控制节点成为边界，避免 wait、try、loop 后的副作用提前执行。

`include "relative.flow"` 不是 Runtime 节点：受信任文件模式在编译期完成受根目录、循环与深度限制的
模块合并，随后将模块路径审计信息写入 `extensions.includes`。HTTP/IDE 源码编译拒绝 include，避免
Runtime 或浏览器取得任意文件系统读取能力。

## **1. Workflow IR 定位**

Workflow IR（Intermediate Representation，工作流中间表示）是：

CloudFlow DSL 编译器输出给 Workflow Runtime 执行引擎的数据结构。

整体链路：

```
CloudFlow DSL

      |
      |
      v

Lexer

      |
      |
      v

Parser

      |
      |
      v

AST

      |
      |
      v

Semantic Analyzer

      |
      |
      v

Workflow IR

      |
      |
      v

Workflow Runtime

      |
      |
      v

Task Scheduler

      |
      |
      v

MQ / Plugin Runtime / Service API
```

------

# **2. Workflow IR 设计目标**

Workflow IR 不负责：

- 用户编程
- 业务语法表达
- 复杂语法糖

Workflow IR 负责：

- 描述执行 DAG
- 描述任务节点
- 描述节点依赖
- 描述输入输出
- 描述权限
- 描述插件调用
- 描述错误处理
- 描述运行策略

核心原则：

DSL 面向人，IR 面向机器。

------

# **3. Workflow IR 顶层结构**

JSON格式：

```json
{
    "apiVersion": "workflow.cloudflow.io/v1",
    "kind": "Workflow",

    "metadata": {},

    "spec": {},

    "runtime": {},

    "security": {},

    "extensions": {}
}
```

------

# **4. apiVersion**

作用：

版本兼容。

格式：

```json
{
 "apiVersion":"workflow.cloudflow.io/v1"
}
```

未来：

```
v1

v2

v3
```

支持：

- IR升级
- Runtime兼容

------

# **5. kind**

固定：

```json
{
 "kind":"Workflow"
}
```

未来扩展：

```
Workflow

Task

Template

SubWorkflow

EventFlow
```

------

# **6. Metadata 元数据规范**

结构：

```json
"metadata": {

    "id":"uuid",

    "name":"weekly_report",

    "displayName":"销售周报",

    "version":"1.0",

    "owner":"user001",

    "labels":{

        "department":"finance"

    }

}
```

## **字段**

| **字段** | **说明**   |
| -------- | ---------- |
| id       | 唯一ID     |
| name     | 工作流名称 |
| version  | 版本       |
| owner    | 创建者     |
| labels   | 标签       |

------

# **7. Spec 工作流定义主体**

结构：

```json
"spec": {


    "trigger": {},


    "variables": {},


    "graph": {

        "nodes": [],

        "edges": []

    },


    "outputs": {}


}
```

------

# **8. Trigger触发定义**

## **Schedule触发**

```json
"trigger":{

"type":"schedule",

"cron":"0 8 * * 1",

"timezone":"Asia/Shanghai"

}
```

------

## **Event触发**

例如：

文件上传完成事件。

```json
{

"type":"event",

"event":"FileUploaded"


}
```

------

## **HTTP触发**

```json
{

"type":"http",

"path":"/workflow/start"

}
```

------

# **9. Variables变量定义**

例如：

```json
"variables":{


"folder_id":{

"type":"string",

"required":true

}


}
```

支持类型：

```
string

number

boolean

array

object

file

user

space

credential
```

------

# **10. Graph 工作流DAG结构**

核心：

```json
"graph":{


"nodes":[],

"edges":[]


}
```

------

# **11. Node节点规范**

每一个step最终转换为Node。

示例：

```json
{
"id":"collect_files",

"type":"task",

"name":"收集文件"

}
```

Node类型：

```
task

plugin

condition

parallel

loop

wait

human

subworkflow
```

------

# **12. Task Node任务节点**

结构：

```json
{

"id":"collect",

"type":"task",

"action":{

"provider":"builtin",

"service":"file",

"method":"list",

"arguments":{}

}

}
```

------

# **13. Action动作模型**

定义：

```json
"action":{


"provider":"builtin",

"service":"file",

"method":"list"

}
```

三个层级：

```
provider

    |

service

    |

method
```

例如：

```
builtin.file.list


plugin.report.generate


api.user.notify
```

------

# **14. Plugin Node插件节点**

例如：

```json
{

"type":"plugin",

"plugin":{


"id":"xxxx",

"version":"1.0",

"function":"generate_report"


}


}
```

运行：

```
Workflow Runtime

        |

Plugin Runtime

        |

Sandbox

        |

Plugin Script
```

------

# **15. Node输入 Input**

结构：

```json
"inputs":{


"files":{


"source":"node.collect.output"

}


}
```

支持：

## **常量**

```json
{

"value":"abc"

}
```

------

## **引用**

```json
{

"ref":"steps.xxx.output"

}
```

------

# **16. Node输出 Output**

结构：

```json
"outputs":{


"result":{

"type":"file"

}

}
```

------

# **17. Dependency依赖关系**

方式1：

Node内部：

```json
"dependsOn":[

"collect"

]
```

------

方式2：

Graph Edge：

```json
"edges":[


{

"from":"collect",

"to":"aggregate"

}


]
```

推荐：

生产环境使用 Edge。

------

# **18. Condition条件节点**

DSL：

```cloudflow
if sales > 10000
```

IR：

```json
{

"id":"check_sales",

"type":"condition",


"expression":{


"operator":">",

"left":"sales",

"right":10000


},


"branches":{


"true":"approve",

"false":"reject"


}


}
```

------

# **19. Expression表达式模型**

支持：

## **Binary**

```json
{

"type":"binary",

"operator":">"

}
```

------

## **Logical**

```json
{

"type":"logical",

"operator":"AND"

}
```

------

## **Function**

```json
{

"type":"call",

"name":"length"

}
```

------

# **20. Parallel并行节点**

DSL：

```cloudflow
parallel{}
```

IR：

```json
{

"type":"parallel",

"branches":[


"generate_pdf",

"generate_excel"


]


}
```

Runtime：

创建多个Task。

------

# **21. Loop循环节点**

例如：

foreach。

IR：

```json
{

"type":"loop",

"iterator":"file",

"collection":"files",


"body":[

]

}
```

------

# **22. Retry重试策略**

结构：

```json
"retry":{


"maxAttempts":3,

"strategy":"exponential",

"backoffSeconds":5


}
```

策略：

```
fixed

linear

exponential
```

------

# **23. Timeout超时**

```json
"timeouts":{


"task":300,

"workflow":3600


}
```

单位：

秒。

------

# **24. Error Handler错误处理**

结构：

```json
"errors":{


"onFailure":{


"action":"notify"


}


}
```

支持：

```
retry

ignore

rollback

notify

compensation
```

------

# **25. Compensation补偿事务**

类似Saga。

例如：

订单创建成功：

失败：

取消订单。

IR：

```json
{

"compensation":{


"task":"cancel_order"


}

}
```

------

# **26. Permission权限模型**

用于插件安全。

```json
"security":{


"permissions":[


"file.read",

"file.write"


]


}
```

------

# **27. Resource资源限制**

Runtime执行限制。

```json
"runtime":{


"resources":{


"cpu":"1",

"memory":"512Mi"


}

}
```

------

# **28. Runtime执行配置**

```json
"runtime":{


"engine":"cloudflow",

"queue":"workflow.task",

"priority":5


}
```

------

# **29. 状态模型**

Workflow Runtime维护：

```
CREATED

|

READY

|

RUNNING

|

WAITING

|

SUCCESS

|

FAILED

|

CANCELLED
```

------

# **30. Task状态模型**

```
PENDING

RUNNING

RETRYING

SUCCESS

FAILED

SKIPPED
```

------

# **31. 完整Workflow IR示例**

```json
{
"apiVersion":"workflow.cloudflow.io/v1",

"kind":"Workflow",


"metadata":{

"name":"weekly_report"

},


"spec":{


"trigger":{


"type":"schedule",

"cron":"0 8 * * 1"


},



"graph":{


"nodes":[


{

"id":"collect",

"type":"task",

"action":{

"service":"file",

"method":"list"

}


},



{

"id":"generate",

"type":"plugin",

"dependsOn":[

"collect"

],


"action":{

"provider":"plugin",

"pluginId":"report",

"function":"generate",

"arguments":{}

}


}


]


}

}

}
```

------

# **32. Workflow Runtime 根据 IR 执行**

执行过程：

```
IR Loader

    |

Validator

    |

DAG Builder

    |

Scheduler

    |

Task Executor

    |

Plugin/API/MQ

    |

State Store
```

------

# **33. 第一版 IR 必须实现模块清单**

## **基础**

- apiVersion
- metadata
- workflow
- node
- edge

## **执行**

- task
- action
- input
- output

## **控制流**

- condition
- parallel
- loop

## **稳定性**

- retry
- timeout
- error handler

## **企业能力**

- permission
- audit
- resource limit
- version

------

最终 CloudFlow 的完整架构应该是：

```
             CloudFlow DSL

                  |
                  v

          CloudFlow Compiler

                  |
                  v

          Workflow IR(JSON)

                  |
                  v

          Workflow Runtime

                  |
      -------------------------
      |          |            |

   MQ Task   Plugin      Service API
```

这个 IR 设计就是整个 CloudFlow 平台的核心契约，相当于 Kubernetes 的 API Object，也是后续**图形化编排、AI生成工作流、DSL双向转换、插件市场能力发现**的基础。

---

## V1.2 IR 扩展

向后兼容新增（均带 serde default + skip_serializing_if，旧 IR 仍可解析）：

- `node.retryOn: string[]`（步骤可重试异常白名单）。
- `node.onTimeout: string`（fail/continue/retry）。
- `node.switchConfig: object`：`{ subject: <expr>, cases: [{value, body:[id]}], default: [id] }`。
- `node.delayMs: number`（delay 节点）。
- `node.type="switch" | "delay"` 新控制节点类型。
- `metadata.namespace`、`metadata.changelog`、`metadata.tags`。
- `spec.environment: map<string, json>`。
- `extensions.importAliases: map<alias, path>`。

### V1.2 IR 扩展 Tranche 2（2026-08-18）

- `loopConfig.kind ∈ {foreach, while, for, for-range}`：`for-range` 携带 `from`/`to`（可求值的
  `$ref`/`$expr`），`for` 携带 `collection`；均为对既有 `loopConfig` 字段的向后兼容扩展。
- `node.type="validate" | "break" | "continue"`：校验节点（`condition` 为布尔表达式）与循环控制节点。
- `parallel.maxConcurrency: number`：分支级并发数上限（缺省沿用 `runtime.maxParallel`）。
- `node.condition` 可承载三元/比较等结构化 `$expr`，不再局限于单一运算符形态。

### V1.2 IR 扩展 Tranche 3（2026-08-18）

- `node.type="notify" | "return"`：内建通知节点与步骤级提前返回节点。
- `node.notifyConfig: { channel, recipient, message }`：通知渠道/接收者/消息。
- `node.onError: { nodes: string[] }`：步骤失败时的错误处理子节点 ID 列表。
- `node.dependsCondition: <bool 表达式>`：条件依赖；求值 false 时该节点无需等待静态依赖完成。
- 值编码新增：
  - `{"$template": [段1, 段2, ...]}`：字符串模板，段为字符串字面量或 `{"$ref": ...}`。
  - `{"$pipeline": {"input": <expr>, "op": {"op": "filter"|"map"|"reduce", "predicate"/"field"/"function"}}}`：
    map/filter/reduce 集合管道。
- `TriggerIr.type ∈ {manual, schedule, event, http, interval}`：`interval` 带 `every`；`http` 带
  `method`（GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS）。
- `spec.audit: { level: low|medium|high, description? }`：工作流级审计注解。
- step group 为编译期组合语法：组在编译阶段展开为普通 `task` 节点（组名仅在语义层 CF4418 校验），
  IR 不新增步组节点，避免幻影边。
