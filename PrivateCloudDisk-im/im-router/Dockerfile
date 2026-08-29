# ================================================================
# IM Router 多阶段构建镜像
# ================================================================

# ---- 阶段 1：构建 ----
FROM golang:1.22-alpine AS builder

# 安装 protoc 与 Go 插件，用于生成 proto 代码
RUN apk add --no-cache git protobuf make bash

ENV GOPROXY=https://goproxy.cn,direct
RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@latest && \
    go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest

WORKDIR /build

# 先拷贝依赖文件，利用 Docker 缓存
COPY go.mod go.sum ./
RUN go mod download

# 拷贝源码与 proto 生成脚本
COPY . .

# 生成 proto 代码（如已提交生成产物则跳过）
RUN chmod +x pkg/proto/generate.sh && \
    PROTO_SRC_DIR=/proto bash pkg/proto/generate.sh || echo "proto 已存在或生成跳过"

# 编译静态二进制
RUN CGO_ENABLED=0 GOOS=linux go build \
    -ldflags="-s -w -X main.version=$(git rev-parse --short HEAD 2>/dev/null || echo dev) \
              -X main.buildTime=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    -o /out/im-router ./cmd/router

# ---- 阶段 2：运行 ----
FROM alpine:3.19

RUN apk add --no-cache ca-certificates tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app

COPY --from=builder /out/im-router /app/im-router
COPY config.yaml /app/config.yaml

EXPOSE 9092 9093

ENTRYPOINT ["/app/im-router", "-config", "/app/config.yaml"]
