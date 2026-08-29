#!/usr/bin/env bash
set -euo pipefail

# [REQ-GIT-AUDIT-5.29] 多用户/CI 式并发推送不同分支，验证 receive-pack、仓库锁、
# Object 同步和 outbox 不互相覆盖。必须使用专门压测仓库，默认拒绝写入。
: "${GIT_REPO_BASE_URL:?例如 https://api.example.com/git}"
: "${GIT_REPO_SLUG:?待测试仓库 slug}"
: "${GIT_AUTH_HEADER:?例如 Basic <base64(x-access-token:pcd_pat_...)>}"
: "${GIT_TEST_WRITE:?必须显式设置为 true}"
if [[ "$GIT_TEST_WRITE" != "true" ]]; then
  echo "GIT_TEST_WRITE must be true for concurrent push verification" >&2
  exit 2
fi

CLIENTS="${GIT_PUSH_CLIENTS:-20}"
CONCURRENCY="${GIT_PUSH_CONCURRENCY:-5}"
if ! [[ "$CLIENTS" =~ ^[1-9][0-9]*$ && "$CONCURRENCY" =~ ^[1-9][0-9]*$ ]]; then
  echo "GIT_PUSH_CLIENTS and GIT_PUSH_CONCURRENCY must be positive integers" >&2
  exit 2
fi
# [FIX-GIT-PERF-PUSH-20260817]
# 原实现把每个客户端交给 xargs -I sh -c。macOS 的 xargs 在继承较大环境或
# 认证 Header 时可能报“command line cannot be assembled, too long”，并且
# http.extraHeader 还要求完整的 `Authorization: Basic ...` Header。
# 新行为使用 Bash 有界 worker 池，仅把客户端序号作为函数参数，并统一规范化
# Authorization；不改变并发数量和测试数据语义。
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

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/pcd-git-push-perf.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT
remote="${GIT_REPO_BASE_URL%/}/${GIT_REPO_SLUG}.git"
started_at="$(date +%s)"

run_client() {
  local index="$1"
  local clone="$work_dir/client-${index}"
  local branch="pcd-load-${index}-$(date +%s)"
  git -c "http.extraHeader=${GIT_EXTRA_AUTH_HEADER}" clone --depth=1 "$remote" "$clone" >/dev/null 2>&1
  git -C "$clone" config user.name "PrivateCloudDisk Load Test"
  git -C "$clone" config user.email "git-load-test@example.invalid"
  git -C "$clone" checkout -b "$branch" >/dev/null
  printf "parallel push %s\n" "$index" > "$clone/.pcd-load-test"
  git -C "$clone" add .pcd-load-test
  git -C "$clone" commit -m "test: concurrent push ${index}" >/dev/null
  git -C "$clone" -c "http.extraHeader=${GIT_EXTRA_AUTH_HEADER}" push -u origin "$branch" >/dev/null
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
echo "Git parallel push completed: clients=$CLIENTS concurrency=$CONCURRENCY elapsed=${elapsed_seconds}s"
