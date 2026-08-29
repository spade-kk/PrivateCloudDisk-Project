> Cloudflow DSL 做高级语言
>
> Cloudflow YAML 做低门槛配置语言
>
> 这是最合理的架构。

> **实现状态（2026-08-20，落地回写）**：YAML 与 DSL 共享的 **CloudFlow 表达式子系统**
> （`expr.cloudflow.io/v1`）已实现，位于 `crates/cloudflow-engine-core/src/expression/`
> （规格 `docs/CLOUDFLOW_EXPRESSION.md`）。DSL 前端已改为把 `${...}` 表达式字符串委托子系统解析，
> YAML 前端统一用 `${{ ... }}` 分隔符（只切字符串、不实现表达式），
> 表达式词法/解析/AST 构建唯一收敛在子系统。YAML 前端落地时直接复用
> `parse_expression_string` / `parse_value_string`，本示例中所有 `condition/if` 表达式字段均按
> “字符串 → 表达式子系统”处理。
>
> **YAML 前端已落地实现**（2026-08-20，0.1.7 起含 Schema 校验层）：本清单示例已整理为
> **14 个可编译 `.flow.yaml`**（如 `examples/yaml/simple_file_process.flow.yaml`），其中示例 21 `weekly_sales_report.flow.yaml` 由旧版
> `automation.pcd/v1` 一次性转化而来（旧版不再解析）；全部通过 YAML→Domain AST→语义→IR 全流程编译，
> 表达式统一使用 `${{ ... }}` 分隔符；示例与文件映射见文末「实现状态（YAML 示例落库回写）」一节。
>

类似：
```
Java
 |
 JVM bytecode
Python
 |
 Python AST
Cloudflow DSL
 |
 Workflow IR
Cloudflow YAML
 |
 Workflow IR
```
你的方向实际上已经接近一个真正工作流引擎的语言设计方式了。下面我给你生成一组 CloudFor YAML 工作流配置语言示例。

设计目标：

* 保留 YAML 简洁优势（类似 GitHub Actions / Docker Compose）
* 不引入复杂 DSL 语法
* 覆盖 Cloudflow YAML 核心能力：
    * workflow 定义
    * 参数输入
    * 环境变量
    * steps
    * action 调用
    * step 依赖
    * 条件判断
    * 表达式
    * 输出变量
    * 数据传递
    * 并行执行
    * 循环
    * 重试
    * 超时
    * 异常捕获
    * 人工审批
    * 子工作流
    * 插件调用
    * 事件触发
    * 定时任务

⸻

示例1：简单文件处理工作流

场景：

上传文件 → 判断类型 → 解析 → AI分析 → 保存结果
```yaml
workflow:
  name: file_process
  version: 1
trigger:
  event: file.upload
input:
  file_id: string
steps:
  - id: get_file
    action: file.get
    input:
      id: ${input.file_id}
  - id: parser
    action: file.parse
    when: ${file.type == "pdf"}
    input:
      file: ${steps.get_file.output}
  - id: ai_summary
    action: ai.summary
    input:
      content: ${steps.parser.output}
  - id: save
    action: metadata.update
    input:
      summary: ${steps.ai_summary.output}
```
⸻

示例2：条件分支

场景：

大文件走大文件处理，小文件普通处理。
```yaml
workflow:
  name: file_route
input:
  size: number
steps:
  - id: large_file
    action: file.large_process
    when: ${input.size > 100MB}
  - id: normal_file
    action: file.normal_process
    when: ${input.size <= 100MB}
```
等价：
```yaml
if size >100MB:
    large_process
else:
    normal_process
```
⸻

示例3：多分支判断

场景：

不同文件类型调用不同解析器。
```yaml
workflow:
  name: document_parser
input:
  type: string
steps:
  - id: pdf
    action: parser.pdf
    when: ${input.type == "pdf"}
  - id: word
    action: parser.word
    when: ${input.type == "word"}
  - id: excel
    action: parser.excel
    when: ${input.type == "excel"}
  - id: default
    action: parser.default
    when: ${else}
```
⸻

示例4：并行任务

场景：

OCR、AI摘要、标签提取同时执行。
```yaml
workflow:
  name: ai_pipeline
steps:
  - id: analyze
    parallel:
      - id: ocr
        action: ai.ocr
      - id: summary
        action: ai.summary
      - id: tag
        action: ai.tag
  - id: save
    action: metadata.save
    depends:
      - analyze
```
执行：
```
        ┌── OCR
start ───┼── Summary
        └── Tag
          |
          v
        Save
```
⸻

示例5：并行失败处理

场景：

三个任务全部成功才保存。

任意失败进入异常。
```yaml
workflow:
  name: ai_process
steps:
  - id: ai_tasks
    parallel:
      fail: stop
      tasks:
        - id: ocr
          action: ai.ocr
        - id: summary
          action: ai.summary
        - id: tag
          action: ai.tag
  - id: save
    action: metadata.save
    depends:
      - ai_tasks
catch:
  - error: "*"
    action: notify.admin
```
⸻

