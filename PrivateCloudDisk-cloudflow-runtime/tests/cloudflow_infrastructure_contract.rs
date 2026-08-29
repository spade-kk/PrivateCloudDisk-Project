use async_trait::async_trait;
use cloudflow_runtime::{
    agent::{AgentError, AgentInvocation, AgentOutput, CapabilityInvoker},
    broker::{EventEnvelope, RabbitRuntimeBus},
    execution::ExecutionCoordinator,
    persistence::{CreateExecution, RuntimeStore},
    semantic::InMemoryCapabilityCatalog,
};
use lapin::{
    options::{BasicPublishOptions, ConfirmSelectOptions},
    BasicProperties, Connection, ConnectionProperties,
};
use serde_json::json;
use std::{
    sync::{Arc, Mutex},
    time::Duration,
};
use uuid::Uuid;

struct StubInvoker;

#[async_trait]
impl CapabilityInvoker for StubInvoker {
    async fn invoke(&self, _invocation: AgentInvocation) -> Result<AgentOutput, AgentError> {
        Ok(AgentOutput {
            value: json!({"ok": true}),
        })
    }
}

/// [CLOUDFLOW-RUNTIME-CONTROL-001] 真实 State Store 集成测试使用的受控能力替身。
/// `file.save` 故意失败，用于验证 try/catch/finally 不会把局部异常升级为整个工作流失败；
/// 其余调用记录方法名，以断言 foreach 确实按集合元素逐项触发。
#[derive(Default)]
struct DynamicControlInvoker {
    calls: Mutex<Vec<String>>,
}

#[async_trait]
impl CapabilityInvoker for DynamicControlInvoker {
    async fn invoke(&self, invocation: AgentInvocation) -> Result<AgentOutput, AgentError> {
        let method = invocation.action.method.clone().unwrap_or_default();
        self.calls.lock().expect("calls lock").push(method.clone());
        if method == "save" {
            return Err(AgentError {
                code: "CF-TEST-EXPECTED".into(),
                summary: "受控测试异常".into(),
                retryable: false,
            });
        }
        Ok(AgentOutput {
            value: json!({"ok": true, "input": invocation.input}),
        })
    }
}

