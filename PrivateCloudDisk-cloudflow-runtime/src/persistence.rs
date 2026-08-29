//! CloudFlow Runtime MySQL 事实源。
//!
//! [CLOUDFLOW-RUNTIME-STATE-001] 所有执行状态、步骤检查点、Inbox 与 Outbox 都在同一数据库中；
//! HTTP/MQ 只负责传输，进程内缓存不能决定幂等性或恢复结果。

use crate::ir::WorkflowIrV1;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha2::{Digest, Sha256};
use sqlx::{mysql::MySqlPoolOptions, types::Json, MySql, MySqlPool, Row, Transaction};
use std::time::Duration;
use uuid::Uuid;

/// [CLOUDFLOW-MQ-006] Inbox 领取租约。进程在写入 PROCESSING 后崩溃时，
/// RabbitMQ 重新投递的同一 event_id 不能永久被幂等记录吞掉；超过租约才允许另一实例接管。
const INBOX_LEASE_SECONDS: u64 = 300;
/// Outbox 发布长期失败时进入数据库终态，交由告警/人工重放处理，避免无限重试掩盖 Broker 故障。
const MAX_OUTBOX_ATTEMPTS: u32 = 10;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CreateExecution {
    pub execution_id: String,
    pub workflow_id: String,
    pub user_id: String,
    pub space_id: Option<String>,
    pub ir: WorkflowIrV1,
    pub variables: Value,
    pub declared_permissions: Vec<String>,
    pub granted_permissions: Vec<String>,
    pub trace_id: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StoredExecution {
    pub execution_id: String,
    pub workflow_id: String,
    pub user_id: String,
    pub space_id: Option<String>,
    pub plan_hash: String,
    pub status: String,
    /// 与 V1 `status=WAITING` 兼容的细粒度执行态，供 API/IDE 显示审批等待而不破坏旧数据库约束。
    pub sub_status: Option<String>,
    pub current_step: Option<String>,
    pub ir: WorkflowIrV1,
    pub variables: Value,
    pub outputs: Value,
    pub declared_permissions: Vec<String>,
    pub granted_permissions: Vec<String>,
    pub trace_id: String,
    pub error_code: Option<String>,
    pub error_summary: Option<String>,
    pub cancel_requested: bool,
    pub pause_requested: bool,
    pub row_version: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StoredLog {
    pub sequence: u64,
    pub level: String,
    pub message: String,
}

#[derive(Debug, Clone)]
pub struct OutboxRecord {
    pub event_id: String,
    pub aggregate_id: String,
    pub event_type: String,
    pub routing_key: String,
    pub payload: Value,
    pub attempts: u32,
}

pub struct StepFailure<'a> {
    pub execution_id: &'a str,
    pub step_id: &'a str,
    pub attempt: u32,
    pub code: &'a str,
    pub summary: &'a str,
    pub retryable: bool,
    pub duration_ms: u64,
}

#[derive(Clone)]
pub struct RuntimeStore {
    pool: MySqlPool,
}

impl RuntimeStore {
    pub async fn connect(database_url: &str, max_connections: u32) -> Result<Self, sqlx::Error> {
        let pool = MySqlPoolOptions::new()
            .max_connections(max_connections.max(2))
            .acquire_timeout(Duration::from_secs(10))
            .connect(database_url)
            .await?;
        Ok(Self { pool })
    }

    pub async fn migrate(&self) -> Result<(), sqlx::migrate::MigrateError> {
        sqlx::migrate!("./migrations").run(&self.pool).await
    }

    pub async fn ping(&self) -> Result<(), sqlx::Error> {
        sqlx::query("SELECT 1").execute(&self.pool).await?;
        Ok(())
    }

    pub async fn create_execution(&self, command: &CreateExecution) -> Result<bool, sqlx::Error> {
        let mut transaction = self.pool.begin().await?;
        let ir_json = serde_json::to_value(&command.ir).unwrap_or(Value::Null);
        let plan_hash = sha256_json(&ir_json);
        let inserted = sqlx::query(
            r#"INSERT IGNORE INTO cloudflow_execution(
                   execution_id, workflow_id, user_id, space_id, plan_hash, status,
                   ir_json, variables_json, outputs_json,
                   declared_permissions_json, granted_permissions_json, trace_id
               ) VALUES (?, ?, ?, ?, ?, 'READY', ?, ?, JSON_OBJECT(), ?, ?, ?)"#,
        )
        .bind(&command.execution_id)
        .bind(&command.workflow_id)
        .bind(&command.user_id)
        .bind(command.space_id.as_deref())
        .bind(plan_hash)
        .bind(Json(ir_json))
        .bind(Json(command.variables.clone()))
        .bind(Json(command.declared_permissions.clone()))
        .bind(Json(command.granted_permissions.clone()))
        .bind(&command.trace_id)
        .execute(&mut *transaction)
        .await?
        .rows_affected()
            == 1;
        if !inserted {
            transaction.rollback().await?;
            return Ok(false);
        }
        append_log_tx(
            &mut transaction,
            &command.execution_id,
            "INFO",
            "Workflow IR 已持久化，执行实例进入 READY",
        )
        .await?;
        enqueue_outbox_tx(
            &mut transaction,
            &command.execution_id,
            "cloudflow.execution.accepted.v1",
            "cloudflow.execution.accepted",
            serde_json::json!({
                "executionId": command.execution_id,
                "workflowId": command.workflow_id,
                "userId": command.user_id,
                "spaceId": command.space_id,
                "status": "READY",
                "traceId": command.trace_id
            }),
        )
        .await?;
        transaction.commit().await?;
        Ok(true)
    }

    pub async fn get_execution(
        &self,
        execution_id: &str,
    ) -> Result<Option<StoredExecution>, sqlx::Error> {
        let row = sqlx::query(
            r#"SELECT execution_id, workflow_id, user_id, space_id, plan_hash, status,
                      current_step, ir_json, variables_json, outputs_json,
                      declared_permissions_json, granted_permissions_json, trace_id,
                      error_code, error_summary, cancel_requested, pause_requested, row_version
                 FROM cloudflow_execution WHERE execution_id=?"#,
        )
        .bind(execution_id)
        .fetch_optional(&self.pool)
        .await?;
        row.map(map_execution).transpose()
    }

    pub async fn claim_next(&self) -> Result<Option<StoredExecution>, sqlx::Error> {
        let mut transaction = self.pool.begin().await?;
        let id = sqlx::query(
            r#"SELECT execution_id FROM cloudflow_execution
                WHERE status='READY' AND cancel_requested=0 AND pause_requested=0
                ORDER BY created_at, execution_id
                LIMIT 1 FOR UPDATE SKIP LOCKED"#,
        )
        .fetch_optional(&mut *transaction)
        .await?
        .map(|row| row.get::<String, _>("execution_id"));
        let Some(execution_id) = id else {
            transaction.rollback().await?;
            return Ok(None);
        };
        sqlx::query(
            r#"UPDATE cloudflow_execution
                  SET status='RUNNING', started_at=COALESCE(started_at, CURRENT_TIMESTAMP(3)),
                      heartbeat_at=CURRENT_TIMESTAMP(3), row_version=row_version+1
                WHERE execution_id=? AND status='READY'"#,
        )
        .bind(&execution_id)
        .execute(&mut *transaction)
        .await?;
        append_log_tx(
            &mut transaction,
            &execution_id,
            "INFO",
            "执行 Worker 已领取实例",
        )
        .await?;
        transaction.commit().await?;
        self.get_execution(&execution_id).await
    }

    pub async fn recover_stale(&self, stale_seconds: u64) -> Result<u64, sqlx::Error> {
        let seconds = stale_seconds.min(i32::MAX as u64) as i32;
        let affected = sqlx::query(
            r#"UPDATE cloudflow_execution
                  SET status='READY', heartbeat_at=NULL, current_step=NULL,
                      row_version=row_version+1, error_code='CF-RUNTIME-RECOVERED',
                      error_summary='检测到失联 Worker，已从持久化检查点恢复'
                WHERE status='RUNNING' AND cancel_requested=0 AND pause_requested=0
                  AND heartbeat_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL ? SECOND)"#,
        )
        .bind(seconds)
        .execute(&self.pool)
        .await?
        .rows_affected();
        sqlx::query(
            r#"UPDATE cloudflow_step_execution s
                  JOIN cloudflow_execution e ON e.execution_id=s.execution_id
                  SET s.status='RETRYING', s.error_code='CF-RUNTIME-RECOVERED',
                      s.error_summary='Worker 失联，等待下一次 attempt', s.ended_at=CURRENT_TIMESTAMP(3)
                WHERE e.status='READY' AND s.status='RUNNING'"#,
        )
        .execute(&self.pool)
        .await?;
        Ok(affected)
    }

    pub async fn heartbeat(
        &self,
        execution_id: &str,
        step_id: Option<&str>,
    ) -> Result<bool, sqlx::Error> {
        Ok(sqlx::query(
            r#"UPDATE cloudflow_execution SET heartbeat_at=CURRENT_TIMESTAMP(3), current_step=?
                WHERE execution_id=? AND status='RUNNING'"#,
        )
        .bind(step_id)
        .bind(execution_id)
        .execute(&self.pool)
        .await?
        .rows_affected()
            == 1)
    }

    pub async fn completed_steps(&self, execution_id: &str) -> Result<Vec<String>, sqlx::Error> {
        let rows = sqlx::query(
            "SELECT DISTINCT step_id FROM cloudflow_step_execution WHERE execution_id=? AND status IN ('SUCCESS','SKIPPED')",
        )
        .bind(execution_id)
        .fetch_all(&self.pool)
        .await?;
        Ok(rows.into_iter().map(|row| row.get("step_id")).collect())
    }

    pub async fn begin_step(
        &self,
        execution_id: &str,
        step_id: &str,
        input: &Value,
    ) -> Result<u32, sqlx::Error> {
        let mut transaction = self.pool.begin().await?;
        sqlx::query("SELECT execution_id FROM cloudflow_execution WHERE execution_id=? FOR UPDATE")
            .bind(execution_id)
            .fetch_one(&mut *transaction)
            .await?;
        let attempt = sqlx::query(
            "SELECT COALESCE(MAX(attempt), 0) + 1 next_attempt FROM cloudflow_step_execution WHERE execution_id=? AND step_id=?",
        )
        .bind(execution_id)
        .bind(step_id)
        .fetch_one(&mut *transaction)
        .await?
        .get::<u32, _>("next_attempt");
        sqlx::query(
            r#"INSERT INTO cloudflow_step_execution(
                   execution_id, step_id, attempt, status, input_summary_json
               ) VALUES (?, ?, ?, 'RUNNING', ?)"#,
        )
        .bind(execution_id)
        .bind(step_id)
        .bind(attempt)
        .bind(Json(input.clone()))
        .execute(&mut *transaction)
        .await?;
        sqlx::query(
            "UPDATE cloudflow_execution SET current_step=?, heartbeat_at=CURRENT_TIMESTAMP(3), row_version=row_version+1 WHERE execution_id=? AND status='RUNNING'",
        )
        .bind(step_id)
        .bind(execution_id)
        .execute(&mut *transaction)
        .await?;
        transaction.commit().await?;
        Ok(attempt)
    }

    pub async fn finish_step_success(
        &self,
        execution_id: &str,
        step_id: &str,
        attempt: u32,
        output: &Value,
        duration_ms: u64,
    ) -> Result<(), sqlx::Error> {
        let mut transaction = self.pool.begin().await?;
        sqlx::query(
            r#"UPDATE cloudflow_step_execution
                  SET status='SUCCESS', output_summary_json=?, duration_ms=?, ended_at=CURRENT_TIMESTAMP(3)
                WHERE execution_id=? AND step_id=? AND attempt=? AND status='RUNNING'"#,
        )
        .bind(Json(output.clone()))
        .bind(duration_ms)
        .bind(execution_id)
        .bind(step_id)
        .bind(attempt)
        .execute(&mut *transaction)
        .await?;
        sqlx::query(
            r#"UPDATE cloudflow_execution
                  -- [CLOUDFLOW-RUNTIME-CONTROL-002] 动态实例 ID 可能含 [] 和 .；JSON Path
                  -- 必须使用 quoted member，而不是旧的 `$.<step_id>` 拼接，否则既无法保存
                  -- foreach 输出，也会让带 '-' 的合法静态步骤 ID 产生歧义。
                  SET outputs_json=JSON_SET(
                          outputs_json,
                          CONCAT('$."', REPLACE(REPLACE(?, '\\', '\\\\'), '"', '\\"'), '"'),
                          CAST(? AS JSON)
                      ),
                      heartbeat_at=CURRENT_TIMESTAMP(3), row_version=row_version+1
                WHERE execution_id=? AND status='RUNNING'"#,
        )
        .bind(step_id)
        .bind(serde_json::to_string(output).unwrap_or_else(|_| "null".into()))
        .bind(execution_id)
        .execute(&mut *transaction)
        .await?;
        transaction.commit().await?;
        Ok(())
    }

    pub async fn finish_step_failure(&self, failure: StepFailure<'_>) -> Result<(), sqlx::Error> {
        sqlx::query(
            r#"UPDATE cloudflow_step_execution
                  SET status=?, error_code=?, error_summary=?, retryable=?, duration_ms=?, ended_at=CURRENT_TIMESTAMP(3)
                WHERE execution_id=? AND step_id=? AND attempt=? AND status='RUNNING'"#,
        )
        .bind(if failure.retryable { "RETRYING" } else { "FAILED" })
        .bind(failure.code)
        .bind(truncate(failure.summary, 2000))
        .bind(failure.retryable)
        .bind(failure.duration_ms)
        .bind(failure.execution_id)
        .bind(failure.step_id)
        .bind(failure.attempt)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    /// 将因条件/循环分支未选中而跳过的节点写入持久化检查点，避免重启后再次执行。
    pub async fn finish_step_skipped(
        &self,
        execution_id: &str,
        step_id: &str,
        reason: &str,
    ) -> Result<(), sqlx::Error> {
        let attempt = self.begin_step(execution_id, step_id, &Value::Null).await?;
        sqlx::query(
            r#"UPDATE cloudflow_step_execution
                  SET status='SKIPPED', output_summary_json=?, error_summary=?, ended_at=CURRENT_TIMESTAMP(3), duration_ms=0
                WHERE execution_id=? AND step_id=? AND attempt=? AND status='RUNNING'"#,
        )
        .bind(Json(serde_json::json!({"skipped": true})))
        .bind(truncate(reason, 2000))
        .bind(execution_id)
        .bind(step_id)
        .bind(attempt)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    pub async fn finish_execution(
        &self,
        execution_id: &str,
        status: &str,
        code: Option<&str>,
        summary: Option<&str>,
    ) -> Result<(), sqlx::Error> {
        let mut transaction = self.pool.begin().await?;
        sqlx::query(
            r#"UPDATE cloudflow_execution SET status=?, current_step=NULL, heartbeat_at=NULL,
                      ended_at=CURRENT_TIMESTAMP(3), error_code=?, error_summary=?, row_version=row_version+1
                WHERE execution_id=? AND status IN ('READY','RUNNING','WAITING')"#,
        )
        .bind(status)
        .bind(code)
        .bind(summary.map(|value| truncate(value, 2000)))
        .bind(execution_id)
        .execute(&mut *transaction)
        .await?;
        append_log_tx(
            &mut transaction,
            execution_id,
            if status == "SUCCESS" { "INFO" } else { "ERROR" },
            summary.unwrap_or(status),
        )
        .await?;
        enqueue_outbox_tx(
            &mut transaction,
            execution_id,
            "cloudflow.execution.completed.v1",
            "cloudflow.execution.completed",
            serde_json::json!({
                "executionId": execution_id,
                "status": status,
                "errorCode": code,
                "errorSummary": summary
            }),
        )
        .await?;
        transaction.commit().await?;
        Ok(())
    }

    pub async fn request_cancel(&self, execution_id: &str) -> Result<bool, sqlx::Error> {
        Ok(sqlx::query(
            r#"UPDATE cloudflow_execution SET cancel_requested=1, row_version=row_version+1
                WHERE execution_id=? AND status IN ('READY','RUNNING','WAITING')"#,
        )
        .bind(execution_id)
        .execute(&self.pool)
        .await?
        .rows_affected()
            == 1)
    }

    pub async fn request_pause(&self, execution_id: &str) -> Result<bool, sqlx::Error> {
        Ok(sqlx::query(
            r#"UPDATE cloudflow_execution SET pause_requested=1, status='WAITING', row_version=row_version+1
                WHERE execution_id=? AND status IN ('READY','RUNNING')"#,
        )
        .bind(execution_id)
        .execute(&self.pool)
        .await?
        .rows_affected()
            == 1)
    }

    pub async fn retry_execution(&self, execution_id: &str) -> Result<bool, sqlx::Error> {
        Ok(sqlx::query(
            r#"UPDATE cloudflow_execution
                  SET status='READY', cancel_requested=0, pause_requested=0, current_step=NULL,
                      error_code=NULL, error_summary=NULL, ended_at=NULL, row_version=row_version+1
                WHERE execution_id=? AND status IN ('FAILED','CANCELLED','WAITING')"#,
        )
        .bind(execution_id)
        .execute(&self.pool)
        .await?
        .rows_affected()
            == 1)
    }

    /// [CLOUDFLOW-WAIT-001] 恢复人工等待必须是持久化状态迁移，而不是向内存 Worker
    /// 发送信号。审批值写入受限 `vars.__wait.<wait-node-id>` 命名空间，恢复后 Worker
    /// 从已成功的 wait 检查点继续，避免重复等待或重复执行前置节点。
    pub async fn resume_execution(
        &self,
        execution_id: &str,
        approval: &Value,
    ) -> Result<bool, sqlx::Error> {
        let approval_text = serde_json::to_string(approval).unwrap_or_else(|_| "null".into());
        let affected = sqlx::query(
            r#"UPDATE cloudflow_execution
                  SET status='READY', pause_requested=0, error_code=NULL, error_summary=NULL,
                      variables_json=JSON_SET(
                          variables_json,
                          CONCAT('$.__wait.', REPLACE(COALESCE(current_step, 'unknown'), '-', '_')),
                          CAST(? AS JSON)
                      ),
                      heartbeat_at=NULL, row_version=row_version+1
                WHERE execution_id=? AND status='WAITING' AND cancel_requested=0"#,
        )
        .bind(approval_text)
        .bind(execution_id)
        .execute(&self.pool)
        .await?
        .rows_affected();
        if affected == 1 {
            let mut transaction = self.pool.begin().await?;
            append_log_tx(
                &mut transaction,
                execution_id,
                "INFO",
                "已收到外部恢复信号，实例从 WAITING_APPROVAL 返回 READY",
            )
            .await?;
            transaction.commit().await?;
        }
        Ok(affected == 1)
    }

    pub async fn logs(
        &self,
        execution_id: &str,
        limit: u32,
    ) -> Result<Vec<StoredLog>, sqlx::Error> {
        let rows = sqlx::query(
            r#"SELECT sequence_no, level, message FROM cloudflow_execution_log
                WHERE execution_id=? ORDER BY sequence_no LIMIT ?"#,
        )
        .bind(execution_id)
        .bind(limit.min(1000))
        .fetch_all(&self.pool)
        .await?;
        Ok(rows
            .into_iter()
            .map(|row| StoredLog {
                sequence: row.get("sequence_no"),
                level: row.get("level"),
                message: row.get("message"),
            })
            .collect())
    }

    pub async fn claim_inbox(
        &self,
        event_id: &str,
        event_type: &str,
        payload: &Value,
        retry_count: u32,
    ) -> Result<bool, sqlx::Error> {
        let payload_text = serde_json::to_string(payload).unwrap_or_else(|_| "null".into());
        let payload_hash = sha256_bytes(payload_text.as_bytes());
        let mut transaction = self.pool.begin().await?;
        let existing = sqlx::query(
            "SELECT status, payload_sha256, retry_count FROM cloudflow_inbox WHERE event_id=? FOR UPDATE",
        )
        .bind(event_id)
        .fetch_optional(&mut *transaction)
        .await?;
        let claimed = if let Some(row) = existing {
            let stored_hash: String = row.get("payload_sha256");
            let stored_retry_count: u32 = row.get("retry_count");
            if stored_hash != payload_hash {
                return Err(sqlx::Error::Protocol(format!(
                    "event_id {event_id} 的 payload hash 与历史记录不一致"
                )));
            }
            let status: String = row.get("status");
            match status.as_str() {
                "FAILED" | "RECEIVED" => {
                    // 兼容旧版本留下的 RECEIVED 记录；新行为将领取态显式记录为
                    // PROCESSING，避免进程崩溃后永久跳过消息。
                    sqlx::query(
                        r#"UPDATE cloudflow_inbox SET status='PROCESSING', retry_count=?,
                                  error_summary=NULL, processed_at=NULL,
                                  received_at=CURRENT_TIMESTAMP(3)
                            WHERE event_id=? AND status IN ('FAILED','RECEIVED')"#,
                    )
                    .bind(retry_count)
                    .bind(event_id)
                    .execute(&mut *transaction)
                    .await?;
                    true
                }
                "PROCESSING" => {
                    // 只有处理租约过期才允许接管；同一消息在正常处理窗口内重复投递
                    // 仍直接 ACK，保证不会并发执行同一个工作流命令。
                    sqlx::query(
                        r#"UPDATE cloudflow_inbox SET retry_count=?, error_summary=NULL,
                                  processed_at=NULL, received_at=CURRENT_TIMESTAMP(3)
                            WHERE event_id=? AND status='PROCESSING'
                              AND (retry_count < ? OR received_at < DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL ? SECOND))"#,
                    )
                    .bind(retry_count)
                    .bind(event_id)
                    .bind(retry_count.max(stored_retry_count))
                    .bind(INBOX_LEASE_SECONDS)
                    .execute(&mut *transaction)
                    .await?
                    .rows_affected()
                        == 1
                }
                // PROCESSED 以及未知状态都不能再次执行，避免状态损坏时产生副作用。
                _ => false,
            }
        } else {
            sqlx::query(
                r#"INSERT INTO cloudflow_inbox(
                       event_id, event_type, payload_sha256, payload_json, status, retry_count
                   ) VALUES (?, ?, ?, CAST(? AS JSON), 'PROCESSING', ?)"#,
            )
            .bind(event_id)
            .bind(event_type)
            .bind(payload_hash)
            .bind(payload_text)
            .bind(retry_count)
            .execute(&mut *transaction)
            .await?;
            true
        };
        transaction.commit().await?;
        Ok(claimed)
    }

    pub async fn finish_inbox(
        &self,
        event_id: &str,
        error: Option<&str>,
    ) -> Result<(), sqlx::Error> {
        sqlx::query(
            r#"UPDATE cloudflow_inbox SET status=?, error_summary=?, processed_at=CURRENT_TIMESTAMP(3)
                WHERE event_id=?"#,
        )
        .bind(if error.is_some() { "FAILED" } else { "PROCESSED" })
        .bind(error.map(|value| truncate(value, 2000)))
        .bind(event_id)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    pub async fn claim_outbox_batch(&self, limit: u32) -> Result<Vec<OutboxRecord>, sqlx::Error> {
        let mut transaction = self.pool.begin().await?;
        // 发布进程异常退出后的 PUBLISHING 记录可被重新领取；RabbitMQ 下游必须以 event_id 幂等。
        sqlx::query(
            r#"UPDATE cloudflow_outbox SET status='PENDING'
                WHERE status='PUBLISHING' AND next_retry_at <= CURRENT_TIMESTAMP(3)"#,
        )
        .execute(&mut *transaction)
        .await?;
        let rows = sqlx::query(
            r#"SELECT event_id, aggregate_id, event_type, routing_key, payload_json, attempts
                 FROM cloudflow_outbox
                WHERE status='PENDING' AND next_retry_at <= CURRENT_TIMESTAMP(3)
                ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED"#,
        )
        .bind(limit.min(100))
        .fetch_all(&mut *transaction)
        .await?;
        let mut records = Vec::with_capacity(rows.len());
        for row in rows {
            let record = OutboxRecord {
                event_id: row.get("event_id"),
                aggregate_id: row.get("aggregate_id"),
                event_type: row.get("event_type"),
                routing_key: row.get("routing_key"),
                payload: row.get::<Json<Value>, _>("payload_json").0,
                attempts: row.get("attempts"),
            };
            sqlx::query(
                r#"UPDATE cloudflow_outbox SET status='PUBLISHING', attempts=attempts+1,
                          next_retry_at=DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 60 SECOND)
                    WHERE event_id=? AND status='PENDING'"#,
            )
            .bind(&record.event_id)
            .execute(&mut *transaction)
            .await?;
            records.push(record);
        }
        transaction.commit().await?;
        Ok(records)
    }

    pub async fn mark_outbox_published(&self, event_id: &str) -> Result<(), sqlx::Error> {
        sqlx::query(
            "UPDATE cloudflow_outbox SET status='PUBLISHED', published_at=CURRENT_TIMESTAMP(3), last_error=NULL WHERE event_id=? AND status='PUBLISHING'",
        )
        .bind(event_id)
        .execute(&self.pool)
        .await?;
        Ok(())
    }

    pub async fn mark_outbox_retry(
        &self,
        event_id: &str,
        attempts: u32,
        error: &str,
    ) -> Result<bool, sqlx::Error> {
        let delay_seconds = 2_u64.saturating_pow(attempts.min(10)).min(900) as i32;
        let terminal = attempts >= MAX_OUTBOX_ATTEMPTS;
        sqlx::query(
            r#"UPDATE cloudflow_outbox SET status=?, last_error=?,
                      next_retry_at=IF(? = 1, CURRENT_TIMESTAMP(3),
                          DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL ? SECOND))
                WHERE event_id=? AND status='PUBLISHING'"#,
        )
        .bind(if terminal { "DEAD" } else { "PENDING" })
        .bind(truncate(error, 2000))
        .bind(terminal)
        .bind(delay_seconds)
        .bind(event_id)
        .execute(&self.pool)
        .await?;
        Ok(terminal)
    }
}

