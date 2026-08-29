"""CloudFlow 统一代码补全规范测试套件（需求 15.29）。

覆盖：
- 补全规范 JSON 合法且通过 schema（15.24/15.29）
- 各补全类别齐全：关键字、块、结构模板、内置函数、类型、触发器、片段、错误码、引用前缀
- 每个补全项必备字段（id/label/kind/category/insertText）
- 内置函数白名单与类型、触发器、可重试异常与 grammar/AST 对齐
- VS Code 片段与 Web 分发产物已生成
- AST 节点快照覆盖 V1.2 FlowNode 变体
"""
from __future__ import annotations

import json
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
GEN = os.path.dirname(HERE)
ROOT = os.path.dirname(GEN)
BUILD = os.path.join(ROOT, "build")
VSCODE = os.path.join(ROOT, "vscode")
SCHEMA_DIR = os.path.join(ROOT, "schema")
sys.path.insert(0, GEN)

import completion_builder  # noqa: E402
import config as cfg  # noqa: E402

COMPLETION_PATH = os.path.join(BUILD, "cloudflow.completion.json")
SCHEMA_PATH = os.path.join(SCHEMA_DIR, "cloudflow.completion.schema.json")
VSCODE_SNIPPETS = os.path.join(VSCODE, "snippets", "cloudflow.code-snippets")
VSCODE_COPY = os.path.join(VSCODE, "syntaxes", "cloudflow.completion.json")


def _load(path):
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


class TestCompletionSpecStructure(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = _load(COMPLETION_PATH)

    def test_meta_and_generated_for(self):
        self.assertEqual(self.spec["$meta"]["generatedFor"], "completion")
        self.assertIn("grammarDigest", self.spec["$meta"]["generatedFrom"])
        self.assertIn("astDigest", self.spec["$meta"]["generatedFrom"])

    def test_pass_schema(self):
        errors = completion_builder.validate_spec(self.spec)
        self.assertEqual(errors, [])

    def test_required_top_keys(self):
        for key in ("items", "keywords", "blocks", "structureTemplates", "builtinFunctions",
                    "types", "triggers", "retryExceptions", "referencePrefixes",
                    "errorCodes", "pairs", "snippets", "astNodes"):
            self.assertIn(key, self.spec)

    def test_items_have_required_fields(self):
        for item in self.spec["items"]:
            for field in ("id", "label", "kind", "category", "insertText", "documentation"):
                self.assertIn(field, item, f"item {item.get('id')} 缺少 {field}")

    def test_item_ids_unique(self):
        ids = [item["id"] for item in self.spec["items"]]
        self.assertEqual(len(ids), len(set(ids)), "补全项 id 必须唯一")

    def test_counts_match_items(self):
        counts = self.spec["$meta"]["counts"]
        self.assertEqual(counts["keywords"], len(self.spec["keywords"]))
        self.assertEqual(counts["blocks"], len(self.spec["blocks"]))
        self.assertEqual(counts["structures"], len(self.spec["structureTemplates"]))
        self.assertEqual(counts["triggers"], len(self.spec["triggers"]["items"]))


class TestCompletionCategories(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = _load(COMPLETION_PATH)

    def test_builtin_functions_cover_whitelist(self):
        names = [f["name"] for f in self.spec["builtinFunctions"]]
        for fn in ("size", "len", "contains", "starts_with", "ends_with"):
            self.assertIn(fn, names, f"内置函数 {fn} 缺失")

    def test_types_cover_supported(self):
        labels = [t["label"] for t in self.spec["types"]]
        for ty in ("string", "number", "boolean", "array", "object", "file", "user", "space"):
            self.assertIn(ty, labels)

    def test_triggers_cover_all_types(self):
        types = self.spec["triggers"]["types"]
        for trig in ("manual", "schedule", "event", "http", "interval", "webhook"):
            self.assertIn(trig, types, f"触发器 {trig} 缺失")

    def test_retry_exceptions(self):
        for exc in ("TimeoutException", "NetworkException", "PluginException"):
            self.assertIn(exc, self.spec["retryExceptions"])

    def test_pairs_cover_brackets_and_quotes(self):
        pairs = self.spec["pairs"]
        self.assertIn("brackets", pairs)
        self.assertIn("autoClosingPairs", pairs)
        self.assertIn("indentation", pairs)
        self.assertIn("comment", pairs)

    def test_error_codes_non_empty(self):
        self.assertGreater(len(self.spec["errorCodes"]), 10)
        for code in ("CF1201", "CF1202", "CF2002", "CF4402", "CF4408"):
            self.assertIn(code, self.spec["errorCodes"])

    def test_reference_prefixes(self):
        prefixes = self.spec["referencePrefixes"]
        for pre in ("vars", "steps", "workflow"):
            self.assertIn(pre, prefixes)
            self.assertTrue(prefixes[pre]["prefix"].endswith("."))


class TestStructureTemplates(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = _load(COMPLETION_PATH)
        cls.labels = {t["label"] for t in cls.spec["structureTemplates"]}

    def test_control_flow_templates_present(self):
        for key in ("if", "foreach", "for", "while", "parallel", "try_catch_finally",
                    "wait", "assert", "switch", "delay", "notify", "validate", "return",
                    "break", "continue", "step_group"):
            self.assertIn(key, self.labels, f"结构模板 {key} 缺失")

    def test_v12_keywords_in_templates(self):
        self.assertIn("switch", " ".join(self.labels))
        self.assertIn("retry_on", " ".join(self.labels))
        self.assertIn("depends_on", " ".join(self.labels))
        self.assertIn("timeout", " ".join(self.labels))


class TestAstSnapshot(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = _load(COMPLETION_PATH)

    def test_flow_node_variants_cover_v12(self):
        variants = set(self.spec["astNodes"]["flowNodeVariants"])
        for v in ("Switch", "For", "While", "Delay", "Notify", "Validate",
                  "Return", "Break", "Continue", "StepGroup"):
            self.assertIn(v, variants, f"AST FlowNode 变体 {v} 缺失")


class TestGeneratedArtifacts(unittest.TestCase):
    def test_vscode_snippets_generated(self):
        self.assertTrue(os.path.isfile(VSCODE_SNIPPETS))
        data = _load(VSCODE_SNIPPETS)
        self.assertIn("Workflow", data)
        self.assertIn("struct_struct_switch", data)

    def test_vscode_completion_copy(self):
        self.assertTrue(os.path.isfile(VSCODE_COPY))
        self.assertEqual(_load(VSCODE_COPY)["$meta"]["generatedFor"], "completion")


class TestCompletionBuilderApi(unittest.TestCase):
    def test_build_completions_returns_spec(self):
        grammar_src, ast_src = completion_builder._load_sources()
        spec = completion_builder.build_completions(grammar_src, ast_src)
        self.assertEqual(spec["$meta"]["generatedFor"], "completion")
        self.assertGreater(len(spec["items"]), 80)

    def test_config_tables_non_empty(self):
        self.assertGreater(len(cfg.BUILTIN_FUNCTIONS), 0)
        self.assertGreater(len(cfg.RETRY_EXCEPTIONS), 0)
        self.assertGreater(len(cfg.TRIGGER_TYPES), 0)
        self.assertGreater(len(cfg.SNIPPETS), 0)
        self.assertGreater(len(cfg.STRUCTURE_TEMPLATES), 0)


if __name__ == "__main__":
    unittest.main()
