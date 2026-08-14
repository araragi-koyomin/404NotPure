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
  - `src/test/`：包含 Spring 上下文加载测试，以及 OSS 图片安全、订单与库存一致性、Flyway 迁移、支付宝回调、统一错误响应和真实 Redis Cache-Aside 行为测试。
- `front_end/`：Vue/Vite 工程。
  - `src/api/`：与后端接口对应的 Axios 封装；订单提交仍调用 `POST /api/cart/checkout`，支付调用 `POST /api/orders/{orderId}/pay`。
  - `src/views/`、`src/components/`：页面和公共组件。
- `docker-compose.yml`：定义 frontend、backend、MySQL 8 和 Redis 6，以及持久化卷。
- `README.md`：环境依赖和基础启动说明。
- `docs/BACKLOG.md`：热层活跃工作索引；只保留未完成项。
- `docs/DEVELOPMENT_SOP.md`：从需求确认、TDD、验证和独立审查到 Git 授权、合并与归档的标准开发流程。
- `docs/plans/`：温层活跃设计、范围、TDD 清单和验收标准。
- `docs/archive/2026-08-11-demo-data-delivery.md`：DATA-001 在独立 `tomatomall_demo` 中生成书籍、库存、规格、本地 SVG、广告和本地演示用户的可重复导入边界及完成证据；演示数据不属于默认测试夹具，也不代表真实业务用户。
- `docs/archive/`：冷层已完成、被替代或取消文档；归档不等于删除。

## 文档与三层记忆

除根目录 `AGENTS.md` 外，开发过程中产生的 Markdown 文档必须放在 `docs/`。所有 `docs/**/*.md` 必须以 YAML Frontmatter 开头，至少包含 `title`、`type`、`layer`、`status`、`created`、`updated`、`owners` 和 `tags`；冷层文档还需 `archived_at`。

- 热层：`docs/BACKLOG.md` 是唯一活跃状态索引，只放未完成项。发现新 bug/缺陷、接受或改变需求、创建开发分支、工作项阻塞/解阻或优先级变化时，必须在同一次变更中更新 BACKLOG。
- 温层：活跃计划和设计放在 `docs/plans/` 等非归档目录，详细记录目标、非目标、TDD 顺序、验收标准、风险和开放问题。
- 冷层：完成、替代或取消的文档移动到 `docs/archive/` 并更新 Frontmatter。归档不得删除历史证据。
- FIX/FEAT 合并到项目所有者确认的集成分支后，必须把完成证据写入冷层并从 BACKLOG 移除对应活跃项；不得在 BACKLOG 长期保留已完成勾选项。不能默认集成分支一定是 `main`，必须根据当前仓库历史和项目所有者决定记录目标。
- `docs/resume-interview-highlights.md` 是持续维护的简历/面试事实来源。每次 FIX/FEAT 合并归档时，必须判断该交付是否新增、增强或否定了其中的亮点；只有已经合并且具有实现和测试证据的能力才能进入“可直接写入简历”区域，计划项和未完成边界必须如实保留。
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

需要在 Docker Desktop 中复现默认测试的内存边界时，使用 TEST-002 的受控脚本；它只允许 1 GiB、1.5 GiB、2 GiB 三个档位，并在容器内运行 Maven 3.9.9/Java 17：

```powershell
cd back_end
.\scripts\Invoke-DefaultTestMemoryAcceptance.ps1 -MemoryLimit 1g -SkipClean
```

`-SkipClean` 对应精确的 `mvn test`；不加该参数对应 `mvn clean test`。脚本读取被 Git 忽略的 `.env` 连接本机 Docker 中的 MySQL/Redis，但会覆盖外部服务参数并关闭真实 OSS/支付宝探针，不得修改脚本使默认测试访问真实外部服务。1 GiB 只是当前 246 项测试在固定本机环境中预先选择并验证的最低候选档位，不是理论最小内存，也不是未来测试集的永久保证。

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
- 支付宝：需要 `ALIPAY_APP_ID`、`ALIPAY_SELLER_ID`、`ALIPAY_APP_PRIVATE_KEY`、`ALIPAY_ALIPAY_PUBLIC_KEY`、`ALIPAY_NOTIFY_URL`、`ALIPAY_SERVER_URL`、`ALIPAY_RETURN_URL`、`FRONTEND_URL`。签名算法配置为 RSA2，字符集为 UTF-8；回调验签后还必须核对应用 ID 和收款方 PID。本地异步通知需要支付宝服务器可访问的 `notify_url`，但个人项目不要求固定公网 IP 或长期部署；PAY-003 只在 SEC-001/PAY-002 完成后使用临时 HTTPS 入口执行一次沙箱端到端验收。
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

