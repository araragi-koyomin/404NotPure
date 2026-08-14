---
title: 本机环境变量配置与启动指南
type: governance
layer: warm
status: active
created: 2026-08-10
updated: 2026-08-14
owners:
  - maintainers
tags:
  - local-development
  - configuration
  - authentication
  - mysql
  - redis
  - alipay
  - oss
related:
  - SEC-001
  - SEC-012
  - PAY-001
  - DB-001A
  - OSS-003
  - RUN-002
---

# 本机环境变量配置与启动指南

本文说明 `back_end/.env` 中每个变量的用途、来源和填写方式，面向本机开发与面试演示。示例文件是 `back_end/.env.example`；真实 `.env` 已被 Git 忽略，禁止把真实密码、AccessKey、支付宝密钥或公网隧道凭据复制到文档、测试、截图和提交记录中。

## 第一次配置

在仓库根目录执行：

```powershell
Copy-Item back_end/.env.example back_end/.env
```

如果 `.env` 已经存在，不要覆盖它。逐项对照本文补充或修正即可。每个配置独占一行，推荐使用 `名称=值`，不要在值后面写说明文字。特别是私钥、公钥和 URL，不要加中文注释或多余空格。

## 本机与 Compose 的地址区别

同一份 `.env` 默认按“后端在宿主机运行、MySQL 和 Redis 由 Compose 提供”的方式填写：

| 服务 | 宿主机后端使用的地址 | Compose 内后端使用的地址 |
|---|---|---|
| MySQL | `127.0.0.1:3307` | `db:3306` |
| Redis | `127.0.0.1:6379` | `redis:6379` |
| 前端 | `127.0.0.1:5173` | 容器监听 `5173` |
| 后端 | `127.0.0.1:8080` | 容器监听 `8080` |

`docker-compose.yml` 会覆盖 backend 容器中的 `DB_HOST`、`DB_PORT` 和 `REDIS_HOST`，所以 `.env` 保持宿主机地址也能用于完整 Compose。必须使用以下命令让 Compose 同时读取数据库插值和 backend 环境变量：

```powershell
docker compose --env-file back_end/.env up --build
```

## 认证 Cookie、JWT 与跨域来源

本轮把 JWT（JSON Web Token，即登录成功后用于证明用户身份的签名令牌）签名密钥从 Java 源码迁移到环境变量。现有真实 `.env` 不会由自动化脚本读取或改写；维护者需要参照 `.env.example` 手工补齐下列变量。缺少 `JWT_SECRET` 时后端会拒绝启动，避免静默退回仓库内置密钥。

| 变量 | 本机填写规则 | 说明 |
|---|---|---|
| `JWT_SECRET` | 使用密码生成器创建至少 32 个字符的随机值 | 只保存在忽略的 `.env` 或部署环境，不得提交、打印或复制到测试。不同环境应使用不同值；修改后，旧令牌会立即失效。 |
| `JWT_EXPIRATION_SECONDS` | 默认 `86400` | JWT 与认证 Cookie 共用该寿命，避免令牌已经过期但浏览器仍长期发送 Cookie。 |
| `AUTH_COOKIE_SECURE` | 本机 HTTP 调试使用 `false`；HTTPS 部署必须使用 `true` | `true` 表示浏览器只通过 HTTPS 发送认证 Cookie。本机没有 HTTPS 时直接开启会导致浏览器不发送 Cookie。 |
| `CORS_ALLOWED_ORIGINS` | 默认 `http://127.0.0.1:5173,http://localhost:5173` | CORS 是浏览器的跨来源访问规则。这里只能列完整、可信的前端来源，逗号分隔，不支持任意来源通配，也不要填写路径。 |

浏览器兼容规则是：名为 `token` 的 HttpOnly Cookie 和精确同名的 `token` 请求头可以单独使用；两者同时存在时必须是同一个令牌，否则请求按未登录拒绝。认证 Cookie 使用 `SameSite=Lax`，这会减少第三方网页自动携带 Cookie 的机会，但不等于完整的 CSRF（跨站请求伪造）防护。当前 CSRF 仍关闭，后续兼容方案由 `SEC-012` 跟踪；部署时不能把 CORS 白名单或 `SameSite` 当作已经解决全部跨站请求风险。

