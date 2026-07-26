# Carpet-Primaryuan-Addition

[![License](https://img.shields.io/badge/license-LGPL--3.0-blue)](https://choosealicense.com/licenses/lgpl-3.0/)
[![Modrinth](https://img.shields.io/modrinth/dt/carpet-primaryuan-addition?color=00AF5C&label=Modrinth%20downloads&logo=modrinth)](https://modrinth.com/mod/carpet-primaryuan-addition)
[![CurseForge](https://img.shields.io/curseforge/dt/1619008?logo=curseforge&label=CurseForge%20downloads&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/carpet-primaryuan-addition)
[![MC Versions](https://img.shields.io/badge/MC-1.21%20~%2026.2-blue)](https://github.com/brokeyuan/Carpet-Primaryuan-Addition)
[![Github](https://img.shields.io/github/downloads/brokeyuan/Carpet-Primaryuan-Addition/total?color=161616&label=Github%20downloads&logo=github)](https://github.com/brokeyuan/Carpet-Primaryuan-Addition/releases)
[![QQGroup:450108190](https://img.shields.io/badge/Chat-QQGroup-12B7F5?style=flat&logo=qq&logoColor=white)](https://qm.qq.com/q/Ez582Z5P0c)

**中文** | [English](README_en.md)

## 简介

**Carpet-Primaryuan-Addition** 是一个基于 [Fabric Carpet](https://github.com/gnembon/fabric-carpet) 的服务端扩展模组，为主要元服务器（Primaryuan Server）开发。新增了 **16 条**可配置 Carpet 规则和 **5 个**新命令，涵盖假人管理增强、模组兼容性修复、功能移植、玩家交互和生存特性扩展。

所有功能均为 Carpet 规则驱动，默认关闭，按需启用。

## 功能特性

### 假人相关

| 规则 | 类型 | 默认值 | 简介 |
|------|------|--------|------|
| `TppFakePlayer` | boolean | `false` | 假人珍珠传送站，启用 `/tpp` 和 `/tppset` 命令 |
| `fakePlayerNameSuggestions` | string | `Steve,Alex` | 自定义 `/player` 命令的名称建议列表 |
| `fakePlayerSkinMode` | string | `default` | 假人皮肤模式：`default` / `summon` / `same_skin` |
| `fakePlayerSkinSet` | string | `Brokeyuan` | 统一皮肤模式下使用的玩家皮肤名 |

### 漏洞修复

| 规则 | 类型 | 默认值 | 简介 |
|------|------|--------|------|
| `FixXaeroLib` | boolean | `false` | 修复 Xaero 地图 + LuckPerms 导致假人数据丢失的问题 |
| `FixBluemap` | boolean | `false` | 修复假人不触发 Fabric API 连接事件导致 BlueMap 等模组追踪异常 |

### 移植功能

| 规则 | 类型 | 默认值 | 简介 |
|------|------|--------|------|
| `sleepingDuringTheDay` | boolean | `false` | 允许白天睡觉，醒来后切换至夜晚（参考 PCA） |
| `unicodeArgumentsSupport` | boolean | `false` | 命令参数支持非 ASCII 字符，可召唤中文名假人（移植自 YACA） |

### 玩家交互

| 规则 | 类型 | 默认值 | 简介 |
|------|------|--------|------|
| `ridingPlayers` | boolean | `false` | 主手持不死图腾时可骑乘其他玩家 |
| `pickupPlayers` | boolean | `false` | 主手持不死图腾 + 副手金胡萝卜时可捡起其他玩家 |
| `ridingPlayersPickUpLimit` | int | `16` | 骑乘和捡起的最大堆叠人数（支持 16/32/自定义） |
| `ridingPlayersDismountOnGameModeChange` | boolean | `false` | 游戏模式变更时乘客自动下车 |
| `ridingPlayersClientAllowInteractions` | boolean | `true` | 骑乘状态下仍可与方块/实体交互（需客户端安装） |

### 生存功能

| 规则 | 类型 | 默认值 | 简介 |
|------|------|--------|------|
| `playerhat` | boolean | `false` | `/hat` 命令将物品戴在头上，头部不死图腾可触发死亡保护 |
| `betterSnowBall` | boolean | `false` | 雪球可对玩家造成击退和伤害 |
| `invisibleInTallGrass` | boolean | `false` | 头部位于高草丛时自动隐形 |

### 命令

| 命令 | 说明 |
|------|------|
| `/tpp <站点>` | 珍珠传送站传送 |
| `/tppset` | 传送站点管理 |
| `/hat` | 将主手物品戴在头上 |
| `/ride on\|off` | 设置是否允许他人骑乘自己 |
| `/pickup on\|off` | 设置是否允许他人捡起自己 |

## 安装

1. 确保服务器已安装 **Fabric Loader >= 0.16.0**
2. 安装必需前置：**[Fabric Carpet](https://modrinth.com/mod/carpet)** + **[Fabric API](https://fabricmc.net/)**
3. 可选前置：[skinrestorer](https://modrinth.com/mod/skinrestorer)（仅假人皮肤功能需要）
4. 将 mod JAR 文件放入服务器的 `mods/` 文件夹
5. 本模组为**服务端模组**，玩家无需安装（仅 `ridingPlayersClientAllowInteractions` 规则需要客户端安装）
6. 所有规则**默认关闭**，使用 `/carpet` 命令或配置文件按需启用

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

## 文档

- [规则](docs/rules.md) | [Rules](docs/rules_en.md)
- [命令](docs/commands.md) | [Commands](docs/commands_en.md)

## 下载

- [GitHub Release](https://github.com/brokeyuan/Carpet-Primaryuan-Addition/releases/latest)
- [Modrinth](https://modrinth.com/mod/carpet-primaryuan-addition)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/carpet-primaryuan-addition)

## 致谢

- **BlueMap 修复** — 参考 [fabric-carpet PR #2142](https://github.com/gnembon/fabric-carpet/pull/2142)
- **XaeroLib 修复** — 感谢 [Wzp-2008](https://github.com/Wzp-2008) 在 [LuckPerms #4232](https://github.com/LuckPerms/LuckPerms/issues/4232) 提供的补丁方案
- **白日做梦（sleepingDuringTheDay）** — 参考 [plusls-carpet-addition](https://github.com/Nyan-Work/plusls-carpet-addition) (PCA)
- **Unicode 参数支持（unicodeArgumentsSupport）** — 移植自 [YetAnotherCarpetAddition](https://github.com/hotpad100c/yetanothercarpetaddition) (YACA)
- 基于 [fabric-carpet](https://github.com/gnembon/fabric-carpet) 构建
