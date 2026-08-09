---
title: 安全边界、测试、数据库与前端质量计划
type: plan
layer: warm
status: active
created: 2026-08-09
updated: 2026-08-10
owners:
  - maintainers
tags:
  - security
  - database
  - testing
  - jpa
  - api
  - frontend
  - tdd
related:
  - SEC-001
  - TEST-002
  - DB-001
  - JPA-001
  - API-001
  - FE-001
---

# 安全边界、测试、数据库与前端质量计划

## SEC-001：认证与授权

已发现缺陷：

- Spring Security 配置为 `anyRequest().permitAll()`，授权依赖 MVC 拦截器和控制器手工调用，边界分散；
- 拦截器放行 `/api/orders`、`/api/cart`、`/api/products` 等前缀，不能证明子接口均安全；
- 历史缺陷：注册请求曾允许提交 `role`，存在普通用户自注册管理员的风险；本轮已改为服务端固定创建普通用户，资料更新也保留数据库中的既有角色。该项完成证据将在本批次合并时转入冷层；
- 购物车更新/删除未验证条目属于当前用户；
- 支付表单未验证订单属于当前用户；
- JWT 签名密钥硬编码；Cookie 生命周期与 JWT 生命周期不一致；CSRF 关闭；
- `LoginInterceptor` 只读取 Cookie，而 `TokenUtil` 和 Axios 还支持 `token` 请求头；仅携带请求头的 API 客户端会在进入控制器前被拒绝，认证来源和行为不一致；
- 前端此前会把登录/更新对象输出到 console；该日志已移除并归入运行恢复归档。

改造必须先写权限矩阵测试，再统一 Security/拦截器职责。测试覆盖未登录、普通用户、管理员、资源所有者和跨用户访问，不能只断言 `validateAdminRole` 被调用。

## DB-001：版本化迁移

当前使用 `spring.jpa.hibernate.ddl-auto=update`，没有 Flyway/Liquibase。后续订单、库存和支付改造涉及支付时间、交易号、库存唯一约束或版本字段时，必须引入可重复的版本化迁移，并验证：

- 空数据库从零迁移；
- 现有数据库从当前 schema 升级；
- 重复运行不破坏数据；
- 唯一约束上线前检测和处理历史重复数据。

## TEST-002：恢复 Maven 默认测试进程隔离

Surefire 是 Maven 运行 JUnit 测试的组件。它默认会启动独立的 Java 测试进程，使测试使用的内存、系统属性和退出行为与 Maven 主进程隔离。项目曾在这种独立进程中出现原生内存不足，随后使用 `-DforkCount=0` 让测试直接运行在 Maven 主进程中：OSS 安全批次完成时为 52 项，ORD-001 当前完整回归为 79 项。这个替代方式证明了测试内容能通过，但不能永久代替正常的测试进程隔离。

处理顺序：

1. 使用占位 OSS 配置和本机测试数据库/Redis 执行不带 `-DforkCount=0` 的 `mvn test`，保存失败类型、Java 版本、可用内存和测试进程数量；不得记录任何真实凭据值。
2. 区分操作系统可用内存不足、Java 堆设置不合理、重复启动 Spring 上下文或测试并发过高，不能在没有证据时只增加内存参数。
3. 先用最小测试集合复现，再调整 Surefire 或测试上下文复用方式；不能通过跳过测试、移除真实事务测试或把所有测试永久塞进 Maven 主进程来取得绿灯。
4. 最终使用仓库约定的默认命令连续执行两次，确认结果可重复。

完成标准：不添加 `-DforkCount=0` 的 `mvn test` 连续两次通过；默认测试不连接真实支付宝或 OSS；测试报告仍包含全部应执行的单元测试和集成测试；文档记录本机或 CI 所需的最低合理内存和实际使用的 Surefire 设置。

## JPA-001：关闭 Open Session in View 前明确数据读取边界