退出登录会调用 `POST /api/accounts/logout` 使 HttpOnly Cookie 立即过期；只有服务器成功响应后，前端才清理当前标签页保存的请求头令牌并跳转登录页。若网络中断或服务器不可达，前端会保留当前页面和本地身份并明确提示 Cookie 可能仍有效，用户可以重试，不能把“只清了页面状态”误当成安全退出。该接口允许未登录调用，以便令牌已过期时仍能清理浏览器 Cookie；它不会使已经被复制到其他设备的无状态 JWT 提前失效。

## MySQL 配置

| 变量 | 本机推荐值或填写规则 | 说明 |
|---|---|---|
| `DB_HOST` | `127.0.0.1` | 本机后端连接 Compose MySQL 时使用。不要写 `db`，除非后端本身也在 Compose 网络中。 |
| `DB_PORT` | `3307` | Compose 将宿主机 `3307` 映射到 MySQL 容器 `3306`。 |
| `DB_NAME` | `Tomato` | Compose 首次创建数据库时也读取这个值；数据库名大小写保持一致。 |
| `DB_USER` | `root` | 当前 Compose 只配置 root 账户。若改为普通数据库用户，还要同步配置 Compose 创建该用户。 |
| `DB_PASSWORD` | 自行生成的本机数据库密码 | 该值同时用于应用连接和 Compose 的 `MYSQL_ROOT_PASSWORD`，不能留空。 |

数据库结构由 Flyway 迁移，再由 Hibernate 检查，不再由 Hibernate 自动改表：

| 变量 | 正常值 | 说明 |
|---|---|---|
| `FLYWAY_BASELINE_ON_MIGRATE` | `false` | 全新数据库、已经有 `flyway_schema_history` 的数据库以及当前已迁移开发库都保持 `false`。 |

只有“数据库已有旧表、但完全没有 Flyway 历史”时，才可能在备份并人工确认它与 V1 结构一致后，临时设为 `true` 运行一次。完成后立即恢复 `false`。不能把它长期打开，也不能用它强行接受来源不明或结构不一致的旧数据库。

## DATA-001 独立演示数据库

DATA-001 不写入日常开发数据库 `Tomato`，只允许写入名称严格等于 `tomatomall_demo` 的独立数据库。导入器不会创建、删除或重建数据库，也不会清空任何表。首次使用时，在 MySQL Compose 服务已经运行后执行下面的交互式命令；`-p` 会让 MySQL 在终端询问本机数据库密码，命令行和文档都不保存密码值：

```powershell
docker compose --env-file back_end/.env exec db `
  mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS tomatomall_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

然后在当前 PowerShell 会话中提供只用于本地演示账户的密码并运行单一入口。请自行填写密码，不要把示例占位符原样使用，也不要截图或提交真实值：

```powershell
$env:TOMATOMALL_DEMO_PASSWORD = '<仅限本地的演示密码，至少 12 个字符>'
.\scripts\demo-data\import-demo-data.ps1
```

默认生成 300 本书、500 个规模用户、`demo_admin` 和 `demo_user` 两个可登录账户，以及库存、规格、本地 SVG 和 6 个广告。数量和固定随机种子可以显式调整：

```powershell
.\scripts\demo-data\import-demo-data.ps1 -Books 500 -Users 800 -Seed 404
```

脚本只从 `back_end/.env` 读取 `DB_HOST`、`DB_PORT`、`DB_USER` 和 `DB_PASSWORD`，不会读取或输出 OSS、支付宝、JWT 等配置；运行时强制把目标数据库设为 `tomatomall_demo`，Java 导入器连接后还会执行第二次数据库名称校验。`TOMATOMALL_DEMO_PASSWORD` 经过 BCrypt 后入库，脚本结束时不会主动清除调用者原先设置的该变量；不使用时可以手工执行 `Remove-Item Env:TOMATOMALL_DEMO_PASSWORD`。

