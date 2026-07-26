Carpet PRY Addition is a server-side Fabric extension for the [fabric-carpet](https://github.com/gnembon/fabric-carpet) mod, developed for the **Primaryuan Server**. It adds 16 configurable Carpet rules and several new commands covering bug fixes, fake player enhancements, and survival gameplay expansion.

---

## Fix

### Xaero Minimap + LuckPerms Compatibility Fix (`FixXaeroLib`)

Resolves an issue where fake player (carpet bot) data is lost or inventory is forced empty when **Xaero's Minimap / World Map** and **LuckPerms** are used together on the server.

Related upstream issues:

- [Xaero's World Map #1191](https://legacy.curseforge.com/minecraft/mc-mods/xaeros-world-map/issues/1191) — When using with LuckPerms and carpet, carpet bot's player data initialize failed
- [LuckPerms #4232](https://github.com/LuckPerms/LuckPerms/issues/4232) — When using with Xaero's Minimap and Xaero's World map, carpet bot's inventory empty
- [fabric-carpet #2158](https://github.com/gnembon/fabric-carpet/issues/2158) — Bot's inventory force empty with LuckPerms xaerolib (include in Xaero's Minimap and Xaero's Worldmap)

### BlueMap Compatibility Fix (`FixBluemap`)

Fixes an issue where fake players do not trigger Fabric API connection events, causing mods like **BlueMap** to fail tracking fake player join/leave correctly.

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

### Fake Player Pearl Teleport System (`TppFakePlayer`)

A fully custom teleport network powered by fake players and ender pearls. Admins set up stations at key locations, and players teleport between them by having a temporary fake player right-click a pearl at the destination.

Commands:

- `/tpp <station>` — teleport to a station.
- `/tppset` — manage stations, player aliases, and per-station pearl use counts. Data is persisted in `config/carpet-pry-tpp.json`.

---

## Gameplay Expansion

### Player Hat (`playerhat`)

![](https://cdn.modrinth.com/data/cached_images/a53fc27b646fd4d9b41b911e25161a0a7a42d165_0.webp)

Wear any main-hand item on your head with the `/hat` command. When a **Totem of Undying** is placed in the helmet slot, taking fatal damage triggers a survival effect: **Regeneration II + Absorption II + Fire Resistance I**, giving you a second chance.

### Riding Players (`ridingPlayers`)

![](https://cdn.modrinth.com/data/cached_images/ecc7060b3e5779c1a804d393023aa142c32562d1_0.webp)

Hold a **Totem of Undying** in your main hand and right-click another player to ride on top of them. Players can control whether others may ride them via `/ride on|off`. Up to `ridingPlayersPickUpLimit` (default 16) players can stack.

Additional related rules:

- `ridingPlayersDismountOnGameModeChange` — passengers dismount when a player's game mode changes.
- `ridingPlayersClientAllowInteractions` — a client-side rule that allows interacting with blocks and entities even when carrying passengers. Requires the mod to be installed on the client.

### Picking Up Players (`pickupPlayers`)

![](https://cdn.modrinth.com/data/cached_images/50d6326b0c7b954962bf9998dc395036dca932b6_0.webp)

Hold a **Totem of Undying** in your main hand and a **Golden Carrot** in your off-hand, then right-click another player to pick them up (they ride on your head). Players control whether others may pick them up via `/pickup on|off`. Shares the same stack limit as riding (`ridingPlayersPickUpLimit`).

---

## Other Features

- **Daydreaming** (`sleepingDuringTheDay`) — sleep in a bed during daytime to skip forward to night (inspired by PCA).
- **Better Snowball** (`betterSnowBall`) — snowballs deal knockback and damage to players ("stones hidden in snow").
- **Invisibility Grass** (`invisibleInTallGrass`) — standing with your head inside tall grass grants automatic invisibility.
- **Unicode Command Arguments** (`unicodeArgumentsSupport`) — allows non-ASCII characters (Chinese, Japanese, Korean, etc.) in command arguments, enabling fake players with CJK names (ported from YACA).

---

## Why Download

- **All features are toggleable** — every mechanic is a Carpet rule, disabled by default. You only enable what you need.
- **Wide version coverage** — supports Minecraft 1.21 through the latest snapshots (26.2), with 10 versions actively maintained.
- **Server-side only** — players do not need to install the mod on their clients (except for the optional `ridingPlayersClientAllowInteractions` client rule).
- **Lightweight and focused** — built on the Carpet framework, integrates naturally with existing Carpet setups.

---

## Before You Download


- **Required dependencies:** [fabric-carpet](https://modrinth.com/mod/carpet) and [Fabric API](https://modrinth.com/mod/fabric-api) must be installed on the server.
- **Fabric Loader >= 0.16.0** is required.
- **Optional:** [skinrestorer](https://modrinth.com/mod/skinrestorer) is needed for the fake player skin features (`fakePlayerSkinMode` / `fakePlayerSkinSet`).
- This is a **server-side** mod. Place it in the server's `mods` folder. Client installation is only needed for the `ridingPlayersClientAllowInteractions` rule.
- All rules are **off by default** — use `/carpet` or the Carpet configuration files to enable the ones you want.
- **Source code:** [GitHub](https://github.com/brokeyuan/Carpet-Primaryuan-Addition)
- **Documentation:** [Wiki](https://primaryuan.top:2026/Primaryuan/mod/PRY/readme.html) | [GitHub README](https://github.com/brokeyuan/Carpet-Primaryuan-Addition/blob/master/README_en.md)