fn map_execution(row: sqlx::mysql::MySqlRow) -> Result<StoredExecution, sqlx::Error> {
    let status: String = row.try_get("status")?;
    let current_step: Option<String> = row.try_get("current_step")?;
    let sub_status =
        (status == "WAITING" && current_step.is_some()).then_some("WAITING_APPROVAL".to_owned());
    Ok(StoredExecution {
        execution_id: row.try_get("execution_id")?,
        workflow_id: row.try_get("workflow_id")?,
        user_id: row.try_get("user_id")?,
        space_id: row.try_get("space_id")?,
        plan_hash: row.try_get("plan_hash")?,
        status,
        sub_status,
        current_step,
        ir: row.try_get::<Json<WorkflowIrV1>, _>("ir_json")?.0,
        variables: row.try_get::<Json<Value>, _>("variables_json")?.0,
        outputs: row.try_get::<Json<Value>, _>("outputs_json")?.0,
        declared_permissions: row
            .try_get::<Json<Vec<String>>, _>("declared_permissions_json")?
            .0,
        granted_permissions: row
            .try_get::<Json<Vec<String>>, _>("granted_permissions_json")?
            .0,
        trace_id: row.try_get("trace_id")?,
        error_code: row.try_get("error_code")?,
        error_summary: row.try_get("error_summary")?,
        cancel_requested: row.try_get("cancel_requested")?,
        pause_requested: row.try_get("pause_requested")?,
        row_version: row.try_get("row_version")?,
    })
}

