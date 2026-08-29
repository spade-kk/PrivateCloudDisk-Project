//! RabbitMQ 事件总线与可靠 Outbox Publisher。
//!
//! [CLOUDFLOW-RUNTIME-MQ-001] Broker 只传输命令/事件；Inbox 与 Outbox 状态以 MySQL 为事实源。
//! Publisher Confirm 成功后才把 Outbox 标为 PUBLISHED，重复投递由 event_id 唯一键消除。

use crate::{execution::ExecutionCoordinator, persistence::CreateExecution};
use futures_util::StreamExt;
use lapin::{
    options::{
        BasicAckOptions, BasicConsumeOptions, BasicPublishOptions, BasicQosOptions,
        BasicRejectOptions, ConfirmSelectOptions, ExchangeDeclareOptions, QueueBindOptions,
        QueueDeclareOptions,
    },
    types::{AMQPValue, FieldTable, LongString, ShortString},
    BasicProperties, Channel, Connection, ConnectionProperties, ExchangeKind,
};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::time::Duration;
use tracing::{error, info, warn};

const EXCHANGE: &str = "pcd.cloudflow.exchange";
const DLX: &str = "pcd.cloudflow.dlx";
const COMMAND_QUEUE: &str = "pcd.cloudflow.execution.command";
const COMMAND_ROUTE: &str = "cloudflow.execution.start";
const CANCEL_ROUTE: &str = "cloudflow.execution.cancel";
const PAUSE_ROUTE: &str = "cloudflow.execution.pause";
const RETRY_ROUTE: &str = "cloudflow.execution.retry";
const COMMAND_DLQ: &str = "pcd.cloudflow.execution.command.dlq";
const COMMAND_DLQ_ROUTE: &str = "cloudflow.execution.start.dlq";
const MAX_COMMAND_RETRIES: u32 = 3;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct EventEnvelope {
    pub id: String,
    pub event_type: String,
    pub correlation_id: String,
    pub causation_id: Option<String>,
    pub user_id: String,
    pub space_id: Option<String>,
    #[serde(default)]
    pub retry_count: u32,
    pub occurred_at: String,
    pub payload: Value,
}

#[derive(Clone)]
pub struct RabbitRuntimeBus {
    channel: Channel,
}

impl RabbitRuntimeBus {
    pub async fn connect(url: &str) -> Result<Self, lapin::Error> {
        let connection = Connection::connect(url, ConnectionProperties::default()).await?;
        let channel = connection.create_channel().await?;
        channel
            .confirm_select(ConfirmSelectOptions::default())
            .await?;
        let bus = Self { channel };
        bus.declare_topology().await?;
        Ok(bus)
    }

    async fn declare_topology(&self) -> Result<(), lapin::Error> {
        let durable_exchange = ExchangeDeclareOptions {
            durable: true,
            ..Default::default()
        };
        self.channel
            .exchange_declare(
                EXCHANGE,
                ExchangeKind::Topic,
                durable_exchange,
                FieldTable::default(),
            )
            .await?;
        self.channel
            .exchange_declare(
                DLX,
                ExchangeKind::Topic,
                durable_exchange,
                FieldTable::default(),
            )
            .await?;
        let mut arguments = FieldTable::default();
        arguments.insert(
            ShortString::from("x-dead-letter-exchange"),
            AMQPValue::LongString(LongString::from(DLX)),
        );
        arguments.insert(
            ShortString::from("x-dead-letter-routing-key"),
            AMQPValue::LongString(LongString::from(COMMAND_DLQ_ROUTE)),
        );
        let durable_queue = QueueDeclareOptions {
            durable: true,
            ..Default::default()
        };
        self.channel
            .queue_declare(COMMAND_QUEUE, durable_queue, arguments)
            .await?;
        self.channel
            .queue_bind(
                COMMAND_QUEUE,
                EXCHANGE,
                COMMAND_ROUTE,
                QueueBindOptions::default(),
                FieldTable::default(),
            )
            .await?;
        for routing_key in [CANCEL_ROUTE, PAUSE_ROUTE, RETRY_ROUTE] {
            self.channel
                .queue_bind(
                    COMMAND_QUEUE,
                    EXCHANGE,
                    routing_key,
                    QueueBindOptions::default(),
                    FieldTable::default(),
                )
                .await?;
        }
        self.channel
            .queue_declare(COMMAND_DLQ, durable_queue, FieldTable::default())
            .await?;
        self.channel
            .queue_bind(
                COMMAND_DLQ,
                DLX,
                COMMAND_DLQ_ROUTE,
                QueueBindOptions::default(),
                FieldTable::default(),
            )
            .await?;
        Ok(())
    }

