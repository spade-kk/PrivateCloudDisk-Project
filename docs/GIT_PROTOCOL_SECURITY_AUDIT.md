# Git Service HTTP Smart / SSH 协议审计与验证报告

需求：`REQ-GIT-AUDIT-20260817`。审计范围覆盖 Git Service、Gateway、Nginx、Platform 内部 Git 授权接口、数据库迁移、Storage Object 同步、Smart HTTP、SSH、现有 CLI 与安全脚本。

## 审计到的真实链路

| 层 | 实际位置 | 结论 |
| --- | --- | --- |
| 边缘层 | `PrivateCloudDisk-web/nginx/conf.d/api.conf` | `/git/` 关闭代理缓冲并使用长传输超时，转发 Gateway |
| Gateway 路由 | `PrivateCloudDisk-gateway-service` | `/git/**` 与 `/api/v1/git/**` 转到 Git Service；管理 API 继续 JWT |
| Git HTTP | `internal/gitproto/http.go` | 安全解析、PAT、授权、限流后委托 `git http-backend` |
| Git SSH | `internal/sshserver/server.go` | `x/crypto/ssh`，仅公钥认证与 upload/receive-pack exec |
| 授权 | `internal/auth` + Platform `InternalGitController` | Platform 负责 active + `resource_type=git`，Git Service 叠加仓库级策略 |
| 存储 | `internal/gitrepo` + Storage Broker | bare repo 是协议缓存；共享 Object、refs、索引同步后为事实源 |
| 自动化 | Outbox | 真实 refs 变化发布 `pcd.git.push.completed.v1` / `git.push.completed` |

## 原缺口与修复

| 风险 | 原发现 | 处理 |
| --- | --- | --- |
| HTTP 覆盖不足 | 仅三个 Smart HTTP 端点 | 补齐受控 HEAD、alternates、packs、pack/idx、loose object、refs、commit-graph 读取；完整 50 项矩阵见 Git Service API 文档 |
| 发现误作写入 | receive-pack `info/refs` 会 snapshot/sync | 仅 POST `git-receive-pack` 视作 mutation |
| 横向越权 | `allow_public_upload` 放行任意认证 push | push 必须有 PAT/SSH 身份与仓库/空间 WRITE；通用文件开关不再影响 Git |
| 可见性不足 | 只有空间通用开关 | Git 仓库新增 `PUBLIC/HIDDEN/PRIVATE`，保持公开空间 Git 资源属性 |
| 隐藏仓库枚举 | 有凭据但无权限返回 403 | HIDDEN/PRIVATE：未认证 401、认证无权 404；SSH 返回 not found |
| dumb push 绕过 | loose object PUT 可绕过 hook/receive-pack | 显式 405；Git CLI 标准 push 仍完整走 receive-pack |
| 资源耗尽 | 无请求体/回包/握手防护 | 413 上限、回包排空、Git timeout、SSH handshake deadline、429 并发 |
| SSH 多 exec | 单 TCP 可并发多个 session | 每连接一个 Git exec；只允许命令白名单 |
| 暴力认证 | 无本服务冷却与认证前审计 | IP 失败窗口/冷却、独立安全审计，不记录 PAT/公钥原文 |
| 迁移演进 | auto-migrate 只执行 V1 | 迁移账本按 `V*__*.sql` 排序执行，新增 V2 |
| 静态索引陈旧 | push 后未 update-server-info | 成功同步后刷新，失败仅告警且不回滚成功 Smart push |

## 强制安全策略

1. Space 仍是上层资源容器，只有 active `public` + `resource_type=git` 才能关联 Git 仓库；Git 的 HIDDEN/PRIVATE 不改变空间为私人空间。
2. `PUBLIC` 匿名 clone/fetch 还要求空间的 `allow_public_browse` 与 `allow_public_download` 同时开启；匿名永不 push。
3. HIDDEN/PRIVATE 需要显式 Read/Write。已认证无权返回 404，降低水平越权探测能力。
4. Gateway 只放行 `.git` 协议根，`/api/v1/git/repos/**` 不在白名单；设备身份过滤器也不会把 Git Basic/PAT 误判为设备请求。
5. SSH 仅接受登记的 Ed25519、ECDSA、≥3072 位 RSA 公钥，每用户最多 20 个；拒绝 shell、PTY、转发与其他命令。
6. 所有协议路径在仓库查找前做方法/路径判定；路径穿越、配置读取与 dumb 写入不进入 bare repo 文件系统。

## 交付的验证脚本