当前 `OrderServiceImpl.addOrder` 已通过 PR #2 合并到 `master`：它建立事务边界，并使用 MySQL 条件原子更新冻结库存；真实 MySQL 测试覆盖事务回滚和 16 线程争抢 1 件库存。该结论只适用于下单时的库存冻结，不能延伸为支付回调幂等、请求级幂等或完整订单生命周期已经解决。库存表的商品唯一约束仍由 DB-001B 跟踪。

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

当前 Redis 使用范围是通用商品详情 Cache-Aside，同时保留广告位对热门商品的提前缓存：

- key：`product:detail:v1:{productId}`，由 `ProductDetailCache` 集中生成，商品服务和广告服务不得分别手写不同前缀。
- 读取：`ProductServiceImpl.getProductById` 先在数据库事务外检查缓存；命中 `ProductDTO` 直接返回。Redis 正常但缓存未命中时，CACHE-002 在单个 Java 后端进程内按商品 ID 合并请求：一名负责人调用独立事务加载组件，取得对应商品行的悲观写锁，查询商品、规格和详情图并在释放锁前回填；同商品的其他请求累计等待最多 500 毫秒后重新读取 Redis。不同商品互不共用协调记录。商品更新和删除使用同一商品行锁，防止较早读取的旧详情在更新失效后重新写回；这不改变库存使用条件原子更新的设计。
- 热点请求合并边界：等待超时或线程中断返回统一 HTTP 503；负责人失败会通知本批等待者并清理协调记录，后续请求可以重试。Redis 基础设施故障不参加 CACHE-002，继续使用 CACHE-003 的受限数据库回退。该机制只覆盖单个后端进程；同时运行多个后端实例时，每个实例仍可能各有一名负责人，不得描述成多实例全局只查询一次。
- 商品 TTL：随机 1800～3599 秒，用于避免大量商品在同一秒失效。
- 不存在标记：数据库确认商品不存在时写入 `MissingProductCacheEntry`，TTL 随机 60～119 秒；读取时必须区分商品与不存在标记。
- 不存在行并发：当前 MySQL 8 的 `REPEATABLE READ` 隔离级别下，悲观查询会使用间隙锁协调同 ID 创建，创建提交后再清除不存在标记。以后若改为 `READ COMMITTED` 或更换数据库，必须重新执行缺失查询与创建并发测试，不能假设行为相同。
- 广告预热：创建或更新广告提交后，仍会把关联商品详情提前写入同一通用 key。预热通过独立新事务重新锁定并读取最新商品，不写入广告事务中捕获的旧快照。原有广告热门商品预热是合理设计，本轮只是把缓存职责扩展为通用商品详情。
- 写后处理：商品创建会在提交后清除同 ID 的短期不存在标记；商品更新/删除、广告删除和广告更换关联商品时，只在数据库事务成功提交后处理缓存；事务回滚不得提前删除或写入。
- 类型与故障：缓存值类型错误时删除并回查数据库；Redis 基础设施故障使用 250 毫秒建连、500 毫秒命令、5 秒临时绕过和单恢复检查者。故障期间商品详情最多 4 个请求并发回退 MySQL、等待 50 毫秒，超出后返回统一 HTTP 503。恢复时先重置 Lettuce 共享连接，再用 `SCAN` 分批删除 `product:detail:v1:*`，禁止 `FLUSHDB`；清理成功后才能恢复缓存读取。读取、写入、失效和广告预热共用该状态，警告日志不得输出缓存值、连接配置或敏感信息。
- Redis Repository：项目只使用 `RedisTemplate<String, Object>`，没有 Redis Repository，因此通过 `spring.data.redis.repositories.enabled=false` 关闭无用扫描；JPA Repository 保持正常加载。