    pub async fn run_command_consumer(
        self,
        coordinator: ExecutionCoordinator,
        mut shutdown: tokio::sync::watch::Receiver<bool>,
    ) -> Result<(), lapin::Error> {
        // [CLOUDFLOW-RUNTIME-MQ-002] 每个实例限制未确认消息，多个 Runtime 实例通过竞争消费自然均衡。
        self.channel
            .basic_qos(32, BasicQosOptions::default())
            .await?;
        let mut consumer = self
            .channel
            .basic_consume(
                COMMAND_QUEUE,
                "cloudflow-runtime",
                BasicConsumeOptions::default(),
                FieldTable::default(),
            )
            .await?;
        loop {
            tokio::select! {
                delivery = consumer.next() => {
                    let Some(delivery) = delivery else { break; };
                    match delivery {
                        Ok(delivery) => self.handle_delivery(&coordinator, delivery).await,
                        Err(error) => warn!(%error, "CloudFlow MQ delivery 读取失败"),
                    }
                }
                _ = shutdown.changed() => {
                    if *shutdown.borrow() { break; }
                }
            }
        }
        Ok(())
    }

    async fn handle_delivery(
        &self,
        coordinator: &ExecutionCoordinator,
        delivery: lapin::message::Delivery,
    ) {
        let envelope = match serde_json::from_slice::<EventEnvelope>(&delivery.data) {
            Ok(value)
                if matches!(
                    value.event_type.as_str(),
                    "cloudflow.execution.start.v1"
                        | "cloudflow.execution.cancel.v1"
                        | "cloudflow.execution.pause.v1"
                        | "cloudflow.execution.retry.v1"
                ) =>
            {
                value
            }
            Ok(value) => {
                warn!(event_type=%value.event_type, "CloudFlow 收到不支持的命令");
                let _ = delivery.reject(BasicRejectOptions { requeue: false }).await;
                return;
            }
            Err(error) => {
                warn!(%error, "CloudFlow MQ 消息契约无效");
                let _ = delivery.reject(BasicRejectOptions { requeue: false }).await;
                return;
            }
        };
        let store = coordinator.store();
        match store
            .claim_inbox(
                &envelope.id,
                &envelope.event_type,
                &envelope.payload,
                envelope.retry_count,
            )
            .await
        {
            Ok(false) => {
                let _ = delivery.ack(BasicAckOptions::default()).await;
                return;
            }
            Err(error) => {
                error!(%error, event_id=%envelope.id, "CloudFlow Inbox 持久化失败");
                self.retry_or_dead_letter(&delivery, &envelope, &error.to_string())
                    .await;
                return;
            }
            Ok(true) => {}
        }
        let handled = if envelope.event_type == "cloudflow.execution.start.v1" {
            match serde_json::from_value::<CreateExecution>(envelope.payload.clone()) {
                Ok(command) => coordinator
                    .submit(command)
                    .await
                    .map(|_| true)
                    .map_err(|error| error.to_string()),
                Err(error) => Err(format!("执行命令 payload 无效：{error}")),
            }
        } else {
            let execution_id = envelope
                .payload
                .get("executionId")
                .and_then(Value::as_str)
                .unwrap_or_default();
            if execution_id.is_empty() {
                Err("执行控制命令缺少 executionId".into())
            } else {
                let result = match envelope.event_type.as_str() {
                    "cloudflow.execution.cancel.v1" => store.request_cancel(execution_id).await,
                    "cloudflow.execution.pause.v1" => store.request_pause(execution_id).await,
                    "cloudflow.execution.retry.v1" => store.retry_execution(execution_id).await,
                    _ => unreachable!(),
                };
                result.map_err(|error| error.to_string())
            }
        };
        match handled {
            Ok(_) => {
                if let Err(error) = store.finish_inbox(&envelope.id, None).await {
                    error!(%error, event_id=%envelope.id, "CloudFlow Inbox 完成状态写入失败");
                    self.retry_or_dead_letter(&delivery, &envelope, &error.to_string())
                        .await;
                } else {
                    let _ = delivery.ack(BasicAckOptions::default()).await;
                }
            }
            Err(error) => {
                let summary = error.to_string();
                let _ = store.finish_inbox(&envelope.id, Some(&summary)).await;
                error!(%error, event_id=%envelope.id, "CloudFlow 执行命令处理失败");
                // 业务契约/参数错误不可重试；数据库连接、锁等待等短暂基础设施异常走有界重试。
                if is_retryable_failure(&summary) {
                    self.retry_or_dead_letter(&delivery, &envelope, &summary)
                        .await;
                } else {
                    let _ = delivery.reject(BasicRejectOptions { requeue: false }).await;
                }
            }
        }
    }

