---
title: 404NotPure 活跃工作清单
type: backlog
layer: hot
status: active
created: 2026-08-09
updated: 2026-08-12
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
| 主开发批次 | PERF-001 Redis/MySQL 缓存性能基线 |
| 当前阶段 | `codex/perf-cache-baseline` 已完成 PERF-001 实现、正式本机测量、主开发代理验证和独立复核，独立复核没有剩余 P0～P2，当前等待项目所有者审阅和 Git 操作授权。容量探测从 1、10、25、50 到 100 QPS（每秒完成请求数）均无错误或丢弃；正式稳定场景采用 10、50、100 QPS、20 秒预热、60 秒记录、三轮取中位数。39 个正式结果中 38 个达到当时的硬性正确性和吞吐阈值；唯一失败是 Redis 容器完全停止：10 秒内 0 个业务请求完成并丢弃 401 个计划请求，Redis 恢复后的同速率 501/501 请求正确。旧结果实际为 100% 正确、0 错误、0 丢弃，但由收紧前的阈值脚本生成；当前严格脚本已完成两次 1 QPS 安全烟雾，高负载代表性复测因可用内存 7.74 GiB 低于 8 GiB 开始线而按规则拒绝。Maven 编译及 32 个测试类、201/201 默认回归通过。Redis 故障缺陷已登记为 CACHE-003，不得把当前实现描述成能够及时回退 MySQL |
| 已完成 | OSS、图片上传、本机运行安全、个人仓库迁移、ORD-001 订单与库存一致性、支付宝回调一致性、支付字段 Flyway 迁移、沙箱探针安全边界、简历/面试亮点事实文档、SEC-011/SEC-001、DOC-005、CACHE-001/TEST-001、DATA-001，以及 API-002 商品列表分页与服务端查询均已合并并进入冷层交付记录 |
| 尚未完成 | 独立审查发现的性能容器凭据范围、旧阈值证据说明、结果复用/缺轮检查、热点请求计数和看门狗异常清理问题已经修正；最终 Maven 回归为 32 类、201/201，资源保护脚本 9/9、结果完整性脚本 4/4、异常清理集成测试 1/1、Compose 和格式检查通过，针对性独立复核没有剩余 P0～P2。当前仅等待项目所有者审阅和 Git 暂存、提交、推送、PR 授权。PERF-001 合并后才移入冷层并更新简历/面试亮点 |
| 当前阻塞或待确认 | 原正式测试前 30 秒预检为平均 CPU 20.47%、可用内存 8.99 GiB；有效结果期间 CPU 峰值 62%、最低可用内存 6.95 GiB，未触发运行中停止线。审查修复后的高负载代表性复测预检为平均 CPU 13.4%、可用内存 7.74 GiB，低于 8 GiB 开始线，因此按规则拒绝执行。项目所有者明确禁止关闭其他内容腾出内存，本轮不会降低安全线强行复测。已有三轮数据实际均为 100% 正确、0 错误、0 丢弃，但文档明确说明它们由旧版宽松阈值脚本产生；当前严格脚本只完成 1 QPS 安全烟雾。PAY-003 和其他活跃任务不属于本批次 |
| 下一步 | 项目所有者审阅本轮结果并决定是否授权 Git 操作；若提交前自然可用内存达到 8 GiB，可使用新结果名补做严格脚本的高负载代表性复测，但不得关闭其他程序、降低安全线或复用旧结果前缀 |
| 本批次不处理 | 已废弃的 AI assistant 和公网长期部署 |

