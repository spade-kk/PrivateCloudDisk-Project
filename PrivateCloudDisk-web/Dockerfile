# ============================================================
# PrivateCloudDisk Web — 企业级多阶段 Docker 构建
# ============================================================
# 阶段 1：前端构建（Node.js）
#   使用 npm ci + 缓存加速安装，Vite 生产构建
#   输出：dist/ 目录（含 .gz / .br 预压缩文件）
#
# 阶段 2：Nginx Brotli 模块编译
#   从源码编译 ngx_brotli 动态模块，与目标 Nginx 版本严格匹配
#   输出：ngx_http_brotli_filter_module.so / ngx_http_brotli_static_module.so
#
# 阶段 3：最终运行镜像
#   合并前端静态文件 + Nginx + Brotli 模块 + 企业级入口脚本
#   以非 root 用户运行，遵循最小权限原则
# ============================================================

# ============================================================
# 阶段 1：前端构建
# ============================================================
FROM node:22-alpine AS builder

WORKDIR /frontend_web_app

# 复制依赖文件，利用 Docker 层缓存加速重复构建
COPY package.json package-lock.json ./

# 使用 npm ci 确保依赖版本一致，挂载缓存目录加速
RUN --mount=type=cache,target=/root/.npm \
    npm config set registry https://registry.npmmirror.com && \
    npm ci

# 构建参数（Vite 环境变量）
ARG VITE_API_BASE_URL=/api/v1
ARG VITE_TURNSTILE_SITE_KEY=0x4AAAAAADf7Z44g-izJGKIB
ARG VITE_CHUNK_SIZE=5242880
ARG VITE_MAX_CONCURRENT_UPLOADS=3
ARG VITE_MAX_CONCURRENT_DOWNLOADS=4
ARG VITE_UPLOAD_THRESHOLD=10485760

ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
ENV VITE_TURNSTILE_SITE_KEY=$VITE_TURNSTILE_SITE_KEY
ENV VITE_CHUNK_SIZE=$VITE_CHUNK_SIZE
ENV VITE_MAX_CONCURRENT_UPLOADS=$VITE_MAX_CONCURRENT_UPLOADS
ENV VITE_MAX_CONCURRENT_DOWNLOADS=$VITE_MAX_CONCURRENT_DOWNLOADS
ENV VITE_UPLOAD_THRESHOLD=$VITE_UPLOAD_THRESHOLD

# 复制源码并构建
# .dockerignore 应排除 node_modules、dist、.git 等
COPY . .
RUN npm run build

# ============================================================
# 阶段 2：编译 Nginx Brotli 模块
# 使用与最终镜像相同版本的 Nginx 源码编译，确保 ABI 兼容
# ============================================================
FROM nginx:1.27-alpine AS brotli-builder

