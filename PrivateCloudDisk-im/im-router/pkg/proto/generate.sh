#!/usr/bin/env bash
# ================================================================
# IM Router Protobuf 代码生成脚本
# ================================================================
# 功能：
#   从 im-common 的 proto 定义编译生成 Go 代码（gRPC + MQ 消息体），
#   并将 package 名称统一归一化为 `proto`，输出到 pkg/proto/ 目录。
#
# 背景：
#   im_grpc.proto 与 im_mq.proto 共享同一个 import 路径
#   (privateclouddisk/im-router/pkg/proto)，但 go_package 中指定的
#   包名分别为 `imgrpc` 与 `immq`。直接编译会产生同目录多包名冲突，
#   因此本脚本在生成后通过 sed 将包名统一为 `proto`。
#
# 依赖：
#   - protoc（Protocol Buffers 编译器）
#   - protoc-gen-go（go install google.golang.org/protobuf/cmd/protoc-gen-go@latest）
#   - protoc-gen-go-grpc（go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest）
#
# 用法：
#   ./generate.sh                  # 使用默认 proto 源目录
#   PROTO_SRC_DIR=/path/to/proto ./generate.sh
# ================================================================

set -euo pipefail

# ---- 路径准备 ----
# 当前脚本所在目录（即 pkg/proto/）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 输出目录 = 脚本所在目录
OUT_DIR="$SCRIPT_DIR"
# proto 源文件目录（可通过环境变量覆盖）
PROTO_SRC_DIR="${PROTO_SRC_DIR:-/Users/user/ProgramDir/PrivateCloudDisk-project/PrivateCloudDisk-im/im-common/src/main/proto}"

# 临时目录用于中间产物
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# ---- 日志辅助 ----
log()  { echo "[generate] $*"; }
err()  { echo "[generate][ERROR] $*" >&2; }
fatal(){ err "$*"; exit 1; }

log "proto 源目录: $PROTO_SRC_DIR"
log "输出目录:     $OUT_DIR"

# ---- 依赖检查 ----
command -v protoc >/dev/null 2>&1 || fatal "未找到 protoc，请安装后重试"
command -v protoc-gen-go >/dev/null 2>&1 || fatal \
  "未找到 protoc-gen-go，请运行: go install google.golang.org/protobuf/cmd/protoc-gen-go@latest"
command -v protoc-gen-go-grpc >/dev/null 2>&1 || fatal \
  "未找到 protoc-gen-go-grpc，请运行: go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest"

[ -d "$PROTO_SRC_DIR" ] || fatal "proto 源目录不存在: $PROTO_SRC_DIR"

# ---- 待编译的 proto 文件 ----
# IM Router 仅需要 gRPC 服务定义与 MQ 消息定义，im_protocol_v2.proto
# 不在本模块直接引用，故不参与编译（如需可追加到数组）。
PROTOS=(
  "$PROTO_SRC_DIR/im_grpc.proto"
  "$PROTO_SRC_DIR/im_mq.proto"
)

for p in "${PROTOS[@]}"; do
  [ -f "$p" ] || fatal "proto 文件不存在: $p"
done

# ---- 编译 ----
# paths=source_relative: 输出文件与 proto 源文件保持相同的相对路径，
# 避免按 go_package 自动创建深层目录。
log "开始编译 proto 文件..."
for p in "${PROTOS[@]}"; do
  protoc \
    --proto_path="$PROTO_SRC_DIR" \
    --go_out="$TMP_DIR" \
    --go_opt=paths=source_relative \
    --go-grpc_out="$TMP_DIR" \
    --go-grpc_opt=paths=source_relative \
    "$p"
done

# ---- 包名归一化 ----
# 两个 proto 文件 go_package 包名不同（imgrpc / immq），
# 统一为 proto 以便共存于同一目录。
# 注意：Go 不支持 '#' 注释，故直接替换包名声明，不附加注释行。
log "归一化 package 名称为 proto..."
find "$TMP_DIR" -name '*.pb.go' -print0 | while IFS= read -r -d '' f; do
  # macOS 的 sed 必须指定 -i 备份后缀；此处仅替换 package 声明行
  sed -i.bak -E 's/^package (imgrpc|immq)$/package proto/' "$f"
  rm -f "$f.bak"
done

# ---- 拷贝到输出目录 ----
mkdir -p "$OUT_DIR"
find "$TMP_DIR" -name '*.pb.go' -print0 | while IFS= read -r -d '' f; do
  cp "$f" "$OUT_DIR/"
done

# ---- 结果输出 ----
log "生成完成，输出文件："
ls -1 "$OUT_DIR"/*.pb.go 2>/dev/null || log "(未生成任何文件，请检查 protoc 输出)"

log "提示：生成后请运行 go mod tidy 同步依赖。"
