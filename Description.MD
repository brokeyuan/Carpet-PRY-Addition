
Carpet PRY Addition is a server-side Fabric extension for the [fabric-carpet](https://github.com/gnembon/fabric-carpet) mod, developed for the **Primaryuan Server**. It adds 37 main Carpet rules (plus 2 companion settings) and 15 new commands (plus `/player` extensions) covering bug fixes, fake player enhancements, player interaction, and survival gameplay expansion.

---

## Bug Fixes

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

### End Crystal Position Sync (`fixEndCrystalSync`)

Fixes client-side position desync when end crystals are pushed by pistons, including invulnerable crystals.

### Standing On Air Misjudgment Fix (`fixStandingOnAir`)

Moves support-block resends after player movement, matching the official 26.4 Snapshot 1 fix, and additionally suppresses the standing-on-air INFO log. Resends and the 200-tick cooldown remain active. Only affects 26.3.

Related upstream issues:

- [MC-311474](https://bugs.mojang.com/browse/MC/issues/MC-311474) — standing on air log false positives
- [MC-311727](https://mojira.dev/MC-311727) — root cause (check runs before movement), fixed in 26.4 Snapshot 1

---

## Fake Player Enhancements

### Fake Player Skin (`fakePlayerSkinMode` / `fakePlayerSkinSet`)

Customize the appearance of carpet fake players by integrating with the optional [skinrestorer](https://modrinth.com/mod/skinrestorer) mod. Install skinrestorer on the server to enable these features.

![](https://cdn.modrinth.com/data/cached_images/39b8bad3935be4a83ed077144ea84b0fa7f5de9d.png)

- **`summon` mode** — the fake player uses the skin of the player who summoned it.
- **`same_skin` mode** — all fake players share a unified skin, specified by `fakePlayerSkinSet` (default: `Brokeyuan`).
- Skins apply to the fake player's session only — nothing is persisted to skinrestorer's storage, so real players sharing the same name (same UUID on offline-mode servers) never get reskinned.
- Skins are applied before spawn when available. A pending lookup may briefly show the default skin; real players are unaffected.
- With [Carpet TIS Addition](https://modrinth.com/mod/carpet-tis-addition) installed, `/player <name> rejoin` gets the same skin treatment — rejoin reuses Carpet's spawn logic, where the skin hook applies.

### Fake Player Name Suggestions (`fakePlayerNameSuggestions`)

![](https://cdn.modrinth.com/data/cached_images/1cbdf44186bd1e7975a087c2848691faa5965738.png)

Customize the name suggestion list shown when using the `/player` command to spawn fake players. Define a comma-separated list of names for quick selection.

### Fake Player Pearl Teleport System (`fakePlayerTpp`)

A teleport network using fake players and ender pearls. Admins set up stations at key locations, and players teleport between them by having a temporary fake player right-click a pearl at the destination.

Commands:

- `/tpp <station>` — teleport to a station.
- `/tppset` — manage stations, player aliases, and per-station pearl use counts. Data lives in the world save at `carpet-pry-addition/tpp.json` (auto-migrated from the old config path).

Station completion matches internal-name or display-name prefixes, ignoring case, and inserts the display name. With `/tppset set qwp 全物品`, typing `/tpp qwp` suggests `全物品`.

`/tpp` requires [Carpet TIS Addition](https://modrinth.com/mod/carpet-tis-addition) — its `/player rejoin` respawns the station's fake player at its logged-off position and orientation. This is a soft dependency: without it, `/tpp` immediately reports the missing requirement, while `/tppset` and everything else work normally.

### Fake Player Continuous Drop (`fakePlayerDropAll`)

Use `/player <name> dropall` (or `dropall once`) to empty the inventory immediately. Timed modes drop one stack per trigger: every tick, at a fixed or random interval, after a delay, or N times per second. `dropall stop` cancels the task.

### Fake Player Inventory Link (`fakePlayerSendto`)

Adds a `/player <name> sendto <target>` sub-command creating a one-way item flow between fake players: one stack is transferred per trigger, round-robin across multiple targets, and the source buffers items when a target is full. The same pace modifiers as dropall are supported; `sendto stop` removes all links. Links live in memory only and do not survive restarts.

### Fake Player Clones (`fakePlayerClones`)

Lets multiple real Carpet fake players share one UUID and skin online simultaneously. Each clone keeps its own entity ID, position and dimension; the group shares one slot-level authoritative inventory (in-place consumption and same-tick edits never duplicate); clones genuinely load chunks and count for mob spawning. `/player <name> spawn` adds a clone per invocation, `/fakeclone kill|rejoin <name>` exits/restores the whole group, snapshots survive restarts, and same-UUID real players are protected.

### Fake Player Brain (`fakePlayerBrain`)

Give fake players one of 24 mob behavior modes with `/player <name> brain <mode>`. They keep their player body and inventory; no extra entities or items are created. Supports CJK mode names, colored name suffixes and `keep` for rejoins during the current server run.

---

## Gameplay Expansion

### Player Hat (`playerHat`)

![](https://cdn.modrinth.com/data/cached_images/a53fc27b646fd4d9b41b911e25161a0a7a42d165_0.webp)

Use `/hat` to swap whole main-hand and head stacks, or right-click air with an empty offhand. The shortcut excludes tools and usable items, respects Binding Curse, is blocked while the head holds a regular helmet, turtle shell or totem of undying (`/hat` unaffected), and supports the `carpet-pry-addition:hat_use_blacklist` item tag. When a **Totem of Undying** is placed in the helmet slot, taking fatal damage triggers a survival effect: **Regeneration II + Absorption II + Fire Resistance I**.

### Player Head Skin (`playerHeadSkin`)

Real and Carpet fake players wear a player head to use its full skin; swapping heads updates it, and removing it or disabling the rule restores the previous skin. The head stays visible. Server-side, with no skin mod or `playerHat` required.

### Riding Players (`ridingPlayers`)

![](https://cdn.modrinth.com/data/cached_images/ecc7060b3e5779c1a804d393023aa142c32562d1_0.webp)

Hold a **Totem of Undying** in your main hand and right-click another player's **head** to ride on top of them. Players can control whether others may ride them via `/riding on|off`. Up to `ridingPlayersStackLimit` (default 16) players can stack in one tower.

Additional related rules:

- `ridingPlayersAutoDismount` — passengers dismount when a player's game mode changes.
- `ridingPlayersClientInteract` — a client-side rule (on by default) that allows interacting with blocks and entities even when carrying passengers. Requires the mod to be installed on the client.

### Picking Up Players (`pickupPlayers`)

![](https://cdn.modrinth.com/data/cached_images/50d6326b0c7b954962bf9998dc395036dca932b6_0.webp)

Hold a **Totem of Undying** in your main hand and right-click another player's **legs** to pick them up (they ride on your head). Torso clicks do nothing, and fake players don't participate. Players control whether others may pick them up via `/picking on|off`. Shares the same stack limit as riding (`ridingPlayersStackLimit`).

### Pat Players (`patPatPlayers`)

Pat other real players on the head, cat-petting style: each right-click is a stroking pulse — the target bobs down and up with the rhythm with hearts above their head, while the patted player gets a heart in front of their eyes, a soft directional sound and a per-player opt-out (`/patnod`, persisted in the world save at `carpet-pry-addition/patnod.json`). Fake players are excluded; works with any item in hand and never consumes the interaction.

### Peaceful Players (`peacefulPlayers`)

Enables the `/pvp` command: each player can turn their own PVP on or off. Players with PVP off cannot attack players and take no damage from players (self-inflicted damage unaffected); blocked attacks play a cue sound so the "poke" signal is preserved. Admins can toggle PVP for anyone or server-wide with `/pvp on|off @a`, and `/pvp list` shows everyone who currently has PVP off. States persist across restarts in the world save at `carpet-pry-addition/pvp.json`.

### Who Called Me (`whoCalledMe`)

Naming a player in chat triggers three sounds and a title; self-mentions also work and fake players are excluded. Matching is case-insensitive, with longer names taking precedence; `mention` mode requires @name. `whoCalledMeSound` chooses the sound, and `whoCalledMeHighlight` chooses one of 16 colors, `rainbow` or `false` (default aqua).

### Chat Punishment (`chatPunishment`)

Admins can punish a player's public chat via `/chatpunish`, one mode per player with an optional duration (permanent by default): `mute` (cancels the broadcast, sender gets a notice), `tape` / `idiot` / `censor` / `villager` (fixed replacement texts), `cat` (appends `喵~`), `stutter` (deterministic syllable repeats) and `reverse` (grapheme-cluster reversal that keeps emoji intact). Punishments resolve players through the vanilla profile mechanism (same as /ban), are recorded by UUID in the world save and run on real time, so they survive restarts and relogs and lift automatically on expiry. Commands, private messages and system messages are unaffected; the rewrite goes through the vanilla ChatDecorator extension point and replaces the default display only, leaving chat signatures and the signed original intact.

### MiSide Subtitles (`textAnimation`)

Use `/text @a|player <message>` to play MiSide-style subtitles on vanilla clients. Text types in, holds, then drops and fades; trailing exclamation marks enlarge it. Defaults to white, supports `&` color codes and inline options. The first 12 eligible recipients get entity subtitles; the rest get an actionbar typewriter. Fake players are excluded.

### Timer (`countdown`)

`/timer down 60s` runs a countdown, `/timer up` runs a count-up; both share one set of pause/resume/stop controls and can be styled with a display position (bar / center / actionbar / both) and an effect tier (simple / normal / fancy). Targets everyone or selected players (fake players excluded). The legacy `/countdown` syntax stays as a compatibility entry. Times by real elapsed time; disabled or stopped instantly cleans up every display.

### Click Through (`clickThrough`)

Right-click wall signs, wall hanging signs, wall banners, item frames or paintings to open the container directly behind them. Sneaking keeps normal interaction, and blocked containers remain blocked. Works on vanilla clients; installing ClickThrough Server disables this handler.

---

## Player Scaling

### Player Scale (`playerScale` + `playerScaleMin` / `playerScaleMax`)

Registers the vanilla `minecraft:scale` attribute for players and enables the `/scale set|reset|info` command: players adjust their own size, and admins (or everyone, depending on the mode) can adjust others. Values only need to be greater than 0, bounded by `playerScaleMin` (default 0.1) and `playerScaleMax` (default 1.5). Includes automatic FOV compensation on clients with the mod installed.

### Realistic Player Scale (`playerScalePhysics`)

Optional physics linkage in three modes (gentle √scale curve, gentle + small-size floors, or strictly proportional): movement speed, jump height, step height, interaction range, fall safety and gravity all scale with size. Transient modifiers only — nothing is written to the save.

### Player Scale Linked Entities (`playerScaleLinkedEntities`)

Living entities spawned directly by a player using an item (armor stands, spawn-egg mobs, built golems, ...) inherit the player's size snapshot at spawn time.

---

## Server Transfer

### Play-to-Transfer (`serverTransfer`)

Admins maintain a list of target servers (one address and port per server); `/tpto` shows them as clickable chat entries, and clicking one transfers that player to the target using the vanilla transfer protocol (no proxy, no client mod). `/tptoset addserver|removeserver|reload` manages the list, persisted in `config/carpet-pry-addition/tpto.json`. The target server must set `accepts-transfers=true` in `server.properties`.

### Player Replay (`playerReplay`)

Record yourself or invited players and summon packet-only phantoms on one shared timeline. Phantoms never appear in the player list and never touch blocks, entities or drops. A preparation page selects participants first: normal players invite online players and need consent, while administrators may add players directly. One recording keeps a shared clock with independent per-participant visual/input tracks and online segments; offline gaps contain no frames. `/playereplay` also provides the shared recording list, detail pages, public/private access, per-player favorites and independent viewer instances. Server restart never resumes a recording; normal shutdown saves and ends it. Offline servers backfill skins from Mojang.

### Fake Player Script (`fakePlayerScript`)

Use the same recordings as `/playereplay`, select one participant in `/playerscript setup <ID>`, and let an auto-spawned Carpet fake player replay **operations**, not outcomes: walks into walls, digs whatever is in front, never pathfinds or corrects position mid-round. Only the participant's real online segments are concatenated; segment boundaries release old continuous actions and rebuild the baseline. Configurable count/infinite rounds, inter-round return-to-start or continue, auto-offline on finish, brain-takeover rejection. 1.21/1.21.1 only captures riding input; `run ... force` is required there.

---

## Other Features

- **More End Crystal Types** (`moreEndCrystalTypes`) — place end crystals on crying obsidian; `invulnerable` places an invulnerable crystal. Their beams point to `(0, 128, 0)`.
- **Daydreaming** (`sleepingDuringTheDay`) — sleep in a bed during daytime to skip forward to night (inspired by PCA).
- **Better Snowball** (`betterSnowball`) — snowballs deal knockback and damage to players ("stones hidden in snow").
- **Invisibility Grass** (`invisibleInTallGrass`) — standing with your head inside tall grass grants automatic invisibility.
- **Unicode Command Arguments** (`unicodeArgumentsSupport`) — allows non-ASCII characters (Chinese, Japanese, Korean, etc.) in command arguments, enabling fake players with CJK names (ported from YACA).

---

## Before You Download

- **Required dependencies:** [fabric-carpet](https://modrinth.com/mod/carpet) and [Fabric API](https://modrinth.com/mod/fabric-api) must be installed on the server.
- **Fabric Loader >= 0.16.0** is required.
- **Optional:** [skinrestorer](https://modrinth.com/mod/skinrestorer) is needed for the fake player skin features (`fakePlayerSkinMode` / `fakePlayerSkinSet`).
- This is a **server-side** mod. Place it in the server's `mods` folder. Client installation is only needed for the player-scaling FOV compensation and the `ridingPlayersClientInteract` rule.
- All rules are **off by default** except `ridingPlayersClientInteract` and `countdown` — use `/carpet` or the Carpet configuration files to enable the ones you want.
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
