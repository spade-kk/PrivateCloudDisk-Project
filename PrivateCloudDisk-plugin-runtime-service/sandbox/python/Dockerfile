FROM python:3.11-alpine3.21

# 固定非 root 身份；镜像不包含 shell 包管理器扩展或编译工具。
RUN addgroup -g 65532 sandbox \
    && adduser -D -H -u 65532 -G sandbox sandbox \
    && mkdir -p /opt/pcd-sdk/pycloud /workspace/input /workspace/work /workspace/context /workspace/plugin \
    && chown -R 65532:65532 /workspace

COPY pycloud /opt/pcd-sdk/pycloud
COPY runner.py /opt/pcd/runner.py

USER 65532:65532
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONHASHSEED=random \
    PYTHONUNBUFFERED=1
ENTRYPOINT ["python3", "-I", "-S", "/opt/pcd/runner.py"]
