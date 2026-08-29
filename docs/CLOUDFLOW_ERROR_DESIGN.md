#  **CloudFlow DSL Compiler 错误诊断系统设计规范文档**



这个文档的目标不是简单输出 `"syntax error"`，而是设计一个接近 **Python / Java Compiler / TypeScript Compiler / Rust Compiler** 级别的错误诊断体系。



它属于 CloudFlow DSL Runtime 的一个核心子模块：

```
CloudFlow DSL Compiler
        |
        |
        +-- Lexer (词法分析)
        |
        +-- Parser (语法分析)
        |
        +-- AST Builder
        |
        +-- Semantic Analyzer (语义分析)
        |
        +-- Type Checker
        |
        +-- IR Generator
        |
        +-- Error Diagnostic Engine  <-- 本设计
```

------

# **CloudFlow DSL Compiler Error Diagnostic System Design**

版本：

```
CloudFlow DSL Compiler v1.0
Error Diagnostic Specification
```

------

# **一、设计目标**

## **1. 提供企业级代码错误反馈能力**

类似：

Python:

```
SyntaxError:
unexpected EOF while parsing

File "main.py", line 5
```

Java:

```
error: cannot find symbol
symbol: variable username
location: class UserService
```

Rust:

```
error[E0425]: cannot find value `x` in this scope

 --> main.rs:4:9
```

CloudFlow DSL 应达到：

```
ERROR CF1202

workflow.flow:23:15

Unknown keyword "triger"

Did you mean:
trigger

23 | triger:
   | ^^^^^^

Available keywords:
- trigger
- schedule
- manual
- event
```

------

# **二、错误类型分类体系**

CloudFlow DSL 错误分为：

```
Compiler Error
        |
        |
        +-- Lexical Error
        |
        +-- Syntax Error
        |
        +-- Semantic Error
        |
        +-- Type Error
        |
        +-- Reference Error
        |
        +-- Runtime Validation Error
        |
        +-- Permission Error
```

------

# **三、错误编码规范**

类似：

Python:

```
SyntaxError
```

Rust:

```
E0308
```

CloudFlow:



设计：

```
CF + 类型 + 编号
```

例如：

```
CF1201
```

含义：

```
CF
CloudFlow

1
Compiler

001
错误编号
```

------

## **错误等级**

```yaml
severity:

INFO
WARNING
ERROR
FATAL
```

例如：

```
WARNING CF2001
unused variable

ERROR CF1201
invalid syntax
```

------

# **四、错误信息标准结构**

所有错误统一格式：

```json
{
  "code": "CF1201",

  "severity": "ERROR",

  "category": "SYNTAX_ERROR",

  "message":
  "Unexpected token",

  "location": {

    "file":
    "weekly_report.flow",

    "line":23,

    "column":15,

    "startOffset":532,

    "endOffset":538

  },

  "source":

  {
    "lineText":
    "triger:",

    "pointer":
    "^^^^^^"

  },

  "suggestions":[
    "trigger"
  ],

  "help":
  "Trigger defines workflow execution source",

  "documentationUrl":
  "/docs/cloudflow/errors/CF1201",

  "cliOutput":
  "ERROR CF1201\n\nweekly_report.flow:23:15\n\nUnexpected token"

}
```

------

# **五、错误定位系统设计**

## **1. 文件定位**

必须支持：

```
filename
module
workflow name
```

例如：

```
workflow/report.flow
```

------

## **2. 行号定位**

保存：

```
line
```

例如：

```
23
```

------

## **3. 列定位**

字符位置：

```
column
```

例如：



代码：

```yaml
triger:
```

输出：

```
      ^
```

------

## **4. 字符范围**

不仅定位一个字符。

支持：

```json
{
 startColumn:10,

 endColumn:16
}
```

用于：

```
^^^^^^
```

------

# **六、Lexer词法错误设计**

Lexer负责：

字符 -> Token

例如：

代码：

```
workflow @@@
```

错误：

```
CF1101

Invalid character

Unexpected character '@'

line 1 column 10


workflow @@@
          ^^^
```

------

## **Lexer错误类型**

| **编号** | **错误**     |
| -------- | ------------ |
| CF1101   | 非法字符     |
| CF1102   | 字符串未闭合 |
| CF1103   | 数字格式错误 |
| CF1104   | 非法转义     |

------

# **七、Parser语法错误设计**

Parser阶段：

Token -> AST

这是最多错误来源。

------

## **示例1**