    /// 将重试计数放回事件头部并重新发布，避免 `nack(requeue=true)` 导致 Broker 无限重投。
    /// 达到上限后显式 reject 到已有 DLX；原始 message_id 保持不变，Inbox 唯一键继续提供幂等边界。
    async fn retry_or_dead_letter(
        &self,
        delivery: &lapin::message::Delivery,
        envelope: &EventEnvelope,
        reason: &str,
    ) {
        if envelope.retry_count >= MAX_COMMAND_RETRIES {
            error!(
                event_id=%envelope.id,
                retry_count=envelope.retry_count,
                reason=%reason,
                "CloudFlow 命令超过重试上限，进入死信"
            );
            let _ = delivery.reject(BasicRejectOptions { requeue: false }).await;
            return;
        }
        let mut next = envelope.clone();
        next.retry_count = next.retry_count.saturating_add(1);
        let Some(route) = command_route(&next.event_type) else {
            let _ = delivery.reject(BasicRejectOptions { requeue: false }).await;
            return;
        };
        let body = match serde_json::to_vec(&next) {
            Ok(body) => body,
            Err(error) => {
                error!(%error, event_id=%envelope.id, "CloudFlow 重试消息序列化失败");
                let _ = delivery.reject(BasicRejectOptions { requeue: false }).await;
                return;
            }
        };
        let properties = BasicProperties::default()
            .with_content_type("application/json".into())
            .with_delivery_mode(2)
            .with_message_id(next.id.clone().into())
            .with_type(next.event_type.clone().into());
        let published = match self
            .channel
            .basic_publish(
                EXCHANGE,
                route,
                BasicPublishOptions::default(),
                &body,
                properties,
            )
            .await
        {
            Ok(confirm) => confirm.await.map_err(|error| error.to_string()),
            Err(error) => Err(error.to_string()),
        };
        match published {
            Ok(confirm) if confirm.is_ack() => {
                info!(
                    event_id=%next.id,
                    retry_count=next.retry_count,
                    reason=%reason,
                    "CloudFlow 命令已重新入队"
                );
                let _ = delivery.ack(BasicAckOptions::default()).await;
            }
            Ok(_) => {
                error!(event_id=%next.id, "RabbitMQ 拒绝 CloudFlow 重试消息");
                let _ = delivery.reject(BasicRejectOptions { requeue: false }).await;
            }
            Err(error) => {
                error!(%error, event_id=%next.id, "CloudFlow 重试消息发布失败");
                let _ = delivery.reject(BasicRejectOptions { requeue: false }).await;
            }
        }
    }