| ID | 优先级 | 状态 | 活跃项 | 完成证据 | 温层文档 |
|---|---|---|---|---|---|
| ORD-002 | P1 | planned | 为结算请求设计跨进程可靠的幂等键和数据库唯一约束；当前请求没有幂等标识，用户重复提交可能创建两个不同订单，不能用进程内 Map 作为替代 | 相同用户和相同幂等键重复或并发请求只产生一个订单并只冻结一次库存；不同幂等键保持正常下单；冲突和失败重试语义有测试 | [交易链路一致性计划](plans/transaction-integrity.md) |
| ORD-003 | P1 | planned | 增加待支付订单取消和超时关闭规则，安全地把冻结库存恢复为可用库存；当前只有 `PENDING -> PAID`，长期未支付订单会一直占用冻结库存 | 明确 `PENDING -> CANCELLED/CLOSED` 的来源、权限、超时依据和库存动作；支付与取消并发时只有一个方向成功；重复取消不重复恢复库存；真实 MySQL 事务和并发测试通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-002 | P2 | planned | 修复支付宝同步返回页仅凭浏览器参数显示支付成功并清理购物车的问题，同时统一支付表单接口的失败 `Response`；页面必须以服务端订单状态为准 | 伪造或提前到达的同步返回不会显示成功或清理购物车；订单不存在、非法状态等失败保持 `code/msg/data`；前后端接口测试通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-003 | P2 | planned | 面向个人项目和面试演示完成一次支付宝沙箱端到端验收：不购买固定公网 IP，不做长期部署；SEC-001/PAY-002 完成后，临时提供支付宝服务器可访问的 HTTPS `notify_url`，`return_url` 只承担浏览器跳转 | 沙箱买家完成虚拟付款；支付宝侧交易查询成功；真实异步通知通过验签并返回 `success`；本地订单变为 `PAID`，支付时间和交易号落库，冻结库存只释放一次；验收记录不含账号、订单号、签名或密钥；测试后关闭临时公网入口 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PERF-001 | P1 | in_progress | 使用独立本机 Compose、开源版 k6 和性能专用 Actuator，为纯 MySQL、冷/热缓存、热点过期、相同/随机无效 ID、集中到期、Redis 停止与恢复及真实 HTTP 读写并发建立基线；只测量当前实现，不在本任务实施 CACHE-002 | 固定提交、镜像、300 本数据、容器资源、负载和预热；主要场景三次取中位数，记录完成 QPS、P50/P90/P95/P99、业务与传输错误、Redis/MySQL/Hikari/JVM/Docker 指标；资源看门狗能安全拒绝或中止；生成可复核摘要、图表和冷层报告 | [Redis 缓存性能验证与热点保护计划](plans/cache-performance.md) |
| CACHE-002 | P1 | planned | PERF-001 已证明同一热门商品缓存刚失效时会发生重复回源和数据库行锁排队：100 个同时请求全部正确，但产生 10 次 Redis 未命中、33 次 MySQL `SELECT`、9 次锁等待，累计锁等待约 172 ms，P95 从热缓存稳定场景约 3.56 ms 上升到约 39.79 ms。实现同一商品同一时刻只由一个请求查库回填、其他请求等待后重查 Redis | 真实 MySQL/Redis 并发测试证明同一热门 key 只发生受控回源、异常后不会遗留永久锁或无限等待；用 PERF-001 的 100 请求瞬时场景复测，MySQL 回源与锁等待明显减少且业务结果保持正确；缓存故障不破坏数据库正确性 | [Redis 缓存性能验证与热点保护计划](plans/cache-performance.md) |
| CACHE-003 | P1 | planned | 修复 Redis 完全停止时商品详情请求被客户端连接等待阻塞的问题。PERF-001 在计划 50 QPS、10 秒故障窗口内只有 3 个 HTTP 请求开始、0 个业务请求完成、401 个计划请求未能发出；Redis 恢复后的 501 个请求全部正确，说明恢复正常但故障窗口不可用 | 明确并配置 Redis 连接与命令超时；故障时请求在有界时间内回退 MySQL或按明确策略快速失败，不能无限堆积；加入限流/连接池保护评估；使用 PERF-001 相同 Redis 停止与恢复场景复测，记录业务成功率、丢弃、MySQL/Hikari 压力和恢复结果 | [Redis 缓存性能验证与热点保护计划](plans/cache-performance.md) |
| TEST-002 | P1 | in_progress | 默认 Surefire 独立 JVM 已在一次性 Maven 3.9.9/Java 17 容器中连续两轮完成 104/104，但当前只证明现有环境可运行，尚未测量稳定通过所需的最低合理内存，也未在项目中覆盖 Surefire 默认 fork 设置 | 不添加 `-DforkCount=0` 的 `mvn test` 连续两次通过；记录经测量的最低合理内存、实际容器限制和 Surefire 设置；默认测试不访问真实 OSS 或支付宝 | [安全与质量计划](plans/security-and-quality.md) |
| RUN-002 | P2 | blocked | Compose 端口配置已修复且四服务可运行；Docker Desktop 曾出现内部镜像数据块错误。2026-08-12 进一步确认 frontend 以 Windows bind mount、`CHOKIDAR_USEPOLLING=true` 和 Vite 开发服务器运行，空闲时持续占约 54%～55% 的一个逻辑 CPU；停止 frontend 15 秒后整机 CPU 从 35% 降至 16%，backend/MySQL/Redis 保持健康。项目所有者选择先停止并记录，完整开发模式修复延后 | 除既有端口、数据卷、日志和镜像稳定证据外，使文件轮询默认关闭或可配置；验证热更新仍可用且 frontend 空闲 CPU 回落；生产静态镜像仍由 DEPLOY-001 处理 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
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
| FE-003 | P3 | planned | API-002 的两个独立 Chrome 会话在商品列表浏览器历史恢复时都出现 Vue “尝试写入只读计算值”警告；URL、分类、排序、页码和结果均正确恢复，当前没有可见功能失败，不能在没有定位来源时直接修改业务代码 | 在可重复的新浏览器会话中记录完整组件调用位置；确认是项目绑定还是 Element Plus 内部行为；修复后前进/后退状态保持正确且不再产生该警告 | [安全与质量计划](plans/security-and-quality.md) |
| DEPLOY-001 | P2 | planned | 规划公网长期部署、域名/CDN、HTTPS、Secret 管理和 OSS 生产访问模式 | 可重复部署文档、环境隔离、健康检查和公网安全验收完成 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |

