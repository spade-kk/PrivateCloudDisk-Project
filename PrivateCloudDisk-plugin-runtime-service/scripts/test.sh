#!/usr/bin/env sh
# Plugin Runtime 测试入口（9.9/9.10/9.22）：
#   scripts/test.sh            -> 仅单元测试（-short），不依赖 Docker
#   scripts/test.sh --integration -> 单元 + Docker 集成测试（需本机 Docker/daemon 可用）
set -eu
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

printf '==> 单元测试 go test -short ./...\n'
go test -short -count=1 ./...

printf '==> JS AST 校验器规则（需求五）\n'
node validator/test_validate_js.mjs

printf '==> Python AST 校验器规则（需求四）\n'
python3 validator/test_validate_python.py

printf '==> pycloud SDK 与运行时受限 Python 层单测（需求六 / 36.x）\n'
PYTHONPATH=sandbox/python python3 -m unittest discover -s sandbox/python/tests -p 'test_*.py'

printf '==> 沙箱加固策略产物与 deploy/ 一致\n'
sh scripts/check_sandbox_profiles.sh

if [ "${1:-}" = "--integration" ]; then
  printf '==> Docker 集成测试 go test -tags=integration ./...\n'
  go test -tags=integration -count=1 ./...
else
  printf '==> （可选）集成测试：scripts/test.sh --integration\n'
fi
