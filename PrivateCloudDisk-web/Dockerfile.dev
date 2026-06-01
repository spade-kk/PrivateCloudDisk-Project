FROM node:22-alpine

WORKDIR /frontend_web_app

# 复制依赖文件，安装依赖
COPY package.json .

RUN npm install

COPY . .

EXPOSE 5500

CMD [ "npm", "run", "dev" ]