真实 Redis 测试必须验证序列化后的对象类型、上述两个 TTL 范围、缓存命中/回填、商品和广告写后的失效、数据库事务回滚时缓存保持不变，以及回填/广告预热与商品更新的真实并发顺序。PERF-001 已在隔离 Compose 环境测量冷/热缓存、热点失效、固定/随机无效 ID、Redis 完全停止和读写并发；CACHE-003 已通过 PR #13 增加有界故障保护，并在固定本机 50 QPS/10 秒 Redis 停止场景完成 501 个请求、P99 约 506.81 毫秒、业务错误与丢弃均为 0，2.879 秒完成恢复。证据见 `docs/archive/2026-08-12-cache-performance-baseline-delivery.md` 和 `docs/archive/2026-08-13-redis-failure-protection-delivery.md`；不得外推为生产容量。CACHE-002 已通过 PR #15 合并：除真实 MySQL/Redis 测试外，还完成三轮关闭、三轮开启功能开关的正式对照，每轮 100 个虚拟用户各请求一次，600/600 正确；开启后每轮 MySQL `SELECT` 从 303 次降为 6 次，行锁等待从 99 次降为 0，P95/P99 三轮中位数从 627.365/644.037 ms 降至 79.705/81.992 ms。这只证明固定本机、300 本演示商品、正常 Redis 和单实例环境的效果，不是生产容量、多实例全局协调或当前版本真实 Redis 停机复合压测证据。Cache-Aside 不是数据库与 Redis 的强一致协议；即使数据库提交成功，如果正常运行时提交后的单次 Redis 删除失败，旧值仍可能继续存在到原 TTL，这一风险不能隐瞒。

## 认证与权限注意事项

- JWT 存在名为 `token` 的 HttpOnly Cookie 中，也兼容精确同名请求头；Cookie 与请求头身份冲突时统一拒绝。Cookie 和 JWT 使用同一可配置有效期，不要在日志或响应之外额外打印 token。
- Spring Security 当前仍配置为所有请求 `permitAll` 且关闭 CSRF；现阶段认证主要由 MVC `LoginInterceptor` 的精确公开路由、统一 `TokenUtil` 解析，以及控制器/服务层资源所有权共同完成。不要误写成已经建立完整 Spring Security 权限平台，SEC-012 仍需单独设计 CSRF token 和兼容例外。
- `LoginInterceptor` 已不再按 `/api/products`、`/api/orders`、`/api/cart` 等前缀整体放行；它按 HTTP 方法和精确路径判断公开接口。废弃 assistant 仅保留原有精确兼容放行，不属于维护范围。
- 商品、库存和广告写接口调用 `validateAdminRole`；修改这些控制器时必须保留管理员校验。
- 购物车查询、添加、更新、删除和结算都从已验证身份取得当前用户；更新/删除还校验数据库条目归属。支付表单只允许订单所有者为自己的 `PENDING` 订单生成，匿名、跨用户和管理员替他人支付会在支付宝 SDK 前被拒绝。
- JWT 签名密钥已迁移到必填环境变量 `JWT_SECRET`；缺少配置时拒绝启动。不得在文档、日志或测试中复制真实值。

## 测试约定