示例6：自动重试

场景：

AI服务失败自动重试。
```yaml
workflow:
  name: retry_demo
steps:
  - id: ai_call
    action: ai.chat
    retry:
      count: 3
      interval: 5s
  - id: save
    action: database.save
```
执行：

AI失败
第一次
↓
等待5秒
↓
第二次
↓
第三次
↓
失败进入异常

⸻
```yaml
示例7：完整异常处理

workflow:
  name: exception_demo
steps:
  - id: upload
    action: file.upload
    retry:
      count:3
catch:
  - error:
      type: Timeout
    action:
      notify:
        user: true
  - error:
      type: "*"
    action:
      log.write
```
⸻

示例8：变量系统

类似编程语言变量。
```yaml
workflow:
  name: variable_demo
variables:
  bucket: user-file
  region: cn
steps:
  - id: upload
    action: storage.put
    input:
      bucket: ${vars.bucket}
      region: ${vars.region}
```
⸻

示例9：Step输出传递

类似 GitHub Action output。
```yaml
workflow:
  name: output_demo
steps:
  - id: download
    action: file.download
    output:
      path: file_path
  - id: parse
    action: file.parse
    input:
      file: ${steps.download.file_path}
```
⸻

示例10：循环

场景：

批量处理文件。
```yaml
workflow:
  name: batch_process
input:
  files:
    type:list
steps:
  - id: process
    foreach:
      item: ${input.files}
    do:
      action: file.process
      input:
        file: ${item}
```
⸻

示例11：循环 + 条件
```yaml
workflow:
  name: batch_ai
steps:
  - id: files
    action: file.list
  - id: process
    foreach:
      item:
        ${steps.files.output}
    do:
      action: ai.summary
      when:
        ${item.size > 1MB}
```
⸻

示例12：定时任务

类似 Cron。
```yaml
workflow:
  name: daily_report
trigger:
  cron:
    expression: "0 2 * * *"
steps:
  - id: report
    action: report.generate
  - id: send
    action: email.send
    depends:
      - report
```
⸻

示例13：Webhook触发
```yaml
workflow:
  name:webhook_demo
trigger:
  webhook:
    path:/deploy
input:
  branch:string
steps:
  - id: deploy
    action:
      kubernetes.deploy
    input:
      branch:${input.branch}
```
⸻

示例14：审批节点

企业工作流常用。
```yaml
workflow:
  name:
    release
steps:
  - id:
      build
    action:
      ci.build
  - id:
      approve
    approval:
      users:
        - admin
  - id:
      deploy
    action:
      kubernetes.deploy
    depends:
      - approve
```
⸻

示例15：子工作流

类似函数调用。
```yaml
workflow:
  name:
    release
steps:
  - id:
      build
    workflow:
      java.build
  - id:
      deploy
    workflow:
      k8s.deploy
```
⸻

示例16：完整企业级文件AI处理

覆盖：

* 文件上传
* 类型判断
* 并行
* AI
* 重试
* 异常
* 保存
```yaml
workflow:
  name:
    document_ai
trigger:
  event:
    file.upload
input:
  file_id:string
steps:
  - id:
      file
    action:
      file.get
    input:
      id:
        ${input.file_id}
  - id:
      parser
    switch:
      ${file.type}
    cases:
      pdf:
        action:
          parser.pdf
      word:
        action:
          parser.word
      default:
        action:
          parser.common
  - id:
      ai
    parallel:
      - id:
          ocr
        action:
          ai.ocr
      - id:
          summary
        action:
          ai.summary
      - id:
          tag
        action:
          ai.tag
    retry:
      count:
        3
  - id:
      save
    action:
      metadata.save
    input:
      data:
        ${steps.ai.output}
catch:
  - error:
      "*"
    action:
      notify.admin
```
⸻

示例17：类似 GitHub Action 的 CI/CD
```yaml
workflow:
  name:
    build_release
trigger:
  push:
    branch:
      main
env:
  JAVA_VERSION:
    21
steps:
  - id:
      checkout
    action:
      git.checkout
  - id:
      build
    action:
      java.build
    env:
      version:
        ${env.JAVA_VERSION}
  - id:
      test
    action:
      test.run
  - id:
      docker
    action:
      docker.build
  - id:
      deploy
    action:
      k8s.deploy
    depends:
      - docker
```
⸻

示例18：消息驱动工作流

适合你的云平台。
```yaml
workflow:
  name:
    file_event
trigger:
  event:
    file.created
steps:
  - id:
      security_scan
    action:
      security.scan
  - id:
      preview
    action:
      file.preview
  - id:
      index
    action:
      search.index
    depends:
      - preview
```
⸻

CloudFor YAML 核心结构总结

最终建议固定为：
```
workflow:
  name:
  version:
trigger:
input:
variables:
env:
steps:
outputs:
catch:
finally:

Step：

- id:
  action:
  input:
  output:
  when:
  depends:
  retry:
  timeout:
  parallel:
  foreach:
```
⸻

