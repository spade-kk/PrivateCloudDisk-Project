FROM python:3.11-alpine

WORKDIR /backend_file_service

# 更换为阿里云镜像源，加速构建
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

RUN apk add --no-cache vips

ENV PATH=/root/.local/bin:$PATH

COPY requirements.txt requirements.txt

# 安装依赖（国内阿里云源，加速安装）
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000

CMD [ "uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8000"]