- 后端使用 JUnit 5（由 `spring-boot-starter-test` 提供），测试放在 `back_end/src/test/java/com/example/tomatomall/` 下，并按被测类或行为命名。
- 纯业务分支优先写不依赖网络和真实密钥的单元测试；Repository 锁/原子更新、事务回滚和并发库存必须另有数据库集成测试证明。
- 订单改造最低覆盖：正常下单、库存不足、事务中途异常回滚、并发不能超卖、零数/负数/空数量。
- 支付改造最低覆盖：签名失败、订单号无效、金额不一致、重复通知、非法状态、成功支付只释放一次冻结库存。
- 缓存改造最低覆盖：命中不查库、未命中查库并回填、空值保护、商品更新/删除失效、广告切换商品时旧 key 失效、TTL 范围。
- 测试不能调用真实支付宝或 OSS 服务。支付宝签名验证应封装出可替换边界，业务服务测试直接传入已验证的通知数据。废弃的 assistant 不新增维护性测试。
- 真实 OSS 只能通过显式外部探针执行：`OssLifecycleProbeIT` 必须设置 `RUN_REAL_OSS_PROBE=true`，`OssBusinessDeletePermissionIT` 必须设置 `RUN_REAL_OSS_PERMISSION_PROBE=true`。两者的 `*IT` 命名都不进入默认 Surefire 发现范围，只能由对应脚本或 `-Dtest=...` 明确选择；生命周期探针只能使用 `_validation` 隔离前缀并在 `finally` 中清理，权限探针只能针对预先确认不存在的随机业务对象名验证拒绝结果。它们都不能作为默认单元测试运行。
- 需要 MySQL/Redis 的测试应明确标注并使用隔离数据；若本机依赖不可用，必须报告未执行项、阻塞原因和替代验证。不要让测试读取开发者真实 `.env`。
- CACHE-001/TEST-001 的真实 Redis 测试必须自行创建带随机标识的少量 MySQL 商品和缓存 key，只清理自己创建的数据且禁止 `FLUSHDB`；不得依赖 DATA-001。DATA-001 只允许人工显式写入名称严格等于 `tomatomall_demo` 的独立数据库，密码从运行时 `TOMATOMALL_DEMO_PASSWORD` 读取且不得回显；普通应用启动和默认 Maven 测试不得自动导入。
- 完成交付前至少执行 `mvn compile`、`mvn test`、适用的 MySQL/Redis 集成测试以及仓库根目录的 `git diff --check`。前端契约发生变化时还需执行 `npm run build`。

代码开发严格遵循 TDD 的 Red → Green → Refactor：先写能因目标缺陷而失败的测试并确认失败原因，再写最小实现，最后在持续绿灯下重构。禁止测试剧场：不得用只验证 Mock 调用次数的测试替代结果/状态验证，不得复制生产实现到测试，不得用无意义断言或跳过真实事务、并发、金额精度和缓存失效语义。数据库锁、条件更新、事务回滚和并发库存必须由真实 MySQL 集成测试证明；Redis TTL、序列化和失效必须由真实 Redis 集成测试证明。

## 敏感信息与 Git 纪律

- 不提交 `.env`、真实数据库凭据、JWT 密钥、支付宝应用私钥/公钥配置、OSS AccessKey、废弃功能遗留的 LLM API Key、支付回调隧道凭据或生产 URL。
- `application.yml` 已被 Git 跟踪，只允许保留环境变量引用和无秘密的默认值；新增变量时同步维护不含真实值的示例说明。
- 不提交 `target/`、`dist/`、`node_modules/`、日志、IDE 文件或本地数据库/Redis 数据。
- 修改前先执行 `git status --short --branch`，保留用户已有修改和未跟踪文件。未经用户明确要求，不提交、不推送，不执行 `git reset`、`git checkout --` 或删除用户文件。

## 当前已知技术债务

