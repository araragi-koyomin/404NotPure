# 前端开发环境 Dockerfile
FROM node:18

WORKDIR /app

# 拷贝依赖文件并安装
COPY package*.json ./
RUN npm install --registry=https://registry.npmmirror.com

# 拷贝全部源码
COPY . .

# 暴露 Vite 默认开发端口
EXPOSE 3000

# 启动 Vite 开发服务器（--host 允许容器外访问）
CMD ["npm", "run", "dev", "--", "--host"]
