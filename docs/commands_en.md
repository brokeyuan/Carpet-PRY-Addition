# Commands Documentation

> **Mod ID**: carpet-pry-addition  
> **Version**: 1.1.2

---

## Quick Navigation

- [Fake Player Pearl Teleport Commands](#fake-player-pearl-teleport-commands)
  - [/tpp - Fake Player Pearl Teleport](#tpp---fake-player-pearl-teleport)
  - [/tppset - Station Management](#tppset---station-management)
- [/hat - Player Hat](#hat---player-hat)
- [Fake Player Continuous Inventory Drop](#fake-player-continuous-inventory-drop)
- [Player Scale Modifiers](#player-scale-modifiers)
- [Riding Permission Commands](#riding-permission-commands)
  - [/riding - Riding Permission Management](#riding---riding-permission-management)
  - [/picking - Pickup Permission Management](#picking---pickup-permission-management)

---

## Fake Player Pearl Teleport Commands

### /tpp - Fake Player Pearl Teleport

#### Syntax

```
/tpp <station>
```

#### Permission

Requires the `TppFakePlayer` rule to be enabled.

#### Description

Teleport to the specified station (relayed via a fake player).

#### Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `station` | string | Target teleport station name (supports internal name or display name) |

#### Workflow

1. Build the fake player name: alias (if any) or player name (truncated to 10 characters) + `_` + station name
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

#### Permission

Most subcommands require administrator permission.

#### Description

Manage TPP teleport stations, player aliases, and rule configurations.

#### Subcommands

##### `/tppset spawn <station>`

Sets the fake player spawn point for this station at the current location. The fake player is spawned immediately and automatically goes offline after 3 seconds.

- **Permission**: Requires the `TppFakePlayer` rule to be enabled
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
  - `alias` - Alias (up to 12 characters)

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

### Syntax

```
/hat
```

### Permission

- Administrators can always use it
- Regular players need the `playerhat` rule enabled

### Description

Wears the main-hand item on the head, swapping it with the item currently on the head.

### Related Rules

**playerhat** - When enabled, placing a Totem of Undying in the head slot first triggers the normal Totem of Undying revive effect upon fatal damage, then additionally grants:
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

### Command Syntax

Adds an independent `dropall` sub-command to Carpet's `/player <name>` command tree via Mixin, letting fake players drop all inventory items at a configured pace:

```
/player <name> dropall [once|continuous|interval <ticks>|after <ticks>|perTick <times>|randomly <min> <max>|stop]
```

`<modifier>` can be one of the following seven:

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

- **fakePlayerDropStackModifiers** — controls the visibility of the entire `dropall` command.
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

## Player Scale Modifiers

### /scale - Player Scale Adjustment

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

- `playerScaleMin` (default 0.1): minimum allowed value
- `playerScaleMax` (default 10.0): maximum allowed value
- Bounded paths: self → self, plus non-OP in everyone mode acting on others, plus self mode (including OP on self)
- **Unbounded**: OP acting on others (non-self mode), any value is allowed (hard cap 0.0~100.0)

#### Description

Registers the `minecraft:scale` attribute for `Player` and manages it through a unified three-tier subcommand `/scale set|reset|info`. Supports four modes (false / self / true / everyone). In `self` mode everyone (even OPs) can only adjust themselves; in `true` mode admins can adjust anyone; in `everyone` mode anyone can adjust anyone. Tab-completion filters players by current identity; range limits apply per-identity tier.

> **Version requirement**: `minecraft:scale` attribute was added to vanilla in Minecraft 1.21.5; 1.21~1.21.4 servers show an unsupported-version message.

#### Tab completion behavior

| Command position | `self` (anyone) | `true` non-OP | `true` OP | `everyone` |
|------------------|------------------|---------------|-----------|------------|
| `<player>` after `set <value>` | self only | self only | all online | all online |
| `<player>` after `reset`       | self only | self only | all online | all online |
| `<player>` after `info`        | self only | all online | all online | all online |

#### Examples

```bash
# Enable the rule (admin)
/carpet playerScaleModifiers self     # Everyone can only adjust themselves (even OPs)
/carpet playerScaleModifiers true     # Players adjust self, OPs adjust anyone
/carpet playerScaleModifiers everyone # Anyone can adjust anyone

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
- Out of range: `§cValue 0.05 is out of allowed range (0.1 ~ 10.0)`
- Permission denied (modify): `§cYou don't have permission to modify other players' scale (current mode only allows adjusting yourself)`
- Notified to modified player: `§eAdmin Brokey has adjusted your scale to 2.0x` or `§ePlayer Alice has adjusted your scale to 0.5x`
- `/scale info` example output (self mode):
```
Your current scale: 0.5x (default 1.0x)
Allowed range: 0.1 ~ 10.0
Current mode: self (everyone can only adjust themselves)
```

---

## Riding Permission Commands

### /riding - Riding Permission Management

#### Syntax

```
/riding on     # Allow other players to ride you
/riding off    # Forbid other players from riding you
```

#### Permission

- Administrators can always use it
- Regular players need the `ridingPlayers` rule enabled

#### Description

Set whether other players are allowed to ride you. When you set it to `on`, other players holding a **Totem of Undying** in their main hand can right-click you to ride on your head.

#### Interaction Conditions

- Rider (the person on top): must hold a **Totem of Undying** in main hand
- Mount (the person below): must execute `/riding on` to allow it
- Stack limit is controlled by the `ridingPlayersPickUpLimit` rule (default: 16)
- When `ridingPlayersDismountOnGameModeChange` is enabled, game mode changes force passengers to dismount
- When `ridingPlayersClientAllowInteractions` is enabled (default), you can still interact with blocks/entities while carrying passengers (requires client-side install)

#### Usage Examples

```bash
# Allow other players to ride you
/riding on

# Forbid other players from riding you
/riding off
```

---

### /picking - Pickup Permission Management

#### Syntax

```
/picking on     # Allow other players to pick you up
/picking off    # Forbid other players from picking you up
```

#### Permission

- Administrators can always use it
- Regular players need the `pickupPlayers` rule enabled

#### Description

Set whether other players are allowed to pick you up (make you ride on their head). When you set it to `on`, other players holding a **Totem of Undying** in their main hand and a **Golden Carrot** in their off-hand can right-click you to pick you up.

#### Interaction Conditions

- Picker (the person below): must hold a **Totem of Undying** in main hand + **Golden Carrot** in off-hand
- Pickee (the person on top): must execute `/picking on` to allow it
- Stack limit is controlled by the `ridingPlayersPickUpLimit` rule (default: 16), shared with riding

#### Usage Examples

```bash
# Allow other players to pick you up
/picking on

# Forbid other players from picking you up
/picking off
```
