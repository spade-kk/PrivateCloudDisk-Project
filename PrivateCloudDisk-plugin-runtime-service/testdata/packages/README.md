# testdata/packages 插件包（.pcdpkg）

由 `scripts/package_test_plugins.sh` 生成（需求三 3.7-3.12 / 六 6.10）。命名规范
`{plugin_id}_{version}.pcdpkg`，其中 `version` 取自 `manifest.yaml` 的 `plugin.version`。

包内为**受约束 ZIP（.pcdpkg）**，结构与 `internal/package` 校验对齐（设计文档第 7 章）：

```text
{plugin_id}_{version}.pcdpkg
├── manifest.yaml          # 必需：入口/权限/limits 声明（manifest 驱动执行的唯一依据）
├── src/                   # 必需：插件源码（entrypoints.module/exports 的相对路径）
├── schemas/               # 可选：config.schema.json / capability input/output schema
├── assets/                # 可选
└── README.md
```

打包时排除 `input.*` 开发样例、点文件与 `__pycache__`/`*.pyc`，保证包内无缓存字节码、
**重打包 sha256 稳定**；解压后文件由 `internal/package.Parse` 强制只读（文件 0400 /
目录 0500），并校验路径穿越/符号链接/硬链接/设备文件、文件数≤1000、解压后≤20 MiB、
单脚本大小/行数限制与敏感文件过滤。

| 包 | 场景 | 说明 |
| --- | --- | --- |
| text_stats_1.0.0.pcdpkg | 文本统计 | 合规：output-bin |
| json_cleaner_1.0.0.pcdpkg | JSON 清洗 | 合规：output-bin |
| csv_report_1.0.0.pcdpkg | CSV 分析 | 合规：output-bin |
| excel_generate_1.0.0.pcdpkg | 报表生成 | 合规：call_api mock |
| excel_parse_1.0.0.pcdpkg | 数据解析 | 合规：output-bin |
| capability_report_1.0.0.pcdpkg | 能力导出 | 合规：ExecuteCapability |
| capability_user_info_1.0.0.pcdpkg | 用户信息 | 合规：ExecuteCapability |
| timeout_sim_1.0.0.pcdpkg | 超时 | 异常样本 |
| resource_hog_1.0.0.pcdpkg | 内存耗尽 | 异常样本 |
| invalid_output_1.0.0.pcdpkg | 输出无效 | 异常样本 |
| path_escape_1.0.0.pcdpkg | 路径逃逸 | 合规（网关拒绝） |
| content_reverse_1.0.0.pcdpkg | 内容反转 | 合规：output-bin |
| multi_entry_pkg_1.0.0.pcdpkg | 多入口链 | 合规：chain |

红色样本 `malicious_import` 默认不打包（其源码无法通过 AST 门禁）；安全测试需
`scripts/package_test_plugins.sh --all`。

- 重新打包：`make test-packages` 或 `sh scripts/package_test_plugins.sh`
- 完整性：包均版本化提交，可用 `shasum -a 256 testdata/packages/*.pcdpkg` 复核
