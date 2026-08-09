---
title: OSS、图片上传与本机运行安全整改交付记录
type: report
layer: cold
status: completed
created: 2026-08-10
updated: 2026-08-10
archived_at: 2026-08-10
owners:
  - araragi-koyomin
tags:
  - oss
  - security
  - runtime
  - redis
  - git
  - delivery
related:
  - PROC-001
  - GIT-001
  - SEC-002
  - SEC-003
  - SEC-004
  - SEC-005
  - SEC-006
  - SEC-007
  - SEC-008
  - OSS-001
  - OSS-002
  - OSS-004
  - OSS-005
  - OSS-006
  - OSS-007
  - IMG-001
  - DOC-001
  - DOC-002
  - DOC-003
---

# OSS、图片上传与本机运行安全整改交付记录

## 交付范围

本批次完成 Java 17 本机运行基线、账户公开注册安全、图片上传权限和文件检查、OSS 对象命名与显式验证、敏感网络日志保护、Redis 仅本机暴露、开发文档治理，以及多人课程仓库转为个人 Fork 的 Git 流程。交付分支为 `fix/oss-runtime-security-hardening`；本归档提交将随 [Pull Request #1](https://github.com/araragi-koyomin/404NotPure/pull/1) 以 squash 方式合并到个人 Fork 的 `master`，只有合并成功后本文件才成为 `master` 上的完成证据。

本批次不包含订单与库存一致性、支付宝回调、商品详情 Cache-Aside、统一认证授权、版本化数据库迁移、完整四容器部署验收或废弃 AI assistant 的物理删除。它们仍由热层 BACKLOG 的独立工作项跟踪。

## 已完成内容

- 运行环境改为 Java 17，保留 Spring Boot 2.7.6 与 `javax.persistence` 兼容；增加不回显环境变量值的本机后端启动脚本和 Compose 配置检查。
- 公开注册改用不包含服务端 ID、角色和积分的专用输入对象；服务层再次清空客户端 ID 并固定普通用户角色，资料更新保留数据库中的既有角色。
- 图片上传要求登录，并按头像、商品和广告用途区分普通用户与管理员权限；服务端检查空文件、10 MiB 大小、声明类型、实际文件签名、可解码性和像素数量。
- OSS key 由服务端生成，使用用途隔离目录和 UUID；Endpoint 与 URL 生成规则集中处理，上传使用禁止覆盖元数据。
- 真实 OSS 生命周期检查只操作 `_validation` 隔离目录，执行上传、匿名读取、删除和删除后 404；失败路径区分是否确认上传、清理是否失败和是否可能残留，错误消息不包含对象名或云端配置。
- 业务图片删除权限检查只请求删除随机生成且预先确认不存在的名称，验证应用账户无法删除头像、商品或广告目录对象；没有上传或删除用户图片。
- 生产与测试日志关闭 Apache HTTP 详细请求日志和阿里云 SDK 详细诊断日志，并对常见凭据字段和云端诊断字段进行统一遮挡。
- Compose Redis 宿主机映射限制为 `127.0.0.1:6379`；配置检查要求恰好一条映射，实际旧容器在保留命名数据卷的情况下重新创建后也只发布到本机地址。
- 建立根目录 `AGENTS.md`、三层文档生命周期、热层 BACKLOG 和开发 SOP；个人 Fork 成为 `origin`，原多人仓库成为禁止推送的 `upstream`，个人默认分支为 `master`。

## 验证证据

- 合并前重新执行后端编译和默认测试。测试使用本机 MySQL `127.0.0.1:3307`、Redis `127.0.0.1:6379`、假的 OSS 占位配置，并关闭两个真实 OSS 开关；Surefire 报告为 14 个测试类、52 项通过、0 失败、0 错误、0 跳过。
- 本机曾因 Surefire 独立 Java 测试进程出现原生内存不足，本轮使用 `-DforkCount=0` 完成 52 项测试。该方式不等同于恢复默认进程隔离，后续由 TEST-002 继续处理。
- 两项真实 OSS 检查是较早单独显式执行的集成测试：`OssLifecycleProbeIT` 1/1、`OssBusinessDeletePermissionIT` 1/1。它们不进入默认 Surefire 发现范围，也不计入上面的 52 项；合并前最终回归没有再次访问真实 OSS。
- 前端 `npm run build` 成功。仍存在背景资源、CSS 注释和大文件警告，由 FE-001 跟踪，未虚构为无警告构建。
- Compose 配置检查通过，解析结果区分容器数据库 `db:3306` 与宿主机数据库 `127.0.0.1:3307`；Redis 实际发布地址、`PING` 和配置检查均通过。
- `git diff --check`、所有 `docs/**/*.md` Frontmatter 和内部 Markdown 链接检查通过。
- 多轮冷启动独立审查检查代码、安全边界、兼容性、测试报告和文档。最终没有 P0-P2 合并阻塞；DOC-003 发现的分支说明、测试数量、真实 OSS 测试数量和历史缺陷状态不一致已经修正并再次复核通过。
- GitHub 在创建 PR #1 后报告 `MERGEABLE/CLEAN`，仓库没有配置远端自动检查；因此合并依据是上述本地验证和独立审查，而不是不存在的 CI 状态。

## 完成并从热层移除的工作项

`PROC-001`、`GIT-001`、`SEC-002`、`SEC-003`、`SEC-004`、`SEC-005`、`SEC-006`、`SEC-007`、`SEC-008`、`OSS-001`、`OSS-002`、`OSS-004`、`OSS-005`、`OSS-006`、`OSS-007`、`IMG-001`、`DOC-001`、`DOC-002`、`DOC-003`。

## 仍然活跃的风险和后续工作

- P0：`ORD-001`、`PAY-001`、`CACHE-001`、`TEST-001`，分别处理订单库存事务、支付宝回调、商品缓存和可信交易测试。
- P1：`TEST-002`、`SEC-001`、`DB-001`，分别处理 Maven 默认测试进程、统一认证授权和版本化数据库迁移。
- P2：`RUN-002`、`OSS-003`、`JPA-001`、`API-001`、`FE-001`、`DEPLOY-001`。完整 Compose 仍是 blocked；业务未引用图片还没有自动清理；其余属于本机与面试主链路之后的维护工作。
- JWT 签名密钥仍是历史源码债务，购物车和订单所有权校验仍不完整；它们由 SEC-001 跟踪，不因本次注册和图片鉴权修复而视为完成。
- JPA 仍使用 `ddl-auto: update` 和 Open Session in View；本轮没有新增可审计数据库迁移。

## 文档生命周期结果

- 本交付记录进入冷层并保留合并、验证和未完成范围的证据。
- `solo-repository-transition.md` 和 `oss-review-remediation.md` 已转入冷层，归档不等于删除。
- 长期 `OSS-003` 已迁入仍在维护的 `runtime-and-external-dependencies.md`；其余未来事项继续由对应温层计划承载。
- `docs/BACKLOG.md` 只保留尚未完成的工作项，并把下一批工作切换为订单、库存、支付和 Redis Cache-Aside 一致性改造。
