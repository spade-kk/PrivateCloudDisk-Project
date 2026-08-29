use axum::{routing::post, Json, Router};
use cloudflow_runtime::{
    agent::{
        proto::capability_agent_server::CapabilityAgentServer, AgentInvocation,
        AuthorizationContext, CapabilityAgentProxy, CapabilityInvoker, GrpcCapabilityInvoker,
    },
    ir::ActionIr,
};
use serde_json::json;
use std::{collections::HashSet, net::SocketAddr, time::Duration};

fn free_address() -> SocketAddr {
    let listener = std::net::TcpListener::bind("127.0.0.1:0").expect("bind ephemeral port");
    let address = listener.local_addr().expect("local address");
    drop(listener);
    address
}

#[tokio::test]
#[ignore = "需要绑定本机 loopback 端口；CI/集成门禁显式执行 --ignored"]
async fn grpc_agent_proxies_to_capability_hub_and_keeps_security_context() {
    let http_listener = tokio::net::TcpListener::bind("127.0.0.1:0")
        .await
        .expect("HTTP listener");
    let http_address = http_listener.local_addr().expect("HTTP address");
    let http = tokio::spawn(async move {
        axum::serve(
            http_listener,
            Router::new().route(
                "/internal/v1/capabilities/invoke",
                post(|Json(body): Json<serde_json::Value>| async move {
                    assert_eq!(body["userId"], "user-1");
                    assert_eq!(body["spaceId"], "space-1");
                    assert_eq!(body["declaredPermissions"][0], "file.read");
                    Json(json!({
                        "code": "OK",
                        "data": {
                            "success": true,
                            "output": {"fileCount": 2},
                            "retryable": false
                        }
                    }))
                }),
            ),
        )
        .await
        .expect("HTTP server");
    });

    let grpc_address = free_address();
    let proxy = CapabilityAgentProxy::new(
        format!("http://{http_address}/internal/v1/capabilities/invoke"),
        "integration-secret".into(),
        Duration::from_secs(2),
    );
    let grpc = tokio::spawn(async move {
        tonic::transport::Server::builder()
            .add_service(CapabilityAgentServer::new(proxy))
            .serve(grpc_address)
            .await
            .expect("gRPC server");
    });
    tokio::time::sleep(Duration::from_millis(25)).await;

    let invoker = GrpcCapabilityInvoker::connect(
        &format!("http://{grpc_address}"),
        "integration-secret",
        Duration::from_secs(2),
    )
    .await
    .expect("gRPC client");
    let output = invoker
        .invoke(AgentInvocation {
            execution_id: "00000000-0000-0000-0000-000000000001".into(),
            step_id: "collect".into(),
            attempt: 1,
            action: ActionIr {
                provider: "builtin".into(),
                service: Some("file".into()),
                method: Some("list".into()),
                arguments: json!({"path":"/"}),
                ..Default::default()
            },
            input: json!({"path":"/"}),
            authorization: AuthorizationContext {
                user_id: "user-1".into(),
                space_id: Some("space-1".into()),
                declared_permissions: HashSet::from(["file.read".into()]),
                granted_permissions: HashSet::from(["file.read".into()]),
            },
            trace_id: "trace-1".into(),
        })
        .await
        .expect("Agent invocation");
    assert_eq!(output.value, json!({"fileCount":2}));
    grpc.abort();
    http.abort();
}

#[tokio::test]
async fn grpc_client_rejects_missing_grant_before_network_call() {
    let invoker = GrpcCapabilityInvoker::connect(
        "http://127.0.0.1:9",
        "integration-secret",
        Duration::from_millis(50),
    )
    .await
    .expect("lazy gRPC client");
    let error = invoker
        .invoke(AgentInvocation {
            execution_id: "00000000-0000-0000-0000-000000000001".into(),
            step_id: "collect".into(),
            attempt: 1,
            action: ActionIr {
                provider: "builtin".into(),
                service: Some("file".into()),
                method: Some("list".into()),
                ..Default::default()
            },
            input: json!({}),
            authorization: AuthorizationContext {
                user_id: "user-1".into(),
                space_id: Some("space-1".into()),
                declared_permissions: HashSet::from(["file.read".into()]),
                granted_permissions: HashSet::new(),
            },
            trace_id: "trace-1".into(),
        })
        .await
        .expect_err("permission must be rejected");
    assert_eq!(error.code, "CF4002");
}
