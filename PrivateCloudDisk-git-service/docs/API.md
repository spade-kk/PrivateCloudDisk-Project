# Git Service API 与 Git 协议契约

Git 仓库是公开空间的一个资源实现：空间必须是 `space_type=public` 且
`resource_type=git`。它不是平台的“私人空间”。仓库自身再使用 `PUBLIC`、`HIDDEN`、
`PRIVATE` 控制 Git 协议可发现性；该设计不会阻碍未来在同一空间抽象下接入 Docker、数据集或 AI 模型资源。

## 管理 API

管理 API 经 Gateway 使用 JWT；Git CLI 不调用这些接口。公网地址为 `/api/v1/git/**`，下游 Git Service 地址为 `/git/**`。

| 方法 | 路径 | 权限 |
| --- | --- | --- |
| POST | `/git/repos` | Git 公开空间所有者创建仓库；body 支持 `visibility` |
| GET | `/git/repos/by-space/{spaceID}` | Metadata |
| GET/PATCH/DELETE | `/git/repos/{repoID}` | Metadata / Admin / Admin |
| GET/POST/DELETE | `/git/repos/{repoID}/branches` | Read / Write / Write |
| GET/POST/DELETE | `/git/repos/{repoID}/tags` | Read / Write / Write |
| GET | `/git/repos/{repoID}/commits` | Read |
| GET | `/git/repos/{repoID}/tree`、`blob`、`raw`、`archive`、`readme`、`diff`、`blame`、`insights` | Read |
| GET/POST | `/git/repos/{repoID}/merge-requests` | Read / Write |
| GET/POST | `/git/repos/{repoID}/merge-requests/{mrID}/comments` | Read / Write |
| POST | `/git/repos/{repoID}/merge-requests/{mrID}/approve`、`merge` | Write |
| GET/PUT/DELETE | `/git/repos/{repoID}/permissions`、`permissions/{subjectID}` | Admin |
| PUT | `/git/repos/{repoID}/branch-protections` | Admin |
| GET/POST/DELETE | `/git/repos/{repoID}/webhooks` | Admin |
| GET/POST | `/git/repos/{repoID}/workflow-bindings` | Admin |
| GET | `/git/repos/{repoID}/audit` | Admin |
| PUT/DELETE/GET | `/git/repos/{repoID}/star`、`star/status` | 登录用户；Star 幂等 |
| POST | `/git/repos/{repoID}/fork` | 登录用户；源仓库 Read + 目标 Git 空间 Admin/Owner |
| GET | `/git/repos/stars`、`/git/repos/forks` | 当前 JWT 用户 |
| GET/POST/DELETE | `/git/credentials/pats`、`ssh-keys` | 当前 JWT 用户 |

Smart HTTP 标准路径为 `/git/{repo-slug}.git/...`；`/api/v1/git/{repo-slug}.git/...` 作为兼容入口。`info/refs` 只接受 GET，RPC 只接受 POST。Gateway 不对 receive-pack 配置自动重试。

`GET /git/repos/{repoID}/commits` 默认按 `ref`（缺省为默认分支）返回历史；仓库提交图可使用
`all=1` 请求服务端固定的 `git log --all` 聚合历史。`all=1` 不能携带任意 revision 表达式，
仍复用 Read 权限、分页上限和文件路径校验，避免浏览器为每个分支并发拉取历史。

创建或更新仓库时，`visibility` 可选为：

- `PUBLIC`：在空间允许公开浏览和下载时，匿名用户可 clone/fetch；任何 push 仍必须是拥有 `write_repository`/`api` PAT 或 SSH 公钥、且具备仓库 `WRITE` 权限的用户。
- `HIDDEN`：地址不在公开发现列表中；未认证请求为 `401`，已认证但未获仓库读取权限时为 `404`。
- `PRIVATE`：与 HIDDEN 的协议防枚举策略相同，供产品表达明确的仅成员仓库；仍挂在 `resource_type=git` 的公开空间资源容器，不会转换为空间 `private` 类型。

