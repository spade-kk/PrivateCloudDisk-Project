FROM golang:1.24-alpine AS builder
WORKDIR /src
RUN apk add --no-cache git ca-certificates
COPY go.mod go.sum* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/git-service ./cmd/server

FROM alpine:3.21
RUN apk add --no-cache git ca-certificates tzdata && addgroup -S pcd && adduser -S -G pcd pcd
WORKDIR /app
COPY --from=builder /out/git-service /app/git-service
COPY db/migration /app/db/migration
RUN mkdir -p /data/git/repos /data/git/ssh && chown -R pcd:pcd /data/git /app
USER pcd
EXPOSE 8091 2222
ENTRYPOINT ["/app/git-service"]
