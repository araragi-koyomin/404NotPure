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
| 当前阶段 | `fix/payment-callback-consistency` 已完成 DB-001A/PAY-001、SEC-009 和 SEC-010 实现及当前沙箱分层验收：RSA2 密钥格式有效，显式探针只允许官方 HTTPS 沙箱网关并得到精确的 `40004 / ACQ.TRADE_NOT_EXIST`；SDK 原始业务参数日志已按 TDD 修复，真实探针退出码为 0 且控制台为空。Maven 编译通过，当前最终代码连续两轮默认回归均为 104/104，临时迁移数据库 0 个残留；当前分支 JAR 在隔离的 18080 端口启动并通过商品接口检查。[PR #3](https://github.com/araragi-koyomin/404NotPure/pull/3) 已创建，尚未合并 |
| 已完成 | OSS、图片上传、本机运行安全、个人仓库迁移以及 ORD-001 订单与库存一致性已经合并；ORD-001 的实现、TDD 证据、两轮冷启动审查、79 项完整回归和修正版并发重复验证已进入冷层归档 |
| 尚未完成 | DB-001A、PAY-001、SEC-009、SEC-010 和 TEST-002 已完成实现、审查、提交和 PR，仍需合并后转入冷层；支付宝开放平台于 2026-04-29 公告沙箱环境升级，旧 APPID、商家 PID、密钥、网关和临时公网域名均不能直接假定有效；CACHE-001 尚未开始；ORD-002、ORD-003、DB-001、SEC-001、PAY-002 等风险继续保持活跃 |
| 当前阻塞或待确认 | 当前没有支付宝服务器可访问的 `notify_url`，所以真实沙箱异步通知端到端闭环暂未执行；`return_url` 由用户浏览器访问，只影响同步跳转检查，不能代替异步通知。RSA2 回调入口、沙箱只读签名请求和真实 MySQL 支付事务已有证据，这不阻塞 PAY-001 代码审查与合并，但交付记录必须明确该外部验证缺口。完整四容器 Compose 验收仍因镜像拉取和本机端口环境保持 P2 blocked |
| 下一步 | 审阅 PR #3 的完整差异和远端检查；项目所有者确认后再执行 squash merge。合并后立即创建冷层交付记录，并从 BACKLOG 移除 DB-001A、PAY-001、SEC-009、SEC-010、TEST-002。真实异步通知留待以后建立临时 HTTPS 隧道后补验 |
| 本批次不处理 | 已废弃的 AI assistant 和公网长期部署 |

| ID | 优先级 | 状态 | 活跃项 | 完成证据 | 温层文档 |
|---|---|---|---|---|---|
| ORD-002 | P1 | planned | 为结算请求设计跨进程可靠的幂等键和数据库唯一约束；当前请求没有幂等标识，用户重复提交可能创建两个不同订单，不能用进程内 Map 作为替代 | 相同用户和相同幂等键重复或并发请求只产生一个订单并只冻结一次库存；不同幂等键保持正常下单；冲突和失败重试语义有测试 | [交易链路一致性计划](plans/transaction-integrity.md) |
| ORD-003 | P1 | planned | 增加待支付订单取消和超时关闭规则，安全地把冻结库存恢复为可用库存；当前只有 `PENDING -> PAID`，长期未支付订单会一直占用冻结库存 | 明确 `PENDING -> CANCELLED/CLOSED` 的来源、权限、超时依据和库存动作；支付与取消并发时只有一个方向成功；重复取消不重复恢复库存；真实 MySQL 事务和并发测试通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-001 | P0 | in_progress | 在 `fix/payment-callback-consistency` 中完善支付宝回调订单号、金额、通知 `app_id`/`seller_id` 归属、合法成功状态、并发重复通知、支付时间与支付宝交易号处理；先完成 DB-001A 支付字段迁移。由于当前沙箱环境已公告升级，合并前还需重新确认沙箱应用和配置，或明确记录真实沙箱交易未执行的环境原因 | 签名失败、通知归属不符、金额不一致、非法状态、`TRADE_SUCCESS`/`TRADE_FINISHED`、串行与并发重复通知、多商品库存释放异常回滚、成功支付测试全部通过；真实沙箱闭环已执行或未执行原因有明确记录 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-002 | P2 | planned | 修复支付宝同步返回页仅凭浏览器参数显示支付成功并清理购物车的问题，同时统一支付表单接口的失败 `Response`；页面必须以服务端订单状态为准 | 伪造或提前到达的同步返回不会显示成功或清理购物车；订单不存在、非法状态等失败保持 `code/msg/data`；前后端接口测试通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| CACHE-001 | P0 | planned | 完善商品详情 Cache-Aside、稳定 key、随机 TTL、空值保护和写后失效；统一使用带明确类型的 RedisTemplate，并关闭项目未使用的 Redis Repository 扫描 | 命中、回填、空值、更新/删除失效、广告换品旧 key 失效测试通过；编译没有原始 RedisTemplate 引起的类型警告，启动没有无意义的 Redis Repository 扫描提示 | [交易链路一致性计划](plans/transaction-integrity.md) |
| TEST-001 | P0 | in_progress | 订单、库存和支付已经具备可信单元或真实 MySQL 集成测试；剩余工作是补齐 Redis Cache-Aside 行为测试 | ORD-001 与 PAY-001 的业务分支、事务回滚和并发测试可重复通过；Redis 命中、回填、穿透保护和失效测试补齐后才能整体完成 TEST-001 | [交易链路一致性计划](plans/transaction-integrity.md) |
| TEST-002 | P1 | in_progress | 一次性 Maven 3.9.9/Java 17 容器内不添加 `-DforkCount=0`，当前最终代码连续两轮默认回归均为 104/104，且两轮报告分别保存在忽略的 `target/` 目录；技术标准已达到，待合并时写入冷层并从 BACKLOG 移除 | 不添加 `-DforkCount=0` 的 `mvn test` 连续两次通过；记录 Java 内存和测试进程要求；默认测试不访问真实 OSS 或支付宝 | [安全与质量计划](plans/security-and-quality.md) |
| RUN-002 | P2 | blocked | Compose 端口配置已修复，但镜像拉取和完整四容器运行尚未完成一次验收；本机/面试阶段继续使用已验证的混合运行方式 | 容器后端使用 `db:3306`，本机后端使用 `127.0.0.1:3307`，四服务健康且 5173 代理成功 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| SEC-001 | P1 | planned | 继续收敛 Security、拦截器和控制器鉴权边界，补购物车条目与支付表单等订单资源所有权，并统一 Cookie 与请求头认证来源；当前整个 `/api/orders` 前缀被放行，匿名用户可按订单 ID 请求支付表单 | 未登录、普通用户、管理员、跨用户购物车和订单访问测试通过，浏览器与 API 客户端使用统一认证规则 | [安全与质量计划](plans/security-and-quality.md) |
| SEC-009 | P1 | in_progress | 支付宝沙箱只读查询返回预期的“交易不存在”时，SDK 的 `sdk.biz.err` logger 曾输出 APPID、探针订单号、请求参数和响应内容；已在测试与应用运行时关闭该 logger，并增加自动化边界测试，等待随当前分支审查和合并后归档 | Red 阶段两个日志断言准确失败；Green 后目标日志测试 6/6，通过真实沙箱只读探针且控制台为空；默认完整回归通过 | [安全与质量计划](plans/security-and-quality.md) |
| SEC-010 | P2 | in_progress | 合并前审查发现显式支付宝只读探针原先接受任意网关 URL 和通用成功响应，可能误访问生产/第三方地址并产生不准确的“沙箱通过”结论；已按 TDD 限制官方沙箱网关并采用精确响应判断，等待随当前分支合并后归档 | Red 因缺少安全判断方法而准确编译失败；Green 后本地边界测试 3/3、真实沙箱探针 1/1、连续两轮默认回归 104/104 通过，探针控制台为空 | [安全与质量计划](plans/security-and-quality.md) |
| OSS-003 | P2 | planned | 本机/面试阶段暂不自动清理未被商品、广告或头像引用的 OSS 图片；长期实现临时上传、业务保存后确认和过期清理，同时不授予应用删除真实业务图片的权限 | 覆盖业务保存失败、超时重试、重复确认和清理失败；在此之前保持业务图片删除权限关闭 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| DB-001A | P0 | in_progress | 建立 PAY-001 所需的最小版本化迁移路径，保存独立支付完成时间和唯一支付宝交易号，并明确现有数据库基线策略；本项不冒充完整 DB-001 | 支付相关迁移可在受控的全新数据库和现有 schema 副本上成功执行，重复运行不破坏数据，应用不再依赖 Hibernate 自动增加支付字段 | [安全与质量计划](plans/security-and-quality.md) |
| DB-001 | P1 | planned | DB-001A 已建立当前完整 schema 的 V1、支付字段 V2，并把 Hibernate 改为 `validate`；剩余重点是补库存商品唯一约束、上线前处理历史重复数据、确认并清理没有业务接入的 `PaymentInfo/payment_info` 遗留结构，以及建立后续 schema 变更纪律 | 库存唯一约束迁移在全新和现有数据库通过；重复数据有明确拒绝或清理流程；遗留支付表确认无引用和无数据后通过可审计迁移处理；后续实体变更必须伴随新迁移版本 | [安全与质量计划](plans/security-and-quality.md) |
| JPA-001 | P2 | planned | 明确服务层事务和关联数据读取边界，在接口测试证明兼容后关闭 JPA 的 Open Session in View | 设置 `spring.jpa.open-in-view=false`；商品、广告和订单接口没有延迟加载错误；响应生成阶段不再依赖仍然打开的数据库会话 | [安全与质量计划](plans/security-and-quality.md) |
| API-001 | P2 | planned | 对齐前端已调用但后端缺失的订单详情和订单列表 GET 接口 | 前端订单页不再调用不存在接口，所有权测试通过 | [安全与质量计划](plans/security-and-quality.md) |
| FE-001 | P2 | planned | 清理前端缺失背景资源、错误 CSS 注释和大 chunk 构建警告 | `npm run build` 无资源或 CSS 警告，并记录文件拆分策略 | [安全与质量计划](plans/security-and-quality.md) |
| DEPLOY-001 | P2 | planned | 规划公网长期部署、域名/CDN、HTTPS、Secret 管理和 OSS 生产访问模式 | 可重复部署文档、环境隔离、健康检查和公网安全验收完成 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |

## 当前分支与阻塞

- PR #1 已于 2026-08-10 通过 squash merge 进入个人 Fork 的 `master`，合并提交为 `f8c9687f`；PR #2 已于同日通过 squash merge 进入 `master`，合并提交为 `39dbd59d`。当前开发分支为 `fix/payment-callback-consistency`，从包含 ORD-001 归档提交 `fde19ba7` 的 `master` 创建；[PR #3](https://github.com/araragi-koyomin/404NotPure/pull/3) 已创建并以 `master` 为目标，当前尚未合并。
- 原多人仓库保留为只读 `upstream`，其 push URL 为 `DISABLED`。个人 Fork 是当前 `origin`，默认分支为 `master`。
- 原仓库 `main` 与有效项目基线 `lab4` 没有共同祖先，因此个人 `master` 从已验证基线 `093a6c9e` 建立，不强行拼接两段历史。
- RUN-002 的阻塞仅表示完整 Compose 没有完成四容器验收；Java 17 后端、本机 MySQL/Redis、5173 前端代理和主要接口已经在混合运行方式中验证。
- 真实 OSS 权限和配置可能被人工修改。每次面试演示前应显式运行生命周期与业务图片删除权限检查；默认 Maven 测试不得访问真实 OSS。

AI assistant 已由项目所有者决定废弃，因此不作为活跃工作项、运行阻塞或验收目标；决策记录见 [assistant 废弃决策](archive/2026-08-09-assistant-deprecation-decision.md)。

当前交付目标仅为本机运行和面试演示；公网长期部署已延后为 DEPLOY-001，不阻塞 P0 交易链路改造。