这个设计实际上已经接近：

* GitHub Actions
* Argo Workflow
* Temporal Workflow
* AWS Step Functions

的融合版。

区别是：

GitHub Actions：

YAML + 少量表达式 + CI/CD

CloudFor：

YAML + Workflow DSL + 企业自动化执行模型

所以 YAML 负责：

描述工作流结构

DSL负责：

表达复杂业务逻辑

最终：
```
CloudFor YAML
        |
        v
YAML Parser
        |
        v
Workflow AST
        |
        v
IR
        |
        v
Workflow Engine
```
这就是比较合理的云原生工作流平台设计。
⸻

## 实现状态（YAML 示例落库回写，2026-08-20）

> 本清单的 18 个设计示例已整理实现为 **14 个可编译 `.flow.yaml`**（部分特性合并到同一文件），存于
> `PrivateCloudDisk-cloudflow-runtime/examples/yaml/`（含模板 `template.flow.yaml`）；示例 21 `weekly_sales_report.flow.yaml` 由旧版
> `automation.pcd/v1` 一次性转化（旧版不再解析）。每个文件均通过
> `cloudflowc compile`（YAML→**Schema 校验**→Domain AST→统一语义→IR）全流程验证，并纳入
> `tests/cloudflow_yaml.rs::all_yaml_examples_compile` 与 `dsl_and_yaml_compile_to_equivalent_ir`
> 回归（需求 29.x：示例即测试资产；模板与错误示例同时是 Schema 层的测试资产）。

| DEMO 示例 | 场景/覆盖特性 | 落地文件（`examples/yaml/`） |
| --- | --- | --- |
| 1 | 简单文件处理：event 触发、input、steps、depends、when、output | `simple_file_process.flow.yaml` |
| 2 | 条件分支路由（文件大小 → 大/小处理） | `condition_route.flow.yaml` |
| 3 | 多分支判断 switch/cases/default | `switch_document_parser.flow.yaml` |
| 4 | 并行任务（OCR/摘要/标签） | `parallel_retry.flow.yaml` |
| 5 | 并行失败处理（fail+catch） | `exception_handling.flow.yaml` |
| 6 | 自动重试（3 次、间隔 5s） | `parallel_retry.flow.yaml` |
| 7 | 完整异常处理（重试+catch+finally） | `exception_handling.flow.yaml` |
| 8 | 变量系统（inputs/variables 声明） | `nested_workflow_form.flow.yaml`、`foreach_loop.flow.yaml` |
| 9 | Step 输出传递（steps.<id>.output） | `simple_file_process.flow.yaml` |
| 10 | 循环 foreach | `foreach_loop.flow.yaml` |
| 11 | 循环+条件过滤 | `foreach_loop.flow.yaml` |
| 12 | 定时任务（cron / interval） | `schedule_triggers.flow.yaml`、`interval_trigger.flow.yaml` |
| 13 | Webhook 触发 | `webhook_deploy.flow.yaml` |
| 14 | 审批节点（approval→approval.request） | `approval_release.flow.yaml` |
| 15 | 子工作流/动作调用（java.build、k8s.deploy） | `webhook_deploy.flow.yaml` |
| 16 | 完整企业级文件 AI 处理（组合特性） | `acme_advanced_file_ai.flow.yaml` |
| 17 | 类 GitHub Actions CI/CD（env、依赖长链） | `webhook_deploy.flow.yaml` |
| 18 | 消息驱动工作流（event + 顺序依赖） | `simple_file_process.flow.yaml`、`acme_advanced_file_ai.flow.yaml` |
| 21 | 销售周报（自旧版 automation.pcd/v1 一次性转化）：schedule / plugin:…:generate_report@1 / `${{ }}` / runtime / retry / 各步骤 name / 失败通知 | `weekly_sales_report.flow.yaml` |

说明：

- **未单列文件的示例**：示例 15 的子工作流调用在 YAML 中表达为普通 `action: java.build` /
  `action: k8s.deploy` 步骤（`webhook_deploy.flow.yaml` 同构体现）；若能力中心注册 `workflow.call`
  类能力，用同一 `action` 结构即可（需求 15.15：子工作流节点——当前以 action 形式落地，未引入
  独立 `subworkflow` 关键字）。
- **表达式**：上述所有 `when/if/switch/foreach` 条件均为 `${{ ... }}` 字符串，仅由表达式子系统解析
  （需求 6.31/28.56/6.32），CloudFlow YAML 只定义 `${{ }}` 一种分隔符，YAML 示例不重复实现表达式能力；
  `weekly_sales_report.flow.yaml` 与 DSL 同义文件 `examples/weekly_sales_report.flow` 语义等价。
- **错误变体与反例**（需求 29.25）：`tests/cloudflow_yaml.rs` 提供 `CFY-1001/1002/1003` 反例用例
  （非法 YAML、结构缺失、未知字段），非独立文件。