# 动态获取 Nginx 版本（确保与基础镜像 nginx 二进制版本完全一致）
RUN NGINX_VERSION=$(nginx -v 2>&1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+') && \
    echo "Detected nginx version: ${NGINX_VERSION}" && \
    echo "NGINX_VERSION=${NGINX_VERSION}" > /tmp/nginx-version.env

# 更换为阿里云镜像源，加速构建
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# 安装编译依赖
RUN apk add --no-cache --virtual .build-deps \
    gcc \
    libc-dev \
    make \
    openssl-dev \
    pcre-dev \
    zlib-dev \
    linux-headers \
    curl \
    git \
    cmake \
    build-base

# 获取 Nginx 源码（动态检测版本，确保与基础镜像完全一致）
RUN . /tmp/nginx-version.env && \
    curl -fsSL http://nginx.org/download/nginx-${NGINX_VERSION}.tar.gz | tar xz -C /tmp && \
    mv /tmp/nginx-${NGINX_VERSION} /tmp/nginx-src

# 克隆 ngx_brotli Nginx 模块（含 Brotli 子模块）
RUN git clone --depth=1 --recurse-submodules https://github.com/google/ngx_brotli.git /tmp/ngx_brotli

# 编译 Brotli C 静态库（ngx_brotli 模块依赖）
RUN mkdir -p /tmp/ngx_brotli/deps/brotli/out && \
    cd /tmp/ngx_brotli/deps/brotli/out && \
    cmake -DCMAKE_BUILD_TYPE=Release \
          -DBUILD_SHARED_LIBS=OFF \
          -DCMAKE_INSTALL_PREFIX=./installed \
          .. && \
    cmake --build . --config Release --target brotlienc brotlicommon

# 编译 Brotli Nginx 动态模块
WORKDIR /tmp/nginx-src
RUN ./configure \
    --with-compat \
    --add-dynamic-module=/tmp/ngx_brotli \
    && make modules

# ============================================================
# 阶段 3：最终运行镜像
# ============================================================
FROM nginx:1.27-alpine

# --- 添加：设置默认环境变量（可被运行时覆盖） ---
ENV DOMAIN=hellomyservice.xyz \
    MONITOR_INTERVAL=300 \
    TZ=Asia/Shanghai

# 安装运行时依赖（logrotate 用于日志轮转，tini 用于优雅退出）
RUN apk add --no-cache \
    logrotate \
    tini \
    curl \
    tzdata \
    openssl

# 设置时区
RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

# ============================================================
# ★★★ 核心新增：复制企业级入口脚本 ★★★
# ============================================================
COPY nginx-entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh && \
    # 确保脚本使用 LF 换行符（防止 Windows 下的 CRLF 导致诡异报错）
    sed -i 's/\r$//' /entrypoint.sh

# 从编译阶段复制 Brotli 模块
COPY --from=brotli-builder /tmp/nginx-src/objs/ngx_http_brotli_filter_module.so \
                          /etc/nginx/modules/ngx_http_brotli_filter_module.so
COPY --from=brotli-builder /tmp/nginx-src/objs/ngx_http_brotli_static_module.so \
                          /etc/nginx/modules/ngx_http_brotli_static_module.so

# 使用自定义主配置文件（含 Brotli 模块加载、worker 优化、events 优化）
COPY nginx/nginx.conf /etc/nginx/nginx.conf

# 复制 Nginx 子域名站点配置（多域 CDN 边缘层架构）
COPY nginx/conf.d/ /etc/nginx/conf.d/

# 从构建阶段复制前端静态文件（含 version-manifest.json）
COPY --from=builder /frontend_web_app/dist/ /usr/share/nginx/html/

# 创建客户端二进制下载目录（实际二进制文件通过 volume 挂载或 CI/CD 上传）
# 目录结构：
#   /usr/share/nginx/html/downloads/
#   ├── version-manifest.json    (随前端构建)
#   └── binaries/                (volume 挂载，运维上传)
#       ├── CloudDrive-Setup-3.2.0-x64.exe
#       ├── CloudDrive-3.2.0-arm64.dmg
#       ├── CloudDrive-3.2.0-x86_64.AppImage
#       └── ...
RUN mkdir -p /usr/share/nginx/html/downloads/binaries && \
    chown -R nginx:nginx /usr/share/nginx/html/downloads

# 创建日志轮转配置（含多子域名日志文件）
RUN echo '/var/log/nginx/*.log {' > /etc/logrotate.d/nginx && \
    echo '    daily' >> /etc/logrotate.d/nginx && \
    echo '    missingok' >> /etc/logrotate.d/nginx && \
    echo '    rotate 14' >> /etc/logrotate.d/nginx && \
    echo '    compress' >> /etc/logrotate.d/nginx && \
    echo '    delaycompress' >> /etc/logrotate.d/nginx && \
    echo '    notifempty' >> /etc/logrotate.d/nginx && \
    echo '    create 640 nginx adm' >> /etc/logrotate.d/nginx && \
    echo '    sharedscripts' >> /etc/logrotate.d/nginx && \
    echo '    postrotate' >> /etc/logrotate.d/nginx && \
    echo '        [ -f /var/run/nginx.pid ] && kill -USR1 $(cat /var/run/nginx.pid)' >> /etc/logrotate.d/nginx && \
    echo '    endscript' >> /etc/logrotate.d/nginx && \
    echo '}' >> /etc/logrotate.d/nginx

# 创建 50x 错误页面
RUN echo '<!DOCTYPE html><html><head><title>Server Error</title></head><body><h1>500 - Internal Server Error</h1><p>Please try again later.</p></body></html>' > /usr/share/nginx/html/50x.html

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost/ || exit 1

# 暴露端口
EXPOSE 80
EXPOSE 443

# ============================================================
# ★★★ 修改启动命令：用 tini 运行入口脚本 ★★★
# ============================================================
# tini 作为 init 进程，确保信号正确转发和僵尸进程回收
# 入口脚本会负责生成临时证书、启动 Nginx、监控证书变化
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["/entrypoint.sh"]