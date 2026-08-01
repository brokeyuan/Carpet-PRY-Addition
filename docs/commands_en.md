# Commands Documentation

> **Mod ID**: carpet-pry-addition  
> **Version**: 1.1.2

---

## Quick Navigation

- [Fake Player Pearl Teleport Commands](#fake-player-pearl-teleport-commands)
  - [/tpp - Fake Player Pearl Teleport](#tpp---fake-player-pearl-teleport)
  - [/tppset - Station Management](#tppset---station-management)
- [/hat - Player Hat](#hat---player-hat)
- [Riding Permission Commands](#riding-permission-commands)
  - [/riding - Riding Permission Management](#riding---riding-permission-management)
  - [/pickup - Pickup Permission Management](#pickup---pickup-permission-management)

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

### /pickup - Pickup Permission Management

#### Syntax

```
/pickup on     # Allow other players to pick you up
/pickup off    # Forbid other players from picking you up
```

#### Permission

- Administrators can always use it
- Regular players need the `pickupPlayers` rule enabled

#### Description

Set whether other players are allowed to pick you up (make you ride on their head). When you set it to `on`, other players holding a **Totem of Undying** in their main hand and a **Golden Carrot** in their off-hand can right-click you to pick you up.

#### Interaction Conditions

- Picker (the person below): must hold a **Totem of Undying** in main hand + **Golden Carrot** in off-hand
- Pickee (the person on top): must execute `/pickup on` to allow it
- Stack limit is controlled by the `ridingPlayersPickUpLimit` rule (default: 16), shared with riding

#### Usage Examples

```bash
# Allow other players to pick you up
/pickup on

# Forbid other players from picking you up
/pickup off
```
