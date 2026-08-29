# PrivateCloudDisk Git Service

独立的公开空间 Git 仓库微服务。Space Service 负责资源身份和租户权限，Git Service 负责仓库索引、Git Smart HTTP/SSH、Object 映射、分支/Tag/MR、凭证、审计和 push 事件；实际 Object 文件通过 Storage Service 的内部 Broker 写入 Local/MinIO Provider。

## 启动

需要注入 `GIT_DATABASE_DSN`、`PCD_INTERNAL_SERVICE_TOKEN`、`PLATFORM_SERVICE_URL`、`STORAGE_SERVICE_URL`，可选配置 `GIT_RABBITMQ_URL`。Docker Compose 已提供 `pcd_git`、`git-service-backend`、`git-repo-cache` 和 Gateway 路由。

仓库权限支持 `USER` 与 `TEAM` 主体；`TEAM.subjectId` 使用现有团队/企业空间 ID，Git Service 每次鉴权由 Platform 内部成员接口确认，不在 Git 数据库复制成员事实。Git 仓库仍是 `resource_type=git` 的公开空间资源；仓库自身可设为 `PUBLIC`、`HIDDEN` 或 `PRIVATE`，不会被映射为平台的私人空间。`PUBLIC` 可匿名只读（受空间 browse/download 开关控制），所有 Git push 始终需要 WRITE 权限及 PAT 或 SSH 公钥。

```bash
GOCACHE=/tmp/pcd-gocache go test ./...

PCD_INTERNAL_SERVICE_TOKEN="test" \
STORAGE_SERVICE_URL="http://127.0.0.1:8000" \
GIT_DATABASE_DSN="youruser:yourpassword@tcp(localhost:3306)/pcd_git?parseTime=true&multiStatements=true&charset=utf8mb4" \
GIT_AUTO_MIGRATE="true" go run ./cmd/server
```

宿主机直接启动 Git Service 时，`STORAGE_SERVICE_URL` 必须指向实际运行的
`PrivateCloudDisk-storage-service`，且 `PCD_INTERNAL_SERVICE_TOKEN` 必须与 Storage
Service 完全一致；不能使用仅供 Git Service 的临时 token。Docker Compose 会把同一个
token 注入两个容器。若共享对象同步曾经失败，修复 Storage 连接后再次执行普通
`git push` 即可，Git Service 会在真正的 receive-pack 写入前自动修复该仓库。

## 入口

- 管理 API：`/git/repos/**`，经 Gateway 为 `/api/v1/git/repos/**`
- Smart HTTP：`/git/{repo}.git/{info/refs|git-upload-pack|git-receive-pack}`，并提供受控的 HEAD、pack、object、refs 等只读 dumb HTTP 兼容路径
- SSH：`git@<domain>:<repo>.git` 或 `ssh://git@<domain>:2222/<repo>.git`
- 健康检查：`GET /health`，就绪检查：`GET /ready`

详细边界、数据流、事件和扩展方式见 [Git 架构设计](../docs/GIT_REPOSITORY_ARCHITECTURE.md)、[使用指南](../docs/GIT_REPOSITORY_USER_GUIDE.md) 与 [协议安全审计](../docs/GIT_PROTOCOL_SECURITY_AUDIT.md)。