```bash
cd PrivateCloudDisk-git-service
GOCACHE=/tmp/pcd-git-audit-gocache go test ./...
GOCACHE=/tmp/pcd-git-audit-gocache go vet ./...
bash -n tests/integration/git_smart_http_test.sh \
  tests/integration/git_ssh_protocol_test.sh \
  tests/security/git_protocol_security_test.sh \
  tests/performance/git_parallel_clone.sh \
  tests/performance/git_parallel_push.sh
```

| 脚本 | 覆盖 |
| --- | --- |
| `tests/integration/git_smart_http_test.sh` | clone、fetch、pull、浅克隆、Protocol v2、分支、标签、可选 SSH；写入须 `GIT_TEST_WRITE=true` |
| `tests/integration/git_ssh_protocol_test.sh` | 登记 SSH key 后的 clone/fetch/receive-pack |
| `tests/security/git_protocol_security_test.sh` | 匿名/隐藏策略、无权 push、Content-Type、方法、路径穿越、config、对象 PUT、安全响应头 |
| `tests/performance/git_parallel_clone.sh` | 默认 100 并发浅克隆，失败即失败 |
| `tests/performance/git_parallel_push.sh` | 不同分支并发 receive-pack/Object 同步；仅专用压测仓库 |

真实环境运行方式示例：

```bash
GIT_REPO_BASE_URL=https://<domain>/git \
GIT_REPO_SLUG=<slug> \
GIT_AUTH_HEADER="Basic <base64(x-access-token:pat)>" \
bash PrivateCloudDisk-git-service/tests/integration/git_smart_http_test.sh

GIT_TEST_WRITE=true \
GIT_REPO_BASE_URL=https://<domain>/git \
GIT_REPO_SLUG=<dedicated-test-slug> \
GIT_AUTH_HEADER="Basic <base64(x-access-token:pat)>" \
GIT_SSH_REMOTE=ssh://git@<domain>:2222/<dedicated-test-slug>.git \
bash PrivateCloudDisk-git-service/tests/integration/git_smart_http_test.sh

# 网关测试
GIT_REPO_BASE_URL=https://<domain>/git \
GIT_REPO_SLUG=testgit \
bash PrivateCloudDisk-git-service/tests/security/git_protocol_security_test.sh
# 直连 Git Service 测试
GIT_REPO_BASE_URL=https://<domain>/git \
GIT_REPO_SLUG=<dedicated-test-slug> \
GIT_EXPECT_INVALID_POST_STATUS=405 \
bash PrivateCloudDisk-git-service/tests/security/git_protocol_security_test.sh
```

本次宿主机的 `127.0.0.1:8091` 已对 `/health` 与 `/ready` 返回成功，但当前进程没有在本次
改动后被重启，也没有向其提供专用测试仓库、PAT、SSH 私钥或隔离的写入授权。因此 Go 单元/
静态验证已完成；真实 Git CLI、跨服务对象同步、100 并发与渗透脚本已交付但必须在隔离集成
环境以本次构建重新部署后执行，不能宣称已在本机通过。

## 上线前检查

- Storage Service 必须由 `app.main:app` 启动；不要使用历史
  `server:app`。后者没有注册 `/internal/v1/git/objects/{algorithm}/{object_hash}`，会造成
  Git Service 的 Object HEAD/PUT 返回 404。由于 Smart HTTP 的 `receive-pack` 已经完成本地
  pack 解包，Git 客户端看到的表象是 HTTP 503 或 `unexpected disconnect`，并不是 Git 协议
  协商失败。开发 Dockerfile、生产 Dockerfile 和 README 已统一为 `app.main:app`。
- 重启 Storage Service 后，用带内部服务令牌的 HEAD 探针确认 Object Broker 已加载；无令牌请求
  应返回 401，不能返回 404。再执行一次失败仓库的 `git push`，Git Service 会先恢复 DEGRADED
  仓库中已存在的本地 refs/Object，再继续正常 `receive-pack`，不会要求用户删除本地仓库。
- 应用 `PrivateCloudDisk-git-service/db/migration/V2__repository_visibility_and_protocol_security.sql`，或维护窗口内以 `GIT_AUTO_MIGRATE=true` 启动。
- 通过 Gateway 地址而非容器 `8091` 完成 HTTP clone/push，确认 Authorization、Git-Protocol、Range 头未被边缘层丢弃。
- 使用 PUBLIC、HIDDEN、PRIVATE 三个专用 Git 资源分别验证匿名、Read PAT、Write PAT、无权 PAT 与 SSH key。
- 在保护分支验证直接 push 被拒绝、MR 合并后更新引用。
- 在专用压测仓库执行并发脚本并采集 P95/P99、失败率、MySQL 连接数和 Storage 延迟。
