# CloudFlow MCP Server Kubernetes 部署

`cloudflow-mcp-server.yaml` 部署的是纯服务端 MCP 适配层：它没有数据库、对象存储、
RabbitMQ、Redis 或业务服务凭证，唯一业务网络出口是 `workflow-service-backend:8087`
中的 Capability Hub。

部署前需要在目标命名空间创建：

```bash
kubectl create secret generic pcd-internal-service --from-literal=token='<internal-service-token>'
kubectl create secret generic cloudflow-mcp-identity --from-literal=gateway-hmac-secret='<32-byte-or-longer-secret>'
```

同时让 Gateway 使用同一个 HMAC secret（`MCP_IDENTITY_SIGNING_SECRET`），并把其
`CLOUDFLOW_MCP_SERVER_URL` 设为 `http://cloudflow-mcp-server:8093`。生产环境还必须在
ConfigMap 中设置 `MCP_OAUTH_AUTHORIZATION_SERVERS` 为平台 OAuth 2.1 授权服务器的 issuer。
如已部署 OpenTelemetry Collector，再设置 `MCP_OTEL_EXPORTER_OTLP_ENDPOINT`；默认为空时服务
仍传播 W3C trace context，但不会向外导出 trace。

`NetworkPolicy` 的 Gateway/Workflow 标签是假定的标准标签；应用前请按集群真实标签校正。
不要为了调试而向该 Deployment 增加 Platform、Storage、Plugin、Runtime 或数据库的网络出口。