#[tokio::test]
#[ignore = "需要 CLOUDFLOW_TEST_DATABASE_URL 与 CLOUDFLOW_TEST_RABBITMQ_URL"]
async fn mysql_inbox_outbox_recovery_and_rabbit_command_are_integrated() {
    let database_url =
        std::env::var("CLOUDFLOW_TEST_DATABASE_URL").expect("CLOUDFLOW_TEST_DATABASE_URL");
    let rabbitmq_url =
        std::env::var("CLOUDFLOW_TEST_RABBITMQ_URL").expect("CLOUDFLOW_TEST_RABBITMQ_URL");
    let store = RuntimeStore::connect(&database_url, 8)
        .await
        .expect("MySQL");
    store.migrate().await.expect("migrations");
    let bus = RabbitRuntimeBus::connect(&rabbitmq_url)
        .await
        .expect("RabbitMQ");
    let coordinator = ExecutionCoordinator::new(
        store.clone(),
        Arc::new(StubInvoker),
        1,
        1,
        Duration::from_millis(20),
        Duration::from_secs(2),
    );
    let (shutdown_tx, shutdown_rx) = tokio::sync::watch::channel(false);
    let consumer = tokio::spawn(
        bus.clone()
            .run_command_consumer(coordinator.clone(), shutdown_rx.clone()),
    );

    let source = r#"workflow "infra" {
        trigger { manual {} }
        variables { path = input.string(required = true) }
        step read { action file.list { path = vars.path } output files }
    }"#;
    let ir = cloudflow_runtime::compile_source(source, &InMemoryCapabilityCatalog::default())
        .expect("compile");
    let execution_id = Uuid::new_v4().to_string();
    let event_id = Uuid::new_v4().to_string();
    let command = CreateExecution {
        execution_id: execution_id.clone(),
        workflow_id: Uuid::new_v4().to_string(),
        user_id: Uuid::new_v4().to_string(),
        space_id: None,
        ir,
        variables: json!({"path":"/reports"}),
        declared_permissions: vec![],
        granted_permissions: vec![],
        trace_id: Uuid::new_v4().simple().to_string(),
    };
    let envelope = EventEnvelope {
        id: event_id.clone(),
        event_type: "cloudflow.execution.start.v1".into(),
        correlation_id: execution_id.clone(),
        causation_id: None,
        user_id: command.user_id.clone(),
        space_id: None,
        retry_count: 0,
        occurred_at: chrono::Utc::now().to_rfc3339(),
        payload: serde_json::to_value(&command).expect("command JSON"),
    };
    let connection = Connection::connect(&rabbitmq_url, ConnectionProperties::default())
        .await
        .expect("publish connection");
    let channel = connection.create_channel().await.expect("channel");
    channel
        .confirm_select(ConfirmSelectOptions::default())
        .await
        .expect("confirm mode");
    channel
        .basic_publish(
            "pcd.cloudflow.exchange",
            "cloudflow.execution.start",
            BasicPublishOptions::default(),
            &serde_json::to_vec(&envelope).expect("envelope JSON"),
            BasicProperties::default().with_delivery_mode(2),
        )
        .await
        .expect("publish")
        .await
        .expect("confirm");

    wait_until(|| async {
        store
            .get_execution(&execution_id)
            .await
            .ok()
            .flatten()
            .is_some()
    })
    .await;
    assert_eq!(
        store
            .get_execution(&execution_id)
            .await
            .expect("execution")
            .expect("present")
            .status,
        "READY"
    );
    assert!(!store
        .claim_inbox(&event_id, &envelope.event_type, &envelope.payload, 0)
        .await
        .expect("duplicate inbox"));

    let publisher = tokio::spawn(bus.run_outbox_publisher(store.clone(), shutdown_rx.clone()));
    let verification_pool = sqlx::MySqlPool::connect(&database_url)
        .await
        .expect("verification pool");
    wait_until(|| async {
        sqlx::query_scalar::<_, i64>(
            "SELECT COUNT(*) FROM cloudflow_outbox WHERE aggregate_id=? AND status='PUBLISHED'",
        )
        .bind(&execution_id)
        .fetch_one(&verification_pool)
        .await
        .unwrap_or(0)
            > 0
    })
    .await;

    sqlx::query(
        "UPDATE cloudflow_execution SET status='RUNNING', heartbeat_at=DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 10 SECOND) WHERE execution_id=?",
    )
    .bind(&execution_id)
    .execute(&verification_pool)
    .await
    .expect("make stale");
    assert_eq!(store.recover_stale(1).await.expect("recover"), 1);
    assert_eq!(
        store
            .get_execution(&execution_id)
            .await
            .expect("execution")
            .expect("present")
            .status,
        "READY"
    );

    let _ = shutdown_tx.send(true);
    consumer.await.expect("consumer join").expect("consumer");
    publisher.await.expect("publisher join");
}

