下面给你设计一份 **CloudFlow DSL Compiler 错误诊断系统设计规范文档**。



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
ERROR CF1002

workflow.cloud:23:15

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
CF1001
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

ERROR CF1005
invalid syntax
```

------

# **四、错误信息标准结构**

所有错误统一格式：

```json
{
  "code": "CF1001",

  "severity": "ERROR",

  "category": "SYNTAX_ERROR",

  "message":
  "Unexpected token",

  "location": {

    "file":
    "weekly_report.cf",

    "line":23,

    "column":15,

    "offset":532

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
  "Trigger defines workflow execution source"

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
workflow/report.cf
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
needs:

- generate_report
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

```
A needs B

B needs C

C needs A
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
${{ vars.xxx }}
```

错误：

```cloudflow
${{ vars.user_id }}
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
uses:

plugin:x:create_report
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

/api/cloudflow/compiler/check
```

Request:

```json
{

"source":

"workflow xxx",

"filename":

"report.cf"

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

weekly_report.cf:32:8


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
  "code": "CF1002",

  "severity": "ERROR",

  "category": "SYNTAX_ERROR",

  "message":
  "Unknown keyword \"triger\"",


  "location": {

    "file":
    "workflow.cloud",

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


  "cliOutput":

  "ERROR CF1002\n\nworkflow.cloud:23:15\n\nUnknown keyword \"triger\"\n\nDid you mean:\ntrigger\n\n23 | triger:\n   | ^^^^^^\n\nAvailable keywords:\n- trigger\n- schedule\n- manual\n- event"

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
cloudflowc compile workflow.cloud
```

输出：

```text
ERROR CF1002

workflow.cloud:23:15

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
23:15 ERROR CF1002

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

/api/cloudflow/compiler/check
```

请求：

```json
{
 "filename":
 "workflow.cloud",

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

"code":"CF1002",

"severity":"ERROR",


"location":{

"line":23,

"column":15

},


"message":

"Unknown keyword \"triger\"",


"cliOutput":

"ERROR CF1002\nworkflow.cloud:23:15..."

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
ERROR CF1002

workflow.cloud:23:15

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