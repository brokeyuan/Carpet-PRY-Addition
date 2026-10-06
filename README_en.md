# Carpet-PRY-Addition

[![License](https://img.shields.io/badge/license-LGPL--3.0-blue)](https://choosealicense.com/licenses/lgpl-3.0/)
[![Modrinth](https://img.shields.io/modrinth/dt/carpet-pry-addition?color=00AF5C&label=Modrinth%20downloads&logo=modrinth)](https://modrinth.com/mod/carpet-pry-addition)
[![CurseForge](https://img.shields.io/curseforge/dt/1619008?logo=curseforge&label=CurseForge%20downloads&color=f16436)](https://www.curseforge.com/minecraft/mc-mods/carpet-pry-addition)
[![MC Versions](https://img.shields.io/badge/MC-1.21%20~%2026.3-blue)](https://github.com/brokeyuan/Carpet-PRY-Addition)
[![Github](https://img.shields.io/github/downloads/brokeyuan/Carpet-PRY-Addition/total?color=161616&label=Github%20downloads&logo=github)](https://github.com/brokeyuan/Carpet-PRY-Addition/releases)
[![QQGroup](https://img.shields.io/badge/Chat-QQGroup-12B7F5?style=flat&logo=qq&logoColor=white)](https://qm.qq.com/q/Ez582Z5P0c)

[中文](README.md) | **English**

## Introduction

**Carpet-PRY-Addition** is a server-side Fabric extension for [Fabric Carpet](https://github.com/gnembon/fabric-carpet), developed for the **Primaryuan Server**; it also enhances the experience when installed client-side. It adds **33** configurable Carpet rules and **9** commands, covering fake player enhancements, player scaling, server management, mod compatibility fixes, ported features, player interactions, and survival gameplay expansion.

All rules are off by default except the client-side rule `ridingPlayersClientInteract`; enable what you need.

## Documentation

- [Rules](docs/rules_en.md) | [规则](docs/rules.md)
- [Commands](docs/commands_en.md) | [命令](docs/commands.md)

## Download

- [GitHub Release](https://github.com/brokeyuan/Carpet-PRY-Addition/releases/latest)
- [Modrinth](https://modrinth.com/mod/carpet-pry-addition)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/carpet-pry-addition)

## Installation

1. Ensure **Fabric Loader >= 0.16.0** is installed on the server
2. Install required dependencies: **[Fabric Carpet](https://modrinth.com/mod/carpet)** + **[Fabric API](https://fabricmc.net/)**
3. Optional: [skinrestorer](https://modrinth.com/mod/skinrestorer) (only needed for fake player skin features)
4. Place the mod JAR in the server's `mods/` folder
5. This is a **server-side** mod — players do not need it installed. Optional client install provides two client-side features: `ridingPlayersClientInteract` (keep interacting while carrying a passenger) and the player-scaling FOV compensation
6. Rules are **off by default** (except `ridingPlayersClientInteract`) — use `/carpet` or config files to enable what you need

## Dependencies

| Name | Type | Links |
|------|------|-------|
| Carpet | Required | [Modrinth](https://modrinth.com/mod/carpet) · [MC百科](https://www.mcmod.cn/class/2361.html) |
| Fabric API | Required | [Official](https://fabricmc.net/) · [MC百科](https://www.mcmod.cn/class/3124.html) |
| skinrestorer | Optional | [Modrinth](https://modrinth.com/mod/skinrestorer) |

## Version Support

| Game Version | Development Status |
|--------------|--------------------|
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
| 26.3 | Maintained |

## Key Features

### Bugfixes

- `fixXaeroLib`: fixes fake player data loss when Xaero's maps are used with LuckPerms
- `fixBlueMap`: fixes fake players not triggering Fabric API connection events, allowing BlueMap and similar mods to track bots properly

### Ported Rules

- `fakePlayerNameSuggestions`: customize autocomplete suggestions for the `/player` command (ported from Ivan-Carpet-Addition)
- `unicodeArgumentsSupport`: allow non-ASCII characters in command arguments, enabling fake players with CJK names (ported from YACA)

### Fake Player Enhancements

- `fakePlayerTpp`: fake player pearl teleport stations with `/tpp` / `/tppset`
- `fakePlayerSkinMode` / `fakePlayerSkinSet`: fake player skin mode (default / summon / same skin) and the shared skin player name; every fake wears its target skin from the first frame (no flash), real players never affected
- `fakePlayerDropAll`: paced `/player dropall` dropping of the fake player's inventory
- `fakePlayerSendto`: `/player sendto` one-way inventory item flow between fake players
- `fakePlayerBrain`: `/player <name> brain` injects 19 vanilla-mob-style AI modes into fake players (zombie/skeleton/piglin/wolf/enderman/...), pure server-side with zero extra entities; supports CJK names, `keep` across restarts and colored name suffixes

### Player Scaling

- `playerScale`: registers `minecraft:scale` for players, adjusts size via `/scale set|reset|info`, FOV compensation included (requires client install)
- `playerScaleMin` / `playerScaleMax`: soft bounds for `/scale set`
- `playerScalePhysics`: physics scale with size (gentle / gentle + small-size floors / strictly proportional), gravity √scale
- `playerScaleLinkedEntities`: entities spawned by a player using an item inherit the player's size

### Player Interaction

- `ridingPlayers` / `pickupPlayers`: right-click another player with a Totem of Undying — head = ride on top, legs = pick them up onto your head (torso does nothing; fake players excluded)
- `ridingPlayersStackLimit`: shared player stack size limit for riding and pickup
- `ridingPlayersAutoDismount`: passengers auto-dismount on game mode change
- `ridingPlayersClientInteract`: keep interacting while carrying a passenger (requires client install)
- `peacefulPlayers`: per-player `/pvp` toggles, `@a` as a server-wide switch
- `patPatPlayers`: pat other players' heads cat-petting style — the target bobs down and up with the rhythm with hearts above their head, while the patted player sees a heart before their eyes and hears a soft sound coming from the patter's direction; works with any item in hand and never alters vanilla interactions (`true` = pat freely / `sneak` = only while sneaking; pure server-side, similar to PatPat)
- `whoCalledMe`: when a chat message contains a player's name (substring with longest-name-wins, glued letters/digits count), the mentioned player gets three notification dings (sound selectable, `false` to mute) and a title showing the message text; player names appearing in chat are highlighted (default aqua, 16 colors + rainbow gradient available, `false` keeps colors untouched; mentioning yourself works too, fake players excluded)

### Survival Features

- `sleepingDuringTheDay`: sleep during daytime, wake to night
- `textAnimation`: `/text` pops up MiSide-style animated subtitles in front of online players (fake players excluded; `@a` for everyone or a player name to direct — a target is required) — characters pop in one by one, hold, then the sentence drops and fades; more trailing exclamation marks make the whole sentence bigger (works on vanilla clients)
- `playerHat`: `/hat` to wear items on head, Totem of Undying in head slot triggers death protection
- `betterSnowball`: snowballs deal knockback and damage to players
- `invisibleInTallGrass`: auto-invisibility when head is inside tall grass


## Credits

- **BlueMap fix** — referenced from [fabric-carpet PR #2142](https://github.com/gnembon/fabric-carpet/pull/2142)
- **XaeroLib fix** — thanks to [Wzp-2008](https://github.com/Wzp-2008) for the patch provided in [LuckPerms #4232](https://github.com/LuckPerms/LuckPerms/issues/4232)
- **Fake Player Name Suggestions (fakePlayerNameSuggestions)** — ported from [Ivan-Carpet-Addition](https://github.com/Ivan-1F/Ivan-Carpet-Addition)
- **Daydreaming (sleepingDuringTheDay)** — referenced from [plusls-carpet-addition](https://github.com/Nyan-Work/plusls-carpet-addition) (PCA)
- **Unicode Argument Support (unicodeArgumentsSupport)** — ported from [YetAnotherCarpetAddition](https://github.com/hotpad100c/yetanothercarpetaddition) (YACA)
- Thanks to [Liuyue_awa](https://github.com/liuyuexiaoyu1) and [Carpet-Igny-Addition](https://github.com/liuyuexiaoyu1/Carpet-Igny-Addition)
- Built on top of [fabric-carpet](https://github.com/gnembon/fabric-carpet)