async fn append_log_tx(
    transaction: &mut Transaction<'_, MySql>,
    execution_id: &str,
    level: &str,
    message: &str,
) -> Result<(), sqlx::Error> {
    sqlx::query("SELECT execution_id FROM cloudflow_execution WHERE execution_id=? FOR UPDATE")
        .bind(execution_id)
        .fetch_one(&mut **transaction)
        .await?;
    sqlx::query(
        r#"INSERT INTO cloudflow_execution_log(execution_id, sequence_no, level, message)
            SELECT ?, COALESCE(MAX(sequence_no), 0) + 1, ?, ?
              FROM cloudflow_execution_log WHERE execution_id=?"#,
    )
    .bind(execution_id)
    .bind(level)
    .bind(truncate(message, 4000))
    .bind(execution_id)
    .execute(&mut **transaction)
    .await?;
    Ok(())
}

async fn enqueue_outbox_tx(
    transaction: &mut Transaction<'_, MySql>,
    aggregate_id: &str,
    event_type: &str,
    routing_key: &str,
    payload: Value,
) -> Result<(), sqlx::Error> {
    sqlx::query(
        r#"INSERT INTO cloudflow_outbox(
               event_id, aggregate_id, event_type, routing_key, payload_json
           ) VALUES (?, ?, ?, ?, ?)"#,
    )
    .bind(Uuid::new_v4().to_string())
    .bind(aggregate_id)
    .bind(event_type)
    .bind(routing_key)
    .bind(Json(payload))
    .execute(&mut **transaction)
    .await?;
    Ok(())
}

fn sha256_json(value: &Value) -> String {
    sha256_bytes(&serde_json::to_vec(value).unwrap_or_default())
}

fn sha256_bytes(value: &[u8]) -> String {
    format!("{:x}", Sha256::digest(value))
}

fn truncate(value: &str, max_chars: usize) -> String {
    value.chars().take(max_chars).collect()
}
