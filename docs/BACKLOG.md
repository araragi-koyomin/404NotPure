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
| 主开发批次 | OSS 与图片上传安全整改交付、个人仓库接管与 Git 分支迁移 |
| 当前阶段 | 个人 Fork 已创建；本地 `origin` 已切换到个人 Fork、原仓库已改为禁止推送的 `upstream`；`master` 和 `fix/oss-runtime-security-hardening` 已从有效基线建立，正在审查和暂存 fix 分支改动 |
| 已完成 | OSS 与图片安全实现、后端 52/52 测试、前端构建、Compose 配置检查、真实 OSS 生命周期检查、业务图片删除权限检查、Redis 实际本机绑定和历次审查问题修复 |
| 尚未完成 | 审查暂存清单、提交 fix 分支、推送 `master` 和 fix 分支、设置个人 Fork 默认分支；后续 Pull Request、merge 和冷层归档不在本次自动执行范围内 |
| 当前阻塞或待确认 | 完整 Compose 保持 P2 blocked；原仓库 `main` 与有效 `lab4` 没有共同祖先，因此明确不合并两段历史 |
| 下一步 | 显式暂存本轮项目文件，排除 `.env`、本地工具、构建产物和 `面试回答指南.md`；通过敏感信息与格式检查后创建 fix 提交并推送两个分支 |
| 本批次不处理 | 订单、库存、支付、Redis Cache-Aside、废弃的 AI assistant 和公网部署 |

