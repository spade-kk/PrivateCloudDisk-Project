# 测试插件夹具（testdata/plugins）

供 `internal/sandbox` 的 Docker 集成测试在运行时打包为 .pcdpkg zip（章节 8）。
所有脚本符合沙箱运行契约：定义 `main(context)` 并返回可 JSON 序列化对象，或通过
`pycloud.write` 写入 `/workspace/work/output.bin`。

| 文件 | 用途 | 对应需求 |
|---|---|---|
| `success.py` | 成功入口，返回结构化 JSON | 5.1/5.2 |
| `write_output.py` | 通过 pycloud.write 写 output.bin（Modified） | 5.7/5.19 |
| `failure.py` | 抛出带宿主路径异常，验证错误脱敏 | 5.5/5.8/6.23 |
| `hostfs.py` | 探测宿主路径可读性与只读/可写挂载 | 5.14/5.18/5.20 |
| `context_probe.py` | 上下文只读校验 | 5.20 |
| `network.py` | 出站网络探测（期望失败） | 5.15 |
| `rootuser.py` | 非 root 身份探测（期望 65532） | 5.17 |
| `capability.py` | 工作流能力入口 | 6.13/6.16 |
| `timeout.py` | 无限睡眠，超时强制终止 | 5.4 |
| `biglog.py` | 日志风暴，验证日志截断 | 5.23 |
| `pids_fork.py` | 子进程风暴，验证 pids-limit | 5.16 |
| `biglog_fail.py` | 日志风暴后抛异常，验证截断后失败路径 | 5.23 |
| `bigresult.py` | 超大结构化输出，result.json 超 1 MiB | 5.11 |
| `capability_empty.py` | 能力入口返回 None，验证空输出规范化 | 6.16 |
| `write_raw_output.py` | 绕过 SDK 直写 output.bin，验证 Modified/CONTENT_FROZEN | 6.11/6.15 |
| `restricted_import_os.py` | 运行时导入 `os`，验证受限 Python 层导入钩子拒绝 | 36.26 |
| `restricted_eval.py` | 运行时调用 `eval`，验证危险内置被删除/改写拒绝 | 36.27 |
| `restricted_dunder.py` | 运行时 `__class__.__bases__[0].__subclasses__()` 逃逸链，验证 AST 改写拦截 | 36.28 |

> 注意：这些样本用于**绕过 AST 静态校验、直测容器边界**的集成测试；真实上传仍必须先通过
> `validator/validate_python.py` 发布门禁。夹具中部分样本（`network.py`/`pids_fork.py`、
> `restricted_*.py` 三类）恰好是静态校验会拒绝的恶意代码，用于验证“沙箱纵深防御、不依赖
> AST 门禁——受限 Python 运行时层仍拦截”的边界。

## realworld：真实场景业务插件

真实业务逻辑（全部符合 AST 白名单、使用 pycloud SDK）在
`testdata/plugins/realworld/`，输入/基线/包分别位于 `testdata/input/`、
`testdata/expected/`、`testdata/packages/`；生成与校验见
`scripts/gen_baselines.py`、`scripts/package_test_plugins.sh` 与
`docs/PLUGIN_RUNTIME_COVERAGE_MATRIX.md`。