Open Session in View 是 Spring Boot 默认开启的一种 JPA 行为：HTTP 请求生成响应期间，数据库会话仍保持打开，因此控制器或 JSON 序列化阶段也可能临时读取尚未加载的关联数据。它能暂时掩盖服务层没有完整读取数据的问题，也可能把数据库查询推迟到响应生成阶段。当前商品规格、商品详情图和广告关联使用延迟加载，所以不能直接关闭该选项后假设接口仍然兼容。

处理顺序：

1. 为商品详情、广告详情、订单详情和订单列表接口补充接口级测试，确认响应字段和查询结果。
2. 在 Repository 查询或带事务的服务方法中明确读取响应所需的关联数据，避免控制器和 JSON 序列化阶段临时访问数据库。
3. 设置 `spring.jpa.open-in-view=false`，运行接口测试、完整 Maven 测试和实际本机启动检查。
4. 如果调整查询方式，要检查是否产生明显的重复查询；没有 SQL 统计前不得声称性能提高。

完成标准：`spring.jpa.open-in-view=false` 生效；商品、广告和订单相关接口没有延迟加载异常；响应字段保持兼容；服务层事务结束前已经准备好响应所需的数据。

## SEC-002：敏感请求头日志

外部依赖探针发现，OSS SDK/Apache HTTP 在 DEBUG 级别会输出完整 `Authorization` 请求头，其中包含 AccessKey ID 和请求签名。AccessKey Secret 未直接输出，但任何凭据材料进入日志都不可接受。

修复要求：

- 轮换本次验证使用的 OSS AccessKey；
- 生产和测试日志配置显式禁止 `org.apache.http.headers`、`org.apache.http.wire` 等敏感网络日志；
- 日志过滤器对 `Authorization`、Cookie、token、签名和 AccessKey 字段做结构化脱敏；
- 添加捕获日志的自动化测试，断言敏感标记和探针凭据均不存在；
- 测试不得把真实凭据写入 fixture、快照或失败消息。

2026-08-09 初始实现证据：`application.yml` 已显式将 `org.apache.http.headers`、`org.apache.http.wire` 设为 OFF，并把 `com.aliyun.oss` 限制为 WARN；自动化测试只从 Spring 配置断言两个敏感网络 logger 的有效级别为 OFF。真实应用 PutObject 日志当时未发现 Authorization、签名、AccessKey 或 token。

随后发现该测试覆盖不足：显式 `OssLifecycleProbeIT` 直接由 Surefire 运行且不启动 Spring，测试运行时沿用 Logback 默认 DEBUG，Apache HTTP headers/wire 日志输出了 AccessKey ID、Authorization 请求签名、Bucket/Endpoint 和请求内容。自动生命周期本身成功并删除对象，但凭据材料已经进入会话输出。SEC-002 重新标记为阻塞，处置顺序为：立即停用并轮换该 AccessKey；先写不启动 Spring 的日志级别红测；为测试运行时增加独立安全日志配置；确认本地测试日志无敏感字段；最后才允许用新凭据复验。不得再次使用已暴露凭据。

本地处置进展：新增 `OssProbeLoggingTest`，修复前准确失败为 headers/wire 有效级别 DEBUG，并捕获到仅用于测试的伪 Authorization/token；新增 `src/test/resources/logback-test.xml`，在 Spring 启动之前即关闭两个敏感 logger，并将其余 Apache HTTP 与 OSS logger 限制为 WARN。修复后原生 Surefire 与 Spring 配置测试 2/2 通过；使用不可用 OSS 占位配置的默认完整测试 30/30 通过。持有旧凭据的 8080 后端进程已经停止。

项目所有者已于 2026-08-09 确认停用旧 AccessKey、创建新 Key 并更新忽略的本地配置。新凭据显式生命周期探针 1/1 通过，输出未出现 Authorization、签名、AccessKey、Endpoint、Bucket 或对象名，验证对象在 `finally` 清理后不可读且无残留。新构建后端在 8080 启动后，直连和 5173 代理商品接口均为 HTTP 200，启动日志未发现敏感标记。SEC-002 的本批次目标已经完成并转入冷层；不得因本次通过而降低后续日志配置或把真实凭据写入测试 fixture。

