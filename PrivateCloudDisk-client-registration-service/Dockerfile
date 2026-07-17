# ============================================
# PrivateCloudDisk 客户端注册微服务 Dockerfile
# ============================================

FROM golang:1.22-alpine AS builder

WORKDIR /app

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o server ./cmd/server

FROM alpine:3.20

RUN apk add --no-cache ca-certificates tzdata

WORKDIR /app
COPY --from=builder /app/server .
COPY --from=builder /app/config ./config

EXPOSE 8089

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD wget -qO- http://localhost:8089/health || exit 1

CMD ["./server"]