# **CloudFlow Workflow IR 设计规范文档 V1.0**

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

    "graph": {},

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


    "nodes": [],


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

"service":"file",

"method":"list"

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


"plugin":{

"id":"report",

"function":"generate"

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