错误：

```cloudflow
workflow {

name=test

}
```

缺少：

```
:
```

输出：

```
CF1201

Expected ':'

line 2 column 10


name=test
    ^
    
Expected:

name: "value"
```

------

## **示例2**

未知关键字

输入：

```cloudflow
triger:
 schedule:
```

输出：

```
CF1202

Unknown keyword

triger


Did you mean:

trigger
```

------

## **Parser错误列表**

| **编号** | **说明**     |
| -------- | ------------ |
| CF1201   | 缺少token    |
| CF1202   | 未知关键字   |
| CF1203   | 括号未关闭   |
| CF1204   | 缩进错误     |
| CF1205   | 语法结构错误 |
| CF1206   | 重复字段     |

------

# **八、AST阶段错误**

Parser成功：

但是AST结构非法。

例如：

DSL:

```cloudflow
step:

name:
```

AST:

```
StepNode

name=null
```

错误：

```
CF1301

Required property missing

Step.name cannot be empty
```

------

# **九、语义分析错误**

这是企业级DSL最重要部分。

语法正确：

但是业务没有意义。

------

## **示例1**

引用不存在步骤

代码：

```cloudflow
step save_report {
    depends_on generate_report
    action file.save {}
}
```

但是没有：

```
generate_report
```

错误：

```
CF2001


Unknown step reference


Step:
save_report


Reference:

generate_report


Available:

collect_files
aggregate_data
```

------

## **示例2**

循环依赖

例如：

```cloudflow
step A { depends_on B action task.run {} }
step B { depends_on C action task.run {} }
step C { depends_on A action task.run {} }
```

错误：

```
CF2002


Workflow dependency cycle detected


A -> B -> C -> A
```

------

# **十、变量检查错误**

CloudFlow支持：

```
vars.xxx
```

错误：

```cloudflow
vars.user_id
```

但是：

```
user_id
```

不存在。



输出：

```
CF2101


Unknown variable


vars.user_id


Available variables:

vars.company_id

vars.report_id
```

------

# **十一、插件能力检查**

例如：

```cloudflow
step create_report {
    action plugin {
        id = "x"
        function = "create_report"
    }
}
```

但是插件不存在。



错误：

```
CF3001


Plugin not found


Plugin:

x


Available:

report-generator
excel-parser
```

------

# **十二、参数类型检查**

插件定义：

```json
{
 generate_report:
 {
   input:
   {
    data:Array
   }
 }
}
```

DSL:

```cloudflow
with:

data:"hello"
```

错误：

```
CF3101


Type mismatch


Expected:

Array


Received:

String


Location:

generate_report.with.data
```

------

# **十三、权限错误**

例如：

工作流：

```
file.delete
```

但是当前用户没有权限。



输出：

```
CF4001


Permission denied


Operation:

file.delete


Required:

FILE_DELETE


Current:

FILE_READ
```

------

# **十四、错误恢复建议系统**

类似：

Rust:

```
help:
```

CloudFlow:



支持：

```
suggestions
```

例如：



错误：

```
triger
```

建议：

```
trigger
```

错误：

```
file.lsit
```

建议：

```
file.list
```

------

# **十五、编译器错误输出接口设计**

Compiler提供：

## **HTTP API**

请求：

```
POST

/api/v1/compile
```

Request:

```json
{

"source":

"workflow xxx",

"filename":

"report.flow"

}
```

Response:



成功：

```json
{

"success":true,

"astId":

"xxx",

"ir":

{}

}
```

失败：

```json
{

"success":false,

"errors":[

{

"code":"CF1201",

"line":20,

"column":5,

"message":

"Expected ':'"

}

]

}
```

------

# **十六、前端IDE错误展示设计**

类似 VS Code。

需要：

## **1. 红色波浪线**

位置：

```
line
column
range
```

------

## **2. Hover提示**

鼠标移动：

显示：

```
CF1202

Unknown keyword

Did you mean trigger?
```

------

## **3. Problems窗口**

类似：

VS Code:

```
Problems(3)


ERROR CF1201

ERROR CF2001

WARNING CF3001
```

------

# **十七、Compiler内部模块设计**

Rust项目结构：

```
cloudflow-runtime/


src/


compiler/

    lexer/
    
    parser/
    
    ast/
    
    semantic/
    
    typecheck/
    
    ir/


diagnostic/

    error.rs

    span.rs

    reporter.rs

    formatter.rs
```

------

# **十八、核心数据结构设计**

