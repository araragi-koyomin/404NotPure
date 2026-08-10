---
title: 运行环境、Docker 与外部依赖验证计划
type: plan
layer: warm
status: active
created: 2026-08-09
updated: 2026-08-10
owners:
  - maintainers
tags:
  - runtime
  - docker
  - alipay
  - oss
related:
  - OSS-003
  - RUN-002
  - DEPLOY-001
  - PAY-001
  - DB-001A
---

# 运行环境、Docker 与外部依赖验证计划

## 已确认基线

- 本机 Java 17.0.19 可编译并实际运行 Spring Boot 2.7.6；本机未全局安装 Maven，验证时使用仓库忽略目录中的临时 Maven 3.9.9。
- Node 20.20.2、npm 10.8.2 可安装依赖、启动 Vite 和完成生产构建。
- 宿主机 3000 被其他项目占用；本项目已统一改用 5173。
- Docker Desktop 可用；MySQL 8.0.46 和 Redis 6.2.23 容器已健康运行。
- 本机混合模式已验证前端 5173、后端 8080、MySQL 3307、Redis 6379。
- 已通过前端代理完成临时账户注册、登录、HttpOnly Cookie 和账户读取烟雾测试。

已完成证据位于 [运行环境恢复归档](../archive/2026-08-09-runtime-revival.md)。

## 本机环境变量统一入口

本机配置模板统一为 `back_end/.env.example`，逐项填写说明统一为[本机环境变量配置与启动指南](../guides/local-environment.md)。真实值只允许保存在被 Git 忽略的 `back_end/.env` 或本机安全的进程环境中；`application.yml` 只保留环境变量引用和无秘密默认值。

同一份 `.env` 以宿主机后端连接地址填写：MySQL `127.0.0.1:3307`、Redis `127.0.0.1:6379`。完整 Compose 会在 backend 容器内覆盖为 `db:3306` 和 `redis:6379`。支付宝 `FRONTEND_URL` 是后端直接拼接订单号的地址前缀，当前必须包含 `?payment_success=true&orderId=`；异步通知地址必须是支付宝服务器可以访问的公网 HTTPS 后端地址。`ARK_API_KEY` 和两个真实 OSS 探针开关都不属于长期 `.env` 配置。

## 外部凭据只读验证结果

用户已授权使用现有支付宝和 OSS 配置做外部连通性验证，执行约束如下：

- 不输出、记录或复制配置值；
- 不上传、覆盖或删除 OSS 对象；
- OSS 仅允许客户端初始化、Bucket 存在性/权限可见性等只读请求；
- 不创建支付宝真实交易、不发起付款、不触发退款；
- 优先验证本地私钥解析、签名能力、支付宝公钥解析和网关可达性；如无法在不创建交易的前提下验证账户状态，明确标为未验证；
- 日志只记录供应商、操作类型、脱敏错误类别、HTTP/SDK 结果与时间；
- 验证失败必须新增或更新 BACKLOG 缺陷，不得静默忽略。

2026-08-09 已完成 EXT-001，脱敏证据归档于 [外部依赖验证报告](../archive/2026-08-09-external-dependency-validation.md)：

- 支付宝应用私钥和支付宝公钥均可由 RSA KeyFactory 解析；
- 支付宝只读交易查询请求到达网关，返回 `40004 / ACQ.TRADE_NOT_EXIST`，证明当前 appId、私钥签名、公钥验签配置和网关连通性可用；未创建真实交易；
- OSS 请求到达目标 Bucket，但供应商返回 `BucketDisable`，当前图片上传链路不可用；
- OSS SDK 默认 DEBUG 输出包含 Authorization 头。虽然未输出 AccessKey Secret，但凭据材料进入日志，已登记 SEC-002，并要求轮换 AccessKey。

EXT-001 已从热层移除；后续恢复任务以 OSS-001 继续跟踪。

## OSS-001：重建对象存储

项目所有者已确认旧 OSS 资源彻底过期且没有数据可恢复，因此不再尝试恢复旧 Bucket。应新建 Bucket 和项目专用的最小权限 RAM 身份。新 Bucket 默认保持私有；若为兼容当前永久图片 URL 而启用公共读，必须保持公共写关闭，并在文档中记录风险和后续私有化方案。前端上传经后端中转，不需要为浏览器直传配置 OSS CORS。

2026-08-09 进展：新 Bucket 已在华北 2 地域创建，当前为私有并开启阻止公共访问；专用 RAM 用户已创建且未开启控制台登录。最小权限策略已按 `acs:oss:*:*:{bucket}/tomatomall/images/*` 配置 `oss:PutObject` 并绑定用户。使用关闭全部 SDK/HTTP 日志的临时探针，对随机 `validation-*.txt` object key 执行真实 PutObject 成功，证明 RAM 控制台“摘要 Beta”的无效提示不代表该策略实际失效。早期临时对象已由项目所有者清理。

