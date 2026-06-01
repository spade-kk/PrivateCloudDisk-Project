FROM node:22-alpine AS builder

WORKDIR /frontend_web_app

COPY package.json .

COPY . .

RUN npm install && npm run build

# 基础镜像：Nginx
FROM nginx:latest

WORKDIR /frontend_web_app

# 复制 Vue 打包后的静态文件到 Nginx 目录
COPY --from=builder /frontend_web_app/dist/ /usr/share/nginx/html/

# 暴露80端口
EXPOSE 80