重复执行会补齐缺失的 DATA-001 记录，但不会覆盖已有演示账户后来修改的密码/资料，也不会重置业务操作改变过的库存。生成的 SVG 位于被 Git 忽略的 `front_end/public/demo-data/generated/`；同一 seed 会覆盖为相同内容，不需要把几百张生成图片提交到 Git。文件生成和 MySQL 事务无法组成同一个跨系统事务：数据库失败时可能留下未被引用的本地 SVG，但再次执行会确定性覆盖它们，不会留下半套数据库记录。

要让完整 Compose 后端连接演示数据库，可以在启动命令所在的 PowerShell 会话临时覆盖 `DB_NAME`：

```powershell
$env:DB_NAME = 'tomatomall_demo'
docker compose --env-file back_end/.env up --build
```

停止演示后执行 `Remove-Item Env:DB_NAME`，恢复 `.env` 中的日常开发数据库名。DATA-001 不提供自动清库或删除 Docker volume 的命令。

## Redis 配置

| 变量 | 本机推荐值 | 说明 |
|---|---|---|
| `REDIS_HOST` | `127.0.0.1` | Compose Redis 只映射到本机回环地址，不对局域网公开。 |
| `REDIS_PORT` | `6379` | 当前 Compose Redis 使用默认端口。 |
| `REDIS_CONNECT_TIMEOUT` | `250ms` | 与 Redis 建立连接最多等待 250 毫秒；超时后商品缓存进入故障回退，不要设为无限等待。 |
| `REDIS_COMMAND_TIMEOUT` | `500ms` | Redis `GET`、`SET`、`DELETE`、恢复扫描等命令最多等待 500 毫秒。 |
| `PRODUCT_DETAIL_CACHE_ENABLED` | `true` | 商品详情 Cache-Aside 总开关；普通本机和 Compose 运行必须保持默认开启。仅 PERF-001 的隔离性能项目可以临时设为 `false`，用于获得不包含 Redis 失败开销的纯 MySQL 对照组。 |
| `PRODUCT_CACHE_SINGLE_FLIGHT_ENABLED` | `true` | CACHE-002 的单实例热点商品请求合并开关。开启后，同一商品正常缓存未命中时只有一个请求查询数据库，其他请求等待后重读 Redis；关闭只用于同版本性能对照或紧急诊断，不代表数据库正确性保护被关闭。 |
| `PRODUCT_CACHE_SINGLE_FLIGHT_WAIT_TIMEOUT` | `500ms` | 同一商品的等待者在 CACHE-002 中累计最多等待 500 毫秒；超时返回统一 HTTP 503。必须使用明确时间单位，0、负数或无法识别的值会导致应用拒绝启动。该时间只约束单个后端实例内的等待，不是多实例集群的全局锁超时。 |
| `PRODUCT_CACHE_FAILURE_BYPASS_DURATION` | `5s` | 第一次 Redis 连接或命令故障后，商品缓存临时跳过 Redis 的时间；到期后只允许一个请求检查恢复。 |
| `PRODUCT_CACHE_DB_FALLBACK_MAX_CONCURRENT` | `4` | Redis 故障期间允许同时回退 MySQL 的商品详情请求数；限制商品读取占用数据库连接，为订单和支付保留容量。 |
| `PRODUCT_CACHE_DB_FALLBACK_WAIT` | `50ms` | 4 个回退名额用满后，新请求最多等待 50 毫秒；仍无名额则返回 HTTP 503。 |
| `PRODUCT_CACHE_RECOVERY_CLEANUP_BATCH_SIZE` | `100` | Redis 恢复时使用 `SCAN` 分批删除 `product:detail:v1:*` 的批量大小；不执行 `FLUSHDB`。 |

当前本机 Redis 没有密码，因此不得把端口绑定改回 `0.0.0.0:6379` 或简写成对所有网络接口开放的 `6379:6379`。

