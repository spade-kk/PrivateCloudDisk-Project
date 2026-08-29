//! CloudFlow Runtime 入口：配置 Axum、MySQL、RabbitMQ、gRPC Agent 与优雅关闭。

use cloudflow_runtime::{
    agent::{
        proto::capability_agent_server::CapabilityAgentServer, CapabilityAgentProxy,
        GrpcCapabilityInvoker,
    },
    broker::RabbitRuntimeBus,
    config::{RuntimeConfig, RuntimeMode},
    execution::ExecutionCoordinator,
    http::{build_router_with_coordinator, HttpConfig},
    persistence::RuntimeStore,
};
use std::{env, net::SocketAddr, process::ExitCode, sync::Arc, time::Duration};
use tracing::{error, info};
use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() -> ExitCode {
    if env::args().any(|arg| arg == "--healthcheck") {
        return healthcheck().await;
    }
    init_tracing();
    let address = env::var("CLOUDFLOW_LISTEN_ADDRESS").unwrap_or_else(|_| "127.0.0.1:8091".into());
    let parsed_address = match address.parse::<SocketAddr>() {
        Ok(value) => value,
        Err(error) => {
            eprintln!("CloudFlow Runtime 监听地址无效：{error}");
            return ExitCode::from(78);
        }
    };
    let service_token = env::var("PCD_INTERNAL_SERVICE_TOKEN").unwrap_or_default();
    if service_token.trim().is_empty() {
        eprintln!("CloudFlow Runtime 拒绝启动：PCD_INTERNAL_SERVICE_TOKEN 未配置");
        return ExitCode::from(78);
    }
    let config = HttpConfig {
        service_token,
        capabilities: split_env("CLOUDFLOW_CAPABILITIES"),
        max_concurrency: env::var("CLOUDFLOW_HTTP_MAX_CONCURRENCY")
            .ok()
            .and_then(|value| value.parse().ok())
            .unwrap_or(32),
        allowed_origins: split_env("CLOUDFLOW_CORS_ALLOWED_ORIGINS"),
        // 开发调试执行入口（需求 4.19/4.20/9.15）：默认关闭，仅显式开启。
        enable_dev_execute: env::var("CLOUDFLOW_ENABLE_DEBUG_EXECUTE")
            .ok()
            .map(|value| value.eq_ignore_ascii_case("true"))
            .unwrap_or(false),
    };
    let runtime_config = match RuntimeConfig::from_env() {
        Ok(value) => value,
        Err(error) => {
            error!(%error, "CloudFlow Runtime 配置无效");
            return ExitCode::from(78);
        }
    };
    let (shutdown_tx, shutdown_rx) = tokio::sync::watch::channel(false);
    let mut background_tasks = Vec::new();
    let coordinator = if runtime_config.mode == RuntimeMode::Production {
        let store = match RuntimeStore::connect(
            runtime_config
                .database_url
                .as_deref()
                .expect("validated database URL"),
            runtime_config.database_max_connections,
        )
        .await
        {
            Ok(value) => value,
            Err(error) => {
                error!(%error, "CloudFlow Runtime 数据库连接失败");
                return ExitCode::from(1);
            }
        };
        if let Err(error) = store.migrate().await {
            error!(%error, "CloudFlow Runtime 数据库迁移失败");
            return ExitCode::from(1);
        }
        let agent_address = match runtime_config
            .capability_agent_listen_address
            .as_deref()
            .expect("validated Agent listen address")
            .parse::<SocketAddr>()
        {
            Ok(value) => value,
            Err(error) => {
                error!(%error, "CLOUDFLOW_AGENT_LISTEN_ADDRESS 无效");
                return ExitCode::from(78);
            }
        };
        let agent_proxy = CapabilityAgentProxy::new(
            runtime_config
                .workflow_capability_url
                .clone()
                .expect("validated Workflow capability URL"),
            config.service_token.clone(),
            runtime_config.action_timeout,
        );
        let mut agent_shutdown = shutdown_rx.clone();
        background_tasks.push(tokio::spawn(async move {
            let result = tonic::transport::Server::builder()
                .concurrency_limit_per_connection(32)
                .timeout(Duration::from_secs(130))
                .add_service(CapabilityAgentServer::new(agent_proxy))
                .serve_with_shutdown(agent_address, async move {
                    loop {
                        if *agent_shutdown.borrow() {
                            break;
                        }
                        if agent_shutdown.changed().await.is_err() {
                            break;
                        }
                    }
                })
                .await;
            if let Err(error) = result {
                error!(%error, "CloudFlow Capability Agent gRPC 服务已停止");
            }
        }));
        let invoker = match GrpcCapabilityInvoker::connect(
            runtime_config
                .capability_agent_url
                .as_deref()
                .expect("validated capability agent URL"),
            &config.service_token,
            runtime_config.action_timeout,
        )
        .await
        {
            Ok(value) => Arc::new(value),
            Err(error) => {
                error!(%error, "CloudFlow Runtime gRPC Capability Agent 连接失败");
                return ExitCode::from(1);
            }
        };
        let coordinator = ExecutionCoordinator::new(
            store.clone(),
            invoker,
            runtime_config.worker_concurrency,
            runtime_config.stale_seconds,
            runtime_config.poll_interval,
            runtime_config.action_timeout,
        );
        let bus = match RabbitRuntimeBus::connect(
            runtime_config
                .rabbitmq_url
                .as_deref()
                .expect("validated RabbitMQ URL"),
        )
        .await
        {
            Ok(value) => value,
            Err(error) => {
                error!(%error, "CloudFlow Runtime RabbitMQ 连接或拓扑声明失败");
                return ExitCode::from(1);
            }
        };
        let worker = coordinator.clone();
        let worker_shutdown = shutdown_rx.clone();
        background_tasks.push(tokio::spawn(async move {
            worker.run_workers(worker_shutdown).await;
        }));
        let consumer_bus = bus.clone();
        let consumer = coordinator.clone();
        let consumer_shutdown = shutdown_rx.clone();
        background_tasks.push(tokio::spawn(async move {
            if let Err(error) = consumer_bus
                .run_command_consumer(consumer, consumer_shutdown)
                .await
            {
                error!(%error, "CloudFlow MQ command consumer 已停止");
            }
        }));
        let publisher_shutdown = shutdown_rx.clone();
        background_tasks.push(tokio::spawn(async move {
            bus.run_outbox_publisher(store, publisher_shutdown).await;
        }));
        info!("CloudFlow Runtime 已启用持久化生产执行面");
        Some(coordinator)
    } else {
        info!("CloudFlow Runtime 以 compiler 模式启动，不接收生产执行");
        None
    };
    let listener = match tokio::net::TcpListener::bind(parsed_address).await {
        Ok(value) => value,
        Err(error) => {
            eprintln!("CloudFlow Runtime 无法绑定监听地址: {error}");
            return ExitCode::from(1);
        }
    };
    info!(%address, "pcd-cloudflow-runtime listening");
    let signal_sender = shutdown_tx.clone();
    let server_result = axum::serve(listener, build_router_with_coordinator(config, coordinator))
        .with_graceful_shutdown(async move {
            shutdown_signal().await;
            let _ = signal_sender.send(true);
        })
        .await;
    let _ = shutdown_tx.send(true);
    for task in background_tasks {
        let _ = tokio::time::timeout(Duration::from_secs(20), task).await;
    }
    if let Err(error) = server_result {
        error!(%error, "CloudFlow Runtime 已停止");
        return ExitCode::from(1);
    }
    ExitCode::SUCCESS
}

