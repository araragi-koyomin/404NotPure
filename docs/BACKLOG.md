---
title: 404NotPure 活跃工作清单
type: backlog
layer: hot
status: active
created: 2026-08-09
updated: 2026-08-16
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
| 主开发批次 | ORD-003：待支付订单取消、超时关闭和冻结库存恢复 |
| 当前阶段 | 已在 `codex/order-cancellation-timeout` 按 TDD 完成 V6、用户取消、自动关闭、库存恢复、支付竞争和支付表单到期实现。冷启动审查发现的状态大小写、迁移历史检查、测试证据、性能隔离和批处理测试隔离问题均已修复，最终复核未发现剩余 P0～P2。完整 Maven 回归为 50 类 310/310；前端构建、预检脚本 4/4、Tomato/tomatomall_demo 实际 V6 对齐与只读复检、Compose 配置检查均通过。当前等待项目所有者验收，尚未暂存、提交或创建 PR。 |
| 已完成 | OSS、图片上传、本机运行安全、个人仓库迁移、ORD-001 订单与库存一致性、支付宝回调一致性、支付字段 Flyway 迁移、沙箱探针安全边界、简历/面试亮点事实文档、SEC-011/SEC-001、DOC-005、CACHE-001/TEST-001、DATA-001、API-002、PERF-001、CACHE-003 Redis 故障保护、CACHE-002 热门商品单次回填、TEST-002 默认测试进程与内存基线、DB-001B 库存唯一记录与商品外键，以及 CART-001 购物车数量与库存状态均已合并并进入冷层交付记录。 |
| 尚未完成 | 订单取消与超时关闭、结算请求幂等、订单读取接口、支付返回页、CSRF 等活跃项继续按表格跟踪。当前版本也没有执行 CACHE-002 开启时 Redis 真实停止、CACHE-003 自动保护并在恢复后重新进入 CACHE-002 热点回填的完整复合压测。 |
| 当前阻塞或待确认 | 无实现或测试阻塞。已明确本轮不接入支付宝交易关闭、不增加订单中心前端取消按钮、不修改购物车；外部交易关闭与晚付款补偿由 PAY-004 独立跟踪。 |
| 下一步 | 项目所有者审阅 ORD-003 的实现和验证结果；只有收到明确 Git 授权后才暂存、提交、推送或创建 PR。支付成功后的购物车清理仍由 PAY-002 单独处理。 |
| 本批次不处理 | 已废弃的 AI assistant 和公网长期部署 |