应用安全接入现已完成但尚未合并：

- 图片写接口不再匿名放行；注册页不再在登录前上传头像；
- `usage=AVATAR|PRODUCT|ADVERTISEMENT` 区分对象前缀，商品和广告图片要求管理员，头像要求已登录；
- 文件上限 10 MiB，并校验 PNG/JPEG/GIF 的声明 MIME、文件签名和可解码内容；
- object key 使用服务端 UUID，上传请求设置真实 Content-Type 和 `x-oss-forbid-overwrite=true`；
- Endpoint 可兼容带协议或不带协议的配置，返回 URL 不再重复协议；
- Apache HTTP headers/wire logger 显式关闭，真实上传日志未出现 Authorization、签名或凭据。

真实应用验证在新构建的 8081 实例完成：注册和登录业务码 200，普通用户商品图业务码 403，头像 PutObject 业务码 200。本地临时账号已从隔离测试数据库清理。验证对象为 `tomatomall/images/avatar/954b441c-5afe-4fe1-b46e-ef43e778574a.png`。

项目所有者完成最小匿名只读配置后，2026-08-09 的闭环复验结果为：匿名 HTTPS GET 返回 200、Content-Type 为 `image/png`、下载 18,805 字节；匿名 ListObject 返回 403；对同一对象携带 `x-oss-forbid-overwrite: true` 的匿名 PutObject 返回 403，随后再次读取仍为 200 和 18,805 字节。这证明当前策略允许目标前缀读取，但没有开放列举或匿名写入。项目所有者随后删除验证对象，复验 GET 返回 404，本轮外部写入、读取、权限负测和清理闭环完成。

新凭据接入运行实例前必须完成：

- SEC-002：关闭可能输出 Authorization 请求头的 SDK/HTTP DEBUG 日志并验证脱敏；
- SEC-003：`POST /api/images` 不再允许匿名上传，并限制允许的调用身份、文件大小和实际文件类型；
- OSS-002：object key 改为隔离前缀和服务端生成的唯一名称，避免使用原文件名导致覆盖；
- 保持当前仅对 `tomatomall/images/*` 匿名 `oss:GetObject` 的策略，Bucket ACL 继续私有，禁止匿名 ListObject 和 PutObject；
- 后续自动化外部探针如需自行清理，应使用 `tomatomall/images/_validation/*` 隔离前缀，并只为该前缀增加 `oss:DeleteObject`；不得给运行身份开放业务图片全前缀删除权。也可在项目所有者明确授权后，使用已登录控制台会话精确删除单个探针对象。

### OSS-001 下一阶段：自动化验证清理

项目所有者已选择自动化方案。人工闭环完成后，OSS-001 进入第四阶段“自动化验证清理”，完成后才进入代码与策略评审阶段。

范围和顺序：

1. 云端策略只新增 `oss:DeleteObject`，Resource 严格限定为 `acs:oss:*:*:{bucket}/tomatomall/images/_validation/*`；现有 `oss:PutObject` 继续覆盖图片前缀，匿名 Bucket Policy 仍只有 `oss:GetObject`，不得开放 ListObject 或公共写。
2. 先写失败单元测试，证明删除 API 缺失，以及业务前缀删除必须被本地边界拒绝；实现仅接受 `_validation/` key 的删除方法。
3. 增加不进入默认 `mvn test` 的显式外部探针命令。探针生成随机 key，执行 PutObject、匿名 HTTPS GET、DeleteObject、匿名 GET 404，并在 `finally` 中再次尝试清理。
4. 探针日志只允许输出阶段、HTTP/SDK 结果和验证 object key，不得输出 Bucket、Endpoint、AccessKey、Authorization、签名或 token。失败时必须明确报告是否可能残留对象。
5. 用真实 RAM 策略执行一次探针，确认业务图片 key 的删除保护测试仍通过，并检查 `_validation` 下没有残留对象。

完成标准：自动探针一次通过完整 Put→Get→Delete→404；故障路径能够执行清理；运行身份无法删除 avatar、product 或 advertisement 对象；默认单元测试不访问真实 OSS。该标准已经完成，合并证据见冷层交付记录；后续演示前仍需重新执行显式检查，因为云端权限可能被人工修改。

2026-08-09 TDD 实现进展：

