FROM python:3.11-alpine

WORKDIR /backend_file_service

# 更换为阿里云镜像源，加速构建
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# =============================================================================
# 安装系统依赖
# - clamav + clamav-daemon: 病毒扫描 (文件消费者)
# - ffmpeg: 视频转码 (文件消费者)
# - openssl: RSA 密钥生成
# - vips: 图片处理
# - tesseract-ocr: 图片 OCR 文字提取
# =============================================================================
RUN apk add --no-cache \
    # 编译工具（pyvips 需要）
    build-base \
    pkgconfig \
    # 运行时依赖
    openssl \
    vips \
    vips-dev \
    ffmpeg \
    clamav \
    clamav-daemon \
    tesseract-ocr \
    tesseract-ocr-data-eng \
    tesseract-ocr-data-chi_sim \
    libarchive \
    libarchive-dev \
    libreoffice

# =============================================================================
# 配置 ClamAV
# =============================================================================

# 配置 freshclam (病毒库自动更新)
RUN mkdir -p /var/lib/clamav && \
    chown -R clamav:clamav /var/lib/clamav && \
    sed -i 's/^Example/#Example/' /etc/clamav/freshclam.conf && \
    sed -i 's/^#DatabaseDirectory /DatabaseDirectory /' /etc/clamav/freshclam.conf && \
    echo "DatabaseDirectory /var/lib/clamav" >> /etc/clamav/freshclam.conf && \
    echo "DatabaseMirror database.clamav.net" >> /etc/clamav/freshclam.conf && \
    echo "MaxAttempts 5" >> /etc/clamav/freshclam.conf && \
    echo "Checks 12" >> /etc/clamav/freshclam.conf && \
    echo "NotifyClamd /etc/clamav/clamd.conf" >> /etc/clamav/freshclam.conf

# 配置 clamd (病毒扫描守护进程)
RUN sed -i 's/^Example/#Example/' /etc/clamav/clamd.conf && \
    sed -i 's/^#LocalSocket /LocalSocket /' /etc/clamav/clamd.conf && \
    echo "LocalSocket /var/run/clamav/clamd.sock" >> /etc/clamav/clamd.conf && \
    echo "LogFile /var/log/clamav/clamd.log" >> /etc/clamav/clamd.conf && \
    echo "LogTime yes" >> /etc/clamav/clamd.conf && \
    echo "MaxThreads 4" >> /etc/clamav/clamd.conf && \
    echo "MaxScanSize 100M" >> /etc/clamav/clamd.conf && \
    echo "MaxFileSize 50M" >> /etc/clamav/clamd.conf && \
    echo "StreamMaxLength 100M" >> /etc/clamav/clamd.conf && \
    echo "PidFile /var/run/clamav/clamd.pid" >> /etc/clamav/clamd.conf && \
    echo "DatabaseDirectory /var/lib/clamav" >> /etc/clamav/clamd.conf && \
    echo "ScanPE yes" >> /etc/clamav/clamd.conf && \
    echo "ScanELF yes" >> /etc/clamav/clamd.conf && \
    echo "ScanPDF yes" >> /etc/clamav/clamd.conf && \
    echo "ScanMail yes" >> /etc/clamav/clamd.conf && \
    echo "ScanArchive yes" >> /etc/clamav/clamd.conf && \
    echo "DetectPUA yes" >> /etc/clamav/clamd.conf && \
    echo "HeuristicAlerts yes" >> /etc/clamav/clamd.conf && \
    mkdir -p /var/log/clamav /var/run/clamav && \
    chown -R clamav:clamav /var/log/clamav /var/run/clamav /var/lib/clamav

# 首次更新病毒库 (构建时下载，减少容器启动时间)
RUN freshclam --quiet || true

ENV PATH=/root/.local/bin:$PATH

# 生成 RSA 私钥和公钥
RUN openssl genpkey -algorithm RSA -out private_key.pem -pkeyopt rsa_keygen_bits:2048 && \
    openssl rsa -pubout -in private_key.pem -out public_key.pem

COPY requirements.txt requirements.txt

# 安装依赖（国内镜像源，加速安装）
RUN pip install --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
COPY . .

EXPOSE 8000
EXPOSE 8719

# =============================================================================
# SkyWalking Python Agent 环境变量
# =============================================================================
ENV SW_AGENT_NAME=${SW_AGENT_NAME:-PrivateCloudDisk-storage-service} \
    SW_AGENT_COLLECTOR_BACKEND_SERVICES=${SW_AGENT_BACKEND:-skywalking-oap:11800} \
    SW_AGENT_NAMESPACE=${SW_AGENT_NAMESPACE:-PrivateCloudDisk} \
    SW_AGENT_INSTANCE_NAME=${HOSTNAME:-storage-1} \
    SW_AGENT_LOGGING_LEVEL=${SW_LOGGING_LEVEL:-INFO}

# =============================================================================
# 入口点脚本
# FastAPI 容器: 通过 sw-python 启动（自动注入 SkyWalking 链路追踪）
# Worker 容器: docker run 时覆盖 CMD 为 /backend_file_service/docker-entrypoint-worker.sh
# =============================================================================
CMD [ "sw-python", "run", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]