fn init_tracing() {
    let filter = EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info"));
    let _ = tracing_subscriber::fmt()
        .with_env_filter(filter)
        .json()
        .with_target(false)
        .try_init();
}

async fn healthcheck() -> ExitCode {
    let configured =
        env::var("CLOUDFLOW_LISTEN_ADDRESS").unwrap_or_else(|_| "127.0.0.1:8091".into());
    let Ok(mut address) = configured.parse::<SocketAddr>() else {
        return ExitCode::from(1);
    };
    if address.ip().is_unspecified() {
        address.set_ip("127.0.0.1".parse().expect("static loopback address"));
    }
    let url = format!("http://{address}/health/ready");
    match reqwest::Client::builder()
        .timeout(Duration::from_secs(2))
        .build()
        .expect("healthcheck client")
        .get(url)
        .send()
        .await
    {
        Ok(response) if response.status().is_success() => ExitCode::SUCCESS,
        Ok(response) => {
            eprintln!("CloudFlow Runtime 健康检查失败: HTTP {}", response.status());
            ExitCode::from(1)
        }
        Err(error) => {
            eprintln!("CloudFlow Runtime 健康检查失败: {error}");
            ExitCode::from(1)
        }
    }
}

fn split_env(name: &str) -> Vec<String> {
    env::var(name)
        .unwrap_or_default()
        .split(',')
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(str::to_owned)
        .collect()
}

async fn shutdown_signal() {
    #[cfg(unix)]
    {
        use tokio::signal::unix::{signal, SignalKind};
        let mut terminate = signal(SignalKind::terminate()).expect("install SIGTERM handler");
        tokio::select! { _ = tokio::signal::ctrl_c() => {}, _ = terminate.recv() => {} }
    }
    #[cfg(not(unix))]
    {
        let _ = tokio::signal::ctrl_c().await;
    }
}
