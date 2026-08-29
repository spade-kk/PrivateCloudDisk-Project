# path_escape 路径逃逸尝试

- 能力：`pycloud.call_api("api.file.content.get", {"path": "/etc/passwd"})`
- 防护：能力网关拒绝非白名单路径（CAPABILITY_FORBIDDEN），插件返回 blocked
- 用例：需求二 2.12 / 六 6.10 / 八 8.21
