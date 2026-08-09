---
title: AI assistant 功能废弃决策
type: decision
layer: cold
status: cancelled
created: 2026-08-09
updated: 2026-08-09
archived_at: 2026-08-09
owners:
  - maintainers
tags:
  - assistant
  - deprecation
  - scope
---

# AI assistant 功能废弃决策

## 决策

项目所有者确认现有 AI assistant 是已废弃的 feature。本轮及后续商城交易链路工作不维护、不修复、不扩展该功能，也不为其补测试或配置外部服务。

## 范围影响

- `ARK_API_KEY` 不再是项目启动、构建、测试或验收的必需配置；
- `/api/assistant/chat` 是否可用不影响商城主链路的运行结论；
- assistant 故障不得进入活跃 BACKLOG，也不得阻塞订单、库存、支付、缓存、认证和对象存储工作；
- 当前遗留的控制器、服务、前端入口和 Ark SDK 暂不在交易改造中删除，避免混入无关兼容变化；
- 若未来决定物理删除，应建立独立清理任务，同时移除后端接口、前端入口、依赖和配置引用，并执行编译及前端构建验证。

## 状态处理

原活跃项 `OPS-003` 已取消并从热层 `docs/BACKLOG.md` 移除。本文件作为冷层决策证据保留，归档不等于删除。
