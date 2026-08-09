---
title: 个人仓库接管与 Git 分支迁移计划
type: plan
layer: warm
status: active
created: 2026-08-10
updated: 2026-08-10
owners:
  - araragi-koyomin
tags:
  - git
  - github
  - repository-transition
  - branch-management
related:
  - GIT-001
---

# 个人仓库接管与 Git 分支迁移计划

## 背景与决定

原仓库 `sharkdingo/404NotPure` 是多人课程项目，当前维护者以 GitHub 账号 `araragi-koyomin` 独立继续改进。当前账号对原仓库有写权限，但不是仓库所有者。为避免个人改进继续直接影响原多人仓库，同时保留原项目来源和提交作者信息，本次选择创建 GitHub Fork，而不是新建没有来源关系的空仓库。

原仓库的 `origin/main` 与当前有效代码分支 `lab4` 没有共同祖先：`origin/main` 属于另一段重新整理过的历史，不能通过普通合并安全地成为当前项目主线。当前能够编译、测试并完成本轮安全整改的有效基线是本地 `lab4` 提交 `093a6c9e`。因此新的个人主线从该提交建立，不合并原 `main` 的无关历史。

## 目标远端结构

- `origin`：个人 Fork `araragi-koyomin/404NotPure`，用于推送个人主线和功能/修复分支。
- `upstream`：原多人仓库 `sharkdingo/404NotPure`，只用于追溯历史，不在本次迁移中写入。
- 个人 Fork 默认分支：`master`。

## 目标分支结构

- `master`：从已提交的 `lab4` 基线 `093a6c9e` 建立，不包含当前工作区尚未提交的整改内容。
- `fix/oss-runtime-security-hardening`：由当前本地 `lab4` 重命名，承载 OSS、图片上传、账户注册安全、本机运行环境、Docker/Redis 安全和配套文档改动。
- 后续分支命名：功能使用 `feat/<简短主题>`，缺陷和安全修复使用 `fix/<简短主题>`，文档或流程改进可使用 `docs/<简短主题>` 或与对应功能分支一并提交。

## 实施顺序

1. 在 BACKLOG 登记 GIT-001 和本计划，之后才创建远端或分支。
2. 创建个人 GitHub Fork；将原 `origin` 重命名为 `upstream`，再把个人 Fork 添加为新的 `origin`。
3. 在提交 `093a6c9e` 创建 `master`，将当前 `lab4` 重命名为 `fix/oss-runtime-security-hardening`。
4. 更新 BACKLOG 中的实际远端和分支状态。
5. 显式暂存本轮项目文件，检查暂存清单、敏感信息和 `git diff --cached --check`。
6. 在 fix 分支创建一个可审查提交，推送 `master` 和 fix 分支到个人 Fork，并把个人 Fork 默认分支设置为 `master`。
7. 本次不自动合并 fix 分支；后续通过 Pull Request 审查并合并到 `master`。完成 merge 后再将 GIT-001 和本轮已完成工作从 BACKLOG 移入冷层归档。

## 明确排除

- 不向原多人仓库推送新提交或分支。
- 不合并、变基或强制拼接没有共同祖先的 `origin/main` 与 `lab4`。
- 不提交 `back_end/.env`、密钥、密码、Token、本地 Maven 工具、构建产物、IDE 文件或日志。
- 不暂存现有用户文件 `面试回答指南.md`，除非项目所有者以后单独明确要求。
- 不执行 `git reset`、`git checkout --`、强制推送或删除用户分支。

## 验收标准

- GitHub 上存在归属于 `araragi-koyomin` 的 Fork，原仓库关系可追溯。
- 本地 `origin` 指向个人 Fork，`upstream` 指向原多人仓库，输出中不包含任何凭据。
- `master` 指向 `093a6c9e`，fix 分支包含本轮提交，二者均已推送到个人 Fork。
- 个人 Fork 的默认分支是 `master`。
- 暂存和提交清单不包含敏感配置、本地工具、构建产物或 `面试回答指南.md`。
- BACKLOG 准确记录分支创建、推送状态以及尚未执行的 Pull Request/merge/归档工作。

## 2026-08-10 实施记录

- GitHub Fork `araragi-koyomin/404NotPure` 已创建，并确认父仓库为 `sharkdingo/404NotPure`。
- 原本地 `origin` 已重命名为 `upstream`，fetch 仍指向原多人仓库，push URL 已设置为 `DISABLED`，防止误推。
- 新 `origin` 已指向个人 Fork。
- `master` 已从提交 `093a6c9e` 创建；原当前分支 `lab4` 已重命名为 `fix/oss-runtime-security-hardening`，工作区修改和未跟踪文件保持原样。
- 暂存清单共 68 个项目文件；`.env`、本地工具、构建产物和 `面试回答指南.md` 未进入暂存区，常见私钥/Token 格式扫描和 `git diff --cached --check` 均通过。
- fix 主提交为 `23df7b9f`（`fix: harden runtime, account registration, and OSS uploads`）。
- `master` 和 `fix/oss-runtime-security-hardening` 均已推送到个人 Fork，GitHub 默认分支已设置为 `master`。
- 当前尚未创建 Pull Request，也未合并或归档。GIT-001 与本轮工作继续保留在 BACKLOG，直到 merge 后再转入冷层。