- `OssUtilTest` 的删除测试首先因 `deleteValidationObject` 不存在而编译失败；实现后 11/11 通过，覆盖验证 key 删除、业务前缀拒绝、嵌套/穿越拒绝和异常时客户端关闭；
- `OssLifecycleProbeTest` 首先因编排类不存在而编译失败；实现后 4/4 通过，覆盖 200→404 正常结果、读取失败清理、上传失败清理和清理失败显式报告；
- `OssLifecycleProbeIT` 采用 `*IT` 命名且要求 `RUN_REAL_OSS_PROBE=true`，不会被默认 Surefire 测试发现；
- 一键入口 `back_end/scripts/Invoke-OssLifecycleProbe.ps1` 只加载变量到当前进程、只报告缺失变量名称，可用 `-NoFork` 兼容本机已知的 Surefire 原生内存限制。

云端最小权限配置完成后，在 `back_end/` 执行：

```powershell
.\scripts\Invoke-OssLifecycleProbe.ps1 -NoFork
```

真实 RAM 策略下首轮自动探针已完成 Put→匿名 GET 200→Delete→GET 404，对象没有残留。但探针没有启动 Spring，测试日志配置未继承 `application.yml`，Apache HTTP DEBUG 输出了 Authorization 凭据材料。原 AccessKey 随即停止使用；在完成测试运行时日志保护前不得再次运行真实探针。

本地日志边界已由非 Spring 红绿测试证明，完整默认测试在占位 OSS 配置下 30/30 通过；持有旧 Key 的后端进程已停止。项目所有者随后停用旧 AccessKey、为同一最小权限 RAM 身份创建新 AccessKey，并更新忽略的 `back_end/.env`。

2026-08-09 使用轮换后的新凭据再次执行显式探针，1/1 通过完整 Put→匿名 GET 200→Delete→GET 404，验证对象没有残留。Maven 输出仅包含构建和测试摘要，没有出现 Authorization、签名、AccessKey、Endpoint、Bucket 或对象名。新构建后端随后在本机 8080 启动，商品接口直连和经 5173 Vite 代理均返回 HTTP 200，启动日志未发现 MySQL/Redis 连接失败或 OSS 敏感日志标记。OSS-001 与 SEC-002 的本批次目标已经完成并转入冷层。

## OSS-002：对象命名与 URL

当前 `OssUtil` 把客户端原始文件名直接作为 object key；OSS `PutObject` 默认会覆盖同名对象。当前 URL 还按 `https://{bucket}.{endpoint}/{object}` 手工拼接，若 endpoint 包含协议会生成错误地址。改造时应由服务端生成类似 `tomatomall/images/{uuid}.{ext}` 的 key，拒绝覆盖，并集中处理 endpoint、公开 URL 或私有签名 URL。

本轮目标仅为本机/面试演示。上传鉴权、文件校验、唯一 object key、真实 PutObject 和匿名读取闭环已完成。Bucket ACL 继续保持私有，不得设为公共读或公共读写；匿名 Allow 只覆盖 `oss:GetObject` 和 `tomatomall/images/*`，写入始终只允许专用 RAM 用户。公网域名、CDN 和私有签名 URL 不纳入本轮。

## RUN-002：完整 Compose

2026-08-10 优先级决定：当前交付目标是本机运行和面试演示，前端、后端、MySQL、Redis 的混合运行方式已经验证可用，因此完整四容器编排从 P1 调整为长期 P2。PR #5 合并前的人工烟雾又成功运行 frontend、backend、MySQL 和 Redis 四个 Compose 服务，数据库与 Redis 显示健康，5173 前端主链路可以访问后端。该证据解决了“四个服务从未同时运行”的旧状态，但 RUN-002 仍保持 blocked，因为下面的完整验收标准尚未全部覆盖；不能把一次成功运行扩展成可重复部署已经完成。

目标命令：

```powershell
docker compose --env-file back_end/.env up --build
```

完成标准：

- 四个服务可创建；MySQL、Redis 健康；后端 8080 启动；前端 5173 启动；
- 前端 `/api/products` 经容器网络代理到 backend；
- 重启后数据库与 Redis 卷行为符合预期；
- Compose 不把 `.env` 值打印到日志；
- Java 17 基础镜像能稳定从选定 registry 拉取。

历史镜像拉取阻塞已在本轮成功构建和启动时暂时解除，但尚未证明选定 registry 在清空本机镜像缓存后仍能稳定拉取。当前剩余证据是：对数据库与 Redis 命名卷执行受控重启并核对数据、检查 Compose 启动日志不会回显真实 `.env` 值，以及记录一次无本机镜像缓存条件下的稳定构建。完成这些验证前，RUN-002 不能标记完成。