Rust:

```rust
struct Diagnostic {

    code:String,

    severity:Severity,

    message:String,

    span:Span,

    suggestion:Vec<String>

}


struct Span {

    start:usize,

    end:usize,

    line:u32,

    column:u32

}
```

------

# **十九、完整编译流程**

最终：

```
CloudFlow DSL


        |
        v


Lexer


        |
        v


Token Stream


        |
        v


Parser


        |
        v


AST


        |
        v


Semantic Analyzer


        |
        v


Validation


        |
        v


IR Generator


        |
        v


Workflow IR
```

任何阶段失败：

```
Error Diagnostic Engine

        |

        v

JSON Error Response

        |

        v

Frontend IDE
```

------

# **二十、最终设计目标**

CloudFlow DSL最终体验应该达到：

用户写：

```
workflow {

}
```

如果错误：



不是：

```
compile failed
```

而是：

```
ERROR CF2001

weekly_report.flow:32:8


Step "save_report"

depends on unknown step


Did you mean:

generate_report


Dependency graph:

collect
 |
aggregate
 |
generate_report
```

------

这套错误诊断体系会直接决定你的 CloudFlow DSL 是否像 **企业级自动化平台（例如 Kubernetes CRD、GitHub Actions、Terraform、Temporal Workflow）**。

它不是简单解析 YAML，而是完整的一门工程语言，所以必须有：

- Lexer
- Parser
- AST
- Semantic Analyzer
- Diagnostic Engine
- IR Compiler

这也是为什么你的 CloudFlow DSL Runtime 应该作为一个独立 Rust 项目建设。

你现在的架构已经明确：

```text
CloudFlow Runtime 项目

├── cloudflow-runtime
│      工作流运行时
│      DSL执行
│      IR执行
│      调度执行
│
└── cloudflowc
       CloudFlow Compiler
       DSL编译器
       语法检查
       AST解析
       IR生成
       错误诊断输出
```

所以之前的 **错误信息标准结构** 需要调整：

- 保留 JSON Error Model（给 IDE、Web 编辑器、客户端可视化使用）
- 新增 CLI Diagnostic Output（给命令行、开发者工具、CI/CD 使用）
- JSON 中增加 `formattedOutput` 字段，用于直接渲染 CLI 风格错误
- 两种输出来源于同一个 Diagnostic Engine，不重复实现

下面只修改相关章节，其余章节保持不变。

------

# **四、错误信息标准结构（修改版）**

CloudFlow Compiler 采用统一 Diagnostic Model，同时支持两种错误展示格式：

```text
CloudFlow Diagnostic Engine

          |
          |
          +----------------+
          |                |
          v                v

JSON Diagnostic       CLI Diagnostic

IDE/Web UI            Terminal/CI/CD
```

------

# **4.1 JSON错误数据结构（IDE模式）**

JSON主要用于：

- Web IDE
- VS Code插件
- 工作流编辑器
- 图形化错误标注
- 红色波浪线
- 错误面板展示

标准结构：

```json
{
  "code": "CF1202",

  "severity": "ERROR",

  "category": "SYNTAX_ERROR",

  "message":
  "Unknown keyword \"triger\"",


  "location": {

    "file":
    "workflow.flow",

    "line":23,

    "column":15,

    "startOffset":532,

    "endOffset":538

  },


  "source":

  {

    "lineText":
    "triger:",

    "pointer":
    "^^^^^^"

  },


  "suggestions":[

    "trigger"

  ],


  "help":

  "Available workflow trigger definitions include trigger, schedule, manual and event",

  "documentationUrl":

  "/docs/cloudflow/errors/CF1202",


  "cliOutput":

  "ERROR CF1202\n\nworkflow.flow:23:15\n\nUnknown keyword \"triger\"\n\nDid you mean:\ntrigger\n\n23 | triger:\n   | ^^^^^^\n\nAvailable keywords:\n- trigger\n- schedule\n- manual\n- event"

}
```

------

# **4.2 字段说明**

| **字段**    | **作用**     |
| ----------- | ------------ |
| code        | 错误编号     |
| severity    | 错误等级     |
| category    | 错误类别     |
| message     | 错误描述     |
| location    | 代码位置     |
| source      | 源码上下文   |
| suggestions | 自动修复建议 |
| help        | 帮助信息     |
| documentationUrl | 错误码文档地址 |
| cliOutput   | CLI格式输出  |

------

# **4.3 CLI Diagnostic Output（新增）**