## 当前分支与阻塞

- PR #1 已于 2026-08-10 通过 squash merge 进入个人 Fork 的 `master`，合并提交为 `f8c9687f`；PR #2 已于同日合并，提交为 `39dbd59d`；[PR #3](https://github.com/araragi-koyomin/404NotPure/pull/3) 已于同日合并，提交为 `dca1acd9`；[PR #4](https://github.com/araragi-koyomin/404NotPure/pull/4) 已于同日合并，提交为 `4c042501`；[PR #5](https://github.com/araragi-koyomin/404NotPure/pull/5) 已于同日合并，提交为 `21463e4f`；[PR #6](https://github.com/araragi-koyomin/404NotPure/pull/6) 已于同日合并，提交为 `1170d4b2`；[PR #7](https://github.com/araragi-koyomin/404NotPure/pull/7) 已于 2026-08-11 合并，提交为 `acff6078`；[PR #8](https://github.com/araragi-koyomin/404NotPure/pull/8) 已于同日合并，提交为 `f3b42dca`；[PR #9](https://github.com/araragi-koyomin/404NotPure/pull/9) 已于同日合并，提交为 `b0fb0754`；[PR #10](https://github.com/araragi-koyomin/404NotPure/pull/10) 已于同日合并，提交为 `62acf0f8`。当前开发分支为 `codex/perf-cache-baseline`，基线为完成 API-002 归档的 `master@62acf0f8`，目标集成分支为个人 Fork 的 `master`。
- 原多人仓库保留为只读 `upstream`，其 push URL 为 `DISABLED`。个人 Fork 是当前 `origin`，默认分支为 `master`。
- 原仓库 `main` 与有效项目基线 `lab4` 没有共同祖先，因此个人 `master` 从已验证基线 `093a6c9e` 建立，不强行拼接两段历史。
- RUN-002 本轮已经增加四个 Compose 服务运行和 5173 浏览器主链路证据，但尚未覆盖原完成标准中的数据卷重启、日志配置回显和基础镜像稳定拉取，因此继续保持 blocked；这不影响本机/面试演示已经验证的当前运行方式。
- 真实 OSS 权限和配置可能被人工修改。每次面试演示前应显式运行生命周期与业务图片删除权限检查；默认 Maven 测试不得访问真实 OSS。

AI assistant 已由项目所有者决定废弃，因此不作为活跃工作项、运行阻塞或验收目标；决策记录见 [assistant 废弃决策](archive/2026-08-09-assistant-deprecation-decision.md)。

当前交付目标仅为本机运行和面试演示；公网长期部署已延后为 DEPLOY-001，不阻塞 P0 交易链路改造。