/// 动态控制流必须以 MySQL 检查点而不是进程内状态为准。运行此测试需要真实 MySQL：
/// - foreach 对三个元素各执行一次；
/// - try 的失败被 catch 吸收，finally 无条件执行；
/// - wait 进入 WAITING，调用 resume 后恢复并收敛为 SUCCESS。
#[tokio::test]
#[ignore = "需要 CLOUDFLOW_TEST_DATABASE_URL"]
async fn mysql_dynamic_controls_and_wait_resume_are_recoverable() {
    let database_url =
        std::env::var("CLOUDFLOW_TEST_DATABASE_URL").expect("CLOUDFLOW_TEST_DATABASE_URL");
    let store = RuntimeStore::connect(&database_url, 12)
        .await
        .expect("MySQL");
    store.migrate().await.expect("migrations");
    let invoker = Arc::new(DynamicControlInvoker::default());
    let coordinator = ExecutionCoordinator::new(
        store.clone(),
        invoker.clone(),
        1,
        30,
        Duration::from_millis(20),
        Duration::from_secs(2),
    );
    let (shutdown_tx, shutdown_rx) = tokio::sync::watch::channel(false);
    let worker = tokio::spawn(coordinator.clone().run_workers(shutdown_rx));

    let source = r#"
workflow "dynamic_controls" {
  runtime { max_parallel = 2 }
  variables { items: array = ["a", "b", "c"] }
  foreach item in vars.items {
    step fan_out { action builtin.file.copy { file = item } }
  }
  try {
    step risky { action builtin.file.save {} }
  } catch error {
    step recovered { action builtin.notification.send { detail = error } }
  } finally {
    step cleanup { action builtin.notification.send { detail = "cleanup" } }
  }
  wait approval { timeout = 1h }
}
"#;
    let command = CreateExecution {
        execution_id: Uuid::new_v4().to_string(),
        workflow_id: Uuid::new_v4().to_string(),
        user_id: Uuid::new_v4().to_string(),
        space_id: Some(Uuid::new_v4().to_string()),
        ir: cloudflow_runtime::compile_source(source, &InMemoryCapabilityCatalog::default())
            .expect("compile dynamic control flow"),
        variables: json!({}),
        declared_permissions: vec![],
        granted_permissions: vec![],
        trace_id: Uuid::new_v4().simple().to_string(),
    };
    let execution_id = command.execution_id.clone();
    assert!(coordinator.submit(command).await.expect("submit"));

    wait_until(|| async {
        store
            .get_execution(&execution_id)
            .await
            .ok()
            .flatten()
            .is_some_and(|execution| execution.status == "WAITING")
    })
    .await;
    let waiting = store
        .get_execution(&execution_id)
        .await
        .expect("read waiting")
        .expect("execution");
    assert_eq!(waiting.sub_status.as_deref(), Some("WAITING_APPROVAL"));
    let calls = invoker.calls.lock().expect("calls lock").clone();
    assert_eq!(calls.iter().filter(|method| *method == "copy").count(), 3);
    assert_eq!(calls.iter().filter(|method| *method == "save").count(), 1);
    assert_eq!(calls.iter().filter(|method| *method == "send").count(), 2);

    // 每一个 foreach 元素都必须有自己的检查点 ID；静态 `fan_out` 则仅作为 Engine 跳过标记，
    // 不能把三次真实调用折叠成同一 step_id 的三次 attempt。
    let verification_pool = sqlx::MySqlPool::connect(&database_url)
        .await
        .expect("verification pool");
    let fan_out_ids = sqlx::query_scalar::<_, String>(
        "SELECT DISTINCT step_id FROM cloudflow_step_execution WHERE execution_id=? AND step_id LIKE '%fan_out'",
    )
    .bind(&execution_id)
    .fetch_all(&verification_pool)
    .await
    .expect("dynamic step checkpoints");
    assert_eq!(
        fan_out_ids
            .iter()
            .filter(|step_id| step_id.contains('['))
            .count(),
        3,
        "foreach 的三个元素必须持久化为三个独立动态 step ID"
    );

    assert!(store
        .resume_execution(&execution_id, &json!({"approved": true}))
        .await
        .expect("resume"));
    wait_until(|| async {
        store
            .get_execution(&execution_id)
            .await
            .ok()
            .flatten()
            .is_some_and(|execution| execution.status == "SUCCESS")
    })
    .await;
    let _ = shutdown_tx.send(true);
    worker.await.expect("worker join");
}

async fn wait_until<F, Fut>(mut predicate: F)
where
    F: FnMut() -> Fut,
    Fut: std::future::Future<Output = bool>,
{
    for _ in 0..100 {
        if predicate().await {
            return;
        }
        tokio::time::sleep(Duration::from_millis(25)).await;
    }
    panic!("condition was not satisfied before timeout");
}
