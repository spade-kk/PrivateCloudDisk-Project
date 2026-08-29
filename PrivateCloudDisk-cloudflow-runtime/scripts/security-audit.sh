#!/usr/bin/env bash
# [19.20/19.28] 依赖库安全扫描 + 安全边界测试（发布前安全检查脚本）。
#
# 用法：
#   scripts/security-audit.sh            # 全量（依赖扫描若工具可用 + 边界测试）
#   scripts/security-audit.sh --tests    # 仅安全边界测试（CI 最低要求）
#
# 说明：
# - 依赖扫描优先使用 `cargo audit`（rustsec 数据库）；未安装时降级打印手动指引，
#   不阻塞其余检查。生产 CI 建议安装 rustsec 工具链后接入。
# - 安全边界测试（YAML 炸弹/表达式超长/求值沙箱白名单/HTTP 越权路径泄露）
#   位于 tests/cloudflow_security_bounds.rs，任何版本必须通过。

set -euo pipefail
cd "$(dirname "$0")/.."

only_tests=0
for arg in "$@"; do
  case "$arg" in
    --tests) only_tests=1 ;;
    *) echo "未知参数：$arg" >&2; exit 2 ;;
  esac
done

echo "==> [1] 安全边界测试（tests/cloudflow_security_bounds.rs）"
cargo test --test cloudflow_security_bounds -- --nocapture

if [ "$only_tests" -eq 0 ]; then
  echo "==> [2] 依赖安全扫描（cargo audit）"
  if command -v cargo-audit >/dev/null 2>&1 || cargo audit --version >/dev/null 2>&1; then
    cargo audit
  else
    echo "未安装 cargo-audit，跳过自动扫描。"
    echo "手动检查：rustup component add clippy; cargo install cargo-audit; cargo audit"
    echo "（依赖安全扫描在 CI 中接入 rustsec 数据库后应作为硬性门禁）"
  fi

  echo "==> [3] 表达式白名单与 YAML 护栏常量一致性（源码级断言）"
  rg -q "MAX_YAML_SOURCE_BYTES: usize = 1024 \* 1024" src/yaml/convert.rs
  rg -q "MAX_YAML_DEPTH: usize = 100" src/yaml/convert.rs
  rg -q "MAX_YAML_NODES: usize = 100_000" src/yaml/convert.rs
  rg -q "MAX_EXPRESSION_CHARS: usize = 16_384" crates/cloudflow-engine-core/src/expression/parser.rs
  echo "安全护栏常量校验通过。"
fi

echo "==> 安全检查完成。"
