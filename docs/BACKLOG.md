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
| 主开发批次 | 先建立可持续维护的简历/面试亮点文档，再进入认证与支付展示边界收敛 |
| 当前阶段 | [PR #3](https://github.com/araragi-koyomin/404NotPure/pull/3) 已于 2026-08-10 通过 squash merge 进入个人 Fork 的 `master`，合并提交为 `dca1acd9`；DB-001A、PAY-001、SEC-009 和 SEC-010 已完成冷层归档。DOC-004 已通过项目所有者审阅和冷启动事实复核，提交 `e3a6a612` 已推送，并已创建以 `master` 为目标的 [PR #4](https://github.com/araragi-koyomin/404NotPure/pull/4) |
| 已完成 | OSS、图片上传、本机运行安全、个人仓库迁移、ORD-001 订单与库存一致性、支付宝回调一致性、支付字段 Flyway 迁移和沙箱探针安全边界均已合并并进入冷层；支付交付证据见[支付回调一致性交付记录](archive/2026-08-10-payment-callback-consistency-delivery.md) |
| 尚未完成 | SEC-001 需要收紧认证和订单资源所有权；新发现的 SEC-011 需要删除登录页面输出 token 和账户资料的控制台日志；PAY-002 需要让同步返回页只相信服务端订单状态；PAY-003 在二者完成后使用临时公网 HTTPS 地址执行一次沙箱付款与真实异步通知，不要求固定公网 IP 或长期部署；TEST-002 尚未测得默认 Surefire 稳定运行所需的最低合理内存；CACHE-001、ORD-002、ORD-003、DB-001 等继续保持活跃 |
| 当前阻塞或待确认 | PAY-003 当前没有支付宝服务器可访问的 `notify_url`，且应等待 SEC-001/PAY-002 后再临时开放；这不影响已合并的 PAY-001 回调代码能力。RUN-002 的完整四容器 Compose 验收仍因镜像拉取和本机端口环境保持 P2 blocked |
| 下一步 | 对 [PR #4](https://github.com/araragi-koyomin/404NotPure/pull/4) 做最终 GitHub 差异审阅；获得明确合并授权后执行 squash merge，写入冷层交付证据并从 BACKLOG 移除 DOC-004；随后从最新 `master` 创建独立分支启动 SEC-001 |
| 本批次不处理 | 已废弃的 AI assistant 和公网长期部署 |

| ID | 优先级 | 状态 | 活跃项 | 完成证据 | 温层文档 |
|---|---|---|---|---|---|
| ORD-002 | P1 | planned | 为结算请求设计跨进程可靠的幂等键和数据库唯一约束；当前请求没有幂等标识，用户重复提交可能创建两个不同订单，不能用进程内 Map 作为替代 | 相同用户和相同幂等键重复或并发请求只产生一个订单并只冻结一次库存；不同幂等键保持正常下单；冲突和失败重试语义有测试 | [交易链路一致性计划](plans/transaction-integrity.md) |
| ORD-003 | P1 | planned | 增加待支付订单取消和超时关闭规则，安全地把冻结库存恢复为可用库存；当前只有 `PENDING -> PAID`，长期未支付订单会一直占用冻结库存 | 明确 `PENDING -> CANCELLED/CLOSED` 的来源、权限、超时依据和库存动作；支付与取消并发时只有一个方向成功；重复取消不重复恢复库存；真实 MySQL 事务和并发测试通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-002 | P2 | planned | 修复支付宝同步返回页仅凭浏览器参数显示支付成功并清理购物车的问题，同时统一支付表单接口的失败 `Response`；页面必须以服务端订单状态为准 | 伪造或提前到达的同步返回不会显示成功或清理购物车；订单不存在、非法状态等失败保持 `code/msg/data`；前后端接口测试通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-003 | P2 | planned | 面向个人项目和面试演示完成一次支付宝沙箱端到端验收：不购买固定公网 IP，不做长期部署；SEC-001/PAY-002 完成后，临时提供支付宝服务器可访问的 HTTPS `notify_url`，`return_url` 只承担浏览器跳转 | 沙箱买家完成虚拟付款；支付宝侧交易查询成功；真实异步通知通过验签并返回 `success`；本地订单变为 `PAID`，支付时间和交易号落库，冻结库存只释放一次；验收记录不含账号、订单号、签名或密钥；测试后关闭临时公网入口 | [交易链路一致性计划](plans/transaction-integrity.md) |
| CACHE-001 | P0 | planned | 完善商品详情 Cache-Aside、稳定 key、随机 TTL、空值保护和写后失效；统一使用带明确类型的 RedisTemplate，并关闭项目未使用的 Redis Repository 扫描 | 命中、回填、空值、更新/删除失效、广告换品旧 key 失效测试通过；编译没有原始 RedisTemplate 引起的类型警告，启动没有无意义的 Redis Repository 扫描提示 | [交易链路一致性计划](plans/transaction-integrity.md) |
| TEST-001 | P0 | in_progress | 订单、库存和支付已经具备可信单元或真实 MySQL 集成测试；剩余工作是补齐 Redis Cache-Aside 行为测试 | ORD-001 与 PAY-001 的业务分支、事务回滚和并发测试可重复通过；Redis 命中、回填、穿透保护和失效测试补齐后才能整体完成 TEST-001 | [交易链路一致性计划](plans/transaction-integrity.md) |
| TEST-002 | P1 | in_progress | 默认 Surefire 独立 JVM 已在一次性 Maven 3.9.9/Java 17 容器中连续两轮完成 104/104，但当前只证明现有环境可运行，尚未测量稳定通过所需的最低合理内存，也未在项目中覆盖 Surefire 默认 fork 设置 | 不添加 `-DforkCount=0` 的 `mvn test` 连续两次通过；记录经测量的最低合理内存、实际容器限制和 Surefire 设置；默认测试不访问真实 OSS 或支付宝 | [安全与质量计划](plans/security-and-quality.md) |
| RUN-002 | P2 | blocked | Compose 端口配置已修复，但镜像拉取和完整四容器运行尚未完成一次验收；本机/面试阶段继续使用已验证的混合运行方式 | 容器后端使用 `db:3306`，本机后端使用 `127.0.0.1:3307`，四服务健康且 5173 代理成功 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| SEC-001 | P1 | planned | 继续收敛 Security、拦截器和控制器鉴权边界，补购物车条目与支付表单等订单资源所有权，并统一 Cookie 与请求头认证来源；当前整个 `/api/orders` 前缀被放行，匿名用户可按订单 ID 请求支付表单 | 未登录、普通用户、管理员、跨用户购物车和订单访问测试通过，浏览器与 API 客户端使用统一认证规则 | [安全与质量计划](plans/security-and-quality.md) |
| SEC-011 | P1 | planned | 冷启动审查发现登录页面仍通过 `console.log` 输出完整登录响应和账户响应；登录响应数据包含 token，账户响应包含角色、电话等资料，与旧运行归档“日志已删除”的结论冲突 | 先增加能够发现敏感响应日志的前端检查，再删除登录成功路径的两处输出；前端构建和登录流程验证通过；更正旧归档结论且不输出真实 token 或账户资料 | [安全与质量计划](plans/security-and-quality.md) |
| OSS-003 | P2 | planned | 本机/面试阶段暂不自动清理未被商品、广告或头像引用的 OSS 图片；长期实现临时上传、业务保存后确认和过期清理，同时不授予应用删除真实业务图片的权限 | 覆盖业务保存失败、超时重试、重复确认和清理失败；在此之前保持业务图片删除权限关闭 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| DB-001 | P1 | planned | DB-001A 已建立当前完整 schema 的 V1、支付字段 V2，并把 Hibernate 改为 `validate`；剩余重点是补库存商品唯一约束、上线前处理历史重复数据、确认并清理没有业务接入的 `PaymentInfo/payment_info` 遗留结构，以及建立后续 schema 变更纪律 | 库存唯一约束迁移在全新和现有数据库通过；重复数据有明确拒绝或清理流程；遗留支付表确认无引用和无数据后通过可审计迁移处理；后续实体变更必须伴随新迁移版本 | [安全与质量计划](plans/security-and-quality.md) |
| JPA-001 | P2 | planned | 明确服务层事务和关联数据读取边界，在接口测试证明兼容后关闭 JPA 的 Open Session in View | 设置 `spring.jpa.open-in-view=false`；商品、广告和订单接口没有延迟加载错误；响应生成阶段不再依赖仍然打开的数据库会话 | [安全与质量计划](plans/security-and-quality.md) |
| API-001 | P2 | planned | 对齐前端已调用但后端缺失的订单详情和订单列表 GET 接口 | 前端订单页不再调用不存在接口，所有权测试通过 | [安全与质量计划](plans/security-and-quality.md) |
| FE-001 | P2 | planned | 清理前端缺失背景资源、错误 CSS 注释和大 chunk 构建警告 | `npm run build` 无资源或 CSS 警告，并记录文件拆分策略 | [安全与质量计划](plans/security-and-quality.md) |
| DOC-004 | P2 | in_progress | 在 `docs/resume-interview-highlights.md` 持续维护可直接用于简历和面试的项目亮点；每项必须区分已实现能力、测试证据、设计取舍和仍未完成边界，不能把计划项写成现有能力；当前内容已通过项目所有者审阅并进入 [PR #4](https://github.com/araragi-koyomin/404NotPure/pull/4)，等待合并与归档 | 覆盖订单库存、支付宝回调、Flyway、OSS/图片安全、运行恢复、测试与工程治理；提供简历短句、面试展开、证据链接和禁用表述；AGENTS 与文档治理规则要求后续交付同步维护；冷启动审查无事实夸大或证据错配 | [简历与面试亮点总结](resume-interview-highlights.md) |
| DEPLOY-001 | P2 | planned | 规划公网长期部署、域名/CDN、HTTPS、Secret 管理和 OSS 生产访问模式 | 可重复部署文档、环境隔离、健康检查和公网安全验收完成 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |

## 当前分支与阻塞

- PR #1 已于 2026-08-10 通过 squash merge 进入个人 Fork 的 `master`，合并提交为 `f8c9687f`；PR #2 已于同日合并，提交为 `39dbd59d`；[PR #3](https://github.com/araragi-koyomin/404NotPure/pull/3) 已于同日合并，提交为 `dca1acd9`。当前分支为 `docs/resume-interview-highlights`，从包含 PR #3 归档提交 `5fe35260` 的最新 `master` 创建；[PR #4](https://github.com/araragi-koyomin/404NotPure/pull/4) 已创建，目标为 `master`，尚未合并；远端 `fix/payment-callback-consistency` 保留为历史分支。
- 原多人仓库保留为只读 `upstream`，其 push URL 为 `DISABLED`。个人 Fork 是当前 `origin`，默认分支为 `master`。
- 原仓库 `main` 与有效项目基线 `lab4` 没有共同祖先，因此个人 `master` 从已验证基线 `093a6c9e` 建立，不强行拼接两段历史。
- RUN-002 的阻塞仅表示完整 Compose 没有完成四容器验收；Java 17 后端、本机 MySQL/Redis、5173 前端代理和主要接口已经在混合运行方式中验证。
- 真实 OSS 权限和配置可能被人工修改。每次面试演示前应显式运行生命周期与业务图片删除权限检查；默认 Maven 测试不得访问真实 OSS。

AI assistant 已由项目所有者决定废弃，因此不作为活跃工作项、运行阻塞或验收目标；决策记录见 [assistant 废弃决策](archive/2026-08-09-assistant-deprecation-decision.md)。

当前交付目标仅为本机运行和面试演示；公网长期部署已延后为 DEPLOY-001，不阻塞 P0 交易链路改造。
