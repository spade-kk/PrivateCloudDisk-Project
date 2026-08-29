FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY cmd ./cmd
COPY internal ./internal
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/plugin-runtime ./cmd/runtime

FROM alpine:3.21
RUN apk add --no-cache ca-certificates docker-cli python3 nodejs \
    && addgroup -g 65532 runtime \
    && adduser -D -H -u 65532 -G runtime runtime \
    && mkdir -p /app/validator /opt/pcd-plugin-runtime/profiles /var/lib/pcd-runtime/work \
    && chown -R 65532:65532 /var/lib/pcd-runtime
COPY --from=build /out/plugin-runtime /usr/local/bin/plugin-runtime
COPY validator/validate_python.py /app/validator/validate_python.py
COPY validator/validate_js.mjs /app/validator/validate_js.mjs
COPY validator/js /app/validator/js
# 沙箱加固策略基线随镜像发布（来源 deploy/seccomp.json 与 deploy/pcd-plugin-sandbox.apparmor）。
# 注意：Docker 守护进程按节点宿主路径读取 seccomp，AppArmor 需在节点 apparmor_parser 预加载；
# 生产按 deploy/plugin-runtime.env.example 注入 PLUGIN_SANDBOX_SECCOMP_PROFILE 宿主路径。
COPY deploy/seccomp.json /opt/pcd-plugin-runtime/profiles/seccomp.json
COPY deploy/pcd-plugin-sandbox.apparmor /opt/pcd-plugin-runtime/profiles/pcd-plugin-sandbox.apparmor
USER 65532:65532
ENV PYTHON_VALIDATOR_SCRIPT=/app/validator/validate_python.py \
    JS_VALIDATOR_SCRIPT=/app/validator/validate_js.mjs
EXPOSE 8090
ENTRYPOINT ["/usr/local/bin/plugin-runtime"]