| ID | 优先级 | 状态 | 活跃项 | 完成证据 | 温层文档 |
|---|---|---|---|---|---|
| ORD-002 | P1 | planned | 为结算请求设计跨进程可靠的幂等键和数据库唯一约束；当前请求没有幂等标识，用户重复提交可能创建两个不同订单，不能用进程内 Map 作为替代 | 相同用户和相同幂等键重复或并发请求只产生一个订单并只冻结一次库存；不同幂等键保持正常下单；冲突和失败重试语义有测试 | [交易链路一致性计划](plans/transaction-integrity.md) |
| ORD-003 | P1 | in_progress | 增加待支付订单取消和超时关闭规则，安全地把冻结库存恢复为可用库存；开发分支为 `codex/order-cancellation-timeout`，实现、本机数据库迁移、测试和独立复核已经完成，等待项目所有者验收与 Git 授权 | `PENDING -> CANCELLED/CLOSED` 的来源、所有权、30 分钟期限和库存动作明确；支付/取消、支付/自动关闭、双扫描器只有一个状态更新赢家；重复取消不重复恢复库存；真实 MySQL 逐单事务、并发、V6 迁移及 310 项完整回归通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-002 | P2 | planned | 修复支付宝同步返回页仅凭浏览器参数显示支付成功并清理购物车的问题，同时统一支付表单接口的失败 `Response`；页面必须以服务端订单状态为准 | 伪造或提前到达的同步返回不会显示成功或清理购物车；订单不存在、非法状态等失败保持 `code/msg/data`；前后端接口测试通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-003 | P2 | planned | 面向个人项目和面试演示完成一次支付宝沙箱端到端验收：不购买固定公网 IP，不做长期部署；SEC-001/PAY-002 完成后，临时提供支付宝服务器可访问的 HTTPS `notify_url`，`return_url` 只承担浏览器跳转 | 沙箱买家完成虚拟付款；支付宝侧交易查询成功；真实异步通知通过验签并返回 `success`；本地订单变为 `PAID`，支付时间和交易号落库，冻结库存只释放一次；验收记录不含账号、订单号、签名或密钥；测试后关闭临时公网入口 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-004 | P2 | planned | ORD-003 只对齐本地订单到期时间与支付宝支付表单有效期，不调用支付宝交易关闭接口；已经打开的支付宝页面仍可能与本地取消或关闭竞争，真实资金场景可能出现外部已付款而本地已关闭 | 接入支付宝交易关闭或查询；区分交易不存在、已经支付和网络失败；外部调用失败可可靠重试；本地取消与支付宝付款竞争有沙箱证据；如项目接收真实资金，本项必须升级为上线阻塞项 | [交易链路一致性计划](plans/transaction-integrity.md) |
| RUN-002 | P2 | blocked | Compose 端口配置已修复且四服务曾可运行；Docker Desktop 曾出现内部镜像数据块错误。2026-08-12 确认 frontend 以 Windows bind mount、`CHOKIDAR_USEPOLLING=true` 和 Vite 开发服务器运行，空闲时持续占约 54%～55% 的一个逻辑 CPU，项目所有者因此选择停止前端并延后完整开发模式修复。2026-08-15 又确认 Windows 当前同时把 IPv4/IPv6 TCP 8062～8161 列为保留范围，8080 位于其中，导致普通本机监听和 Docker 后端端口映射被系统拒绝；不映射宿主机端口的后端容器仍可正常启动和访问数据库 | 除既有端口、数据卷、日志和镜像稳定证据外，使文件轮询默认关闭或可配置；解决或避开 Windows 8080 保留范围且保持前后端契约一致；验证热更新仍可用且 frontend 空闲 CPU 回落；生产静态镜像仍由 DEPLOY-001 处理 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| SEC-012 | P1 | planned | 本轮先把 CORS 从反射任意 Origin 改为明确前端来源白名单，并给认证 Cookie 增加 `SameSite=Lax`；CSRF 暂不直接开启，后续需要设计前端 CSRF token、登录/注册和支付宝 notify 精确例外 | 状态修改请求需要可信 CSRF token；前端正常调用、匿名公开接口和支付宝 notify 均有兼容测试；不能只打开开关造成商城请求全部失败 | [安全与质量计划](plans/security-and-quality.md) |
| SEC-013 | P2 | planned | 全局异常处理目前对预期的未登录、越权和业务校验异常直接执行 `printStackTrace()`，会把完整内部调用栈写入控制台并让正常拒绝场景产生大量噪声；本轮不扩大 SEC-011 的前端敏感响应日志范围 | 预期业务拒绝只记录不含 token、Cookie、账户资料和内部堆栈的必要信息；非预期异常仍保留可诊断且经过脱敏的结构化日志，并有日志捕获测试 | [安全与质量计划](plans/security-and-quality.md) |
| ACCT-001 | P1 | planned | `PATCH /api/accounts/{username}/points` 没有可信积分发放规则；旧拦截器路径解析使该接口实际上无法正常使用，本轮保持拒绝，不因认证重构把它意外开放 | 明确谁能因何种业务事件改变积分、是否允许管理员人工调整，并测试普通用户不能自行设置积分 | [安全与质量计划](plans/security-and-quality.md) |
| OSS-003 | P2 | planned | 本机/面试阶段暂不自动清理未被商品、广告或头像引用的 OSS 图片；长期实现临时上传、业务保存后确认和过期清理，同时不授予应用删除真实业务图片的权限 | 覆盖业务保存失败、超时重试、重复确认和清理失败；在此之前保持业务图片删除权限关闭 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| STOCK-001 | P1 | planned | 管理员库存调整接口当前可以把 `amount` 设置为负数，而且尚未定义它表示新的可用库存还是新的总库存；该缺陷在 DB-001B 审计中发现，但数量业务规则与记录唯一性不同，不混入本批次 | 明确调整语义及其与 `frozen` 的关系；拒绝负数和冲突数量；根据规则决定是否增加数据库非负检查约束；并发下单与管理员调整的最终库存由真实 MySQL 测试证明 | [安全与质量计划](plans/security-and-quality.md) |
| DB-001C | P2 | planned | 单独核对并清理没有业务接入的 `PaymentInfo/payment_info` 遗留实体和表；该工作与库存唯一约束的风险、数据检查和回滚方式不同，不再放进同一个 PR | 代码引用、Repository、外键和真实数据检查均有证据；只有确认无引用且无数据后才通过新 Flyway 迁移删除旧表并移除实体；有数据时拒绝破坏性清理 | [安全与质量计划](plans/security-and-quality.md) |
| JPA-001 | P2 | planned | 明确服务层事务和关联数据读取边界，在接口测试证明兼容后关闭 JPA 的 Open Session in View | 设置 `spring.jpa.open-in-view=false`；商品、广告和订单接口没有延迟加载错误；响应生成阶段不再依赖仍然打开的数据库会话 | [安全与质量计划](plans/security-and-quality.md) |
| API-001 | P2 | planned | 对齐前端已调用但后端缺失的订单详情和订单列表 GET 接口 | 前端订单页不再调用不存在接口，所有权测试通过 | [安全与质量计划](plans/security-and-quality.md) |
| FE-001 | P2 | planned | 清理前端缺失背景资源、错误 CSS 注释和大 chunk 构建警告 | `npm run build` 无资源或 CSS 警告，并记录文件拆分策略 | [安全与质量计划](plans/security-and-quality.md) |
| FE-002 | P2 | planned | 普通浏览器冷启动人工烟雾中曾出现登录、个人资料、购物车和商品页面首次点击像整页刷新、第二次才进入目标路由；Vite 当时正在重新优化依赖，预热后的独立浏览器和项目所有者复测均为第一次点击成功，因此不作为持续业务故障或本批次阻塞 | 在未来全新前端依赖缓存启动时记录第一次点击的 URL、Document 请求和 Vite 日志；若稳定复现，再以失败路由烟雾测试修复，不能用重复跳转或要求双击掩盖 | [安全与质量计划](plans/security-and-quality.md) |
| FE-003 | P3 | planned | API-002 的两个独立 Chrome 会话在商品列表浏览器历史恢复时都出现 Vue “尝试写入只读计算值”警告；URL、分类、排序、页码和结果均正确恢复，当前没有可见功能失败，不能在没有定位来源时直接修改业务代码 | 在可重复的新浏览器会话中记录完整组件调用位置；确认是项目绑定还是 Element Plus 内部行为；修复后前进/后退状态保持正确且不再产生该警告 | [安全与质量计划](plans/security-and-quality.md) |
| DEPLOY-001 | P2 | planned | 规划公网长期部署、域名/CDN、HTTPS、Secret 管理和 OSS 生产访问模式 | 可重复部署文档、环境隔离、健康检查和公网安全验收完成 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |

