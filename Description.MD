Carpet PRY Addition is a server-side Fabric extension for the [fabric-carpet](https://github.com/gnembon/fabric-carpet) mod, developed for the **Primaryuan Server**. It adds 26 configurable Carpet rules and 7 new commands (plus `/player` extensions) covering bug fixes, fake player enhancements, player interaction, and survival gameplay expansion.

---

## Fix

### Xaero Minimap + LuckPerms Compatibility Fix (`fixXaeroLib`)

Resolves an issue where fake player (carpet bot) data is lost or inventory is forced empty when **Xaero's Minimap / World Map** and **LuckPerms** are used together on the server.

Related upstream issues:

- [Xaero's World Map #1191](https://legacy.curseforge.com/minecraft/mc-mods/xaeros-world-map/issues/1191) — When using with LuckPerms and carpet, carpet bot's player data initialize failed
- [LuckPerms #4232](https://github.com/LuckPerms/LuckPerms/issues/4232) — When using with Xaero's Minimap and Xaero's World map, carpet bot's inventory empty
- [fabric-carpet #2158](https://github.com/gnembon/fabric-carpet/issues/2158) — Bot's inventory force empty with LuckPerms xaerolib (include in Xaero's Minimap and Xaero's Worldmap)

### BlueMap Compatibility Fix (`fixBlueMap`)

Fixes an issue where fake players do not trigger Fabric API connection events, causing mods like **BlueMap** to fail tracking fake player join/leave correctly.

Related upstream issues:

- [fabric-carpet #1962](https://github.com/gnembon/fabric-carpet/issues/1962) — Compatibility issue with Bluemap
- [BlueMap #598](https://github.com/BlueMap-Minecraft/BlueMap/issues/598) — Unable to properly handle fake players who go offline

---

## Fake Player Enhancements

### Fake Player Skin (`fakePlayerSkinMode` / `fakePlayerSkinSet`)

Customize the appearance of carpet fake players by integrating with the optional [skinrestorer](https://modrinth.com/mod/skinrestorer) mod. Install skinrestorer on the server to enable these features.

![](https://cdn.modrinth.com/data/cached_images/39b8bad3935be4a83ed077144ea84b0fa7f5de9d.png)

- **`summon` mode** — the fake player uses the skin of the player who summoned it.
- **`same_skin` mode** — all fake players share a unified skin, specified by `fakePlayerSkinSet` (default: `Brokeyuan`).

### Fake Player Name Suggestions (`fakePlayerNameSuggestions`)

![](https://cdn.modrinth.com/data/cached_images/1cbdf44186bd1e7975a087c2848691faa5965738.png)

Customize the name suggestion list shown when using the `/player` command to spawn fake players. Define a comma-separated list of names for quick selection.

### Fake Player Pearl Teleport System (`fakePlayerTpp`)

A fully custom teleport network powered by fake players and ender pearls. Admins set up stations at key locations, and players teleport between them by having a temporary fake player right-click a pearl at the destination.

Commands:

- `/tpp <station>` — teleport to a station.
- `/tppset` — manage stations, player aliases, and per-station pearl use counts. Data is persisted in `config/carpet-pry-tpp.json`.

### Fake Player Continuous Drop (`fakePlayerDropAll`)

Adds a `/player <name> dropall` sub-command that drops the fake player's whole inventory at a configurable pace: once, every tick, at fixed intervals, after a delay, N times per second, or at random intervals — with `/player <name> dropall stop` to cancel.

### Fake Player Inventory Link (`fakePlayerSendto`)

Adds a `/player <name> sendto <target>` sub-command creating a one-way item flow between fake players: one stack is transferred per trigger, round-robin across multiple targets, and the source buffers items when a target is full. The same pace modifiers as dropall are supported; `sendto stop` removes all links. Links live in memory only and do not survive restarts.

---

## Gameplay Expansion

### Player Hat (`playerHat`)

![](https://cdn.modrinth.com/data/cached_images/a53fc27b646fd4d9b41b911e25161a0a7a42d165_0.webp)

Wear any main-hand item on your head with the `/hat` command. When a **Totem of Undying** is placed in the helmet slot, taking fatal damage triggers a survival effect: **Regeneration II + Absorption II + Fire Resistance I**, giving you a second chance.

### Riding Players (`ridingPlayers`)

![](https://cdn.modrinth.com/data/cached_images/ecc7060b3e5779c1a804d393023aa142c32562d1_0.webp)

Hold a **Totem of Undying** in your main hand and right-click another player to ride on top of them. Players can control whether others may ride them via `/riding on|off`. Up to `ridingPlayersStackLimit` (default 16) players can stack in one tower.

Additional related rules:

- `ridingPlayersAutoDismount` — passengers dismount when a player's game mode changes.
- `ridingPlayersClientInteract` — a client-side rule (on by default) that allows interacting with blocks and entities even when carrying passengers. Requires the mod to be installed on the client.

### Picking Up Players (`pickupPlayers`)

![](https://cdn.modrinth.com/data/cached_images/50d6326b0c7b954962bf9998dc395036dca932b6_0.webp)

Hold a **Totem of Undying** in your main hand and a **Golden Carrot** in your off-hand, then right-click another player to pick them up (they ride on your head). Players control whether others may pick them up via `/picking on|off`. Shares the same stack limit as riding (`ridingPlayersStackLimit`).

### Peaceful Players (`peacefulPlayers`)

Enables the `/pvp` command: each player can turn their own PVP on or off. Players with PVP off cannot attack players and take no damage from players (self-inflicted damage unaffected); blocked attacks play a cue sound so the "poke" signal is preserved. Admins can toggle PVP for anyone or server-wide with `/pvp on|off @a`, and `/pvp list` shows everyone who currently has PVP off. States persist across restarts in `config/carpet-pry-pvp.json`.

---

## Player Scaling

### Player Scale (`playerScale` + `playerScaleMin` / `playerScaleMax`)

Registers the vanilla `minecraft:scale` attribute for players and enables the `/scale set|reset|info` command: players adjust their own size, and admins (or everyone, depending on the mode) can adjust others. Values only need to be greater than 0, bounded by `playerScaleMin` (default 0.1) and `playerScaleMax` (default 1.5). Includes automatic FOV compensation on clients with the mod installed.

### Realistic Player Scale (`playerScalePhysics`)

Optional physics linkage in three modes (gentle √scale curve, gentle + small-size floors, or strictly proportional): movement speed, jump height, step height, interaction range, fall safety and gravity all scale with size. Transient modifiers only — nothing is written to the save.

### Player Scale Linked Entities (`playerScaleLinkedEntities`)

Living entities spawned directly by a player using an item (armor stands, spawn-egg mobs, built golems, ...) inherit the player's size snapshot at spawn time.

---

## Other Features

- **Daydreaming** (`sleepingDuringTheDay`) — sleep in a bed during daytime to skip forward to night (inspired by PCA).
- **Better Snowball** (`betterSnowball`) — snowballs deal knockback and damage to players ("stones hidden in snow").
- **Invisibility Grass** (`invisibleInTallGrass`) — standing with your head inside tall grass grants automatic invisibility.
- **Unicode Command Arguments** (`unicodeArgumentsSupport`) — allows non-ASCII characters (Chinese, Japanese, Korean, etc.) in command arguments, enabling fake players with CJK names (ported from YACA).

---

## Why Download

- **All features are toggleable** — every mechanic is a Carpet rule. Everything is off by default except `ridingPlayersClientInteract` (a client-side quality-of-life rule that is on by default).
- **Wide version coverage** — supports Minecraft 1.21 through the latest snapshots (26.2), with 10 versions actively maintained.
- **Server-side only** — players do not need to install the mod on their clients (except for the FOV compensation of the player scaling feature and the optional `ridingPlayersClientInteract` client rule).
- **Lightweight and focused** — built on the Carpet framework, integrates naturally with existing Carpet setups.

---

## Before You Download


- **Required dependencies:** [fabric-carpet](https://modrinth.com/mod/carpet) and [Fabric API](https://modrinth.com/mod/fabric-api) must be installed on the server.
- **Fabric Loader >= 0.16.0** is required.
- **Optional:** [skinrestorer](https://modrinth.com/mod/skinrestorer) is needed for the fake player skin features (`fakePlayerSkinMode` / `fakePlayerSkinSet`).
- This is a **server-side** mod. Place it in the server's `mods` folder. Client installation is only needed for the player-scaling FOV compensation and the `ridingPlayersClientInteract` rule.
- All rules are **off by default** (except `ridingPlayersClientInteract`) — use `/carpet` or the Carpet configuration files to enable the ones you want.
- **Source code:** [GitHub](https://github.com/brokeyuan/Carpet-PRY-Addition)
- **Documentation:** [Wiki](https://primaryuan.top:2026/Primaryuan/mod/PRY/readme.html) | [GitHub README](https://github.com/brokeyuan/Carpet-PRY-Addition/blob/master/README_en.md)

---

## Credits

- **BlueMap fix** — referenced from [fabric-carpet PR #2142](https://github.com/gnembon/fabric-carpet/pull/2142)
- **XaeroLib fix** — thanks to [Wzp-2008](https://github.com/Wzp-2008) for the patch in [LuckPerms #4232](https://github.com/LuckPerms/LuckPerms/issues/4232)
- **Fake Player Name Suggestions** — referenced from [Ivan-Carpet-Addition](https://github.com/Ivan-1F/Ivan-Carpet-Addition)
- **Daydreaming** — referenced from [plusls-carpet-addition](https://github.com/Nyan-Work/plusls-carpet-addition) (PCA)
- **Unicode arguments** — ported from [YetAnotherCarpetAddition](https://github.com/hotpad100c/yetanothercarpetaddition) (YACA)
- Thanks to [Liuyue_awa](https://github.com/liuyuexiaoyu1) and [Carpet-Igny-Addition](https://github.com/liuyuexiaoyu1/Carpet-Igny-Addition)
- Built on [fabric-carpet](https://github.com/gnembon/fabric-carpet)
