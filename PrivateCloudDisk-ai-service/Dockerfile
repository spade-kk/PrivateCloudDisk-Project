# ============================================================
# PrivateCloudDisk AI Processing Service - Dockerfile
# 多阶段构建，优化镜像体积
# ============================================================

# ---- Stage 1: Base ----
FROM python:3.12-slim-bookworm AS base

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# 系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    # PaddleOCR / OpenCV 依赖
    libgl1-mesa-glx \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    # dlib / face_recognition 依赖
    cmake \
    build-essential \
    # 中文支持
    locales \
    && sed -i '/zh_CN.UTF-8/s/^# //g' /etc/locale.gen \
    && locale-gen zh_CN.UTF-8 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

ENV LANG=zh_CN.UTF-8 \
    LC_ALL=zh_CN.UTF-8

WORKDIR /app

# ---- Stage 2: Dependencies ----
FROM base AS deps

# 先只复制 requirements，利用 Docker layer cache
COPY requirements.txt .

# 安装 Python 依赖（ONNX Runtime CPU 版本）
RUN pip install --no-cache-dir \
    -i https://pypi.org/simple \
    -r requirements.txt \
    && pip uninstall -y onnxruntime-gpu 2>/dev/null

# 下载预训练模型（可选，加速首次启动）
# 实际运行时会自动下载到 MODEL_CACHE_DIR

# ---- Stage 3: Runtime ----
FROM deps AS runtime

# 复制应用代码
COPY app/ ./app/
COPY models/ ./models/

# 创建非 root 用户
RUN groupadd -r aiapp && useradd -r -g aiapp -d /app aiapp \
    && chown -R aiapp:aiapp /app

USER aiapp

# SkyWalking Python Agent 环境变量（默认值，可通过 docker-compose 覆盖）
ENV SW_AGENT_NAME=PrivateCloudDisk-ai-service \
    SW_AGENT_COLLECTOR_BACKEND_SERVICES=skywalking-oap:11800 \
    SW_AGENT_NAMESPACE=PrivateCloudDisk \
    SW_AGENT_LOGGING_LEVEL=INFO

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD python -c "import httpx; httpx.get('http://localhost:8001/health').raise_for_status()" || exit 1

EXPOSE 8001

# 通过 sw-python 启动（自动注入 SkyWalking 链路追踪）
CMD ["sw-python", "run", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001", "--workers", "1"]

# Worker 进程入口（通过 docker-compose 覆盖 command）
# CMD ["sw-python", "run", "python", "app/worker.py"]