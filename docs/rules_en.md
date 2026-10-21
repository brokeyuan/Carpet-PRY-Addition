# Carpet Pry Addition Rules Documentation

> Mod ID: `carpet-pry-addition` | Version: `1.3.0`
>
> Total: **35 rules** (`whoCalledMeSound` / `whoCalledMeHighlight` are documented inline as companion rules in the whoCalledMe section)
>
> **Tip: Use `Ctrl+F` to quickly find the rule you want**

---

## Quick Navigation

- [Fake Player (BOT)](#fake-player-bot)
  - [fakePlayerTpp - Fake Player Pearl Station Teleport](#fakeplayertpp---fake-player-pearl-station-teleport)
  - [fakePlayerNameSuggestions - Fake Player Name Suggestions](#fakeplayernamesuggestions---fake-player-name-suggestions)
  - [fakePlayerSkinMode - Fake Player Skin Setting](#fakeplayerskinmode---fake-player-skin-setting)
  - [fakePlayerSkinSet - Fake Player Unified Skin Setting](#fakeplayerskinset---fake-player-unified-skin-setting)
  - [fakePlayerDropAll - Fake Player Continuous Drop](#fakeplayerdropall---fake-player-continuous-drop)
  - [fakePlayerSendto - Fake Player Inventory Link](#fakeplayersendto---fake-player-inventory-link)
  - [fakePlayerBrain - Fake Player Brain](#fakeplayerbrain---fake-player-brain)
- [Bug Fixes (BUGFIX)](#bug-fixes-bugfix)
  - [fixXaeroLib - XaeroLib Compatibility Patch](#fixxaerolib---xaerolib-compatibility-patch)
  - [fixBlueMap - BlueMap Compatibility Patch](#fixbluemap---bluemap-compatibility-patch)
  - [fixEndCrystalSync - Fix End Crystal Sync](#fixendcrystalsync---fix-end-crystal-sync)
  - [fixStandingOnAir - Fix Standing On Air](#fixstandingonair---fix-standing-on-air)
- [Ported Features (PORTING)](#ported-features-porting)
  - [sleepingDuringTheDay - Daydreaming](#sleepingduringtheday---daydreaming)
  - [unicodeArgumentsSupport - Unicode Argument Support](#unicodeargumentssupport---unicode-argument-support)
- [Player Interaction](#player-interaction)
  - [ridingPlayers - Riding Players](#ridingplayers---riding-players)
  - [pickupPlayers - Picking Up Players](#pickupplayers---picking-up-players)
  - [ridingPlayersStackLimit - Player Riding Stack Limit](#ridingplayersstacklimit---player-riding-stack-limit)
  - [ridingPlayersAutoDismount - Dismount on Game Mode Change](#ridingplayersautodismount---dismount-on-game-mode-change)
  - [ridingPlayersClientInteract - Allow Interaction While Riding (Client)](#ridingplayersclientinteract---allow-interaction-while-riding-client)
  - [peacefulPlayers - Peaceful Players](#peacefulplayers---peaceful-players)
  - [patPatPlayers - Pat Pat Players](#patpatplayers---pat-pat-players)
  - [whoCalledMe - Who Called Me](#whocalledme---who-called-me)
- [Survival Features](#survival-features)
  - [playerHat - Player Hat](#playerhat---player-hat)
  - [playerHeadSkin - Player Head Skin](#playerheadskin---player-head-skin)
  - [betterSnowball - Better Snowball](#bettersnowball---better-snowball)
  - [invisibleInTallGrass - Invisibility Grass](#invisibleintallgrass---invisibility-grass)
  - [moreEndCrystalTypes - More End Crystal Types](#moreendcrystaltypes---more-end-crystal-types)
  - [textAnimation - MiSide Subtitles](#textanimation---miside-subtitles)
- [Player Scaling](#player-scaling)
  - [playerScale - Player Scale](#playerscale---player-scale)
  - [playerScaleMin - Player Scale Min](#playerscalemin---player-scale-min)
  - [playerScaleMax - Player Scale Max](#playerscalemax---player-scale-max)
  - [playerScalePhysics - Realistic Player Scale](#playerscalephysics---realistic-player-scale)
  - [playerScaleLinkedEntities - Player Scale Linked Entities](#playerscalelinkedentities---player-scale-linked-entities)

---

## Fake Player (BOT)

### fakePlayerTpp - Fake Player Pearl Station Teleport

Use fake players to quickly use pearl teleport stations. When set to true, enables the /tppset setup command and the /tpp player command. The `/tpp` teleport requires [Carpet TIS Addition](https://modrinth.com/mod/carpet-tis-addition) (its `/player rejoin` respawns the station's fake player where it logged off); without it, `/tpp` fails immediately with a clear message, while `/tppset` and everything else keep working.

| Property | Value |
|----------|-------|
| **Rule Name** | `fakePlayerTpp` |
| **Description** | Use fake players to trigger pearl teleport stations with /tpp and /tppset. /tpp requires Carpet TIS Addition. |
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
| **Description** | Customize the fake player names suggested by the /player command, separated by ',' |
| **Type** | `string` |
| **Default Value** | `Steve,Alex` |
| **Suggested Options** | `Steve,Alex`, `Pry,hsds`, `Pry,hsds,Firework,Food`, `` |
| **Categories** | `PRIMARYUAN`, `BOT` |

---

### fakePlayerSkinMode - Fake Player Skin Setting

After installing the [skinrestorer](https://modrinth.com/mod/skinrestorer) dependency, you can set the skin of fake players. default=no change to fake player skin, summon=fake player uses the summoner's skin, same_skin=fake player uses a unified skin. The target skin is applied before spawn when available; a pending lookup may show the default skin briefly. Skins are session-only and do not affect real players.

| Property | Value |
|----------|-------|
| **Rule Name** | `fakePlayerSkinMode` |
| **Description** | Use skinrestorer to give fake players the summoner’s or a specified player’s skin. Changes are session-only and do not affect real players. |
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

> `summon` uses the summoner’s current skin, falling back to a name lookup when textures are missing. Console and command-block summons use `fakePlayerSkinSet`. `same_skin` resolves that configured player at startup; a pending lookup may briefly show the default skin.

> Changes apply only to the fake player’s current session. They are not saved to skinrestorer and do not overwrite a same-name real player’s skin.

> [Carpet TIS Addition](https://modrinth.com/mod/carpet-tis-addition)’s `/player <name> rejoin` applies the same skin rules. In `summon` mode, the command executor is the summoner.

---

### fakePlayerSkinSet - Fake Player Unified Skin Setting

When fakePlayerSkinMode is same_skin, sets the player name used for the fake player skin.

| Property | Value |
|----------|-------|
| **Rule Name** | `fakePlayerSkinSet` |
| **Description** | When fakePlayerSkinMode is same_skin, the player name whose skin is used for fake players |
| **Type** | `string` |
| **Default Value** | `Brokeyuan` |
| **Suggested Options** | `Brokeyuan`, `hsds`, `` |
| **Categories** | `PRIMARYUAN`, `BOT` |

---

### fakePlayerDropAll - Fake Player Continuous Drop

Adds a dropall sub-command to /player `<name>` that drops the fake player's whole inventory at a configured pace.

| Property | Value |
|----------|-------|
| **Rule Name** | `fakePlayerDropAll` |
| **Description** | Add /player dropall to empty a fake player’s inventory immediately or drop its contents one stack at a time at a chosen pace. |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `BOT`, `COMMAND` |

---

### fakePlayerSendto - Fake Player Inventory Link

Adds a sendto sub-command to /player `<name>` creating one-way inventory item flow between fake players.

| Property | Value |
|----------|-------|
| **Rule Name** | `fakePlayerSendto` |
| **Description** | Add /player sendto to transfer inventory items between fake players at a chosen pace. Links do not survive restarts. |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `BOT`, `COMMAND` |

---

### fakePlayerBrain - Fake Player Brain

Adds 19 mob behavior modes to Carpet fake players with `/player <name> brain`. Fake players keep their player body, inventory and combat; no extra entities or items are created. Server-side only.

Available modes (`off` restores Carpet manual control). Zombie and zombified-piglin modes also use spears on 26.1.2+:

| Mode | Behavior |
|------|----------|
| `zombie` | Chases players in melee. |
| `skeleton` | Shoots with a bow and kites targets; consumes inventory arrows. |
| `pillager` | Shoots with a crossbow; 25-tick charge. |
| `irongolem` | Attacks hostile mobs and hostile fake players, excluding creepers; retaliates when hurt. |
| `spider` | Neutral by day, hostile at night with sprinting pursuit. |
| `wolf` | Follows the command executor and assists in combat; does not teleport to follow. |
| `villager` | Strolls, panics when hurt and avoids zombies. |
| `enderman` | Becomes hostile when stared at and sprints in pursuit. |
| `babyzombie` | Faster melee pursuit than the zombie mode. |
| `witherskeleton` | Melee-chases players, iron golems and piglins; avoids wolves. Hits do not apply Wither. |
| `drowned` | Throws a held trident, otherwise uses melee; alerts nearby drowned and zombified-piglin fakes when hurt. |
| `zombiepiglin` | Retaliates when hurt and alerts nearby same-mode fakes. |
| `vindicator` | Melee-chases players, villagers and iron golems. |
| `piglinbrute` | Always hostile; uses melee and ignores gold armor. |
| `slime` | Hops while idle and pursues targets in a straight line. |
| `magmacube` | Uses the same hopping movement and pursuit as slime. |
| `fish` | Swims, avoids players within 8 blocks, flees when hurt, surfaces for air and flops on land. |
| `pig` | Strolls when idle and flees when hurt. |
| `piglin` | Hostile to players without gold armor; uses melee, crossbows or spears by held weapon, and picks up and equips gear. |
| `off` | Detaches the brain, restoring Carpet manual control |

While a brain is attached, Carpet manual movement, attack and use actions are paused. `brain off` or disabling the rule restores manual control; death, logoff and mode changes detach the old brain.

**Name suffix and keep**: the name tag and Tab list show the mode in red (hostile), blue (neutral) or green (non-hostile). The suffix follows `/carpet language`; English mode keys always work. Original team prefixes are retained, and team membership is restored on detach. `brain <mode> keep` restores the mode and wolf owner within one second after the fake player rejoins, during the current server run only. Restarting the server or running `brain off` clears it.

| Property | Value |
|----------|-------|
| **Rule Name** | `fakePlayerBrain` |
| **Description** | Add /player brain to let fake players act using vanilla mob behavior. |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `BOT`, `COMMAND` |

---

## Bug Fixes (BUGFIX)

### fixXaeroLib - XaeroLib Compatibility Patch

Fixes the issue where higher versions of Xaero combined with LuckPerms cause fake player data loss.

| Property | Value |
|----------|-------|
| **Rule Name** | `fixXaeroLib` |
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

### fixBlueMap - BlueMap Compatibility Patch

Fixes fake players not triggering Fabric API connection events, causing mods like BlueMap to fail tracking fake player join/leave.

| Property | Value |
|----------|-------|
| **Rule Name** | `fixBlueMap` |
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

### fixEndCrystalSync - Fix End Crystal Sync

Fixes the client-side position desync when an end crystal (including invulnerable ones) is pushed by pistons.

| Property | Value |
|----------|-------|
| **Rule Name** | `fixEndCrystalSync` |
| **Description** | Fix client-side position desync when end crystals are pushed by pistons. |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `BUGFIX` |

Position changes are synchronized to observers, including invulnerable crystals. Disable the rule to restore vanilla behavior.

---

### fixStandingOnAir - Fix Standing On Air

Fixes incorrect support-block resends and suppresses the `Player <x> standing on air - force-sending blocks below` INFO log. Only affects 26.3.

| Property | Value |
|----------|-------|
| **Rule Name** | `fixStandingOnAir` |
| **Description** | Fixes incorrect support-block resends and suppresses the standing-on-air INFO log. Only affects 26.3. |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `BUGFIX` |

Defers support-block resends until after player movement and suppresses the standing-on-air INFO log. Resends and the 200-tick cooldown remain active. Disabling the rule restores vanilla behavior; only 26.3 is affected.

#### Related Issues

- [MC-311474](https://bugs.mojang.com/browse/MC/issues/MC-311474) — standing on air log false positives
- [MC-311727](https://mojira.dev/MC-311727) — root cause (check runs before movement), fixed in 26.4 Snapshot 1

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

While holding a Totem of Undying, right-click a player's head to ride on top of them (torso does nothing; fake players excluded).

| Property | Value |
|----------|-------|
| **Rule Name** | `ridingPlayers` |
| **Description** | While holding a Totem of Undying, right-click a player's head to ride on top of them (torso does nothing; fake players excluded) |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### pickupPlayers - Picking Up Players

While holding a Totem of Undying, right-click a player's legs to pick them up onto your head (torso does nothing; fake players excluded).

| Property | Value |
|----------|-------|
| **Rule Name** | `pickupPlayers` |
| **Description** | While holding a Totem of Undying, right-click a player's legs to pick them up onto your head (torso does nothing; fake players excluded) |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### ridingPlayersStackLimit - Player Riding Stack Limit

The maximum number of players that can be stacked when riding and picking up. This limit is shared between riding and picking up. Counted per whole tower: once the total number of players in the tower (including the base player and the one initiating the ride/pickup) reaches the limit, further riding and picking up are rejected.

| Property | Value |
|----------|-------|
| **Rule Name** | `ridingPlayersStackLimit` |
| **Description** | The maximum number of players that can be stacked when riding and picking up. This limit is shared between riding and picking up |
| **Type** | `int` |
| **Default Value** | `16` |
| **Suggested Options** | `16`, `32` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### ridingPlayersAutoDismount - Dismount on Game Mode Change

When a player's game mode changes, players on top will dismount.

| Property | Value |
|----------|-------|
| **Rule Name** | `ridingPlayersAutoDismount` |
| **Description** | When a player's game mode changes, players on top will dismount |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### ridingPlayersClientInteract - Allow Interaction While Riding (Client)

Requires client installation. When there are passengers on top, you can still interact with blocks/entities.

| Property | Value |
|----------|-------|
| **Rule Name** | `ridingPlayersClientInteract` |
| **Description** | Requires client installation. When there are passengers on top, you can still interact with blocks/entities |
| **Type** | `boolean` |
| **Default Value** | `true` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE`, `CLIENT` |

---

### peacefulPlayers - Peaceful Players

Enables /pvp to toggle PVP per player: two-way protection, self-damage unaffected, blocked attacks play a notice sound.

| Property | Value |
|----------|-------|
| **Rule Name** | `peacefulPlayers` |
| **Description** | Toggle PVP per player with /pvp. Disabled players cannot damage or be damaged by other players; settings survive restarts. |
| **Type** | `string` |
| **Default Value** | `"false"` |
| **Suggested Options** | `false`, `true`, `self`, `everyone` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### patPatPlayers - Pat Pat Players

Right-click another real player’s head to pat them: hearts, a soft directional sound and a brief crouch visible to others. Both players must be non-spectators. Works with any item and preserves vanilla interactions; Carpet fake players are excluded. Inspired by [PatPat](https://github.com/LopyMine/PatPat-Plugin).

| Property | Value |
|----------|-------|
| **Rule Name** | `patPatPlayers` |
| **Description** | Right-click another real player’s head to pat them, with hearts, a soft sound and a brief crouch. Works with any item and preserves vanilla interactions. |
| **Type** | `string` |
| **Default Value** | `"false"` |
| **Suggested Options** | `false`, `true`, `sneak` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

`true` allows patting freely; `sneak` requires sneaking. Cooldown starts at 10 ticks, shortens by 2 per combo hit to a 3-tick minimum, and resets after 20 idle ticks. Vanilla interaction reach applies.

Players can opt out with `/patnod off`; the preference survives restarts. The patted player hears the sound from the patter’s direction.

The forced crouch lasts 3 ticks and is visible to other players. Holding sneak keeps the player crouched; riding or carrying passengers skips the crouch to avoid dismounting. Hearts and sound still play.

---

### whoCalledMe - Who Called Me

When a chat message contains a player's name, the mentioned player gets a reminder: three notification dings in a row plus a title showing the message text. `true` = substring matching with longest-name-wins; `mention` = only @name triggers (highlighting the @name as a whole), suited to busy servers.

| Property | Value |
|----------|-------|
| **Rule Name** | `whoCalledMe` |
| **Description** | Notify players by sound and title when named in chat, and highlight their names. Can be limited to @mentions. |
| **Type** | `string` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true`, `mention` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

Matching is case-insensitive. In `true` mode, any substring matches, with longer names taking precedence (`timy` does not also notify `Tim`). In `mention` mode only `@name` matches. Mentioning yourself works; fake players are not notified.

**Companion rules**: `whoCalledMeSound` defaults to `ding`; options are `false`, `true` (alias of `ding`), `ding`, `levelup`, `bell`, `pling` and `hit`. Sounds play three times, 6 ticks apart. `whoCalledMeHighlight` defaults to `aqua` and accepts all 16 vanilla colors, `rainbow` or `false`. `false` disables recoloring; unknown values, including old `none`, fall back to the default.

Online player names are highlighted in player chat and the notification title. Other message styles are retained; system messages such as `/say` are unaffected. Titles fade in for 0.25 s, stay for 3 s, and fade out for 0.5 s. Long titles are cropped to 40 display-width units (CJK characters count as 2), retaining the first matched name. Decorated signed chat may carry an “unverified” badge; unsigned offline-mode chat is unaffected.

## Survival Features

### playerHat - Player Hat

Allows players to wear items on their head and adds the /hat command. When a Totem of Undying is placed in the head slot, the death protection effect is triggered.

| Property | Value |
|----------|-------|
| **Rule Name** | `playerHat` |
| **Description** | Allows players to wear items on their head and adds the /hat command. When a Totem of Undying is placed in the head slot, the death protection effect is triggered |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### playerHeadSkin - Player Head Skin

Real players and Carpet fake players use a player head's full skin while wearing it in the head slot. Swapping heads updates the skin; removing the head, equipping another item, or disabling the rule restores the previous skin, including a configured fake player skin. Also applies when enabling the rule, joining, or respawning with a head equipped.

| Property | Value |
|----------|-------|
| **Rule Name** | `playerHeadSkin` |
| **Description** | Real and fake players use a worn player head's full skin; removing it or disabling the rule restores their original skin |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

- Uses the head's embedded skin first; name-only or UUID-only heads are resolved asynchronously through vanilla services. Keeps the original skin while waiting or on failure. Mob heads and player heads without a valid skin source do not change the skin.
- Changes only the skin: name, UUID, and game state stay intact, and the equipped head remains visible. Server-side; no skin mod required. Works independently of `playerHat`: equipping a player head normally is enough.
- Refreshing a real player's own view may briefly show a loading screen. With an external container open or an item held by the cursor, self-refresh waits until the container is closed and the item is put back; other players see the change immediately.

---

### betterSnowball - Better Snowball

Stones in snow. Allows snowballs to deal knockback and damage to players.

| Property | Value |
|----------|-------|
| **Rule Name** | `betterSnowball` |
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

### moreEndCrystalTypes - More End Crystal Types

Allows placing end crystals on crying obsidian. In vanilla, end crystals can only be placed on obsidian and bedrock; this rule unlocks crying obsidian as a crystal base. When set to `invulnerable`, the placed crystal is invulnerable — identical to the one obtainable in vanilla by pushing a pillar crystal off during dragon resurrection and interrupting the process (`Invulnerable=1` with the bottom slab shown); when set to `true`, the placed crystal is a normal vulnerable one. In both modes the crystal's beam points to the fixed position `(0, 128, 0)` (matching the vanilla resurrection crystal, regardless of placement location). Crystals placed on obsidian/bedrock are unaffected.

| Property | Value |
|----------|-------|
| **Rule Name** | `moreEndCrystalTypes` |
| **Description** | Allow end crystals, including invulnerable ones, on crying obsidian. Placement on obsidian and bedrock is unaffected. |
| **Type** | `string` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true`, `invulnerable` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

Works on vanilla clients. Crying-obsidian crystals point their beam at `(0, 128, 0)`; invulnerable crystals retain their bottom slab, can be pushed by pistons and can be broken in creative. Ordinary placement checks and item consumption still apply.

---

### textAnimation - MiSide Subtitles

/text pops dialogue text up character by character in front of online players (fake players excluded; `@a` for everyone or an online player name to direct — a target is required), holds, then lets the whole sentence drop and fade — the text display effect from the game MiSide.

| Property | Value |
|----------|-------|
| **Rule Name** | `textAnimation` |
| **Description** | Use /text to play MiSide-style subtitles in front of selected players, typing in before dropping and fading out. Works on vanilla clients. |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `FEATURE`, `COMMAND` |

Use `/text @a <message>` or `/text <player> <message>`; the target is required. The console and command blocks work too. Default text is white, with `&` color codes supported. Up to 12 eligible recipients get display-entity subtitles; remaining recipients get an actionbar typewriter. Limit: 128 characters per message and 8 concurrent broadcasts. See [command syntax and options](commands_en.md#text---miside-subtitles).

---

### clickThrough - Click Through

Right-clicking a wall sign, wall hanging sign, wall banner, item frame or painting opens the container mounted directly behind it; sneaking keeps the vanilla interaction.

| Property | Value |
|----------|-------|
| **Rule Name** | `clickThrough` |
| **Description** | Right-clicking a wall sign, wall hanging sign, wall banner, item frame or painting opens the container mounted directly behind it; sneaking keeps the vanilla interaction, so does having no container behind |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

Sneaking retains sign editing and frame rotation. Standing signs and banners are not handled; spectators cannot trigger it. Containers still follow vanilla blocked-opening checks. Installing ClickThrough Server (`clickthrough_server`) disables this rule’s handler to avoid duplicate interactions.

---

## Player Scaling

### playerScale - Player Scale

Adjust player size with `/scale set|reset|info`. Values must be finite and greater than 0, within `playerScaleMin` and `playerScaleMax`; FOV compensation requires client installation.

| Property | Value |
|----------|-------|
| **Rule Name** | `playerScale` |
| **Description** | Adjust player size with /scale within playerScaleMin and playerScaleMax. FOV compensation requires client installation. |
| **Type** | `string` |
| **Default Value** | `"false"` |
| **Suggested Options** | `false`, `true`, `self`, `everyone` |
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
| **Default Value** | `0.1` |
| **Suggested Options** | `0.1`, `0.01`, `0.25`, `0.5` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### playerScaleMax - Player Scale Max

Maximum scale value all players can set via `/scale set`; admins are limited too and can adjust the bound itself by changing this rule.

| Property | Value |
|----------|-------|
| **Rule Name** | `playerScaleMax` |
| **Description** | Maximum scale value all players can set via /scale set; admins can adjust the bound by changing this rule |
| **Type** | `double` |
| **Default Value** | `1.5` |
| **Suggested Options** | `1.5`, `2.0`, `5.0`, `10.0`, `16.0` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### playerScalePhysics - Realistic Player Scale

Player physics scale with size in one of four modes. Requires the playerScale rule to adjust size.

| Property | Value |
|----------|-------|
| **Rule Name** | `playerScalePhysics` |
| **Description** | Scale movement, jumping, reach and gravity with player size, with optional minimum values for small players. |
| **Type** | `string` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true`, `safety`, `strict` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

| Mode | Behavior |
|------|----------|
| `false` | Disabled (default) |
| `true` | Gentle: every linked quantity scales with the √scale curve, no floors |
| `safety` | Gentle+floors (recommended): same curve as true, with small-size (scale<1.0) floors so tiny sizes stay playable |
| `strict` | Movement, reach and jump height scale proportionally; gravity remains √scale; no floors |

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

Modifiers are temporary and are removed when the rule is disabled or size returns to 1.0.

- `safety` applies the floors in the table only below size 1.0.
- Creative flight overrides vertical gravity. Elytra flight and firework movement follow the movement multiplier; wall-collision damage uses vanilla stored speed and is not scaled with displacement.


### playerScaleLinkedEntities - Player Scale Linked Entities

Living entities spawned directly by a player using an item inherit the player's current size. Pairs with the playerScale rule (the size may also come from a minecraft:scale set via /attribute).

| Property | Value |
|----------|-------|
| **Rule Name** | `playerScaleLinkedEntities` |
| **Description** | Living entities spawned by a player using an item inherit that player’s size at spawn. Dispensers, spawners and non-living entities are unaffected. |
| **Type** | `boolean` |
| **Default Value** | `false` |
| **Suggested Options** | `false`, `true` |
| **Categories** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

Size is captured at spawn and does not track later player changes. Existing modifiers, including baby size, still apply. A player at size 1.0 leaves custom entity sizes untouched.

**Coverage**:

- Armor stands, spawn-egg mobs and player-built golems inherit size.
- Projectiles, dropped items, frames, boats, minecarts, TNT, end crystals and paintings are unaffected.
- Dispensers, spawners and other block devices do not trigger it.
