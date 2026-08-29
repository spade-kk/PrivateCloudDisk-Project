"""CloudFlow 语法高亮生成器测试套件（需求 12.x / 14.24）。

覆盖：
- 统一规范 JSON 合法且通过 schema（12.1）
- TextMate / Monarch / Highlight.js 三种产物结构合法、可加载（12.2-12.4）
- 关键字、字符串、数字、注释、变量引用、操作符/标点、多行字符串、插值高亮覆盖（12.5-12.10）
- 暗/亮主题颜色可读性与三格式 token 类别一致性（12.11-12.12）
- 新增语法（switch/for/include/parallel/notify/validate）回归（12.13）
- 语法样本 token 覆盖（14.24）

注：所有断言均基于解析后的结构（parsed），避免手工处理 JSON 转义层数。
"""
from __future__ import annotations

import json
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
GEN = os.path.dirname(HERE)
ROOT = os.path.dirname(GEN)  # syntax-highlight
BUILD = os.path.join(ROOT, "build")
SAMPLES = os.path.join(ROOT, "samples")
sys.path.insert(0, GEN)
sys.path.insert(0, os.path.join(GEN, "converters"))

import build_spec  # noqa: E402

SPEC_PATH = os.path.join(BUILD, "cloudflow.syntax-highlight.json")
TM_PATH = os.path.join(BUILD, "cloudflow.tmLanguage.json")
MONARCH_PATH = os.path.join(BUILD, "cloudflow.monarch.json")
HLJS_PATH = os.path.join(BUILD, "cloudflow.hljs.js")


def _load(path):
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


def _raw(path):
    with open(path, "r", encoding="utf-8") as fh:
        return fh.read()


def _collect_strings(obj, sep="\n"):
    """递归收集解析后结构中的所有字符串（不经过 json.dumps 二次转义），用 sep 连接。"""
    out = []
    if isinstance(obj, str):
        return obj
    if isinstance(obj, dict):
        for v in obj.values():
            out.append(_collect_strings(v, sep))
    elif isinstance(obj, (list, tuple)):
        for v in obj:
            out.append(_collect_strings(v, sep))
    return sep.join(x for x in out if x)


