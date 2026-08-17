-- [CLOUDFLOW-DSL-001] Workflow DSL 从 automation.pcd/v1 YAML 切换为 CloudFlow 自定义语言。
-- 历史版本保留用于审计，但不可再次发布或执行；避免静默删除用户的版本历史。
ALTER TABLE pcd_workflow_version
    MODIFY schema_version VARCHAR(64) NOT NULL DEFAULT 'workflow.cloudflow.io/v1';

UPDATE pcd_workflow_version
   SET validation_report_json = JSON_OBJECT(
           'valid', false,
           'issues', JSON_ARRAY(JSON_OBJECT(
               'code', 'CF-LEGACY-YAML-UNSUPPORTED',
               'path', '$',
               'message', '历史 YAML DSL 已停止支持，请使用 CloudFlow DSL 重新保存'
           )),
           'normalized', JSON_OBJECT(),
           'sha256', ''
       ),
       schema_version = 'workflow.cloudflow.io/v1'
 WHERE schema_version = 'automation.pcd/v1';
