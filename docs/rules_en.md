# Carpet Pry Addition Rules Documentation

> Mod ID: `carpet-pry-addition` | Version: `1.1.2`
>
> Total: **16 rules**
>
> **Tip: Use `Ctrl+F` to quickly find the rule you want**

---

## Quick Navigation

- [Fake Player (BOT)](#fake-player-bot)
  - [TppFakePlayer - Fake Player Pearl Station Teleport](#tppfakeplayer---fake-player-pearl-station-teleport)
  - [fakePlayerNameSuggestions - Fake Player Name Suggestions](#fakeplayernamesuggestions---fake-player-name-suggestions)
  - [fakePlayerSkinMode - Fake Player Skin Setting](#fakeplayerskinmode---fake-player-skin-setting)
  - [fakePlayerSkinSet - Fake Player Unified Skin Setting](#fakeplayerskinset---fake-player-unified-skin-setting)
  - [fakePlayerDropStackModifiers - Fake Player Continuous Drop](#fakeplayerdropstackmodifiers---fake-player-continuous-drop)
- [Bug Fixes (BUGFIX)](#bug-fixes-bugfix)
  - [FixXaeroLib - XaeroLib Compatibility Patch](#fixxaerolib---xaerolib-compatibility-patch)
  - [FixBluemap - BlueMap Compatibility Patch](#fixbluemap---bluemap-compatibility-patch)
- [Ported Features (PORTING)](#ported-features-porting)
  - [sleepingDuringTheDay - Daydreaming](#sleepingduringtheday---daydreaming)
  - [unicodeArgumentsSupport - Unicode Argument Support](#unicodeargumentssupport---unicode-argument-support)
- [Player Interaction](#player-interaction)
  - [ridingPlayers - Riding Players](#ridingplayers---riding-players)
  - [pickupPlayers - Picking Up Players](#pickupplayers---picking-up-players)
  - [ridingPlayersPickUpLimit - Player Riding Stack Limit](#ridingplayerspickuplimit---player-riding-stack-limit)
  - [ridingPlayersDismountOnGameModeChange - Dismount on Game Mode Change](#ridingplayersdismountongamemodechange---dismount-on-game-mode-change)
  - [ridingPlayersClientAllowInteractions - Allow Interaction While Riding (Client)](#ridingplayersclientallowinteractions---allow-interaction-while-riding-client)
- [Survival Features](#survival-features)
  - [playerhat - Player Hat](#playerhat---player-hat)
  - [betterSnowBall - Better Snowball](#bettersnowball---better-snowball)
  - [invisibleInTallGrass - Invisibility Grass](#invisibleintallgrass---invisibility-grass)

---

## Fake Player (BOT)

### TppFakePlayer - Fake Player Pearl Station Teleport

Use fake players to quickly use pearl teleport stations. When set to true, enables the /tppset setup command and the /tpp player command.

| Property | Value |
|----------|-------|
| **Rule Name** | `TppFakePlayer` |
| **Description** | Use fake players to quickly use pearl teleport stations. When set to true, enables the /tppset setup command and the /tpp player command |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `BOT`, `COMMAND` |

---

### fakePlayerNameSuggestions - Fake Player Name Suggestions

Customize the fake player list suggested by /player. Use ',' to separate each name.

| Property | Value |
|----------|-------|
| **Rule Name** | `fakePlayerNameSuggestions` |
| **Description** | Customize the fake player list suggested by /player. Use ',' to separate each name |
| **Type** | `string` |
| **Default Value** | `Steve,Alex` |
| **Suggested Options** | `Steve,Alex`, `Pry,hsds`, `Pry,hsds,Firework,Food`, `` |
| **Categories** | `PRIMARYUAN`, `BOT` |

---

### fakePlayerSkinMode - Fake Player Skin Setting

After installing the [skinrestorer](https://modrinth.com/mod/skinrestorer) dependency, you can set the skin of fake players. default=no change to fake player skin, summon=fake player uses the summoner's skin, same_skin=fake player uses a unified skin.

| Property | Value |
|----------|-------|
| **Rule Name** | `fakePlayerSkinMode` |
| **Description** | After installing the skinrestorer dependency, you can set the skin of fake players. default=no change to fake player skin, summon=fake player uses the summoner's skin, same_skin=fake player uses a unified skin |
| **Type** | `string` |
| **Default Value** | `default` |
| **Suggested Options** | `default`, `summon`, `same_skin` |
| **Categories** | `PRIMARYUAN`, `BOT` |

#### Mode Description

| Mode | Behavior |
|------|----------|
| `default` | No change to fake player skin |
| `summon` | Fake player uses the summoner's skin |
| `same_skin` | Fake player uses a unified skin |

---

### fakePlayerSkinSet - Fake Player Unified Skin Setting

When FakeplayersSkinMode is same_skin, sets the player name used for the fake player skin.

| Property | Value |
|----------|-------|
| **Rule Name** | `fakePlayerSkinSet` |
| **Description** | When FakeplayersSkinMode is same_skin, sets the player name used for the fake player skin |
| **Type** | `string` |
| **Default Value** | `Brokeyuan` |
| **Suggested Options** | `Brokeyuan`, `hsds`, `` |
| **Categories** | `PRIMARYUAN`, `BOT` |

---

## Bug Fixes (BUGFIX)

### FixXaeroLib - XaeroLib Compatibility Patch

Fixes the issue where higher versions of Xaero combined with LuckPerms cause fake player data loss.

| Property | Value |
|----------|-------|
| **Rule Name** | `FixXaeroLib` |
| **Description** | Fixes the issue where higher versions of Xaero combined with LuckPerms cause fake player data loss |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `BUGFIX` |

#### Related Issues

- [fabric-carpet #2158](https://github.com/gnembon/fabric-carpet/issues/2158) — Bot's inventory force empty with LuckPerms + xaerolib
- [LuckPerms #4232](https://github.com/LuckPerms/LuckPerms/issues/4232) — Patch provided by [Wzp-2008](https://github.com/Wzp-2008)
- [Xaero's World Map #1191](https://legacy.curseforge.com/minecraft/mc-mods/xaeros-world-map/issues/1191) — fake player data initialize failed

---

### FixBluemap - BlueMap Compatibility Patch

Fixes fake players not triggering Fabric API connection events, causing mods like BlueMap to fail tracking fake player join/leave.

| Property | Value |
|----------|-------|
| **Rule Name** | `FixBluemap` |
| **Description** | Fixes fake players not triggering Fabric API connection events, causing mods like BlueMap to fail tracking fake player join/leave |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `BUGFIX` |

#### Related Issues

- [fabric-carpet #1962](https://github.com/gnembon/fabric-carpet/issues/1962) — Compatibility issue with Bluemap
- [fabric-carpet PR #2142](https://github.com/gnembon/fabric-carpet/pull/2142)
- [BlueMap #598](https://github.com/BlueMap-Minecraft/BlueMap/issues/598) — Unable to properly handle fake players who go offline

---

## Ported Features (PORTING)

### sleepingDuringTheDay - Daydreaming

Allows players to sleep during the day. After sleeping, the time switches to night (referenced from PCA).

| Property | Value |
|----------|-------|
| **Rule Name** | `sleepingDuringTheDay` |
| **Description** | Allows players to sleep during the day. After sleeping, the time switches to night (referenced from PCA) |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `PORTING`, `SURVIVAL` |

---

### unicodeArgumentsSupport - Unicode Argument Support

Allows the use of non-ASCII characters in command arguments (Chinese, Japanese, Korean, etc., can be used to summon fake players with Chinese names) (ported from YACA).

| Property | Value |
|----------|-------|
| **Rule Name** | `unicodeArgumentsSupport` |
| **Description** | Allows the use of non-ASCII characters in command arguments (Chinese, Japanese, Korean, etc., can be used to summon fake players with Chinese names) (ported from YACA) |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `PORTING` |

---

## Player Interaction

### ridingPlayers - Riding Players

When holding a Totem of Undying in the main hand, you can ride other players.

| Property | Value |
|----------|-------|
| **Rule Name** | `ridingPlayers` |
| **Description** | When holding a Totem of Undying in the main hand, you can ride other players |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### pickupPlayers - Picking Up Players

When holding a Totem of Undying in the main hand and a Golden Carrot in the off-hand, you can pick up other players (have them ride on you).

| Property | Value |
|----------|-------|
| **Rule Name** | `pickupPlayers` |
| **Description** | When holding a Totem of Undying in the main hand and a Golden Carrot in the off-hand, you can pick up other players (have them ride on you) |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### ridingPlayersPickUpLimit - Player Riding Stack Limit

The maximum number of players that can be stacked when riding and picking up. This limit is shared between riding and picking up.

| Property | Value |
|----------|-------|
| **Rule Name** | `ridingPlayersPickUpLimit` |
| **Description** | The maximum number of players that can be stacked when riding and picking up. This limit is shared between riding and picking up |
| **Type** | `int` |
| **Default Value** | `16` |
| **Suggested Options** | `16`, `32`, `` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### ridingPlayersDismountOnGameModeChange - Dismount on Game Mode Change

When a player's game mode changes, players on top will dismount.

| Property | Value |
|----------|-------|
| **Rule Name** | `ridingPlayersDismountOnGameModeChange` |
| **Description** | When a player's game mode changes, players on top will dismount |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### ridingPlayersClientAllowInteractions - Allow Interaction While Riding (Client)

Requires client installation. When there are passengers on top, you can still interact with blocks/entities.

| Property | Value |
|----------|-------|
| **Rule Name** | `ridingPlayersClientAllowInteractions` |
| **Description** | Requires client installation. When there are passengers on top, you can still interact with blocks/entities |
| **Type** | `boolean` |
| **Default Value** | `true` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE`, `CLIENT` |

---

## Survival Features

### playerhat - Player Hat

Allows players to wear items on their head and adds the /hat command. When a Totem of Undying is placed in the head slot, the death protection effect is triggered.

| Property | Value |
|----------|-------|
| **Rule Name** | `playerhat` |
| **Description** | Allows players to wear items on their head and adds the /hat command. When a Totem of Undying is placed in the head slot, the death protection effect is triggered |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### betterSnowBall - Better Snowball

Stones in snow. Allows snowballs to deal knockback and damage to players.

| Property | Value |
|----------|-------|
| **Rule Name** | `betterSnowBall` |
| **Description** | Stones in snow. Allows snowballs to deal knockback and damage to players |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### invisibleInTallGrass - Invisibility Grass

Automatically makes the player invisible when their head is located in tall grass.

| Property | Value |
|----------|-------|
| **Rule Name** | `invisibleInTallGrass` |
| **Description** | Automatically makes the player invisible when their head is located in tall grass |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### playerScale - Player Scale

Registers the `minecraft:scale` attribute for Player and manages player size through the `/scale set|reset|info` command (value only needs to be greater than 0, with no fixed bounds; this mod lets any finite positive value through the SCALE attribute, keeping the command feedback consistent with the effective value); also compensates the scaling-induced FOV change (requires the mod installed client-side). Only supported on 1.21.5+.

| Property | Value |
|----------|-------|
| **Rule Name** | `playerScale` |
| **Description** | Registers minecraft:scale attribute for Player and adds /scale set/reset/info command (value only needs to be greater than 0, with no fixed bounds; display on vanilla clients is still clamped beyond the vanilla attribute range 0.0625–16), and compensates the scaling-induced FOV change (client-side install required). false=disable; self=everyone can only adjust themselves (even OPs); true=players adjust self only, admins adjust anyone; everyone=any player can adjust anyone. Only supported on 1.21.5+ |
| **Type** | `string` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `self`, `true`, `everyone` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE`, `COMMAND` |

#### Mode Description

| Mode | Behavior |
|------|----------|
| `false` | Disable the command (default) |
| `self` | Everyone can only adjust themselves (even OPs) |
| `true` | Players adjust themselves only; admins can adjust anyone |
| `everyone` | Any player can adjust anyone |

---

### playerScaleMin - Player Scale Min

Minimum scale value all players can set via `/scale set`; admins are limited too and can adjust the bound itself by changing this rule.

| Property | Value |
|----------|-------|
| **Rule Name** | `playerScaleMin` |
| **Description** | Minimum scale value all players can set via /scale set; admins can adjust the bound by changing this rule |
| **Type** | `double` |
| **Default Value** | `0.01` |
| **Suggested Options** | `0.01`, `0.1`, `0.25`, `0.5` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### playerScaleMax - Player Scale Max

Maximum scale value all players can set via `/scale set`; admins are limited too and can adjust the bound itself by changing this rule.

| Property | Value |
|----------|-------|
| **Rule Name** | `playerScaleMax` |
| **Description** | Maximum scale value all players can set via /scale set; admins can adjust the bound by changing this rule |
| **Type** | `double` |
| **Default Value** | `16.0` |
| **Suggested Options** | `2.0`, `5.0`, `10.0`, `16.0` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### realisticPlayerScale - Realistic Player Scale

Player physics scale with size in one of four modes. Requires the playerScale rule to adjust size. Only supported on 1.21.5+.

| Mode | Behavior |
|------|----------|
| `false` | Disabled (default) |
| `true` | Gentle: every linked quantity scales with the √scale curve, no floors |
| `safety` | Gentle+floors (recommended): same curve as true, with small-size (scale<1.0) floors so tiny sizes stay playable |
| `strict` | Strictly proportional: every linked quantity scales exactly with size, no floors at all |

| Linked dimension | true / safety (gentle) | strict (proportional) | safety floor (scale<1.0 only) |
|-------------------|------------------------|------------------------|-------------------------------|
| Movement speed (walking) | √scale | ×scale | 0.3× |
| Flying speed (creative flight) | √scale | ×scale | 0.3× |
| Elytra gliding / firework boost movement | √scale | ×scale | 0.3× |
| Jump height (jump strength) | √scale (jump height ∝ √scale) | scale^0.75 (jump height ∝ scale) | 0.5× |
| Step height | √scale | ×scale | 0.5× |
| Block interaction/attack range | √scale | ×scale | 0.5× |
| Entity interaction/attack range | √scale | ×scale | 0.5× |
| Safe fall distance | √scale | ×scale | 0.5× |
| Fall speed (gravity) | √scale | √scale | 0.3× |

All attribute modifications use transient modifiers (not persisted to save data) and are removed automatically when the rule is disabled or size returns to 1.0. Design intent of the three modes:

- **true / safety (gentle)**: the √scale curve keeps large players restrained (a 16× player moves 4× faster instead of 16×) and small players floaty; `safety` adds scale<1.0 floors on top (speed/flying/gravity 0.3×, jump/step/interaction/fall 0.5×), keeping extreme sizes (e.g., 0.1) playable — recommended for most servers.
- **strict (proportional)**: movement speed, step height, interaction ranges and safe fall distance scale exactly ×scale; jump strength scales ×scale^0.75 which, paired with gravity ×√scale, keeps jump height proportional to size (a 2× player jumps 2× as high); no floors at all — tiny players may be too weak to interact with blocks. For geometry-focused play.
- **Gravity**: ×√scale in every mode — gravity is an acceleration rather than a size quantity; the square-root curve pairs with the jump curve and keeps terminal velocity under control (linear scaling would give a 16× player a terminal velocity of about 62 blocks/tick, tunneling through the world).

During creative flight vanilla overrides the vertical velocity, so gravity has no effect; during elytra gliding the gravity term does follow the gravity attribute, and gliding/firework movement distance is scaled by the elytra mixin using the same factor as movement speed (mode-dependent, see table above) — larger players glide and boost faster with wider turning radii (geometric similarity), smaller ones slower and floatier. Known trade-off: elytra wall-crash damage is computed from stored velocity (vanilla magnitude) and does not scale with the displacement.

| **Rule Name** | `realisticPlayerScale` |
| **Description** | Physics scale with size (minecraft:scale), four modes: false=off; true=gentle, movement/flying/elytra-firework speed, jump, step height, interaction ranges and safe fall distance all scale with the square root of size (no floors); safety=gentle+floors (recommended), adds small-size floors on top of true (speed/gravity 0.3x, jump/step/interaction/fall 0.5x) so tiny sizes stay playable; strict=strictly proportional, all speeds, step height, interaction ranges and safe fall distance scale exactly with size and jump height stays proportional to size (jump strength x scale^0.75), no floors. Gravity always scales with the square root (larger players fall faster). FOV compensation belongs to the playerScale rule (since v1.1.8). Requires the playerScale rule to adjust size. Only supported on 1.21.5+ |
| **Type** | `string` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true`, `safety`, `strict` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

### playerScaleLinkedEntities - Player Scale Linked Entities

Living entities spawned directly by a player using an item inherit the player's current size. Pairs with the playerScale rule (the size may also come from a minecraft:scale set via /attribute). Only supported on 1.21.5+.

| Property | Value |
|----------|-------|
| **Rule Name** | `playerScaleLinkedEntities` |
| **Description** | Living entities spawned directly by a player using an item inherit the player's current size (stored as a minecraft:scale base-value snapshot): a 0.5x player places 0.5x armor stands; spawn-egg mobs and built iron/snow/copper golems are sized the same way (existing modifiers such as babies stack on top proportionally). Only covers living entities that have the scale attribute; projectiles, dropped items, item frames, boats, minecarts, TNT and other non-living entities are not linked; dispenser/spawner sources are not linked; players at size 1.0 are never modified. Only supported on 1.21.5+ |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

**How it works**: every server-side "player uses item" goes through the ServerPlayerGameMode funnels useItem / useItemOn, and spawned entities are added via ServerLevel.addFreshEntity (addFreshEntityWithPassengers delegates to it per passenger). While the funnel is active the acting player is recorded; on entity add, the player's current `minecraft:scale` value is written into the spawned living entity's scale base value.

**Coverage**:

- ✅ Living entities (minecraft:scale base value written; model + hitbox follow the vanilla attribute and sync to clients): armor stands, spawn-egg mobs (baby modifiers stack proportionally), built iron/snow/copper golems (pumpkin-place construction happens synchronously inside the use funnel)
- ❌ Projectiles, dropped items, item frames, boats, minecarts, TNT, end crystals, paintings: not linked (rendering and hitboxes untouched)
- ❌ Entities produced by dispensers, spawners and other block devices — not a player-use path, not linked
- The written value is a snapshot at spawn time and does not follow later size changes; players at size 1.0 never modify anything (mod-defined entity sizes are untouched)
