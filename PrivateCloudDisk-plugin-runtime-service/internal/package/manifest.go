// Package pkg 实现 .pcdpkg 受约束 ZIP + manifest.yaml 的解析与安全校验。
//
// 以 PLUGIN_AUTOMATION_PLATFORM_DESIGN.md 第 7 章为规范（7.1/7.2/7.3）：
//   - 扩展名 .pcdpkg，本质为受约束 ZIP；包内必须含 manifest.yaml 与 src/。
//   - manifest_version=1；plugin.id 为 UUID；plugin.type 为 CLOUD_PLUGIN；
//     runtime.language=python 且版本在允许列表；permissions/entrypoints/exports/limits 校验。
//
// 能力导出项 relative 到 src/ 的 module/function 属 §7.2 示例方案的代码落地扩展。
package pkg

import (
	"errors"
	"fmt"
	"regexp"
	"strings"

	"gopkg.in/yaml.v3"
)

// ErrorKind 区分结构化解析错误类型（4.25/3.25：只返回原因，不暴露内部路径）。
type ErrorKind string

const (
	ErrManifestMissing ErrorKind = "MANIFEST_MISSING"
	ErrManifestInvalid ErrorKind = "MANIFEST_INVALID"
	ErrManifestVersion ErrorKind = "MANIFEST_VERSION_UNSUPPORTED"
	ErrPluginID        ErrorKind = "PLUGIN_ID_INVALID"
	ErrPluginType      ErrorKind = "PLUGIN_TYPE_INVALID"
	ErrPluginVersion   ErrorKind = "PLUGIN_VERSION_INVALID"
	ErrRuntime         ErrorKind = "RUNTIME_INVALID"
	ErrEntrypoint      ErrorKind = "ENTRYPOINT_INVALID"
	ErrExport          ErrorKind = "EXPORT_INVALID"
	ErrPermission      ErrorKind = "PERMISSION_INVALID"
	ErrLimit           ErrorKind = "LIMIT_INVALID"
	ErrStructure       ErrorKind = "PACKAGE_STRUCTURE_INVALID"
	ErrSecurity        ErrorKind = "PACKAGE_SECURITY_INVALID"
	ErrResourceLimit   ErrorKind = "PACKAGE_RESOURCE_LIMIT"
	ErrSensitiveFile   ErrorKind = "SENSITIVE_FILE_REJECTED"
	ErrPathEscape      ErrorKind = "PATH_ESCAPE_REJECTED"
)

// ParseError 是结构化包解析错误：Err 为业务原因，Internal 为非敏感内部细节（不落日志）。
type ParseError struct {
	Kind     ErrorKind
	Message  string
	Detailed string
}

func (e *ParseError) Error() string {
	return string(e.Kind) + ": " + e.Message
}

func kindError(kind ErrorKind, format string, args ...interface{}) error {
	return &ParseError{Kind: kind, Message: fmt.Sprintf(format, args...)}
}

// Manifest 对应 manifest.yaml（设计文档 7.2 清单示例）。
type Manifest struct {
	ManifestVersion int          `yaml:"manifest_version" json:"manifest_version"`
	Plugin          PluginMeta   `yaml:"plugin" json:"plugin"`
	Runtime         RuntimeMeta  `yaml:"runtime" json:"runtime"`
	Permissions     []string     `yaml:"permissions" json:"permissions"`
	Entrypoints     Entrypoints  `yaml:"entrypoints" json:"entrypoints"`
	Exports         []ExportMeta `yaml:"exports" json:"exports,omitempty"`
	Limits          LimitsMeta   `yaml:"limits" json:"limits,omitempty"`
}

type PluginMeta struct {
	ID   string `yaml:"id" json:"id"`
	Name string `yaml:"name" json:"name"`
	Type string `yaml:"type" json:"type"`
	// Version 为语义化版本；yaml.v3 解析 "1.0.0" 为字符串需保留原值，由此字段承接。
	Version string `yaml:"version" json:"version"`
}

