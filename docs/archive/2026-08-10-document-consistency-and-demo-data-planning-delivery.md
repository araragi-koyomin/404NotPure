---
title: 文档一致性与演示数据规划交付记录
type: report
layer: cold
status: completed
created: 2026-08-10
updated: 2026-08-10
archived_at: 2026-08-10
owners:
  - maintainers
tags:
  - documentation
  - authentication
  - redis
  - demo-data
  - lifecycle
related:
  - DOC-005
  - CACHE-001
  - TEST-001
  - DATA-001
  - PR-6
---

# 文档一致性与演示数据规划交付记录

## 交付结果

DOC-005 已于 2026-08-10 通过 [Pull Request #6](https://github.com/araragi-koyomin/404NotPure/pull/6) 以 squash 方式合并到个人 Fork 的 `master`，合并提交为 `1170d4b2`。

本项只修正文档事实和后续计划，不修改 Java、Vue、数据库结构、Redis 数据或外部服务。用户未跟踪的私人文档、真实 `.env` 和外部服务凭据均未读取或进入提交。

## 修正内容

- 根目录 `AGENTS.md` 不再把已经由 SEC-001/PR #5 修复的精确公开路由、Cookie/请求头统一认证、JWT 环境变量、购物车条目归属和支付表单订单归属写成当前缺陷；仍如实保留 Spring Security `permitAll`、职责分散和 CSRF 关闭等剩余边界。
- 支付与本机环境文档不再把支付表单所有权列为未来任务；PAY-002 只处理同步返回页必须查询服务端订单状态，PAY-003 仍等待 PAY-002 和临时可访问的 `notify_url`。
- Redis 文档明确：`advertisement:product:{productId}` 是由广告创建/更新触发的热门商品详情预热，不是错误设计。CACHE-001 在保留广告预热的前提下补通用商品详情回填、空值保护、事务提交后失效、类型恢复和真实 Redis 测试。
- 推荐任务顺序更新为 DOC-005 → CACHE-001/TEST-001 → DATA-001，并保留其余交易、安全、数据库和运行任务的活跃状态。
- DATA-001 已加入热层 BACKLOG，并新增[本机与面试演示数据计划](../plans/demo-data.md)：范围只包括约 12～20 本公版书籍、库存、规格、仓库内本地图片和广告，不创建账户、购物车、评论、订单或支付记录。

## DATA-001 与测试数据边界

DATA-001 将提供人工显式执行、可重复且不会清空数据库的演示数据脚本。它不进入 Flyway 正式结构迁移，也不由默认 Maven 测试自动执行；若当前 schema 无法提供不会误认用户商品的稳定标识，实现必须暂停确认，不能仅凭普通书名覆盖或删除数据。

CACHE-001/TEST-001 的自动化测试不依赖演示书籍。测试必须自行创建带随机标识的少量 MySQL 商品和 Redis key，只清理自己创建的数据，并禁止执行 `FLUSHDB`。

本地图片用于保证浏览器演示稳定，不作为 OSS 集成证据。真实 OSS 继续由默认关闭的生命周期和业务删除权限探针在单独授权下验证。

## 检查与独立复核

- 检查了全部 `docs/**/*.md` 的必需 YAML Frontmatter，冷层文档包含 `archived_at`；
- 检查了全部本地 Markdown 链接和目标文件；
- 检查了尾随空白并执行 `git diff --check`；
- 冷启动独立审查最初发现两处活跃文档仍把 SEC-001 写成未来任务，修正后针对性复核通过；最终未发现 P0～P3 剩余问题；
- 本项是纯文档改动，因此没有运行 Maven、MySQL、Redis、前端构建或真实外部服务测试。

## 生命周期结果

- DOC-005 已从热层 BACKLOG 移除；
- 本文作为 DOC-005 冷层完成证据保留，归档不等于删除；
- DATA-001、CACHE-001 和 TEST-001 继续保留在热层，不能因规划文档完成而标记实现完成；
- 当前开发批次切换为 CACHE-001/TEST-001，代码分支尚未创建。