用于：

- cloudflowc命令行工具
- CI/CD流水线
- 开发者调试
- 服务端日志

例如：

执行：

```bash
cloudflowc compile workflow.flow
```

输出：

```text
ERROR CF1202

workflow.flow:23:15

Unknown keyword "triger"


Did you mean:

trigger


23 | triger:
   | ^^^^^^


Available keywords:

- trigger
- schedule
- manual
- event
```

------

# **4.4 CLI输出格式规范**

统一模板：

```text
{SEVERITY} {ERROR_CODE}


{FILE}:{LINE}:{COLUMN}


{MESSAGE}


{SOURCE_CONTEXT}


{SUGGESTION}


{AVAILABLE_OPTIONS}
```

------

# **五、错误定位系统设计（保持）**

增加：

CLI和JSON共用同一个 Span 数据。

例如：

```rust
struct Span {

    file:String,

    start_line:u32,

    start_column:u32,

    end_line:u32,

    end_column:u32,

    start_offset:u32,

    end_offset:u32

}
```

------

# **十六、前端IDE错误展示设计（修改）**

前端 IDE 不直接解析字符串。

而是使用 JSON Diagnostic。

流程：

```text
CloudFlow DSL

      |
      v

cloudflowc compiler

      |
      v

Diagnostic JSON

      |
      v

Frontend IDE

      |
      |
      +-- 红色波浪线
      |
      +-- Problems窗口
      |
      +-- Hover提示
      |
      +-- Quick Fix
```

------

## **IDE显示效果**

源码：

```cloudflow
workflow {

 triger:

}
```

IDE：

```text
23:15 ERROR CF1202

Unknown keyword "triger"

Did you mean:

trigger
```

同时：



代码区域：

```text
triger:
^^^^^^
```

标红。

------

# **十五、Compiler API设计（补充）**

Compiler API同时返回机器数据和展示数据。

接口：

```http
POST

/api/v1/compile
```

请求：

```json
{
 "filename":
 "workflow.flow",

 "source":
 "..."
}
```

失败：

```json
{

"success":false,


"errors":[


{

"code":"CF1202",

"severity":"ERROR",


"location":{

"line":23,

"column":15

},


"message":

"Unknown keyword \"triger\"",


"cliOutput":

"ERROR CF1202\nworkflow.flow:23:15..."

}


]

}
```

------

# **十七、cloudflowc内部模块调整**

增加：

```text
cloudflowc/


src/


compiler/

    lexer/

    parser/

    ast/

    semantic/

    typecheck/

    ir/


diagnostic/

    error.rs

    code.rs

    span.rs

    diagnostic.rs

    formatter.rs

    cli_formatter.rs

    json_formatter.rs
```

------

# **新增模块说明**

## [**diagnostic.rs**](http://diagnostic.rs)

统一错误模型：

```rust
struct Diagnostic {

    code:String,

    severity:Severity,

    category:String,

    message:String,

    span:Span,

    suggestions:Vec<String>

}
```

------

## [**json_formatter.rs**](http://json_formatter.rs)

负责：

```text
Diagnostic

        |

        v

JSON Response
```

给：

- IDE
- API

------

## [**cli_formatter.rs**](http://cli_formatter.rs)

负责：

```text
Diagnostic

        |

        v

Terminal Output
```

例如生成：

```text
ERROR CF1202

workflow.flow:23:15

Unknown keyword
```

------

# **最终架构**

```text
                 CloudFlow DSL


                       |
                       v


              cloudflowc Compiler


                       |

        +--------------+--------------+

        |                             |

        v                             v


 JSON Diagnostic              CLI Diagnostic


        |                             |

        |                             |

 Web IDE                      Terminal

 VSCode                       CI/CD

 Workflow UI                  DevOps
```

------

这样设计后，`cloudflowc` 的错误体系会接近：

- Rust Compiler Diagnostic
- TypeScript Compiler API
- LLVM Diagnostic
- VS Code Language Server Protocol(LSP)

并且未来如果你做：

- CloudFlow Web IDE
- VS Code插件
- 企业工作流编辑器
- 图形化流程设计器

都可以直接复用这一套 Diagnostic Engine。你不需要为每个平台重新设计错误输出。

---

## V1.2 新语法错误码（CF44xx）

