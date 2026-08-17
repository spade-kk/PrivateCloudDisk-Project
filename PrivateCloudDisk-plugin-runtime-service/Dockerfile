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
    && mkdir -p /app/validator /var/lib/pcd-runtime/work \
    && chown -R 65532:65532 /var/lib/pcd-runtime
COPY --from=build /out/plugin-runtime /usr/local/bin/plugin-runtime
COPY validator/validate_python.py /app/validator/validate_python.py
USER 65532:65532
ENV PYTHON_VALIDATOR_SCRIPT=/app/validator/validate_python.py
EXPOSE 8090
ENTRYPOINT ["/usr/local/bin/plugin-runtime"]
