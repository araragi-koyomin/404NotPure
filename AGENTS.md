# 404NotPure 项目协作指南

## 项目定位与技术栈

本仓库是南京大学软件工程课程项目 TomatoMall（404NotPure），实现在线图书商城。活跃主链路包括账户、商品、规格与详情图、广告位、购物车、库存、订单、支付宝网页支付、评论、OSS 图片上传和 Redis 缓存。仓库仍保留简单 LLM 聊天接口，但该功能已被产品决策废弃，不属于维护范围。

- 后端：Java 17、Spring Boot 2.7.6、Spring Web、Spring Data JPA、Hibernate、Flyway、Spring Security、Bean Validation、MySQL Connector/J 8.0.31、Spring Data Redis、支付宝 Java SDK、阿里云 OSS SDK；构建中仍有废弃功能遗留的 Volcengine Ark SDK。
- 前端：Vue 3、TypeScript、Vite 5、Element Plus、Axios、Vue Router、ECharts。
- 数据与运行依赖：MySQL 8、Redis 6；`docker-compose.yml` 还可启动前端和后端容器。
- API 响应保持 `Response<T>` 包装，字段为 `code`、`msg`、`data`；成功业务码当前为字符串 `"200"`。

## 沟通与解释铁律

无论处于项目检查、需求分析、方案设计、代码开发、测试验证、问题审查还是最终汇报阶段，都不得为了追求简短而过度压缩对用户的说明。阶段更新和最终回复必须提供足够的背景、判断依据、影响范围、当前状态和下一步，让没有相关工程经验的用户也能据此理解问题并作出决定。

- 优先使用日常、常见、直接的中文表达。能够说“上传成功但商品保存失败后，云端留下了一张没有被任何商品使用的图片”，就不要只说“产生孤儿对象”。
- 严禁自造词语、自造缩写，或用小众术语替代本可直接说明的事实。不得只抛出 `SEC-002`、`RAM 负测`、`兜底脱敏` 等编号或短语而不解释实际含义。
- 专业术语、英文缩写和项目编号第一次出现时，必须立即解释：它是什么、在本项目中对应哪段行为、为什么重要、出问题会造成什么后果。适合时给出一个与当前项目直接相关的例子。
- 当需要用户选择方案时，必须分别说明每个方案会修改什么、不会修改什么、优点、缺点、风险、工作量和推荐理由。不能让用户在不理解术语的情况下确认方案。
- 工作过程中的阶段更新不能只报告“Red/Green”“已阻塞”“已闭环”等状态词；必须说明具体哪个测试为什么失败、代码做了什么改变、还缺少什么证据。TDD 的 Red 和 Green 可以继续使用，但首次出现时必须解释为“先写能复现缺陷的失败测试”和“实现修复后测试通过”。
- 审查报告必须把结论翻译成用户能验证的具体行为。例如不要只说“权限边界不完整”，而要说明“普通用户能够通过哪个请求获得什么本不应拥有的能力”。
- 如果用户表示没有看懂，必须停止推进依赖该理解的重大方案，从头用更常见的语言重新解释，不得把相同术语换一种排列后再次要求确认。
- 本规则要求公开说明可验证的判断依据和设计取舍，不要求展示不可见的内部推理过程；但最终结论不能省略支撑它的事实、测试或代码证据。

## 仓库结构与主要模块

- `back_end/`：后端 Maven 工程；仓库根目录没有聚合 `pom.xml`。
  - `src/main/java/com/example/tomatomall/controller/`：HTTP 接口。账户、商品、库存、广告、购物车/结算、支付宝支付、评论和图片入口均在此处；其中 assistant 入口是废弃遗留代码。
  - `service/`：服务接口；`service/serviceImpl/`：业务实现。注意现有支付接口名为 `service.PaymentServiceImpl`，实现类名为 `service.serviceImpl.PaymentService`，命名与通常约定相反。
  - `repository/`：Spring Data JPA Repository。
  - `po/`：JPA 实体；订单实体为 `Orders`/`OrderItem`，库存实体为 `StockPile`。
  - `dto/`、`vo/`：请求 DTO、缓存 DTO 和响应 VO。
  - `configure/`、`util/`：Security、MVC 拦截器、Redis 序列化、JWT、CORS 等基础设施。
  - `src/main/resources/application.yml`：运行配置，使用环境变量注入外部服务参数；Flyway 先执行版本化迁移，JPA 当前使用 `ddl-auto: validate` 检查实体与数据库结构。
  - `.env.example`：不含真实凭据的本机配置模板；真实配置写入被 Git 忽略的 `.env`，逐项说明见 `docs/guides/local-environment.md`。
  - `src/test/`：包含 Spring 上下文加载测试，以及 OSS 图片安全、订单与库存一致性、Flyway 迁移、支付宝回调和统一错误响应测试；Redis Cache-Aside 核心行为测试仍缺。
