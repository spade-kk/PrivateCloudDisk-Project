// Package adapter converts Hub registry rows into a deliberately narrow MCP
// tool surface.  This is not an "expose every registered capability" adapter:
// a static reviewed export policy is a defence-in-depth layer, while Hub still
// makes the final user/tenant/resource permission decision on every call.
package adapter

import (
	"encoding/json"
	"sort"
	"strings"

	"privateclouddisk/cloudflow-mcp-server/internal/model"
)

type ToolBinding struct {
	ToolName        string
	Title           string
	CapabilityKey   string
	DescriptionHint string
}

var reviewedBindings = []ToolBinding{
	{ToolName: "cloudflow.file.list", Title: "列出文件", CapabilityKey: "api:file.list", DescriptionHint: "列出当前已授权空间中的文件和目录。"},
	{ToolName: "cloudflow.file.search", Title: "搜索文件", CapabilityKey: "api:file.search", DescriptionHint: "按关键词搜索当前有权读取的文件。"},
	{ToolName: "cloudflow.file.read", Title: "读取文本文件", CapabilityKey: "api:file.content.get", DescriptionHint: "读取受限大小的文本、代码或 Markdown 文件内容。"},
	{ToolName: "cloudflow.file.metadata", Title: "获取文件元数据", CapabilityKey: "api:file.metadata.get", DescriptionHint: "获取已授权文件的名称、大小和类型等元数据。"},
	{ToolName: "cloudflow.space.info", Title: "获取空间信息", CapabilityKey: "api:space.info", DescriptionHint: "获取当前已授权空间的基本信息。"},
	{ToolName: "cloudflow.workflow.list", Title: "列出工作流", CapabilityKey: "api:workflow.list", DescriptionHint: "列出当前用户可访问的工作流。"},
	{ToolName: "cloudflow.workflow.run", Title: "运行工作流", CapabilityKey: "api:workflow.execute", DescriptionHint: "在当前授权上下文中运行工作流。"},
	{ToolName: "cloudflow.workflow.status", Title: "查询工作流状态", CapabilityKey: "api:workflow.status", DescriptionHint: "查询已授权工作流运行状态。"},
}

var protectedArguments = map[string]struct{}{
	"user_id": {}, "userid": {}, "tenant_id": {}, "tenantid": {}, "space_id": {}, "spaceid": {},
	"permission_context": {}, "permissioncontext": {}, "granted_permissions": {}, "grantedpermissions": {},
	"declared_permissions": {}, "declaredpermissions": {}, "execution_id": {}, "executionid": {},
	"step_id": {}, "stepid": {}, "trace_id": {}, "traceid": {}, "idempotency_key": {}, "idempotencykey": {},
}

func init() {
	sort.Slice(reviewedBindings, func(left, right int) bool { return reviewedBindings[left].ToolName < reviewedBindings[right].ToolName })
}

func BindingForTool(name string) (ToolBinding, bool) {
	for _, binding := range reviewedBindings {
		if binding.ToolName == name {
			return binding, true
		}
	}
	return ToolBinding{}, false
}

func BindingForCapability(key string) (ToolBinding, bool) {
	for _, binding := range reviewedBindings {
		if binding.CapabilityKey == key {
			return binding, true
		}
	}
	return ToolBinding{}, false
}

func ToTools(rows []model.CapabilityRow) []model.Tool {
	tools := make([]model.Tool, 0, len(rows))
	for _, row := range rows {
		binding, ok := BindingForCapability(row.CapabilityKey)
		if !ok || row.Status != "ACTIVE" {
			continue
		}
		input, ok := sanitizedSchema(row.InputSchemaJSON)
		if !ok {
			continue // Invalid internal schema must not become an ambiguous public tool.
		}
		output, _ := schema(row.OutputSchemaJSON)
		description := strings.TrimSpace(row.Description)
		if description == "" {
			description = binding.DescriptionHint
		}
		tools = append(tools, model.Tool{
			Name: binding.ToolName, Title: binding.Title, Description: description,
			InputSchema: input, OutputSchema: output,
			Annotations: map[string]any{"readOnlyHint": binding.CapabilityKey != "api:workflow.execute"},
			Execution:   map[string]any{"taskSupport": "forbidden"},
		})
	}
	sort.Slice(tools, func(left, right int) bool { return tools[left].Name < tools[right].Name })
	return tools
}

func SanitizeArguments(arguments map[string]any) map[string]any {
	result := make(map[string]any, len(arguments))
	for key, value := range arguments {
		if _, protected := protectedArguments[canonical(key)]; protected {
			continue
		}
		result[key] = value
	}
	return result
}

func sanitizedSchema(value string) (map[string]any, bool) {
	result, ok := schema(value)
	if !ok {
		return nil, false
	}
	properties, _ := result["properties"].(map[string]any)
	if properties != nil {
		for key := range properties {
			if _, protected := protectedArguments[canonical(key)]; protected {
				delete(properties, key)
			}
		}
	}
	if required, ok := result["required"].([]any); ok {
		filtered := make([]any, 0, len(required))
		for _, item := range required {
			text, _ := item.(string)
			if _, protected := protectedArguments[canonical(text)]; !protected {
				filtered = append(filtered, item)
			}
		}
		result["required"] = filtered
	}
	result["type"] = "object"
	return result, true
}

func schema(value string) (map[string]any, bool) {
	if strings.TrimSpace(value) == "" {
		return map[string]any{"type": "object", "additionalProperties": false}, true
	}
	var result map[string]any
	if err := json.Unmarshal([]byte(value), &result); err != nil || result == nil {
		return nil, false
	}
	return result, true
}

func canonical(value string) string {
	value = strings.ToLower(strings.TrimSpace(value))
	value = strings.ReplaceAll(value, "-", "_")
	return value
}
