#!/usr/bin/env sh
# 插件运行时手动调试/教学测试单元包装脚本。
#
# 包装 internal/sandbox/manual_plugin_test.go 的 env 驱动（复用 runner.go /
# manifest.go / parse.go / pcdpkg 校验 + Fake Broker/Packages/能力 relay，不重复实现），
# 把 CLI 参数映射为 PCD_DEBUG_* 环境变量并运行：
#
#   go test -tags=integration -v -run '^TestManualPluginDriver$' ./internal/sandbox/
#
# 运行前需本机 Docker 可用（沙箱镜像默认 pcd/plugin-sandbox-python:0.1.2）；
# restricted 子命令不需 Docker。
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SANDBOX_IMAGE="${PLUGIN_SANDBOX_IMAGE:-pcd/plugin-sandbox-python:0.1.2}"
SANDBOX_RUNTIME="${PLUGIN_SANDBOX_RUNTIME:-runc}"

usage() {
  cat <<'EOF'
插件运行时手动调试测试单元 —— 用法
=====================================
  sh scripts/run_manual_plugin.sh <dir|pkg|src|restricted> ... [选项]

子命令（插件来源三选一）:
  dir <插件项目目录>   未打包的云插件工程（含 manifest.yaml + src/ + schemas/
                       可带 input.json / input.txt；与真实项目目录一致）
  pkg <插件包>         已打包 .pcdpkg 文件
  src <入口.py>        单个 python 入口文件（快速验证 runner.py / restricted.py）
  restricted [源码]    宿主机直接探针 restricted.py（无需 Docker；不传源码用内置 5 组探针）

选项:
  --event <类型>       事件：pcd.file.content.ready.v1(默认) | pcd.file.available.v1
  --capability <name>  切换到 ExecuteCapability（= manifest exports 中的能力名）
  --input '<json>'     输入参数/内容：capability 模式必须是合法 JSON 对象
  --input-file <path>  从文件注入输入内容（优先级高于 --input）
  --timeout <秒>       覆盖容器执行超时
  --memory-mb <MB>     覆盖容器内存
  --no-restricted      关闭受限 Python 层（探针夹具，默认开启受限层）
  --verify-digest      开启镜像摘要门禁（需 PLUGIN_SANDBOX_IMAGE_DIGEST）
  --image <镜像>       沙箱镜像（默认 pcd/plugin-sandbox-python:0.1.2）
  --runtime <运行时>   沙箱运行时（默认 runc；rootless+runsc 环境可传 runsc）

示例:
  sh scripts/run_manual_plugin.sh dir testdata/plugins/realworld/text_stats \
      --input-file testdata/input/text_stats.txt
  sh scripts/run_manual_plugin.sh dir my-plugin --input '{"text":"hi"}'
  sh scripts/run_manual_plugin.sh dir my-plugin --capability generate_report \
      --input '{"rows":10}'
  sh scripts/run_manual_plugin.sh src /tmp/probe.py --no-restricted --timeout 4
  sh scripts/run_manual_plugin.sh restricted "import os\ndef main(c):\n    return os.getuid()"
EOF
}

[ "$#" -eq 0 ] && { usage; exit 0; }
cmd="$1"; shift

export PCD_DEBUG_PLUGIN_DIR=""
export PCD_DEBUG_PLUGIN_PKG=""
export PCD_DEBUG_PLUGIN_SRC=""
export PCD_DEBUG_INPUT=""
export PCD_DEBUG_INPUT_FILE=""
export PCD_DEBUG_EVENT=""
export PCD_DEBUG_CAPABILITY=""
export PCD_DEBUG_TIMEOUT=""
export PCD_DEBUG_MEMORY_MB=""
export PCD_DEBUG_DISABLE_RESTRICTED=""
export PCD_DEBUG_VERIFY_DIGEST=""
export PCD_DEBUG_RESTRICTED_PROBE=""
export PCD_DEBUG_RESTRICTED_SNIPPET=""

case "$cmd" in
  restricted)
    # 宿主机受限层探针：无需 Docker，直接调用 sandbox/python/restricted.py。
    PCD_DEBUG_RESTRICTED_PROBE=1
    if [ "$#" -gt 0 ]; then
      PCD_DEBUG_RESTRICTED_SNIPPET="$1"
    fi
    echo "==> 受限 Python 层（restricted.py）宿主机探针"
    GOCACHE=/tmp/pcd-gocache GOTMPDIR=/tmp/pcd-gotmp \
      go test -tags=integration -v -count=1 -run '^TestManualRestrictedProbe$' ./internal/sandbox/
    exit 0
    ;;
  dir|pkg|src)
    [ "$#" -eq 0 ] && { usage >&2; exit 1; }
    case "$cmd" in
      dir) PCD_DEBUG_PLUGIN_DIR="$1" ;;
      pkg) PCD_DEBUG_PLUGIN_PKG="$1" ;;
      src) PCD_DEBUG_PLUGIN_SRC="$1" ;;
    esac
    shift
    ;;
  -h|--help|help)
    usage
    exit 0
    ;;
  *)
    echo "未知子命令: $cmd" >&2
    usage >&2
    exit 1
    ;;
esac

while [ "$#" -gt 0 ]; do
  case "$1" in
    --event) PCD_DEBUG_EVENT="$2"; shift 2 ;;
    --capability) PCD_DEBUG_CAPABILITY="$2"; shift 2 ;;
    --input) PCD_DEBUG_INPUT="$2"; shift 2 ;;
    --input-file) PCD_DEBUG_INPUT_FILE="$2"; shift 2 ;;
    --timeout) PCD_DEBUG_TIMEOUT="$2"; shift 2 ;;
    --memory-mb) PCD_DEBUG_MEMORY_MB="$2"; shift 2 ;;
    --no-restricted) PCD_DEBUG_DISABLE_RESTRICTED=1; shift ;;
    --verify-digest) PCD_DEBUG_VERIFY_DIGEST=1; shift ;;
    --image) SANDBOX_IMAGE="$2"; shift 2 ;;
    --runtime) SANDBOX_RUNTIME="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage >&2; exit 1 ;;
  esac
done

if [ -z "$PCD_DEBUG_PLUGIN_DIR" ] && [ -z "$PCD_DEBUG_PLUGIN_PKG" ] && [ -z "$PCD_DEBUG_PLUGIN_SRC" ]; then
  echo "必须指定插件来源：dir <目录> / pkg <包> / src <文件>" >&2
  usage >&2
  exit 1
fi

echo "==> 手动驱动沙箱执行：source=$cmd  image=$SANDBOX_IMAGE  runtime=$SANDBOX_RUNTIME"
PLUGIN_SANDBOX_IMAGE="$SANDBOX_IMAGE" \
PLUGIN_SANDBOX_RUNTIME="$SANDBOX_RUNTIME" \
GOCACHE=/tmp/pcd-gocache GOTMPDIR=/tmp/pcd-gotmp \
  go test -tags=integration -v -count=1 -timeout 30m \
    -run '^TestManualPluginDriver$' ./internal/sandbox/
