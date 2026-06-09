FROM python:3.11-alpine

WORKDIR /backend_file_service

# 更换为阿里云镜像源，加速构建
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

RUN apk add --no-cache openssl vips

ENV PATH=/root/.local/bin:$PATH

# 生成 RSA 私钥和公钥（新增）
RUN openssl genpkey -algorithm RSA -out private_key.pem -pkeyopt rsa_keygen_bits:2048 && \
    openssl rsa -pubout -in private_key.pem -out public_key.pem

COPY requirements.txt requirements.txt

# 安装依赖（国内阿里云源，加速安装）
RUN pip install --no-cache-dir -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple

COPY . .

EXPOSE 8000

CMD [ "uvicorn", "server:app", "--host", "0.0.0.0", "--port", "8000"]