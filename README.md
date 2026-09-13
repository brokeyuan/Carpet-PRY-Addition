# Carpet-PRY-Addition

[![License](https://img.shields.io/badge/license-LGPL--3.0-blue)](https://choosealicense.com/licenses/lgpl-3.0/)
[![Modrinth](https://img.shields.io/modrinth/dt/carpet-pry-addition?color=00AF5C&label=Modrinth%20downloads&logo=modrinth)](https://modrinth.com/mod/carpet-pry-addition)
[![CurseForge](https://img.shields.io/curseforge/dt/1619008?logo=curseforge&label=CurseForge%20downloads&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/carpet-primaryuan-addition)
[![MC Versions](https://img.shields.io/badge/MC-1.21%20~%2026.2-blue)](https://github.com/brokeyuan/Carpet-Primaryuan-Addition)
[![Github](https://img.shields.io/github/downloads/brokeyuan/Carpet-Primaryuan-Addition/total?color=161616&label=Github%20downloads&logo=github)](https://github.com/brokeyuan/Carpet-Primaryuan-Addition/releases)
[![QQGroup:450108190](https://img.shields.io/badge/Chat-QQGroup-12B7F5?style=flat&logo=qq&logoColor=white)](https://qm.qq.com/q/Ez582Z5P0c)

**中文** | [English](README_en.md)

## 简介

**Carpet-PRY-Addition** 是一个基于 [Fabric Carpet](https://github.com/gnembon/fabric-carpet) 的服务端扩展模组，主要为 PRY 服务器（Primaryuan Server）开发，在客户端安装时能增加使用体验。新增 **24 条**可配置 Carpet 规则和 **7 个**命令，涵盖假人管理增强、玩家缩放、服务器管理、模组兼容性修复、功能移植、玩家交互和生存特性扩展。

除 `ridingPlayersClientAllowInteractions` 客户端规则默认开启外，其余规则默认关闭，按需启用。

## 文档

- [规则](docs/rules.md) | [Rules](docs/rules_en.md)
- [命令](docs/commands.md) | [Commands](docs/commands_en.md)

## 下载

- [GitHub Release](https://github.com/brokeyuan/Carpet-Primaryuan-Addition/releases/latest)
- [Modrinth](https://modrinth.com/mod/carpet-primaryuan-addition)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/carpet-primaryuan-addition)

## 安装

1. 确保服务器已安装 **Fabric Loader >= 0.16.0**
2. 安装必需前置：**[Fabric Carpet](https://modrinth.com/mod/carpet)** + **[Fabric API](https://fabricmc.net/)**
3. 可选前置：[skinrestorer](https://modrinth.com/mod/skinrestorer)（仅假人皮肤功能需要）
4. 将 mod JAR 文件放入服务器的 `mods/` 文件夹
5. 本模组为**服务端模组**，玩家无需安装；客户端可选安装，提供两项客户端功能——`ridingPlayersClientAllowInteractions`（头上有乘客时仍可交互）与玩家缩放的视野（FOV）补偿
6. 规则默认关闭（`ridingPlayersClientAllowInteractions` 除外），使用 `/carpet` 命令或配置文件按需启用

## 依赖

| 名称 | 类型 | 链接 |
|------|------|------|
| Carpet | 必须 | [Modrinth](https://modrinth.com/mod/carpet) · [MC百科](https://www.mcmod.cn/class/2361.html) |
| Fabric API | 必须 | [官方](https://fabricmc.net/) · [MC百科](https://www.mcmod.cn/class/3124.html) |
| skinrestorer | 可选 | [Modrinth](https://modrinth.com/mod/skinrestorer) |

## 版本支持

| 游戏版本 | 开发状态 |
|----------|----------|
| 1.21 | 维护中 |
| 1.21.1 | 维护中 |
| 1.21.3 | 维护中 |
| 1.21.4 | 维护中 |
| 1.21.5 | 维护中 |
| 1.21.8 | 维护中 |
| 1.21.10 | 维护中 |
| 1.21.11（主版本） | 维护中 |
| 26.1.2 | 维护中 |
| 26.2 | 维护中 |


## 主要功能特性

### 漏洞修复

- `FixXaeroLib`：修复 Xaero 地图 + LuckPerms 导致假人数据丢失的问题
- `FixBluemap`：修复假人不触发 Fabric API 连接事件导致 BlueMap 等模组追踪异常

### 移植规则

- `fakePlayerNameSuggestions`：自定义 `/player` 命令的补全建议（移植自 Ivan-Carpet-Addition）
- `unicodeArgumentsSupport`：允许命令参数使用非 ASCII 字符，可召唤中文名假人（移植自 YACA）

### 假人增强

- `TppFakePlayer`：假人珍珠传送站，附 `/tpp` / `/tppset` 命令
- `fakePlayerSkinMode` / `fakePlayerSkinSet`：假人皮肤模式（默认 / 召唤时 / 统一皮肤）与统一皮肤玩家名
- `fakePlayerDropStackModifiers`：`/player dropall` 按设定节奏持续丢出假人背包物品
- `fakePlayerSendto`：`/player sendto` 建立假人间单向背包物品流

### 玩家缩放

- `playerScale`：为玩家注册 `minecraft:scale` 属性，`/scale set|reset|info` 调整体型，含 FOV 补偿（需客户端安装）
- `playerScaleMin` / `playerScaleMax`：`/scale set` 的软边界
- `realisticPlayerScale`：物理随体型联动（平缓 / 平缓+小体型保底 / 严格等比），重力 √scale
- `playerScaleLinkedEntities`：玩家用物品直接生成的生物实体继承玩家体型

### 玩家交互

- `ridingPlayers` / `pickupPlayers`：骑乘 / 捡起其他玩家（主手持不死图腾触发）
- `ridingPlayersPickUpLimit`：骑乘与捡起共用的堆叠人数上限
- `ridingPlayersDismountOnGameModeChange`：游戏模式变更时乘客自动下车
- `ridingPlayersClientAllowInteractions`：头上有乘客时仍可交互方块/实体（需客户端安装）
- `peacefulPlayers`：`/pvp` 按玩家开关 PVP，`@a` 为全服总开关

### 生存功能

- `sleepingDuringTheDay`：白天睡觉，睡醒切换至夜晚
- `playerhat`：`/hat` 将物品戴在头上，头部不死图腾可触发死亡保护
- `betterSnowBall`：雪球对玩家造成击退与伤害
- `invisibleInTallGrass`：头部位于高草丛时自动隐身


## 致谢

- **BlueMap 修复** — 参考 [fabric-carpet PR #2142](https://github.com/gnembon/fabric-carpet/pull/2142)
- **XaeroLib 修复** — 感谢 [Wzp-2008](https://github.com/Wzp-2008) 在 [LuckPerms #4232](https://github.com/LuckPerms/LuckPerms/issues/4232) 提供的补丁方案
- **假人名称建议（fakePlayerNameSuggestions）** — 移植自 [Ivan-Carpet-Addition](https://github.com/Ivan-1F/Ivan-Carpet-Addition)
- **白日做梦（sleepingDuringTheDay）** — 参考 [plusls-carpet-addition](https://github.com/Nyan-Work/plusls-carpet-addition) (PCA)
- **Unicode 参数支持（unicodeArgumentsSupport）** — 移植自 [YetAnotherCarpetAddition](https://github.com/hotpad100c/yetanothercarpetaddition) (YACA)
- 感谢 [Liuyue_awa](https://github.com/liuyuexiaoyu1) 及其项目 [Carpet-Igny-Addition](https://github.com/liuyuexiaoyu1/Carpet-Igny-Addition)
- 基于 [fabric-carpet](https://github.com/gnembon/fabric-carpet) 构建
