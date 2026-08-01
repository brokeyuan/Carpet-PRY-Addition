# 命令文档

> **Mod ID**: carpet-pry-addition  
> **版本**: 1.1.2

---

## 快速导航

- [假人珍珠传送命令](#假人珍珠传送命令)
  - [/tpp - 假人珍珠传送](#tpp---假人珍珠传送)
  - [/tppset - 站点管理](#tppset---站点管理)
- [/hat - 玩家帽子](#hat---玩家帽子)
- [骑乘权限命令](#骑乘权限命令)
  - [/riding - 骑乘权限管理](#riding---骑乘权限管理)
  - [/pickup - 捡起权限管理](#pickup---捡起权限管理)

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

### /pickup - 捡起权限管理

#### 语法

```
/pickup on     # 允许其他玩家捡起你
/pickup off    # 禁止其他玩家捡起你
```

#### 权限

- 管理员总是可用
- 普通玩家需要启用 `pickupPlayers` 规则

#### 功能描述

设置是否允许其他玩家捡起你（让你骑到他们头上）。你设置为 `on` 后，其他玩家**主手持不死图腾 + 副手金胡萝卜**对你右键即可将你捡起。

#### 交互条件

- 捡起者（下面的人）：主手持**不死图腾** + 副手持**金胡萝卜**
- 被捡起者（上面的人）：需执行 `/pickup on` 允许
- 堆叠上限由 `ridingPlayersPickUpLimit` 规则控制（默认 16），与骑乘共用

#### 使用示例

```bash
# 允许其他玩家捡起你
/pickup on

# 禁止其他玩家捡起你
/pickup off
```