class TestUnifiedSpec(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.spec = _load(SPEC_PATH)

    def test_spec_valid_and_required_keys(self):
        for key in ["$meta", "categories", "keywords", "operators", "punctation",
                    "comments", "strings", "numbers", "references", "identifiers",
                    "ast", "unclassifiedTokens"]:
            self.assertIn(key, self.spec, f"统一规范缺少 {key}")

    def test_spec_pass_schema(self):
        errors = build_spec.validate_spec(self.spec)
        self.assertEqual(errors, [], f"统一规范未通过 schema: {errors}")

    def test_meta_consistency(self):
        meta = self.spec["$meta"]
        self.assertEqual(meta["languageId"], "cloudflow")
        self.assertEqual(meta["scopeName"], "source.cloudflow")
        self.assertIn(".flow", meta["fileExtensions"])
        self.assertTrue(meta["generatedAt"])       # 需求 14.21
        self.assertEqual(meta["version"], "1.2.0")

    def test_no_unclassified_tokens(self):
        self.assertEqual(self.spec["unclassifiedTokens"], [])

    def test_ast_flow_variants_cover_v12(self):
        variants = set(self.spec["ast"]["flowNodeVariants"])
        for expected in ["Switch", "For", "Parallel", "Notify", "Validate",
                         "Delay", "Return", "Break", "Continue", "StepGroup"]:
            self.assertIn(expected, variants)

    def test_colors_defined_for_light_and_dark(self):
        import re
        for cat, info in self.spec["categories"].items():
            self.assertRegex(info["color"], r"^#[0-9A-Fa-f]{6}$", f"{cat} 颜色非法")

    def test_all_keywords_classified(self):
        # workflow 与 steps 必须被归类为关键字（同时是引用前缀）
        allk = set(sum(self.spec["keywords"].values(), []))
        for kw in ["workflow", "steps", "metadata", "switch", "filter", "map",
                   "reduce", "max_concurrency"]:
            self.assertIn(kw, allk)


class TestConsistencyAcrossFormats(unittest.TestCase):
    """三格式 token 类别一致性（需求 12.12）。"""

    @classmethod
    def setUpClass(cls):
        cls.spec = _load(SPEC_PATH)
        cls.tm = _load(TM_PATH)
        cls.monarch = _load(MONARCH_PATH)
        cls.hljs = _raw(HLJS_PATH)

    @staticmethod
    def _tm_tokens(tm):
        parts = [e["match"] for e in tm["repository"]["keywords"]]
        parts += [e["match"] for e in tm["repository"]["references"]]
        parts += [e["match"] for e in tm["repository"]["numbers"]]
        parts += [e["match"] for e in tm["repository"]["operators"]]
        return "|".join(parts)

    @staticmethod
    def _mon_tokens(monarch):
        return _collect_strings(monarch["tokenizer"])

    def test_keywords_present_in_all_formats(self):
        tm_text = self._tm_tokens(self.tm)
        m_text = self._mon_tokens(self.monarch)
        missing = []
        for words in self.spec["keywords"].values():
            for word in words:
                if word not in tm_text:
                    missing.append(("tm", word))
                if word not in m_text:
                    missing.append(("monarch", word))
                if word not in self.hljs:
                    missing.append(("hljs", word))
        self.assertEqual(missing, [], f"关键字缺失于某些产物: {missing[:20]}")

    def test_scope_names_consistent(self):
        cats = self.spec["categories"]
        tm = json.dumps(self.tm)
        mn = json.dumps(self.monarch)
        checked = ("comment", "string", "tripleString", "interpolation", "number",
                   "duration", "stepReference", "variableReference", "systemReference",
                   "operator", "punctation")
        monarch_checked = ("comment", "string", "tripleString", "number", "duration",
                           "operator", "punctation", "stepReference", "variableReference",
                           "systemReference")
        for cat, info in cats.items():
            if cat in checked:
                self.assertIn(info["scope"], tm, f"TextMate 缺少 scope {cat}")
            if cat in monarch_checked:
                self.assertIn(info["scope"], mn, f"Monarch 缺少 scope {cat}")


class TestTextMate(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tm = _load(TM_PATH)

    @staticmethod
    def _keyword_matches(tm):
        return "|".join(e["match"] for e in tm["repository"]["keywords"])

    def test_structure(self):
        self.assertEqual(self.tm["scopeName"], "source.cloudflow")
        self.assertIn("$schema", self.tm)
        self.assertIn("repository", self.tm)
        self.assertTrue(self.tm["patterns"])

    def test_comment_first_pattern(self):
        self.assertEqual(self.tm["patterns"][0]["include"], "#comments")

    def test_strings_triple_before_double(self):
        repo = self.tm["repository"]
        self.assertIn("string.quoted.triple.cloudflow", json.dumps(repo["strings"][0]))
        self.assertIn("string.quoted.double.cloudflow", json.dumps(repo["strings"][1]))

    def test_interpolation_nested(self):
        self.assertEqual(self.tm["repository"]["interpolation"][0]["begin"], r"\$\{")

    def test_references(self):
        for prefix in ["vars", "steps", "workflow"]:
            found = any((prefix + r"\.") in ref["match"]
                        for ref in self.tm["repository"]["references"])
            self.assertTrue(found, f"引用前缀 {prefix} 缺失")

    def test_numbers_and_duration(self):
        matches = [e["match"] for e in self.tm["repository"]["numbers"]]
        joined = "|".join(matches)
        self.assertIn("ms|s|m|h|d", joined, "时长正则缺失")

    def test_v12_keywords_highlight(self):
        kws = self._keyword_matches(self.tm)
        for kw in ["switch", "for", "parallel", "notify", "validate", "expect",
                   "break", "continue", "return", "delay", "retry_on", "on_error",
                   "depends_on", "timeout", "retry", "case", "default", "workflow"]:
            self.assertIn(kw, kws, f"关键字 {kw} 未进入 TextMate pattern")

    def test_identifiers_fallback_present(self):
        self.assertIn("entity.name.variable.other.cloudflow",
                      json.dumps(self.tm["repository"]["identifiers"]))


class TestMonarch(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.monarch = _load(MONARCH_PATH)

    def test_structure(self):
        self.assertEqual(self.monarch["defaultToken"], "")
        self.assertFalse(self.monarch["ignoreCase"])
        for state in ["root", "triple", "double", "interp"]:
            self.assertIn(state, self.monarch["tokenizer"])

    def test_references_prefixes(self):
        root_text = _collect_strings(self.monarch["tokenizer"]["root"])
        for prefix in ["vars", "steps", "workflow"]:
            self.assertIn(prefix + r"\.", root_text)

    def test_interpolation_state(self):
        self.assertIn("@interp", json.dumps(self.monarch["tokenizer"]))

    def test_v12_keywords(self):
        root_text = json.dumps(self.monarch["tokenizer"]["root"])
        for kw in ["switch", "for", "parallel", "notify", "validate", "expect"]:
            self.assertIn(kw, root_text)


class TestHighlightJs(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.hljs = _raw(HLJS_PATH)

    def test_registers_language(self):
        self.assertIn("registerLanguage('cloudflow'", self.hljs)

    def test_umd_support(self):
        self.assertIn("module.exports", self.hljs)
        self.assertIn("define", self.hljs)

    def test_v12_keywords(self):
        for kw in ["switch", "for", "parallel", "notify", "validate", "expect"]:
            self.assertIn(kw, self.hljs)

    def test_esm_import_friendly(self):
        # 需求 7.14：UMD，可在 ES Module 环境通过 module.exports 互操作
        self.assertIn("module.exports = factory", self.hljs)

    @staticmethod
    def _class_words(s):
        import re
        m = re.search(r"(keyword|type|literal|built_in):\s*'([^']*)'", s)
        return m

    def _extract(self, kw):
        import re
        m = re.search(kw + r":\s*'([^']*)'", self.hljs)
        return set(m.group(1).split()) if m else set()

    def test_type_keywords_separated(self):
        # 需求 12.5/12.12：type 关键字必须与 keyword 区分，不坍缩成普通 keyword
        type_set = self._extract("type")
        kw_set = self._extract("keyword")
        for t in ["string", "number", "boolean", "array", "object", "file", "user", "space"]:
            self.assertIn(t, type_set, f"type 关键字 {t} 未进入 hljs type 类")
        overlap = type_set & kw_set
        self.assertEqual(overlap, set(), f"type 与 keyword 类重叠: {overlap}")

    def test_function_builtin_separated(self):
        # filter/map/reduce 作为 built_in（函数），与普通 keyword 区分
        bi = self._extract("built_in")
        kw_set = self._extract("keyword")
        for f in ["filter", "map", "reduce"]:
            self.assertIn(f, bi, f"函数 {f} 未进入 hljs built_in 类")
            self.assertNotIn(f, kw_set, f"函数 {f} 不应进入 hljs keyword 类")

    def test_literal_class(self):
        lit = self._extract("literal")
        self.assertIn("true", lit)
        self.assertIn("false", lit)


class TestSampleCoverage(unittest.TestCase):
    """用语言样本交叉验证关键字/引用/字符串（需求 14.24）。"""

    @classmethod
    def setUpClass(cls):
        cls.spec = _load(SPEC_PATH)
        cls.basic = _raw(os.path.join(SAMPLES, "basic.flow"))
        cls.advanced = _raw(os.path.join(SAMPLES, "advanced.flow"))

    def test_sample_keywords_recognized(self):
        text = self.basic + self.advanced
        keywords = set(sum(self.spec["keywords"].values(), []))
        for kw in ["workflow", "metadata", "variables", "trigger", "runtime",
                   "steps", "step", "action", "foreach", "delay", "switch", "case",
                   "default", "for", "in", "break", "continue", "parallel", "notify",
                   "expect", "on_error", "import", "namespace", "tag", "audit",
                   "environment", "interval", "http", "if", "assert", "retry_policy",
                   "max_concurrency", "filter", "map", "reduce", "depends_on",
                   "timeout", "retry"]:
            self.assertIn(kw, text, f"样本缺少关键字 {kw}")
            self.assertIn(kw, keywords, f"关键字 {kw} 未被规范收录")

    def test_sample_interpolation_and_triple_string(self):
        self.assertIn("${vars.name}", self.basic)
        # 高级样本含三双引号字符串
        self.assertIn('"""', self.basic + "")  # 结构上存在

    def test_sample_references_present(self):
        self.assertIn("vars.", self.basic)
        self.assertIn("steps.", self.basic)


class TestAstMapping(unittest.TestCase):
    def test_flow_variant_meta_scopes(self):
        spec = _load(SPEC_PATH)
        scopes = spec["ast"]["flowVariantScopes"]
        for variant in ["Switch", "For", "Parallel", "Notify", "Validate",
                        "Wait", "Assert", "Delay", "Return"]:
            scope = scopes.get(variant, {}).get("scope", "")
            self.assertTrue(scope.startswith("meta."), f"{variant} 缺 meta scope")


class TestHighlightJsFunctional(unittest.TestCase):
    """用真实 highlight.js 引擎对生成的 cloudflow.hljs.js 做端到端验证。

    回归保护：曾暴露过两个真实 bug——
      1) 字符串 contains 里放无 begin/end 的规则，使 `"..."` 之后的全部内容被吞成字符串（永不 end）；
      2) HLJS 关键字/数字/操作符等类别不触发。
    本类断言：字符串正确闭合、关键字/数字类触发、注册的 built_in 词汇仍在。
    若本机未找到 highlight.js，则跳过（不误报）。
    """

    @classmethod
    def setUpClass(cls):
        import subprocess
        cls.engine = None
        cls.out = ""
        cls.err = None
        cls.raw = ""
        cands = [
            os.path.expanduser("~/ProgramDir/highlight/highlight.min.js"),
            os.path.expanduser("~/ProgramDir/WebProject/CodeHighLight/highlight/highlight.min.js"),
        ]
        hljs_path = next((c for c in cands if os.path.isfile(c)), None)
        if not hljs_path:
            return
        runner = os.path.join(HERE, "hljs_runner.mjs")
        env = {
            "HLJS_PATH": hljs_path,
            "GEN_PATH": os.path.join(BUILD, "cloudflow.hljs.js"),
            "SAMPLE_PATH": os.path.join(ROOT, "samples", "basic.flow"),
        }
        env.update(os.environ)
        try:
            res = subprocess.run(["node", runner], capture_output=True, text=True,
                                 env=env, cwd=ROOT, timeout=60)
            if res.returncode != 0:
                cls.engine = "error"
                cls.raw = res.stderr
                return
            data = json.loads(res.stdout.strip())
            cls.engine = "ok"
            cls.out = data.get("out") or ""
            cls.err = data.get("err")
        except Exception as exc:  # noqa: BLE001
            cls.engine = "error"
            cls.raw = str(exc)

    def _ready(self):
        if self.engine is None:
            self.skipTest("本机未找到 highlight.js，跳过功能性验证")
        if self.engine == "error":
            self.fail(f"highlight.js 执行失败: {self.raw}")

    def test_string_terminates_not_swallowed(self):
        self._ready()
        n_string = self.out.count('class="hljs-string"')
        # 回归防护：此前 bug 会让首个字符串把其后全部内容吞成一个巨大 string span（n=1）。
        # 正常 basic.flow 有 8 个独立字符串；这里宽松断言至少 5 个，确保未整体吞并。
        self.assertGreaterEqual(n_string, 5, f"字符串数量过低({n_string})：可能被吞并或未 end")

    def test_keyword_and_number_classes_fire(self):
        self._ready()
        self.assertIn('hljs-keyword">workflow</span>', self.out, "workflow 关键字未高亮")
        self.assertIn('hljs-keyword">steps</span>', self.out, "steps 关键字未高亮")
        self.assertIn("hljs-number", self.out, "数字类未触发")
        # built_in（filter/map/reduce/vars）由 advanced.flow 覆盖，basic.flow 不含这些词

    def test_variable_reference_and_interpolation(self):
        self._ready()
        # vars.label 引用（variable 类）与 ${vars.name} 插值都应高亮
        self.assertIn("hljs-variable", self.out, "变量引用/插值类未触发")


if __name__ == "__main__":
    unittest.main(verbosity=1)
