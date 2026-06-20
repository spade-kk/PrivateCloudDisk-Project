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

# 默认启动 1 个 Worker
WORKER_COUNT=1

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

if [ "$WORKER_COUNT" -eq 1 ]; then
    exec python worker.py
else
    # 多 Worker 模式：后台启动多个进程
    for i in $(seq 1 "$WORKER_COUNT"); do
        echo "启动 Worker #${i}..."
        python worker.py &
    done
    echo "所有 Worker 已启动 (PID: $(jobs -p | tr '\n' ' '))"
    # 等待所有后台进程
    wait
fi