# realworld 真实场景测试插件

每个插件目录 = 一个业务场景，采用 `.pcdpkg` 受约束包结构（与设计文档第 7 章
manifest.yaml + src/ 对齐，即发布格式本体）：

| 路径/文件 | 说明 |
| --- | --- |
| `manifest.yaml` | 包清单：plugin(id/name/type/version)、runtime(language/version)、permissions、entrypoints(events → event/module/function/priority/conditions/permissions)、exports(name/input_schema/output_schema)、limits(timeout_seconds/memory_mb) |
| `src/main.py` | 插件源码（必须通过 AST 静态校验白名单、使用 pycloud SDK） |
| `schemas/` | 可选：config.schema.json / capability input/output schema |
| `README.md` | 业务场景与预期输出说明 |
| `input.*` | 开发样例输入（不打入发布包；公共输入在 `testdata/input/`） |

- **manifest 驱动执行**：Runner 从 `manifest.yaml` 读取 entrypoints/exports/limits，
  不再信任请求中外部传入的模块路径/函数名（外部旧式 entrypoints → 拒绝并返回
  `MANIFEST_DRIVEN_REQUIRED`）。
- **打包**：`make test-packages` 或 `scripts/package_test_plugins.sh`
  （产出 `testdata/packages/*.pcdpkg`）。
- **基线**：`make baseline` 或 `scripts/gen_baselines.py`
  （产出 `testdata/expected/*.golden`）。
- 单插件迁移为 .pcdpkg 结构：`scripts/migrate_realworld_pcdpkg.py`（一次性、幂等）。

| 插件 | 场景 | 链路 | 期望输出基线 |
| --- | --- | --- | --- |
| text_stats | 文本统计与 Markdown 摘要 | Execute（修改） | text_stats.golden |
| json_cleaner | JSON 清洗规范化 | Execute（修改） | json_cleaner.golden |
| csv_report | CSV 分析 + Markdown 表格 | Execute（修改） | csv_report.golden |
| excel_generate | 能力网关生成报表 CSV | Execute（修改，call_api） | excel_generate.golden |
| excel_parse | 数据解析 + TXT 报告 | Execute（修改） | excel_parse.golden |
| capability_report | 能力函数导出 generate_report | ExecuteCapability | capability_report.golden |
| capability_user_info | 能力调用 user.info（mock） | ExecuteCapability | capability_user_info.golden |
| timeout_sim | 超时模拟 | Execute（超时） | 无（超时码） |
| resource_hog | 内存耗尽 | Execute（OOM 失败） | 无（失败码） |
| malicious_import | 恶意导入（AST 拒绝） | 不进入执行 | 无（校验拒绝） |
| path_escape | 路径逃逸尝试 | ExecuteCapability（blocked） | path_escape.golden |
| content_reverse | 内容反转修改 | Execute（修改） | content_reverse.golden |
| invalid_output | 输出无效文件 | Execute（失败） | 无（PLUGIN_EXECUTION_FAILED） |
| multi_entry_pkg | 多步骤入口链 | Execute（多入口） | multi_entry_pkg.golden |

红色/异常样本（timeout_sim、resource_hog、malicious_import、path_escape、
invalid_output）只用于验证安全边界与失败语义，绝不产生真实业务副作用。
