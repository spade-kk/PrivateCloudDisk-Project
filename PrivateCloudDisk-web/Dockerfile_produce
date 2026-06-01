# 基础镜像：Nginx
FROM nginx:latest

WORKDIR /frontend_web_app

COPY package.json .

RUN npm install && npm run build

# 复制 Vue 打包后的静态文件到 Nginx 目录
COPY dist/ /usr/share/nginx/html/

# 暴露80端口
EXPOSE 80