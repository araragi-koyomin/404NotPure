---
title: 订单、库存、支付与缓存一致性改造计划
type: plan
layer: warm
status: planned
created: 2026-08-09
updated: 2026-08-10
owners:
  - maintainers
tags:
  - order
  - inventory
  - payment
  - redis
  - tdd
related:
  - ORD-001
  - PAY-001
  - CACHE-001
  - TEST-001
---

# 订单、库存、支付与缓存一致性改造计划

## 目标与边界

在不破坏现有 `/api/cart/checkout`、`/api/orders/{orderId}/pay`、`/api/orders/notify` 和前端字段的前提下，让交易链路具备可由代码与测试证明的事务一致性、库存并发安全、支付幂等和 Cache-Aside 行为。

不引入微服务、消息队列或复杂分布式架构；已废弃的 assistant 完全排除在维护、测试和验收范围外；不虚构并发或性能数据。实现继续基于 Spring Boot 2.7.6、Java 17、JPA、MySQL 8 和 Redis 6。

## ORD-001：订单与库存

必须实现：

- `OrderServiceImpl.addOrder` 建立覆盖用户校验、商品读取、库存冻结、订单和订单项保存的事务边界；
- 购买数量在 Controller Bean Validation 和服务层边界均校验为正整数；
- 同一请求重复商品先聚合或以等价方式按总量校验；
- 根据现有表结构选择悲观锁、乐观锁或带条件的原子更新，优先使用易验证、低复杂度的数据库方案；
- 并发成功冻结量不得超过可用库存；
- 库存不足时不得创建订单，中途异常时库存、订单和订单项全部回滚；
- 低成本可行时增加重复请求幂等键，但不得用进程内 Map 冒充跨进程幂等。

TDD 顺序：

1. 非法数量失败测试；
2. 正常下单状态与金额测试；
3. 库存不足且订单不存在测试；
4. 保存中途异常后的事务回滚集成测试；
5. 两个以上事务并发争用库存的真实 MySQL 测试；
6. 最小实现与重构。

## PAY-001：支付宝回调

必须实现：

- 保留支付宝异步回调签名校验；
- 校验 `out_trade_no` 格式、订单存在性和支持的 `trade_status`；
- 用 `BigDecimal.compareTo` 校验通知 `total_amount` 与订单应付金额；
- 状态只能从 `PENDING` 合法进入 `PAID`；已 `PAID` 的重复通知幂等成功，其他状态拒绝；
- 状态更新和冻结库存释放在同一事务内，并对并发重复回调加数据库级保护；
- 支付成功只执行 `frozen -= quantity`，不再次扣减可用库存；
- 增加独立支付完成时间，不能覆盖 `createTime`；
- 明确支付宝交易号的保存位置和唯一性；若沿用 `PaymentInfo`，需把其包结构、Repository 和关联关系整理清楚。

TDD 顺序：签名失败、订单号无效、金额不一致、非法状态、首次成功、串行重复、并发重复、库存释放中异常回滚。

## CACHE-001：Redis Cache-Aside

当前实际 key 为 `advertisement:product:{productId}`，TTL 随机 1800～3599 秒。改造必须保持广告位和商品详情前端行为兼容，并完成：

- 集中定义稳定 key；若迁移为通用商品 key，所有读写方同批切换或提供兼容读取；
- 商品详情缓存未命中时查库并回填；
- 保留随机 TTL，测试只断言范围；
- 以短 TTL 空值标记或等价方案防缓存穿透；
- 商品更新、删除提交成功后失效缓存；
- 广告更新更换商品时同时失效旧商品 key；
- `AdvertisementsServiceImpl` 和 `ProductServiceImpl` 使用 `RedisTemplate<String, Object>`，不再使用没有泛型参数的原始类型；
- 缓存读取先检查对象实际类型，再转换为 `ProductDTO`。遇到旧数据、错误类型或无法反序列化的数据时，应删除该缓存并从数据库恢复，不能直接强制转换导致商品接口失败；
- 当前项目通过 `RedisTemplate` 使用 Redis，没有定义 Spring Data Redis Repository。应在上下文测试证明 JPA Repository 仍能正常加载后，明确关闭 Redis Repository 自动扫描，避免每次启动进行无意义的 Repository 类型判断；
- `mvn compile` 不再出现由原始 RedisTemplate 引起的 unchecked 类型警告，应用启动不再出现未使用 Redis Repository 扫描产生的提示；
- 缓存故障不得破坏数据库主链路的正确性；
- 不声称没有测量依据的性能提升。

TDD 顺序：缓存命中、未命中回填、数据库不存在空值、错误缓存类型恢复、更新失效、删除失效、广告换品、Redis 异常降级、Spring 上下文中的 JPA/Redis Repository 边界。真实 Redis 测试还要验证序列化后的类型、TTL 范围和失效行为，不能只用 Mock 证明方法被调用。

## 测试真实性要求

- Service 单元测试验证业务结果和状态，不只验证 Repository 调用次数；
- 事务回滚、锁、条件更新和并发库存必须运行真实 MySQL 集成测试；
- Redis 序列化、TTL 和失效必须运行真实 Redis 集成测试；
- 支付回调业务与 SDK 验签边界分离，金额和状态测试不依赖真实支付宝；
- 每个缺陷先保存失败测试证据，再实现绿灯；没有看到正确失败的测试不算完成 Red 阶段。

## 完成标准

- `mvn compile`、`mvn test` 和 MySQL/Redis 集成测试通过；
- `git diff --check` 通过；
- 接口字段和前端构建兼容；
- BACKLOG 中 ORD-001、PAY-001、CACHE-001、TEST-001 在 merge 到 main 后移除；
- 实现与测试证据归档到 `docs/archive/`。
