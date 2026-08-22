# Carpet Pry Addition 规则文档

> Mod ID: `carpet-pry-addition` | 版本: `1.1.2`
>
> 共 **16 条**规则
>
> **提示：可以使用 `Ctrl+F` 快速查找自己想要的规则**

---

## 快速导航

- [假人相关 (BOT)](#假人相关-bot)
  - [TppFakePlayer - 假人珍珠站传送](#tppfakeplayer---假人珍珠站传送)
  - [fakePlayerNameSuggestions - 假人名称建议](#fakeplayernamesuggestions---假人名称建议)
  - [fakePlayerSkinMode - 假人皮肤设置](#fakeplayerskinmode---假人皮肤设置)
  - [fakePlayerSkinSet - 假人统一皮肤设置](#fakeplayerskinset---假人统一皮肤设置)
- [漏洞修复 (BUGFIX)](#漏洞修复-bugfix)
  - [FixXaeroLib - XaeroLib兼容性修复补丁](#fixxaerolib---xaerolib兼容性修复补丁)
  - [FixBluemap - BlueMap兼容性修复补丁](#fixbluemap---bluemap兼容性修复补丁)
- [移植功能 (PORTING)](#移植功能-porting)
  - [sleepingDuringTheDay - 白日做梦](#sleepingduringtheday---白日做梦)
  - [unicodeArgumentsSupport - Unicode 参数支持](#unicodeargumentssupport---unicode-参数支持)
- [玩家交互](#玩家交互)
  - [ridingPlayers - 骑乘玩家](#ridingplayers---骑乘玩家)
  - [pickupPlayers - 捡起玩家](#pickupplayers---捡起玩家)
  - [ridingPlayersPickUpLimit - 玩家骑乘堆叠上限](#ridingplayerspickuplimit---玩家骑乘堆叠上限)
  - [ridingPlayersDismountOnGameModeChange - 玩家骑乘更改模式下车](#ridingplayersdismountongamemodechange---玩家骑乘更改模式下车)
  - [ridingPlayersClientAllowInteractions - 玩家骑乘时可交互（客户端）](#ridingplayersclientallowinteractions---玩家骑乘时可交互客户端)
- [生存功能](#生存功能)
  - [playerhat - 玩家帽子](#playerhat---玩家帽子)
  - [betterSnowBall - 更好的雪球](#bettersnowball---更好的雪球)
  - [invisibleInTallGrass - 隐身草](#invisibleintallgrass---隐身草)

---

## 假人相关 (BOT)

### TppFakePlayer - 假人珍珠站传送

使用假人快速使用珍珠传送站。当为true时启用/tppset设置指令和/tpp 玩家指令。

| 属性 | 值 |
|------|-----|
| **规则名** | `TppFakePlayer` |
| **描述** | 使用假人快速使用珍珠传送站。当为true时启用/tppset设置指令和/tpp 玩家指令 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `BOT`, `COMMAND` |

---

### fakePlayerNameSuggestions - 假人名称建议

自定义/player建议的假人列表。使用','分隔每个名称。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerNameSuggestions` |
| **描述** | 自定义/player建议的假人列表。使用','分隔每个名称 |
| **类型** | `string` |
| **默认值** | `Steve,Alex` |
| **参考选项** | `Steve,Alex`, `Pry,hsds`, `Pry,hsds,Firework,Food`, `` |
| **分类** | `PRIMARYUAN`, `BOT` |

---

### fakePlayerSkinMode - 假人皮肤设置

安装前置 [skinrestorer](https://modrinth.com/mod/skinrestorer) 后，可以设置假人的皮肤。default=不更改假人皮肤，summon=假人使用召唤者的皮肤，same_skin=假人使用统一皮肤。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerSkinMode` |
| **描述** | 安装前置skinrestorer后，可以设置假人的皮肤。default=不更改假人皮肤，summon=假人使用召唤者的皮肤，same_skin=假人使用统一皮肤 |
| **类型** | `string` |
| **默认值** | `default` |
| **参考选项** | `default`, `summon`, `same_skin` |
| **分类** | `PRIMARYUAN`, `BOT` |

#### 模式说明

| 模式 | 行为 |
|------|------|
| `default` | 不更改假人皮肤 |
| `summon` | 假人使用召唤者的皮肤 |
| `same_skin` | 假人使用统一皮肤 |

---

### fakePlayerSkinSet - 假人统一皮肤设置

当FakeplayersSkinMode为same_skin时，设置用于假人皮肤的玩家名称。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerSkinSet` |
| **描述** | 当FakeplayersSkinMode为same_skin时，设置用于假人皮肤的玩家名称 |
| **类型** | `string` |
| **默认值** | `Brokeyuan` |
| **参考选项** | `Brokeyuan`, `hsds`, `` |
| **分类** | `PRIMARYUAN`, `BOT` |

---

### fakePlayerDropStackModifiers - 假人持续清空背包

给 `/player <name>` 下追加独立的 dropall 子命令，让假人按设定节奏持续丢出背包所有物品。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerDropStackModifiers` |
| **描述** | 给 /player <name> 下追加独立的 dropall 子命令，让假人按设定节奏持续丢出背包所有物品。命令：/player <name> dropall [once\|continuous\|interval <ticks>\|after <ticks>\|perTick <times>\|randomly <min> <max>\|stop] |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `BOT`, `COMMAND` |

---

## 漏洞修复 (BUGFIX)

### FixXaeroLib - XaeroLib兼容性修复补丁

修复高版本Xaero 搭配LuckPerms 会导致假人数据丢失的问题。

| 属性 | 值 |
|------|-----|
| **规则名** | `FixXaeroLib` |
| **描述** | 修复高版本Xaero 搭配LuckPerms 会导致假人数据丢失的问题 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `BUGFIX` |

#### 相关 issue

- [fabric-carpet #2158](https://github.com/gnembon/fabric-carpet/issues/2158) — Bot's inventory force empty with LuckPerms + xaerolib
- [LuckPerms #4232](https://github.com/LuckPerms/LuckPerms/issues/4232) — [Wzp-2008](https://github.com/Wzp-2008) 提供补丁方案
- [Xaero's World Map #1191](https://legacy.curseforge.com/minecraft/mc-mods/xaeros-world-map/issues/1191) — fake player data initialize failed

---

### FixBluemap - BlueMap兼容性修复补丁

修复假人不触发Fabric API连接事件导致BlueMap等模组无法正确追踪假人上下线的问题。

| 属性 | 值 |
|------|-----|
| **规则名** | `FixBluemap` |
| **描述** | 修复假人不触发Fabric API连接事件导致BlueMap等模组无法正确追踪假人上下线的问题 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `BUGFIX` |

#### 相关 issue

- [fabric-carpet #1962](https://github.com/gnembon/fabric-carpet/issues/1962) — Compatibility issue with Bluemap
- [fabric-carpet PR #2142](https://github.com/gnembon/fabric-carpet/pull/2142)
- [BlueMap #598](https://github.com/BlueMap-Minecraft/BlueMap/issues/598) — Unable to properly handle fake players who go offline

---

## 移植功能 (PORTING)

### sleepingDuringTheDay - 白日做梦

允许玩家在白天睡觉，睡觉后切换至夜晚（参考 PCA）。

> **版本要求**：完整功能（白天入睡）需 Minecraft 1.21.11+；1.21~1.21.10 上无法在白天开始睡觉。

| 属性 | 值 |
|------|-----|
| **规则名** | `sleepingDuringTheDay` |
| **描述** | 允许玩家在白天睡觉，睡觉后切换至夜晚（参考 PCA） |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `PORTING`, `SURVIVAL` |

---

### unicodeArgumentsSupport - Unicode 参数支持

允许命令参数中使用非ASCII字符（中文，日文，韩文等，可以用于召唤中文名假人）（移植来自YACA）。

| 属性 | 值 |
|------|-----|
| **规则名** | `unicodeArgumentsSupport` |
| **描述** | 允许命令参数中使用非ASCII字符（中文，日文，韩文等，可以用于召唤中文名假人）（移植来自YACA） |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `PORTING` |

---

## 玩家交互

### ridingPlayers - 骑乘玩家

主手持不死图腾时，可以骑上其他玩家。

| 属性 | 值 |
|------|-----|
| **规则名** | `ridingPlayers` |
| **描述** | 主手持不死图腾时，可以骑上其他玩家 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### pickupPlayers - 捡起玩家

主手持不死图腾+副手金胡萝卜时，可以捡起其他玩家（让对方骑到自己身上）。

| 属性 | 值 |
|------|-----|
| **规则名** | `pickupPlayers` |
| **描述** | 主手持不死图腾+副手金胡萝卜时，可以捡起其他玩家（让对方骑到自己身上） |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### ridingPlayersPickUpLimit - 玩家骑乘堆叠上限

骑乘和捡起时最多可堆叠的玩家数量，骑乘和捡起共用此上限。

| 属性 | 值 |
|------|-----|
| **规则名** | `ridingPlayersPickUpLimit` |
| **描述** | 骑乘和捡起时最多可堆叠的玩家数量，骑乘和捡起共用此上限 |
| **类型** | `int` |
| **默认值** | `16` |
| **参考选项** | `16`, `32`, `` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### ridingPlayersDismountOnGameModeChange - 玩家骑乘更改模式下车

当玩家游戏模式变更的时候，让头上的玩家下车。

| 属性 | 值 |
|------|-----|
| **规则名** | `ridingPlayersDismountOnGameModeChange` |
| **描述** | 当玩家游戏模式变更的时候，让头上的玩家下车 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### ridingPlayersClientAllowInteractions - 玩家骑乘时可交互（客户端）

需客户端安装，当头上有乘客的时候，仍可与方块/实体交互。

| 属性 | 值 |
|------|-----|
| **规则名** | `ridingPlayersClientAllowInteractions` |
| **描述** | 需客户端安装，当头上有乘客的时候，仍可与方块/实体交互 |
| **类型** | `boolean` |
| **默认值** | `true` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE`, `CLIENT` |

---

## 生存功能

### playerhat - 玩家帽子

允许玩家将物品戴在头上，并添加/hat指令。头部放置不死图腾时可触发死亡保护效果。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerhat` |
| **描述** | 允许玩家将物品戴在头上，并添加/hat指令。头部放置不死图腾时可触发死亡保护效果 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### betterSnowBall - 更好的雪球

雪中塞石。允许雪球给玩家造成击退和伤害。

| 属性 | 值 |
|------|-----|
| **规则名** | `betterSnowBall` |
| **描述** | 雪中塞石。允许雪球给玩家造成击退和伤害 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### invisibleInTallGrass - 隐身草

玩家头部位于高草丛时自动隐形。

| 属性 | 值 |
|------|-----|
| **规则名** | `invisibleInTallGrass` |
| **描述** | 玩家头部位于高草丛时自动隐形 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### playerScaleModifiers - 玩家随地大小变

为 Player 注册 `minecraft:scale` 属性，并通过 `/scale set|reset|info` 命令调节玩家体型大小。仅 1.21.5+ 版本支持。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerScaleModifiers` |
| **描述** | 为 Player 注册 minecraft:scale 属性，并添加 /scale set/reset/info 命令。false=关闭命令；self=所有人都只能调自己（无论 OP）；true=玩家仅可调自己，管理员可调任意玩家；everyone=所有人可调任意玩家。仅 1.21.5+ 版本支持 |
| **类型** | `string` |
| **默认值** | `false` |
| **参考选项** | `false`, `self`, `true`, `everyone` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE`, `COMMAND` |

#### 模式说明

| 模式 | 行为 |
|------|------|
| `false` | 关闭命令（默认） |
| `self` | 所有人都只能调自己（无论 OP） |
| `true` | 玩家仅可调自己，管理员可调任意玩家 |
| `everyone` | 所有人可调任意玩家 |

---

### playerScaleMin - 玩家大小最小值

玩家（含 everyone 模式下的非 OP）执行 `/scale set` 时可设置的最小 scale 值，管理员路径不受此限制。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerScaleMin` |
| **描述** | 玩家（含 everyone 模式下的非 OP）执行 /scale set 时可设置的最小 scale 值，管理员路径不受此限制 |
| **类型** | `double` |
| **默认值** | `0.1` |
| **参考选项** | `0.1`, `0.25`, `0.5` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### playerScaleMax - 玩家大小最大值

玩家（含 everyone 模式下的非 OP）执行 `/scale set` 时可设置的最大 scale 值，管理员路径不受此限制。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerScaleMax` |
| **描述** | 玩家（含 everyone 模式下的非 OP）执行 /scale set 时可设置的最大 scale 值，管理员路径不受此限制 |
| **类型** | `double` |
| **默认值** | `10.0` |
| **参考选项** | `2.0`, `5.0`, `10.0` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### realisticPlayerScale - 更真实的玩家大小变

开启后玩家物理特性随体型全方位联动。需配合玩家随地大小变规则使用，仅 1.21.5+ 版本支持。

| 联动维度 | 缩放规律 | 保底 |
|----------|----------|------|
| 移动速度（行走） | √scale（scale<1.0）/ 线性（scale≥1.0） | 0.3× |
| 飞行速度（创造飞行） | √scale（scale<1.0）/ 线性（scale≥1.0） | 0.3× |
| 跳跃高度 | √scale | 0.5× |
| 台阶高度 | 线性 | 0.5× |
| 方块交互/攻击距离 | 线性 | 0.5× (≥2.25格) |
| 实体交互/攻击距离 | 线性 | 0.5× (≥1.5格) |
| 摔落安全距离 | 线性 | 0.5× (≥1.5格) |
| 视野（FOV） | 补偿缩小带来的视野变窄，保持观感一致（需客户端安装本模组） | — |

所有属性修改均使用瞬态修改器（不落盘存档），关闭规则或体型恢复 1.0 后自动移除。scale<1.0 时采用混合策略：速度/飞行用 √scale 曲线更平缓地衰减，关键属性（交互距离、摔落安全、台阶）和跳跃加保底下限，确保极端缩小（如 0.1）时仍保留基本可玩性。scale≥1.0 保持线性缩放，大玩家体验不变。

| 属性 | 值 |
|------|-----|
| **规则名** | `realisticPlayerScale` |
| **描述** | 体型全方位联动：移动/飞行速度随体型平方根缩放（缩小更平缓，有保底），台阶高度、方块与实体交互距离、摔落安全距离随体型等比缩放（缩小有保底下限确保可玩性），跳跃高度随体型平方根缩放（含保底），并补偿缩小后的视野变化（需客户端安装）。需配合玩家随地大小变规则使用，仅 1.21.5+ 版本支持 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |
