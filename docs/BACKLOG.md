---
title: 404NotPure 活跃工作清单
type: backlog
layer: hot
status: active
created: 2026-08-09
updated: 2026-08-11
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
| 主开发批次 | CACHE-001 商品详情 Cache-Aside 与 TEST-001 真实 Redis 行为测试 |
| 当前阶段 | `codex/feat-cache-aside` 已完成 CACHE-001/TEST-001 的实现、两轮独立审查与本机复测；缓存单元测试 8/8、真实 MySQL/Redis 集成测试 17/17，`mvn clean test` 与最终 `mvn test` 均为 28 类、172/172。项目所有者已授权提交、推送、创建 PR 和 squash merge，当前先登记 PERF-001/CACHE-002 后续规划，再执行 Git 交付 |
| 已完成 | OSS、图片上传、本机运行安全、个人仓库迁移、ORD-001 订单与库存一致性、支付宝回调一致性、支付字段 Flyway 迁移、沙箱探针安全边界、简历/面试亮点事实文档、SEC-011/SEC-001，以及 DOC-005 文档一致性修正均已合并并进入冷层交付记录 |
| 尚未完成 | CACHE-001/TEST-001 尚需完成本次 Git/PR/合并与冷层归档；DATA-001 需要提供不会清空数据库的可重复演示数据脚本；PERF-001 将在演示数据完成后建立缓存性能基线，CACHE-002 是否实施取决于压测证据；其余活跃项继续按下表跟踪 |
| 当前阻塞或待确认 | CACHE-001/TEST-001 没有实现、环境或授权阻塞；真实 MySQL 8 与 Redis 6 已完成隔离数据验证，测试仅删除自己创建的商品、广告和缓存 key，未执行 `FLUSHDB`。PAY-003 仍缺支付宝服务器可访问的 `notify_url`；RUN-002 仍缺数据卷重启、日志配置回显和基础镜像稳定拉取证据 |
| 下一步 | 提交并推送 CACHE-001/TEST-001、创建 PR、执行 squash merge；随后完成冷层归档并从 BACKLOG 移除两项。下一开发批次为 DATA-001；演示数据完成后执行 PERF-001，再根据测量结果决定是否实施 CACHE-002 |
| 本批次不处理 | 已废弃的 AI assistant 和公网长期部署 |

