-- [AI-AGENT-CAPABILITY-001] Cloud AI Agent never calls WorkflowController or
-- CloudFlow Runtime directly. These registrations make the existing Capability Hub
-- the single policy/audit boundary for read, validate, execute and status operations.
-- The invoker is in-process and delegates to WorkflowService, which retains its
-- per-user/per-space ownership and publish-state checks.
INSERT INTO pcd_capability_registry (
    capability_key, source_type, source_id, display_name, description,
    input_schema_json, output_schema_json, required_permissions_json,
    availability_policy_json, status
) VALUES
('api:workflow.list', 'API', 'workflow', '列出工作流', '返回当前用户在当前空间可访问的工作流，Capability Hub 与 WorkflowService 双重校验范围',
 '{"type":"object","properties":{"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":100}}}',
 '{"type":"object","properties":{"items":{"type":"array"}}}',
 '["workflow.read"]', '{"timeout_seconds":10,"max_concurrency":30,"idempotent":true}', 'ACTIVE'),
('api:workflow.validate', 'API', 'workflow', '校验 CloudFlow DSL', '调用当前 Rust CloudFlow Runtime 编译校验 .flow 源码；不保存、不发布、不执行',
 '{"type":"object","required":["dsl"],"properties":{"dsl":{"type":"string","minLength":1,"maxLength":1048576}}}',
 '{"type":"object","properties":{"valid":{"type":"boolean"},"diagnostics":{"type":"array"}}}',
 '["workflow.write"]', '{"timeout_seconds":30,"max_concurrency":10,"idempotent":true}', 'ACTIVE'),
('api:workflow.execute', 'API', 'workflow', '执行已发布工作流', '仅执行用户有管理权限且已发布的不可变工作流版本；上游 Agent 还必须经过审批门禁',
 '{"type":"object","required":["workflow_id"],"properties":{"workflow_id":{"type":"string","maxLength":128},"inputs":{"type":"object"},"version":{"type":"integer","minimum":1}}}',
 '{"type":"object","properties":{"execution_id":{"type":"string"},"status":{"type":"string"}}}',
 '["workflow.execute"]', '{"timeout_seconds":15,"max_concurrency":10,"idempotent":false}', 'ACTIVE'),
('api:workflow.status', 'API', 'workflow', '查询工作流执行状态', '查询当前用户拥有的工作流执行状态和脱敏摘要',
 '{"type":"object","required":["execution_id"],"properties":{"execution_id":{"type":"string","maxLength":128}}}',
 '{"type":"object","properties":{"execution_id":{"type":"string"},"status":{"type":"string"}}}',
 '["workflow.read"]', '{"timeout_seconds":10,"max_concurrency":50,"idempotent":true}', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    display_name=VALUES(display_name), description=VALUES(description),
    input_schema_json=VALUES(input_schema_json), output_schema_json=VALUES(output_schema_json),
    required_permissions_json=VALUES(required_permissions_json),
    availability_policy_json=VALUES(availability_policy_json), status=VALUES(status);
