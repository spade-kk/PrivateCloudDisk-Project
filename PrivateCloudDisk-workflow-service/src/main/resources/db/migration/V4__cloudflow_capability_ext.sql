-- [CAPABILITY-HUB-EXT-001] 扩展平台 API 能力注册表 + 能力调用审计台账（需求四 4.3-4.13 / 五 5.16-5.17）。
-- 新增 11 个平台 API 能力：文件类（metadata/scan/content/list/search/tag）、空间类（info/members）、
-- 用户/通知/分享类（user.info/notification.send/share.create）。
-- source_id 为目标数据面服务名（platform/storage），由 Capability Hub 按配置解析为基址（4.22 服务发现与配置），
-- 目标地址不来自调用方，避免 SSRF（5.21）。
CREATE TABLE pcd_capability_audit (
    audit_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    capability_key VARCHAR(255) NOT NULL,
    caller_service VARCHAR(80) NOT NULL,
    execution_id BINARY(16) NULL,
    step_id VARCHAR(128) NULL,
    user_id BINARY(16) NULL,
    space_id BINARY(16) NULL,
    trace_id VARCHAR(64) NULL,
    param_summary_json JSON NULL,
    success TINYINT(1) NOT NULL,
    result_code VARCHAR(64) NULL,
    target_service VARCHAR(80) NULL,
    duration_ms BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (audit_id),
    KEY idx_capability_audit_key_time (capability_key, created_at),
    KEY idx_capability_audit_user_time (user_id, created_at),
    KEY idx_capability_audit_result_time (success, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO pcd_capability_registry (
    capability_key, source_type, source_id, display_name, description,
    input_schema_json, output_schema_json, required_permissions_json,
    availability_policy_json, status
) VALUES
('api:file.metadata.get', 'API', 'platform', '获取文件元数据', '获取文件名称、大小、类型、创建时间等元数据；下游 Platform 数据面校验空间读取权限与资源归属',
 '{"type":"object","required":["file_id"],"properties":{"file_id":{"type":"string","pattern":"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$","maxLength":36},"space_id":{"type":"string","maxLength":128}}}',
 '{"type":"object","properties":{"id":{"type":"string"},"name":{"type":"string"},"type":{"type":"string"},"size":{"type":"integer"},"uploaded_time":{"type":"string"},"node_id":{"type":"string"},"space_id":{"type":"string"}}}',
 '["file.read"]', '{"timeout_seconds":10,"max_concurrency":50,"circuit_breaker":"platform","idempotent":true}', 'ACTIVE'),

('api:file.scan', 'API', 'platform', '触发文件安全扫描', '异步触发文件安全扫描，返回任务 ID；仅允许对当前用户有读取权限的文件发起，防止扫描任意文件',
 '{"type":"object","required":["file_id"],"properties":{"file_id":{"type":"string","pattern":"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$","maxLength":36},"reason":{"type":"string","maxLength":500}}}',
 '{"type":"object","required":["task_id","status"],"properties":{"task_id":{"type":"string"},"status":{"type":"string"}}}',
 '["file.read"]', '{"timeout_seconds":5,"max_concurrency":20,"circuit_breaker":"platform","idempotent":false}', 'ACTIVE'),

('api:file.content.get', 'API', 'storage', '获取文件文本内容', '获取文本/代码/Markdown 等可预览类型的文件内容，限制大小；二进制或超长文件直接拒绝',
 '{"type":"object","required":["file_id"],"properties":{"file_id":{"type":"string","pattern":"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$","maxLength":36},"max_bytes":{"type":"integer","minimum":1024,"maximum":1048576},"text_only":{"type":"boolean"}}}',
 '{"type":"object","properties":{"file_id":{"type":"string"},"file_name":{"type":"string"},"file_type":{"type":"string"},"size":{"type":"integer"},"content":{"type":"string"},"truncated":{"type":"boolean"}}}',
 '["file.read"]', '{"timeout_seconds":20,"max_concurrency":20,"circuit_breaker":"platform","idempotent":true}', 'ACTIVE'),

('api:file.list', 'API', 'platform', '文件列表', '获取指定空间/目录下的文件列表，仅返回当前用户有读取权限的节点',
 '{"type":"object","required":[],"properties":{"space_id":{"type":"string","maxLength":128},"parent_id":{"type":"string","maxLength":128},"keyword":{"type":"string","maxLength":200},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":200}}}',
 '{"type":"object","properties":{"total":{"type":"integer"},"page":{"type":"integer"},"page_size":{"type":"integer"},"items":{"type":"array"}}}',
 '["file.read"]', '{"timeout_seconds":10,"max_concurrency":50,"circuit_breaker":"platform","idempotent":true}', 'ACTIVE'),

('api:file.search', 'API', 'platform', '文件搜索', '按关键词搜索文件元数据，仅返回当前用户有权限的文件',
 '{"type":"object","required":["keyword"],"properties":{"keyword":{"type":"string","maxLength":200,"minLength":1},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":100}}}',
 '{"type":"object","properties":{"total":{"type":"integer"},"hits":{"type":"array"},"search_after":{"type":"string"}}}',
 '["file.read"]', '{"timeout_seconds":10,"max_concurrency":30,"circuit_breaker":"platform","idempotent":true}', 'ACTIVE'),

('api:space.info', 'API', 'platform', '空间信息', '获取空间基本信息（名称、类型、配额等），需空间读取权限',
 '{"type":"object","required":["space_id"],"properties":{"space_id":{"type":"string","maxLength":128}}}',
 '{"type":"object","properties":{"space_id":{"type":"string"},"space_name":{"type":"string"},"space_type":{"type":"string"},"space_owner_id":{"type":"string"},"space_quota":{"type":"integer"},"space_used":{"type":"integer"},"space_file_count":{"type":"integer"},"space_visibility":{"type":"string"}}}',
 '["space.read"]', '{"timeout_seconds":10,"max_concurrency":50,"circuit_breaker":"platform","idempotent":true}', 'ACTIVE'),

('api:space.members.list', 'API', 'platform', '空间成员列表', '获取空间成员列表（需空间权限）；仅返回成员身份与角色，不返回敏感资料',
 '{"type":"object","required":["space_id"],"properties":{"space_id":{"type":"string","maxLength":128},"keyword":{"type":"string","maxLength":200},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":200}}}',
 '{"type":"object","properties":{"total":{"type":"integer"},"items":{"type":"array"}}}',
 '["space.read"]', '{"timeout_seconds":10,"max_concurrency":30,"circuit_breaker":"platform","idempotent":true}', 'ACTIVE'),

('api:user.info', 'API', 'platform', '用户基本信息', '获取当前用户或指定用户脱敏后的基本信息，不暴露手机号、邮箱等敏感字段',
 '{"type":"object","required":[],"properties":{"user_id":{"type":"string","maxLength":128}}}',
 '{"type":"object","properties":{"user_id":{"type":"string"},"username":{"type":"string"},"account":{"type":"string"},"avatar_path":{"type":"string"}}}',
 '["user.profile.read"]', '{"timeout_seconds":10,"max_concurrency":60,"circuit_breaker":"platform","idempotent":true}', 'ACTIVE'),

('api:notification.send', 'API', 'platform', '发送站内通知', '通过平台通知能力发送站内通知；发送者必须拥有通知发送权限，接收范围受控',
 '{"type":"object","required":["title","body"],"properties":{"title":{"type":"string","maxLength":120},"body":{"type":"string","maxLength":2000},"user_id":{"type":"string","maxLength":128},"channel":{"type":"string","enum":["inbox","push","email","all"]}}}',
 '{"type":"object","properties":{"accepted":{"type":"boolean"},"event_id":{"type":"string"}}}',
 '["notification.send"]', '{"timeout_seconds":5,"max_concurrency":100,"circuit_breaker":"platform","idempotent":true}', 'ACTIVE'),

('api:tag.list', 'API', 'platform', '文件标签列表', '获取目标文件的标签列表，需文件读取权限',
 '{"type":"object","required":["file_id"],"properties":{"file_id":{"type":"string","pattern":"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$","maxLength":36}}}',
 '{"type":"object","properties":{"items":{"type":"array"}}}',
 '["file.read"]', '{"timeout_seconds":10,"max_concurrency":50,"circuit_breaker":"platform","idempotent":true}', 'ACTIVE'),

('api:share.create', 'API', 'platform', '创建分享链接', '创建分享链接（需校验分享资源权限与分享策略），仅允许分享当前用户有权限的资源',
 '{"type":"object","required":["resources","share_name"],"properties":{"share_name":{"type":"string","maxLength":200,"minLength":1},"share_description":{"type":"string","maxLength":10000},"resources":{"type":"array","maxItems":50,"items":{"type":"object","required":["type","id"],"properties":{"type":{"type":"string","enum":["file","folder"]},"id":{"type":"string","maxLength":128}}}},"password":{"type":"string","pattern":"^[A-Za-z0-9]{4,20}$"},"expires_in_days":{"type":"integer","minimum":0},"allow_download":{"type":"boolean"}}}',
 '{"type":"object","properties":{"share_id":{"type":"string"},"share_token":{"type":"string"},"share_url":{"type":"string"},"share_name":{"type":"string"},"share_has_password":{"type":"boolean"},"share_expires_at":{"type":"string"},"resource_count":{"type":"integer"}}}',
 '["file.share"]', '{"timeout_seconds":15,"max_concurrency":30,"circuit_breaker":"platform","idempotent":false}', 'ACTIVE');
