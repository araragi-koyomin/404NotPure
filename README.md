# 404NotPure
南京大学23届软工Ⅱ大作业

## 环境变量配置

项目使用 Java 17。先复制不含真实凭据的示例，再按照[本机环境变量配置与启动指南](docs/guides/local-environment.md)逐项填写：

```powershell
Copy-Item back_end/.env.example back_end/.env
```

不要直接修改 `application.yml` 写入密码或密钥，也不要提交 `back_end/.env`。MySQL、Redis、OSS、支付宝沙箱、Flyway 和本机/Compose 地址差异都在配置指南中说明。

AI assistant 已废弃，不属于当前启动和维护范围，无需配置 `ARK_API_KEY`。

## 启动前依赖服务

* mysql
  * 默认数据库名称为 `Tomato`，Compose 会在首次启动时创建
  * 用户、密码和地址通过 `back_end/.env` 配置，不写入 `application.yml`
  * 本机后端连接 Compose 数据库时设置 `DB_HOST=127.0.0.1`、`DB_PORT=3307`

* 支付宝沙箱公网回调
  * 异步通知由支付宝服务器请求，本机地址不能直接接收；需要把后端 8080 映射为公网 HTTPS 地址
  * `ALIPAY_NOTIFY_URL`、`ALIPAY_RETURN_URL`、`ALIPAY_SELLER_ID` 和密钥来源见本机配置指南

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
  * 直接运行 `src/main/java/com/example/tomatomall/TomatoMallApplication.java` 时，需在 IDE 运行配置中导入 `.env` 中的变量
  * 已有后端 JAR 时，也可在 `back_end/` 执行 `.\scripts\Start-LocalBackend.ps1`
