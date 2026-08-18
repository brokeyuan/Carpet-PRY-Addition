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

### playerScaleModifiers - Player Scale Modifiers

Registers the `minecraft:scale` attribute for Player and manages player size through the `/scale set|reset|info` command. Only supported on 1.21.5+.

| Property | Value |
|----------|-------|
| **Rule Name** | `playerScaleModifiers` |
| **Description** | Registers minecraft:scale attribute for Player and adds /scale set/reset/info command. false=disable; self=everyone can only adjust themselves (even OPs); true=players adjust self only, admins adjust anyone; everyone=any player can adjust anyone. Only supported on 1.21.5+ |
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

Minimum scale value that players (non-ops in everyone mode included) can set via `/scale set`; the admin path is not limited by this.

| Property | Value |
|----------|-------|
| **Rule Name** | `playerScaleMin` |
| **Description** | Minimum scale value that players (non-ops in everyone mode included) can set via /scale set; admin path is not limited by this |
| **Type** | `double` |
| **Default Value** | `0.1` |
| **Suggested Options** | `0.1`, `0.25`, `0.5` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### playerScaleMax - Player Scale Max

Maximum scale value that players (non-ops in everyone mode included) can set via `/scale set`; the admin path is not limited by this.

| Property | Value |
|----------|-------|
| **Rule Name** | `playerScaleMax` |
| **Description** | Maximum scale value that players (non-ops in everyone mode included) can set via /scale set; admin path is not limited by this |
| **Type** | `double` |
| **Default Value** | `10.0` |
| **Suggested Options** | `2.0`, `5.0`, `10.0` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### realisticPlayerScale - Realistic Player Scale

When enabled, player speed scales with size: half the size means half the speed, enlarging makes you faster (affects walking and creative flying speed). Requires the playerScaleModifiers rule to adjust size. Only supported on 1.21.5+.

| Property | Value |
|----------|-------|
| **Rule Name** | `realisticPlayerScale` |
| **Description** | Player speed scales linearly with size (minecraft:scale attribute): half the size means half the speed, enlarging makes you faster (affects walking and creative flying speed). Requires the playerScaleModifiers rule to adjust size. Only supported on 1.21.5+ |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |
