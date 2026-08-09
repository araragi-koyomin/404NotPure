---
title: 项目文档与三层记忆治理
type: governance
layer: warm
status: active
created: 2026-08-09
updated: 2026-08-10
owners:
  - maintainers
tags:
  - documentation
  - lifecycle
  - tdd
---

# 项目文档与三层记忆治理

除仓库根目录的 `AGENTS.md` 外，开发过程中产生的 Markdown 文档必须位于 `docs/`，并带有 YAML Frontmatter。文档不是临时聊天记录，而是项目状态、决策、验收证据和风险的可维护来源。

项目从需求确认到合并归档的操作顺序以 [开发流程标准作业程序](DEVELOPMENT_SOP.md) 为准。本文件管理文档放在哪里、何时转层以及 Frontmatter 格式；SOP 管理一次开发工作按照什么顺序推进。两者同时生效。

## 三层记忆架构

### 热层：`docs/BACKLOG.md`

热层是唯一的活跃工作索引，只保留尚未完成的缺陷、需求、风险和验证任务。每个条目必须有稳定 ID、状态、优先级、目标、完成证据和对应温层文档。BACKLOG 还必须有“当前开发批次”，用常见语言说明当前目标、阶段、已完成、尚未完成、阻塞和下一步，避免项目所有者只能从多个工作项状态中猜测进度。

以下事件必须在同一次变更中同步更新 BACKLOG：

- 发现任何新 bug、缺陷或运行风险，即使尚未开始修复；
- 接受新需求、改变范围、拆分或合并工作项；
- 创建开发分支；
- 工作项被阻塞、解除阻塞或优先级变化；
- FIX/FEAT 完成并合并到项目所有者确认的集成分支；完成项必须从热层移除，并把证据归档到冷层，而不是留在 BACKLOG 里打勾。合并目标可能是 `main`，也可能是文档明确记录的课程集成分支，不能自行猜测。

### 温层：活跃文档

温层位于 `docs/plans/` 等非归档目录，保存仍在演进的范围、设计、TDD 测试清单、验收标准、风险和开放问题。BACKLOG 只保留摘要和链接，细节归温层。

`docs/DEVELOPMENT_SOP.md` 是持续生效的温层治理文档。它不会因为单个开发批次完成而归档；只有被新流程替代时，旧版本才按 `superseded` 进入冷层。

### 冷层：`docs/archive/`

冷层保存已完成或被替代的文档和交付证据。归档不等于删除；归档文档应把 `layer` 改为 `cold`，把 `status` 改为 `completed`、`superseded` 或 `cancelled`，并记录 `archived_at` 和最终证据。

## Frontmatter 最低字段

所有 `docs/**/*.md` 至少包含：

```yaml
---
title: 文档标题
type: backlog | plan | governance | report | decision
layer: hot | warm | cold
status: active | planned | blocked | completed | superseded | cancelled
created: YYYY-MM-DD
updated: YYYY-MM-DD
owners:
  - maintainers
tags:
  - example
---
```

冷层文档额外包含 `archived_at`。日期使用 ISO 8601；字段值不得包含密钥、密码、Token 或其他敏感配置。

## TDD 与证据规则

代码开发遵循 Red → Green → Refactor：

1. 先写能够因缺失行为而失败的测试，并确认失败原因与目标缺陷一致；
2. 编写让测试通过的最小实现；
3. 在测试持续通过的前提下重构；
4. 将执行命令、失败证据、通过证据和未覆盖风险写入对应温层文档；
5. 完成后将证据归档，并从 BACKLOG 删除该活跃项。

禁止测试剧场：不得只断言 Mock 调用次数来替代状态/结果验证，不得把生产实现复制进测试，不得用永远成功的断言充数，不得为了绿灯跳过并发、事务、金额精度、缓存失效或真实数据库语义。事务锁、原子更新和并发库存必须由真实数据库集成测试证明；支付宝、OSS 等外部边界应使用官方验签/客户端边界和可控测试替身，不调用真实支付或写入生产资源。

## 开发流程与文档生命周期的关系

- 接受需求、发现缺陷或改变范围：先更新 BACKLOG，再修改代码；
- 创建开发分支：在同一次变更中记录当前分支和目标集成分支；
- Red/Green/Refactor：过程证据写入对应温层计划；
- 验证或独立审查发现问题：立即更新 BACKLOG 状态、风险和下一步；
- 代码和测试完成但尚未合并：仍属于活跃工作，不得从 BACKLOG 移除；
- 合并成功：归档完成证据，从 BACKLOG 移除完成项，并切换“当前开发批次”。

纯文档改动、正常代码改动和外部服务验证使用不同强度的检查流程，具体规则见 [开发流程标准作业程序](DEVELOPMENT_SOP.md)。
