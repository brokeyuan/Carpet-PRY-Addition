# Changelog

All notable changes to **Carpet-PRY-Addition** are documented in this file.

## [1.1.6] - 2026-08-12

相较于 v1.1.5，本次更新包含 47 次提交，主要新增 3 个规则、2 个独立命令，并完成多版本兼容性重构与若干修复。

### 新增规则

- **`playerScaleModifiers`**：为 Player 注册 `minecraft:scale` 属性，新增统一三层子命令 `/scale set|reset|info`。支持四种模式：
  - `false`：关闭命令（默认）
  - `self`：所有人都只能调自己（无论 OP）
  - `true`：玩家仅可调自己，管理员可调任意玩家
  - `everyone`：所有人可调任意玩家
  - Tab 补全根据模式和权限动态过滤；范围受 `playerScaleMin`（默认 0.1）/ `playerScaleMax`（默认 10.0）限制；仅 1.21.5+ 版本支持
- **`fakePlayerDropStackModifiers`**：让假人持续丢出整组物品，任务在物品栏为空时仍保留，避免频繁重启

### 新增 / 重构命令

- **`/scale`**：玩家大小调节命令（结构：`set <value> [player]` / `reset [player]` / `info [player]`）
- **`/dropall`**：独立命令让假人清空物品栏（v3 重构，原 dropStack 路径）
- **`/picking`**：原 `/pickup` 重命名，保持命名一致性
- **`/riding`**：原 `/ride` 重命名以解决与原版 ride 命令冲突；同时移除 `RideCommandMixin` 恢复原版行为

### 修复

- **多版本兼容性**：
  - 1.21~1.21.4 不存在 `Attributes.SCALE`，通过 `//#if MC >= 12105` 预处理指令隔离，低版本提示不支持
  - 1.21.10 以下使用 `GameProfile.getName()`，1.21.11+ 使用 `name()`
  - 1.21.10 以下使用 `source.hasPermission(4)`，1.21.11+ 使用 `Commands.LEVEL_OWNERS.check`
  - 1.21.3+ 使用 `hurtServer(ServerLevel, DamageSource, float)`，1.21/1.21.1 保留 `hurt(DamageSource, float)`
- **命令树注册**：dropall 命令改为无条件注册并在执行时检查规则，避免规则切换后命令不可见
- **异常处理**：`ScaleCommand.applyScale` 中 `getPlayerOrException()` 用 try-catch 包裹，避免 `CommandSyntaxException` 向上传播
- **类型转换**：`Component` → `String` 使用 `.getString()`，修复 `ServerI18n.tr()` 返回类型不匹配
- **Mixin 注入**：`EntityPlayerActionPack.stopAll()` 改用 `CallbackInfoReturnable` 正确拦截返回值
- **预处理标记**：修正 `//$$` 标记方向，确保 main project 多版本预处理生效

### 重构

- **命令结构统一**：`/scale` 采用"动作 + 参数 + 可选目标"三层结构，避免参数定义重复
- **多版本隔离**：版本专属 `mixin.json` + 预处理指令组合，确保低版本不注册包含 `SCALE` 字段的 Mixin
- **RuleObserver**：规则切换时调用 `CommandHelper.notifyPlayersCommandsChanged` 动态刷新命令树，无需重登
- **Tab 补全策略**：根据规则模式（self / true / everyone）和玩家权限动态过滤可见玩家列表

### 文档

- 新增 `commands_en.md` 英文命令文档
- 重写 `README.md` / `README_en.md`：新增介绍、特性、安装、致谢章节，添加 Modrinth/CurseForge 徽章
- 添加 `Ivan-Carpet-Addition`（fakePlayerNameSuggestions）与 `Liuyue_awa / Carpet-Igny-Addition` 致谢
- 完善 `docs/Description.MD` 规则计数（16 rules）与 `FixBluemap` 条目

### CI

- 升级 GitHub Actions 依赖：
  - `gradle/actions/setup-gradle` v6 → v6.2.0
  - `actions/setup-python` v6 → v7
  - `softprops/action-gh-release` v2 → v3

---

## [1.1.5] - 2026-07-20

初始公开发布版本。
