---
title: 404NotPure 活跃工作清单
type: backlog
layer: hot
status: active
created: 2026-08-09
updated: 2026-08-10
owners:
  - maintainers
tags:
  - backlog
  - active-work
---

# 404NotPure 活跃工作清单

本文件只记录活跃项。完成项必须移出本文件并归档到 `docs/archive/`。详细设计与 TDD 清单位于对应温层文档。

## 当前开发批次

| 当前信息 | 内容 |
|---|---|
| 主开发批次 | 订单、库存、支付与 Redis Cache-Aside 一致性改造 |
| 当前阶段 | `fix/order-inventory-consistency` 已完成实现、测试和两轮冷启动审查；提交 `c9522f44` 已推送，并创建面向 `master` 的 [PR #2](https://github.com/araragi-koyomin/404NotPure/pull/2)，当前等待 PR 审阅与后续合并授权 |
| 已完成 | OSS、图片上传、本机运行安全和个人仓库迁移已合并；ORD-001 已完成实现、两轮冷启动审查、审查问题修正、79 项完整回归和修正版并发重复验证 |
| 尚未完成 | ORD-001 的 PR #2 尚未合并；合并成功后还需归档完成证据并从热层移除；PAY-001 与 CACHE-001 尚未开始 |
| 当前阻塞或待确认 | 完整四容器 Compose 验收仍因镜像拉取和本机端口环境保持 P2 blocked；这不阻塞本机混合运行和下一批交易链路开发 |
| 下一步 | 审阅 PR #2；获得明确合并授权后再执行 squash merge，并同步完成冷层归档和 BACKLOG 热层移除 |
| 本批次不处理 | 已废弃的 AI assistant 和公网长期部署 |

| ID | 优先级 | 状态 | 活跃项 | 完成证据 | 温层文档 |
|---|---|---|---|---|---|
| ORD-001 | P0 | in_progress | 提交 `c9522f44` 已推送至 `fix/order-inventory-consistency`；[PR #2](https://github.com/araragi-koyomin/404NotPure/pull/2) 已创建并以 `master` 为目标，等待审阅与合并授权 | 定向测试 27/27、全量测试 79/79 通过；修正版真实 MySQL 并发测试额外连续 3 次通过；最终本地检查无阻塞 | [交易链路一致性计划](plans/transaction-integrity.md) |
| ORD-002 | P1 | planned | 为结算请求设计跨进程可靠的幂等键和数据库唯一约束；当前请求没有幂等标识，用户重复提交可能创建两个不同订单，不能用进程内 Map 作为替代 | 相同用户和相同幂等键重复或并发请求只产生一个订单并只冻结一次库存；不同幂等键保持正常下单；冲突和失败重试语义有测试 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-001 | P0 | planned | 完善支付宝回调订单号、金额、合法状态、并发重复通知和支付时间处理 | 签名失败、金额不一致、非法状态、重复通知、成功支付测试全部通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| CACHE-001 | P0 | planned | 完善商品详情 Cache-Aside、稳定 key、随机 TTL、空值保护和写后失效；统一使用带明确类型的 RedisTemplate，并关闭项目未使用的 Redis Repository 扫描 | 命中、回填、空值、更新/删除失效、广告换品旧 key 失效测试通过；编译没有原始 RedisTemplate 引起的类型警告，启动没有无意义的 Redis Repository 扫描提示 | [交易链路一致性计划](plans/transaction-integrity.md) |
| TEST-001 | P0 | in_progress | 本轮先建立订单与库存的可信单元和真实 MySQL 集成测试；支付与 Redis 测试仍留在后续工作 | ORD-001 的业务分支、事务回滚和并发库存测试可重复通过；后续支付与 Redis 测试补齐后才能整体完成 TEST-001 | [交易链路一致性计划](plans/transaction-integrity.md) |
| TEST-002 | P1 | planned | 排查 Maven 测试独立进程曾出现的原生内存不足，恢复不依赖 `-DforkCount=0` 的默认测试方式 | 不添加 `-DforkCount=0` 的 `mvn test` 连续两次通过；记录 Java 内存和测试进程要求；默认测试不访问真实 OSS 或支付宝 | [安全与质量计划](plans/security-and-quality.md) |
| RUN-002 | P2 | blocked | Compose 端口配置已修复，但镜像拉取和完整四容器运行尚未完成一次验收；本机/面试阶段继续使用已验证的混合运行方式 | 容器后端使用 `db:3306`，本机后端使用 `127.0.0.1:3307`，四服务健康且 5173 代理成功 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| SEC-001 | P1 | planned | 继续收敛 Security、拦截器和控制器鉴权边界，补购物车与订单资源所有权，并统一 Cookie 与请求头认证来源 | 未登录、普通用户、管理员、跨用户购物车和订单访问测试通过，浏览器与 API 客户端使用统一认证规则 | [安全与质量计划](plans/security-and-quality.md) |
| OSS-003 | P2 | planned | 本机/面试阶段暂不自动清理未被商品、广告或头像引用的 OSS 图片；长期实现临时上传、业务保存后确认和过期清理，同时不授予应用删除真实业务图片的权限 | 覆盖业务保存失败、超时重试、重复确认和清理失败；在此之前保持业务图片删除权限关闭 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| DB-001 | P1 | planned | 用版本化迁移替代 `ddl-auto: update`，补库存唯一约束和支付时间字段迁移 | 空库和现有库升级测试通过，可回溯 schema 版本 | [安全与质量计划](plans/security-and-quality.md) |
| JPA-001 | P2 | planned | 明确服务层事务和关联数据读取边界，在接口测试证明兼容后关闭 JPA 的 Open Session in View | 设置 `spring.jpa.open-in-view=false`；商品、广告和订单接口没有延迟加载错误；响应生成阶段不再依赖仍然打开的数据库会话 | [安全与质量计划](plans/security-and-quality.md) |
| API-001 | P2 | planned | 对齐前端已调用但后端缺失的订单详情和订单列表 GET 接口 | 前端订单页不再调用不存在接口，所有权测试通过 | [安全与质量计划](plans/security-and-quality.md) |
| FE-001 | P2 | planned | 清理前端缺失背景资源、错误 CSS 注释和大 chunk 构建警告 | `npm run build` 无资源或 CSS 警告，并记录文件拆分策略 | [安全与质量计划](plans/security-and-quality.md) |
| DEPLOY-001 | P2 | planned | 规划公网长期部署、域名/CDN、HTTPS、Secret 管理和 OSS 生产访问模式 | 可重复部署文档、环境隔离、健康检查和公网安全验收完成 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |

## 当前分支与阻塞

- PR #1 已于 2026-08-10 通过 squash merge 进入个人 Fork 的 `master`，合并提交为 `f8c9687f`；当前开发分支为 `fix/order-inventory-consistency`，从该提交创建。
- 原多人仓库保留为只读 `upstream`，其 push URL 为 `DISABLED`。个人 Fork 是当前 `origin`，默认分支为 `master`。
- 原仓库 `main` 与有效项目基线 `lab4` 没有共同祖先，因此个人 `master` 从已验证基线 `093a6c9e` 建立，不强行拼接两段历史。
- RUN-002 的阻塞仅表示完整 Compose 没有完成四容器验收；Java 17 后端、本机 MySQL/Redis、5173 前端代理和主要接口已经在混合运行方式中验证。
- 真实 OSS 权限和配置可能被人工修改。每次面试演示前应显式运行生命周期与业务图片删除权限检查；默认 Maven 测试不得访问真实 OSS。

AI assistant 已由项目所有者决定废弃，因此不作为活跃工作项、运行阻塞或验收目标；决策记录见 [assistant 废弃决策](archive/2026-08-09-assistant-deprecation-decision.md)。

当前交付目标仅为本机运行和面试演示；公网长期部署已延后为 DEPLOY-001，不阻塞 P0 交易链路改造。
