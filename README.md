# Carpet-Primaryuan-Addition

[![License](https://img.shields.io/badge/license-LGPL--3.0-blue)](https://choosealicense.com/licenses/lgpl-3.0/)
[![Modrinth](https://img.shields.io/modrinth/dt/carpet-pry-addition?color=00AF5C&label=Modrinth%20downloads&logo=modrinth)](https://modrinth.com/mod/carpet-pry-addition)
[![CurseForge](https://img.shields.io/curseforge/dt/1619008?logo=curseforge&label=CurseForge%20downloads&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/carpet-primaryuan-addition)
[![MC Versions](https://img.shields.io/badge/MC-1.21%20~%2026.2-blue)](https://github.com/brokeyuan/Carpet-Primaryuan-Addition)
[![Github](https://img.shields.io/github/downloads/brokeyuan/Carpet-Primaryuan-Addition/total?color=161616&label=Github%20downloads&logo=github)](https://github.com/brokeyuan/Carpet-Primaryuan-Addition/releases)
[![QQGroup:450108190](https://img.shields.io/badge/Chat-QQGroup-12B7F5?style=flat&logo=qq&logoColor=white)](https://qm.qq.com/q/Ez582Z5P0c)

**中文** | [English](README_en.md)

## 简介

**Carpet-Primaryuan-Addition** 是一个基于 [Fabric Carpet](https://github.com/gnembon/fabric-carpet) 的服务端扩展模组，主要为 PRY 服务器（Primaryuan Server）开发。新增 **24 条**可配置 Carpet 规则和 **7 个**命令（另有 `/player <name> dropall`、`/player <name> sendto` 两个假人子命令扩展），涵盖假人管理增强、玩家缩放、服务器管理、模组兼容性修复、功能移植、玩家交互和生存特性扩展。

除 `ridingPlayersClientAllowInteractions` 默认开启外，其余规则默认关闭，按需启用。

## 功能特性

### 漏洞修复

| 规则 | 类型 | 默认值 | 简介 |
|------|------|--------|------|
| `FixXaeroLib` | boolean | `false` | 修复 Xaero 地图 + LuckPerms 导致假人数据丢失的问题 |
| `FixBluemap` | boolean | `false` | 修复假人不触发 Fabric API 连接事件导致 BlueMap 等模组追踪异常 |

### 假人增强

| 规则 | 类型 | 默认值 | 简介 |
|------|------|--------|------|
| `TppFakePlayer` | boolean | `false` | 假人珍珠站传送，启用 `/tpp` 与 `/tppset` 命令 |
| `fakePlayerSkinMode` | string | `default` | 假人皮肤模式：`default` / `summon` / `same_skin` |
| `fakePlayerSkinSet` | string | `Brokeyuan` | `same_skin` 模式下用于统一皮肤的玩家名 |
| `fakePlayerDropStackModifiers` | boolean | `false` | 给假人追加 `/player <name> dropall` 子命令，按设定节奏持续丢出背包物品（频率参数见命令表） |
| `fakePlayerSendto` | boolean | `false` | 给假人追加 `/player <name> sendto <目标>` 子命令，建立假人间单向背包物品流：多目标逐组轮流分配，目标放不下时物品留在源背包 |

### 玩家缩放

| 规则 | 类型 | 默认值 | 简介 |
|------|------|--------|------|
| `playerScale` | string | `false` | 为 Player 注册 `minecraft:scale` 属性并启用 `/scale set\|reset\|info` 命令，含 FOV 补偿（需客户端安装），详见[规则文档](docs/rules.md) |
| `playerScaleMin` / `playerScaleMax` | double | `0.1` / `1.5` | `/scale set` 的软边界，管理员可修改本规则调整范围 |
| `realisticPlayerScale` | string | `false` | 玩家物理随体型联动：`true`=√scale 平缓、`safety`=平缓+小体型保底（推荐）、`strict`=严格等比、`false`=关闭；重力均 √scale。需配合 `playerScale` 使用 |
| `playerScaleLinkedEntities` | boolean | `false` | 玩家用物品直接生成的生物实体继承玩家当前体型（scale 基础值快照）；非生物实体与发射器等非玩家来源不联动 |

### 移植功能

| 规则 | 类型 | 默认值 | 简介 |
|------|------|--------|------|
| `fakePlayerNameSuggestions` | string | `Steve,Alex` | 自定义 `/player` 命令的补全建议（移植自 Ivan-Carpet-Addition） |
| `unicodeArgumentsSupport` | boolean | `false` | 允许命令参数使用非 ASCII 字符，可召唤中文名假人（移植自 YACA） |

### 服务器管理

| 规则 | 类型 | 默认值 | 简介 |
|------|------|--------|------|
| `peacefulPlayers` | string | `false` | 启用 `/pvp` 按玩家开关 PVP（双向保护，自伤不限，拦截时播放提示音）；`/pvp on\|off @a` 为全服总开关，仅管理员可用 |

### 玩家交互

| 规则 | 类型 | 默认值 | 简介 |
|------|------|--------|------|
| `ridingPlayers` | boolean | `false` | 主手持不死图腾时可骑上其他玩家 |
| `pickupPlayers` | boolean | `false` | 主手持不死图腾 + 副手金胡萝卜时可捡起其他玩家 |
| `ridingPlayersPickUpLimit` | int | `16` | 骑乘与捡起共用的最大堆叠人数，`/carpet` 可设任意值 |
| `ridingPlayersDismountOnGameModeChange` | boolean | `false` | 游戏模式变更时乘客自动下车 |
| `ridingPlayersClientAllowInteractions` | boolean | `true` | 头上有乘客时仍可交互方块/实体（需客户端安装） |

### 生存功能

| 规则 | 类型 | 默认值 | 简介 |
|------|------|--------|------|
| `sleepingDuringTheDay` | boolean | `false` | 白天睡觉，睡醒切换至夜晚（参考 PCA；完整功能需 1.21.11+） |
| `playerhat` | boolean | `false` | `/hat` 命令将物品戴在头上；头部不死图腾可触发死亡保护 |
| `betterSnowBall` | boolean | `false` | 雪球对玩家造成击退与伤害 |
| `invisibleInTallGrass` | boolean | `false` | 头部位于高草丛时自动隐身 |

### 命令

| 命令 | 说明 |
|------|------|
| `/tpp <station>` | 经珍珠传送站传送 |
| `/tppset` | 管理传送站（站点 / 玩家别名 / 触发次数） |
| `/hat` | 将主手物品戴在头上 |
| `/riding on\|off` | 开关他人骑乘自己的权限 |
| `/picking on\|off` | 开关他人捡起自己的权限 |
| `/scale set\|reset\|info` | 玩家大小调节（需 `playerScale` 规则） |
| `/pvp [list\|on\|off [玩家\|@a]]` | 按玩家开关 PVP，`@a` 为全服总开关（需 `peacefulPlayers`） |
| `/player <name> dropall [once\|continuous\|interval <ticks>\|after <ticks>\|perTick <times>\|randomly <min> <max>\|stop]` | 假人按节奏持续丢出背包物品（需 `fakePlayerDropStackModifiers`） |
| `/player <name> sendto <目标>` | 假人间背包物品流，同款频率参数可调速（需 `fakePlayerSendto`） |

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
5. 本模组为**服务端模组**，玩家无需安装（仅 `ridingPlayersClientAllowInteractions` 规则需要客户端安装）
6. 规则默认关闭（`ridingPlayersClientAllowInteractions` 除外），使用 `/carpet` 命令或配置文件按需启用

## 依赖

| 名称 | 类型 | 链接 |
|------|------|------|
| Carpet | 必须 | [Modrinth](https://modrinth.com/mod/carpet) · [MC百科](https://www.mcmod.cn/class/2361.html) |
| Fabric API | 必须 | [官方](https://fabricmc.net/) · [MC百科](https://www.mcmod.cn/class/3124.html) |
| skinrestorer | 可选 | [Modrinth](https://modrinth.com/mod/skinrestorer) |

## 版本支持

| 游戏版本 | 开发状态 | 功能差异 |
|----------|----------|----------|
| 1.21 | 维护中 | 白日做梦不能在白天上床 |
| 1.21.1 | 维护中 | 同 1.21 |
| 1.21.3 | 维护中 | 同 1.21 |
| 1.21.4 | 维护中 | 同 1.21 |
| 1.21.5 | 维护中 | 同 1.21 |
| 1.21.8 | 维护中 | 同 1.21 |
| 1.21.10 | 维护中 | 同 1.21 |
| 1.21.11（主版本） | 维护中 | 全部功能可用 |
| 26.1.2 | 维护中 | 同 1.21.11 |
| 26.2 | 维护中 | 同 1.21.11 |

## 致谢

- **BlueMap 修复** — 参考 [fabric-carpet PR #2142](https://github.com/gnembon/fabric-carpet/pull/2142)
- **XaeroLib 修复** — 感谢 [Wzp-2008](https://github.com/Wzp-2008) 在 [LuckPerms #4232](https://github.com/LuckPerms/LuckPerms/issues/4232) 提供的补丁方案
- **假人名称建议（fakePlayerNameSuggestions）** — 移植自 [Ivan-Carpet-Addition](https://github.com/Ivan-1F/Ivan-Carpet-Addition)
- **白日做梦（sleepingDuringTheDay）** — 参考 [plusls-carpet-addition](https://github.com/Nyan-Work/plusls-carpet-addition) (PCA)
- **Unicode 参数支持（unicodeArgumentsSupport）** — 移植自 [YetAnotherCarpetAddition](https://github.com/hotpad100c/yetanothercarpetaddition) (YACA)
- 感谢 [Liuyue_awa](https://github.com/liuyuexiaoyu1) 及其项目 [Carpet-Igny-Addition](https://github.com/liuyuexiaoyu1/Carpet-Igny-Addition)
- 基于 [fabric-carpet](https://github.com/gnembon/fabric-carpet) 构建