| ID | 优先级 | 状态 | 活跃项 | 完成证据 | 温层文档 |
|---|---|---|---|---|---|
| ORD-002 | P1 | planned | 为结算请求设计跨进程可靠的幂等键和数据库唯一约束；当前请求没有幂等标识，用户重复提交可能创建两个不同订单，不能用进程内 Map 作为替代 | 相同用户和相同幂等键重复或并发请求只产生一个订单并只冻结一次库存；不同幂等键保持正常下单；冲突和失败重试语义有测试 | [交易链路一致性计划](plans/transaction-integrity.md) |
| ORD-003 | P1 | planned | 增加待支付订单取消和超时关闭规则，安全地把冻结库存恢复为可用库存；当前只有 `PENDING -> PAID`，长期未支付订单会一直占用冻结库存 | 明确 `PENDING -> CANCELLED/CLOSED` 的来源、权限、超时依据和库存动作；支付与取消并发时只有一个方向成功；重复取消不重复恢复库存；真实 MySQL 事务和并发测试通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-002 | P2 | planned | 修复支付宝同步返回页仅凭浏览器参数显示支付成功并清理购物车的问题，同时统一支付表单接口的失败 `Response`；页面必须以服务端订单状态为准 | 伪造或提前到达的同步返回不会显示成功或清理购物车；订单不存在、非法状态等失败保持 `code/msg/data`；前后端接口测试通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-003 | P2 | planned | 面向个人项目和面试演示完成一次支付宝沙箱端到端验收：不购买固定公网 IP，不做长期部署；SEC-001/PAY-002 完成后，临时提供支付宝服务器可访问的 HTTPS `notify_url`，`return_url` 只承担浏览器跳转 | 沙箱买家完成虚拟付款；支付宝侧交易查询成功；真实异步通知通过验签并返回 `success`；本地订单变为 `PAID`，支付时间和交易号落库，冻结库存只释放一次；验收记录不含账号、订单号、签名或密钥；测试后关闭临时公网入口 | [交易链路一致性计划](plans/transaction-integrity.md) |
| CACHE-001 | P0 | in_progress | 完善商品详情 Cache-Aside、稳定 key、随机 TTL、空值保护和写后失效；统一使用带明确类型的 RedisTemplate，并关闭项目未使用的 Redis Repository 扫描 | 命中、回填、空值、更新/删除失效、广告换品旧 key 失效测试通过；编译没有原始 RedisTemplate 引起的类型警告，启动没有无意义的 Redis Repository 扫描提示 | [交易链路一致性计划](plans/transaction-integrity.md) |
| TEST-001 | P0 | in_progress | 订单、库存和支付已经具备可信单元或真实 MySQL 集成测试；剩余工作是补齐 Redis Cache-Aside 行为测试 | ORD-001 与 PAY-001 的业务分支、事务回滚和并发测试可重复通过；Redis 命中、回填、穿透保护和失效测试补齐后才能整体完成 TEST-001 | [交易链路一致性计划](plans/transaction-integrity.md) |
| DATA-001 | P1 | planned | 为当前空数据库提供本机和面试演示数据：只包含约 12～20 本公版经典书籍、库存、规格、仓库内本地图片和 3～5 个广告，不创建账户、购物车、评论、订单或支付记录；不把演示数据混入 Flyway 正式结构迁移或默认测试 | 提供人工显式执行、可重复且不会清空数据库的注入脚本；重复执行不重复插入；失败不留下半套数据；不会删除用户数据；全新数据库导入后商品列表、详情、库存和广告可用；图片不依赖 OSS/第三方链接 | [本机与面试演示数据计划](plans/demo-data.md) |
| PERF-001 | P1 | planned | 在 DATA-001 后建立可重复的 Redis/MySQL 压力测试，分别测量冷缓存、热缓存、热门 key 过期、相同/随机无效 ID、大量 key 接近过期、Redis 不可用和读写并发；当前没有 QPS、P95/P99 或数据库减压数据，不能提前声称生产级高并发能力 | 固定代码、环境、数据量、并发和持续时间；记录吞吐量、延迟、错误率、Redis 命中率、MySQL 查询/连接/锁等待和资源使用；相同参数至少可重复运行并形成冷层报告，不虚构性能数字 | [Redis 缓存性能验证与热点保护计划](plans/cache-performance.md) |
| CACHE-002 | P1 | planned | 只有 PERF-001 证明热门商品过期会产生明显重复查库或连接池等待后，才实现同一商品在同一时刻只由一个请求查库回填、其他请求等待后重查 Redis；随机无效 ID 和 Redis 整体重启按测量结果分别处理，不提前引入复杂组件 | 真实 MySQL/Redis 并发测试证明同一热门 key 只发生受控回源、异常后不会遗留永久锁或无限等待；用 PERF-001 的相同参数复测并归档对比；缓存故障不破坏数据库正确性 | [Redis 缓存性能验证与热点保护计划](plans/cache-performance.md) |
| TEST-002 | P1 | in_progress | 默认 Surefire 独立 JVM 已在一次性 Maven 3.9.9/Java 17 容器中连续两轮完成 104/104，但当前只证明现有环境可运行，尚未测量稳定通过所需的最低合理内存，也未在项目中覆盖 Surefire 默认 fork 设置 | 不添加 `-DforkCount=0` 的 `mvn test` 连续两次通过；记录经测量的最低合理内存、实际容器限制和 Surefire 设置；默认测试不访问真实 OSS 或支付宝 | [安全与质量计划](plans/security-and-quality.md) |
| RUN-002 | P2 | blocked | Compose 端口配置已修复，本轮四服务已经成功运行且 5173 主链路人工烟雾通过；但尚未完整验证数据卷重启行为、日志不回显配置和基础镜像稳定拉取，因此不能提前标记完成 | 容器后端使用 `db:3306`，本机后端使用 `127.0.0.1:3307`；四服务健康且 5173 代理成功；数据卷重启、日志与镜像拉取证据全部补齐 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| SEC-012 | P1 | planned | 本轮先把 CORS 从反射任意 Origin 改为明确前端来源白名单，并给认证 Cookie 增加 `SameSite=Lax`；CSRF 暂不直接开启，后续需要设计前端 CSRF token、登录/注册和支付宝 notify 精确例外 | 状态修改请求需要可信 CSRF token；前端正常调用、匿名公开接口和支付宝 notify 均有兼容测试；不能只打开开关造成商城请求全部失败 | [安全与质量计划](plans/security-and-quality.md) |
| SEC-013 | P2 | planned | 全局异常处理目前对预期的未登录、越权和业务校验异常直接执行 `printStackTrace()`，会把完整内部调用栈写入控制台并让正常拒绝场景产生大量噪声；本轮不扩大 SEC-011 的前端敏感响应日志范围 | 预期业务拒绝只记录不含 token、Cookie、账户资料和内部堆栈的必要信息；非预期异常仍保留可诊断且经过脱敏的结构化日志，并有日志捕获测试 | [安全与质量计划](plans/security-and-quality.md) |
| CART-001 | P1 | planned | 购物车添加和数量更新目前允许零数或负数，可能保存没有业务意义的数量；本轮 SEC-001 只处理认证与所有权，不把数量规则悄悄包装成已完成 | 添加和更新均只接受正整数；库存上限、失败后购物车不变和统一 `Response` 有接口与数据库状态测试 | [安全与质量计划](plans/security-and-quality.md) |
| ACCT-001 | P1 | planned | `PATCH /api/accounts/{username}/points` 没有可信积分发放规则；旧拦截器路径解析使该接口实际上无法正常使用，本轮保持拒绝，不因认证重构把它意外开放 | 明确谁能因何种业务事件改变积分、是否允许管理员人工调整，并测试普通用户不能自行设置积分 | [安全与质量计划](plans/security-and-quality.md) |
| OSS-003 | P2 | planned | 本机/面试阶段暂不自动清理未被商品、广告或头像引用的 OSS 图片；长期实现临时上传、业务保存后确认和过期清理，同时不授予应用删除真实业务图片的权限 | 覆盖业务保存失败、超时重试、重复确认和清理失败；在此之前保持业务图片删除权限关闭 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| DB-001 | P1 | planned | DB-001A 已建立当前完整 schema 的 V1、支付字段 V2，并把 Hibernate 改为 `validate`；剩余重点是补库存商品唯一约束、上线前处理历史重复数据、确认并清理没有业务接入的 `PaymentInfo/payment_info` 遗留结构，以及建立后续 schema 变更纪律 | 库存唯一约束迁移在全新和现有数据库通过；重复数据有明确拒绝或清理流程；遗留支付表确认无引用和无数据后通过可审计迁移处理；后续实体变更必须伴随新迁移版本 | [安全与质量计划](plans/security-and-quality.md) |
| JPA-001 | P2 | planned | 明确服务层事务和关联数据读取边界，在接口测试证明兼容后关闭 JPA 的 Open Session in View | 设置 `spring.jpa.open-in-view=false`；商品、广告和订单接口没有延迟加载错误；响应生成阶段不再依赖仍然打开的数据库会话 | [安全与质量计划](plans/security-and-quality.md) |
| API-001 | P2 | planned | 对齐前端已调用但后端缺失的订单详情和订单列表 GET 接口 | 前端订单页不再调用不存在接口，所有权测试通过 | [安全与质量计划](plans/security-and-quality.md) |
| FE-001 | P2 | planned | 清理前端缺失背景资源、错误 CSS 注释和大 chunk 构建警告 | `npm run build` 无资源或 CSS 警告，并记录文件拆分策略 | [安全与质量计划](plans/security-and-quality.md) |
| FE-002 | P2 | planned | 普通浏览器冷启动人工烟雾中曾出现登录、个人资料、购物车和商品页面首次点击像整页刷新、第二次才进入目标路由；Vite 当时正在重新优化依赖，预热后的独立浏览器和项目所有者复测均为第一次点击成功，因此不作为持续业务故障或本批次阻塞 | 在未来全新前端依赖缓存启动时记录第一次点击的 URL、Document 请求和 Vite 日志；若稳定复现，再以失败路由烟雾测试修复，不能用重复跳转或要求双击掩盖 | [安全与质量计划](plans/security-and-quality.md) |
| DEPLOY-001 | P2 | planned | 规划公网长期部署、域名/CDN、HTTPS、Secret 管理和 OSS 生产访问模式 | 可重复部署文档、环境隔离、健康检查和公网安全验收完成 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |

