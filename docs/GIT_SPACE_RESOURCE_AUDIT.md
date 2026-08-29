# 公开空间 Git 资源抽象层审计报告

审计范围：Space/公开空间、Platform Service、Storage Service、Gateway、Vue 公开空间页面、Plugin/Workflow/CloudFlow 集成和 Docker/MySQL 编排。审计基线为当前工作区代码；本报告不把尚未运行的外部依赖集成测试描述为已通过。

## 结论

现有公开空间原本是“空间 + 文件根目录”的专用实现：Platform 的公开空间接口直接查询 `file/folder` 数据，创建空间时无条件创建文件根节点，Vue 页面也固定调用 root/children/readme 和文件上传接口。它不能承载 Git 引用、Object、Commit 索引。

Storage Service 已具备可复用的 Provider、Local/MinIO、Range 读取、分片上传和哈希校验能力，但业务代码不应直接拼接文件路径。因此本次新增了受内部服务令牌保护的 Git Object Broker：Git Service 只使用 `git/objects/{algorithm}/{xx}/{rest}` 命名空间，StorageProvider 负责实际物理读写。

## 复用与扩展矩阵

| 领域 | 现有能力 | 本次处理 | 边界 |
| --- | --- | --- | --- |
| Space | 空间身份、所有者、可见性、公开浏览/下载/上传开关 | 增加 `resource_type` 和 Provider 注册表 | Space 不解析 Git Object |
| File | 分片、合并、SHA 校验、Local/MinIO、Range | 新增内部 Git Object Broker | Git 仓库不写普通文件元数据表 |
| Gateway | JWT、路由、限流 | Smart HTTP 三端点 Basic/匿名白名单；管理 API 仍 JWT | 不在网关实现 Git 权限 |
| Git | 无 | 新建独立 Go 微服务、bare cache、Git 原生协议、SSH、索引、MR | Git Service 不持有 Platform root 凭证 |
| Workflow | CloudEvent/Outbox/CloudFlow Runtime | `git.push.completed` 事件和 workflow binding 消费 | 不新建 Actions 执行器 |
| Plugin | Plugin/Runtime/市场元数据链路 | 通过 push 事件和工作流绑定接入 | 插件沙箱仍由既有 Runtime 执行 |

## 主要风险与修复

1. Gateway 原先只接受 Bearer JWT，标准 Git 客户端的 Basic/PAT 和匿名 clone 会在边缘层失败；已只对白名单 Smart HTTP 路径放行，授权仍在 Git Service 完成。
2. Git Object 如果直接写文件系统会绕过 MinIO/本地 Provider；已使用内部 Broker、传输 SHA-256、Git canonical hash、压缩大小/解压大小和 Range 边界校验。
3. 仓库删除只软删业务行会使全局 Object 引用计数虚高；已在同一事务中释放 repo-object/ref/commit 索引并递减引用计数，零引用物理 Object 留给后续 GC。
4. 受保护分支的 `required_approvals` 不能只展示；合并前已按 Git glob 匹配目标 ref，并校验有效 APPROVED 数与 CHANGES_REQUESTED。
5. 仓库权限的 `TEAM` 记录不能只写入数据库；已通过 Platform 内部团队/企业空间成员接口实时校验，Git Service 不复制成员事实。
6. Git 服务端生成附注 Tag/Merge Commit 需要身份；bare repo 初始化时配置固定技术身份，不覆盖客户端提交者。

## 尚需外部环境验证

需要 MySQL、RabbitMQ、Platform、Storage、Gateway、SSH 端口和一个真实 Git 客户端组成部署环境后执行集成脚本；本地已完成 Go 编译和 Vue 生产构建，但没有把“无依赖环境”误报为端到端通过。
