# 公开空间 Git 仓库使用指南

## 创建仓库

创建空间时选择“公开仓库 → Git 仓库”。Platform 先创建空间并保存 `resource_type=git`，随后 Git Service 创建同空间的仓库和默认分支。文件空间仍使用原来的目录、上传、预览和分享页面。

## 克隆与推送

仓库主页显示两个地址：

```bash
git clone https://<domain>/git/<repo-slug>.git
git clone ssh://git@<domain>:2222/<repo-slug>.git
```

HTTPS 使用个人 PAT 作为 Basic Authentication 的密码，用户名只作为 Git 客户端要求的占位值，不参与平台用户身份匹配；可填平台账号名或 `x-access-token`。SSH 需要先在仓库设置页或个人安全设置添加 `ssh-ed25519`/`ecdsa`/`rsa` 公钥。匿名 clone 只有在公开浏览和公开下载都开启时可用。

```bash
git config --global credential.useHttpPath true
git remote add origin https://<domain>/git/<repo-slug>.git  # 没有 origin 时
# 已存在 origin 时改用：git remote set-url origin https://<domain>/git/<repo-slug>.git
git push origin main
# Username: x-access-token（也可以填平台账号名）
# Password: 创建时显示的完整 pcd_pat_... PAT
```

首次 clone 和后续 fetch 使用同一套凭证：

```bash
git clone https://<domain>/git/<repo-slug>.git
git fetch origin
```

Git CLI 询问 `Username` 时填任意非空占位用户名，询问 `Password` 时粘贴完整 PAT，不能只粘贴列表页的 `tokenPrefix`。如果本机缓存了错误凭证，先清除该域名的旧凭证再重新 push，例如本地开发地址执行 `printf 'protocol=http\\nhost=localhost:8080\\n\\n' | git credential reject`。不要把 PAT 写入仓库 URL、脚本或日志；PAT 明文只在创建成功响应显示一次。

管理 API 的仓库更新使用 `PATCH /api/v1/git/repos/{repoId}`；浏览器管理请求携带 JWT，Git CLI 的 Smart HTTP 请求则使用 Basic(PAT) 直达 `/git/{repo-slug}.git/*`，两条认证链路互不混用。

## 页面能力

公开 Git 空间页面支持分支切换、代码树、文本 Blob、README、提交历史和合并请求入口。文件上传按钮不会出现在 Git 空间；源码变更统一由 `git push` 进入仓库，避免把普通文件上传误当作 Git 提交。

## 分支保护与合并请求

仓库管理员可通过管理 API 配置 `refs/heads/main` 等保护模式、是否必须 MR、所需审批数。保护模式下直接 push 会由 bare repo 的 pre-receive hook 拒绝；MR 合并前 Git Service 还会重新读取规则，确认有效审批数且不存在 `CHANGES_REQUESTED`。

## CI/CD 与插件

在仓库页面的“自动化”页签或 Git 仓库管理 API 创建 workflow binding，绑定已发布的 Workflow DSL，并配置 ref pattern。push 后 Git Service 发布 `git.push.completed`，Workflow Service 过滤目标分支并调用既有 CloudFlow/Plugin Runtime；因此云插件、本地插件和工作流模板继续由现有市场/运行时治理，Git 只负责版本源和触发事实。需要从固定分支或 Tag 读取 `plugin.yaml`、Workflow DSL 的适配器应通过 Git 的只读 Blob/README API 获取内容，Git Service 本身不执行不受信任脚本。

## 常见故障

- `401`：PAT scope 不足、PAT 已撤销/过期，或 SSH fingerprint 未登记。
- `403`：空间未启用公开浏览/下载/上传，或仓库级权限不足。
- `503`：Storage Object Broker 不可用或对象同步失败。服务会回滚本次 push 的本地和数据库 refs，避免下一次 Git CLI 被 `Everything up-to-date` 卡住；恢复 Storage 后直接重新执行原来的 `git push` 即可。若是宿主机 `go run`，必须显式配置 `STORAGE_SERVICE_URL`，并让 Git Service 与 Storage Service 使用完全相同的 `PCD_INTERNAL_SERVICE_TOKEN`。
- 空仓库页面显示“暂无提交”：先在本地创建首个 commit，再 push 到默认分支。
