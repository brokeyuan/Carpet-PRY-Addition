# Commands Documentation

> **Mod ID**: carpet-pry-addition  
> **Version**: 1.3.1

---

## Quick Navigation

- [Fake Player Pearl Teleport Commands](#fake-player-pearl-teleport-commands)
  - [/tpp](#tpp---fake-player-pearl-teleport)
  - [/tppset](#tppset---station-management)
- [/hat - Player Hat](#hat---player-hat)
- [Fake Player Continuous Inventory Drop](#fake-player-continuous-inventory-drop)
- [/player sendto - Fake Player Inventory Link](#player-sendto---fake-player-inventory-link)
- [/player brain - Fake Player Brain](#player-brain---fake-player-brain)
- [/fakeclone - Same-UUID Fake Player Clones](#fakeclone---same-uuid-fake-player-clones)
- [Player Scale Modifiers](#player-scale-modifiers)
  - [/scale](#scale---player-scale-adjustment)
- [Riding Permission Commands](#riding-permission-commands)
  - [/riding](#riding---riding-permission-management)
  - [/picking](#picking---pickup-permission-management)
- [/pvp - Peaceful Players](#pvp---peaceful-players)
- [/patnod - Pat Interaction Toggle](#patnod---pat-interaction-toggle)
- [/text - MiSide Subtitles](#text---miside-subtitles)
- [/countdown - Countdown](#countdown---countdown)
- [Server Transfer Commands](#server-transfer-commands)
  - [/tpto](#tpto---server-transfer)
  - [/tptoset](#tptoset---server-management)
- [/timer - Timer](#timer---timer)
- [/countdown - Legacy Countdown](#countdown---legacy-countdown-compatibility-entry)
- [/fakegame - Fake Games](#fakegame---fake-games)
- [/chatpunish - Chat Punishment](#chatpunish---chat-punishment)

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

Station completion matches internal-name or display-name prefixes, ignoring case, and inserts the display name (or the internal name if unset). After `/tppset set qwp 全物品`, `/tpp q`, `/tpp qwp` and `/tpp 全` all suggest `全物品`. Abbreviations come from internal names; pinyin is not generated. Station arguments in `/tppset spawn`, `remove` and `rule use` share this completion.

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

Exchanges the complete main-hand and head stacks, preserving counts and components. `/hat` is not subject to automatic-hat item exclusions.

With `playerHat` enabled, main-hand air use also swaps whole stacks when the offhand is empty. Tools and usable items are excluded, and survival swaps respect Binding Curse. The shortcut is blocked while the head holds a regular helmet, turtle shell or totem of undying; the command is not subject to this. Datapacks can add item types to `#carpet-pry-addition:hat_use_blacklist`; external scripts with no detectable use signal should use this tag. Inventory head-slot drag capacity remains one.

### Related Rules

**playerHat** - If neither hand triggers totem protection, a head-slot totem can prevent fatal damage. It consumes one totem, removes harmful effects, restores 1 health point and grants:

- Regeneration II (45 seconds)
- Absorption II (5 seconds)
- Fire Resistance I (40 seconds)

**playerHeadSkin** - With both rules enabled, hold a player head and run `/hat` to use its full skin; removing it or disabling the rule restores the original skin. This rule works independently of `playerHat`: equipping a player head normally is enough. Mob heads and heads without a valid skin do not trigger it.

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
| `once` | `dropall once` | Empty the whole inventory immediately |
| `continuous` | `dropall continuous` | Drop one stack per tick; wait for new items when empty |
| `interval` | `dropall interval <ticks>` | Drop one stack every `<ticks>` ticks |
| `after` | `dropall after <ticks>` | Drop one stack after `<ticks>` ticks (one-shot) |
| `perTick` | `dropall perTick <times>` | Drop one stack `<times>` times per second (20 ticks) |
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

# Fake player drops one stack every tick (keeps waiting when empty, stop anytime)
/player Steve dropall continuous

# Drop one stack every 10 ticks
/player Steve dropall interval 10

# Drop one stack after 20 ticks (one-shot)
/player Steve dropall after 20

# Drop one stack 4 times per second
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
- `after` mode: drops one stack, then stops; if inventory is empty at the scheduled time, the task keeps checking every tick until an item is available to drop.
- Target fake player disconnects: all related tasks are cleaned up automatically to avoid tick listener leaks.
- If a dropall task is already running for the same fake player, a new trigger is rejected with a hint to `stop` first.

---

## /player sendto - Fake Player Inventory Link

> **Rule**: `fakePlayerSendto`

Adds a sendto sub-command under Carpet's built-in `/player <name>` command tree, creating **one-way** inventory item flow links between fake players: the source keeps transferring its items to the target.

### Permission

Reuses Carpet's own permission check on the `/player` command (controlled by Carpet's `commandPlayer` rule), with no additional restriction.

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
/player <name> brain [zombie|babyzombie|skeleton|witherskeleton|drowned|zombiepiglin|pillager|vindicator|irongolem|spider|piglin|piglinbrute|slime|magmacube|fish|enderman|wolf|villager|pig|allay|fox|witch|coppergolem|off] [keep]
```

Mode names follow `/carpet language`: with a Chinese language (zh_cn/zh_tw) completion and input use Chinese names (e.g. `brain 僵尸`); English keys always work. `keep` can be appended to either.

| Argument | Behavior |
|----------|----------|
| (none) | Query the current AI mode and diagnostics (target, pathing, movement) |
| `<mode> keep` | Restore the mode and owner (wolf hate target / allay delivery target) when the fake rejoins during this server run; restarting or `brain off` clears it |
| `zombie` | Chases players in melee. |
| `skeleton` | Shoots with a bow and kites targets; consumes inventory arrows; seeks shade when burning in daylight. |
| `pillager` | Shoots with a crossbow; 25-tick charge. |
| `irongolem` | Attacks hostile mobs and hostile fake players, excluding creepers; retaliates when hurt. |
| `spider` | Neutral by day, hostile at night with sprinting pursuit and close-range pounce. |
| `wolf` | Follows the executor and assists in combat (flees first when hurt); stares at players holding food. |
| `villager` | Lives by the vanilla schedule; a claimed composter is a long-term farmer job ([Villager-Farmer]) kept across gathering/sleep/panic pauses; works crops and composts while at work. |
| `enderman` | Becomes hostile when stared at (64-block range) and sprints in pursuit. |
| `babyzombie` | Faster melee pursuit than the zombie mode. |
| `witherskeleton` | Melee-chases players, iron golems and piglins; avoids wolves. Hits do not apply Wither. |
| `drowned` | Throws a held trident, otherwise uses melee; alerts nearby drowned and zombified-piglin fakes when hurt. |
| `zombiepiglin` | Retaliates when hurt and alerts nearby same-mode fakes. |
| `vindicator` | Melee-chases players, villagers and iron golems. |
| `piglinbrute` | Always hostile; uses melee and ignores gold armor. |
| `slime` | Hops while idle and pursues targets in a straight line. |
| `magmacube` | Uses the same hopping movement and pursuit as slime. |
| `fish` | Swims, avoids players within 8 blocks, flees when hurt, surfaces for air and flops on land. |
| `pig` | Strolls when idle, flees when hurt, and follows players holding carrots. |
| `piglin` | Hostile to players without gold armor; uses melee, crossbows or spears by held weapon, and picks up and equips gear. |
| `allay` | Collects items matching the offhand sample and delivers them to the executing player; console-attached allays have no default target and only deliver to a valid note block. Note-block listening follows the brain session, switches to a note block for 30 seconds and respects a 240-tick delivery cooldown. |
| `fox` | Picks mature sweet berries, eats FOOD items when actually hungry, hunts chickens/rabbits and avoids wolves. |
| `witch` | Uses real inventory potions; self-healing drinks potions, attack potions follow the vanilla 60-tick/10-block cycle with target/team/line-of-sight rechecks; no fake throws without stock. |
| `coppergolem` | Takes up to 16 items from copper chests and books the actual inventory delta; pausing keeps cargo and resumes the matching seek phase, both double-chest halves share one identity; 1.21.10+. |
| `off` | Detaches the brain, restoring Carpet manual control (combat team kept) |

**Combat teams** (independent of the brain, settable without one):

| Argument | Behavior |
|----------|----------|
| `team` | Query the combat team |
| `team <team>` | Join or switch team (names support Chinese and English, Tab completion for existing teams) |
| `team off` | Leave the team |

Fakes sharing the same non-empty team name are allies (across brain modes): they never target each other, never retaliate, never join group aggro, staring does not trigger anger, and attacks never land. Switching to the same team mid-combat immediately revokes the invalid targets. Enemies of different or no teams fight under each mode's normal rules; passive modes (pig/fish/villager) are unaffected. Real players never carry team identity; difficulty, creative/spectator and owner-exemption gates stay in effect. Friendly-fire protection is judged at the moment of attack: in-flight projectiles, sweep hits and an already-raised spear thrust (a settle window of at most 1 tick) do not track team changes in flight — vanilla trajectories are not intercepted. Teams are runtime data: brain swap, `brain off` and rejoin keep them; server restart clears them.

While a brain is attached Carpet's manual movement, attack and use commands are suspended; `brain off` or disabling the rule restores them. Death, rejoin and mode switches detach the old brain.

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

# Two zombie fakes in the same team never fight each other
/player ZombieA brain zombie
/player ZombieB brain zombie
/player ZombieA brain team red
/player ZombieB brain team red

# Detach the brain, restoring Carpet manual control
/player Steve brain off

# Query / leave the combat team (kept across brain off)
/player Steve brain team
/player Steve brain team off
```

### Automatic Detach Conditions

- Running `/player <name> brain off`
- Switching to another mode (the old brain is detached first)
- Disabling the `fakePlayerBrain` rule (detaches within one tick)
- The fake player dying, logging off, or being killed via `/player <name> kill`

After detaching, the fake player returns to a still "mannequin" state with no leftover entities or state (the module never spawns extra entities).

---

## /fakeclone - Same-UUID Fake Player Clones

Available when the `fakePlayerClones` rule is on (level 2+). Multiple real Carpet fake players share one UUID and skin online at the same time, with independent positions/entity IDs, a shared inventory and genuine chunk loading.

### Syntax

```
/fakeclone list                          List all clone groups
/fakeclone status <name>                 Group details (cloneId/EntityId/position/dimension)
/fakeclone spawn <name>                  Spawn another clone of that identity (same as /player <name> spawn)
/fakeclone kill <name>                   Remove the whole group gracefully (no death, no drops), saves a snapshot
/fakeclone kill <name> <cloneId>         Remove one clone gracefully; the group and shared inventory continue
/fakeclone rejoin <name>                 Restore member count/dimensions/positions/inventory from the snapshot
/fakeclone select <name> <cloneId>       Address a single clone (echoes EntityId and position)
/fakeclone action <action>               Run an action on all clones (settled per cloneId order)
/fakeclone action <action> <name>        Run an action on that group
/fakeclone action <action> <name> <id>   Run an action on one specific clone
```

Actions: `stop` `drop` `dropstack` `swaphands` `jump` `use` `attack` `sneak` `unsneak` `slot1`~`slot9` (reuses Carpet ActionPack; `slotN` is hotbar slot N, action state and selected slot stay per clone, no global selection).

### Notes

- `/player <name> spawn` adds one clone per invocation for an existing group instead of reporting "already logged on"; vanilla fakes are unaffected.
- On exit each member's dimension/position/rotation/game mode is written to `<world>/playerdata/<name>-clones.json`; the shared inventory persists through the primary instance's vanilla playerdata (full data components), so `rejoin` works across restarts.
- `spawn`/`rejoin` are rejected while a same-UUID real player is online; `carpet fakePlayerClones false` is refused while members are online or exiting — exit the group first.
- `rejoin` validates the snapshot and all dimensions first; online/exiting groups are rejected and a failed attempt rolls back only its new members.
- `give`/`clear`/`item`/`enchant`/`loot` run once per account for a clone group; body commands such as `tp`/`execute`/`effect` still run per entity.
- `#cloneId` increments per group and is never reused; the snapshot stores it, so `cloneId` and display UUIDs survive restarts.

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
| `true`     | Players can only `set/reset` self; OPs can `set/reset` others; everyone can `info` others. Non-OP completion for `set` shows only self |
| `everyone` | Any player can `set/reset/info` any online player; tab-completion shows all online players |

- Rule changes take effect immediately via Carpet `RuleObserver` refreshing the command tree, no relogin required
- `info` is more permissive than modify: non-OPs under `true` mode can still `info` others (read-only, non-destructive); in `self` mode everyone can only `info` themselves; `set/reset` still requires permission

#### Range Control

- Hard bound: value must be finite and greater than 0, applied uniformly to all paths
- `playerScaleMin` (default 0.1): minimum allowed value for all players
- `playerScaleMax` (default 1.5): maximum allowed value for all players
- The soft bounds apply to everyone (including admins, on self and on others); admins who need a wider range can adjust the bounds themselves via `/carpet playerScaleMin` / `/carpet playerScaleMax`

#### Description

Registers the `minecraft:scale` attribute for `Player` and manages it through a unified three-tier subcommand `/scale set|reset|info`. Supports four modes (false / self / true / everyone). In `self` mode everyone (even OPs) can only adjust themselves; in `true` mode admins can adjust anyone; in `everyone` mode anyone can adjust anyone. Tab-completion filters players by current identity; the same size bounds apply to everyone.

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
/scale set 1.5 Steve
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

Toggles the per-player preference for accepting pats: declined players cannot be patted at all (pats have no effect on them). The state is persisted per player name in the world save at `carpet-pry-addition/patnod.json` (follows the world; the old config path is auto-migrated) and survives restarts.

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
| `distance` | float | `2.5` | Offset along the view direction, with a spawn height of feet +1.3 blocks |
| `scale` | float | `2.2` | Character scale (final pop state) |
| `spacing` | float | auto (0.15×scale) | Letter spacing unit (blocks per width unit) |
| `hold` | int | `80` | Hold between typing and the drop (ticks) |
| `glow` | true/false | `false` | Glowing outline on characters |
| `sound` | true/false | `true` | Click sound per character |
| `drop` | true/false | `true` | Drop after holding (false = fade in place) |

### Behavior Details

- Pop-in: one character every 2 ticks, each with a random ±45° tilt, 1.8x scale settling and a random y jitter, plus a click sound (volume 1.0 / pitch 1.2)
- **Exclamation gain, uncapped**: the more trailing `!`/`！`, the bigger the whole sentence and the farther its spawn point — final scale = base scale × (1 + 0.3 × count), distance = base distance × (1 + 0.15 × count); trailing spaces don't interrupt, other characters do
- Default color white (#FFFFFF), use `&` codes to change
- Drop: gravity 0.03/tick², drag 0.99, one 0.28 bounce then a resting fade on landing, a one-shot random tumble; fading from tick 24 of the drop at -8 opacity/tick
- Grouping: ≤25 characters per group, break point pushed to a punctuation mark within 10 characters ahead; non-final groups get a " - " connector; the next group starts as the previous one drops, with a random ±22.5° yaw and height jitter between groups
- Spawn point: each online player's feet + view direction × distance, at feet +1.3 (one independent copy per player, based on their own position and view); the glyph plane stays upright and fixed (never tilts with pitch), with position and orientation locked at spawn — they no longer track the sender's movement or view
- Recipient limit: the first 12 eligible recipients get entity subtitles; the rest get an actionbar typewriter.
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


---

## /timer - Timer

> **Related rule**: `countdown` (on by default)

The unified entry for count-up and countdown: one active timer per server, both directions sharing pause/resume/stop controls; four display positions and three effect tiers combine freely, and the common cases are one short command. Timing follows real elapsed time (low TPS does not stretch it), implemented purely with server packets — vanilla clients work.

### Permission

- Permission level 2: admins and command blocks, hidden from regular players
- When the `countdown` rule is off the command stays visible and reports "feature is off" on use

### Syntax

- `/timer down <time> [options…] [-- label]` — start a countdown (1s ~ 24h)
- `/timer up [options…] [-- label]` — start a count-up (from zero, no cap, never ends by itself)
- `/timer pause` / `resume` / `stop` / `status`
- `/timer time <time>` — set the remaining time directly (countdown only)
- `/timer add <time>` / `remove <time>` — add / remove time (countdown only; removing to zero completes)
- `/timer style <simple|normal|fancy>` — change the effect tier
- `/timer display <bar|center|actionbar|both>` — change the display position
- `/timer sound <on|off>` — toggle sounds for this session
- `/timer label <label>` — change the label
- `/timer players <selector>` — change targets
- `/timer` — brief status when a timer is active; usage examples and last result otherwise

### Options and defaults

Option words come in any order, case-insensitive, at most one per category; everything after `--` is the label (1–128 characters) and stops option parsing.

| Category | Values | Notes |
|----------|--------|-------|
| Effect | `simple` / `normal` / `fancy` | default simple |
| Position | `bar` / `center` / `actionbar` / `both` | default bar for down, actionbar for up; fancy without an explicit position uses both |
| Silent | `silent` | disables all sounds for this timer |
| Targets | vanilla selectors like `@s`, `@a`, `@a[team=red]` | down defaults to everyone; up run by a player defaults to self, console/command blocks default to everyone |

Most common: `/timer down 60s`, `/timer up`, `/timer down 5m fancy -- Event start`, `/timer down 5m fancy bar silent -- Quiet event`.

### Display positions

| Position | Idle | Events |
|----------|------|--------|
| `bar` | top boss bar "label · remaining/elapsed time"; down shows progress, up stays full | everything updates the bar only |
| `center` | big time in the center with the label as subtitle, always visible; last 10s of a countdown switch to single seconds | pause/resume marked in the subtitle |
| `actionbar` | action bar "label · remaining/elapsed time" | status and result shown only there |
| `both` | bar always visible, center idle | start/pause/resume flash 2s, finish 3s; countdown ticks the last 5s centrally (fancy 10s) |

### Effect tiers

| Content | simple | normal | fancy |
|---------|--------|--------|-------|
| Colors | white text and bar | cyan bar, yellow last 10s, red last 3s | cyan bar, gold under 60s, yellow under 10s, red under 3s |
| Start | appears directly | short fade-in | opening transition and chime |
| Milestones | none | none | one prompt each at 60s/30s |
| Last seconds | both last 5s, silent | both last 5s with 3/2/1 ticks | both last 10s with rising pitch |
| Finish | static result, silent | green result with a short sound | green result with a stronger finish sound |

simple is silent by default; `/timer sound on` enables sounds for the running session. A count-up plays no ticking sounds, only start/stop. `silent` and `sound off` always override every sound.

### Targets

- down: omitted or standalone `@a` means everyone dynamically (joining players get the display automatically); up run by a player defaults to self
- Any other selector is snapshotted at execution: quitting players lose the display and rejoining ones regain it; Carpet fake players are always excluded, no real match is an error
- Selectors start with `@` and keep their brackets/quotes intact when split; bare player names are not target words (use `-- label` for labels)

### Behavior

- Starting up/down while a timer is active is rejected; during the 3-second result window a new timer may replace the old display directly
- Pausing freezes real time; every position shows "paused"; resuming continues from the frozen point
- up rejects time/add/remove ("count-up elapsed time cannot be modified"); down removing to zero counts as finished
- down reaching zero naturally: shows "label · finished" for 3s with one finish sound; `/timer stop` on a countdown only reports "stopped, xx left" to the caller, no finish sound and no fake natural end; up stop settles the final elapsed time (milliseconds) and shows it for 3s
- Time jumps (time/add/remove) never replay skipped digits, milestones or sounds; joining players only receive the current frame
- The last result is kept in memory, visible via `/timer` or `/timer status`; cleared on restart
- Coexists safely with /text and whoCalledMe: only its own display is removed; other notifications on the same channel may temporarily cover the timer

### Examples

```
/timer down 60s                              # server-wide countdown (top bar)
/timer up                                    # count-up visible to yourself (action bar)
/timer down 5m fancy -- Event start          # fancy tier, center + top
/timer down 5m fancy bar silent -- Quiet     # fancy visuals, top only, no sound
/timer up @a[team=red] -- Red team test      # targeted count-up
/timer pause / resume / stop / status        # run control
/timer add 30s / remove 10s / time 60s       # countdown adjustments
/timer style fancy / display both            # change tier and position live
/timer sound off / label Final / players @a  # sound, label and targets
```

---

## /countdown - Legacy Countdown (compatibility entry)

> **Related rule**: `countdown` (on by default)

The legacy syntax is kept and shares the same manager and active session with /timer (both entries view and control the same timer). Prefer /timer for new usage.

### Syntax

- `/countdown start <time> [display combo] [targets]` — start a countdown; omitting the combo defaults to `bossbar+title` with a last-10s center window, mapped to the normal tier
- `/countdown pause` / `resume` / `stop` / `status` — pause, resume, immediate cancel (no finish prompt), status
- `/countdown add <time>` / `remove <time>` / `set time <time>` — adjust remaining time (rejected on count-up sessions too)
- `/countdown set display <bossbar+title+subtitle+actionbar combo>` — render by channel combo
- `/countdown set players <selector>` / `set label <text>` — targets and label
- `/countdown set last <time|all>` — center window
- `/countdown set milestones <on|off>` — legacy beat table (30m/15m/10m/5m/1m/30s/15s once each, still honoring the selected channels)
- `/countdown set sound <on|off>` — sounds

Time formats, selectors and permissions match /timer (permission level 2; the disabled rule reports "feature is off").
## /fakegame - Fake Games

The `fakeGames` rule controls the whole tree (hidden when off). `/fakegame` is the main entry and `/fg` is an alias for the same tree. The main panel links to the game list, current session, leaderboard, personal stats and admin settings (admin only). Every button carries a session ID; old invites or buttons report an expired session.

```
/fakegame                          # main panel
/fakegame list                     # all games, player counts, intros and availability
/fakegame info <game>              # gameplay, win condition, settings and dependencies
/fakegame create <game> [preset]   # create a lobby (standard preset by default)
/fakegame join [session]           # join a waiting session
/fakegame start [session]          # host/admin starts a 3-second countdown
/fakegame leave [session]          # leave at any time
/fakegame cancel [session]         # end and clean up (host/admin)
/fakegame status [session]         # lobby/running panel, members, checks and settings
/fakegame invite [session]         # resend invites carrying the session ID
/fakegame set <session> <key> <value>
/fakegame guess <word>             # charades guess
/fakegame goal                     # red light green light: set the goal at your feet
/fakegame top [game]               # leaderboard by game/mode/difficulty
/fakegame me [game]                # personal stats
/fakegame admin                    # admin panel
/fakegame admin tempRules on|off    # in-game temporary rule switch (off by default)
/fakegame admin radius <8-128>      # maximum region radius
/fakegame admin boundary <4-40>     # boundary particle interval (ticks)
/fakegame admin preset <name> <radius> <minY> <maxY>
```

The lobby no longer auto-starts when the minimum player count is met: the host clicks Start. If a condition fails during the countdown (players, death, disconnect, dimension, or the start circle in Red Light Green Light), the session returns to the lobby with the reason.

### Region and boundary

- Regions come from admin presets (`small` / `standard` / `large`; default radius 32) and the server-level maximum radius; ordinary hosts cannot expand beyond them.
- The boundary is drawn with participant-only particle arcs; ActionBar warnings appear near the edge. Out-of-bounds keeps a 100-tick grace, and rendering shares the same radius/height as judgement.
- Elytra-style games also show and enforce a vertical range. No real walls, no world-border changes, no forced teleport as a substitute for warnings.

### Game list

| Game | Key settings | Gameplay | Win condition |
|------|--------------|----------|---------------|
| `grass_is_lava` | mode=fixed/rotate, hazard, interval, evacuate | Fixed or rotating hazard blocks with a warning and evacuation time; water uses fluid contact | Last standing |
| `rising_lava` | display=particles/blocks/flood, start_offset, rise_seconds, accelerate | Virtual surface rises from below the arena; accelerates while nobody is eliminated, resets after an elimination | Last standing |
| `red_light_green_light` | start_radius, goal=auto/manual | Everyone must be in the start circle; move on green, freeze on red; goal checks horizontal and vertical distance | First to the goal |
| `random_bans` | actions=all/basic/oneshot, round_seconds | Sustained rules (jump/stop/sneak/move) and one-shot tasks (jump/look up/look down) with reaction grace | Last standing |
| `safe_spot` | spots, rounds, round_seconds | 1-4 spots per round; first valid arrival scores and closes the spot; claims are not reassigned | Highest score after fixed rounds |
| `hide_and_seek` | mode=classic/shrink/herb, hide_seconds, seek_seconds | Seeker is anchored while hiding; capture requires line of sight; shrink-infection converts caught hiders; herb mode grants tall-grass invisibility | Team/time based |
| `tag` | mode=freeze/infection, rescue_seconds, duration | Ghost touch sustains; rescue requires sustained contact and resets on interruption; infection joins the ghosts | Ghosts clear or runners survive |
| `hot_potato` | fuse, pass_lock | Only effective attacks pass the potato; immediate back-pass is briefly locked; passing does not reset the fuse | Last standing |
| `king_of_the_hill` | mode=single/rotate/simultaneous, points, rotate_seconds | 1-4 independently shown points; closed points never score; contested points pause scoring | Highest score |
| `charades` | rounds | Performer acts the word; others guess with `/fakegame guess` | Highest score |
| `shooting` | mode=training/speed/accuracy/versus, targets, duration | Aim with the crosshair; swap-hand fires (follows rebinding); virtual ray checks occlusion/distance/rate; no item use | Highest score; training supports solo |
| `elytra` | mode=rings/collect, rings, duration | Use your own elytra; pass rings in order or collect coins; segment intersection avoids high-speed misses | Finish rings or most coins |
| `snake` | speed, max_length, food_count | Keep moving; collecting an ownerless fake grows a tail that follows your recorded path; hitting a tail eliminates; tails become ownerless fakes | Last standing or longest tail |
| `murder_mystery` | script, round_seconds | Currently unavailable: script import and phase machine not adapted this round | - |
| `werewolf` | day_seconds, night_seconds | Currently unavailable: phase machine not adapted this round | - |
| `draw_guess` | mode=classic/relay/same, rounds | Currently unavailable: Magic Brush is not merged into master | - |

### Judgement and protection

- Virtual elimination plays the vanilla totem animation/sound without consuming a totem, without real death, and without touching inventory or XP.
- Real deaths follow vanilla while the player leaves the session.
- Knockback, water/lava flow and climbing are exempt in red-light and jump checks; piston pushes have no server-side signal and are not exempt.
- Joining requires alive, non-spectator, same dimension and inside the region. Death, disconnect, dimension change and spectator leave automatically; out-of-bounds keeps a 100-tick grace.
- Multiple violations in one tick resolve as one batch; same-tick spot claims use distance then UUID order, without claiming sub-tick precision.
- Temporary in-game abilities come only from enabled rules or the admin switch; leaving, elimination, disconnect, dimension change, end, rule disable and server stop clean up every effect added by the session.
- Results are separated by game/mode/key difficulty and keyed by UUID; cancelled sessions never count as wins and a session is recorded once.

## Server Transfer Commands

### /tpto - Server Transfer

> **Related rule**: `serverTransfer` (off by default)

#### Syntax

```
/tpto
/tpto <server>
```

With no arguments, lists all configured servers in add order; names are clickable to transfer, hover shows the target address and port.

#### Permission

Available to all players (while the rule is on).

#### Arguments

| Argument | Type | Description |
|----------|------|-------------|
| `server` | text | Server id or display name; bare Chinese and display names with spaces work as-is, optionally wrapped in a matched pair of quotes |

#### Behavior

- Transfer uses the vanilla transfer protocol: the server sends the target address to the executing player only; the client disconnects and connects to the target directly, no proxy and no client mod
- Never transfers anyone but the executor; console / RCON cannot transfer
- Resolved by id or display name (case-insensitive); failures list the available servers
- **The target server must set `accepts-transfers=true` in `server.properties`**, otherwise the connection is kicked with `transfers_disabled`
- Tab completion matches id or display name prefixes (case-insensitive) and inserts the id, with the display name as tooltip

### /tptoset - Server Management

> **Related rule**: `serverTransfer` (off by default)

#### Syntax

```
/tptoset
/tptoset addserver <address:port> <id> [displayName]
/tptoset removeserver <server>
/tptoset reload
```

#### Permission

Admins (permission level 4). While the rule is off the whole tree is hidden; command blocks do not gain admin.

#### Subcommands

| Subcommand | Description |
|------------|-------------|
| `/tptoset` | Shows configured servers (id, display name, address:port), each row with a clickable `[remove]` |
| `/tptoset addserver <address:port> <id> [displayName]` | Adds a server (one address per server); `address:port` is written bare, accepting domains, IPv4, `[IPv6]:port` and localhost; `id` is a unique ASCII id (letters/digits/`_-.+`); displayName falls back to the id and may contain Chinese and spaces. Rejected when id or display name collides with an existing record ignoring case; to change an address, removeserver then addserver |
| `/tptoset removeserver <server>` | Removes the whole record (by unique id or display name) |
| `/tptoset reload` | Re-reads the config (no restart needed after hand edits); on failure the current list is kept and writes pause until a successful reload |

#### Config file

`config/carpet-pry-addition/tpto.json` (follows the server, not the world). Data version 3: one record per address and port; v1 (single host) and v2 (multi-line) files are flattened on load — v2 keeps the entry named by defaultLine (or the first valid one), other entries and playerLines are not imported, and the original file is backed up as `tpto.json.pre-v3-<timestamp>-<n>.bak` before the first write.

```json
{
  "version": 3,
  "data": {
    "servers": {
      "sc": {
        "host": "survival.example.com",
        "port": 25565,
        "displayName": "生存服"
      }
    }
  }
}
```

Hand edits must obey: `servers` must exist and be an object; `host` a string and `port` an integer 1-65535; ids/display names must not conflict (case-insensitive); legacy `lines`/`defaultLine`/`playerLines` in a v3 file or a newer format are rejected and writes pause until a successful reload after fixing.

#### Examples

```
/tpto                                         # list servers, click to transfer
/tpto sc                                      # by id (or the display name)
/tptoset                                      # view configured servers
/tptoset addserver survival.example.com:25565 sc 生存服
/tpto sc                                      # one address per server, direct transfer
/tptoset removeserver sc                      # remove a server
/tptoset reload                               # re-read after hand edits
```


---

## /chatpunish - Chat Punishment

> **Related rule**: `chatPunishment`

### Syntax

```
/chatpunish set <player> <mode> [duration]
/chatpunish clear <player>
/chatpunish list
```

`<mode>` is one of `mute` / `tape` / `cat` / `idiot` / `censor` / `villager` / `stutter` / `reverse` (literals, tab-completed). `[duration]` is `number+s/m/h/d` (e.g. `10m`, `1h`, `1d`); permanent when omitted, lifting automatically on expiry, measured in real time.

### Permissions

- Admin-only (permission level 4); the console works
- The whole command tree is hidden while the `chatPunishment` rule is off

### Description

Applies a punishment to a player's public chat; each player can only have one punishment at a time, and another `set` replaces it. The effects and examples of the eight modes are in the [rule documentation](rules_en.md#chatpunishment---chat-punishment). Under `mute` the message is no longer broadcast and the sender sees "You have been muted"; every other mode replaces the default chat display with the rewritten text. The signed original is still carried with the message and may be visible through the client's secure-chat setting or the modified marker; this rule does not promise content hiding or filtering.

Player names resolve through the vanilla profile mechanism (same as /ban): online names tab-complete, offline players resolve by UUID through the profile cache, and unknown players get the vanilla error — no guessed profiles. Punishments are stored by UUID in `<world>/carpet-pry-addition/chatpunish.json` and survive restarts and relogs; expiry is evaluated lazily on read and cleaned up when saving.

Commands, private messages and system messages are unaffected; with the rule off, chat stays untouched and records are kept.

### Related Rule

**chatPunishment** - master switch; while off the commands are hidden and stored punishments are kept (unexpired ones resume when re-enabled).

### Examples

```
/chatpunish set Steve tape            # Steve's chat becomes the tape text (permanent)
/chatpunish set Steve cat 1h          # Steve's chat gets 喵~ appended for 1 hour
/chatpunish set Steve mute 30m        # mute for 30 minutes
/chatpunish list                      # view active punishments (name, mode, time left or permanent)
/chatpunish clear Steve               # clear Steve's punishment
```

## Player Replay Commands

### /playereplay - Recording and Phantom Playback

#### Syntax

```
/playereplay                                  # main panel (start recording / recording list)
/playereplay record [name] | on [name]        # preparation page (self by default)
/playereplay record begin                     # start capturing joined participants
/playereplay record objects                   # invite / direct-add / remove self
/playereplay record invite <player>           # normal invite; administrators add directly
/playereplay record uninvite <player UUID>    # cancel an unanswered invite
/playereplay record self on|off               # include/remove self
/playereplay record settings                  # duration and access
/playereplay record duration <seconds>        # adjust duration within server limit
/playereplay record access public|private     # access (locked after an invite)
/playereplay record pause|resume              # pause/resume shared timeline
/playereplay record stop | stop               # stop and save
/playereplay record retry                     # retry a failed save
/playereplay record leave                     # normal invitee leaves; direct-added players cannot
/playereplay record discard [confirm <task ID>]
/playereplay invite accept|deny <task ID> <token>
/playereplay start [name] [here] | startid <ID> [here]
/playereplay loop <name> | loopid <ID>
/playereplay pause|resume|end <playback ID> | playings | endall
/playereplay list [page] | list mine [page] | favlist [page]
/playereplay info <ID> [source] [page]
/playereplay rename <ID> <new name>           # creator/administrator
/playereplay access <ID> public|private       # creator/administrator
/playereplay fav <ID> | unfav <ID>            # personal reference only
/playereplay delete <ID> confirm
/playereplay panel
```

#### Permissions

- Root command: all players, with `playerReplay` or `fakePlayerScript` enabled.
- Recording/list/playback/favorites: checked against the recording's access. Public recordings are available to all players with the feature permission; private recordings only to the creator and administrators.
- Rename/access/delete: creator or level-2 administrator; being recorded never grants management rights.
- Phantom playback/control requires `playerReplay`; `pause/resume/end` only control your own viewer instance, administrators may control all.
- `endall`: administrators end all; normal players end only their own instances.

#### Description

Both rules share one recording, storage, list and permission system. `record` opens a preparation page and captures nothing yet. The default target is yourself; normal players invite online players and require consent, administrators add directly without consent. Unanswered preparation invites expire when recording starts, accepted participants join; invitations may continue during recording and late joiners are captured from their join tick. Starting captures only joined participants and never waits for unanswered invites.

One recording has one shared timeline; each participant owns independent visual/input tracks and online segments. A normal logout records the leave tick and hides the phantom; participants with an active relationship reconnect by UUID at the current tick, with no line drawn across the gap. A normal invitee who actively leaves does not auto-resume. The creator going offline does not end the task. Pausing freezes the shared timeline; resume rebuilds input/slot/equipment baselines and starts a new segment without backfilling paused clicks.

The idle main panel keeps only “玩家回放”, “开始录制” and “录像列表”. Running playback controls live in the list footer or the `playings` subpage. During recording the panel shows participants and pause/stop/discard/objects/settings buttons; effective duration is shown on the action bar. Each list row is one clickable line formatted as `name · yyyy/MM/dd · HH:mm:ss · mm:ss`, with public/private filters, “mine only”, favorites and pagination; detail pages preserve source page.

#### Limits

- Maximum 10 minutes (12000 ticks), minimum 30 seconds, 20 recordings per player; auto-stop and save at the limit.
- Up to 16 participants; recordings are stored per participant segment and offline gaps contain no frames.
- Files: `<world>/carpet-pry-addition/replay/recordings/<recording ID>.repry`; favorites at `favorites/<player UUID>-favorites.json` as references only.
- Normal shutdown saves and ends tasks; server restart does not resume recording, invites or scripts. A crash recovers only the latest automatic checkpoint and marks it as “recovered (incomplete)”.
- Container GUI drags are not recorded; view rotation is sampled at tick end; offline servers backfill skins from Mojang by player name.

## Fake Player Script Commands

### /playerscript - Recording and Fake Player Execution

#### Syntax

```
/playerscript                                 # script panel
/playerscript record [name] | record stop     # shared preparation/recording/save
/playerscript setup <recording ID>            # select participant and open settings
/playerscript setup <recording ID> participant <participant UUID>
/playerscript setup <recording ID> bot <name> | count <n> | inf | mode reset|continue
/playerscript run <recording ID>              # auto-spawn and execute, auto-offline on finish
/playerscript run <recording ID> force        # 1.21/1.21.1 only: riding/stationary scripts
/playerscript run <name> as <bot>             # bind an online fake (not taken offline on stop)
/playerscript pause|resume|stop|status <bot>
/playerscript scripts
/playerscript list
```

#### Permissions

Level 2 (game master); requires `fakePlayerScript`. Script control additionally checks that the operator is the session owner or an administrator.

#### Description

The script uses only the selected participant's real online input segments, concatenated without offline gaps, and lets an auto-spawned Carpet fake player replay **operations**, not outcomes: walks into walls, digs whatever is in front, never pathfinds or corrects position mid-round. Multi-participant recordings require participant selection in the setup page; settings are isolated by operator, recording ID and participant UUID. Execution is refused when no valid input exists.

At segment boundaries the previous tail frame is consumed by an entity tick first, then old continuous actions are released and the recorded baseline rebuilt; no teleporting between segments. Auto-spawned bots go offline on finish; externally bound bots are only released, never taken offline.

#### Semantics

- Half-open timeline `[0, duration)`; each loop restores the first frame's absolute look direction; “return to start” is only an explicit inter-round reset, never an in-round correction.
- Equipment is not copied, materials are not supplied, inventory/durability/health are not reset; missing items follow vanilla behavior.
- Pausing releases continuous use actions (bow/eating/shield) with normal side effects; the bot remains subject to gravity and collision while paused.
- Setup edits refresh the panel and stay bound to the recording ID and participant UUID.
- Pause/resume restarts continuous use; old charge is not restored and consumed materials are not refunded; instant events are not replayed.
- 1.21/1.21.1 emit no ordinary walking input, so `run` refuses by default; append `force` only for riding/stationary interactions.
- Known limits: off-hand use/attack falls back to main hand; container GUI drags are not recorded; attack cooldown follows server physics and may differ slightly from the recording.
