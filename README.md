# 404NotPure
南京大学23届软工Ⅱ大作业

## 当前分支
lab4

## 环境变量配置

请到对应官网购买或注册使用相应服务，并配置环境变量

* 阿里云：请详细参考*back_end/src/main/resources/application.yml*中的配置

* 支付宝：请详细参考*back_end/src/main/resources/application.yml*中的配置

AI assistant 已废弃，不属于当前启动和维护范围，无需配置 `ARK_API_KEY`。

## 启动前依赖服务

* mysql
  * 数据库名称为tomato，请提前创建
  * 用户名称和密码可自行在back_end/src/main/resources/application.yml中修改
  * 本机后端连接 Compose 数据库时设置 `DB_HOST=127.0.0.1`、`DB_PORT=3307`

* natapp
  * 启动内网穿透，并相应配置关于支付宝的环境变量
  * 如需使用完整支付功能，请参考博客 https://blog.csdn.net/mnn12/article/details/136299334

* redis：
  * 需要提前配置好Redis服务端
  * 启动cmd，进入Redis安装目录，输入`redis-server.exe redis.windows.conf`启动服务
  * 参考教程 https://www.runoob.com/redis/redis-install.html
  * 也可以直接使用 Compose 提供的 Redis；它映射到 `127.0.0.1:6379`，只允许本机连接，不对局域网开放

## 启动指南

本项目后端使用 Java 17。Docker Compose 需要显式读取后端环境文件：

```powershell
docker compose --env-file back_end/.env up --build
```

* 前端
  * 进入前端目录，执行`npm ci`安装依赖
  * 执行`npm run dev`启动前端服务（默认端口 5173）

* 后端
  * 安装 Java 17 与 Maven，进入后端目录，执行`mvn clean verify`
  * 直接运行src/main/java/com/example/tomatomall/TomatoMallApplication.java
