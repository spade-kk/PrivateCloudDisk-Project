FROM node:22-alpine AS builder

WORKDIR /frontend_web_app

COPY package.json package-lock.json ./
RUN npm config set registry https://registry.npmmirror.com
RUN npm ci

COPY . .
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
RUN npm run build

# 基础镜像：Nginx
FROM nginx:1.27-alpine

WORKDIR /frontend_web_app

COPY nginx/default.conf /etc/nginx/conf.d/default.conf
# 复制 Vue 打包后的静态文件到 Nginx 目录
COPY --from=builder /frontend_web_app/dist/ /usr/share/nginx/html/

# 暴露80端口
EXPOSE 80
EXPOSE 443

CMD ["nginx", "-g", "daemon off;"]
