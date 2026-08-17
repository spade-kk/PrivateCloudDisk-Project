#!/bin/bash
# =============================================================================
# PrivateCloudDisk Worker 启动脚本
#
# 用法:
#   ./start_worker.sh              # 默认配置启动
#   ./start_worker.sh --workers 2  # 启动 2 个 Worker 进程
#
# 环境变量 (可选):
#   WORKER_PREFETCH_FP=4    文件处理队列 prefetch 数
#   WORKER_PREFETCH_FD=2    文件删除队列 prefetch 数
#   WORKER_PREFETCH_CI=2    内容索引队列 prefetch 数
#   WORKER_PREFETCH_DLQ=1   死信队列 prefetch 数
#   WORKER_CONCURRENCY_FP=8 文件处理队列最大并发协程数
#   WORKER_LOG_LEVEL=INFO   日志级别
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 激活虚拟环境
if [ -d ".venv" ]; then
    source .venv/bin/activate
fi

# W-05：未显式配置时按宿主机 CPU 核心数启动；容器部署可通过环境变量固定进程数。
WORKER_COUNT="${WORKER_PROCESSES:-$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 1)}"

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --workers)
            WORKER_COUNT="$2"
            shift 2
            ;;
        *)
            echo "用法: $0 [--workers N]"
            exit 1
            ;;
    esac
done

echo "============================================"
echo "PrivateCloudDisk Worker 启动"
echo "Worker 数量: ${WORKER_COUNT}"
echo "============================================"

# W-05：原脚本通过 shell 后台 fork，无法统一转发 SIGTERM；新行为只启动一个管理进程，
# 由 worker.py 负责 spawn 子进程、健康端口偏移和优雅关闭。
exec env WORKER_PROCESSES="$WORKER_COUNT" python worker.py
