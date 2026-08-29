"""CloudFlow 语法高亮转换器集合。

每个转换器实现统一接口（需求 14.15）：`generate(spec: dict, options: dict=None) -> str`，
输入统一规范 `cloudflow.syntax-highlight.json`，输出对应平台的语法文件文本。

新增目标格式（Prism.js / CodeMirror 等，需求 8.13/14.14）时，只需在 converters/
新增一个模块实现同一 `generate(spec)` 接口，并在 `convert.py` 的 FORMATS 表中登记。
"""
