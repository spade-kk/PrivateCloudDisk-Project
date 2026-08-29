package validation

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"os/exec"
	"regexp"
	"sync"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/model"
	"privateclouddisk/plugin-runtime-service/internal/sanitize"
)

// Validator 以子进程方式运行各语言的 AST 静态校验器，并做结果缓存（5.20）。
//
// Python 与 JavaScript 校验器都是“只解析不执行”的独立脚本（validate_python.py /
// validate_js.mjs），失败时会退化为内建正则门禁，保证向后兼容。
type Validator struct {
	PythonScript string
	JSScript     string
	Timeout      time.Duration

	cacheMu sync.Mutex
	cache   map[string]model.ValidationResponse
}

// ValidationCacheSize 限制结果缓存条目数（4.22/5.20），避免无界增长。
const ValidationCacheSize = 96

func (v *Validator) cached(source, entrypoint, kind string) (model.ValidationResponse, bool) {
	v.cacheMu.Lock()
	defer v.cacheMu.Unlock()
	if v.cache == nil {
		return model.ValidationResponse{}, false
	}
	response, ok := v.cache[v.cacheKey(source, entrypoint, kind)]
	return response, ok
}

func (v *Validator) store(source, entrypoint, kind string, response model.ValidationResponse) {
	v.cacheMu.Lock()
	defer v.cacheMu.Unlock()
	if v.cache == nil {
		v.cache = make(map[string]model.ValidationResponse)
	}
	v.cache[v.cacheKey(source, entrypoint, kind)] = response
	if len(v.cache) > ValidationCacheSize {
		for key := range v.cache {
			delete(v.cache, key)
			break
		}
	}
}

func (v *Validator) cacheKey(source, entrypoint, kind string) string {
	sum := sha256.Sum256([]byte(kind + "\x00" + source + "\x00" + entrypoint))
	return hex.EncodeToString(sum[:])
}

func (v *Validator) cacheAndReturn(
	request model.ValidationRequest, kind string, response model.ValidationResponse,
) model.ValidationResponse {
	v.store(request.Source, request.Entrypoint, kind, response)
	return response
}

// Python 调用 AST 校验器（--ast-only 由校验器本身保证：本服务从不执行插件代码，4.20）。
func (v *Validator) Python(request model.ValidationRequest) model.ValidationResponse {
	if cached, ok := v.cached(request.Source, request.Entrypoint, "python"); ok {
		return cached
	}
	ctx, cancel := context.WithTimeout(context.Background(), v.Timeout)
	defer cancel()
	command := exec.CommandContext(ctx, "python3", "-I", "-S", v.PythonScript, "--ast-only")
	payload, _ := json.Marshal(request)
	command.Stdin = bytes.NewReader(payload)
	output, err := command.Output()
	if ctx.Err() != nil {
		return v.cacheAndReturn(request, "python", model.ValidationResponse{
			Valid: false, ErrorType: "RESOURCE_LIMIT",
			Message: "Python 静态校验超时", Metrics: map[string]interface{}{},
			Findings: []map[string]interface{}{},
		})
	}
	if err != nil {
		return v.cacheAndReturn(request, "python", model.ValidationResponse{
			Valid: false, ErrorType: "VALIDATOR_ERROR",
			Message: "Python 静态校验器不可用", Metrics: map[string]interface{}{},
			Findings: []map[string]interface{}{},
		})
	}
	var response model.ValidationResponse
	if json.Unmarshal(output, &response) != nil {
		return v.cacheAndReturn(request, "python", model.ValidationResponse{
			Valid: false, ErrorType: "VALIDATOR_ERROR",
			Message: "Python 静态校验结果无效", Metrics: map[string]interface{}{},
			Findings: []map[string]interface{}{},
		})
	}
	response.Message = sanitize.Summary(sanitize.Sanitize(response.Message), 500)
	return v.cacheAndReturn(request, "python", response)
}

