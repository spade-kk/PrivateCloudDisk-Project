#!/usr/bin/env sh
# 校验 sandbox 镜像内策略产物与 deploy/ 权威源保持一致（10.21/8.15 文档示例与代码一致）。
set -eu
root="$(cd "$(dirname "$0")/.." && pwd)"
while read -r src rel_dst; do
  [ -z "${src:-}" ] && continue
  if ! cmp -s "$root/$src" "$root/$rel_dst"; then
    echo "策略产物不一致：$src != $rel_dst" >&2
    exit 1
  fi
done <<'LIST'
deploy/seccomp.json sandbox/python/seccomp.json
deploy/pcd-plugin-sandbox.apparmor sandbox/python/pcd-plugin-sandbox.apparmor
LIST
echo "sandbox 加固策略产物与 deploy/ 一致"