`allow_public_upload` 是普通公开文件资源的开关，不再授予 Git push 权限。

## HTTPS Git CLI 认证

标准 URL 为 `https://<domain>/git/<repo-slug>.git`。HTTP Basic 的用户名可以是平台账号或 `x-access-token`；密码必须为完整 `pcd_pat_...`，服务不把该用户名作为授权主体。PAT 只保存 SHA-256 哈希，创建响应只显示一次明文。

```bash
git clone https://<domain>/git/<repo-slug>.git
git remote set-url origin https://<domain>/git/<repo-slug>.git
git push -u origin main
# Username: x-access-token
# Password: 完整 PAT
```

PAT scope：`read_repository` 允许 fetch/clone；`write_repository` 同时覆盖读取并允许协议写入；`api` 允许全部 Git 协议动作。PAT 不用于其他平台 API。

## 共享 Object 同步失败恢复

Git Service 先由系统 `git-receive-pack` 写入本地 bare repository，再把可达 Object
写入 Storage Service 的 `/internal/v1/git/objects/{algorithm}/{hash}`。如果 HEAD/PUT
返回非成功状态，服务会记录 Storage Broker 的响应 detail，将仓库标记为 `DEGRADED`
并回滚本次本地/数据库 refs，然后返回 HTTP 503。下一次 push 的
`info/refs?service=git-receive-pack` 会先自动重试该仓库的共享 Object 同步；恢复成功
后即使客户端显示 `Everything up-to-date`，也已经完成了缺失 Object 的修复。

### Storage Service 启动契约

Storage Service 的 Git Object Broker 只注册在模块化 FastAPI 入口 `app.main:app`。开发容器和
生产容器都必须使用该入口；历史 `server:app` 仅是兼容入口，不包含
`/internal/v1/git/objects/{algorithm}/{hash}`。如果 Storage 进程误用历史入口，Git 的 clone/fetch
仍可能从本地 bare 仓库成功，但 push 在 Object HEAD/PUT 阶段会返回 404，Git Service 为保护
共享存储一致性会向客户端返回 503。修复后必须重启 Storage 进程，并确认未认证探针得到 401，
而不是 404；这可以区分“Broker 已加载但缺凭证”和“进程没有加载 Broker”。

## Smart HTTP 与受控 dumb HTTP 矩阵

下表是 Git Service 对外协议面，共 50 项可验证行为。Smart HTTP 协商和 packfile 由系统 `git http-backend` 处理；本服务只负责安全边界、对象同步和审计。

