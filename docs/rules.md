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

### playerScale - 玩家随地大小变

为 Player 注册 `minecraft:scale` 属性，并通过 `/scale set|reset|info` 命令调节玩家体型大小（value 仅要求大于 0，不设上下限；本模组放行 SCALE 属性的任意有限正值，保证命令反馈值与实际生效值一致）；同时补偿缩放带来的视野（FOV）变化（需客户端安装本模组）。


| 属性 | 值 |
|------|-----|
| **规则名** | `playerScale` |
| **描述** | 为 Player 注册 minecraft:scale 属性，并添加 /scale set/reset/info 命令（value 仅要求大于 0，不设上下限；超出原版属性范围 0.0625–16 时纯原版客户端的显示仍会被夹紧），并补偿缩放带来的视野（FOV）变化（需客户端安装）。false=关闭命令；self=所有人都只能调自己（无论 OP）；true=玩家仅可调自己，管理员可调任意玩家；everyone=所有人可调任意玩家 |
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

非管理员玩家执行 `/scale set` 时可设置的最小 scale 值；所有玩家（含管理员）均受此限制，管理员可通过修改本规则调整边界。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerScaleMin` |
| **描述** | 所有玩家执行 /scale set 时可设置的最小 scale 值，管理员可通过修改本规则调整边界 |
| **类型** | `double` |
| **默认值** | `0.01` |
| **参考选项** | `0.01`, `0.1`, `0.25`, `0.5` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### playerScaleMax - 玩家大小最大值

非管理员玩家执行 `/scale set` 时可设置的最大 scale 值；所有玩家（含管理员）均受此限制，管理员可通过修改本规则调整边界。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerScaleMax` |
| **描述** | 所有玩家执行 /scale set 时可设置的最大 scale 值，管理员可通过修改本规则调整边界 |
| **类型** | `double` |
| **默认值** | `16.0` |
| **参考选项** | `2.0`, `5.0`, `10.0`, `16.0` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### realisticPlayerScale - 更真实的玩家大小变

开启后玩家物理特性随体型联动，提供四种模式。需配合玩家随地大小变规则使用。

| 模式 | 行为 |
|------|------|
| `false` | 关闭（默认） |
| `true` | 平缓：所有联动量按 √scale 曲线缩放，无保底 |
| `safety` | 平缓+保底（推荐）：同 true 曲线，小体型（scale<1.0）加保底，极端缩小仍可玩 |
| `strict` | 严格等比：所有联动量严格按 scale 等比缩放，无任何保底 |

| 联动维度 | true / safety（平缓） | strict（严格等比） | safety 保底（仅 scale<1.0） |
|----------|----------------------|--------------------|------------------------------|
| 移动速度（行走） | √scale | ×scale | 0.3× |
| 飞行速度（创造飞行） | √scale | ×scale | 0.3× |
| 鞘翅滑翔/烟花加速移动距离 | √scale | ×scale | 0.3× |
| 跳跃高度（跳跃初速） | √scale（跳高 ∝ √scale） | scale^0.75（跳高 ∝ scale） | 0.5× |
| 台阶高度 | √scale | ×scale | 0.5× |
| 方块交互/攻击距离 | √scale | ×scale | 0.5× |
| 实体交互/攻击距离 | √scale | ×scale | 0.5× |
| 摔落安全距离 | √scale | ×scale | 0.5× |
| 下落速度（重力） | √scale | √scale | 0.3× |

所有属性修改均使用瞬态修改器（不落盘存档），关闭规则或体型恢复 1.0 后自动移除。三种模式的设计取向：

