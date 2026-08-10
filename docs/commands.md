# 命令文档

> **Mod ID**: carpet-pry-addition  
> **版本**: 1.1.2

---

## 快速导航

- [假人珍珠传送命令](#假人珍珠传送命令)
  - [/tpp - 假人珍珠传送](#tpp---假人珍珠传送)
  - [/tppset - 站点管理](#tppset---站点管理)
- [/hat - 玩家帽子](#hat---玩家帽子)
- [假人持续清空背包](#假人持续清空背包)
- [玩家随地大小变](#玩家随地大小变)
- [骑乘权限命令](#骑乘权限命令)
  - [/riding - 骑乘权限管理](#riding---骑乘权限管理)
  - [/picking - 捡起权限管理](#picking---捡起权限管理)

---

## 假人珍珠传送命令

### /tpp - 假人珍珠传送

#### 语法

```
/tpp <station>
```

#### 权限

需要启用 `TppFakePlayer` 规则。

#### 功能描述

传送到指定站点（通过假人中转）。

#### 参数说明

| 参数 | 类型 | 说明 |
|------|------|------|
| `station` | 字符串 | 目标传送站点名称（支持内部名或显示名） |

#### 工作流程

1. 构建假人名：别名（如有）或玩家名（截断至 10 字符） + `_` + 站点名
2. 执行 `/player <假人名> rejoin`（让已有假人重新加入）
3. 轮询等待假人上线（最多 10 秒）
4. 按站点级右键次数（未设置则使用全局默认值）循环执行 `/player <假人名> use`（传送者操控假人右键末影珍珠），每次间隔 0.5 秒
5. 等待 3 秒让传送完成
6. 执行 `/player <假人名> kill`（清除假人）

#### 使用示例

```bash
# 传送到名为 spawn 的站点
/tpp spawn

# 传送到名为 base 的站点
/tpp base
```

---

### /tppset - 站点管理

#### 权限

大部分子命令需要管理员权限。

#### 功能描述

管理 TPP 传送站点、玩家别名和规则配置。

#### 子命令

##### `/tppset spawn <station>`

在当前位置设置该站点的假人生成点，立即生成假人并在 3 秒后自动下线。

- **权限**: 需要启用 `TppFakePlayer` 规则
- **参数**:
  - `station` - 站点名称

##### `/tppset set <name> [<displayName>]`

添加传送站点。

- **权限**: 管理员专属
- **参数**:
  - `name` - 站点内部名称
  - `displayName` - 可选，站点显示名称

##### `/tppset remove <station>`

删除传送站点。

- **权限**: 管理员专属
- **参数**:
  - `station` - 站点名称（支持内部名或显示名）

##### `/tppset rename <player> set <alias>`

为玩家设置假人传送别名。

- **权限**: 管理员专属
- **参数**:
  - `player` - 玩家真实名称
  - `alias` - 别名（最多 12 字符）

##### `/tppset rename <player> remove`

移除玩家的假人传送别名。

- **权限**: 管理员专属
- **参数**:
  - `player` - 玩家真实名称

##### `/tppset rule use <count> [station]`

设置传送时假人右键末影珍珠的次数。可不指定 `station` 设置全局默认值，或指定 `station` 设置该站点的独立次数（站点级优先级高于全局）。

- **权限**: 管理员专属
- **参数**:
  - `count` - 右键次数（最小为 1）
  - `station` - 可选，站点名称（支持内部名或显示名）。未指定时设置全局默认值，指定时仅对该站点生效

##### `/tppset rule`

查看当前 TPP 规则配置。

- **权限**: 管理员专属

#### 别名系统说明

管理员可为玩家设置短别名，用于构建更短的假人名，避免超过字符限制。

```
原始: VeryLongPlayerName_station (可能超过字符限制)
别名: VIP
结果: VIP_station (更短且安全)
```

#### 使用示例

```bash
# 添加站点（无显示名）
/tppset set spawn

# 添加站点（带显示名）
/tppset set farm 农场

# 删除站点
/tppset remove spawn

# 为玩家设置别名
/tppset rename VeryLongPlayerName set VIP

# 移除玩家别名
/tppset rename VeryLongPlayerName remove

# 设置全局右键次数为 2
/tppset rule use 2

# 仅对指定站点设置右键次数为 3
/tppset rule use 3 farm

# 查看规则配置
/tppset rule
```

---

## /hat - 玩家帽子

### 语法

```
/hat
```

### 权限

- 管理员总是可用
- 普通玩家需要启用 `playerhat` 规则

### 功能描述

将主手物品戴在头上，与头上物品交换。

### 相关规则

**playerhat** - 启用时，头部槽位放置不死图腾后，玩家受到致命伤害时先触发不死图腾的正常复活效果，再额外附加以下状态：
- 再生 II
- 伤害吸收 II
- 抗火 I

### 使用示例

```bash
# 手持钻石块，将其戴在头上
/hat
```

---

## 假人持续清空背包

### 命令语法

通过 Mixin 在 Carpet 自带的 `/player <name>` 命令树下追加独立的 `dropall` 子命令，让假人按设定节奏持续丢出背包所有物品：

```
/player <name> dropall [once|continuous|interval <ticks>|after <ticks>|perTick <times>|randomly <min> <max>|stop]
```

`<modifier>` 可选以下七种：

| 修饰参数 | 语法 | 行为 |
|---------|------|------|
| (无参数) | `dropall` | 立即丢一次（等价 `once`） |
| `once` | `dropall once` | 立即丢一次全部 |
| `continuous` | `dropall continuous` | 每个 server tick 丢一次，直到清空 |
| `interval` | `dropall interval <ticks>` | 每隔 `<ticks>` tick 丢一次 |
| `after` | `dropall after <ticks>` | 在 `<ticks>` tick 之后丢一次（一次性） |
| `perTick` | `dropall perTick <times>` | 每秒（20 tick）丢 `<times>` 次 |
| `randomly` | `dropall randomly <min> <max>` | 在 `[min, max]` tick 区间随机取值作为本次间隔，每次重新随机 |
| `stop` | `dropall stop` | 停止持续丢出任务 |

### 权限

沿用 Carpet `/player` 命令本身的权限检查（由 Carpet 的 `commandPlayer` 规则控制），不额外限制。

### 相关规则

- **fakePlayerDropStackModifiers** — 控制整个 `dropall` 命令的可见性。
  - 规则关闭时：整个 `dropall` 命令不可见（tab 补全不到、无法执行），请使用原版 `/player <name> dropStack all` 实现一次性丢出。
  - 规则开启时：所有修饰参数正常工作。
  - 规则切换立即生效：通过 Carpet `RuleObserver` 在规则变更时重新下发命令树，玩家无需重新登录即可看到可见性变化。

### 与原版 dropStack 的关系

- `dropall` 是完全独立的子命令，不修改 Carpet 原版 `dropStack` 命令树。
- `dropStack all`（Carpet 原版）→ 立即丢一次全部
- `dropall continuous`（新增）→ 持续丢出，背包清空后保持等待新物品
- 执行 `/player <name> stop` 会同步清理本项目维护的所有持续丢出任务。

### 使用示例

```bash
# 立即丢一次全部（等价原版 dropStack all）
/player Steve dropall

# 假人逐 tick 丢出背包所有物品（背包清空后保持等待，可随时 stop）
/player Steve dropall continuous

# 每 10 tick 丢一次
/player Steve dropall interval 10

# 20 tick 后丢一次（一次性）
/player Steve dropall after 20

# 每秒丢 4 次
/player Steve dropall perTick 4

# 在 5~20 tick 之间随机间隔丢出
/player Steve dropall randomly 5 20

# 停止持续丢出任务
/player Steve dropall stop

# 停止该假人所有动作（包括持续丢出任务）
/player Steve stop
```

### 自动停止条件

- `continuous`/`interval`/`perTick`/`randomly` 模式：背包清空后任务保持运行，等待新物品装入后继续丢出；需手动 `stop` 才会停止。
- `after` 模式：成功丢出一次后自动结束；若到时机时背包为空，任务保持每 tick 检查，直到有物品可丢出为止。
- 目标假人下线：所有相关任务自动清理，避免 tick 监听泄漏。
- 同一假人已有进行中的 dropall 任务时，再次触发会被拒绝并提示先 `stop`。

---

## 玩家随地大小变

### /scale - 玩家大小调节

#### 语法

```
/scale <value>                 # 玩家调节自己大小（受 playerScaleMin/Max 范围限制）
/scale reset                   # 玩家恢复默认大小（1.0）
/scale <player> <value>        # 管理员调节指定玩家大小（不受范围限制）
/scale <player> reset          # 管理员重置指定玩家大小
```

#### 权限

- 整个 `/scale` 命令受 `playerScaleModifiers` 规则控制可见性，规则关闭时命令不可见
- 玩家路径（`/scale <value>`、`/scale reset`）：受 `playerScaleMin` 和 `playerScaleMax` 范围限制
- 管理员路径（`/scale <player> ...`）：需要 OP 4 级权限，不受范围限制
- 规则变更时通过 Carpet `RuleObserver` 立即刷新命令树，无需玩家重新登录

#### 功能描述

为 `Player` 注册 `minecraft:scale` 属性，并通过 `/scale` 命令调节玩家体型大小。玩家可调节自身大小，管理员可调节任意在线玩家大小。

> **版本要求**：`minecraft:scale` 属性从 Minecraft 1.21.5 起加入原版，1.21~1.21.4 版本上执行 `/scale` 会提示版本不支持。

#### 范围控制

- `playerScaleMin`（默认 0.1）：玩家可设置的最小 scale 值
- `playerScaleMax`（默认 10.0）：玩家可设置的最大 scale 值
- 管理员路径不受此范围限制，可设置任意值（0.0~100.0）

#### 使用示例

```bash
# 玩家变小到一半
/scale 0.5

# 玩家恢复默认大小
/scale reset

# 管理员把 Steve 变成 2 倍大小
/scale Steve 2.0

# 管理员重置 Steve 的大小
/scale Steve reset
```

#### 提示消息

- 成功设置自己：`§a你的大小已设为 0.5`
- 成功设置他人：`§aSteve 的大小已设为 2.0`
- 超出范围：`§c值 0.05 超出允许范围（0.1 ~ 10.0）`
- 被管理员调整：`§e管理员将你的大小调整为 2.0`
- 玩家不在线：`§c玩家 Steve 不在线`
- 版本不支持：`§c当前 Minecraft 版本不支持 minecraft:scale 属性（需 1.21.5+）`

---

## 骑乘权限命令

### /riding - 骑乘权限管理

#### 语法

```
/riding on     # 允许其他玩家骑乘你
/riding off    # 禁止其他玩家骑乘你
```

#### 权限

- 管理员总是可用
- 普通玩家需要启用 `ridingPlayers` 规则

#### 功能描述

设置是否允许其他玩家骑乘你。你设置为 `on` 后，其他玩家**主手持不死图腾**对你右键即可骑乘上来。

#### 交互条件

- 骑乘者（上面的人）：主手持**不死图腾**
- 被骑乘者（下面的人）：需执行 `/riding on` 允许
- 堆叠上限由 `ridingPlayersPickUpLimit` 规则控制（默认 16）
- 当 `ridingPlayersDismountOnGameModeChange` 启用时，游戏模式变更会自动让乘客下车
- 当 `ridingPlayersClientAllowInteractions` 启用时（默认），骑乘状态下仍可与方块/实体交互（需客户端安装）

#### 使用示例

```bash
# 允许其他玩家骑乘你
/riding on

# 禁止其他玩家骑乘你
/riding off
```

---

### /picking - 捡起权限管理

#### 语法

```
/picking on     # 允许其他玩家捡起你
/picking off    # 禁止其他玩家捡起你
```

#### 权限

- 管理员总是可用
- 普通玩家需要启用 `pickupPlayers` 规则

#### 功能描述

设置是否允许其他玩家捡起你（让你骑到他们头上）。你设置为 `on` 后，其他玩家**主手持不死图腾 + 副手金胡萝卜**对你右键即可将你捡起。

#### 交互条件

- 捡起者（下面的人）：主手持**不死图腾** + 副手持**金胡萝卜**
- 被捡起者（上面的人）：需执行 `/picking on` 允许
- 堆叠上限由 `ridingPlayersPickUpLimit` 规则控制（默认 16），与骑乘共用

#### 使用示例

```bash
# 允许其他玩家捡起你
/picking on

# 禁止其他玩家捡起你
/picking off
```
