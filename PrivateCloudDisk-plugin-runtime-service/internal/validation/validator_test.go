package validation

import (
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/model"
)

func testTimeout() time.Duration { return 8 * time.Second }

func TestJavaScriptFallbackRejectsHostAccess(t *testing.T) {
	validator := Validator{Timeout: testTimeout()}
	response := validator.JavaScript(model.ValidationRequest{
		Source: `const fs = require("fs"); fs.readFileSync("/etc/passwd");`,
	})
	if response.Valid {
		t.Fatal("内建门禁应拒绝 require 宿主模块")
	}
	if response.ErrorType != "SECURITY_VIOLATION" {
		t.Fatalf("应返回 SECURITY_VIOLATION，实际 %q", response.ErrorType)
	}
	if len(response.Findings) == 0 {
		t.Fatal("应包含安全告警 finding")
	}
	if strings.Contains(response.Message, "/etc") {
		t.Fatalf("错误信息必须脱敏，实际 %q", response.Message)
	}
}

func TestJavaScriptFallbackAllowsCleanCode(t *testing.T) {
	validator := Validator{Timeout: testTimeout()}
	response := validator.JavaScript(model.ValidationRequest{
		Source:     `function add(a, b) { return a + b; }`,
		Entrypoint: "main.js",
	})
	if !response.Valid {
		t.Fatalf("纯业务代码应通过内建门禁，实际 %+v", response)
	}
}

func TestJavaScriptScriptPathASTGate(t *testing.T) {
	if _, err := exec.LookPath("node"); err != nil {
		t.Skip("node 不可用，跳过 JS AST 校验器集成测试")
	}
	script, err := filepath.Abs(filepath.Join("..", "..", "validator", "validate_js.mjs"))
	if err != nil {
		t.Fatal(err)
	}
	validator := Validator{JSScript: script, Timeout: testTimeout()}

	clean := validator.JavaScript(model.ValidationRequest{
		Source:     `import { invoke } from "plugin-sdk"; export const main = () => invoke("x", {});`,
		Entrypoint: "main.js",
	})
	if !clean.Valid {
		t.Fatalf("SDK 代码应通过 JS AST 校验，实际 %+v", clean)
	}

	hostile := validator.JavaScript(model.ValidationRequest{
		Source:     `eval("process")`,
		Entrypoint: "main.js",
	})
	if hostile.Valid || hostile.ErrorType != "SECURITY_VIOLATION" {
		t.Fatalf("eval 应被 AST 校验拦截，实际 %+v", hostile)
	}
	// 消息经全局脱敏（6.2/6.7）。
	if message := hostile.Message; message == "" {
		t.Fatal("应返回可读的脱敏错误信息")
	}
}

func TestPythonScriptPathASTGate(t *testing.T) {
	if _, err := exec.LookPath("python3"); err != nil {
		t.Skip("python3 不可用，跳过 Python AST 校验器集成测试")
	}
	script, err := filepath.Abs(filepath.Join("..", "..", "validator", "validate_python.py"))
	if err != nil {
		t.Fatal(err)
	}
	validator := Validator{PythonScript: script, Timeout: testTimeout()}

	clean := validator.Python(model.ValidationRequest{
		Source:     "def main():\n    return 42\n",
		Entrypoint: "main.py",
	})
	if !clean.Valid {
		t.Fatalf("普通 Python 应通过 AST 校验，实际 %+v", clean)
	}

	hostile := validator.Python(model.ValidationRequest{
		Source:     "import os\nos.system('id')\n",
		Entrypoint: "main.py",
	})
	if hostile.Valid {
		t.Fatalf("导入 os 应被 Python AST 校验拦截")
	}
}

func TestJavaScriptCacheReusesResult(t *testing.T) {
	validator := Validator{Timeout: testTimeout()}
	request := model.ValidationRequest{Source: `process.binding("x")`, Entrypoint: "main.js"}
	first := validator.JavaScript(request)
	second := validator.JavaScript(request)
	if first.Valid != second.Valid || first.Message != second.Message {
		t.Fatal("同源重复校验应命中结果缓存")
	}
	if first.ErrorType != "SECURITY_VIOLATION" {
		t.Fatalf("兜底门禁应识别 process.binding，实际 %+v", first)
	}
}
