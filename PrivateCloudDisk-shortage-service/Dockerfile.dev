FROM python:3.10-alpine

WORKDIR /backend_file_service

# 利用docker layer分层缓存 先复制依赖文件 执行下载命令 后续重新构建镜像不用重复执行下载依赖命令
# 注意.dockerignore 别把本机的python依赖复制替换了容器刚刚下载的依赖
COPY requirements.txt .

# 安装依赖（国内阿里云源，加速安装）
RUN pip install --no-cache-dir -r requirements.txt -i https://mirrors.aliyun.com/pypi/simple/

COPY . .

EXPOSE 8000

CMD [ "uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8000", "--reload"]