| 错误码 | 类别 | 说明 | 触发 |
|--------|-----|------|------|
| CF4401 | SYNTAX_ERROR | switch 只允许一个 default 分支 | switch 体内出现多个 default |
| CF4402 | RETRY_ERROR | retry_on 引用了未知异常类型 | retry_on [UnknownException] |
| CF4403 | TIMEOUT_ERROR | on_timeout 取值非法 | on_timeout 非 fail/continue/retry |
| CF4404 | DELAY_ERROR | delay 时长必须大于 0 | delay 0s |
| CF4405 | ENVIRONMENT_ERROR | environment 值必须是字面量 | environment { KEY = vars.x } |
| CF4406 | NAMESPACE_ERROR | namespace 不符合小写点分标识符 | namespace Com.Example |
| CF4407 | IMPORT_ERROR | import 别名重复 | 两个 import 使用同一 as 别名 |
| CF4408 | CONTROL_ERROR | break/continue 只能出现在 for/while 循环体内 | 顶层写成 break/continue |
| CF4409 | VALIDATE_ERROR | validate 表达式必须是 boolean | validate { 1 } |
| CF4410 | FOR_ERROR | for range 端点必须是 number | range(0, "x") |
| CF4411 | PARALLEL_ERROR | parallel max_concurrency 必须为正整数 | parallel(max_concurrency=0) |
| CF4412 | VALIDATE_ERROR | validate 校验未通过（运行时） | validate 表达式求值为 false |
| CF4413 | WEBHOOK_ERROR | http 触发 method 非法 | trigger { http { method = "FETCH" } } |
| CF4414 | INTERVAL_ERROR | interval 触发时长必须大于 0 | trigger { interval = 0s } |
| CF4415 | AUDIT_ERROR | audit level 非法 | audit { level = "critical" } |
| CF4416 | NOTIFY_ERROR | notify 渠道非法 | notify { channel = "sms-turbo" } |
| CF4417 | RETURN_ERROR | 步骤级提前返回信号（运行期 SUCCESS） | step 内 return <expr> |
| CF4418 | GROUP_ERROR | step group 冲突 / 空组 | step group g {} 或组名与 step 冲突 |
| CF4419 | WAIT_ERROR | wait 节点携带 timeout 时必须大于 0（10.13，`WaitConfigRule`） | wait approval { timeout = 0s } |
| CF4420 | USE_ERROR | use/with 引用了未声明模块别名 | use missing（未 import as） |
| CF4421 | COND_DEPENDS_ERROR | 条件依赖 must 是布尔表达式 | depends_on a if vars.n + 1 |
| CF4423 | RETRY_ERROR | retry 配置非法：max_attempts 必须为正整数，strategy 仅 `fixed` / `exponential`（10.12，`RetryConfigRule`；与执行引擎退避策略白名单一致） | retry { max_attempts = 0 }、strategy = "cosmic" |
| CF4424 | TIMEOUT_ERROR | 步骤级 / runtime 级 timeout 必须大于 0（10.12，`TimeoutConfigRule`） | step 内 timeout = 0s、runtime { timeout = 0s } |
| CF4425 | METADATA_ERROR | metadata.tags 不能包含空白标签（10.19，`MetadataRule`） | tag "" |

> CF4419/CF4423/CF4424/CF4425 为 2026-08-21 统一语义层规则体系（V1.3-RULE）新增，
> 由 `semantic::builtin_rules()` 注册表在单体检查之后运行；CF4422 预留未用。

## V1.3 语义规则体系新增错误码（2026-08-21 落地）

统一语义层在 V1.3 引入规则插件接口（`SemanticRule` trait + `builtin_rules()` 注册表 +
`validate_with_rules` 可扩展入口，需求 10.27）。规则诊断与既有单体诊断共用 `Diagnostic`
结构；编译管线在 IR 生成前强制经过全部检查（10.29）。

| 错误码 | 类别 | 说明 | 触发示例 |
|--------|-----|------|---------|
| CF2003 | VAR_ERROR | 变量重复声明（AST 级兜底，`DuplicateVariableRule`）。**编译管线中重复变量由解析层先行拦截并报 CF2001**（既有行为，向后兼容）；CF2003 覆盖直接构造/传入 Domain AST 的调用方（IDE、未来前端） | 手工构建 AST 时 `variables` 含两个同名变量 |