| ID | 优先级 | 状态 | 活跃项 | 完成证据 | 温层文档 |
|---|---|---|---|---|---|
| ORD-001 | P0 | planned | 建立下单事务边界、正数校验和并发库存控制，确保失败全量回滚且不超卖 | 正常、库存不足、非法数量、事务回滚、真实数据库并发测试全部通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| PAY-001 | P0 | planned | 完善支付宝回调订单号、金额、合法状态、并发重复通知和支付时间处理 | 签名失败、金额不一致、非法状态、重复通知、成功支付测试全部通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| CACHE-001 | P0 | planned | 完善商品详情 Cache-Aside、稳定 key、随机 TTL、空值保护和写后失效；统一使用带明确类型的 RedisTemplate，并关闭项目未使用的 Redis Repository 扫描 | 命中、回填、空值、更新/删除失效、广告换品旧 key 失效测试通过；编译没有原始 RedisTemplate 引起的类型警告，启动没有无意义的 Redis Repository 扫描提示 | [交易链路一致性计划](plans/transaction-integrity.md) |
| TEST-001 | P0 | planned | 建立订单、库存、支付、Redis 的可信单元与集成测试基线 | Maven 测试覆盖核心分支，并在 MySQL/Redis 环境重复通过 | [交易链路一致性计划](plans/transaction-integrity.md) |
| TEST-002 | P1 | planned | 排查 Maven 测试独立进程曾出现的原生内存不足，恢复不依赖 `-DforkCount=0` 的默认测试方式 | 不添加 `-DforkCount=0` 的 `mvn test` 连续两次通过；记录 Java 内存和测试进程要求；默认测试不访问真实 OSS 或支付宝 | [安全与质量计划](plans/security-and-quality.md) |
| PROC-001 | P1 | in_progress | 建立固定检查步骤、保留技术选择空间的开发 SOP，并在 BACKLOG 显示当前开发批次和阶段 | SOP、文档治理规则和 AGENTS 入口一致；Frontmatter、链接和格式检查通过；合并后从 BACKLOG 移除 | [开发流程 SOP](DEVELOPMENT_SOP.md) |
| GIT-001 | P1 | in_progress | Fork、远端和本地分支迁移已完成：`origin` 指向个人 Fork；`upstream` 保留原仓库 fetch 且 push 为 `DISABLED`；`master` 指向 `093a6c9e`；当前工作区位于 `fix/oss-runtime-security-hardening`，待提交和推送 | 两个分支推送成功；Fork 默认分支为 `master`；提交不含敏感配置、本地工具、构建产物或用户的面试文档；后续 PR merge 后归档 | [个人仓库接管与 Git 分支迁移计划](plans/solo-repository-transition.md) |
| SEC-007 | P0 | in_progress | 已使用专用注册输入并在服务层清空 ID，阻止匿名注册请求通过客户端已有账户 ID 覆盖数据库账户；真实数据库红测先复现原账户被覆盖，修复后转绿，独立复核已确认目标成立，待合并 | 真实数据库预置账户后，携带其 ID 的注册请求创建了数据库分配的新账户；原账户、密码、角色和积分不变；目标测试 1/1、相关安全测试 4/4、完整后端 52/52 通过 | [OSS 评审整改计划](plans/oss-review-remediation.md) |
| SEC-008 | P1 | in_progress | 已在不删除 Redis 数据卷的前提下重新创建旧容器，实际发布地址从所有网卡改为 `127.0.0.1:6379`；键数量重启前后均为 0，容器健康且 `PING` 通过；独立复核确认配置与证据一致，待合并 | `docker compose ps` 只显示 `127.0.0.1:6379`；Compose 配置脚本和 Redis `PING` 通过；更新过程没有删除数据卷或影响 MySQL | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| SEC-005 | P0 | in_progress | 已修复注册和资料更新角色提权，3 个红测从 3/3 失败转为 3/3 通过，相关鉴权回归 9/9、前端构建成功；待完整回归与二审 | 注册提交 ADMIN 仍创建普通用户；普通用户更新 role 不能提权；已有管理员更新资料保持管理员；服务与接口测试通过 | [OSS 评审整改计划](plans/oss-review-remediation.md) |
| OSS-001 | P0 | in_progress | 新凭据下自动 Put→Get→Delete→404 已成功、对象无残留且输出无敏感日志；已进入待评审/待合并 | 新凭据下探针无敏感日志并完成无残留闭环；业务图片前缀不可删除；评审并 merge 后归档 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| SEC-002 | P0 | in_progress | 已实现普通日志和异常消息的秘密遮挡，并关闭阿里云 SDK 详细诊断日志；目标测试 9/9、真实权限复验 1/1、当前完整后端测试 52/52 通过且控制台无云账户诊断详情，待合并 | 已知危险日志类别关闭；统一遮挡测试覆盖 Authorization、Cookie、token、签名、AccessKey 和云端诊断字段；真实 OSS 验证不输出凭据或云账户诊断详情 | [OSS 评审整改计划](plans/oss-review-remediation.md) |
| SEC-004 | P1 | in_progress | 已用测试日志配置抑制 Spring Boot 临时开发安全口令输出，红绿测试和 31 项完整回归通过；待评审/合并 | 原生日志红测可复现；默认 Maven 测试输出不再包含生成口令提示；完整测试通过 | [安全与质量计划](plans/security-and-quality.md) |
| SEC-006 | P1 | in_progress | Compose Redis 只绑定 `127.0.0.1:6379`，配置测试要求恰好一条映射；临时追加第二条公网映射时测试准确失败，恢复安全配置后通过，待合并 | 配置测试能阻止直接改回公网绑定，也能阻止在安全映射之外增加第二条公网映射；MySQL 端口约定保持不变 | [OSS 评审整改计划](plans/oss-review-remediation.md) |
| SEC-003 | P0 | in_progress | 已关闭匿名图片写入、移除注册前头像上传，并按头像/商品/广告用途鉴权和校验文件；待评审、合并和归档 | 未登录上传业务码 401；普通用户商品图 403、头像 200；大小、声明类型和实际内容测试通过 | [安全与质量计划](plans/security-and-quality.md) |
| OSS-002 | P1 | in_progress | 已使用用途隔离前缀、服务端 UUID、禁止覆盖元数据和规范化 Endpoint；待读取闭环、评审、合并和归档 | 唯一 key、用途前缀、Content-Type、禁止覆盖和 URL 生成测试通过，真实写入成功 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| IMG-001 | P1 | in_progress | 已增加解码前元数据尺寸校验；编译红测后工具/服务回归 10/10 通过，PNG/JPEG/GIF 兼容且超限不调用 OSS；待完整回归与二审 | 超大尺寸头部红测先失败；实现不创建完整 BufferedImage 即拒绝；PNG/JPEG/GIF 正常样本仍通过 | [OSS 评审整改计划](plans/oss-review-remediation.md) |
| RUN-002 | P2 | blocked | 端口缺陷已修复，Compose 解析测试确认容器 `db:3306` 与宿主机 `127.0.0.1:3307`；镜像构建再次长时间无输出，四容器验收仍被 registry/当前端口环境阻塞。本机/面试阶段可使用已验证的混合运行方式 | 容器后端使用 `db:3306`，本机后端使用 `127.0.0.1:3307`，四服务健康且 5173 代理成功 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| SEC-001 | P1 | planned | 在 SEC-005 独立修复注册提权后，继续收敛 Security/拦截器/控制器鉴权边界、资源所有权和两套 token 来源 | 未登录、普通用户、管理员、跨用户购物车和订单访问测试通过，浏览器与 API 客户端使用统一认证规则 | [安全与质量计划](plans/security-and-quality.md) |
| OSS-003 | P2 | planned | 项目所有者已确认：本机/面试阶段暂不实现自动清理未被商品、广告或头像使用的 OSS 图片；完整的“临时上传、业务保存后确认、过期清理”方案保留为长期工作，应用不得获得删除真实业务图片的权限 | 后续完整方案覆盖业务保存失败、超时重试、重复确认和清理失败；在此之前保持业务图片删除权限关闭 | [OSS 评审整改计划](plans/oss-review-remediation.md) |
| OSS-004 | P2 | in_progress | PNG/JPEG/GIF 和类型不一致测试通过；真实验证确认阿里云拒绝应用删除头像、商品和广告目录中的随机不存在文件，复验输出干净，当前完整后端测试 52/52 通过，待合并 | 三种格式行为测试通过；随机不存在的业务文件名删除请求返回权限拒绝且不触及用户对象 | [OSS 评审整改计划](plans/oss-review-remediation.md) |
| OSS-005 | P2 | in_progress | 生命周期验证会在上传前确认 404，上传异常时不删除未确认创建的文件，失败消息直接断言不含对象名；流程测试 5/5、相关工具测试 16/16、真实生命周期复验 1/1 通过，待合并 | 名称已存在时停止且不删除；只有上传明确成功后才自动清理；失败日志不包含随机文件名 | [OSS 评审整改计划](plans/oss-review-remediation.md) |
| OSS-006 | P2 | in_progress | 已为上传结果不确定的失败增加 `residuePossible=true` 提示，继续禁止盲删未确认创建的对象；红测先失败后转绿，独立复核已确认目标成立，待合并 | 生命周期测试现为 6/6、完整后端 52/52 通过；失败结果明确提示可能残留且不包含对象名、Bucket、Endpoint 或凭据；不扩大业务图片删除权限 | [OSS 评审整改计划](plans/oss-review-remediation.md) |
| OSS-007 | P2 | in_progress | 已为匿名读取抛异常和返回非 200 两条路径补充 Red 测试；修复后仍执行删除，但因为没有删除后 404 证据，保守报告 `cleanupFailed=false, residuePossible=true`；独立复核确认语义和测试成立，待合并 | Red 阶段 6 项中 2 项因错误报告不会残留而失败；Green 后生命周期测试 6/6、完整后端 52/52 通过，消息不包含对象名且删除仍被调用 | [OSS 评审整改计划](plans/oss-review-remediation.md) |
| DOC-001 | P3 | in_progress | 当前结果已统一为 52/52，历史 31/31、48/48、49/49、50/50 和 51/51 标明对应阶段；assistant 废弃文档状态改为 `cancelled`，待合并 | 当前验收数字与命令对应；历史数字明确标为历史；冷层状态符合 `completed/superseded/cancelled` 约定 | [OSS 评审整改计划](plans/oss-review-remediation.md) |
| DOC-002 | P3 | in_progress | 已把 Compose 数据库端口描述改为历史缺陷已修复，并保留镜像拉取和四容器尚未完整验收的真实阻塞，待独立复核 | 温层计划明确区分历史缺陷、已修复配置和仍未完成的四容器运行验收；与 Compose 和配置测试一致 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |
| DB-001 | P1 | planned | 用版本化迁移替代 `ddl-auto: update`，补库存唯一约束和支付时间字段迁移 | 空库和现有库升级测试通过，可回溯 schema 版本 | [安全与质量计划](plans/security-and-quality.md) |
| JPA-001 | P2 | planned | 明确服务层事务和关联数据读取边界，在接口测试证明兼容后关闭 JPA 的 Open Session in View | 设置 `spring.jpa.open-in-view=false`；商品、广告和订单接口没有延迟加载错误；响应生成阶段不再依赖仍然打开的数据库会话 | [安全与质量计划](plans/security-and-quality.md) |
| API-001 | P2 | planned | 对齐前端已调用但后端缺失的订单详情和订单列表 GET 接口 | 前端订单页不再调用不存在接口，所有权测试通过 | [安全与质量计划](plans/security-and-quality.md) |
| FE-001 | P2 | planned | 清理前端缺失背景资源、错误 CSS 注释和大 chunk 构建警告 | `npm run build` 无资源/CSS 警告，并记录 chunk 策略 | [安全与质量计划](plans/security-and-quality.md) |
| DEPLOY-001 | P2 | planned | 规划公网长期部署、域名/CDN、HTTPS、Secret 管理和 OSS 生产访问模式 | 可重复部署文档、环境隔离、健康检查和公网安全验收完成 | [运行与外部依赖计划](plans/runtime-and-external-dependencies.md) |