- `front_end/`：Vue/Vite 工程。
  - `src/api/`：与后端接口对应的 Axios 封装；订单提交仍调用 `POST /api/cart/checkout`，支付调用 `POST /api/orders/{orderId}/pay`。
  - `src/views/`、`src/components/`：页面和公共组件。
- `docker-compose.yml`：定义 frontend、backend、MySQL 8 和 Redis 6，以及持久化卷。
- `README.md`：环境依赖和基础启动说明。
- `docs/BACKLOG.md`：热层活跃工作索引；只保留未完成项。
- `docs/DEVELOPMENT_SOP.md`：从需求确认、TDD、验证和独立审查到 Git 授权、合并与归档的标准开发流程。
- `docs/plans/`：温层活跃设计、范围、TDD 清单和验收标准。
- `docs/archive/`：冷层已完成、被替代或取消文档；归档不等于删除。

## 文档与三层记忆

除根目录 `AGENTS.md` 外，开发过程中产生的 Markdown 文档必须放在 `docs/`。所有 `docs/**/*.md` 必须以 YAML Frontmatter 开头，至少包含 `title`、`type`、`layer`、`status`、`created`、`updated`、`owners` 和 `tags`；冷层文档还需 `archived_at`。

- 热层：`docs/BACKLOG.md` 是唯一活跃状态索引，只放未完成项。发现新 bug/缺陷、接受或改变需求、创建开发分支、工作项阻塞/解阻或优先级变化时，必须在同一次变更中更新 BACKLOG。
- 温层：活跃计划和设计放在 `docs/plans/` 等非归档目录，详细记录目标、非目标、TDD 顺序、验收标准、风险和开放问题。
- 冷层：完成、替代或取消的文档移动到 `docs/archive/` 并更新 Frontmatter。归档不得删除历史证据。
- FIX/FEAT 合并到项目所有者确认的集成分支后，必须把完成证据写入冷层并从 BACKLOG 移除对应活跃项；不得在 BACKLOG 长期保留已完成勾选项。不能默认集成分支一定是 `main`，必须根据当前仓库历史和项目所有者决定记录目标。
- 文档不得包含密钥、密码、Token、真实支付参数或其他敏感配置值。

文档字段、生命周期和转层细则以 `docs/README.md` 为准；实际开发步骤以 `docs/DEVELOPMENT_SOP.md` 为准。

## 开发流程检查步骤

后续开发必须遵守 `docs/DEVELOPMENT_SOP.md`，核心顺序为：

1. 检查 Git 状态和项目指令；
2. 明确目标、非目标、兼容要求和完成证据；
3. 更新 BACKLOG 的工作项和“当前开发批次”，再更新对应温层计划；
4. 确认起始分支、开发分支和目标集成分支；创建分支时同步更新 BACKLOG；
5. 代码改动严格执行 Red → Green → Refactor；
6. 按改动范围执行编译、测试、真实 MySQL/Redis 集成测试、前端构建、Compose 检查或外部服务检查；
7. 主开发代理自查后，由不了解实现过程的新 subagent 对交易、权限、外部服务、数据库迁移和大型改动进行只读独立审查；
8. 审查问题修复后必须重新验证并进行针对性复核，不能沿用修复前的审查结论；
9. 完整报告结果并等待项目所有者明确授权后，才能暂存、提交、推送或合并；
10. 合并成功后归档证据、从 BACKLOG 移除完成项并切换当前开发批次。

纯文档改动可使用简化流程，不强制运行 Maven 或前端构建，但仍要检查 Git 状态、BACKLOG、Frontmatter、内部链接、行尾空白和 `git diff --check`。真实 OSS、支付宝或其他外部服务验证必须单独获得授权，默认测试不得访问真实服务。

## 本地启动、构建与测试

所有 Maven 命令必须在 `back_end/` 中执行：