- 下单事务边界、正数校验、重复商品汇总和条件原子更新已由 ORD-001 实现并通过真实 MySQL 测试；但结算请求没有幂等键，重复提交仍可能创建不同订单，该问题由 ORD-002 跟踪。库存表尚无商品唯一约束，仍需 DB-001B 通过版本化迁移补齐；历史重复库存默认应阻止迁移并要求人工判断，不能擅自合并数量。
- PAY-001 已通过 PR #3 合并到 `master`：回调完成验签后的订单号和金额校验、`PENDING -> PAID` 条件更新、同一交易号重复通知幂等、并发通知单次库存释放、独立支付时间和交易号唯一性。遗留 `PaymentInfo` 仍位于 `dto/` 且没有 Repository/业务接入，后续由独立的 DB-001C 在确认无引用和无数据后清理。
- PAY-003 是一次性沙箱外部验收，不是生产部署任务：使用临时 HTTPS `notify_url` 完成沙箱买家虚拟付款、真实异步通知和本地订单/库存闭环后立即关闭公网入口；不保存账号、订单号、交易号、签名或隧道凭据，也不把它扩展为退款、清结算或多渠道支付。
- 订单状态在数据库和实体中仍使用字符串，支付与下单服务已通过 `OrderStatus` 枚举集中使用 `PENDING`/`PAID`，但数据库没有状态约束，也没有取消、超时解冻、退款或支付失败处理。
- CACHE-001/TEST-001 已通过 PR #7 合并并归档：商品详情具备回填、短期不存在标记、随机 TTL、类型恢复、事务提交后失效、广告最新值预热和并发回填顺序。CACHE-003 已通过 PR #13 增加 Redis 超时、5 秒绕过、4 个数据库回退名额、50 毫秒等待、HTTP 503、单恢复检查者和商品键清理。CACHE-002 已通过 PR #15 实现单进程内同商品正常缓存未命中的请求合并，并完成 600 请求的本机正式开关对照；它不是 Redis 分布式锁，也不能让多个后端实例全局只回源一次。正常运行时 Redis 删除失败后旧值仍可能存活到原 TTL，当前版本也尚未执行 CACHE-002 与 CACHE-003 的真实 Redis 停机复合压测。
- DB-001A 已通过 PR #3 建立 Flyway V1 完整基线和 V2 订单支付字段，并将 JPA 切换为 `ddl-auto: validate`；库存商品唯一约束和历史重复库存处理由 DB-001B 跟踪，遗留支付实体/表清理由 DB-001C 独立跟踪。后续实体结构变化必须增加新迁移版本，不能修改已应用的 V1/V2。
- JPA 当前依赖 Spring Boot 默认开启的 Open Session in View；部分实体关联为延迟加载，可能把数据库查询推迟到控制器或响应生成阶段。JPA-001 要求先用接口测试明确数据读取边界，再关闭该选项。
- SEC-001 已收紧精确公开路由、认证来源、账户/购物车/支付表单资源所有权、JWT 配置和 CORS 白名单；但 Spring Security 仍使用 `permitAll`，具体边界分布在拦截器、控制器和服务层，且 CSRF 当前关闭。后续改动必须避免形成相互矛盾的双重认证实现，SEC-012 继续跟踪 CSRF。
- 测试已覆盖 OSS 图片上传安全边界、ORD-001 下单事务与真实 MySQL 并发库存、PAY-001 的真实 MySQL 迁移与并发重复通知、CACHE-001 的真实 Redis 序列化/TTL/回填/失效/事务回滚和并发顺序、CACHE-003 的连接/命令超时、受限数据库回退、HTTP 503 与单恢复检查者，以及 DATA-001 的确定性生成、真实 MySQL 重复导入、失败回滚和命名锁并发顺序。CACHE-002 新增同商品并发合并、不同商品并行、负责人异常/超时/中断、独立事务加载和 CACHE-003 衔接测试；PR #15 合并代码的默认 Maven 回归为 40 个测试类、245/245，其中真实 Spring + MySQL 8 + Redis 6 的 `ProductCacheIntegrationTest` 为 25/25。TEST-002 已通过 PR #17 增加 Surefire 配置防回归测试，当前 `master` 为 41 个测试类、246/246；Maven 3.9.9/Java 17/Surefire 3.2.5 使用一个可复用独立测试进程，在 1 GiB 容器硬上限下连续两轮默认 `mvn test` 通过，最多同时观察到 2 个 Java 进程。该 1 GiB 结论只代表预先选择的本机最低候选档位，低于 1 GiB 未测试。等待后 Redis 故障分流由测试替身在真实 Spring/数据库环境中触发，不等同于真实停止 Redis；正式热点对照为三轮关闭、三轮开启、每轮 100 请求，600/600 正确，数据库查询、行锁等待、连接池等待和 P95/P99 均满足计划中的硬性标准。支付宝沙箱只读查询探针必须显式设置 `RUN_REAL_ALIPAY_PROBE=true`，默认测试不会访问支付宝；探针只允许 HTTPS 的 `alipaydev.com` 官方沙箱网关，只接受随机订单返回 `40004 / ACQ.TRADE_NOT_EXIST`，且不能替代公网异步通知。本机没有全局 Maven 时，当前使用固定 `maven:3.9.9-eclipse-temurin-17` 容器执行。
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
