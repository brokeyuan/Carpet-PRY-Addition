# Carpet-Primaryuan-Addition

[![License](https://img.shields.io/badge/license-LGPL--3.0-blue)](https://choosealicense.com/licenses/lgpl-3.0/)
[![Modrinth](https://img.shields.io/modrinth/dt/carpet-primaryuan-addition?color=00AF5C&label=Modrinth%20downloads&logo=modrinth)](https://modrinth.com/mod/carpet-primaryuan-addition)
[![CurseForge](https://img.shields.io/curseforge/dt/1619008?logo=curseforge&label=CurseForge%20downloads&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/carpet-primaryuan-addition)
[![MC Versions](https://img.shields.io/badge/MC-1.21%20~%2026.2-blue)](https://github.com/brokeyuan/Carpet-Primaryuan-Addition)
[![Github](https://img.shields.io/github/downloads/brokeyuan/Carpet-Primaryuan-Addition/total?color=161616&label=Github%20downloads&logo=github)](https://github.com/brokeyuan/Carpet-Primaryuan-Addition/releases)
[![QQGroup](https://img.shields.io/badge/Chat-QQGroup-12B7F5?style=flat&logo=qq&logoColor=white)](https://qm.qq.com/q/Ez582Z5P0c)

[中文](README.md) | **English**

## Introduction

**Carpet-Primaryuan-Addition** is a server-side Fabric extension for [Fabric Carpet](https://github.com/gnembon/fabric-carpet), developed for the **Primaryuan Server**. It adds **17** configurable Carpet rules and **5** new commands covering fake player enhancements, mod compatibility fixes, ported features, player interactions, and survival gameplay expansion.

All features are Carpet-rule-driven, off by default, enabled on demand.

## Features

### Bugfixes

| Rule | Type | Default | Description |
|------|------|---------|-------------|
| `FixXaeroLib` | boolean | `false` | Fixes fake player data loss when Xaero's maps are used with LuckPerms |
| `FixBluemap` | boolean | `false` | Fixes fake players not triggering Fabric API connection events, allowing BlueMap and similar mods to track bots properly |

### Fake Player Enhancements

| Rule | Type | Default | Description |
|------|------|---------|-------------|
| `TppFakePlayer` | boolean | `false` | Fake player pearl teleport stations, enables `/tpp` and `/tppset` commands |
| `fakePlayerSkinMode` | string | `default` | Fake player skin mode: `default` / `summon` / `same_skin` |
| `fakePlayerSkinSet` | string | `Brokeyuan` | Player name used for the shared skin in `same_skin` mode |
| `fakePlayerDropStackModifiers` | boolean | `false` | Adds an independent `/player <name> dropall [once\|continuous\|interval\|after\|perTick\|randomly\|stop]` sub-command, letting fake players drop all inventory items at a configured pace. The entire `dropall` command is hidden when the rule is disabled |
| `playerScaleModifiers` | string | `false` | Registers `minecraft:scale` attribute for Player and adds unified `/scale set\|reset\|info` command. `false`=hidden; `self`=everyone can only adjust themselves (even OPs); `true`=self-only for players + admins anyone; `everyone`=any player can modify anyone. Requires Minecraft 1.21.5+ |
| `playerScaleMin` | double | `0.1` | Minimum scale value that players (including non-ops in everyone mode) can set via `/scale set`; admin path is not limited |
| `playerScaleMax` | double | `10.0` | Maximum scale value that players (including non-ops in everyone mode) can set via `/scale set`; admin path is not limited |

### Ported Features

| Rule | Type | Default | Description |
|------|------|---------|-------------|
| `fakePlayerNameSuggestions` | string | `Steve,Alex` | Customize autocomplete suggestions for the `/player` command (ported from Ivan-Carpet-Addition) |
| `unicodeArgumentsSupport` | boolean | `false` | Allow non-ASCII characters in command arguments, enabling fake players with CJK names (ported from YACA) |

### Player Interaction

| Rule | Type | Default | Description |
|------|------|---------|-------------|
| `ridingPlayers` | boolean | `false` | Ride other players by holding a Totem of Undying in main hand |
| `pickupPlayers` | boolean | `false` | Pick up other players by holding a Totem of Undying + Golden Carrot in off-hand |
| `ridingPlayersPickUpLimit` | int | `16` | Maximum player stack size for riding and pickup (supports 16/32/custom) |
| `ridingPlayersDismountOnGameModeChange` | boolean | `false` | Passengers automatically dismount when game mode changes |
| `ridingPlayersClientAllowInteractions` | boolean | `true` | Allow block/entity interaction while carrying a passenger (requires client install) |

### Survival Features

| Rule | Type | Default | Description |
|------|------|---------|-------------|
| `sleepingDuringTheDay` | boolean | `false` | Sleep during daytime to skip to night (referenced from PCA) |
| `playerhat` | boolean | `false` | `/hat` command to wear items on head; Totem of Undying in head slot triggers death protection |
| `betterSnowBall` | boolean | `false` | Snowballs deal knockback and damage to players |
| `invisibleInTallGrass` | boolean | `false` | Auto-invisibility when head is inside tall grass |

### Commands

| Command | Description |
|---------|-------------|
| `/tpp <station>` | Teleport via pearl stations |
| `/tppset` | Manage teleport stations |
| `/hat` | Wear main-hand item on head |
| `/riding on\|off` | Toggle permission for others to ride you |
| `/picking on\|off` | Toggle permission for others to pick you up |

## Installation

1. Ensure **Fabric Loader >= 0.16.0** is installed on the server
2. Install required dependencies: **[Fabric Carpet](https://modrinth.com/mod/carpet)** + **[Fabric API](https://fabricmc.net/)**
3. Optional: [skinrestorer](https://modrinth.com/mod/skinrestorer) (only needed for fake player skin features)
4. Place the mod JAR in the server's `mods/` folder
5. This is a **server-side** mod — players do not need it installed (only `ridingPlayersClientAllowInteractions` requires client installation)
6. All rules are **off by default** — use `/carpet` or config files to enable what you need

## Dependencies

| Name | Type | Links |
|------|------|-------|
| Carpet | Required | [Modrinth](https://modrinth.com/mod/carpet) · [MC百科](https://www.mcmod.cn/class/2361.html) |
| Fabric API | Required | [Official](https://fabricmc.net/) · [MC百科](https://www.mcmod.cn/class/3124.html) |
| skinrestorer | Optional | [Modrinth](https://modrinth.com/mod/skinrestorer) |

## Version Support

| Game Version | Development Status |
|--------------|-------------------|
| 1.21 | Maintained |
| 1.21.1 | Maintained |
| 1.21.3 | Maintained |
| 1.21.4 | Maintained |
| 1.21.5 | Maintained |
| 1.21.8 | Maintained |
| 1.21.10 | Maintained |
| 1.21.11 (Main) | Maintained |
| 26.1.2 | Maintained |
| 26.2 | Maintained |

## Documentation

- [Rules](docs/rules_en.md) | [规则](docs/rules.md)
- [Commands](docs/commands_en.md) | [命令](docs/commands.md)

## Download

- [GitHub Release](https://github.com/brokeyuan/Carpet-Primaryuan-Addition/releases/latest)
- [Modrinth](https://modrinth.com/mod/carpet-primaryuan-addition)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/carpet-primaryuan-addition)

## Credits

- **BlueMap fix** — referenced from [fabric-carpet PR #2142](https://github.com/gnembon/fabric-carpet/pull/2142)
- **XaeroLib fix** — thanks to [Wzp-2008](https://github.com/Wzp-2008) for the patch provided in [LuckPerms #4232](https://github.com/LuckPerms/LuckPerms/issues/4232)
- **Fake Player Name Suggestions (fakePlayerNameSuggestions)** — ported from [Ivan-Carpet-Addition](https://github.com/Ivan-1F/Ivan-Carpet-Addition)
- **Daydreaming (sleepingDuringTheDay)** — referenced from [plusls-carpet-addition](https://github.com/Nyan-Work/plusls-carpet-addition) (PCA)
- **Unicode Argument Support (unicodeArgumentsSupport)** — ported from [YetAnotherCarpetAddition](https://github.com/hotpad100c/yetanothercarpetaddition) (YACA)
- Thanks to [Liuyue_awa](https://github.com/liuyuexiaoyu1) and [Carpet-Igny-Addition](https://github.com/liuyuexiaoyu1/Carpet-Igny-Addition)
- Built on top of [fabric-carpet](https://github.com/gnembon/fabric-carpet)