所有 CF44xx 诊断仍复用统一 `Diagnostic` 结构（code/severity/category/message/location/source/suggestions/help/cliOutput）。
其中 CF4412 由 Runtime 在求值阶段产生（RuntimeExecutionError::ValidateFailed）；
CF4408 同时作为 Runtime 内部循环控制信号（LoopBreak/LoopContinue）的错误码载体，供恢复审计使用；
CF4417 为步骤级 `return` 的 Runtime 提前返回信号（以 SUCCESS 结束并携带返回输出）。
其余 CF44xx 均在编译期产出。

---

## YAML 前端错误码（CFY-100x，2026-08-20 落地）

YAML 是 CloudFlow 的第二个前端语言（`src/yaml/`）。为与 DSL 错误码（`CFxxxx`）区分，YAML 侧
使用 `CFY` 前缀。诊断仍复用统一 `Diagnostic` 结构（code/severity/category/message/location/
source/suggestions/help/cliOutput），并额外携带 `yaml` 语言标识与 YAML 字段路径、行/列。

| 错误码 | 类别 | 说明 | 触发示例 |
|--------|-----|------|---------|
| CFY-1001 | YAML_PARSE_ERROR | YAML 解析失败（serde_yaml_ng/libyaml，含行/列；不经过 Schema 层） | 非法缩进、语法错误 |
| CFY-1002 | YAML_SCHEMA_ERROR（转换/语义期） | 转换期结构/语义错误，如输入/变量重复声明；**形状校验已移交 `CFY-SCHEMA-*`** | 重复声明 `input.file_id` |
| CFY-1003 | （已移除） | 原“未知/非法字段”由 `CFY-SCHEMA-1003 UNKNOWN_FIELD` / `CFY-SCHEMA-1004 INVALID_VALUE` 取代 | — |
| CFY-SCHEMA-1001 | REQUIRED_FIELD | Workflow Schema：必填字段缺失（需求 31.3） | `steps` 缺失、step 缺 `id`/`action`、`catch` 缺 `action`、`parallel` 缺 `tasks`、`foreach` 缺 `do` |
| CFY-SCHEMA-1002 | TYPE_MISMATCH | Workflow Schema：字段类型不匹配（需求 31.4） | `retry.count` 为字符串、`steps` 非列表、`input` 非对象 |
| CFY-SCHEMA-1003 | UNKNOWN_FIELD | Workflow Schema：未知字段（需求 31.5/31.22，含“是否想使用 X 而不是 Y？”建议） | step 中出现 `foo`、`retry_count` |
| CFY-SCHEMA-1004 | INVALID_VALUE | Workflow Schema：字段值非法（需求 31.6） | `retry.count: -1`、`strategy: bogus`、`trigger.type: bogus`、`action.provider` 白名单外、`timeout` 时长格式错误 |
| CFY-SCHEMA-1005 | YAML_LIMIT_ERROR | YAML 资源护栏：源码超过 1 MiB（`MAX_YAML_SOURCE_BYTES`，与 HTTP 编译请求体上限一致，19.9）；先于 libyaml 执行 | 超过 1 MiB 的 .flow.yaml |
| CFY-SCHEMA-1006 | YAML_LIMIT_ERROR | YAML 资源护栏：嵌套深度超过 100 层（`MAX_YAML_DEPTH`，19.9）；与 libyaml 自身递归限制构成双保险 | 120 层嵌套 mapping/sequence |
| CFY-SCHEMA-1007 | YAML_LIMIT_ERROR | YAML 资源护栏：解析后节点总数超过 10 万（`MAX_YAML_NODES`，19.10）；锚点/别名在事件期展开为完整值树，以节点总数约束别名放大（别名炸弹拦截） | 1 个 1000 元素序列被 200 个别名引用 |
| CFY-EXPR-102 | EXPRESSION_ERROR | 统一表达式子系统解析失败（DSL 与 YAML 共用；span 对齐所在源码）；亦覆盖表达式内求值错误（未知函数、类型不符、索引越界等，运行期包装为变量错误） | `when: ${{ 1 + }}`、DSL 中 `${1 +}` |
| CFY-EXPR-103 | EXPRESSION_ERROR | 表达式资源防线：长度超过 16K 字符（`MAX_EXPRESSION_CHARS`，19.16），先于 pest 解析执行 | 数万个 `+` 拼成的表达式 |
| CFY-EXPR-104 | EXPRESSION_ERROR | 表达式资源防线：嵌套过深——平衡括号最大深度或三元符 `?` 总数超过 512（`MAX_EXPRESSION_NESTING`，19.16）；PEG 递归无内置嵌套上限，此防线拦截 5000 层括号类栈溢出 | `"(" × 5000` + `")" × 5000` |

