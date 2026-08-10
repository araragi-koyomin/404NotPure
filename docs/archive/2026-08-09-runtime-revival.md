---
title: 2026-08-09 运行环境恢复与烟雾验证
type: report
layer: cold
status: completed
created: 2026-08-09
updated: 2026-08-10
archived_at: 2026-08-09
owners:
  - maintainers
tags:
  - runtime
  - java17
  - docker
  - smoke-test
---

# 2026-08-09 运行环境恢复与烟雾验证

## 完成范围

- 检查 Git 工作区并保留原有未跟踪文件；未提交或推送；
- 确认本机 Java 17、Node 20、npm 10 和 Docker Desktop 状态；
- 将 Maven 编译目标和后端 Dockerfile 从 Java 8 升级到 Java 17；
- 将前端开发端口从 3000 调整为 5173，关闭自动打开浏览器并增加 `/api` 代理；
- 修正 Compose 环境文件使用说明、后端无效源码挂载、Redis 健康检查和宿主端口映射；
- 增加 JDBC `DB_PORT`，支持本机后端连接 Compose MySQL 3307；
- 升级 `vue-tsc`，修复购物车模板隐式 `any`；
- 删除前端登录/更新对象 console 日志，避免密码泄露；
- 创建并健康启动 MySQL 8.0.46、Redis 6.2.23；
- 启动 Spring Boot 8080 和 Vite 5173；
- 通过前端代理完成临时账户注册、登录、Cookie 和账户读取验证。

## 执行证据

- `mvn clean test`：成功；91 个后端源文件重新编译；现有 1 个测试通过；
- `npm ci`：成功；
- `npm run build`：成功；
- `docker compose --env-file back_end/.env config --quiet`：成功；
- Redis：`PONG`；
- MySQL：Hibernate 创建 14 张业务表；
- `GET http://127.0.0.1:8080/api/products`：HTTP 200 JSON；
- `GET http://127.0.0.1:5173/api/products`：HTTP 200 JSON，证明 Vite 代理联通；
- 浏览器登录页正常渲染，无 console warning/error；
- `git diff --check`：通过。

## 2026-08-10 事实更正

DOC-004 冷启动审查重新核对当前 `master` 后确认：`front_end/src/views/user/Login.vue` 仍有两处 `console.log`，分别输出完整登录响应和账户响应；登录响应数据包含 token，账户响应包含角色、电话等资料。因此上文“删除前端登录/更新对象 console 日志”不是当前代码事实，不能作为已完成能力或简历亮点。该缺陷已重新登记为 SEC-011，后续必须通过前端测试和构建验证完成修复。冷层保留原记录并追加本更正，用于说明历史结论为什么被修订。

## 未纳入本归档的活跃问题

- 修改后的后端 Docker 镜像尚未因 registry 超时/限流完成构建，见 RUN-002；
- 支付宝和 OSS 凭据尚待只读验证，见 EXT-001；
- 交易一致性、支付回调、Redis Cache-Aside 和核心测试仍未实施；
- 前端资源/CSS/chunk 警告仍活跃，见 FE-001；
- 当次运行恢复时 assistant 缺少 `ARK_API_KEY`；项目所有者随后决定废弃该功能，原 OPS-003 已取消，见 [assistant 废弃决策](2026-08-09-assistant-deprecation-decision.md)。