## SEC-004：测试日志中的临时开发口令

默认 Spring Boot 测试上下文会自动生成开发安全口令并以 WARN 写入构建日志。该值不是 `.env`、JWT、OSS 或支付配置，也只对当前临时上下文有效，但凭据类信息仍不应出现在 CI/会话输出。

2026-08-09 Red 阶段在不启动 Spring 的原生日志测试中模拟同一 logger，确认其有效级别继承为 INFO 且占位消息会进入 appender；Green 阶段仅在 `logback-test.xml` 将 `UserDetailsServiceAutoConfiguration` logger 设为 OFF。Green 后目标日志测试 2/2、默认完整测试 31/31 通过，完整输出不再出现生成口令提示。该设置只影响测试，不用于掩盖生产环境缺失的认证配置；生产认证边界仍由 SEC-001 跟踪。SEC-004 的本批次目标已经完成并转入冷层。

## SEC-003：匿名图片上传

`LoginInterceptor` 当前明确放行 `POST /api/images`，而控制器会使用服务器持有的 OSS 凭据执行上传。任何能访问后端的人都可能借此消耗存储和公网流量，且当前没有可靠的文件大小、实际 MIME 内容或对象命名限制。

兼容性冲突：注册页当前会在账号创建之前调用同一个 `/api/images` 上传可选头像，而 `Account.avatar` 允许为空，登录后的个人中心已提供头像上传与账号更新。推荐移除注册前匿名上传，注册页改为提示登录后设置头像；不得为了保留可选头像而继续公开通用 OSS 写入口。

修复必须先写失败测试，并至少做到：

- 未登录请求不能上传；
- 注册页不再在认证前调用 `/api/images`，且无头像注册仍可成功；
- 根据头像、商品图和广告图的实际业务边界确定普通用户与管理员权限；
- 在控制器和服务边界限制大小、扩展名与实际文件类型；
- object key 由服务端生成，不能信任原始文件名；
- OSS 异常响应和应用日志不得包含凭据、签名或本地文件内容。

2026-08-09 TDD 证据：首轮 9 个目标测试中 8 个按预期失败，证明匿名放行、敏感 logger、原文件名和缺少文件校验等缺陷可复现；实现最小修复后 9/9 通过。第二轮新增用途权限、分类前缀、Endpoint、元数据和空配置测试，12 个目标测试中 6 个按预期失败；实现后 12/12 通过。完整后端测试为 17/17，前端 `vue-tsc && vite build` 成功。

当前实现：注册页移除头像上传并提示登录后在个人主页设置；所有前端调用显式传入图片用途；未登录图片请求业务码 401；普通用户可上传头像，但商品和广告图片要求管理员；服务端生成按用途隔离的 UUID key，并在调用 OSS 前拒绝空文件、超过 10 MiB、声明类型不匹配、签名不支持或不可解码的内容。历史上曾由 ImageIO 在像素数检查前完整解析图片；本轮已改为先读取图片尺寸并拒绝像素数超限的内容，再执行完整解码。该项完成证据将在本批次合并时转入冷层。

## API-001：订单读接口契约

前端 `front_end/src/api/order.ts` 已调用订单详情和当前用户订单列表 GET 接口，后端当前没有对应 Controller。实现时保持现有 `Order` 字段兼容并验证订单所有权，管理员列表与普通用户列表不得混用。

## FE-001：前端构建质量

当前 `npm run build` 成功但仍有：

- `../../assets/pexels-padrinan-19670.jpg` 无法在构建期解析；
- CSS 使用 `//` 注释导致压缩警告；
- 主 chunk 约 987 KB，超过 500 KB 警告阈值。

应先增加构建/关键路由烟雾测试，再修复资源引用和 CSS；chunk 拆分需基于路由与依赖边界，不得只调高 warning limit 掩盖问题。
