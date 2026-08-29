package adapter

import (
	"testing"

	"privateclouddisk/cloudflow-mcp-server/internal/model"
)

func TestToToolsUsesReviewedBindingsAndRemovesInternalSchemaFields(t *testing.T) {
	rows := []model.CapabilityRow{
		{CapabilityKey: "api:file.list", Status: "ACTIVE", Description: "list", InputSchemaJSON: `{"type":"object","required":["space_id","keyword"],"properties":{"space_id":{"type":"string"},"keyword":{"type":"string"}}}`},
		{CapabilityKey: "api:share.create", Status: "ACTIVE", Description: "must not export", InputSchemaJSON: `{"type":"object"}`},
	}
	tools := ToTools(rows)
	if len(tools) != 1 || tools[0].Name != "cloudflow.file.list" {
		t.Fatalf("unexpected exported tools: %#v", tools)
	}
	properties := tools[0].InputSchema["properties"].(map[string]any)
	if _, exists := properties["space_id"]; exists {
		t.Fatal("server-managed space_id must not be exposed to Agent")
	}
	if got := tools[0].InputSchema["required"].([]any); len(got) != 1 || got[0] != "keyword" {
		t.Fatalf("protected required fields were not removed: %#v", got)
	}
}

func TestSanitizeArgumentsDropsContextFields(t *testing.T) {
	arguments := SanitizeArguments(map[string]any{
		"keyword": "roadmap", "space_id": "forged", "tenantId": "forged", "trace-id": "forged",
	})
	if len(arguments) != 1 || arguments["keyword"] != "roadmap" {
		t.Fatalf("unexpected sanitized arguments: %#v", arguments)
	}
}