type RuntimeMeta struct {
	Language string `yaml:"language" json:"language"`
	Version  string `yaml:"version" json:"version"`
}

// EventEntry 是事件触发入口（7.2 entrypoints.events[]）。
type EventEntry struct {
	Event       string                 `yaml:"event" json:"event"`
	Module      string                 `yaml:"module" json:"module"`
	Function    string                 `yaml:"function" json:"function"`
	Priority    int                    `yaml:"priority" json:"priority"`
	Conditions  map[string]interface{} `yaml:"conditions" json:"conditions,omitempty"`
	Permissions []string               `yaml:"permissions" json:"permissions,omitempty"`
}

type Entrypoints struct {
	Events []EventEntry `yaml:"events" json:"events,omitempty"`
}

// ExportMeta 是能力导出项；Runner 据此解析 ExecuteCapability 的 module/function。
type ExportMeta struct {
	Name         string   `yaml:"name" json:"name"`
	Description  string   `yaml:"description" json:"description,omitempty"`
	Module       string   `yaml:"module" json:"module"`
	Function     string   `yaml:"function" json:"function"`
	InputSchema  string   `yaml:"input_schema" json:"input_schema,omitempty"`
	OutputSchema string   `yaml:"output_schema" json:"output_schema,omitempty"`
	Permissions  []string `yaml:"permissions" json:"permissions,omitempty"`
}

type LimitsMeta struct {
	TimeoutSeconds int `yaml:"timeout_seconds" json:"timeout_seconds,omitempty"`
	MemoryMB       int `yaml:"memory_mb" json:"memory_mb,omitempty"`
}

var (
	uuidRe   = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)
	semverRe = regexp.MustCompile(`^[0-9]+\.[0-9]+\.[0-9]+$`)
)

// allowedPythonVersions 由 config 与镜像约束（MVP python 3.11，§7.2/§8.4）。
var allowedPythonVersions = map[string]bool{"3.11": true}

// ParseManifestBytes 解析并校验 manifest.yaml 内容。
func ParseManifestBytes(data []byte) (*Manifest, error) {
	var raw map[string]interface{}
	if err := yaml.Unmarshal(data, &raw); err != nil {
		return nil, kindError(ErrManifestInvalid, "manifest.yaml 不是合法 YAML")
	}
	manifest, err := decodeManifest(raw)
	if err != nil {
		return nil, err
	}
	if err := manifest.validate(); err != nil {
		return nil, err
	}
	return manifest, nil
}

// decodeManifest 从 YAML map 手工取字段并做初类型收敛（不信任多余字段）。
func decodeManifest(raw map[string]interface{}) (*Manifest, error) {
	manifest := &Manifest{}
	if value, ok := raw["manifest_version"]; ok {
		n, err := toInt(value)
		if err != nil {
			return nil, kindError(ErrManifestVersion, "manifest_version 必须为整数")
		}
		manifest.ManifestVersion = n
	}
	if plugin, ok := asMap(raw["plugin"]); ok {
		manifest.Plugin.ID, _ = asString(plugin["id"])
		manifest.Plugin.Name, _ = asString(plugin["name"])
		manifest.Plugin.Type, _ = asString(plugin["type"])
		manifest.Plugin.Version = asStringAny(plugin["version"])
	}
	if runtime, ok := asMap(raw["runtime"]); ok {
		manifest.Runtime.Language, _ = asString(runtime["language"])
		manifest.Runtime.Version = asStringAny(runtime["version"])
	}
	if permissions, ok := asStringList(raw["permissions"]); ok {
		manifest.Permissions = permissions
	}
	if entryRaw, ok := asMap(raw["entrypoints"]); ok {
		if events, ok := asMapList(entryRaw["events"]); ok {
			for _, item := range events {
				entry, err := decodeEvent(item)
				if err != nil {
					return nil, err
				}
				manifest.Entrypoints.Events = append(manifest.Entrypoints.Events, entry)
			}
		}
	}
	if exports, ok := asMapList(raw["exports"]); ok {
		for _, item := range exports {
			export, err := decodeExport(item)
			if err != nil {
				return nil, err
			}
			manifest.Exports = append(manifest.Exports, export)
		}
	}
	if limits, ok := asMap(raw["limits"]); ok {
		if value, ok := asInt(limits["timeout_seconds"]); ok {
			manifest.Limits.TimeoutSeconds = value
		}
		if value, ok := asInt(limits["memory_mb"]); ok {
			manifest.Limits.MemoryMB = value
		}
	}
	return manifest, nil
}