行为约定：

- **一次收集多个错误**：YAML Compiler 允许部分转换失败时聚合多条诊断，不因首错中断（需求 14.18/31.8）；
  Schema 形状校验（`src/yaml/schema.rs`）作为**编译第一步**（31.2）一次返回全部 `CFY-SCHEMA-*` 错误（31.8），
  转换/语义诊断随后合并返回。
- **位置**：解析/结构/字段错误均带行号与列号；Schema 错误携带 **YAML 字段路径**（如 `steps[2].retry.count`，需求 31.7）；
  `YamlLocator` 按文档序标量文本近似回填位置
  （serde_yaml_ng 反序列化后不保留逐值 span，见 `src/yaml/locator.rs`，需求 7.6/7.7 取舍）。
- **不暴露内部路径**：消息只含 `filename`/`<inline>`/`<stdin>` 与 YAML 字段路径（需求 14.9）。
- 错误输出与 DSL 一致：miette 彩色文本 / `--output-format json`（`diagnostics[]`）、`--no-color`、
  `--explain` 均适用。

---

## 错误码分层分类（编译时 vs 运行时，按层前缀区分）

CloudFlow 的错误码按**所属层 + 前缀**分层，避免不同层借用同一前缀（历史遗留的
CF7xxx/CF81xx 已分别更名为 `CFI-` / `CFD-`，2026-08-21）：

| 前缀 | 层 | 编译时/运行时 | 说明 |
|------|----|--------------|------|
| `CF`（CF1xxx–CF6xxx） | CloudFlow DSL 编译/控制面 | 编译时（CF1/CF2/CF3）+ 生产控制面/持久化（CF4/CF6）与生产执行运行时（CF5 动作调用、CF2xxx 部分执行语义码） | 既有主体；DSL 前端与编译器诊断、生产执行面（`execution.rs` + 数据库任务表 + Capability Agent） |
| `CFY-` | CloudFlow YAML 前端 | 编译时 | YAML 解析/Schema/转换/表达式委托诊断（见上节 CFY-100x / CFY-SCHEMA-* / CFY-EXPR-*） |
| `CFI-` | IR 契约校验层 | 编译时（IR 预校验） | IR 结构/契约校验（`ir_validate::validate_ir_contracts`）；生产 `RuntimeEngine::load`、`/ir-validate` API、微服务与调试入口共用同一校验器（`compiler::validate_ir` 为其文本适配层） |
| `CFD-` | 开发调试执行面（Dev Runner） | 运行时（仅开发调试，纯内存） | dev 执行引擎（`dev_exec.rs`）专属，永不出现于生产执行结果 |
| 生产执行面运行语义码 | 生产执行面 | 运行时 | 生产 `execution.rs` 保留既有 CF2xxx/CF4xxx/CF5xxx 码（如 CF2201 循环超限、CF2202 assert 失败、CF2203 wait 位置、CF4412 validate 失败、CF5001 节点超时） |

### IR 契约校验错误码（CFI-xxxx，2026-08-21 落地）

Dev-Execute 调试入口与生产共用（`crates/cloudflow-engine-core/src/ir_validate.rs`，宿主 crate 根层再导出，详见
[CLOUDFLOW_DEV_EXECUTE.md](./CLOUDFLOW_DEV_EXECUTE.md)）。`validate_ir_contracts` 为
纯函数、一次收集全部问题，每个问题携带 `CFI-xxxx` 码 + JSON 路径 + 节点 ID：

| 错误码 | 说明 |
|--------|------|
| CFI-7001–CFI-7024、CFI-7026–CFI-7028 | IR 契约校验（apiVersion/kind/重复节点 ID、节点类型与必备字段、变量/触发器/运行时配置、表达式结构、`$ref` 可解析性、引用一致性、边引用与环检测） |

### 开发调试执行面错误码（CFD-81xx，2026-08-21 落地）

仅出现于调试入口结果（CLI `dev-execute` / HTTP `/api/dev/execute`）：

| 错误码 | 说明 |
|--------|------|
| CFD-8101 | 变量规范化/表达式求值/引用失败（含 local 变量被启动参数覆盖拒绝） |
| CFD-8102 | 未知节点/类型/动作、缺少必要配置 |
| CFD-8103 | 调度死锁（存在未完成节点但无可调度节点） |
| CFD-8104 | 全局执行超时（`overall_timeout_ms`） |
| CFD-8105 | 工作流进入 WAITING（审批/等待节点） |
| CFD-8106 | 到达断点/单步边界 |
| CFD-8107 | 提前 return（剩余节点跳过，工作流 success） |
| CFD-8108 | 工作流失败：剩余节点跳过 |