| # | 方法与路径 | 行为 / 授权 |
| ---: | --- | --- |
| 1 | GET `/{repo}.git/info/refs?service=git-upload-pack` | Smart fetch 发现；Fetch 权限 |
| 2 | POST `/{repo}.git/git-upload-pack` | Smart clone/fetch；`application/x-git-upload-pack-request` |
| 3 | GET `/{repo}.git/info/refs?service=git-receive-pack` | push 前发现；PAT/SSH 写权限 |
| 4 | POST `/{repo}.git/git-receive-pack` | Smart push；`application/x-git-receive-pack-request` |
| 5 | GET/HEAD `/{repo}.git/HEAD` | 默认分支引用；Fetch 权限 |
| 6 | GET `objects/info/alternates` | 只读 alternates 兼容端点 |
| 7 | GET `objects/info/http-alternates` | 只读 HTTP alternates 兼容端点 |
| 8 | GET `objects/info/packs` | 只读 pack 索引；空仓库返回 Git 原生空索引 |
| 9 | GET/HEAD `objects/pack/{hash}.pack` | pack 下载，透传 Range |
| 10 | GET/HEAD `objects/pack/{hash}.idx` | pack 索引下载，透传 Range |
| 11 | GET/HEAD `objects/{xx}/{rest}` | Git 标准 loose object 读取 |
| 12 | GET/HEAD `objects/{object-id}` | 安全规范化到标准 loose object 地址 |
| 13 | GET/HEAD `refs/heads/{branch}` | 分支引用读取，分支名须通过 Git ref 校验 |
| 14 | GET/HEAD `refs/tags/{tag}` | 标签引用读取，标签名须通过 Git ref 校验 |
| 15 | GET/HEAD `description` | Git 描述元数据读取 |
| 16 | GET/HEAD `objects/info/commit-graph` | 提交图索引读取 |
| 17 | GET/HEAD `objects/info/commit-graphs/{file}` | 提交图子文件读取 |
| 18 | GET `info/refs` 无 `service` | `400`，不退化为未受控列表 |
| 19 | GET `info/refs?service=invalid` | `400` |
| 20 | 无凭据 upload-pack RPC | `401` + `WWW-Authenticate: Basic realm="Git"`（除匿名 PUBLIC fetch） |
| 21 | 无凭据 receive-pack RPC | `401`；匿名永不 push |
| 22 | 不存在仓库的 fetch discovery | `404` |
| 23 | 不存在仓库的 upload-pack | `404` |
| 24 | 不存在仓库的 receive-pack | `404` |
| 25 | 不存在仓库的 HEAD | `404` |
| 26 | 不存在 pack/idx | Git 后端 `404` |
| 27 | 不存在或非法对象 | `404`；非法路径不进入文件系统 |
| 28 | PUBLIC + browse/download 的匿名 clone | 允许，只有 Fetch |
| 29 | HIDDEN/PRIVATE 未认证访问 | `401`，不返回仓库细节 |
| 30 | HIDDEN/PRIVATE 已认证无 Read | `404`，防止资源枚举 |
| 31 | PUBLIC 已认证无 Read 的 fetch | 按公开策略允许 |
| 32 | receive-pack 的 PAT scope | 必须 `write_repository` 或 `api` |
| 33 | 已认证但无 Write 的 push | PUBLIC 为 `403`；HIDDEN/PRIVATE 为 `404` |
| 34 | upload-pack Content-Type | 只接受 `application/x-git-upload-pack-request` |
| 35 | upload-pack 响应 | Git 原生 `application/x-git-upload-pack-result` |
| 36 | receive-pack Content-Type | 只接受 `application/x-git-receive-pack-request` |
| 37 | receive-pack 响应 | Git 原生 `application/x-git-receive-pack-result` |
| 38 | `Git-Protocol: version=2` | 透传 `git http-backend` 协商 |
| 39 | shallow/depth 请求 | 透传 Git 原生 upload/receive-pack |
| 40 | Range 请求 | 静态 pack/idx/object 透传 `HTTP_RANGE` |
| 41 | PUT `objects/**` | 显式 `405`；Git dumb push 不安全且 Git CLI 正常 push 走 receive-pack |
| 42 | GET `config` | `404`，不暴露 bare repository 内部配置 |
| 43 | 协议响应头 | `X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`、`X-Frame-Options: DENY` |
| 44 | 超大 receive-pack 请求 | `413`，由 `GIT_MAX_PROTOCOL_REQUEST_BYTES` 限制 |
| 45 | Git 子进程超时 | `408` 或断开，由 `GIT_COMMAND_TIMEOUT` 约束 |
| 46 | 协议并发超限 | `429` + `Retry-After` |
| 47 | 成功 receive-pack | 同步 refs、Object 映射、提交索引与统计 |
| 48 | 成功 push 后 | 调用 `git update-server-info` 刷新 dumb HTTP 索引 |
| 49 | 协议成功/拒绝 | 记录仓库审计和安全审计，绝不记录 PAT 明文 |
| 50 | refs 真正变更后 | Outbox 发布 `pcd.git.push.completed.v1` / `git.push.completed` |