func decodeEvent(item map[string]interface{}) (EventEntry, error) {
	entry := EventEntry{}
	entry.Event, _ = asString(item["event"])
	entry.Module, _ = asString(item["module"])
	entry.Function, _ = asString(item["function"])
	entry.Priority, _ = asInt(item["priority"])
	if conditions, ok := asMap(item["conditions"]); ok {
		entry.Conditions = conditions
	}
	if permissions, ok := asStringList(item["permissions"]); ok {
		entry.Permissions = permissions
	}
	return entry, nil
}

func decodeExport(item map[string]interface{}) (ExportMeta, error) {
	export := ExportMeta{}
	export.Name, _ = asString(item["name"])
	export.Description, _ = asString(item["description"])
	export.Module, _ = asString(item["module"])
	export.Function, _ = asString(item["function"])
	export.InputSchema, _ = asString(item["input_schema"])
	export.OutputSchema, _ = asString(item["output_schema"])
	if permissions, ok := asStringList(item["permissions"]); ok {
		export.Permissions = permissions
	}
	return export, nil
}

func (m *Manifest) validate() error {
	if m.ManifestVersion != 1 {
		return kindError(ErrManifestVersion, "manifest_version 必须为 1，当前 %d", m.ManifestVersion)
	}
	if m.Plugin.ID == "" {
		return kindError(ErrPluginID, "plugin.id 缺失")
	}
	if !uuidRe.MatchString(m.Plugin.ID) {
		return kindError(ErrPluginID, "plugin.id 必须是 UUID 格式")
	}
	if m.Plugin.Type == "" {
		return kindError(ErrPluginType, "plugin.type 缺失")
	}
	if m.Plugin.Type != "CLOUD_PLUGIN" && m.Plugin.Type != "LOCAL_PLUGIN" && m.Plugin.Type != "WORKFLOW_PLUGIN" {
		return kindError(ErrPluginType, "plugin.type 不支持：%s", m.Plugin.Type)
	}
	if !semverRe.MatchString(m.Plugin.Version) {
		return kindError(ErrPluginVersion, "plugin.version 必须是语义化版本 x.y.z")
	}
	if m.Runtime.Language != "python" {
		return kindError(ErrRuntime, "runtime.language 仅支持 python，当前 %s", m.Runtime.Language)
	}
	if !allowedPythonVersions[m.Runtime.Version] {
		return kindError(ErrRuntime, "runtime.version 不在允许列表：%s", m.Runtime.Version)
	}
	for _, permission := range m.Permissions {
		if permission == "" {
			return kindError(ErrPermission, "permissions 包含空权限")
		}
	}
	for index := range m.Entrypoints.Events {
		entry := &m.Entrypoints.Events[index]
		if entry.Event == "" || entry.Module == "" || entry.Function == "" {
			return kindError(ErrEntrypoint, "entrypoints.events[%d] 缺少 event/module/function", index)
		}
		if !moduleUnderSrc(entry.Module) {
			return kindError(ErrEntrypoint, "entrypoints.events[%d] 的 module 必须在 src/ 内", index)
		}
	}
	for index, export := range m.Exports {
		if export.Name == "" {
			return kindError(ErrExport, "exports[%d] 缺少 name", index)
		}
		if export.Module == "" || export.Function == "" {
			return kindError(ErrExport, "exports[%d] (%s) 缺少 module/function", index, export.Name)
		}
		if !moduleUnderSrc(export.Module) {
			return kindError(ErrExport, "exports[%d] (%s) 的 module 必须在 src/ 内", index, export.Name)
		}
	}
	// 同一事件允许多个入口构成按 priority 升序的链式处理（4.16）。
	for _, event := range m.Entrypoints.Events {
		for _, permission := range event.Permissions {
			if !containsString(m.Permissions, permission) {
				return kindError(ErrPermission, "事件 %s 声明权限 %s 不在全局 permissions 中", event.Event, permission)
			}
		}
	}
	seenExport := map[string]bool{}
	for _, export := range m.Exports {
		if seenExport[export.Name] {
			return kindError(ErrExport, "exports 存在重复能力：%s", export.Name)
		}
		seenExport[export.Name] = true
		for _, permission := range export.Permissions {
			if !containsString(m.Permissions, permission) {
				return kindError(ErrPermission, "能力 %s 声明权限 %s 不在全局 permissions 中", export.Name, permission)
			}
		}
	}
	if m.Limits.TimeoutSeconds < 0 || m.Limits.MemoryMB < 0 {
		return kindError(ErrLimit, "limits 不能为负数")
	}
	return nil
}

