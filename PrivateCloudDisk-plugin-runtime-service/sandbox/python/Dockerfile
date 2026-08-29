FROM python:3.11-alpine3.21

# 固定非 root 身份；镜像不包含 shell 包管理器扩展或编译工具。
RUN addgroup -g 65532 sandbox \
    && adduser -D -H -u 65532 -G sandbox sandbox \
    && mkdir -p /opt/pcd-sdk/pycloud /opt/pcd-sdk/bin /opt/pcd/profiles \
        /workspace/input /workspace/work /workspace/context /workspace/plugin /runtime \
    && chown -R 65532:65532 /workspace

COPY pycloud /opt/pcd-sdk/pycloud
# 运行时受限 Python 层 + 受控启动器（36.4：/opt/pcd-sdk/bin/runner.py）。
# 所有云插件都必须经过该启动器执行；启动器与 restricted.py 以只读镜像层固定，
# 插件无法改写（36.25）。
COPY restricted.py /opt/pcd-sdk/restricted.py
COPY runner.py /opt/pcd-sdk/bin/runner.py
# 随版本发布的沙箱加固策略产物（与 deploy/ 目录保持一致，见 scripts/check_sandbox_profiles.sh）：
# Docker 守护进程按节点路径读取 seccomp；AppArmor 需在节点 apparmor_parser 预加载，
# 镜像内仅作不可变策略基线，运行时由 PLUGIN_SANDBOX_SECCOMP_PROFILE/APPARMOR_PROFILE 注入。
COPY seccomp.json /opt/pcd/profiles/seccomp.json
COPY pcd-plugin-sandbox.apparmor /opt/pcd/profiles/pcd-plugin-sandbox.apparmor

USER 65532:65532
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONHASHSEED=random \
    PYTHONUNBUFFERED=1 \
    PCD_SANDBOX_SECCOMP=/opt/pcd/profiles/seccomp.json \
    PCD_SANDBOX_APPARMOR=/opt/pcd/profiles/pcd-plugin-sandbox.apparmor
ENTRYPOINT ["python3", "-I", "-S", "/opt/pcd-sdk/bin/runner.py"]