```powershell
cd back_end
mvn spring-boot:run
mvn compile
mvn test
mvn clean verify
```

前端命令在 `front_end/` 中执行；有锁文件时优先使用 `npm ci`：

```powershell
cd front_end
npm ci
npm run dev
npm run build
```

需要完整依赖环境时可在仓库根目录执行：

```powershell
docker compose --env-file back_end/.env up --build
```

Compose 对外端口当前为前端 `5173`、后端 `8080`、MySQL `3307`；Redis 为了支持宿主机直接运行后端而映射到 `127.0.0.1:6379`，只允许本机访问，不得改回绑定所有网络接口的 `6379:6379`。Compose 插值必须通过 `--env-file back_end/.env` 读取数据库变量。运行集成测试前先确认 MySQL 数据库已创建且 Redis 可连接。构建和测试会生成 `target/`、`dist/` 等忽略产物，不要把这些文件加入 Git。

## 外部依赖与配置

- MySQL：应用数据库名、主机、端口、用户和密码由 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD` 提供。代码按 MySQL 8 运行，结构先由 Flyway 迁移、再由 Hibernate 验证；本机后端连接 Compose 数据库时端口为 3307。`FLYWAY_BASELINE_ON_MIGRATE` 默认必须为 `false`，只能在人工确认旧库与 V1 一致后临时设为 `true`，完成基线后立即恢复关闭。
- Redis：由 `REDIS_HOST`、`REDIS_PORT` 提供，默认 Redis 端口为 6379。Compose 内后端使用 `redis:6379`，宿主机后端使用 `127.0.0.1:6379`；当前 Redis 没有密码，因此宿主机端口必须只绑定本机回环地址。`RedisConfig` 使用字符串 key 和 `GenericJackson2JsonRedisSerializer` 序列化对象。
- 支付宝：需要 `ALIPAY_APP_ID`、`ALIPAY_SELLER_ID`、`ALIPAY_APP_PRIVATE_KEY`、`ALIPAY_ALIPAY_PUBLIC_KEY`、`ALIPAY_NOTIFY_URL`、`ALIPAY_SERVER_URL`、`ALIPAY_RETURN_URL`、`FRONTEND_URL`。签名算法配置为 RSA2，字符集为 UTF-8；回调验签后还必须核对应用 ID 和收款方 PID。本地异步通知通常还需要可公开访问的回调地址。
- 阿里云 OSS：需要 `ALIYUN_OSS_ENDPOINT`、`ALIYUN_OSS_ACCESS_KEY_ID`、`ALIYUN_OSS_ACCESS_KEY_SECRET`、`ALIYUN_OSS_BUCKET_NAME`。
- AI assistant：已废弃，不是启动、验收、测试或维护范围，不要求配置 `ARK_API_KEY`。不得扩展、修复或为其补测试；如需物理删除接口、前端入口或 Ark 依赖，必须作为独立清理任务评估兼容影响。

配置变量的来源、格式、本机与 Compose 地址差异统一维护在 `docs/guides/local-environment.md`。新增或修改配置变量时，必须同步更新该指南和 `back_end/.env.example`。不得在日志、测试输出、提交说明或文档中回显上述配置值。

## 订单与库存领域不变量

以下约束是后续交易代码必须维持的领域不变量；其中一部分是当前代码的整改目标，而不是对现状能力的声明：

1. 每个订单必须属于一个已存在账户，至少包含一个有效商品项，支付方式非空。
2. 每个订单项的 `productId` 必须存在，购买数量必须是非空正整数。同一请求出现重复商品时，应先聚合数量或以等价方式校验总需求，不能绕过库存检查。
3. 订单总额必须由服务端按商品价格和购买数量计算，客户端金额不得作为可信来源；金额计算使用 `BigDecimal`，支付比较使用数值等值而不是字符串等值。
4. `StockPile.amount` 表示可用库存，`StockPile.frozen` 表示已下单但尚未完成结算的冻结库存；二者均不得为负数。
5. 创建待支付订单时，对每个商品执行 `amount -= quantity`、`frozen += quantity`；所有库存变更和订单/订单项落库必须处于同一事务中，任一步失败时全部回滚。
6. 多个并发订单争用同一商品时，成功冻结的总量不得超过事务开始前可用库存。实现应使用数据库悲观锁、乐观锁或带库存条件的原子更新之一，并用并发测试证明。
7. 支付成功时只释放对应订单已冻结的库存，即 `frozen -= quantity`；可用库存不再次扣减。重复支付通知不能重复释放冻结库存。
8. 库存调整接口不能制造负库存，也不能把可用库存设置为与已冻结业务含义冲突的值。商品与库存记录应保持一一对应；当前数据库映射尚未对 `StockPile.productId` 声明唯一约束，修改时需谨慎迁移已有数据。
9. 不得在事务提交前向外部系统声明订单成功。涉及缓存失效时，应在数据库事务成功后处理，或采用能够避免缓存长期脏读的等价方案。

当前 `OrderServiceImpl.addOrder` 已通过 PR #2 合并到 `master`：它建立事务边界，并使用 MySQL 条件原子更新冻结库存；真实 MySQL 测试覆盖事务回滚和 16 线程争抢 1 件库存。该结论只适用于下单时的库存冻结，不能延伸为支付回调幂等、请求级幂等或完整订单生命周期已经解决。库存表的商品唯一约束仍由 DB-001 跟踪。

## 订单状态与支付流转

当前代码实际使用的订单状态只有：

- `PENDING`：订单已创建且库存已冻结，等待支付。
- `PAID`：支付宝异步通知处理成功，冻结库存已释放。

合法状态流转当前仅为 `PENDING -> PAID`。后续代码必须遵守：

- 创建订单只能产生 `PENDING`。
- 发起支付宝支付只允许 `PENDING` 订单。
- 有效的支付回调必须先通过支付宝签名校验，并校验 `out_trade_no` 可解析且对应真实订单、通知金额与订单 `totalAmount` 数值一致、交易状态为项目支持的成功状态。
- `PENDING -> PAID` 与冻结库存释放必须在同一事务中完成。
- 对已经 `PAID` 的同一订单重复通知应幂等成功，且不能再次修改库存；其他非法源状态不得直接变为 `PAID`。
- 当前没有取消、关闭、退款或超时解冻流程。新增状态前必须同时定义来源状态、目标状态、库存动作、幂等语义和测试，不能只增加字符串常量。
- `Orders.createTime` 只表示创建时间，不得覆盖为支付时间。当前实体已通过 V2 迁移增加独立 `paidTime` 和唯一 `alipayTradeNo`；支付完成时间作为向后兼容的新响应字段，支付宝交易号不在订单 VO 中公开。

## Redis Cache-Aside 约定

当前 Redis 使用范围仅是“广告位关联商品详情缓存”：

- 现有 key：`advertisement:product:{productId}`。
- 写入：创建或更新广告时，将关联商品转换为 `ProductDTO` 后写入。
- 读取：`ProductServiceImpl.getProductById` 先读取该 key，命中后转换为 `ProductVO`。
- TTL：随机 1800～3599 秒，用于打散同时过期时间。
- 删除：删除广告时删除其关联商品 key。

后续兼容改造规则：

1. key 必须集中定义，保留稳定前缀并避免在多个服务中手写不同格式；若迁移到通用商品详情 key，应提供兼容读取或统一切换所有读写方。
2. 使用 Cache-Aside：读缓存未命中时查询数据库、转换 DTO、按随机 TTL 回填，再返回；数据库不存在时可短时缓存明确的空值标记以防穿透，但不能把空标记当成商品对象反序列化。
3. 商品更新或删除成功后必须失效对应详情缓存。广告更新若更换关联商品，应同时失效旧商品 key，并正确处理新商品缓存。
4. 数据库写入失败时不得提前删除/写入造成误导性状态；事务内数据修改应在提交后执行缓存失效，或采用一致性效果等价且有测试的方案。
5. 保留 TTL 随机化，不使用永久缓存。测试不应依赖随机 TTL 的精确秒数，只断言其位于约定范围。
6. 不能声称缓存带来具体性能提升，除非有可复现的测量结果。

现状缺口：商品详情缓存未命中不会回填；商品更新/删除不会失效；广告切换商品时不会删除旧 key；没有空值缓存；缓存 key 的广告语义与商品详情读取职责耦合。

## 认证与权限注意事项

- JWT 存在名为 `token` 的 HttpOnly Cookie 中；过期时间与 Cookie 最大存活时间当前不一致。不要在日志或响应之外额外打印 token。
- Spring Security 当前配置为所有请求 `permitAll` 且关闭 CSRF，真正鉴权主要依赖 MVC `LoginInterceptor` 和控制器中的 `TokenUtil` 调用。
- `LoginInterceptor` 当前直接放行 `/api/products`、`/api/orders`、`/api/cart`、`/api/assistant/chat` 等前缀；部分接口随后在控制器中手工取 token，但不能假设所有放行接口都安全。
- 商品、库存和广告写接口调用 `validateAdminRole`；修改这些控制器时必须保留管理员校验。
- 购物车查询、添加和结算会解析当前用户；当前购物车更新/删除只验证存在登录 token，没有校验条目所有权。支付表单接口也未校验订单所有权。这些属于已知越权风险。
- JWT 签名密钥当前硬编码在源码中，是必须迁移到环境变量/密钥管理的技术债；不得在文档、日志或测试中复制其值。

## 测试约定

- 后端使用 JUnit 5（由 `spring-boot-starter-test` 提供），测试放在 `back_end/src/test/java/com/example/tomatomall/` 下，并按被测类或行为命名。
- 纯业务分支优先写不依赖网络和真实密钥的单元测试；Repository 锁/原子更新、事务回滚和并发库存必须另有数据库集成测试证明。
- 订单改造最低覆盖：正常下单、库存不足、事务中途异常回滚、并发不能超卖、零数/负数/空数量。
- 支付改造最低覆盖：签名失败、订单号无效、金额不一致、重复通知、非法状态、成功支付只释放一次冻结库存。
- 缓存改造最低覆盖：命中不查库、未命中查库并回填、空值保护、商品更新/删除失效、广告切换商品时旧 key 失效、TTL 范围。
- 测试不能调用真实支付宝或 OSS 服务。支付宝签名验证应封装出可替换边界，业务服务测试直接传入已验证的通知数据。废弃的 assistant 不新增维护性测试。
- 真实 OSS 只能通过显式外部探针执行：`OssLifecycleProbeIT` 必须设置 `RUN_REAL_OSS_PROBE=true`，`OssBusinessDeletePermissionIT` 必须设置 `RUN_REAL_OSS_PERMISSION_PROBE=true`。两者的 `*IT` 命名都不进入默认 Surefire 发现范围，只能由对应脚本或 `-Dtest=...` 明确选择；生命周期探针只能使用 `_validation` 隔离前缀并在 `finally` 中清理，权限探针只能针对预先确认不存在的随机业务对象名验证拒绝结果。它们都不能作为默认单元测试运行。
- 需要 MySQL/Redis 的测试应明确标注并使用隔离数据；若本机依赖不可用，必须报告未执行项、阻塞原因和替代验证。不要让测试读取开发者真实 `.env`。
- 完成交付前至少执行 `mvn compile`、`mvn test`、适用的 MySQL/Redis 集成测试以及仓库根目录的 `git diff --check`。前端契约发生变化时还需执行 `npm run build`。

代码开发严格遵循 TDD 的 Red → Green → Refactor：先写能因目标缺陷而失败的测试并确认失败原因，再写最小实现，最后在持续绿灯下重构。禁止测试剧场：不得用只验证 Mock 调用次数的测试替代结果/状态验证，不得复制生产实现到测试，不得用无意义断言或跳过真实事务、并发、金额精度和缓存失效语义。数据库锁、条件更新、事务回滚和并发库存必须由真实 MySQL 集成测试证明；Redis TTL、序列化和失效必须由真实 Redis 集成测试证明。

## 敏感信息与 Git 纪律

- 不提交 `.env`、真实数据库凭据、JWT 密钥、支付宝应用私钥/公钥配置、OSS AccessKey、废弃功能遗留的 LLM API Key、支付回调隧道凭据或生产 URL。
- `application.yml` 已被 Git 跟踪，只允许保留环境变量引用和无秘密的默认值；新增变量时同步维护不含真实值的示例说明。
- 不提交 `target/`、`dist/`、`node_modules/`、日志、IDE 文件或本地数据库/Redis 数据。
- 修改前先执行 `git status --short --branch`，保留用户已有修改和未跟踪文件。未经用户明确要求，不提交、不推送，不执行 `git reset`、`git checkout --` 或删除用户文件。

## 当前已知技术债务

- 下单事务边界、正数校验、重复商品汇总和条件原子更新已由 ORD-001 实现并通过真实 MySQL 测试；但结算请求没有幂等键，重复提交仍可能创建不同订单，该问题由 ORD-002 跟踪。库存表尚无商品唯一约束，仍需 DB-001 通过版本化迁移补齐。
- PAY-001 当前分支已实现验签后的订单号和金额校验、`PENDING -> PAID` 条件更新、同一交易号重复通知幂等、并发通知单次库存释放、独立支付时间和交易号唯一性；只有合并并完成最终审查后才能作为主分支能力声明。遗留 `PaymentInfo` 仍位于 `dto/` 且没有 Repository/业务接入，后续应独立清理。
- 订单状态在数据库和实体中仍使用字符串，支付与下单服务已通过 `OrderStatus` 枚举集中使用 `PENDING`/`PAID`，但数据库没有状态约束，也没有取消、超时解冻、退款或支付失败处理。
- Redis 缓存职责、回填和失效策略不完整，详见上文；Redis 操作使用原始类型 `RedisTemplate`，存在未检查类型转换，应用启动还会扫描项目没有使用的 Redis Repository。这些问题统一由 CACHE-001 跟踪。
- DB-001A 当前分支已建立 Flyway V1 完整基线和 V2 订单支付字段，并将 JPA 切换为 `ddl-auto: validate`；库存商品唯一约束和历史重复库存处理仍由 DB-001 跟踪。后续实体结构变化必须增加新迁移版本，不能修改已应用的 V1/V2。
- JPA 当前依赖 Spring Boot 默认开启的 Open Session in View；部分实体关联为延迟加载，可能把数据库查询推迟到控制器或响应生成阶段。JPA-001 要求先用接口测试明确数据读取边界，再关闭该选项。
- 认证授权边界分散且存在大范围放行；购物车条目、订单支付等资源所有权校验不完整；CSRF 当前关闭。
- 测试已覆盖 OSS 图片上传安全边界、ORD-001 下单事务与真实 MySQL 并发库存，以及 PAY-001 的真实 MySQL 迁移、通知归属、金额、状态、交易号、确定性并发重复通知和多商品回滚。支付宝沙箱只读查询探针必须显式设置 `RUN_REAL_ALIPAY_PROBE=true`，默认测试不会访问支付宝；探针只允许 HTTPS 的 `alipaydev.com` 官方沙箱网关，只接受随机订单返回 `40004 / ACQ.TRADE_NOT_EXIST`，且不能替代公网异步通知。一次性 Maven 3.9.9/Java 17 容器中默认 Surefire 独立 JVM 已在当前最终代码上连续两次完成 104 项测试，TEST-002 的技术标准已达到并等待随分支合并归档；宿主机当前没有 Maven。Redis Cache-Aside 行为测试仍缺。
- 前端 `order.ts` 声明了获取订单详情/列表接口，但后端当前没有对应 GET 订单接口；不要误认为这两条链路已经实现。
- AI assistant 已废弃；现存控制器、服务、前端入口和 Ark SDK 均视为遗留代码，不投入维护，不得让其配置或故障阻塞商城主链路。
- 存在重复 Lombok 注解、字段注入、`Optional.get()`、实体/DTO 命名混乱和异常码语义不一致等可维护性问题；优先在改动触及范围内渐进修复，不做无关大重写。

## 后续修改的兼容性要求

- 保持 Java 17、Spring Boot 2.7.6 和 `javax.persistence` 兼容；升级 Spring Boot 3/Jakarta 前必须单独评估实体、Security 配置和第三方 SDK 迁移。
- 保持现有路由、HTTP 方法、请求字段以及 `Response<T>` 包装，尤其是 `/api/cart/checkout`、`/api/orders/{orderId}/pay` 和 `/api/orders/notify`，除非前后端在同一变更中完成兼容迁移。
- 保持 `OrderRequest.items[].productId/amount`、订单 `orderId/totalAmount/paymentMethod/createTime/status` 和支付响应字段兼容；新增字段应为向后兼容。
- 不信任客户端价格、用户 ID、订单状态或支付结果；服务端从认证信息、数据库和已验签通知派生。
- 交易一致性优先依赖 MySQL 事务和明确锁/条件更新，不引入微服务、消息队列或复杂分布式架构。
- AI assistant 已废弃：不扩展、不修复、不补测试，也不把其外部配置纳入运行验收；不大规模重写前端，不虚构并发或性能数据。
- 任何“防超卖”“支付幂等”“缓存一致性”结论都必须同时有实现证据和可重复测试证据。
