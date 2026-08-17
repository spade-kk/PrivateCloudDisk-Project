# Plugin Runtime 专用沙箱节点部署

生产环境不得把 Runtime 与业务服务放在同一 Docker 主机，也不得向 Runtime 容器挂载
宿主机 `docker.sock`。Runtime 应作为 `pcd-runtime` 非 root 用户的 systemd 服务运行在
专用 Sandbox Node；该节点只运行 gVisor `runsc` 沙箱容器，并通过防火墙仅接受
Automation Service 的私网请求。

部署顺序：

1. 安装 Docker Engine 与 gVisor，执行 `runsc install` 后确认
   `docker info --format '{{json .Runtimes}}'` 包含 `runsc`。
2. 构建 `sandbox/python/Dockerfile` 并以不可变摘要推送/加载到沙箱节点。
3. 创建 `pcd-runtime` 系统用户与 `/var/lib/pcd-runtime/work`（权限 `0700`）。
4. 安装 `seccomp.json`、`runtime.env` 和 Runtime 二进制到示例路径。
5. 使用 `apparmor_parser -r pcd-plugin-sandbox.apparmor` 加载策略。
6. 安装并启动 `pcd-plugin-runtime.service`；在服务网格或反向代理层启用 mTLS。
7. 将主集群的 `PLUGIN_RUNTIME_URL` 指向该私网 HTTPS 地址。

上线门禁必须同时验证：runsc、seccomp、AppArmor、无出站网络、CPU/内存/PID/时间限制、
路径只读与候选输出目录可写。任一项缺失时 Runtime 会拒绝在 production 模式启动。
