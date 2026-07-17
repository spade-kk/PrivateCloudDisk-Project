#!/usr/bin/env bash

set -euo pipefail

# ---------- 日志函数 ----------
log_info() {
    echo "[INFO] $(date '+%Y-%m-%d %H:%M:%S') - $*"
}

log_error() {
    echo "[ERROR] $(date '+%Y-%m-%d %H:%M:%S') - $*" >&2
}

# ---------- 默认配置（环境变量覆盖） ----------
: "${DOMAIN:=hellomyservice.xyz}"
: "${CERT_DIR:=/etc/letsencrypt/live/${DOMAIN}}"
: "${CERT_FILE:=${CERT_DIR}/fullchain.pem}"
: "${KEY_FILE:=${CERT_DIR}/privkey.pem}"
: "${MONITOR_INTERVAL:=300}"
: "${NGINX_PID_FILE:=/var/run/nginx.pid}"

log_info "Starting Nginx entrypoint with domain: ${DOMAIN}"
log_info "Certificate path: ${CERT_FILE}"
log_info "Monitor interval: ${MONITOR_INTERVAL}s"

# ---------- 证书初始化 ----------
ensure_certificate() {
    if [[ -f "${CERT_FILE}" && -f "${KEY_FILE}" ]]; then
        log_info "Existing certificate found, using it."
        return 0
    fi

    log_info "Certificate not found, generating temporary self-signed certificate..."
    mkdir -p "$(dirname "${CERT_FILE}")" "$(dirname "${KEY_FILE}")"

    openssl req -x509 -nodes -days 1 -newkey rsa:2048 \
        -keyout "${KEY_FILE}" \
        -out "${CERT_FILE}" \
        -subj "/CN=${DOMAIN}" 2>/dev/null

    if [[ $? -eq 0 ]]; then
        log_info "Temporary certificate generated successfully."
    else
        log_error "Failed to generate temporary certificate. Nginx may not start."
        return 1
    fi
}

# ---------- 启动 Nginx（守护进程） ----------
start_nginx() {
    log_info "Starting Nginx in daemon mode..."
    nginx -g "daemon on;"
    if [[ $? -ne 0 ]]; then
        log_error "Nginx failed to start. Exiting."
        exit 1
    fi

    local retry=0
    local max_retry=10
    until [[ -f "${NGINX_PID_FILE}" ]] && kill -0 "$(cat "${NGINX_PID_FILE}")" 2>/dev/null; do
        if [[ $retry -ge $max_retry ]]; then
            log_error "Nginx did not start within timeout."
            exit 1
        fi
        sleep 1
        ((retry++))
    done
    log_info "Nginx started successfully (PID: $(cat "${NGINX_PID_FILE}"))."
}

# ---------- 证书监控循环 ----------
monitor_certificates() {
    local old_mtime=0
    if [[ -f "${CERT_FILE}" ]]; then
        old_mtime=$(stat -c %Y "${CERT_FILE}" 2>/dev/null || echo 0)
    fi

    log_info "Starting certificate monitor (check every ${MONITOR_INTERVAL}s)..."

    while true; do
        sleep "${MONITOR_INTERVAL}"

        # ★★★ 增强：检查 Nginx 进程是否还活着 ★★★
        if [[ -f "${NGINX_PID_FILE}" ]]; then
            local pid
            pid=$(cat "${NGINX_PID_FILE}")
            if ! kill -0 "${pid}" 2>/dev/null; then
                log_error "Nginx process (PID ${pid}) is dead! Exiting container."
                exit 1
            fi
        else
            log_error "Nginx PID file missing! Exiting container."
            exit 1
        fi

        if [[ ! -f "${CERT_FILE}" ]]; then
            log_error "Certificate file disappeared! Skipping reload."
            continue
        fi

        local new_mtime
        new_mtime=$(stat -c %Y "${CERT_FILE}" 2>/dev/null || echo 0)
        if [[ "${new_mtime}" -ne "${old_mtime}" ]]; then
            log_info "Certificate file changed, reloading Nginx..."
            nginx -s reload
            if [[ $? -eq 0 ]]; then
                log_info "Nginx reloaded successfully."
                old_mtime="${new_mtime}"
            else
                log_error "Nginx reload failed!"
            fi
        fi
    done
}

# ---------- 优雅退出处理 ----------
cleanup() {
    log_info "Received shutdown signal, stopping Nginx gracefully..."
    nginx -s quit 2>/dev/null || true
    if [[ -f "${NGINX_PID_FILE}" ]]; then
        local pid
        pid=$(cat "${NGINX_PID_FILE}")
        while kill -0 "${pid}" 2>/dev/null; do
            sleep 1
        done
    fi
    log_info "Nginx stopped. Exiting."
    exit 0
}

trap cleanup SIGTERM SIGINT SIGQUIT

# ---------- 主流程 ----------
main() {
    ensure_certificate
    start_nginx
    monitor_certificates
}

main "$@"