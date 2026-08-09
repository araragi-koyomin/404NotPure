---
title: 2026-08-09 支付宝与 OSS 只读验证报告
type: report
layer: cold
status: completed
created: 2026-08-09
updated: 2026-08-09
archived_at: 2026-08-09
owners:
  - maintainers
tags:
  - alipay
  - oss
  - security
  - external-dependency
---

# 2026-08-09 支付宝与 OSS 只读验证报告

## 范围

在用户授权下，使用现有环境配置进行只读验证。未创建支付宝交易，未发起付款或退款，未上传、覆盖或删除 OSS 对象。报告不记录 endpoint、Bucket 名、appId、AccessKey、密钥、签名、Token 或请求体。

## 支付宝结果

- 应用私钥：RSA PKCS#8 解析成功；
- 支付宝公钥：RSA X.509 解析成功；
- 网关：只读 `alipay.trade.query` 请求成功到达；
- 返回：`40004 / ACQ.TRADE_NOT_EXIST`，查询的是确定不存在的探针订单号；
- 结论：当前 appId、应用私钥签名、支付宝公钥配置和网关连通性可以完成签名查询。真实付款、异步通知公网可达性、回调签名和金额校验仍必须由沙箱/测试交易另行验证。

## OSS 结果

- SDK 客户端初始化成功；
- 请求到达配置的 Bucket；
- 供应商返回 `BucketDisable`；
- 结论：当前 Bucket 已停用，图片上传链路不可用。无法把此次结果解释为 AccessKey 全面有效，也未进行任何写入测试。

后续活跃缺陷：OSS-001，恢复或迁移 Bucket 后重新执行安全只读验证和隔离前缀写入闭环。

## 安全事件

OSS SDK/Apache HTTP 默认 DEBUG 日志意外输出了 `Authorization` 请求头，包含 AccessKey ID 和一次性请求签名；AccessKey Secret 未直接输出。该输出违反项目敏感信息约束。

已采取措施：

- 后续文档和报告只保留脱敏错误码；
- 未把任何凭据值写入仓库文件；
- 登记 SEC-002，要求增加日志级别约束、结构化脱敏和自动化日志测试；
- 建议立即轮换相关 OSS AccessKey。

## BACKLOG 生命周期

- EXT-001 验证任务已完成并从热层移除；
- 新增 OSS-001：Bucket 停用；
- 新增 SEC-002：敏感 Authorization 日志；
- 支付宝交易链路业务改造继续由 PAY-001 跟踪。