- **true / safety（平缓）**：√scale 曲线让大体型的移速/极速增幅更克制（16 倍体型移速 4 倍而非 16 倍）、小体型更飘逸；`safety` 在此之上为 scale<1.0 提供保底（速度/飞行/重力 0.3×，跳跃/台阶/交互/摔落 0.5×），确保极端缩小（如 0.1）仍保留基本可玩性，推荐大多数服务器使用。
- **strict（严格等比）**：移速、台阶、交互距离、摔落安全距离严格 ×scale；跳跃初速 ×scale^0.75，与重力 √scale 配套后跳高与体型等比放大（2 倍体型跳 2 倍高）；无任何保底，小体型可能弱到无法与方块交互，适合追求几何真实的场景。
- **重力**：三种模式均 ×√scale——重力是加速度而非尺寸量，√ 曲线与跳跃曲线配套且保持终端速度可控（若按线性缩放，16 倍体型终端速度约 62 格/tick 会失控穿地）。

创造飞行时原版会以飞行前的竖直速度覆盖重力，重力属性无效；鞘翅滑翔的重力项随重力属性生效，滑翔与烟花的移动距离由鞘翅联动 mixin 按移动速度联动因子缩放（因子随模式变化，见上表）——大体型滑翔极速与烟花极速随体型放大、转向半径更大（几何相似），小体型更慢更飘。已知取舍：鞘翅撞墙伤害按存储速度（原版量级）计算，不随位移缩放。

| **规则名** | `realisticPlayerScale` |
| **描述** | 体型全方位联动，四种模式：false=关闭；true=平缓，移动/飞行/鞘翅烟花速度、跳跃、台阶、交互距离、摔落安全距离均按 √scale 平缓缩放，无保底；safety=平缓+保底（推荐），在 true 基础上为小体型保底（速度/重力 0.3×，跳跃/台阶/交互/摔落 0.5×），极端缩小仍可玩；strict=严格等比，所有速度、台阶、交互、摔落严格 ×scale，跳跃高度与体型等比放大（跳跃初速 ×scale^0.75），无任何保底。重力均为 √scale（大体型下落更快）。FOV 补偿归属 playerScale 规则（v1.1.8 起）。需配合玩家随地大小变规则使用 |
| **类型** | `string` |
| **默认值** | `false` |
| **参考选项** | `false`, `true`, `safety`, `strict` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

### playerScaleLinkedEntities - 玩家大小变联动实体

玩家使用物品直接生成的生物实体继承玩家当前体型。需配合玩家随地大小变规则使用（体型来源也可以是 /attribute 设置的 minecraft:scale）。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerScaleLinkedEntities` |
| **描述** | 玩家使用物品直接生成的生物实体继承玩家当前体型（写入 minecraft:scale 基础值快照）：0.5 的玩家放出的盔甲架也是 0.5 大小，刷怪蛋生物、摆出的铁傀儡/雪傀儡/铜傀儡同理（幼崽等原有修饰符在此基础上叠加比例）。仅覆盖有 scale 属性的生物实体；投掷物、掉落物、物品展示框、船、矿车、TNT 等非生物实体不联动；发射器、刷怪笼等非玩家来源不联动；玩家体型为 1.0 时不做任何改动 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

**工作原理**：服务端所有"玩家使用物品"都经 `ServerPlayerGameMode` 的 useItem / useItemOn 漏斗，实体最终经 `ServerLevel.addFreshEntity` 添加（盔甲架的 addFreshEntityWithPassengers 也是逐个委托它）。规则在漏斗期间记录操作玩家，实体添加时把玩家的 `minecraft:scale` 当前值写入生成生物的 scale 基础值。

**覆盖范围**：

- ✅ 生物实体（写入 minecraft:scale 基础值，模型+碰撞箱随原版属性生效并同步客户端）：盔甲架、刷怪蛋生成的生物（幼崽修饰符叠加比例）、摆出的铁傀儡/雪傀儡/铜傀儡（放南瓜的构造生成同步发生在使用漏斗内）
- ❌ 投掷物、掉落物、物品展示框、船、矿车、TNT、末影水晶、画等非生物实体：不联动（不触碰渲染与碰撞箱）
- ❌ 发射器、刷怪笼等方块装置生成的实体——非玩家使用路径，不联动
- 写入的是生成时刻的快照，之后不随玩家体型变化；玩家体型为 1.0 时完全不改写（不影响模组自定义体型的生物）
