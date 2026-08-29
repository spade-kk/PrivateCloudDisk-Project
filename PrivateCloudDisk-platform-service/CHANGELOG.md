# PrivateCloudDisk-platform-service 更新日志

## [0.1.1-SNAPSHOT] - 2026-08-24

- 修复 `DirectoryTreeServiceImpl.findUserNodesByNodeIdPaged` 返回 `null` 导致
  `/business/internal/capability/files/list` 在读取分页结果时抛出 500 的问题。
- 分页查询复用既有空间上下文权限边界，并补充关键词、类型、排序、页码和页大小处理。
- 新增能力数据面文件列表回归测试。

## [0.1.0-SNAPSHOT] - 2026-08-21

- **新增能力中心数据面内部接口** `InternalCapabilityController`（`/business/internal/capability/*`）：
  文件元数据/列表/搜索/标签、异步安全扫描（发布 `file.scan.requested`）、空间信息/成员、
  用户信息（脱敏）、创建分享；每个端点 `resolveContext → requireOperation → requireFileInCurrentSpace`
  二次鉴权，供 workflow-service 能力中心调用。
- **新增 RabbitMQ 队列/绑定**：`pcd.file.scan.requested.queue` / `file.scan.requested`。

## [0.0.1-SNAPSHOT] - 基线

用户、文件元数据、目录树、空间、分享、标签、收藏、回收站、配额及 Storage/Automation 内部协调。