路径根可为 `/git` 或 Gateway 兼容入口 `/api/v1/git`。`config` 与 dumb HTTP 写入没有实现为业务 API：这是防止配置泄露与绕过 Smart receive-pack 授权/钩子的安全设计，不影响 Git CLI 的 clone、fetch、pull、push、分支、标签、合并或 rebase。

## SSH 协议

SSH 默认监听 `2222`，URL 为 `ssh://git@<domain>:2222/<repo>.git`；端口为 22 时可使用 `git@<domain>:<repo>.git`。用户先经管理 API 上传 SSH 公钥；每位用户最多 20 个，接受 Ed25519、ECDSA 和至少 3072 位 RSA。

| # | SSH 行为 |
| ---: | --- |
| 1 | 仅接受标准 SSH 公钥认证 |
| 2 | 公钥以 SHA-256 fingerprint 关联用户，服务端不接受 PAT 作为 SSH 密码 |
| 3 | 允许 `git-upload-pack '<slug>.git'` |
| 4 | 允许 `git-receive-pack '<slug>.git'` |
| 5 | shell、PTY、端口转发与其他 exec 命令拒绝 |
| 6 | 命令解析使用严格 slug 正则，拒绝路径穿越/命令注入 |
| 7 | HTTP 与 SSH 复用同一 Authorizer、裸缓存和 Object Store |
| 8 | 无公钥、无效公钥或冷却中来源拒绝并记录安全审计 |
| 9 | SSH 不支持匿名 clone；PUBLIC 也必须有登记公钥 |
| 10 | receive-pack 必须具备仓库 WRITE 权限 |
| 11 | HIDDEN/PRIVATE 无权访问返回 `Repository not found` |
| 12 | 握手 deadline 由 `GIT_SSH_HANDSHAKE_TIMEOUT` 控制 |
| 13 | 单连接只允许一个 Git exec，连接数受 `GIT_MAX_PROTOCOL_CONCURRENT` 限制 |
| 14 | Git 子进程受 `GIT_COMMAND_TIMEOUT` 限制并透传标准 stdout/stderr |
| 15 | 成功 push 使用同一 refs/Object/outbox/update-server-info 流程 |

## 迁移与安全配置

`GIT_AUTO_MIGRATE=true` 会按 `db/migration/V*__*.sql` 的字典序执行并在 `pcd_git_schema_migration` 记录版本。存量部署必须应用 `V2__repository_visibility_and_protocol_security.sql` 和 `V3__repository_social_graph.sql` 后再上线该版本；V3 创建 Star/Fork 关系及计数索引，未迁移时仓库浏览仍可用，但 Star/Fork 写接口会返回服务错误。

| 环境变量 | 默认值 | 用途 |
| --- | ---: | --- |
| `GIT_MAX_PROTOCOL_CONCURRENT` | 64 | HTTP 与 SSH 协议并发总槽位 |
| `GIT_MAX_PROTOCOL_REQUEST_BYTES` | 2 GiB | receive-pack 请求上限 |
| `GIT_MAX_PROTOCOL_RESPONSE_BYTES` | 32 MiB | receive-pack 回包内存上限（超限仍排空） |
| `GIT_COMMAND_TIMEOUT` | 10m | Git 子进程上限 |
| `GIT_SSH_HANDSHAKE_TIMEOUT` | 15s | SSH 握手上限 |
| `GIT_AUTH_FAILURE_LIMIT` | 10 | 单 IP 冷却前的失败次数 |
| `GIT_AUTH_FAILURE_WINDOW` | 5m | 失败计数窗口 |
| `GIT_AUTH_FAILURE_COOLDOWN` | 15m | 认证冷却时间 |

完整 CLI、安全、并发回归脚本位于 `tests/integration`、`tests/security`、`tests/performance`；执行说明见 [协议安全审计报告](../../docs/GIT_PROTOCOL_SECURITY_AUDIT.md)。
