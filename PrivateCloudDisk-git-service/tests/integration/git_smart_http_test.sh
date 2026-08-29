#!/usr/bin/env bash
set -euo pipefail

# [REQ-GIT-AUDIT-5.1~5.30] 真实服务 Git CLI 回归：覆盖初始 push、clone/fetch/pull、
# 分支、标签、合并前后的远端同步、浅克隆、对象读取和可选 SSH。脚本默认不写入任何
# 仓库；只有明确 GIT_TEST_WRITE=true 才执行会改变远端 refs 的场景，避免误测生产仓库。
: "${GIT_REPO_BASE_URL:?例如 https://api.example.com/git}"
: "${GIT_REPO_SLUG:?待测试仓库 slug}"
: "${GIT_AUTH_HEADER:?例如 Basic <base64(x-access-token:pcd_pat_...)>}"

# [FIX-GIT-CLI-AUTH-20260817]
# 原实现直接把 GIT_AUTH_HEADER 拼进 http.extraHeader 和 curl；Git 配置实际要求
# `Authorization: Basic <base64(...)>`，而用户误传直接 PAT 时还可能被凭据助手的旧值
# 遮蔽，导致测试结论失真。新行为在本地规范化完整 Header、Basic 值或直接 PAT，
# 同一认证值同时用于 Git CLI 和 curl，且不打印令牌。影响范围仅为集成测试脚本。
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

WRITE_ENABLED="${GIT_TEST_WRITE:-false}"
SSH_REMOTE="${GIT_SSH_REMOTE:-}"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/pcd-git-it.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT
remote="${GIT_REPO_BASE_URL%/}/${GIT_REPO_SLUG}.git"

git_http() { git -c "http.extraHeader=${GIT_EXTRA_AUTH_HEADER}" "$@"; }
assert_ref() { git_http ls-remote "$remote" "$1" | grep -q .; }

echo "[1/12] discover refs and clone through Smart HTTP"
git_http ls-remote --symref "$remote" HEAD >/dev/null
git_http clone "$remote" "$work_dir/clone"
git -C "$work_dir/clone" config user.name "PrivateCloudDisk Git CLI Test"
git -C "$work_dir/clone" config user.email "git-cli-test@example.invalid"

echo "[2/12] verify history, file object and shallow clone"
git -C "$work_dir/clone" log -1 --format=%H >/dev/null
head_ref="$(git -C "$work_dir/clone" rev-parse --abbrev-ref HEAD)"
git_http clone --depth 1 --branch "$head_ref" "$remote" "$work_dir/shallow"
git -C "$work_dir/shallow" log -1 --format=%H >/dev/null

echo "[3/12] fetch all refs and tags"
git -C "$work_dir/clone" -c "http.extraHeader=${GIT_EXTRA_AUTH_HEADER}" fetch --all --tags --prune

if [[ "$WRITE_ENABLED" == "true" ]]; then
  test_branch="pcd-cli-audit-$(date +%s)"
  test_tag="pcd-cli-audit-v$(date +%s)"
  echo "[4/12] push a feature branch"
  git -C "$work_dir/clone" checkout -b "$test_branch"
  printf 'Git CLI audit %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$work_dir/clone/.pcd-git-cli-audit"
  git -C "$work_dir/clone" add .pcd-git-cli-audit
  git -C "$work_dir/clone" commit -m "test: Git Smart HTTP audit"
  git -C "$work_dir/clone" -c "http.extraHeader=${GIT_EXTRA_AUTH_HEADER}" push -u origin "$test_branch"
  assert_ref "refs/heads/${test_branch}"

  echo "[5/12] create and fetch an annotated tag"
  git -C "$work_dir/clone" tag -a "$test_tag" -m "Git protocol audit tag"
  git -C "$work_dir/clone" -c "http.extraHeader=${GIT_EXTRA_AUTH_HEADER}" push origin "$test_tag"
  git -C "$work_dir/clone" -c "http.extraHeader=${GIT_EXTRA_AUTH_HEADER}" fetch --tags origin
  git -C "$work_dir/clone" rev-parse "$test_tag" >/dev/null

  echo "[6/12] pull and prove branch/ref visibility"
  git -C "$work_dir/clone" checkout "$head_ref"
  git -C "$work_dir/clone" -c "http.extraHeader=${GIT_EXTRA_AUTH_HEADER}" pull --ff-only origin "$head_ref"
  git -C "$work_dir/clone" branch -r | grep -q "origin/${test_branch}"

  if [[ -n "$SSH_REMOTE" ]]; then
    echo "[7/12] clone/fetch/push through SSH (uses GIT_SSH_COMMAND if supplied)"
    git clone "$SSH_REMOTE" "$work_dir/ssh-clone"
    git -C "$work_dir/ssh-clone" fetch --all --tags
    git -C "$work_dir/ssh-clone" checkout -b "${test_branch}-ssh"
    printf 'SSH Git CLI audit\n' > "$work_dir/ssh-clone/.pcd-git-ssh-audit"
    git -C "$work_dir/ssh-clone" add .pcd-git-ssh-audit
    git -C "$work_dir/ssh-clone" -c user.name='PrivateCloudDisk SSH Test' -c user.email='git-ssh-test@example.invalid' commit -m 'test: SSH protocol audit'
    git -C "$work_dir/ssh-clone" push -u origin "${test_branch}-ssh"
  fi
else
  echo "[4-7/12] write, branch, tag and SSH push tests skipped (set GIT_TEST_WRITE=true)"
fi

echo "[8/12] verify Git Protocol v2 negotiation"
GIT_PROTOCOL=version=2 git -c "http.extraHeader=${GIT_EXTRA_AUTH_HEADER}" ls-remote "$remote" >/dev/null

echo "[9/12] verify read-only HTTP HEAD/Range endpoints through service"
# [REQ-GIT-AUDIT-5.4/5.5] HTTP CLI 场景始终携带同一 PAT，因而可同时验证
# PUBLIC、HIDDEN 与 PRIVATE 仓库；匿名访问策略由安全脚本单独覆盖。
curl -fsSI -H "Authorization: ${GIT_AUTHORIZATION_VALUE}" "${remote}/HEAD" >/dev/null
curl -fsS -H "Authorization: ${GIT_AUTHORIZATION_VALUE}" -H 'Range: bytes=0-15' "${remote}/HEAD" >/dev/null

echo "[10/12] verify local merge/rebase compatibility before remote push"
git -C "$work_dir/clone" checkout "$head_ref"
git -C "$work_dir/clone" merge --ff-only "origin/${head_ref}" >/dev/null
git -C "$work_dir/clone" rebase "origin/${head_ref}" >/dev/null

echo "[11/12] verify local object inspection"
git -C "$work_dir/clone" show "HEAD^{commit}" >/dev/null
first_path="$(git -C "$work_dir/clone" ls-tree -r --name-only HEAD | head -n 1 || true)"
if [[ -n "$first_path" ]]; then
  git -C "$work_dir/clone" show "HEAD:${first_path}" >/dev/null
fi

echo "[12/12] Git CLI Smart HTTP regression passed: $remote"
