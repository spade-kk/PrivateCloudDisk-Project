# Plugin Runtime 专用沙箱节点部署

生产环境不得把 Runtime 与业务服务放在同一 Docker 主机，也不得向 Runtime 容器挂载
宿主机 `docker.sock`。Runtime 应作为 `pcd-runtime` 非 root 用户（uid/gid `65532`）的
systemd 服务运行在专用 Sandbox Node；该节点只运行 gVisor `runsc` 沙箱容器，并通过
防火墙仅接受 Automation Service 的私网请求。

## 运行时如何按 Docker Engine + gVisor 策略运行容器

`internal/sandbox/runner.go` 的 `containerArgs` 由纯函数构造，启动参数已锁定：
`--runtime <SandboxRuntime>`（生产必须 `runsc`）、`--network none`、`--read-only`、
`--ipc=private --cgroupns=private`（PID/UTS 在 Docker 29.x 起默认私有，不再显式传
`--pid=private`/`--uts=private`）、`--cap-drop ALL`、`--security-opt no-new-privileges`、
`--user uid:gid`（默认 `65532:65532`，须与运行 Runtime 的宿主用户一致）、CPU/内存/
swap/PID/`nofile` 限制、`tmpfs` 临时目录、只读绑定插件/输入/上下文与可写输出目录。

生产门禁在 `internal/config/config.go#Load()` 强制：`RUNTIME_ENV=production` 时
必须 `PLUGIN_SANDBOX_RUNTIME=runsc`、配置 seccomp 与 AppArmor、关闭 Debug、
`PLUGIN_SANDBOX_NETWORK=none`。任一项缺失 Runtime 拒绝启动。

## 部署顺序

1. 安装 Docker Engine 与 gVisor：
   - `wget https://storage.googleapis.com/gvisor/releases/release/latest/bin/runsc`，
     `install -o root -g root -m 0755 runsc /usr/local/bin/runsc`
   - `runsc install`（或写入 `/etc/docker/daemon.json` 的 `runtimes`），随后
     `docker info --format '{{json .Runtimes}}'` 必须包含 `"runsc"`。
2. 构建沙箱镜像（含 seccomp/AppArmor 策略产物，见 `sandbox/python/Dockerfile`）：
   `docker build -t pcd/plugin-sandbox-python:0.1.2 sandbox/python`
   推送并以不可变摘要加载；在 `plugin-runtime.env.example` 中填写
   `PLUGIN_SANDBOX_IMAGE_DIGEST` 与 `PLUGIN_SANDBOX_REQUIRE_DIGEST=true`。
3. 创建 `pcd-runtime` 系统用户（uid/gid `65532`）、`/var/lib/pcd-runtime/work` 与
   `/run/pcd/plugins`（均 `0700`，属主 `65532:65532`）。`/run/pcd/plugins` 只能由 Runtime
   管理；每个 Socket 在创建后为 `0660`、group=`65532`，与插件容器 `PLUGIN_SANDBOX_USER` 的 gid 对齐。
4. 安装 `seccomp.json` 到宿主路径（如 `/etc/pcd-plugin-runtime/seccomp.json`），
   并把 `runtime.env`、Runtime 二进制和 `validator/` 相关文件复制到对应路径。
5. 加载 AppArmor 策略：`apparmor_parser -r pcd-plugin-sandbox.apparmor`；
   确认已注册：`apparmor_status | grep pcd-plugin-sandbox`。
6. 安装并启动 `pcd-plugin-runtime.service`；在服务网格或反向代理层启用 mTLS。
7. 将主集群的 `PLUGIN_RUNTIME_URL` 指向该私网 HTTPS 地址。

## 镜像版本与能力通道

- `pcd/plugin-sandbox-python:0.1.2`：内置 pycloud 能力通道 SDK（`call_api`/`user_info`/
  `space_members_list`/`notification_send`/`file.metadata`/`file.move`），并通过
  `/opt/pcd-sdk/bin/runner.py` 受控启动器 + `restricted.py` 运行时受限 Python 层执行
  （白名单导入/危险内置删除/逃逸链改写/PEP 578 审计钩子，36.x）。
  验证：`docker run --rm --entrypoint python3 pcd/plugin-sandbox-python:0.1.2 -c "import sys;sys.path.insert(0,'/opt/pcd-sdk');import pycloud;print('call_api' in dir(pycloud))"` → `True`。
- CI 每次从 `sandbox/python` 源码重建，随本仓库演进，无需手工镜像漂移。

### 每实例 Unix Socket 挂载

Runtime 会在容器启动前创建不可预测的 `plugin-<session>.sock`，通过 bind mount 映射为容器内固定的
`/runtime/runtime.sock`，并以 `runner.py --pcd-instance-id/--pcd-instance-token` 传递一次性凭据。Token
不可放进环境变量、`context.json`、插件包或挂载文件。插件容器保持 `--network none`、不可访问 Socket 根目录、
Docker Socket 或宿主 `/run`；`readonly` mount 只防止篡改挂载点元数据，不阻止对 Unix Socket 的 `connect()`。

Runtime 到 Capability Hub 仍是内部服务网络调用，当前由 `X-PCD-Service-Token` 认证；部署应在私网与服务网格层
启用 mTLS。生产启动会拒绝缺少 `CAPABILITY_HUB_URL` 的配置，且没有文件轮询回退。

运维通过带 `X-PCD-Service-Token` 的 `GET /internal/v1/metrics/uds` 读取聚合 Socket 会话数、连接数、请求数、
失败请求数和错误率；禁止采集或记录单实例 Socket 路径、Token、用户/空间标识或请求参数。

## 沙箱测试与回归

- 单元/AST/SDK：`make test-unit`；真实 Docker 集成：`make test-integration`。
- realworld 业务插件与基线：`docs/PLUGIN_RUNTIME_TESTING.md`、`docs/PLUGIN_RUNTIME_COVERAGE_MATRIX.md`。
- 每次合并前必须：包无漂移（`make test-packages` 后 `git diff --exit-code testdata/packages/`）、
  基线无漂移（`make baseline` 后 `git diff --exit-code testdata/expected/`）。

## 版本同步（seccomp/AppArmor）

`deploy/seccomp.json` 与 `deploy/pcd-plugin-sandbox.apparmor` 是权威源，同时复制进
`sandbox/python/` 随沙箱镜像发布、复制进 Runtime 镜像 `/opt/pcd-plugin-runtime/profiles/`
作基线。CI 用 `scripts/check_sandbox_profiles.sh` 保持一致。注意：Docker 守护进程按
**节点宿主路径**读取 seccomp；AppArmor 必须在节点预加载；镜像内产物仅作随版本基线。

## 本地开发与集成测试主机要求

- 单元测试 `sh scripts/test.sh` 无需 Docker。
- Docker 集成测试 `sh scripts/test.sh --integration` 需要 Docker CLI/daemon；
  本机未注册 runsc 时用 `PLUGIN_SANDBOX_RUNTIME=runc`（development 配置），
  生产 runsc/seccomp/AppArmor 门禁仍由 `config.Load()` 强制，不受影响。
- `PLUGIN_SANDBOX_IMAGE`、`RUNTIME_DOCKER_BINARY` 支持用环境变量覆盖镜像与 CLI。

上线门禁必须同时验证：runsc、seccomp、AppArmor、无出站网络、CPU/内存/PID/时间限制、
路径只读与候选输出目录可写。任一项缺失时 Runtime 会拒绝在 production 模式启动。