## 当前分支与阻塞

- PR #1 已于 2026-08-10 通过 squash merge 进入个人 Fork 的 `master`，合并提交为 `f8c9687f`；PR #2 已于同日合并，提交为 `39dbd59d`；[PR #3](https://github.com/araragi-koyomin/404NotPure/pull/3) 已于同日合并，提交为 `dca1acd9`；[PR #4](https://github.com/araragi-koyomin/404NotPure/pull/4) 已于同日合并，提交为 `4c042501`；[PR #5](https://github.com/araragi-koyomin/404NotPure/pull/5) 已于同日合并，提交为 `21463e4f`；[PR #6](https://github.com/araragi-koyomin/404NotPure/pull/6) 已于同日合并，提交为 `1170d4b2`。当前开发分支为 `codex/feat-cache-aside`，基线是包含 DOC-005 归档的 `master@3e817a0c`。
- 原多人仓库保留为只读 `upstream`，其 push URL 为 `DISABLED`。个人 Fork 是当前 `origin`，默认分支为 `master`。
- 原仓库 `main` 与有效项目基线 `lab4` 没有共同祖先，因此个人 `master` 从已验证基线 `093a6c9e` 建立，不强行拼接两段历史。
- RUN-002 本轮已经增加四个 Compose 服务运行和 5173 浏览器主链路证据，但尚未覆盖原完成标准中的数据卷重启、日志配置回显和基础镜像稳定拉取，因此继续保持 blocked；这不影响本机/面试演示已经验证的当前运行方式。
- 真实 OSS 权限和配置可能被人工修改。每次面试演示前应显式运行生命周期与业务图片删除权限检查；默认 Maven 测试不得访问真实 OSS。

AI assistant 已由项目所有者决定废弃，因此不作为活跃工作项、运行阻塞或验收目标；决策记录见 [assistant 废弃决策](archive/2026-08-09-assistant-deprecation-decision.md)。

当前交付目标仅为本机运行和面试演示；公网长期部署已延后为 DEPLOY-001，不阻塞 P0 交易链路改造。
