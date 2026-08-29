#!/usr/bin/env bash
set -euo pipefail

# [REQ-GIT-AUDIT-5.28/5.29] 默认 100 个并发浅克隆；只测真实服务，不把本地编译
# 结果当作性能结论。任一 worker 失败会使脚本失败，输出 elapsed 秒数便于与容量基线比较。
: "${GIT_REPO_BASE_URL:?例如 https://api.example.com/git}"
: "${GIT_REPO_SLUG:?待测试仓库 slug}"
: "${GIT_AUTH_HEADER:?例如 Basic <base64(user:pat)>}"
CLIENTS="${GIT_CLONE_CLIENTS:-100}"
CONCURRENCY="${GIT_CLONE_CONCURRENCY:-20}"
if ! [[ "$CLIENTS" =~ ^[1-9][0-9]*$ && "$CONCURRENCY" =~ ^[1-9][0-9]*$ ]]; then
  echo "GIT_CLONE_CLIENTS and GIT_CLONE_CONCURRENCY must be positive integers" >&2
  exit 2
fi

# [FIX-GIT-PERF-CLONE-20260817]
# 原实现通过 xargs -I sh -c 启动客户端，并将 remote、临时目录和认证信息全部
# export 到子进程。在 macOS 上，较大的继承环境可能触发“command line cannot be
# assembled, too long”；同时 http.extraHeader 要求完整的 Header 名称和值。
# 新行为只向 worker 传递客户端序号，并复用标准化后的 Authorization Header，
# 保持并发模型和浅克隆语义不变。
normalize_auth_header() {
  local raw="$GIT_AUTH_HEADER"
  local value="$raw"
  if [[ "$value" == Authorization:* ]]; then
    value="${value#Authorization:}"
    value="${value# }"
  fi
  if [[ "$value" == Basic\ * ]]; then
    local basic_value="${value#Basic }"
    if [[ "$basic_value" == pcd_pat_* ]]; then
      basic_value="$(printf 'x-access-token:%s' "$basic_value" | base64 | tr -d '\n')"
    fi
    printf 'Basic %s' "$basic_value"
    return
  fi
  if [[ "$value" == pcd_pat_* ]]; then
    printf 'Basic %s' "$(printf 'x-access-token:%s' "$value" | base64 | tr -d '\n')"
    return
  fi
  printf '%s' "$value"
}

GIT_AUTHORIZATION_VALUE="$(normalize_auth_header)"
GIT_EXTRA_AUTH_HEADER="Authorization: ${GIT_AUTHORIZATION_VALUE}"

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/pcd-git-perf.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT
remote="${GIT_REPO_BASE_URL%/}/${GIT_REPO_SLUG}.git"
started_at="$(date +%s)"

run_client() {
  local index="$1"
  git -c "http.extraHeader=${GIT_EXTRA_AUTH_HEADER}" \
    clone --depth=1 "$remote" "$work_dir/client-${index}" >/dev/null 2>&1
}

active_pids=()
for index in $(seq "$CLIENTS"); do
  run_client "$index" &
  active_pids+=("$!")
  if (( ${#active_pids[@]} >= CONCURRENCY )); then
    wait "${active_pids[0]}"
    active_pids=("${active_pids[@]:1}")
  fi
done
for pid in "${active_pids[@]}"; do
  wait "$pid"
done

elapsed_seconds="$(( $(date +%s) - started_at ))"
echo "Git parallel clone completed: clients=$CLIENTS concurrency=$CONCURRENCY elapsed=${elapsed_seconds}s"