// JavaScript 调用 Node AST 校验器（validate_js.mjs）。校验器不可用时退回内建正则门禁（5.24 辅助）。
func (v *Validator) JavaScript(request model.ValidationRequest) model.ValidationResponse {
	if cached, ok := v.cached(request.Source, request.Entrypoint, "javascript"); ok {
		return cached
	}
	if v.JSScript == "" {
		return v.javascriptFallback(request)
	}
	ctx, cancel := context.WithTimeout(context.Background(), v.Timeout)
	defer cancel()
	command := exec.CommandContext(ctx, "node", v.JSScript, "--ast-only")
	payload, _ := json.Marshal(request)
	command.Stdin = bytes.NewReader(payload)
	output, err := command.Output()
	if ctx.Err() != nil {
		return v.cacheAndReturn(request, "javascript", model.ValidationResponse{
			Valid: false, ErrorType: "RESOURCE_LIMIT",
			Message: "JavaScript 静态校验超时", Metrics: map[string]interface{}{},
			Findings: []map[string]interface{}{},
		})
	}
	if err != nil {
		if errors.Is(err, exec.ErrNotFound) {
			// node 缺失：退化为无子进程依赖的内建正则门禁（向后兼容）。
			return v.javascriptFallback(request)
		}
		// 校验器脚本异常：同样退化为内建门禁，避免发布门禁降级成“直接放行”。
		return v.javascriptFallback(request)
	}
	var response model.ValidationResponse
	if json.Unmarshal(output, &response) != nil {
		return v.cacheAndReturn(request, "javascript", model.ValidationResponse{
			Valid: false, ErrorType: "VALIDATOR_ERROR",
			Message: "JavaScript 静态校验结果无效", Metrics: map[string]interface{}{},
			Findings: []map[string]interface{}{},
		})
	}
	response.Message = sanitize.Summary(sanitize.Sanitize(response.Message), 500)
	return v.cacheAndReturn(request, "javascript", response)
}

// forbiddenJavaScript 内建正则门禁：ESLint 安全插件的轻量等价物，只作为校验器不可用时的兜底（5.24）。
var forbiddenJavaScript = []*regexp.Regexp{
	regexp.MustCompile(`(?i)\beval\s*\(`),
	regexp.MustCompile(`(?i)\bnew\s+Function\s*\(`),
	regexp.MustCompile(`(?i)\bdocument\.write\s*\(`),
	regexp.MustCompile(`(?i)\bchild_process\b`),
	regexp.MustCompile(`(?i)\bprocess\.binding\b`),
	regexp.MustCompile(`(?i)\brequire\s*\(\s*["'][^"']+["']\s*\)`),
}

func (v *Validator) javascriptFallback(request model.ValidationRequest) model.ValidationResponse {
	findings := make([]map[string]interface{}, 0)
	message := "校验通过"
	for _, pattern := range forbiddenJavaScript {
		if loc := pattern.FindStringIndex(request.Source); loc != nil {
			line, column := sourceLocation(request.Source, loc[0])
			findings = append(findings, map[string]interface{}{
				"type":    "SECURITY_VIOLATION",
				"line":    line,
				"column":  column,
				"message": "代码包含本地插件运行时禁止的动态执行或宿主访问能力",
			})
		}
	}
	if len(findings) > 0 {
		message = "JavaScript 安全策略校验未通过"
	}
	errorType := ""
	if len(findings) > 0 {
		errorType = "SECURITY_VIOLATION"
	}
	return model.ValidationResponse{
		Valid: len(findings) == 0, Metrics: map[string]interface{}{
			"mode": "regex-fallback", "source_bytes": len(request.Source),
		},
		Message: message, Findings: findings, ErrorType: errorType,
	}
}

// sourceLocation 把字节偏移换算为 1-based 行/列，用于内建门禁的定位（5.16）。
func sourceLocation(source string, offset int) (line, column int) {
	line = 1
	column = 1
	for index := 0; index < offset && index < len(source); index++ {
		if source[index] == '\n' {
			line++
			column = 1
		} else {
			column++
		}
	}
	return line, column
}
