#!/usr/bin/env bash
set -euo pipefail

# [REQ-GIT-AUDIT-6.1~6.25] 在隔离测试环境执行协议安全回归。GIT_REPO_BASE_URL 必须
# 已包含 /git，例如 https://api.example.com/git；脚本绝不打印 GIT_AUTH_HEADER 内容。
: "${GIT_REPO_BASE_URL:?例如 https://api.example.com/git}"
: "${GIT_REPO_SLUG:?待测试仓库 slug}"
AUTH_HEADER="${GIT_AUTH_HEADER:-}"
UNAUTHORIZED_HEADER="${GIT_UNAUTHORIZED_AUTH_HEADER:-}"
EXPECT_ANON_FETCH="${GIT_EXPECT_ANON_FETCH_STATUS:-200}"
EXPECT_AUTHORIZED_FETCH="${GIT_EXPECT_AUTHORIZED_FETCH_STATUS:-200}"
# Gateway 默认会在无效 POST 方法进入 Git Service 前由 AuthGlobalFilter 返回 401；
# 直连 Git Service 时可设为 405，测试仍能覆盖服务端协议校验。
EXPECT_INVALID_POST="${GIT_EXPECT_INVALID_POST_STATUS:-401}"

# [FIX-GIT-SECURITY-TEST-20260817]
# 原实现只把 GIT_AUTH_HEADER 原样拼接为 curl Header。通过 Gateway 测试时，
# POST /info/refs 没有 Git 白名单，缺少 JWT/Authorization 会先得到 401，不能
# 误报为 Git Service 的 405；同时 Git extraHeader 需要完整的 Header 名称。
# 新行为兼容 Authorization Header、Basic 值和直接 PAT，且只在进程内规范化，不打印令牌。
normalize_auth_header() {
  local raw="$1"
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

AUTHORIZATION_VALUE=""
UNAUTHORIZED_AUTHORIZATION_VALUE=""
if [[ -n "$AUTH_HEADER" ]]; then
  AUTHORIZATION_VALUE="$(normalize_auth_header "$AUTH_HEADER")"
fi
if [[ -n "$UNAUTHORIZED_HEADER" ]]; then
  UNAUTHORIZED_AUTHORIZATION_VALUE="$(normalize_auth_header "$UNAUTHORIZED_HEADER")"
fi

remote="${GIT_REPO_BASE_URL%/}/${GIT_REPO_SLUG}.git"
status() { curl --path-as-is -ksS -o /dev/null -w '%{http_code}' "$@"; }
expect_status() {
  local expected="$1"; shift
  local actual
  actual="$(status "$@")"
  if [[ "$actual" != "$expected" ]]; then
    echo "expected HTTP ${expected}, got ${actual}: $*" >&2
    exit 1
  fi
}
expect_one_of() {
  local allowed="$1"; shift
  local actual
  actual="$(status "$@")"
  if [[ " ${allowed} " != *" ${actual} "* ]]; then
    echo "expected one of [${allowed}], got ${actual}: $*" >&2
    exit 1
  fi
}

echo "[1] public/hidden anonymous fetch policy"
expect_status "$EXPECT_ANON_FETCH" "${remote}/info/refs?service=git-upload-pack"
expect_status 401 "${remote}/info/refs?service=git-receive-pack"
expect_status 401 -X POST -H 'Content-Type: application/x-git-receive-pack-request' --data-binary '' "${remote}/git-receive-pack"

echo "[2] Smart HTTP request format and service validation"
expect_status 400 "${remote}/info/refs"
expect_status 400 "${remote}/info/refs?service=git-unknown"
# Gateway 的 AuthGlobalFilter 对 POST /info/refs 未配置 Git 协议白名单，因此会先
# 返回 401；直连 Git Service 时设置 GIT_EXPECT_INVALID_POST_STATUS=405，验证协议层。
expect_status "$EXPECT_INVALID_POST" -X POST "${remote}/info/refs?service=git-upload-pack"
expect_status 415 -X POST --data-binary '' "${remote}/git-upload-pack"
expect_status 405 -X GET "${remote}/git-upload-pack"

echo "[3] static protocol endpoints and required security headers"
expect_status "$EXPECT_ANON_FETCH" "${remote}/HEAD"
expect_status "$EXPECT_ANON_FETCH" "${remote}/objects/info/packs"
headers="$(curl -ksSI "${remote}/HEAD")"
printf '%s\n' "$headers" | grep -qi '^X-Content-Type-Options: nosniff'
printf '%s\n' "$headers" | grep -qi '^Referrer-Policy: no-referrer'

echo "[4] path traversal, config exposure and invalid object rejection"
expect_status 404 "${remote}/objects/../../config"
expect_status 404 "${remote}/config"
expect_status 404 "${remote}/objects/not-an-object"
expect_status 405 -X PUT --data-binary 'malicious' "${remote}/objects/0123456789012345678901234567890123456789"

echo "[5] repository and branch enumeration controls"
expect_status 404 "${GIT_REPO_BASE_URL%/}/does-not-exist.git/info/refs?service=git-upload-pack"
expect_status 404 "${remote}/refs/heads/../main"
expect_status 404 "${remote}/refs/tags/.."

if [[ -n "$AUTH_HEADER" ]]; then
  echo "[6] PAT read/write compatibility and protocol v2"
  expect_status "$EXPECT_AUTHORIZED_FETCH" -H "Authorization: ${AUTHORIZATION_VALUE}" "${remote}/info/refs?service=git-upload-pack"
  expect_status "$EXPECT_AUTHORIZED_FETCH" -H "Authorization: ${AUTHORIZATION_VALUE}" -H 'Git-Protocol: version=2' "${remote}/info/refs?service=git-upload-pack"
fi
if [[ -n "$UNAUTHORIZED_HEADER" ]]; then
  echo "[7] authenticated horizontal-authority rejection"
  expect_one_of '403 404' -H "Authorization: ${UNAUTHORIZED_AUTHORIZATION_VALUE}" "${remote}/info/refs?service=git-upload-pack"
  expect_one_of '403 404' -H "Authorization: ${UNAUTHORIZED_AUTHORIZATION_VALUE}" "${remote}/info/refs?service=git-receive-pack"
fi

echo "Git protocol security regression passed: $remote"