历史配置缺陷已经修复：同一份 `back_end/.env` 在本机混合模式中使用 `DB_HOST=127.0.0.1`、`DB_PORT=3307`，而 Compose 内 backend 必须使用 `DB_HOST=db`、`DB_PORT=3306`。当前 Compose 已同时显式覆盖这两个容器内值，`Test-ComposeConfiguration.ps1` 也会检查它们，避免再次继承宿主机端口。本轮四个服务运行和 5173 浏览器主链路已经验证；RUN-002 尚未完成的原因收窄为数据卷重启、日志配置回显和无本机缓存镜像拉取三类可重复证据仍缺。

## OSS-003：业务未引用图片的长期清理

本机和面试阶段接受可能出现少量“已经上传到 OSS、但后来没有被商品、广告或头像记录引用”的图片，不在当前批次实现自动删除。这里不扩大应用运行账户对 `avatar`、`product`、`advertisement` 业务图片的删除权限，避免程序故障或凭据泄露时能够删除正在使用的真实图片。

后续优先采用两阶段方案：图片先上传到隔离的临时目录，业务数据保存成功后再由后端确认并移动或复制到正式业务目录；超过保留时间且没有确认的临时图片由受限清理流程回收。实现前必须先用测试覆盖业务保存失败、请求超时与重试、重复确认、确认成功和清理失败。若使用 Bucket Lifecycle 自动清理临时目录，需把不含账号或密钥的规则模板纳入仓库，并确认前端只得到已经确认的正式图片 URL。

该项是长期 P2，不阻塞当前本机运行和面试演示，也不能因为延期就宣称业务未引用图片已经具备自动清理能力。

2026-08-10 曾发现磁盘上的 Compose 已限制 Redis 端口，但旧运行容器仍发布到所有网络接口。SEC-008 随后只重新创建 Redis 服务容器，没有删除命名数据卷，也没有重启 MySQL；重启前后 `DBSIZE` 都为 0。新容器恢复 healthy 后，实际发布地址为 `127.0.0.1:6379`，`redis-cli PING` 和 `Test-ComposeConfiguration.ps1` 均通过，冷启动独立复核也已确认配置与运行证据一致。该缺陷已转入冷层；后续每次修改端口绑定仍要同时检查 Compose 解析结果和正在运行的容器发布地址。

## 本机或面试演示前的 OSS 检查

阿里云 RAM 权限、Bucket Policy、AccessKey 状态和 Bucket 配置都可能在代码没有变化时被人工修改。这个风险不新增独立开发任务，而是作为每次需要演示真实 OSS 上传前必须执行的运行检查：

1. 确认使用的是当前有效、仅供本项目使用的 RAM AccessKey，且本地 `.env` 未被 Git 跟踪。不要在终端、截图或文档中显示具体值。
2. 在 `back_end/` 中运行 `./scripts/Invoke-OssLifecycleProbe.ps1 -NoFork`。它应完成验证目录中的上传、读取、删除和删除后 404，并且不留下验证对象。
3. 运行 `./scripts/Invoke-OssBusinessDeletePermissionProbe.ps1 -NoFork`。它使用随机生成且确认不存在的业务图片名称，验证运行身份仍然不能删除头像、商品和广告目录中的图片。
4. 检查控制台只包含 Maven 与测试结果，不包含 AccessKey、Authorization、签名、Bucket、Endpoint、对象名或云账户诊断信息。
5. 任一检查失败时停止真实演示，把具体问题和阻塞原因同步到 BACKLOG；不得临时扩大 RAM 权限来绕过失败。

这两个检查会访问真实阿里云，因此不进入默认 `mvn test`，只在项目所有者明确允许且凭据已配置时执行。

## 废弃功能边界

AI assistant 已由项目所有者决定废弃，不再要求配置 `ARK_API_KEY`，也不属于运行、测试或外部依赖验收范围。现存代码暂留以避免在交易改造中引入无关兼容变化；决策见 [assistant 废弃决策](../archive/2026-08-09-assistant-deprecation-decision.md)。

## DEPLOY-001：公网长期部署

公网部署已明确延后为长期 P2，不阻塞本机/面试演示。后续独立规划至少覆盖：部署平台与地域、环境隔离、域名与 HTTPS、CDN 或 OSS 自定义域名、私有资源签名 URL、Secret 管理与轮换、数据库备份、日志与监控、费用告警、健康检查和回滚流程。

如果未来恢复长期部署，还必须把 OSS 生命周期检查和业务图片删除权限检查变成定期、可告警的自动检查，用来发现 RAM 策略、Bucket Policy 或 AccessKey 状态被意外修改。自动检查仍然只能删除 `_validation` 目录中的验证对象，不能获得业务图片删除权限。没有完成真实部署和公网安全验证前，不得宣称项目已具备生产部署能力。
