---
title: 404NotPure 活跃工作清单
type: backlog
layer: hot
status: active
created: 2026-08-09
updated: 2026-08-17
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
| 主开发批次 | SEC-014A：前端严重/高危依赖的兼容升级 |
| 当前阶段 | TDD Green、自动化验证、真实浏览器回归和独立只读审查均已完成，等待项目所有者授权 Git 交付。开发分支 `codex/frontend-dependency-security` 已于 2026-08-17 从个人 `master@a75b8983` 创建。升级前生产依赖审计以退出码 1 复现 1 个严重、5 个高危、4 个中危；兼容升级后严重/高危已清零，只剩 ECharts/Vue ECharts 两个需要跨主要版本处理的中危告警。`ElMessage.center` 类型不兼容、购物车复选框弃用接口和商品表单文本按钮弃用接口均已按最小范围修复；从锁文件重新安装后 11 项 Vitest、5 项敏感日志检查、生产构建和生产依赖审计通过。独立审查修正一处无关冷层差异后，未发现剩余 P0～P3 问题。 |
| 已完成 | OSS、图片上传、本机运行安全、个人仓库迁移、ORD-001 订单与库存一致性、支付宝回调一致性、支付字段 Flyway 迁移、沙箱探针安全边界、简历/面试亮点事实文档、SEC-011/SEC-001、DOC-005、CACHE-001/TEST-001、DATA-001、API-002、PERF-001、CACHE-003 Redis 故障保护、CACHE-002 热门商品单次回填、TEST-002 默认测试进程与内存基线、DB-001B 库存唯一记录与商品外键、CART-001 购物车数量与库存状态、ORD-003 订单取消与超时关闭，以及 ORD-002 结算请求幂等均已合并并进入或正在进入冷层交付记录。 |
| 尚未完成 | 订单读取接口、支付返回页、CSRF、支付宝侧交易关闭等活跃项继续按表格跟踪。当前版本也没有执行 CACHE-002 开启时 Redis 真实停止、CACHE-003 自动保护并在恢复后重新进入 CACHE-002 热点回填的完整复合压测。 |
| 当前阻塞或待确认 | 无环境阻塞；需求设计和开发授权均已确认。严重/高危告警阻止交付，中危继续记录并由兼容修复或 SEC-014B 处理；运行基线保持 Node.js 20.20.2/npm 10.8.2；浏览器验收由 Codex 优先执行，只有工具安全策略明确阻止的步骤才交由项目所有者人工确认。 |
| 下一步 | 等待项目所有者授权暂存 SEC-014A 文件、提交、推送并创建/合并 PR；合并后再把 SEC-014A 从热层移除并写入冷层交付记录。浏览器验收发现的商品详情页字符串商品 ID 和可选手机号空字符串缺陷已分别登记为 CART-002、ACCT-002，不混入本次依赖升级。 |
| 本批次不处理 | ECharts 5 → 6、Vue ECharts 6 → 8 等主要版本迁移；中危告警的跨主要版本修复；已废弃的 AI assistant；公网长期部署 |

