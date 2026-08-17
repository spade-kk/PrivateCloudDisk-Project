package validation

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"os/exec"
	"regexp"
	"strings"
	"time"
	"privateclouddisk/plugin-runtime-service/internal/model"
)

type Validator struct {
	PythonScript string
	Timeout      time.Duration
}

func (v Validator) Python(request model.ValidationRequest) model.ValidationResponse {
	ctx, cancel := context.WithTimeout(context.Background(), v.Timeout)
	defer cancel()
	command := exec.CommandContext(ctx, "python3", "-I", "-S", v.PythonScript)
	payload, _ := json.Marshal(request)
	command.Stdin = bytes.NewReader(payload)
	output, err := command.Output()
	if ctx.Err() != nil {
		return model.ValidationResponse{
			Valid: false, ErrorType: "RESOURCE_LIMIT",
			Message: "Python 静态校验超时", Findings: []map[string]interface{}{},
			Metrics: map[string]interface{}{},
		}
	}
	if err != nil {
		return model.ValidationResponse{
			Valid: false, ErrorType: "VALIDATOR_ERROR",
			Message: "Python 静态校验器不可用", Findings: []map[string]interface{}{},
			Metrics: map[string]interface{}{},
		}
	}
	var response model.ValidationResponse
	if json.Unmarshal(output, &response) != nil {
		return model.ValidationResponse{
			Valid: false, ErrorType: "VALIDATOR_ERROR",
			Message: "Python 静态校验结果无效", Findings: []map[string]interface{}{},
			Metrics: map[string]interface{}{},
		}
	}
	return response
}

var forbiddenJavaScript = []*regexp.Regexp{
	regexp.MustCompile(`(?i)\beval\s*\(`),
	regexp.MustCompile(`(?i)\bnew\s+Function\s*\(`),
	regexp.MustCompile(`(?i)\bdocument\.write\s*\(`),
	regexp.MustCompile(`(?i)\bchild_process\b`),
	regexp.MustCompile(`(?i)\bprocess\.binding\b`),
}

func (v Validator) JavaScript(request model.ValidationRequest) model.ValidationResponse {
	findings := make([]map[string]interface{}, 0)
	for _, pattern := range forbiddenJavaScript {
		if pattern.FindStringIndex(request.Source) != nil {
			findings = append(findings, map[string]interface{}{
				"type":    "SECURITY_VIOLATION",
				"message": "代码包含本地插件运行时禁止的动态执行或宿主访问能力",
			})
		}
	}
	ctx, cancel := context.WithTimeout(context.Background(), v.Timeout)
	defer cancel()
	command := exec.CommandContext(ctx, "node", "--check", "-")
	command.Stdin = strings.NewReader(request.Source)
	var stderr bytes.Buffer
	command.Stderr = &stderr
	err := command.Run()
	if ctx.Err() != nil {
		return model.ValidationResponse{
			Valid: false, ErrorType: "RESOURCE_LIMIT", Message: "JavaScript 校验超时",
			Findings: findings, Metrics: map[string]interface{}{},
		}
	}
	if err != nil {
		message := sanitize(stderr.String())
		if errors.Is(err, exec.ErrNotFound) {
			message = "JavaScript 静态校验器不可用"
		}
		return model.ValidationResponse{
			Valid: false, ErrorType: "SYNTAX_ERROR", Message: message,
			Findings: findings, Metrics: map[string]interface{}{},
		}
	}
	if len(findings) > 0 {
		return model.ValidationResponse{
			Valid: false, ErrorType: "SECURITY_VIOLATION",
			Message:  "JavaScript 安全策略校验未通过",
			Findings: findings, Metrics: map[string]interface{}{},
		}
	}
	return model.ValidationResponse{
		Valid: true, Message: "校验通过", Findings: findings,
		Metrics: map[string]interface{}{"source_bytes": len(request.Source)},
	}
}

func sanitize(value string) string {
	value = strings.ReplaceAll(value, "\n", " ")
	value = strings.TrimSpace(value)
	if len(value) > 500 {
		return value[:500]
	}
	return value
}
