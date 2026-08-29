#!/usr/bin/env bash
# CloudFlow 语法覆盖编译门禁。需求关联：CLOUDFLOW-COVERAGE-001。
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_dir="$(mktemp -d)"
trap 'rm -rf "${output_dir}"' EXIT

if [[ -n "${CLOUDFLOWC_BIN:-}" ]]; then
  compiler_bin="${CLOUDFLOWC_BIN}"
  [[ -x "${compiler_bin}" ]] || { echo "CLOUDFLOWC_BIN 不可执行：${compiler_bin}" >&2; exit 2; }
else
  # 始终请求 Cargo 增量构建，避免 target/debug 中的旧二进制掩盖 grammar/IR 改动。
  cargo build --quiet --manifest-path "${root_dir}/Cargo.toml" --bin cloudflowc
  compiler_bin="${root_dir}/target/debug/cloudflowc"
fi

while IFS= read -r -d '' flow_file; do
  base_name="$(basename "${flow_file}" .flow)"
  ir_file="${output_dir}/${base_name}.json"
  "${compiler_bin}" compile "${flow_file}" -o "${ir_file}" --no-color
  python3 "${root_dir}/scripts/validate_coverage_ir.py" "${ir_file}"
done < <(find "${root_dir}/examples/coverage" -type f -name '*.flow' -print0 | sort -z)

echo "CloudFlow coverage: all .flow examples compiled and passed IR schema contract."