    pub async fn run_outbox_publisher(
        self,
        store: crate::persistence::RuntimeStore,
        mut shutdown: tokio::sync::watch::Receiver<bool>,
    ) {
        loop {
            if *shutdown.borrow() {
                break;
            }
            match store.claim_outbox_batch(50).await {
                Ok(records) if records.is_empty() => {}
                Ok(records) => {
                    for record in records {
                        let body = serde_json::to_vec(&EventEnvelope {
                            id: record.event_id.clone(),
                            event_type: record.event_type.clone(),
                            correlation_id: record.aggregate_id.clone(),
                            causation_id: None,
                            user_id: String::new(),
                            space_id: None,
                            retry_count: record.attempts,
                            occurred_at: chrono::Utc::now().to_rfc3339(),
                            payload: record.payload.clone(),
                        })
                        .unwrap_or_default();
                        let properties = BasicProperties::default()
                            .with_content_type("application/json".into())
                            .with_delivery_mode(2)
                            .with_message_id(record.event_id.clone().into())
                            .with_type(record.event_type.clone().into());
                        let result = self
                            .channel
                            .basic_publish(
                                EXCHANGE,
                                &record.routing_key,
                                BasicPublishOptions::default(),
                                &body,
                                properties,
                            )
                            .await;
                        match result {
                            Ok(confirm) => match confirm.await {
                                Ok(confirmation) if confirmation.is_ack() => {
                                    if let Err(error) =
                                        store.mark_outbox_published(&record.event_id).await
                                    {
                                        error!(%error, event_id=%record.event_id, "Outbox 发布成功但状态更新失败");
                                    }
                                }
                                Ok(_) => {
                                    match store
                                        .mark_outbox_retry(
                                            &record.event_id,
                                            record.attempts + 1,
                                            "RabbitMQ NACK",
                                        )
                                        .await
                                    {
                                        Ok(true) => {
                                            error!(event_id=%record.event_id, "Outbox 重试达到上限，进入 DEAD 状态")
                                        }
                                        Err(error) => {
                                            error!(%error, event_id=%record.event_id, "Outbox 重试状态写入失败")
                                        }
                                        Ok(false) => {}
                                    }
                                }
                                Err(error) => {
                                    match store
                                        .mark_outbox_retry(
                                            &record.event_id,
                                            record.attempts + 1,
                                            &error.to_string(),
                                        )
                                        .await
                                    {
                                        Ok(true) => {
                                            error!(event_id=%record.event_id, "Outbox 发布确认异常达到上限，进入 DEAD 状态")
                                        }
                                        Err(error) => {
                                            error!(%error, event_id=%record.event_id, "Outbox 重试状态写入失败")
                                        }
                                        Ok(false) => {}
                                    }
                                }
                            },
                            Err(error) => {
                                match store
                                    .mark_outbox_retry(
                                        &record.event_id,
                                        record.attempts + 1,
                                        &error.to_string(),
                                    )
                                    .await
                                {
                                    Ok(true) => {
                                        error!(event_id=%record.event_id, "Outbox 发布失败达到上限，进入 DEAD 状态")
                                    }
                                    Err(error) => {
                                        error!(%error, event_id=%record.event_id, "Outbox 重试状态写入失败")
                                    }
                                    Ok(false) => {}
                                }
                            }
                        }
                    }
                }
                Err(error) => error!(%error, "CloudFlow Outbox 领取失败"),
            }
            tokio::select! {
                _ = tokio::time::sleep(Duration::from_millis(500)) => {},
                _ = shutdown.changed() => {}
            }
        }
        info!("CloudFlow Outbox Publisher 已停止");
    }
}

fn command_route(event_type: &str) -> Option<&'static str> {
    match event_type {
        "cloudflow.execution.start.v1" => Some(COMMAND_ROUTE),
        "cloudflow.execution.cancel.v1" => Some(CANCEL_ROUTE),
        "cloudflow.execution.pause.v1" => Some(PAUSE_ROUTE),
        "cloudflow.execution.retry.v1" => Some(RETRY_ROUTE),
        _ => None,
    }
}

fn is_retryable_failure(summary: &str) -> bool {
    let normalized = summary.to_ascii_lowercase();
    normalized.contains("数据库")
        || normalized.contains("database")
        || normalized.contains("deadlock")
        || normalized.contains("连接")
        || normalized.contains("connection")
        || normalized.contains("timeout")
        || normalized.contains("超时")
}