| ID | 优先级 | 状态 | 活跃项 | 完成证据 | 温层文档 |
|---|---|---|---|---|---|
| PAY-002 | P2 | planned | 修复支付宝同步返回页仅凭浏览器参数显示支付成功并清理购物车的问题，同时统一支付表单接口的失败 `Response`；页面必须以服务端订单状态为准 | 伪造或提前到达的同步返回不会显示成功或清理购物车；订单不存在、非法状态等失败保持 `code/msg/data`；前后端接口测试通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-003 | P2 | planned | 面向个人项目和面试演示完成一次支付宝沙箱端到端验收：不购买固定公网 IP，不做长期部署；SEC-001/PAY-002 完成后，临时提供支付宝服务器可访问的 HTTPS `notify_url`，`return_url` 只承担浏览器跳转 | 沙箱买家完成虚拟付款；支付宝侧交易查询成功；真实异步通知通过验签并返回 `success`；本地订单变为 `PAID`，支付时间和交易号落库，冻结库存只释放一次；验收记录不含账号、订单号、签名或密钥；测试后关闭临时公网入口 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-004 | P2 | planned | ORD-003 只对齐本地订单到期时间与支付宝支付表单有效期，不调用支付宝交易关闭接口；已经打开的支付宝页面仍可能与本地取消或关闭竞争，真实资金场景可能出现外部已付款而本地已关闭 | 接入支付宝交易关闭或查询；区分交易不存在、已经支付和网络失败；外部调用失败可可靠重试；本地取消与支付宝付款竞争有沙箱证据；如项目接收真实资金，本项必须升级为上线阻塞项 | [交易链路一致性计划](plans/transaction-integrity.md) |
| RUN-002 | P2 | blocked | Compose 端口配置已修复且四服务曾可运行；Docker Desktop 曾出现内部镜像数据块错误。2026-08-12 确认 frontend 以 Windows bind mount、`CHOKIDAR_USEPOLLING=true` 和 Vite 开发服务器运行，空闲时持续占约 54%～55% 的一个逻辑 CPU，项目所有者因此选择停止前端并延后完整开发模式修复。2026-08-15 又确认 Windows 当前同时把 IPv4/IPv6 TCP 8062～8161 列为保留范围，8080 位于其中，导致普通本机监听和 Docker 后端端口映射被系统拒绝；不映射宿主机端口的后端容器仍可正常启动和访问数据库 | 除既有端口、数据卷、日志和镜像稳定证据外，使文件轮询默认关闭或可配置；解决或避开 Windows 8080 保留范围且保持前后端契约一致；验证热更新仍可用且 frontend 空闲 CPU 回落；生产静态镜像仍由 DEPLOY-001 处理 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| SEC-012 | P1 | planned | 本轮先把 CORS 从反射任意 Origin 改为明确前端来源白名单，并给认证 Cookie 增加 `SameSite=Lax`；CSRF 暂不直接开启，后续需要设计前端 CSRF token、登录/注册和支付宝 notify 精确例外 | 状态修改请求需要可信 CSRF token；前端正常调用、匿名公开接口和支付宝 notify 均有兼容测试；不能只打开开关造成商城请求全部失败 | [安全与质量计划](plans/security-and-quality.md) |
| SEC-013 | P2 | planned | 全局异常处理目前对预期的未登录、越权和业务校验异常直接执行 `printStackTrace()`；ORD-002 页面验收还确认支付宝支付表单签名异常会由容器记录完整调用栈及请求业务元数据。两者都会制造日志噪声，并可能暴露订单号、金额、应用标识或回调地址；本轮不扩大 SEC-011 的前端敏感响应日志范围 | 预期业务拒绝只记录不含 token、Cookie、账户资料和内部堆栈的必要信息；支付 SDK 失败转换为稳定错误响应且不记录完整待签名请求；非预期异常仍保留可诊断且经过脱敏的结构化日志，并有日志捕获测试 | [安全与质量计划](plans/security-and-quality.md) |
| SEC-014A | P1 | in_progress | 在不跨主要版本、不运行 `npm audit fix --force` 的前提下，升级能够兼容修复的前端直接依赖和锁文件中的间接依赖。当前严重/高危来源包括 Axios 及其 `form-data`、Element Plus 引入的 Lodash、Vue/Vite 工具链引入的 PostCSS/Nanoid；是否能仅靠兼容升级全部清除必须由实际锁文件和复测证明，不能预先承诺 | 记录升级前后依赖路径与版本；生产依赖严重和高危告警清零，或对仍无法在兼容范围修复的项目停止交付并请项目所有者决定是否扩大至 SEC-014B；前端单元测试、敏感日志检查、生产构建以及账户、商品、购物车、结算浏览器回归通过 | [安全与质量计划](plans/security-and-quality.md) |
| SEC-014B | P2 | planned | 只处理 SEC-014A 完成后仍需跨主要版本才能修复的依赖告警。当前已知 ECharts `<6.1.0` 的中危跨站脚本风险需要 ECharts 6，且 Vue ECharts 也可能需要从 6 升到 8；这类升级可能改变图表组件接口和渲染行为，不能混入兼容升级 | 逐项完成主要版本迁移说明、组件契约测试、图表页面浏览器验收和生产构建；不通过调整审计阈值、忽略告警或无依据声称“项目未使用所以安全”来关闭任务 | [安全与质量计划](plans/security-and-quality.md) |
| SEC-014C | P1 | planned | SEC-014A 重新安装锁文件后，包含开发依赖的完整 `npm audit` 仍报告 1 个严重、5 个高危、6 个中危；严重/高危主要来自 Vitest 2、Vite 5、Rollup 4 和旧版自动导入工具链。它们不进入浏览器生产包，但会由开发者和未来 CI 执行；当前安全版本需要 Vitest/Vite/插件等跨主要版本迁移，不得混进已确认的生产依赖兼容升级 | 独立核对每条开发工具公告的触发条件和修复版本；升级 Vitest、Vite、Vue 插件及自动导入工具链并保持 Node 运行基线兼容；单元测试、类型检查、生产构建、开发服务器和完整审计通过，或对无法修复项给出可核验的隔离措施 | [安全与质量计划](plans/security-and-quality.md) |
| CART-002 | P1 | planned | 商品详情页从路由读取到字符串形式的商品 ID，并原样提交给购物车接口；CART-001 的严格 JSON 正整数解析会拒绝该请求，因此详情页加入购物车稳定返回 HTTP 400，而商品列表的数字 ID 入口正常 | 在前端接口边界把详情页 ID 明确转换并验证为正整数；自动化覆盖详情页和列表页两种入口；真实浏览器中两种入口均能加入购物车，非法路由参数不会发送请求 | [安全与质量计划](plans/security-and-quality.md#cart-002商品详情页加入购物车的商品-id-类型不兼容) |
| ACCT-002 | P1 | planned | 注册页把未填写的可选手机号作为空字符串提交；后端仍对空字符串执行手机号唯一性查询，因此数据库中已有空手机号账户后，后续不填写手机号的注册请求会被错误拒绝为“电话号码已存在” | 注册和资料更新入口把空白可选联系方式统一规范为 `null`；只有非空手机号参与唯一性校验；真实 MySQL 测试覆盖多个不填手机号的账户均可注册、重复真实手机号仍被拒绝，真实浏览器不填手机号可以完成注册和登录 | [安全与质量计划](plans/security-and-quality.md#acct-002可选手机号为空时注册被错误拒绝) |
| SEC-015 | P1 | planned | 支付跳转前，前端把 HttpOnly Cookie 中的登录身份额外复制为 `localStorage.payment_temp_token`，支付宝返回时再恢复。`localStorage` 可被页面 JavaScript 读取且跨页面保留，这会削弱 HttpOnly Cookie 对令牌的保护；伪造同步返回参数还可能触发错误的支付成功展示和购物车清理 | 与 PAY-002 一并删除浏览器可读的临时 JWT；支付宝返回页只查询服务端订单状态，未支付或伪造参数不能显示成功或清理购物车；刷新和跨站返回后仍能依靠 HttpOnly Cookie 正常恢复页面 | [安全与质量计划](plans/security-and-quality.md) |
| TEST-003 | P2 | planned | ORD-002 最终完整回归中，既有 `ProductCacheCommandTimeoutTest` 在宿主机最低剩余约 3.37 GiB 时测得 1707 毫秒并超过 1500 毫秒上限；全新测试进程又出现 1574 毫秒失败，而资源稳定后的单测和完整 339 项回归通过。该测试把 Lettuce 首次类加载、连接初始化、宿主机调度和 200 毫秒命令超时一起计时，存在偶发误报，不能简单放宽阈值 | 先写能区分连接建立与 Redis 命令阶段的确定性测试；只测命令发出到超时回退的时间或改用可控制时钟/事件证据；在 1 GiB 受控容器中连续重复并随完整套件运行均稳定，同时保持真实 Redis 停机验收的 500 毫秒级保护标准不被降低 | [安全与质量计划](plans/security-and-quality.md) |
| ACCT-001 | P1 | planned | `PATCH /api/accounts/{username}/points` 没有可信积分发放规则；旧拦截器路径解析使该接口实际上无法正常使用，本轮保持拒绝，不因认证重构把它意外开放 | 明确谁能因何种业务事件改变积分、是否允许管理员人工调整，并测试普通用户不能自行设置积分 | [安全与质量计划](plans/security-and-quality.md) |
| OSS-003 | P2 | planned | 本机/面试阶段暂不自动清理未被商品、广告或头像引用的 OSS 图片；长期实现临时上传、业务保存后确认和过期清理，同时不授予应用删除真实业务图片的权限 | 覆盖业务保存失败、超时重试、重复确认和清理失败；在此之前保持业务图片删除权限关闭 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| STOCK-001 | P1 | planned | 管理员库存调整接口当前可以把 `amount` 设置为负数，而且尚未定义它表示新的可用库存还是新的总库存；该缺陷在 DB-001B 审计中发现，但数量业务规则与记录唯一性不同，不混入本批次 | 明确调整语义及其与 `frozen` 的关系；拒绝负数和冲突数量；根据规则决定是否增加数据库非负检查约束；并发下单与管理员调整的最终库存由真实 MySQL 测试证明 | [安全与质量计划](plans/security-and-quality.md) |
| DB-001C | P2 | planned | 单独核对并清理没有业务接入的 `PaymentInfo/payment_info` 遗留实体和表；该工作与库存唯一约束的风险、数据检查和回滚方式不同，不再放进同一个 PR | 代码引用、Repository、外键和真实数据检查均有证据；只有确认无引用且无数据后才通过新 Flyway 迁移删除旧表并移除实体；有数据时拒绝破坏性清理 | [安全与质量计划](plans/security-and-quality.md) |
| JPA-001 | P2 | planned | 明确服务层事务和关联数据读取边界，在接口测试证明兼容后关闭 JPA 的 Open Session in View | 设置 `spring.jpa.open-in-view=false`；商品、广告和订单接口没有延迟加载错误；响应生成阶段不再依赖仍然打开的数据库会话 | [安全与质量计划](plans/security-and-quality.md) |
| API-001 | P2 | planned | 对齐前端已调用但后端缺失的订单详情和订单列表 GET 接口 | 前端订单页不再调用不存在接口，所有权测试通过 | [安全与质量计划](plans/security-and-quality.md) |
| FE-001 | P2 | planned | 清理前端缺失背景资源、错误 CSS 注释和大 chunk 构建警告 | `npm run build` 无资源或 CSS 警告，并记录文件拆分策略 | [安全与质量计划](plans/security-and-quality.md) |
| FE-002 | P2 | planned | 普通浏览器冷启动人工烟雾中曾出现登录、个人资料、购物车和商品页面首次点击像整页刷新、第二次才进入目标路由；Vite 当时正在重新优化依赖，预热后的独立浏览器和项目所有者复测均为第一次点击成功，因此不作为持续业务故障或本批次阻塞 | 在未来全新前端依赖缓存启动时记录第一次点击的 URL、Document 请求和 Vite 日志；若稳定复现，再以失败路由烟雾测试修复，不能用重复跳转或要求双击掩盖 | [安全与质量计划](plans/security-and-quality.md) |
| FE-003 | P3 | planned | API-002 的两个独立 Chrome 会话在商品列表浏览器历史恢复时都出现 Vue “尝试写入只读计算值”警告；ORD-002 的商品详情/结算验收再次出现该警告，并伴随 Live2D 装饰组件读取空渲染对象的异常。当前主链路仍可结算，不能在没有定位来源时直接修改业务代码；废弃 assistant 也不因本项恢复维护 | 在可重复的新浏览器会话中记录完整组件调用位置；区分项目绑定、Element Plus 和 Live2D 装饰组件问题；修复后商品浏览、前进/后退和结算状态保持正确且控制台不再出现对应警告或渲染异常 | [安全与质量计划](plans/security-and-quality.md) |
| DEPLOY-001 | P2 | planned | 规划公网长期部署、域名/CDN、HTTPS、Secret 管理和 OSS 生产访问模式 | 可重复部署文档、环境隔离、健康检查和公网安全验收完成 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |

## 推荐处理顺序

CART-001、ORD-003 与 ORD-002 合并归档后，项目所有者已选择先处理前端严重/高危依赖，再回到订单读取和支付返回链路；顺序变化时必须同时更新本节和对应温层计划：

1. SEC-014A：在现有主要版本范围内清除生产依赖严重/高危告警，并完成前端主链路兼容验收；
2. API-001：补齐带订单所有权校验的详情和列表读取接口；
3. PAY-002：支付同步返回页改为查询服务端订单状态，并处理浏览器临时保存 JWT 的 SEC-015 风险；
4. SEC-012：在最终路由契约上增加 CSRF token，并精确保留登录、注册和支付宝异步通知例外；
5. PAY-003：最后执行一次临时 HTTPS 入口下的支付宝沙箱端到端验收。

DB-001C、SEC-013、JPA-001、运行环境和前端质量任务继续按风险与面试价值择机处理。CACHE-002/CACHE-003 真实停机复合压测、多实例后端和长期公网部署不在当前主线上。

## 当前分支与阻塞

- SEC-014A 开发分支 `codex/frontend-dependency-security` 已于 2026-08-17 从个人 `master@a75b8983` 创建；尚未提交、推送或创建 PR。
- ORD-002 功能分支 `codex/order-checkout-idempotency` 已通过 [PR #25](https://github.com/araragi-koyomin/404NotPure/pull/25) squash 合并，功能合并提交为 `a9f406c7`；冷层归档分支 `codex/archive-ord002` 通过 PR #26 合并。本批次只实现结算幂等及其前端重试状态；购物车清理、订单读取接口、支付宝外部关单、多支付渠道完整接入和生产容量测试不在范围内。完整证据见[结算请求幂等交付记录](archive/2026-08-16-order-checkout-idempotency-delivery.md)。
- ORD-003 开发分支 `codex/order-cancellation-timeout` 已于 2026-08-16 从个人 `master@b0a098f0` 创建；功能提交 `95ee7b6b` 通过 [PR #23](https://github.com/araragi-koyomin/404NotPure/pull/23) squash 合并到个人 `master@0a0f72ed`。冷层归档分支为 `codex/archive-ord003`，对应 PR #24；完整证据见[订单取消与超时关闭交付记录](archive/2026-08-16-order-cancellation-expiration-delivery.md)。
- PR #1～#13 的交付历史保留在对应冷层记录；[PR #15](https://github.com/araragi-koyomin/404NotPure/pull/15) 已于 2026-08-14 squash 合并 CACHE-002，提交为 `28b41ad4`。功能分支为 `codex/cache-hotspot-single-flight`，合并后冷层归档使用 `codex/archive-cache002`；目标集成分支始终为个人 `master`。
- TEST-002 功能分支 `codex/test-default-surefire-memory` 已通过 PR #17 squash 合并，合并提交为 `73f73836`；合并后冷层归档使用 `codex/archive-test002`。DB-001B 功能分支 `codex/db-inventory-unique` 已通过 PR #19 squash 合并，合并提交为 `d82a23c9`；合并后冷层归档使用 `codex/archive-db001b`，目标集成分支仍为个人 `master`。
- CART-001 功能分支 `codex/cart-quantity-integrity` 已通过 PR #21 squash 合并，功能合并提交为 `abbb9fec`；冷层归档分支 `codex/archive-cart001` 已通过 PR #22 squash 合并，归档合并提交为 `b0a098f0`。两次交付的目标集成分支均为个人 `master`，当前本地 `master` 与 `origin/master` 的分支提交点一致。
- 原多人仓库保留为只读 `upstream`，其 push URL 为 `DISABLED`。个人 Fork 是当前 `origin`，默认分支为 `master`。
- 原仓库 `main` 与有效项目基线 `lab4` 没有共同祖先，因此个人 `master` 从已验证基线 `093a6c9e` 建立，不强行拼接两段历史。
- RUN-002 本轮已经增加四个 Compose 服务运行和 5173 浏览器主链路证据，但尚未覆盖原完成标准中的数据卷重启、日志配置回显和基础镜像稳定拉取，因此继续保持 blocked；这不影响本机/面试演示已经验证的当前运行方式。
- 真实 OSS 权限和配置可能被人工修改。每次面试演示前应显式运行生命周期与业务图片删除权限检查；默认 Maven 测试不得访问真实 OSS。

AI assistant 已由项目所有者决定废弃，因此不作为活跃工作项、运行阻塞或验收目标；决策记录见 [assistant 废弃决策](archive/2026-08-09-assistant-deprecation-decision.md)。

当前交付目标仅为本机运行和面试演示；公网长期部署已延后为 DEPLOY-001，不阻塞 P0 交易链路改造。