PERF-001 使用 `performance/scripts/Initialize-PerfRuntime.ps1` 生成被 Git 忽略的性能运行文件。该脚本只把性能数据库连接必需的 `DB_PASSWORD` 写入 `performance/runtime.env`，另行生成性能专用 JWT 密钥和不会访问真实服务的 OSS/支付宝占位配置；演示管理员密码单独写入只读挂载文件。性能后端和 Maven 回归不得加载整份日常 `.env`，因此不会接收真实 OSS AccessKey、支付宝密钥、废弃 AI 配置或演示账户密码。所有文件均不得提交或写入结果报告。

## 阿里云 OSS 配置

| 变量 | 从哪里取得 | 填写规则 |
|---|---|---|
| `ALIYUN_OSS_ENDPOINT` | OSS Bucket 概览页的地域 Endpoint | 例如北京地域填写 `oss-cn-beijing.aliyuncs.com`。代码能够去除开头的 `http://` 或 `https://`，示例统一不写协议。不要填写带 Bucket 名的完整域名。 |
| `ALIYUN_OSS_ACCESS_KEY_ID` | 专用 RAM 用户的 AccessKey | 使用本项目运行身份，不使用阿里云主账号 AccessKey。 |
| `ALIYUN_OSS_ACCESS_KEY_SECRET` | 创建同一 AccessKey 时显示的 Secret | Secret 通常只显示一次；遗失时应轮换 AccessKey，不要尝试从日志或提交中找回。 |
| `ALIYUN_OSS_BUCKET_NAME` | OSS Bucket 列表或概览页 | 只填 Bucket 名，不带协议、Endpoint 或路径。 |

RAM 权限保持最小范围：运行身份可以上传业务图片，只能删除 `_validation` 验证目录中的对象；不能获得删除头像、商品图或广告图的权限。Bucket 保持私有，匿名读取只通过现有 Bucket Policy 限定到 `tomatomall/images/*`，不得开放匿名列举和写入。