边界约定：

- `CFI-xxxx` 仅表达“IR 契约不满足”；任何执行面（生产 `RuntimeEngine::load` 或
  dev 入口）加载该 IR 前都会得到同一批问题（同一校验器）。
- `CFD-81xx` 仅由调试面产生；生产执行入口沿用既有 CF2/CF4/CF5 运行时错误码，
  不产生 CFD-xxxx。
- 节点级业务失败（动作返回错误）两执行面共享同一套码（如 `CF5001` 超时），
  前缀不变，仅“调试面包装”与“生产面落库”两种呈现。

---

## 能力中心错误码（WF-CAPABILITY-*，2026-08-21 落地）

来源：`PrivateCloudDisk-workflow-service` Capability Hub 统一解析/校验/分发管线
（`CapabilityHubService`）。能力调用 `/internal/v1/capabilities/invoke` 的结果统一走
`CapabilityResult` 信封（HTTP 200 + `errorCode/errorSummary/retryable`），不同于控制面接口的 HTTP 状态码。

| 错误码 | HTTP | 阶段 | 说明 | 重试 |
|--------|------|------|------|------|
| `AUTH-UNAUTHENTICATED` | 401 | 服务身份认证 | 内部服务凭证缺失/伪造（`InternalServiceFilter`） | 否 |
| `WF-CAPABILITY-KEY` | 200(信封) | 解析 | 能力键格式非法（白名单正则） | 否 |
| `WF-CAPABILITY-NOT-FOUND` | 200(信封) | 解析 | 能力不存在或已下架 | 否 |
| `WF-CAPABILITY-INPUT` | 200(信封) | Schema 校验 | 输入违反注册表 JSON Schema | 否 |
| `WF-CAPABILITY-FORBIDDEN` | 200(信封) | 权限 | 权限不足（声明∩授权交集 / 实时授权不包含必需权限） | 否 |
| `WF-CAPABILITY-AUTH-UNAVAILABLE` | 200(信封) | 权限 | 权限服务暂不可用 | 是 |
| `WF-CAPABILITY-CIRCUIT-OPEN` | 200(信封) | 分发 | 能力熔断中 | 是 |
| `WF-CAPABILITY-DATAPLANE-UNAVAILABLE` | 200(信封) | 数据面 | 平台/存储不可达或传输错误（幂等重试耗尽） | 是 |
| `WF-CAPABILITY-DATAPLANE-EMPTY` | 200(信封) | 数据面 | 数据面未返回结果 | 否 |
| `WF-CAPABILITY-DATAPLANE-ERROR` | 200(信封) | 数据面 | 数据面业务错误码/消息 | 否 |
| `WF-CAPABILITY-CONTENT-TYPE` | 200(信封) | 内容边界 | 非文本/代码/Markdown 可预览类型 | 否 |
| `WF-CAPABILITY-CONTENT-TOO-LARGE` | 200(信封) | 内容边界 | 文件超过 1MiB | 否 |
| `WF-CAPABILITY-CONTENT-LIMIT` | 200(信封) | 内容边界 | 超过步骤 `max_bytes` 限制 | 否 |
| `WF-CAPABILITY-CONTENT-UNAVAILABLE` | 200(信封) | 内容读取 | 存储读取失败 | 是 |
| `WF-CAPABILITY-IN-PROGRESS` | 200(信封) | 幂等 | 相同能力调用正在处理中 | 是 |
| `WF-CAPABILITY-IDEMPOTENCY-CONFLICT` | 200(信封) | 幂等 | 幂等键已绑定另一能力，禁止跨能力复用 | 否 |
| `WF-CAPABILITY-AGENT-FAILED` | 200(信封) | Agent 面 | 能力执行捕获的运行时异常 | 是 |
| `WF-CAPABILITY-RESULT-CORRUPTED` | 200(信封) | Agent 面 | 历史能力结果无法读取 | 否 |
| `WF-CAPABILITY-SOURCE` / `WF-LOCAL-CLIENT-OFFLINE` | 200(信封) | 分发 | 能力来源不受支持 / 本地插件客户端离线 | 否 |

错误消息脱敏（`token/password/secret=` 打码）并截断（≤1000 字符），不泄露内部路径与凭证。