## 当前分支与阻塞

- 当前分支：`lab4`，比 `origin/lab4` 领先 1 个提交；本轮未创建开发分支。
- 2026-08-10 项目所有者确认增加开发 SOP：固定需求记录、Git 边界、TDD、验证、独立审查、授权和归档等检查步骤，同时保留事务、锁、缓存和代码结构等具体技术选择空间。BACKLOG 新增“当前开发批次”，用于直接显示当前阶段、已完成、尚未完成、阻塞和下一步。
- 2026-08-10 项目所有者授权开始最终独立复核。复核由不继承此前实现过程的新 subagent 只读执行，不读取 `.env`、不访问真实外部服务、不修改文件；主开发代理必须复核其证据，不能把 subagent 的结论直接当作合并授权。
- 2026-08-10 最终独立复核发现并由主开发代理接受三个问题：SEC-007（公开注册仍接受客户端账户 ID，可能覆盖已有账户，阻止进入 Git 授权）、OSS-006（上传结果不确定时没有明确提示验证对象可能残留）和 DOC-002（运行计划仍把已修复的 Compose 数据库端口覆盖写成当前缺陷）。按安全影响从高到低执行 TDD 修复，修复后必须重新验证并由新的独立复核确认。
- 2026-08-10 最终审查整改证据：SEC-007 的真实 MySQL 接口红测先证明预置账户被攻击者注册请求覆盖，专用注册 DTO 和服务层清空 ID 后目标测试 1/1、相关角色安全测试合计 4/4 通过，测试事务回滚；OSS-006 红测先证明上传异常消息没有提示可能残留，修复后生命周期测试 5/5 通过；DOC-002 已与 Compose 和配置测试对齐。`mvn compile` 成功，使用假的 OSS 值并关闭真实探针的完整后端测试 51/51 通过，`npm run build` 成功，Compose 配置检查通过。等待针对性独立复核。
- 2026-08-10 针对性独立复核确认 SEC-007、OSS-006 和 DOC-002 的目标修复成立，但新增 OSS-007：上传成功后若匿名读取验证失败，删除调用成功并不能证明对象已经不存在；当前代码没有执行删除后 404 验证，却错误报告 `residuePossible=false`。该问题按 P2 登记并继续执行 Red → Green → Refactor，修复后必须再次独立复核。
- 2026-08-10 完整测试排障期间只读执行 `docker compose --env-file back_end/.env ps --format json`，确认磁盘上的安全绑定尚未应用到正在运行的 Redis 旧容器：当前发布地址仍为 `0.0.0.0:6379`。该运行时风险登记为 SEC-008；更新容器时不得删除数据卷，完成后必须检查实际发布地址而不只检查 YAML。
- 2026-08-10 OSS-007 验证证据：新增两个行为断言后，生命周期测试 6 项中 2 项准确因 `residuePossible=false` 失败；最小修改两个匿名读取失败分支后，目标测试 6/6 通过。首次完整测试因沿用非本机数据库地址而出现 `No database selected`，不计为通过证据；按 AGENTS.md 使用宿主机 MySQL `127.0.0.1:3307` 后，真实数据库注册测试 1/1、完整后端测试 52/52 通过，假的 OSS 参数和关闭真实探针确保没有访问真实 OSS。
- 2026-08-10 SEC-008 运行修复证据：只重新创建 Redis 服务容器，未删除命名数据卷，重启前后 `DBSIZE` 都为 0；容器恢复 healthy 后实际发布地址为 `127.0.0.1:6379`，`redis-cli PING` 和 `Test-ComposeConfiguration.ps1` 均通过，MySQL 未被重启。
- 2026-08-10 最终冷启动只读复核结论：OSS-007 与 SEC-008 没有发现阻塞项或新的 P0/P1/P2/P3 回归。复核确认上传未确认时不会盲删、匿名读取失败时的两个状态准确、失败消息不泄露对象或云端信息；Redis 配置测试要求唯一且仅本机绑定，运行证据明确区别静态 YAML 与实际容器发布地址。复核独立汇总默认 Surefire 报告为 14 个测试类、52 项全部通过，两个历史 `*IT` 不计入默认套件。
- 2026-08-10 风险整理决定：新增 TEST-002，单独解决 Maven 测试进程的内存与隔离问题；新增 JPA-001，在接口测试保护下关闭 Open Session in View；扩展 CACHE-001，包含 RedisTemplate 类型安全和关闭未使用的 Redis Repository 扫描；由于当前目标是本机运行和面试演示，RUN-002 从 P1 调整为 P2，但仍保持 blocked，不能误报为完成。阿里云权限可能被人工修改或策略变化的问题不单独创建工作项，改为在每次演示前运行 OSS 生命周期检查和业务图片删除权限检查，并在未来 DEPLOY-001 中设计定期检查。
- 项目所有者新增沟通铁律：所有阶段更新、方案说明、审查和最终回复不得过度压缩；必须优先使用常见语言，首次出现的专业术语要解释其含义、项目实例、影响和选择理由，禁止只使用自造短语、少见术语、缩写或工作项编号要求用户决策。该要求已写入根目录 `AGENTS.md`。
- 项目所有者已经选择 OSS-003 的方案 A：本机和面试阶段接受可能出现少量未被业务使用的云端图片，自动清理作为长期 P2；不扩大应用账户对商品、广告和头像图片的删除权限。项目所有者同时确认继续实现日志秘密遮挡，并授权使用随机生成、从未上传的文件名验证阿里云拒绝删除业务图片。当前按顺序处理 SEC-002 和 OSS-004。
- OSS 第一轮评审未批准整体合并。项目所有者已授权按 SEC-005 → IMG-001 → RUN-002 → OSS-003 → SEC-002/OSS-004 的顺序进行 TDD 整改；整改完成后必须由不继承本会话实现上下文的冷启动 subagent 进行第二轮独立审查。
- `RUN-002` 阻塞原因：Docker Hub 拉取 Temurin 17 镜像持续超时，ECR 检查触发限流，四容器健康检查、前端代理和数据卷重启行为尚未完成一次完整验收。此前 Compose 后端只覆盖 `DB_HOST=db`、可能继承宿主机 `DB_PORT=3307` 的缺陷已经修复；当前 Compose 明确使用 `db:3306`，配置测试会阻止回退。源码已在本机 Java 17 下编译、测试和启动。
- `OSS-001` 当前进展：专用 RAM 用户未开启控制台登录，应用已完成 SEC-002、SEC-003、OSS-002 的代码和测试改造。在真实运行实例中验证未登录上传业务码 401、普通用户上传商品图业务码 403、头像 PutObject 业务码 200；本地探针账号已清理。最小 Bucket Policy 配置后，验证对象匿名 HTTPS GET 为 200、类型 `image/png`、大小 18,805 字节；匿名 ListObject 和带禁止覆盖保护的匿名 PutObject 均为 403，之后对象仍为 18,805 字节。项目所有者删除验证对象后复验 GET 为 404，外部闭环完成。
- 项目所有者选择将后续清理自动化。下一阶段只为 `tomatomall/images/_validation/*` 增加 `oss:DeleteObject`，实现可重复探针在 `finally` 中清理并验证删除后 404；不得把删除权扩大到 avatar、product、advertisement 或整个 images 前缀。
- 自动化阶段代码进展：受限删除红测先因方法缺失而失败，探针编排红测先因类缺失而失败；实现后删除边界 11/11、探针编排 4/4 通过。显式入口为 `back_end/scripts/Invoke-OssLifecycleProbe.ps1`，真实 `OssLifecycleProbeIT` 不匹配 Surefire 默认命名规则且还要求 `RUN_REAL_OSS_PROBE=true`，默认测试不会访问 OSS。云端 `_validation` DeleteObject 已由真实生命周期闭环验证。
- 真实自动探针已完成 Put→匿名 GET 200→Delete→GET 404，对象没有残留；但探针直接运行 JUnit、未启动 Spring，导致 `application.yml` 的 OFF 日志级别未生效，Apache HTTP DEBUG 输出了 Authorization 凭据材料。SEC-002 因此重新阻塞 OSS-001；必须停用并轮换本次 AccessKey，增加测试运行时日志配置与红测后再使用新凭据复验。
- SEC-002 处置与复验证据：新增原生 Surefire 日志红测，确认修复前 headers/wire 有效级别为 DEBUG 且伪凭据会被记录；增加 `src/test/resources/logback-test.xml` 后 Spring/非 Spring 两类日志测试 2/2 通过，使用不可用 OSS 占位值的默认完整测试 30/30 通过。项目所有者已停用旧 AccessKey、创建新 Key 并更新本地 `.env`；新凭据真实探针 1/1 通过，输出未出现 Authorization、签名、AccessKey、Endpoint、Bucket 或对象名，对象无残留。当前已解除阻塞并进入待评审/待合并。
- 2026-08-09 新发现日志缺陷：业务图片删除权限真实验证虽然确认 avatar、product、advertisement 三个目录均被阿里云拒绝，但 `com.aliyun.oss` 在 WARN 级别输出了 Bucket 域名、请求编号、RAM 账户内部编号和编码后的诊断信息。未发现 AccessKey ID、AccessKey Secret 或请求签名，现有凭据无需因本次输出轮换；该云端诊断输出仍按 SEC-002 缺陷处理，修复后必须重新运行真实验证并确认控制台只保留 Maven 测试结果。
- SEC-002/OSS-004 修复证据：日志正文、异常消息、危险日志类别和云端诊断字段测试 9/9 通过；图片服务与权限验证本地测试 12/12 通过。第二次真实权限验证 1/1 通过，阿里云拒绝 avatar、product、advertisement 三个目录中随机生成且已确认不存在的文件名删除请求；控制台只保留 Maven 和测试结果，没有再次输出云账户诊断详情，也没有创建或删除任何真实图片。
- 在增加“直接加载正式日志配置”测试之前，完整后端验证共有 48 项：第一次因没有读取本机配置而使用仅适用于 Compose 网络的数据库主机名 `db`，得到 47 个通过、Spring 上下文 1 个连接错误；随后安全读取本机 `.env` 的数据库/Redis连接值，同时用假的 OSS 值覆盖真实云端配置并关闭两个真实 OSS 验证开关，48/48 通过。这是早期阶段证据，不是当前最终数量。
- 前端生产构建再次成功；仍有 FE-001 已登记的背景图片路径、CSS `//` 注释和主文件超过 Vite 默认大小提示，这些 P2 问题不阻塞本轮 OSS 安全整改。
- 第二轮独立审查发现 1 个建议阻止合并的问题和 3 个较小问题：Compose 的无密码 Redis 对宿主机所有网络接口开放；旧生命周期验证在上传失败后仍无条件删除验证文件名；BACKLOG 混用历史测试数量；一份冷层文档状态不符合约定。已登记 SEC-006、OSS-005、DOC-001，并按安全影响从高到低修复。
- 2026-08-10 修复后独立复核确认生命周期、测试数量和冷层状态修复有效，Redis 当前配置也只绑定本机；但发现 Redis 配置测试使用 `Select-Object -First 1`，只检查第一条映射，无法阻止以后追加第二条公网映射。SEC-006 保持进行中，增加“恰好一条映射”检查和模拟回退验证后再复核。
- 2026-08-10 最终补强：Redis 配置测试改为要求恰好一条端口映射。临时在安全映射之外追加 `6380:6379` 时测试准确失败，移除临时映射并恢复安全配置后测试与 `docker compose config --quiet` 均通过；该临时映射从未用于启动容器。OSS 生命周期测试增加失败消息不包含随机对象名的直接断言，目标测试 5/5、当时完整后端测试 50/50 通过。
- 新构建后端已使用轮换后的本机配置在 8080 启动，`GET /api/products` 直连和经 5173 Vite 代理均返回 HTTP 200；启动日志未发现数据库/Redis 连接失败或 OSS 敏感日志标记。启动入口已固化为 `back_end/scripts/Start-LocalBackend.ps1`，脚本不回显环境变量值。
- 默认完整测试曾发现 Spring Boot 会在测试构建日志打印临时生成的开发安全口令，已登记 SEC-004 并按 Red→Green 修复；该修复当时的目标测试 2/2、完整测试 31/31 通过且提示消失。该值并非项目 `.env` 或外部服务凭据，但仍按敏感日志处理；31/31 是历史阶段数量。
- 早期 OSS 阶段检查曾得到：`mvn compile` 成功、真实 OSS 生命周期验证 1/1、当时默认 Maven 测试 31/31、`npm run build` 成功。SEC-007、OSS-006 和 DOC-002 修复后曾为 51/51；当前加入 OSS-007 回归测试后的完整后端证据为 52/52。前端仍保留 FE-001 已登记的背景资源、CSS 注释和大文件提示。
- 第二轮审查修复后的验证：Compose 配置检查通过，Redis 只绑定 `127.0.0.1:6379`；生命周期流程测试 5/5、相关工具测试 16/16、真实生命周期验证 1/1 通过；完整后端测试使用本机 MySQL/Redis和假的 OSS 值执行，50/50 通过，0 失败、0 错误、0 跳过。
- 真实验证还确认 `LoginInterceptor` 只读取 Cookie，而部分控制器和 Axios 同时支持 `token` 请求头；仅带请求头的 API 客户端会被拦截器拒绝。该认证来源分裂已并入 SEC-001，不在 OSS-001 中扩大重构。

AI assistant 已由项目所有者决定废弃，因此不再作为活跃工作项、运行阻塞或验收目标；决策记录见 [assistant 废弃决策](archive/2026-08-09-assistant-deprecation-decision.md)。

当前交付目标仅为本机运行和面试演示；公网长期部署已明确延后为 DEPLOY-001，不阻塞 P0 交易链路与 OSS 本机演示改造。
