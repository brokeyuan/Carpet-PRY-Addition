# Commands Documentation

> **Mod ID**: carpet-pry-addition  
> **Version**: 1.2.0

---

## Quick Navigation

- [Fake Player Pearl Teleport Commands](#fake-player-pearl-teleport-commands)
  - [/tpp - Fake Player Pearl Teleport](#tpp---fake-player-pearl-teleport)
  - [/tppset - Station Management](#tppset---station-management)
- [/hat - Player Hat](#hat---player-hat)
  - [Syntax](#syntax)
  - [Permission](#permission)
  - [Description](#description)
  - [Related Rules](#related-rules)
  - [Usage Examples](#usage-examples)
- [Fake Player Continuous Inventory Drop](#fake-player-continuous-inventory-drop)
  - [Command Syntax](#command-syntax)
  - [Permission](#permission)
  - [Related Rule](#related-rule)
  - [Relationship with Vanilla dropStack](#relationship-with-vanilla-dropstack)
  - [Examples](#examples)
  - [Auto-Stop Conditions](#auto-stop-conditions)
- [/player sendto - Fake Player Inventory Link](#player-sendto---fake-player-inventory-link)
- [/player brain - Fake Player Brain](#player-brain---fake-player-brain)
  - [Command Syntax](#command-syntax)
  - [Transfer Behavior](#transfer-behavior)
  - [Usage Examples](#usage-examples)
- [Player Scale Modifiers](#player-scale-modifiers)
  - [/scale - Player Scale Adjustment](#scale---player-scale-adjustment)
- [Riding Permission Commands](#riding-permission-commands)
  - [/riding - Riding Permission Management](#riding---riding-permission-management)
  - [/picking - Pickup Permission Management](#picking---pickup-permission-management)
- [/pvp - Peaceful Players](#pvp---peaceful-players)
- [/patnod - Pat Interaction Toggle](#patnod---pat-interaction-toggle)
- [/text - MiSide Subtitles](#text---miside-subtitles)

---

## Fake Player Pearl Teleport Commands

### /tpp - Fake Player Pearl Teleport

> **Rule**: `fakePlayerTpp`

#### Syntax

```
/tpp
/tpp <station>
```

With no argument, lists all available stations (display names preferred).

#### Permission

Requires the `fakePlayerTpp` rule to be enabled.

#### Description

Teleport to the specified station (relayed via a fake player).

#### Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `station` | string | Target teleport station name (supports internal name or display name) |

#### Workflow

1. Build the fake player name: alias (if any) or player name + `_` + station name, capped at 16 characters total — the player name is truncated to the remaining space (alias up to 10 characters, the station name is always kept in full; a too-long station leaves no room for the player name)
2. Execute `/player <fakePlayerName> rejoin` (have the existing fake player rejoin)
3. Poll and wait for the fake player to come online (up to 10 seconds)
4. Loop executing `/player <fakePlayerName> use` (the teleporter controls the fake player to right-click an ender pearl) based on the station-level use count (or the global default if not set), with a 0.5-second interval between each use
5. Wait 3 seconds for the teleport to complete
6. Execute `/player <fakePlayerName> kill` (remove the fake player)

#### Usage Examples

```bash
# Teleport to a station named spawn
/tpp spawn

# Teleport to a station named base
/tpp base
```

---

### /tppset - Station Management

> **Rule**: `fakePlayerTpp`

#### Permission

Most subcommands require administrator permission.

#### Description

Manage TPP teleport stations, player aliases, and rule configurations.

#### Subcommands

##### `/tppset spawn <station>`

Sets the fake player spawn point for this station at the current location. The fake player is spawned immediately and automatically goes offline after 3 seconds.

- **Permission**: Requires the `fakePlayerTpp` rule to be enabled
- **Parameters**:
  - `station` - Station name

##### `/tppset set <name> [<displayName>]`

Add a teleport station.

- **Permission**: Admin only
- **Parameters**:
  - `name` - Internal station name
  - `displayName` - Optional, station display name

##### `/tppset remove <station>`

Remove a teleport station.

- **Permission**: Admin only
- **Parameters**:
  - `station` - Station name (supports internal name or display name)

##### `/tppset rename <player> set <alias>`

Set a fake player teleport alias for a player.

- **Permission**: Admin only
- **Parameters**:
  - `player` - Player's real name
  - `alias` - Alias (up to 10 characters, no spaces, Chinese supported)

##### `/tppset rename <player> remove`

Remove a player's fake player teleport alias.

- **Permission**: Admin only
- **Parameters**:
  - `player` - Player's real name

##### `/tppset rule use <count> [station]`

Set the number of times the fake player right-clicks an ender pearl during teleport. You can omit `station` to set the global default, or specify `station` to set an independent count for that station (station-level takes precedence over global).

- **Permission**: Admin only
- **Parameters**:
  - `count` - Number of right-clicks (minimum 1)
  - `station` - Optional, station name (supports internal name or display name). When omitted, sets the global default; when specified, only applies to that station

##### `/tppset rule`

View the current TPP rule configuration.

- **Permission**: Admin only

#### Alias System Description

Administrators can set short aliases for players to build shorter fake player names and avoid exceeding the character limit.

```
Original: VeryLongPlayerName_station (may exceed the character limit)
Alias: VIP
Result: VIP_station (shorter and safe)
```

#### Usage Examples

```bash
# Add a station (no display name)
/tppset set spawn

# Add a station (with display name)
/tppset set farm 农场

# Remove a station
/tppset remove spawn

# Set an alias for a player
/tppset rename VeryLongPlayerName set VIP

# Remove a player alias
/tppset rename VeryLongPlayerName remove

# Set the global use count to 2
/tppset rule use 2

# Set the use count to 3 for a specific station only
/tppset rule use 3 farm

# View the rule configuration
/tppset rule
```

---

## /hat - Player Hat

> **Rule**: `playerHat`

### Syntax

```
/hat
```

### Permission

- Available to everyone (including admins) while the `playerHat` rule is enabled
- Hidden from everyone when the rule is disabled (no admin exemption)

### Description

Wears the main-hand item on the head, swapping it with the item currently on the head.

### Related Rules

**playerHat** - When enabled, placing a Totem of Undying in the head slot first triggers the normal Totem of Undying revive effect upon fatal damage, then additionally grants:
- Regeneration II
- Absorption II
- Fire Resistance I

### Usage Examples

```bash
# Hold a diamond block and wear it on your head
/hat
```

---

## Fake Player Continuous Inventory Drop

> **Rule**: `fakePlayerDropAll`

### Command Syntax

Adds an independent `dropall` sub-command to Carpet's `/player <name>` command tree via Mixin, letting fake players drop all inventory items at a configured pace:

```
/player <name> dropall [once|continuous|interval <ticks>|after <ticks>|perTick <times>|randomly <min> <max>|stop]
```

`<modifier>` has 7 modifiers (the first table row is the top-level form with no modifier):

| Modifier | Syntax | Behavior |
|----------|--------|----------|
| (none) | `dropall` | Drop once immediately (equivalent to `once`) |
| `once` | `dropall once` | Drop once immediately |
| `continuous` | `dropall continuous` | Drop once per server tick until inventory is empty |
| `interval` | `dropall interval <ticks>` | Drop once every `<ticks>` ticks |
| `after` | `dropall after <ticks>` | Drop once after `<ticks>` ticks (one-shot) |
| `perTick` | `dropall perTick <times>` | Drop `<times>` times per second (20 ticks) |
| `randomly` | `dropall randomly <min> <max>` | Use a random value in `[min, max]` ticks as the next interval (re-rolled each time) |
| `stop` | `dropall stop` | Stop the continuous drop task |

### Permission

Reuses Carpet's own permission check on the `/player` command (controlled by Carpet's `commandPlayer` rule), with no additional restriction.

### Related Rule

- **fakePlayerDropAll** — controls the visibility of the entire `dropall` command.
  - When the rule is `false`: the entire `dropall` command is invisible (not tab-completable, not executable); use vanilla `/player <name> dropStack all` for one-shot drops.
  - When the rule is `true`: all modifiers work normally.
  - Rule changes take effect immediately: a Carpet `RuleObserver` re-dispatches the command tree on rule change, so players see visibility changes without relogging.

### Relationship with Vanilla dropStack

- `dropall` is a completely independent sub-command and does not modify Carpet's vanilla `dropStack` command tree.
- `dropStack all` (Carpet vanilla) → drops once immediately
- `dropall continuous` (new) → drops continuously, keeps waiting after inventory is empty
- Running `/player <name> stop` also clears all continuous drop tasks maintained by this mod.

### Examples

```bash
# Drop everything once (equivalent to vanilla dropStack all)
/player Steve dropall

# Fake player drops inventory every tick (keeps waiting when empty, stop anytime)
/player Steve dropall continuous

# Drop once every 10 ticks
/player Steve dropall interval 10

# Drop once after 20 ticks (one-shot)
/player Steve dropall after 20

# Drop 4 times per second
/player Steve dropall perTick 4

# Drop at random intervals between 5 and 20 ticks
/player Steve dropall randomly 5 20

# Stop the continuous drop task
/player Steve dropall stop

# Stop all actions of this fake player (including continuous drop tasks)
/player Steve stop
```

### Auto-Stop Conditions

- `continuous`/`interval`/`perTick`/`randomly` modes: when inventory is empty, the task keeps running and waits for new items to be added; only a manual `stop` will stop it.
- `after` mode: auto-stops after a successful drop; if inventory is empty at the scheduled time, the task keeps checking every tick until an item is available to drop.
- Target fake player disconnects: all related tasks are cleaned up automatically to avoid tick listener leaks.
- If a dropall task is already running for the same fake player, a new trigger is rejected with a hint to `stop` first.

---

## /player sendto - Fake Player Inventory Link

> **Rule**: `fakePlayerSendto`

Adds a sendto sub-command under Carpet's built-in `/player <name>` command tree, creating **one-way** inventory item flow links between fake players: the source keeps transferring its items to the target.

### Command Syntax

- `/player <src> sendto <target>`: create the link and start transferring
- `/player <src> sendto once|continuous|interval <ticks>|after <ticks>|perTick <times>|randomly <min> <max>`: adjust the pace
- `/player <src> sendto stop`: stop and remove all links

### Transfer Behavior

- One stack per trigger, default every tick
- Round-robin across multiple targets
- Items buffer in the source when a target is full (nothing lost)
- Links are memory-only: lost on fake player logout or server restart

### Usage Examples

```bash
# Create link and start transferring
/player Steve sendto Alex

# Transfer one stack every 20 ticks
/player Steve sendto interval 20

# Stop and remove all links
/player Steve sendto stop
```

## /player brain - Fake Player Brain

### Syntax

Adds a standalone `brain` sub-command under Carpet's `/player <name>` command tree via a Mixin, attaching/detaching mob-style AI to fake players:

```text
/player <name> brain [zombie|babyzombie|skeleton|witherskeleton|drowned|zombiepiglin|pillager|vindicator|irongolem|spider|piglin|piglinbrute|slime|magmacube|fish|enderman|wolf|villager|pig|off] [keep]
```

Mode names follow `/carpet language`: with a Chinese language (zh_cn/zh_tw) completion and input use Chinese names (e.g. `brain 僵尸`); English keys always work. `keep` can be appended to either.

| Argument | Behavior |
|----------|----------|
| (none) | Query the current AI mode |
| `<mode> keep` | Keep the brain: automatically restored when the fake player relogs (owner included for wolf); `brain off` clears it |
| `zombie` | Zombie mode: melee-chases the nearest player, swings via native `attack()` |
| `skeleton` | Skeleton mode: ranged attacks + kiting while holding a bow, native bow draw consumes inventory arrows |
| `pillager` | Pillager mode: same as skeleton but with a crossbow (25-tick charge) |
| `irongolem` | Iron golem mode: attacks hostile mobs (creepers excluded) and hostile fake players (also retaliates against its attackers) |
| `spider` | Spider mode: neutral in daylight, hostile at night |
| `wolf` | Wolf mode: follows the executing player and syncs aggro (bites whoever hurts the owner) |
| `villager` | Villager mode: random strolling, panics when attacked, flees from zombies |
| `enderman` | Enderman mode: provoked by being stared at, then sprints at the starer |
| `babyzombie` | Baby zombie mode: faster melee pursuit |
| `witherskeleton` | Wither skeleton mode: melee-chases players/iron golems and piglins, avoids wolves |
| `drowned` | Drowned mode: ranged trident throws + bare-hand melee, group anger broadcast |
| `zombiepiglin` | Zombified piglin mode: neutral; retaliates when hurt and alerts same-mode fakes |
| `vindicator` | Vindicator mode: melee-chases players/villagers/iron golems |
| `piglinbrute` | Piglin brute mode: always-hostile melee, ignores gold armor |
| `slime` | Slime mode: hop-based movement and hop pursuit |
| `magmacube` | Magma cube mode: same as slime |
| `fish` | Fish mode: swims and avoids players in water, flops when beached, surfaces for air |
| `pig` | Pig mode: fully neutral, panics when attacked |
| `piglin` | Piglin mode: hostile to players without gold armor, weapon decides combat style, picks up gear |
| `off` | Detaches the brain, restoring Carpet manual control |

### Permission

Inherits Carpet's `/player` command permission check (controlled by Carpet's `commandPlayer` rule), no extra restriction. The wolf mode's "owner" is the real player executing the command (console execution has no owner; wolf mode fails to attach with a message).

### Related Rules

- **fakePlayerBrain** — controls the visibility of the entire `brain` command.
  - Rule off: the whole `brain` command is invisible (no tab completion, not executable); any attached brain detaches within one tick.
  - Rule changes take effect immediately: the command tree is re-sent via Carpet's `RuleObserver`, no re-login required.

### Examples

```bash
# Query the fake player's current AI mode
/player Steve brain

# Attach zombie mode (a sword in the fake player's hand helps)
/player Steve brain zombie

# Attach skeleton mode (remember to give the fake player a bow and arrows)
/player Steve brain skeleton

# Detach the brain, restoring Carpet manual control
/player Steve brain off
```

### Automatic Detach Conditions

- Running `/player <name> brain off`
- Switching to another mode (the old brain is detached first)
- Disabling the `fakePlayerBrain` rule (detaches within one tick)
- The fake player dying, logging off, or being killed via `/player <name> kill`

After detaching, the fake player returns to a still "mannequin" state with no leftover entities or state (the module never spawns extra entities).

---

## Player Scale Modifiers

### /scale - Player Scale Adjustment

> **Rule**: `playerScale (bounds: playerScaleMin / playerScaleMax)`

#### Command structure (uniform 3-level subcommands: action first, then value/target)

```
scale
  set
    <value>                     # Set scale for self (bounded by playerScaleMin/Max)
    <value> <player>            # Set scale for target player (OP / everyone; not available in self mode)
  reset
    (no args)                   # Reset own scale to 1.0
    <player>                    # Reset target player's scale (OP / everyone; not available in self mode)
  info
    (no args)                   # View own current scale + allowed range
    <player>                    # View target player's current scale (not available in self mode)
```

#### Syntax

```
/scale set <value>                 # Player adjusts own scale (range limited)
/scale set <value> <player>        # Adjust target player (permission: OP / everyone; rejected in self mode)
/scale reset                       # Reset own scale to 1.0
/scale reset <player>              # Reset target player (permission: OP / everyone; rejected in self mode)
/scale info                        # View own scale + allowed range + current mode
/scale info <player>               # View target player's current scale (rejected in self mode)
```

#### Permissions (four-tier rule)

| Rule value | Behavior |
|------------|----------|
| `false`    | Entire `/scale` command is invisible |
| `self`     | Everyone (even OPs) can only `set/reset/info` themselves. Tab-completion shows only own name |
| `true`     | Players can only `set/reset` self; only OP can `set/reset/info` others. Tab-completion after `set <value>` shows only own name |
| `everyone` | Any player can `set/reset/info` any online player; tab-completion shows all online players |

- Rule changes take effect immediately via Carpet `RuleObserver` refreshing the command tree, no relogin required
- `info` is more permissive than modify: non-OPs under `true` mode can still `info` others (read-only, non-destructive); in `self` mode everyone can only `info` themselves; `set/reset` still requires permission

#### Range Control

- Hard bound: value only needs to be greater than 0 (no fixed bounds), applied uniformly to all paths
- `playerScaleMin` (default 0.1): minimum allowed value for all players
- `playerScaleMax` (default 1.5): maximum allowed value for all players
- The soft bounds apply to everyone (including admins, on self and on others); admins who need a wider range can adjust the bounds themselves via `/carpet playerScaleMin` / `/carpet playerScaleMax`

#### Description

Registers the `minecraft:scale` attribute for `Player` and manages it through a unified three-tier subcommand `/scale set|reset|info`. Supports four modes (false / self / true / everyone). In `self` mode everyone (even OPs) can only adjust themselves; in `true` mode admins can adjust anyone; in `everyone` mode anyone can adjust anyone. Tab-completion filters players by current identity; range limits apply per-identity tier.

> **Version note**: the `minecraft:scale` attribute has been provided by vanilla since Minecraft 1.20.5 (snapshot 23w51a); every version this mod supports (1.21~1.21.4, 1.21.5+) can use it, with no version restriction.
>
> **Tip**: Combine with the `playerScalePhysics` rule (`/carpet playerScalePhysics true`) to make speed, jump height, step height, interaction range, safe fall distance and more scale with size, with FOV compensation, for a more realistic experience.

#### Tab completion behavior

| Command position | `self` (anyone) | `true` non-OP | `true` OP | `everyone` |
|------------------|------------------|---------------|-----------|------------|
| `<player>` after `set <value>` | self only | self only | all online | all online |
| `<player>` after `reset`       | self only | self only | all online | all online |
| `<player>` after `info`        | self only | all online | all online | all online |

#### Examples

```bash
# Enable the rule (admin)
/carpet playerScale self     # Everyone can only adjust themselves (even OPs)
/carpet playerScale true     # Players adjust self, OPs adjust anyone
/carpet playerScale everyone # Anyone can adjust anyone

# Shrink yourself to half size
/scale set 0.5

# Reset yourself to default
/scale reset

# View current scale and allowed range
/scale info

# Admin / everyone mode: adjust another player (not available in self mode)
/scale set 2.0 Steve
/scale reset Steve

# View another player's scale (not available in self mode)
/scale info Steve
```

#### Messages (excerpt)

- Set self: `§aYour scale has been set to 0.5x`
- Set other: `§aSteve's scale has been set to 2.0x`
- Out of range: `§cValue 0.05 is out of allowed range (0.1 ~ 1.5)`
- Permission denied (modify): `§cYou don't have permission to modify other players' scale (current mode only allows adjusting yourself)`
- Notified to modified player: `§eAdmin Brokey has adjusted your scale to 2.0x` or `§ePlayer Alice has adjusted your scale to 0.5x`
- `/scale info` example output (self mode):
```
Your current scale: 0.5x (default 1.0x)
Allowed range: 0.1 ~ 1.5
Current mode: self (everyone can only adjust themselves)
```

---

## Riding Permission Commands

### /riding - Riding Permission Management

> **Rule**: `ridingPlayers`

#### Syntax

```
/riding        # toggle: allow <-> forbid being ridden
/riding on     # Allow other players to ride you
/riding off    # Forbid other players from riding you
```

#### Permission

- Available to everyone (including admins) while the `ridingPlayers` rule is enabled
- Hidden from everyone when the rule is disabled (no admin exemption)

#### Description

Set whether other players are allowed to ride you. When you set it to `on`, other players holding a **Totem of Undying** in their main hand can right-click your **head** to ride on top (torso/leg clicks do not trigger riding; fake players excluded).

#### Interaction Conditions

- Rider (the person on top): must hold a **Totem of Undying** in main hand and right-click your **head**
- Mount (the person below): must execute `/riding on` to allow it
- Stack limit is controlled by the `ridingPlayersStackLimit` rule (default: 16)
- When `ridingPlayersAutoDismount` is enabled, game mode changes force passengers to dismount
- When `ridingPlayersClientInteract` is enabled (default), you can still interact with blocks/entities while carrying passengers (requires client-side install)

#### Usage Examples

```bash
# Allow other players to ride you
/riding on

# Forbid other players from riding you
/riding off
```

---

### /picking - Pickup Permission Management

> **Rule**: `pickupPlayers`

#### Syntax

```
/picking        # toggle: allow <-> forbid being picked up
/picking on     # Allow other players to pick you up
/picking off    # Forbid other players from picking you up
```

#### Permission

- Available to everyone (including admins) while the `pickupPlayers` rule is enabled
- Hidden from everyone when the rule is disabled (no admin exemption)

#### Description

Set whether other players are allowed to pick you up (make you ride on their head). When you set it to `on`, other players holding a **Totem of Undying** in their main hand can right-click your **legs** to pick you up (head clicks are riding, torso does nothing; fake players excluded).

#### Interaction Conditions

- Picker (the person below): must hold a **Totem of Undying** in main hand and right-click your **legs**
- Pickee (the person on top): must execute `/picking on` to allow it
- Stack limit is controlled by the `ridingPlayersStackLimit` rule (default: 16), shared with riding

#### Usage Examples

```bash
# Allow other players to pick you up
/picking on

# Forbid other players from picking you up
/picking off
```

---

## /pvp - Peaceful Players

> **Rule**: `peacefulPlayers`

Toggle PVP per player: players with PVP off cannot attack players and take no damage from players (self-damage unaffected). Blocked attacks play a notice sound at the victim's position (heard by both sides), keeping the poke-to-get-attention signal.

### Command Syntax

- `/pvp`: toggle your own PVP (the reply shows the new state)
- `/pvp list`: list all players with PVP off (including registered offline players)
- `/pvp on|off`: toggle your own PVP
- `/pvp on|off <player>`: toggle a player
- `/pvp on|off @a`: server-wide switch (new joiners follow it)

### Permission Modes (peacefulPlayers values)

- `false`: hide the /pvp command
- `self`: everyone can only toggle themselves (even OPs)
- `true`: players toggle themselves, admins toggle anyone
- `everyone`: anyone toggles anyone
- The `@a` global switch is admin-only (except in self mode)

### Server-wide Switch Behavior

- While the global switch is off, all per-player toggles are locked (admins included); only `/pvp on @a` can lift it
- Lifting force-overrides and restores PVP for all players
- Player PVP states persist across server restarts

---

## /patnod - Pat Interaction Toggle

> **Rule**: `patPatPlayers`

Toggles the per-player preference for accepting pats: declined players cannot be patted at all (pats have no effect on them). The state is persisted per player name in config/carpet-pry-patnod.json and survives restarts.

### Syntax

- `/patnod`: toggle whether you accept being patted (the reply shows the new state)
- `/patnod on`: accept being patted
- `/patnod off`: decline being patted

### Permission

- Server players only (the console has no own state)
- The whole command tree is hidden while the `patPatPlayers` rule is disabled

### Related Rules

- `patPatPlayers`: the main pat rule

### Examples

```
/patnod          # toggle: accepted ↔ declined (reply shows the new state)
/patnod off      # declined: players cannot pat you
/patnod on       # accept again
```

---

## /text - MiSide Subtitles

> **Related rule**: `textAnimation`

Pops up dialogue text character by character in front of online players (fake players excluded), holds, then lets the whole sentence drop and fade (the text display effect from the game MiSide). One vanilla text_display entity per character, removed after play; long texts are split into groups at punctuation and played sequentially. The console and command blocks work too. **A target is required**: `@a` for everyone or an online player name to direct.

### Syntax

- `/text @a <message>[|options]`: broadcast to everyone
- `/text <player> <message>[|options]`: direct at a single online player (matched case-insensitively against the current online list; single target only — send multiple commands for more)
- A target is required: a first word that is neither `@a` nor an online player name is rejected (including other `@`-prefixed forms)

Use `||` for a literal `|`; legacy color codes use `&` (e.g. `&c`), `&&` for a literal `&`, and color codes reset style flags like vanilla `§`.

### Permission

- Available to all players (console and command blocks included)
- The whole command tree is hidden while the `textAnimation` rule is disabled
- 10-second per-player send cooldown (console exempt); the error shows the remaining seconds

### Options

| Key | Type | Default | Description |
|------|------|------|------|
| `distance` | float | `2.5` | Spawn distance from each player's view direction (blocks) |
| `scale` | float | `2.2` | Character scale (final pop state) |
| `spacing` | float | auto (0.15×scale) | Letter spacing unit (blocks per width unit) |
| `hold` | int | `40` | Hold between typing and the drop (ticks) |
| `glow` | true/false | `false` | Glowing outline on characters |
| `sound` | true/false | `true` | Click sound per character |
| `drop` | true/false | `true` | Drop after holding (false = fade in place) |

### Behavior Details

- Pop-in: 1 char/tick, each with a random ±45° tilt, 1.8x scale settling and a random y jitter, plus a click sound (volume 1.0 / pitch 1.2)
- **Exclamation gain, uncapped**: the more trailing `!`/`！`, the bigger the whole sentence and the farther its spawn point — scale +0.3 each (no cap), spawn distance ×1.15 each; trailing spaces don't interrupt, other characters do
- Default color white (#FFFFFF), use `&` codes to change
- Drop: gravity 0.03/tick², drag 0.99, one 0.28 bounce on landing, a one-shot random tumble; fading from tick 24 of the drop at -8 opacity/tick
- Grouping: ≤25 characters per group, break point pushed to a punctuation mark within 10 characters ahead; non-final groups get a " - " connector; the next group starts as the previous one drops, with a random ±22.5° yaw and height jitter between groups
- Spawn point: each online player's feet + view direction × distance, at feet +1.3 (one independent copy per player, based on their own position and view); the whole group follows that player's live view (position and orientation) while typing and freezes in world coordinates once typing completes
- Guards: ≤128 characters per sentence, ≤8 concurrent broadcasts server-wide; "no online players can receive subtitles" when no humans are online, players with unloaded chunks are skipped

### Examples

```
/text @a hello everyone                   # broadcast to everyone
/text @a done!!!                          # exclamation gain enlarges the sentence
/text @a &cRed&eYellow&B                  # & color codes
/text @a Hi|scale=3;hold=60               # inline options
/text @a Static text|drop=false           # fade in place, no drop
/text @a Glowing|glow=true;sound=false    # glow, no sound
/text brokeyuan private ping              # direct at a single player
```
