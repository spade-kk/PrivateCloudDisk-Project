#!/usr/bin/env bash
set -euo pipefail

# [REQ-GIT-AUDIT-3.1~3.15/5.6/5.25] 独立 SSH 协议回归。调用前先通过 Git 管理 API
# 登记公钥，并以 GIT_SSH_COMMAND 指向该私钥；脚本不回显私钥或令牌。
: "${GIT_SSH_REMOTE:?例如 ssh://git@example.com:2222/demo.git}"
: "${GIT_TEST_WRITE:?必须显式设置为 true，避免误写远端仓库}"
if [[ "$GIT_TEST_WRITE" != "true" ]]; then
  echo "GIT_TEST_WRITE must be true for SSH push verification" >&2
  exit 2
fi

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/pcd-git-ssh-it.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT
git clone "$GIT_SSH_REMOTE" "$work_dir/clone"
git -C "$work_dir/clone" config user.name "PrivateCloudDisk SSH Test"
git -C "$work_dir/clone" config user.email "git-ssh-test@example.invalid"
branch="pcd-ssh-audit-$(date +%s)"
git -C "$work_dir/clone" checkout -b "$branch"
printf 'ssh protocol test\n' > "$work_dir/clone/.pcd-ssh-protocol-test"
git -C "$work_dir/clone" add .pcd-ssh-protocol-test
git -C "$work_dir/clone" commit -m "test: SSH upload-pack and receive-pack"
git -C "$work_dir/clone" push -u origin "$branch"
git -C "$work_dir/clone" fetch --all --tags
echo "Git SSH protocol regression passed: $GIT_SSH_REMOTE"
