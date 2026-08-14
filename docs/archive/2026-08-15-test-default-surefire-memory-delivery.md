---
title: TEST-002 默认 Surefire 测试进程与内存基线交付记录
type: report
layer: cold
status: completed
created: 2026-08-15
updated: 2026-08-15
archived_at: 2026-08-15
owners:
  - maintainers
tags:
  - testing
  - maven
  - surefire
  - java
  - docker
  - memory
  - tdd
related:
  - TEST-002
---

# TEST-002 默认 Surefire 测试进程与内存基线交付记录

## 交付结果

TEST-002 已于 2026-08-15 通过 [Pull Request #17](https://github.com/araragi-koyomin/404NotPure/pull/17) 以 squash 方式合并到个人仓库 `master`，合并提交为 `73f73836`。功能分支为 `codex/test-default-surefire-memory`，合并后的文档归档分支为 `codex/archive-test002`。

本次交付解决的是测试运行方式和资源证据问题，不改变商城业务逻辑。项目曾在 Surefire 独立 Java 测试进程中出现原生内存不足，部分旧批次使用 `-DforkCount=0` 把测试放进 Maven 主进程；这只能证明当时测试内容能通过，不能证明正常的独立测试进程在明确资源限制下稳定运行。

## 实现与安全边界

- `pom.xml` 显式固定 Maven Surefire Plugin 3.2.5、`forkCount=1`、`reuseForks=true`。这表示 Maven 使用一个独立 Java 测试进程，并在所有测试类之间复用它。
- `MavenSurefireConfigurationTest` 读取项目 POM，防止以后意外删除插件版本或把默认配置改回不明确状态。XML 解析关闭外部实体和文档类型声明，不读取网络资源。
- `Invoke-DefaultTestMemoryAcceptance.ps1` 只允许 1 GiB、1.5 GiB、2 GiB 三个预先定义的容器总内存档位，并把交换内存限制为同一数值、CPU 限制为 2 核。
- 脚本从被 Git 忽略的 `back_end/.env` 取得本机数据库登录信息，但不输出变量值；真实 OSS 生命周期、OSS 权限和支付宝沙箱探针全部强制关闭，外部服务配置由占位值覆盖。
- 每轮只删除 `target/surefire-reports` 下旧的 `TEST-*.xml` 生成物，避免把旧报告误判为本次成功；不删除源码、MySQL/Redis 容器、镜像、数据卷或用户文件。
- 容器启动失败、测试退出失败、容器内存不足、整机可用内存跌破安全线、没有新报告、测试数为 0 或没有观察到至少两个 Java 进程时，脚本都会拒绝验收。

## TDD 与自查修复

Red 阶段的配置测试准确失败为 `pom.xml` 没有显式配置 `maven-surefire-plugin`。Green 阶段固定 Surefire 3.2.5、`forkCount=1`、`reuseForks=true`。第一次 Green 运行发现测试错误地在插件根节点读取 `forkCount`，随后修正为读取 `<configuration>`，目标测试通过。

测量脚本在开发和自查中继续发现并修复两类可能造成错误结论的问题：

1. `docker top` 只请求进程名称时，Docker Desktop 要求同时包含 PID，导致进程数量观测无效；修复后使用 `pid,comm`，并让运行中观测失败直接终止验收。
2. 容器创建成功但启动失败时，脚本原本没有检查启动退出码，并可能汇总旧的 XML 报告；修复后每轮先清除旧 XML，检查启动结果，并要求本轮产生非空报告和至少两个 Java 进程。

这些修复之后重新执行了完整 1 GiB 默认测试，不沿用修复前的进程观察结论。

## 内存档位与测试结果

所有测试使用固定的 Maven 3.9.9、Eclipse Temurin Java 17、Surefire 3.2.5、健康的 MySQL 8 与 Redis 6。新增配置测试后，当前完整默认测试为 41 个测试类、246 项。

| 容器总内存上限 | Maven 命令 | 结果 | 容器采样峰值 |
|---|---|---:|---:|
| 2 GiB | `mvn clean test` | 246/246 | 约 814.7 MiB |
| 1.5 GiB | `mvn clean test` | 246/246 | 约 728.7 MiB |
| 1 GiB | `mvn clean test` | 246/246 | 约 726.0 MiB |
| 1 GiB | 第一轮 `mvn test` | 246/246 | 约 513.7 MiB |
| 1 GiB | 第二轮 `mvn test` | 246/246 | 约 514.2 MiB |
| 1 GiB | 脚本最终修复后 `mvn test` | 246/246 | 约 518.9 MiB |

所有有效结果均为 0 失败、0 错误、0 跳过，没有容器内存不足或整机安全停止。最终有效轮开始时整机可用内存约 7.59 GiB，运行期最低约 5.21 GiB；最多同时观察到 2 个 Java 进程，证明 Maven 主进程与 Surefire 独立测试进程共同处于 1 GiB 容器硬上限内。

另外执行并通过：

- 单独的 Maven 编译；
- Maven 有效 POM 检查，确认 Surefire 3.2.5、`forkCount=1`、`reuseForks=true` 实际生效；
- PowerShell 语法检查；
- 全部 `docs/**/*.md` YAML Frontmatter 和内部链接检查；
- 敏感凭据特征扫描；
- `git diff --check`。

默认 Surefire 报告不包含 `OssLifecycleProbeIT`、`OssBusinessDeletePermissionIT` 或支付宝沙箱外部探针。

## 可以陈述和不能陈述的结论

可以陈述：当前 41 个测试类、246 项默认测试在固定 Maven 3.9.9、Java 17、Surefire 3.2.5 和本机 Docker Desktop 环境中，使用一个可复用独立测试进程，在 1 GiB 容器总内存硬上限下连续两轮通过。

不能陈述：项目理论上最低只需要 1 GiB、所有电脑和 CI 都能在同样资源下运行、未来测试数量增长后仍永久满足 1 GiB，或该结果代表应用生产运行内存。低于 1 GiB 的候选档位没有测试；项目仍未建立远端 CI。

## 后续方向

TEST-002 完成后，下一项首选代码任务是 DB-001B：使用新 Flyway 迁移为 `stockpile.product_id` 增加数据库唯一约束，并在发现历史重复库存时拒绝自动合并或删除。遗留 `PaymentInfo/payment_info` 清理由范围独立的 DB-001C 后续处理。