## 推荐处理顺序

CART-001 合并归档后，优先按以下依赖顺序选择开发批次；顺序变化时必须同时更新本节和对应温层计划：

1. ORD-003：增加待支付订单取消、超时关闭和冻结库存恢复；
2. ORD-002：为重复或并发结算请求增加跨进程幂等保护；
3. API-001：补齐带订单所有权校验的详情和列表读取接口；
4. PAY-002：支付同步返回页改为查询服务端订单状态；
5. SEC-012：在最终路由契约上增加 CSRF token，并精确保留登录、注册和支付宝异步通知例外；
6. PAY-003：最后执行一次临时 HTTPS 入口下的支付宝沙箱端到端验收。

DB-001C、SEC-013、JPA-001、运行环境和前端质量任务继续按风险与面试价值择机处理。CACHE-002/CACHE-003 真实停机复合压测、多实例后端和长期公网部署不在当前主线上。

## 当前分支与阻塞

- ORD-003 开发分支 `codex/order-cancellation-timeout` 已于 2026-08-16 从个人 `master@b0a098f0` 创建，计划集成分支为个人 `master`。需求已确认并完成实现与本机验证；仍需独立审查、项目所有者验收和明确 Git 授权。
- PR #1～#13 的交付历史保留在对应冷层记录；[PR #15](https://github.com/araragi-koyomin/404NotPure/pull/15) 已于 2026-08-14 squash 合并 CACHE-002，提交为 `28b41ad4`。功能分支为 `codex/cache-hotspot-single-flight`，合并后冷层归档使用 `codex/archive-cache002`；目标集成分支始终为个人 `master`。
- TEST-002 功能分支 `codex/test-default-surefire-memory` 已通过 PR #17 squash 合并，合并提交为 `73f73836`；合并后冷层归档使用 `codex/archive-test002`。DB-001B 功能分支 `codex/db-inventory-unique` 已通过 PR #19 squash 合并，合并提交为 `d82a23c9`；合并后冷层归档使用 `codex/archive-db001b`，目标集成分支仍为个人 `master`。
- CART-001 功能分支 `codex/cart-quantity-integrity` 已通过 PR #21 squash 合并，功能合并提交为 `abbb9fec`；冷层归档分支 `codex/archive-cart001` 已通过 PR #22 squash 合并，归档合并提交为 `b0a098f0`。两次交付的目标集成分支均为个人 `master`，当前本地 `master` 与 `origin/master` 的分支提交点一致。
- 原多人仓库保留为只读 `upstream`，其 push URL 为 `DISABLED`。个人 Fork 是当前 `origin`，默认分支为 `master`。
- 原仓库 `main` 与有效项目基线 `lab4` 没有共同祖先，因此个人 `master` 从已验证基线 `093a6c9e` 建立，不强行拼接两段历史。
- RUN-002 本轮已经增加四个 Compose 服务运行和 5173 浏览器主链路证据，但尚未覆盖原完成标准中的数据卷重启、日志配置回显和基础镜像稳定拉取，因此继续保持 blocked；这不影响本机/面试演示已经验证的当前运行方式。
- 真实 OSS 权限和配置可能被人工修改。每次面试演示前应显式运行生命周期与业务图片删除权限检查；默认 Maven 测试不得访问真实 OSS。

AI assistant 已由项目所有者决定废弃，因此不作为活跃工作项、运行阻塞或验收目标；决策记录见 [assistant 废弃决策](archive/2026-08-09-assistant-deprecation-decision.md)。

当前交付目标仅为本机运行和面试演示；公网长期部署已延后为 DEPLOY-001，不阻塞 P0 交易链路改造。