func moduleUnderSrc(module string) bool {
	return strings.HasPrefix(module, "src/") && len(module) > len("src/")
}

// EventByName 返回指定事件类型入口（不存在返回 false）。
func (m *Manifest) EventByName(event string) (EventEntry, bool) {
	for _, entry := range m.Entrypoints.Events {
		if entry.Event == event {
			return entry, true
		}
	}
	return EventEntry{}, false
}

// ExportByName 返回指定能力导出（不存在返回 false）。
func (m *Manifest) ExportByName(name string) (ExportMeta, bool) {
	for _, export := range m.Exports {
		if export.Name == name {
			return export, true
		}
	}
	return ExportMeta{}, false
}

// --------------------------------------------------------------------------- 类型助手

func toInt(value interface{}) (int, error) {
	switch v := value.(type) {
	case int:
		return v, nil
	case int64:
		return int(v), nil
	case float64:
		return int(v), nil
	case string:
		var result int
		if _, err := fmt.Sscanf(v, "%d", &result); err == nil {
			return result, nil
		}
	}
	return 0, errors.New("not an int")
}

func asInt(value interface{}) (int, bool) {
	if value == nil {
		return 0, false
	}
	result, err := toInt(value)
	return result, err == nil
}

func asString(value interface{}) (string, bool) {
	text, ok := value.(string)
	return text, ok
}

func asStringAny(value interface{}) string {
	if text, ok := value.(string); ok {
		return text
	}
	return ""
}

func asMap(value interface{}) (map[string]interface{}, bool) {
	result, ok := value.(map[string]interface{})
	return result, ok
}

func asMapList(value interface{}) ([]map[string]interface{}, bool) {
	list, ok := value.([]interface{})
	if !ok {
		return nil, false
	}
	result := make([]map[string]interface{}, 0, len(list))
	for _, item := range list {
		entry, ok := item.(map[string]interface{})
		if !ok {
			return nil, false
		}
		result = append(result, entry)
	}
	return result, true
}

func asStringList(value interface{}) ([]string, bool) {
	list, ok := value.([]interface{})
	if !ok {
		return nil, false
	}
	result := make([]string, 0, len(list))
	for _, item := range list {
		text, ok := item.(string)
		if !ok {
			return nil, false
		}
		result = append(result, text)
	}
	return result, true
}

func containsString(values []string, expected string) bool {
	for _, value := range values {
		if value == expected {
			return true
		}
	}
	return false
}