真实 OSS 检查会访问云资源，不属于默认测试。需要演示前，按[运行与外部依赖验证计划](../plans/runtime-and-external-dependencies.md#本机或面试演示前的-oss-检查)执行两个显式探针。

## 支付宝沙箱配置

八个支付宝变量必须来自同一个沙箱应用和对应的沙箱商户，不能混用正式环境、旧沙箱应用或另一套密钥。

### 长时间未维护项目的恢复原则

支付宝开放平台在 2026-04-29 发布了“沙箱环境升级公告”。对于多年未使用的项目，不能仅凭旧 `.env` 判断旧沙箱 APPID、账号、网关和密钥是否仍有效。恢复时把旧值全部视为“尚未确认”，以当前登录后的沙箱控制台为准：

1. 打开[支付宝开放平台](https://open.alipay.com/)并使用当年创建沙箱的开发者支付宝账号登录。也可直接进入官方[沙箱调试入口](https://openhome.alipay.com/platform/appDaily.htm?tab=info)，未登录时会先跳转到登录页。
2. 在控制台找到“沙箱”或“沙箱调试”。先看当前是否仍有可用的沙箱应用；如果旧应用不再显示、无法进入或平台要求重新开通，就使用当前控制台提供的新沙箱应用，不再尝试恢复旧 APPID。
3. 在“沙箱应用/基本信息”记录当前 APPID 和控制台显示的沙箱网关；在“沙箱账号”记录当前商家 PID，并保存当前沙箱买家登录账号。买家账号只用于支付页面登录，不写入 `.env`。
4. 在“接口加签方式/开发设置/密钥配置”检查当前应用使用的密钥模式。本项目使用 RSA2 普通公钥模式，不使用证书模式。旧应用私钥无法从支付宝平台下载；只要不确定本地私钥是否与当前已登记应用公钥匹配，就使用[支付宝开放平台密钥工具](https://open.alipay.com/tool)重新生成 RSA2 密钥对，把新的应用公钥配置到当前沙箱应用。
5. 配置应用公钥后，从同一页面重新复制平台生成或展示的“支付宝公钥”。应用私钥和支付宝公钥是一对不同用途的配置，不能互换。
6. 重新建立本机 8080 的公网 HTTPS 映射。多年以前的 natapp 或其他临时域名应默认视为已过期，重新生成 `notify` 和 `returnUrl` 地址。
7. 八项配置填完后再启动后端并做沙箱交易；不要先拿旧值逐项试错，因为不同沙箱应用的 APPID、商家 PID和密钥混合后一定无法形成可信回调。

当前官方帮助中心仍把沙箱 APPID、沙箱网关、沙箱应用私钥和沙箱支付宝公钥列为联调所需的基本信息，并提供沙箱账号、密钥生成和支付宝公钥查看入口。控制台菜单名称可能调整，找不到时从[官方帮助中心](https://open.alipay.com/support/supportCenter.htm)进入“沙箱相关”或“签名相关”。

### 旧值是否还能使用

| 旧配置 | 何时可以继续使用 | 推荐动作 |
|---|---|---|
| `ALIPAY_APP_ID` | 与当前沙箱应用页面显示的 APPID 完全相同 | 不相同或旧应用消失时，改用当前 APPID。 |
| `ALIPAY_SELLER_ID` | 与当前“沙箱账号”中的商家 PID 完全相同 | 重新复制当前 PID，不凭记忆填写。 |
| `ALIPAY_APP_PRIVATE_KEY` | 能确认它对应当前沙箱应用已经登记的应用公钥 | 私钥无法从平台找回；不确定时直接生成新密钥对并更新应用公钥。 |
| `ALIPAY_ALIPAY_PUBLIC_KEY` | 与当前应用密钥页面显示的支付宝公钥完全相同 | 每次更换应用公钥后都重新复制，不沿用旧值。 |
| `ALIPAY_SERVER_URL` | 与当前沙箱控制台显示的网关完全相同 | 因存在沙箱升级，以当前控制台为准；不要从旧博客复制。 |
| `ALIPAY_NOTIFY_URL` | 旧公网域名仍有效，并能把公网 HTTPS POST 转发到本机 8080 | 临时域名通常已经过期，推荐重新创建。 |
| `ALIPAY_RETURN_URL` | 旧公网域名仍有效，并能把浏览器 GET 转发到本机 8080 | 与新的公网映射一起更新。 |
| `FRONTEND_URL` | 本机前端仍在 `127.0.0.1:5173` 运行 | 当前项目端口未变时可以继续使用。 |

| 变量 | 从哪里取得 | 填写规则 |
|---|---|---|
| `ALIPAY_APP_ID` | 支付宝开放平台沙箱应用详情 | 填沙箱应用 APPID。回调中的 `app_id` 必须与它完全一致。 |
| `ALIPAY_SELLER_ID` | 沙箱账号页面的商家账号 PID/支付宝用户号 | 通常是以 `2088` 开头的数字。它不是 APPID，也不是商家登录邮箱。回调中的 `seller_id` 必须与它完全一致；留空时项目会拒绝回调。 |
| `ALIPAY_APP_PRIVATE_KEY` | 为这个沙箱应用生成的应用私钥 | 使用 RSA2 对应的 PKCS#8 私钥正文，移除 `BEGIN/END` 标记和换行，保存为一行。绝不提交。 |
| `ALIPAY_ALIPAY_PUBLIC_KEY` | 开放平台在应用密钥设置中提供的“支付宝公钥” | 填支付宝公钥正文并保存为一行。不要误填应用公钥，也不要填支付宝公钥证书路径。 |
| `ALIPAY_SERVER_URL` | 当前沙箱应用页面显示的支付宝沙箱网关 | 完整复制控制台显示值。历史环境常见值是 `https://openapi.alipaydev.com/gateway.do`，但由于 2026 年存在沙箱升级，不能绕过控制台核对。正式网关不能与沙箱 APPID、账号或密钥混用。 |
| `ALIPAY_NOTIFY_URL` | 本机公网映射工具提供的 HTTPS 后端地址 | 必须以 `/api/orders/notify` 结尾。该请求由支付宝服务器发起，所以 `127.0.0.1`、`localhost` 和只在局域网可见的地址都不可用。 |
| `ALIPAY_RETURN_URL` | 同一个可访问的后端地址 | 必须以 `/api/orders/returnUrl` 结尾。它负责接收浏览器同步跳转，再跳回前端。 |
| `FRONTEND_URL` | 本机前端地址 | 当前代码会把订单号直接拼在该值后面，必须写为 `http://127.0.0.1:5173/?payment_success=true&orderId=`，末尾的等号不能省略。 |

密钥生成和沙箱账号位置以支付宝当前控制台、[官方帮助中心](https://open.alipay.com/support/supportCenter.htm)和[开发者工具页](https://open.alipay.com/tool)为准。旧版文档或博客只用于理解概念，不能作为当前 APPID、网关和密钥的来源。

异步通知才是把订单从 `PENDING` 改为 `PAID` 的可信入口。浏览器访问 `ALIPAY_RETURN_URL` 只用于页面跳转，不能代替异步通知，也不能作为支付成功证据。当前前端仍可能仅凭同步返回参数显示成功，这个缺陷由 PAY-002 跟踪。

### 暂时没有公网回调地址时如何测试

没有支付宝服务器能够访问的 `ALIPAY_NOTIFY_URL` 时，无法完成“沙箱付款后，支付宝服务器主动调用本机后端”这一段真实端到端验证。这是网络可达性限制，不是通过修改一个本机配置就能绕过的。当前可以分层验证：

为了让本机应用完成配置解析和启动，可以暂时使用下面两个仅本机占位地址：

```dotenv
ALIPAY_NOTIFY_URL=http://127.0.0.1:8080/api/orders/notify
ALIPAY_RETURN_URL=http://127.0.0.1:8080/api/orders/returnUrl
```

它们不代表公网回调已经配置成功。用户浏览器在同一台电脑上可能访问 `return_url`，但支付宝服务器不能通过 `127.0.0.1` 访问这台电脑的 `notify_url`。使用这组配置打开或完成沙箱收银台后，订单仍可能保持 `PENDING`，这是预期限制，不能手工改库伪装成回调成功。

1. **回调入口自动化测试**：`AliPayControllerNotifyTest` 每次生成临时 RSA2 密钥对，构造并签名与支付宝通知同结构的参数，验证签名失败、应用不符、商家不符、非法状态和两个成功状态。它会真实执行支付宝 SDK 的签名与验签代码，但不会调用支付宝网络，也不会使用 `.env` 中的密钥。
2. **支付事务真实 MySQL 测试**：`PaymentServiceIntegrationTest` 验证金额、订单状态、支付时间、支付宝交易号唯一性、串行/并发重复通知、冻结库存单次释放和中途异常回滚。这能证明收到可信通知后的数据库行为，但不能证明支付宝能够访问本机。
3. **支付表单边界检查**：配置当前沙箱 APPID、密钥和网关后，可以检查后端能否调用沙箱网关并生成支付表单。即使能够打开沙箱收银台，也不能把它当作完整支付成功，因为异步通知仍无法到达本机。
4. **同步跳转仅作界面检查**：`return_url` 最终由用户浏览器访问，因此同机测试时可以尝试指向本机后端；但它只验证浏览器跳转，不能修改订单支付状态，也不能替代异步通知。平台若拒绝本机地址，就等建立临时公网映射后再测。

项目还提供一个默认不运行的只读连通性探针。它只向 HTTPS 的支付宝官方 `alipaydev.com` 沙箱网关发送 `alipay.trade.query`，使用随机且不存在的订单号，并且只把 `40004 / ACQ.TRADE_NOT_EXIST` 视为通过；生产网关、第三方地址、HTTP、附带用户信息或查询参数的地址都会在网络请求前被拒绝。确认 `.env` 中的四项沙箱调用配置来自同一应用后，可在仓库根目录显式执行：

```powershell
docker run --rm --env-file back_end/.env -e RUN_REAL_ALIPAY_PROBE=true `
  -v "${PWD}\back_end\pom.xml:/workspace/pom.xml:ro" `
  -v "${PWD}\back_end\src:/workspace/src:ro" `
  -w /workspace maven:3.9.9-eclipse-temurin-17 `
  mvn -Dtest=AlipaySandboxConnectivityProbeIT test
```

这个命令不会创建交易、支付或退款，也不证明异步通知能够到达本机。它只证明当前 APPID、应用私钥、支付宝公钥和沙箱网关能够组成一条有效的已签名查询。探针控制台不应出现 APPID、签名、订单号、请求参数或响应正文；如果出现，停止继续测试并按安全缺陷处理。

不建议为了取得绿灯而手工修改数据库订单状态、直接调用支付服务方法冒充支付宝回调，或临时取消验签。也不建议把真实沙箱通知发送到不受信任的在线请求收集网站后复制回来，因为通知中包含订单号、交易号、签名和账户标识。

如果以后需要完整沙箱闭环，最低成本方案仍是为本机 8080 建立一个仅在测试期间有效的临时 HTTPS 隧道。它不等于长期部署：测试完成后关闭隧道并使临时域名失效即可。在此之前，应把验收结果写成“自动化支付逻辑通过，真实支付宝异步通知尚未验证”，不能写成“支付宝沙箱端到端支付已经通过”。

本项目作为个人项目和面试展示，不要求购买固定公网 IP，也不要求长期保存一个可访问的回调域名。PAY-003 只需要一次临时公网 HTTPS 验收：`notify_url` 必须让支付宝服务器能够访问；`return_url` 由付款浏览器访问，可以在同机测试时使用本机地址，也可以复用临时域名。SEC-001/PR #5 已完成精确订单路由和支付表单所有权；完整闭环当前只等待 PAY-002 同步返回状态确认和临时可访问的 `notify_url`。临时入口尽量只转发 `/api/orders/notify` 与 `/api/orders/returnUrl`，测试结束立即关闭。

## 不需要长期填写的变量

- `ARK_API_KEY`：AI assistant 已废弃，本机启动、测试和验收均不需要。
- `RUN_REAL_OSS_PROBE`、`RUN_REAL_OSS_PERMISSION_PROBE`：只由显式 OSS 探针脚本在当前测试进程中临时设置，不要写进 `.env`。
- `RUN_REAL_ALIPAY_PROBE`：只在人工确认当前网关是支付宝官方沙箱地址后，为上述只读查询命令临时设置；不要写进 `.env` 或 `.env.example`，默认测试不得启用。
- `FLYWAY_BASELINE_ON_MIGRATE=true`：不是常规配置，只允许在经过备份和结构核对的旧库首次接管时临时使用。

## 启动与检查

只启动数据库和 Redis：

```powershell
docker compose --env-file back_end/.env up -d db redis
```

如果后端 JAR 已构建，可在 `back_end/` 中使用现有脚本读取 `.env` 并启动后端。脚本不会输出环境变量值，端口已被占用时也不会结束原进程：

```powershell
.\scripts\Start-LocalBackend.ps1
```

若使用 `mvn spring-boot:run` 或 IDE 直接启动，注意 Spring Boot 2.7 本身不会自动读取 `.env`；必须在同一进程环境或 IDE Run Configuration 中导入这些变量。完整 Compose 使用 `--env-file` 时会自动读取。

检查配置时只确认“变量是否存在”和“服务是否可连接”，不要执行会把整份 `.env` 打印到终端的命令，也不要把包含环境变量的运行配置截图发到公开位置。真实支付宝沙箱验证应使用沙箱买家账号支付，并确认：异步通知返回 `success`、数据库订单状态变为 `PAID`、`paid_time` 与支付宝交易号已保存、冻结库存只释放一次。默认 Maven 测试不会发起真实交易。
