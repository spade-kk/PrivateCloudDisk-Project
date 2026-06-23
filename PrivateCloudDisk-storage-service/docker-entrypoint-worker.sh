#!/bin/sh
# =============================================================================
# PrivateCloudDisk Worker 容器入口点
#
# 启动流程:
#   1. 更新 ClamAV 病毒库 (freshclam)
#   2. 启动 clamd 守护进程 (病毒扫描)
#   3. 启动 Worker 进程 (文件消费者 / 死信消费者)
#
# 用法 (docker run / docker-compose):
#   docker run ... privatecloud-file-service \
#     /backend_file_service/docker-entrypoint-worker.sh
#
# 环境变量:
#   SKIP_FRESHCLAM=1    跳过病毒库更新 (开发环境)
#   SKIP_CLAMD=1        跳过 clamd 启动 (仅转码等不需要扫毒的 Worker)
# =============================================================================

set -e

echo "============================================"
echo "PrivateCloudDisk Worker Entrypoint"
echo "============================================"

# ---------- 1. 更新病毒库 ----------
if [ "$SKIP_FRESHCLAM" != "1" ]; then
    echo "[entrypoint] 更新 ClamAV 病毒库..."
    if freshclam --quiet --no-warnings 2>/dev/null; then
        echo "[entrypoint] 病毒库更新完成"
    else
        echo "[entrypoint] 警告: 病毒库更新失败，继续使用已有数据库"
    fi
else
    echo "[entrypoint] 跳过病毒库更新 (SKIP_FRESHCLAM=1)"
fi

# ---------- 2. 启动 clamd ----------
if [ "$SKIP_CLAMD" != "1" ]; then
    echo "[entrypoint] 启动 clamd 守护进程..."
    mkdir -p /var/run/clamav /var/log/clamav
    chown -R clamav:clamav /var/run/clamav /var/log/clamav /var/lib/clamav

    # 后台启动 clamd
    clamd -c /etc/clamav/clamd.conf &

    # 等待 clamd socket 就绪 (最多等待 15 秒)
    wait_count=0
    while [ ! -S /var/run/clamav/clamd.sock ] && [ $wait_count -lt 15 ]; do
        sleep 1
        wait_count=$((wait_count + 1))
    done

    if [ -S /var/run/clamav/clamd.sock ]; then
        echo "[entrypoint] clamd 已就绪 (socket: /var/run/clamav/clamd.sock)"
    else
        echo "[entrypoint] 警告: clamd 启动超时，病毒扫描可能不可用"
        echo "[entrypoint] 请检查日志: /var/log/clamav/clamd.log"
    fi
else
    echo "[entrypoint] 跳过 clamd 启动 (SKIP_CLAMD=1)"
fi

# ---------- 3. 启动 Worker ----------
echo "[entrypoint] 启动 Worker 进程..."
echo "============================================"

exec sw-python run python worker.py