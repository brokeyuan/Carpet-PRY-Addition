# Carpet Pry Addition 规则文档

> Mod ID: `carpet-pry-addition` | 版本: `1.2.0`
>
> 共 **27 条**规则
>
> **提示：可以使用 `Ctrl+F` 快速查找自己想要的规则**

---

## 快速导航

- [假人相关 (BOT)](#假人相关-bot)
  - [fakePlayerTpp - 假人珍珠站传送](#fakeplayertpp---假人珍珠站传送)
  - [fakePlayerNameSuggestions - 假人名称建议](#fakeplayernamesuggestions---假人名称建议)
  - [fakePlayerSkinMode - 假人皮肤设置](#fakeplayerskinmode---假人皮肤设置)
  - [fakePlayerSkinSet - 假人统一皮肤设置](#fakeplayerskinset---假人统一皮肤设置)
  - [fakePlayerDropAll - 假人持续清空背包](#fakeplayerdropall---假人持续清空背包)
  - [fakePlayerSendto - 假人背包链接](#fakeplayersendto---假人背包链接)
  - [fakePlayerBrain - 假人脑子](#fakeplayerbrain---假人脑子)
- [漏洞修复 (BUGFIX)](#漏洞修复-bugfix)
  - [fixXaeroLib - XaeroLib兼容性修复补丁](#fixxaerolib---xaerolib兼容性修复补丁)
  - [fixBlueMap - BlueMap兼容性修复补丁](#fixbluemap---bluemap兼容性修复补丁)
  - [fixEndCrystalSync - 修复末地水晶位置不同步](#fixendcrystalsync---修复末地水晶位置不同步)
- [移植功能 (PORTING)](#移植功能-porting)
  - [sleepingDuringTheDay - 白日做梦](#sleepingduringtheday---白日做梦)
  - [unicodeArgumentsSupport - Unicode 参数支持](#unicodeargumentssupport---unicode-参数支持)
- [玩家交互](#玩家交互)
  - [ridingPlayers - 骑乘玩家](#ridingplayers---骑乘玩家)
  - [pickupPlayers - 捡起玩家](#pickupplayers---捡起玩家)
  - [ridingPlayersStackLimit - 玩家骑乘堆叠上限](#ridingplayersstacklimit---玩家骑乘堆叠上限)
  - [ridingPlayersAutoDismount - 玩家骑乘更改模式下车](#ridingplayersautodismount---玩家骑乘更改模式下车)
  - [ridingPlayersClientInteract - 玩家骑乘时可交互（客户端）](#ridingplayersclientinteract---玩家骑乘时可交互客户端)
  - [peacefulPlayers - 和平的玩家](#peacefulplayers---和平的玩家)
- [生存功能](#生存功能)
  - [playerHat - 玩家帽子](#playerhat---玩家帽子)
  - [betterSnowball - 更好的雪球](#bettersnowball---更好的雪球)
  - [invisibleInTallGrass - 隐身草](#invisibleintallgrass---隐身草)
  - [moreEndCrystalTypes - 更多种类的末地水晶](#moreendcrystaltypes---更多种类的末地水晶)
- [玩家缩放](#玩家缩放)
  - [playerScale - 玩家随地大小变](#playerscale---玩家随地大小变)
  - [playerScaleMin - 玩家大小最小值](#playerscalemin---玩家大小最小值)
  - [playerScaleMax - 玩家大小最大值](#playerscalemax---玩家大小最大值)
  - [playerScalePhysics - 更真实的玩家大小变](#playerscalephysics---更真实的玩家大小变)
  - [playerScaleLinkedEntities - 玩家大小变联动实体](#playerscalelinkedentities---玩家大小变联动实体)

---

## 假人相关 (BOT)

### fakePlayerTpp - 假人珍珠站传送

使用假人快速使用珍珠传送站。当为true时启用/tppset设置指令和/tpp 玩家指令。`/tpp` 传送需前置 [Carpet TIS Addition](https://modrinth.com/mod/carpet-tis-addition)（其 `/player rejoin` 让站点假人在下线位置与朝向重生）；未安装时执行 `/tpp` 会立即提示缺少前置，`/tppset` 站点管理与其余功能不受影响。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerTpp` |
| **描述** | 使用假人快速使用珍珠传送站；开启后启用 /tppset 站点管理与 /tpp 传送命令。/tpp 需前置 Carpet TIS Addition（提供 /player rejoin），未安装时其余功能不受影响 |
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
| **描述** | 自定义 /player 命令建议的假人名称列表，使用 ',' 分隔 |
| **类型** | `string` |
| **默认值** | `Steve,Alex` |
| **参考选项** | `Steve,Alex`, `Pry,hsds`, `Pry,hsds,Firework,Food`, `` |
| **分类** | `PRIMARYUAN`, `BOT` |

---

### fakePlayerSkinMode - 假人皮肤设置

安装前置 [skinrestorer](https://modrinth.com/mod/skinrestorer) 后，可以设置假人的皮肤。default=不更改假人皮肤，summon=假人使用召唤者的皮肤，same_skin=假人使用统一皮肤。皮肤仅对假人生效，不写入 skinrestorer 的持久存储，因此不会影响同名真人玩家的皮肤。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerSkinMode` |
| **描述** | 安装前置 skinrestorer 后可设置假人皮肤：default=不修改；summon=使用召唤者的皮肤；same_skin=使用 fakePlayerSkinSet 指定玩家的皮肤。皮肤仅对假人生效，不写入 skinrestorer 的持久存储，因此不会影响同名真人玩家的皮肤 |
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

> 皮肤仅应用到假人当前会话，不写入 skinrestorer 的持久存储：假人与同名真人玩家共用 UUID（服务器用户缓存命中或离线服场景下），一旦持久化，真人玩家上线时会被换肤，自行通过 `/skin` 设置的皮肤也会被覆盖。因此本规则从不持久化皮肤，真人玩家的皮肤不受任何影响。

> 若安装 [Carpet TIS Addition](https://modrinth.com/mod/carpet-tis-addition)，其提供的 `/player <name> rejoin`（假人在下线位置与朝向重生）同样会应用上述皮肤：rejoin 内部复用 Carpet 原版 spawn 逻辑，本模组的皮肤注入点位于其末尾，行为与普通 spawn 完全一致（`summon` 模式使用执行 rejoin 命令者的皮肤）。

---

### fakePlayerSkinSet - 假人统一皮肤设置

当 fakePlayerSkinMode 为 same_skin 时，设置用于假人皮肤的玩家名称。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerSkinSet` |
| **描述** | fakePlayerSkinMode 为 same_skin 时，用于统一假人皮肤的玩家名 |
| **类型** | `string` |
| **默认值** | `Brokeyuan` |
| **参考选项** | `Brokeyuan`, `hsds`, `` |
| **分类** | `PRIMARYUAN`, `BOT` |

---

### fakePlayerDropAll - 假人持续清空背包

给 `/player <name>` 下追加独立的 dropall 子命令，让假人按设定节奏持续丢出背包所有物品。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerDropAll` |
| **描述** | 给 /player <name> 追加 dropall 子命令，让假人按设定节奏持续丢出背包所有物品 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `BOT`, `COMMAND` |

---

### fakePlayerSendto - 假人背包链接

给 /player <name> 追加 sendto 子命令，建立假人之间单向的背包物品流链接。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerSendto` |
| **描述** | 给 /player <name> 追加 sendto 子命令，建立假人间单向背包物品流（默认每 tick 一组，多目标轮流分配，目标满时源背包缓冲），转移频率可调；sendto stop 停止并移除链接，链接不跨重启 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `BOT`, `COMMAND` |

---

### fakePlayerBrain - 假人脑子

给 `/player <name>` 下追加 brain 子命令，把原版生物式 AI"脑子"挂到假人身上（11 种模式）。核心设计是**只换脑子、不换身体、零额外实体**：假人始终保持 `ServerPlayer` 实体类型，血量/攻击/背包/交互全部为玩家原生属性；AI 的移动指令全部折算为玩家原生按键输入（`zza/xxa` + 限速转向 + 原生跳跃），交由玩家原生 `travel()` 物理执行，绝不直接改写坐标或速度。寻路为自研紧凑 A*（原版 `MobNavigation` 构造器强绑定 `Mob` 实例，为守住"零额外实体"红线，在方块网格上等价复刻了原版寻路的节点推进与卡死重算语义）。架构为策略模式：mixin 在 `ServerPlayer` 初始化时嫁接 `PryMob` 假面接口（导航器/移动控制/视线控制/双目标选择器按需惰性挂载，真人零开销），各模式往双选择器装配移植 Goal，纯服务端实现、客户端无需安装任何模组。

可用模式（`/player <name> brain <mode>`，`off` 为卸载；26.1.2+ 的僵尸模式额外支持原版长矛：主手持矛时由移植版 `SpearUseGoal` 接管——接近→举矛蓄力冲刺→原版动能判定刺中→后撤循环，1.21.x 无长矛物品不受影响）：

| 模式 | 行为 |
|------|------|
| `zombie` | 近战追击最近的玩家（原版 `MeleeAttackGoal` 语义），进入玩家原生攻击距离后调用原生 `attack()` 挥砍 |
| `skeleton` | 主手持弓时激活：远程锁定 + 风筝走位，原生 `startUsingItem → releaseUsingItem` 拉弓消耗背包真实箭矢 |
| `pillager` | 同骷髅但持弩（上弦 25 tick） |
| `irongolem` | 攻击周围敌对生物（`Monster`，不攻击苦力怕，对齐原版铁傀儡）与敌对假人（处于敌对 AI 模式的假人，含报复攻击过自己的玩家） |
| `spider` | 昼中立、夜敌对（对齐原版蜘蛛），夜间疾跑追击 |
| `wolf` | 跟随主人（执行命令的玩家）并仇恨同步：主人被谁打就咬谁 |
| `villager` | 无仇恨：随机漫步、被攻击恐慌逃离、遇僵尸反向逃跑（原版 `PanicGoal`/`AvoidEntityGoal` 语义） |
| `enderman` | 被凝视激怒（原版 `isStaredAt` 点积算法）后锁定目标并 `setSprinting(true)` 疾跑扑击 |
| `babyzombie` | 小僵尸：更快的近战追击（1.25 疾跑）与更急的索敌 |
| `off` | 卸载脑子，恢复 Carpet 手动控制 |

挂载期间 Carpet 的手动移动/攻击指令（`/player <name> move|attack|use` 等）会被屏蔽（actionPack 停摆），`brain off` 或关闭规则后立即恢复；规则关闭、模式切换、假人下线/死亡均自动卸载。视觉同步（行走/疾跑/挥手/拉弓/视角）全部由原版实体同步机制驱动，客户端零依赖。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerBrain` |
| **描述** | 给 /player <name> 追加 brain 子命令，为假人注入生物 AI（纯服务端、零额外实体、无需客户端模组；AI 移动全部折算为玩家原生按键输入，绝不直接改坐标），8 种模式：zombie / skeleton / pillager / irongolem / spider / wolf / villager / enderman |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `BOT`, `COMMAND` |

---

## 漏洞修复 (BUGFIX)

### fixXaeroLib - XaeroLib兼容性修复补丁

修复高版本Xaero 搭配LuckPerms 会导致假人数据丢失的问题。

| 属性 | 值 |
|------|-----|
| **规则名** | `fixXaeroLib` |
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

### fixBlueMap - BlueMap兼容性修复补丁

修复假人不触发Fabric API连接事件导致BlueMap等模组无法正确追踪假人上下线的问题。

| 属性 | 值 |
|------|-----|
| **规则名** | `fixBlueMap` |
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

### fixEndCrystalSync - 修复末地水晶位置不同步

修复活塞推动末地水晶（含无敌水晶）后，客户端显示位置与服务端真实位置不同步的问题。

| 属性 | 值 |
|------|-----|
| **规则名** | `fixEndCrystalSync` |
| **描述** | 修复活塞推动末地水晶后客户端与服务端位置不同步的问题。原因：原版末地水晶不做周期性位置同步，活塞推动由客户端和服务端各自独立模拟，模拟结果一旦分歧永不自愈（重进/重启后才恢复）。开启后服务端检测到末地水晶位置变化即强制执行原版位置同步，客户端 1 tick 内自动对齐，重进服务器时的出生时序竞争同样会被纠正 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `BUGFIX` |

**工作原理**：原版把末地水晶的跟踪间隔注册为 `updateInterval=Integer.MAX_VALUE`，服务端位置同步分支对水晶永不触发（除出生包外）；活塞推动时客户端与服务端各自在 `PistonMovingBlockEntity` 里独立模拟，一旦分歧没有任何自愈途径。开启后服务端在水晶位置变化时把 `ServerEntity.tickCount` 归零，令本次同步命中原版位置同步分支——数据包构造与广播完全复用原版，客户端 1 tick 内对齐。26.2 起 `tickCount` 自增移至门控之前，按版本预处理置 -1。

---

## 移植功能 (PORTING)

### sleepingDuringTheDay - 白日做梦

允许玩家在白天睡觉，睡觉后切换至夜晚（参考 PCA）。

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

### ridingPlayersStackLimit - 玩家骑乘堆叠上限

骑乘和捡起时最多可堆叠的玩家数量，骑乘和捡起共用此上限。按整座玩家塔计：塔内玩家总数（含被骑的基座与本次发起骑乘/捡起的玩家）达到上限后，后续骑乘与捡起均被拒绝。

| 属性 | 值 |
|------|-----|
| **规则名** | `ridingPlayersStackLimit` |
| **描述** | 骑乘和捡起时最多可堆叠的玩家数量，骑乘和捡起共用此上限 |
| **类型** | `int` |
| **默认值** | `16` |
| **参考选项** | `16`, `32` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### ridingPlayersAutoDismount - 玩家骑乘更改模式下车

当玩家游戏模式变更的时候，让头上的玩家下车。

| 属性 | 值 |
|------|-----|
| **规则名** | `ridingPlayersAutoDismount` |
| **描述** | 当玩家游戏模式变更的时候，让头上的玩家下车 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### ridingPlayersClientInteract - 玩家骑乘时可交互（客户端）

需客户端安装，当头上有乘客的时候，仍可与方块/实体交互。

| 属性 | 值 |
|------|-----|
| **规则名** | `ridingPlayersClientInteract` |
| **描述** | 需客户端安装，当头上有乘客的时候，仍可与方块/实体交互 |
| **类型** | `boolean` |
| **默认值** | `true` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE`, `CLIENT` |

---

### peacefulPlayers - 和平的玩家

启用 /pvp 指令，按玩家开关 PVP：双向免伤、自伤不限，拦截时播放提示音。

| 属性 | 值 |
|------|-----|
| **规则名** | `peacefulPlayers` |
| **描述** | 启用 /pvp 按玩家开关 PVP：双向免伤、自伤不限，拦截时播放提示音。false=隐藏命令；self=仅能调自己；true=管理员可调任意玩家；everyone=人人可调；/pvp on\|off @a 为全服总开关（仅管理员），全局关闭期间锁定个人开关，状态跨重启保留 |
| **类型** | `string` |
| **默认值** | `"false"` |
| **参考选项** | `false`, `true`, `self`, `everyone` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |


---

## 生存功能

### playerHat - 玩家帽子

允许玩家将物品戴在头上，并添加/hat指令。头部放置不死图腾时可触发死亡保护效果。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerHat` |
| **描述** | 允许玩家将物品戴在头上，并添加/hat指令。头部放置不死图腾时可触发死亡保护效果 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### betterSnowball - 更好的雪球

雪中塞石。允许雪球给玩家造成击退和伤害。

| 属性 | 值 |
|------|-----|
| **规则名** | `betterSnowball` |
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

### moreEndCrystalTypes - 更多种类的末地水晶

允许把末地水晶放在哭泣的黑曜石上。原版末地水晶只能放在黑曜石和基岩上，本规则解锁哭泣的黑曜石作为水晶基座。设为 `invulnerable` 时放出的为无敌水晶——与原版复活龙过程中推出柱子、打断复活得到的水晶相同（`Invulnerable=1` 且显示底部黑曜石板）；设为 `true` 时放出的为不无敌的普通水晶。两种模式放出的水晶光束均指向固定坐标 `(0, 128, 0)`（与原版复活龙水晶一致，无论放在哪里）。放在普通黑曜石/基岩上的水晶不受影响。

| 属性 | 值 |
|------|-----|
| **规则名** | `moreEndCrystalTypes` |
| **描述** | 允许把末地水晶放在哭泣的黑曜石上：true=放出的为普通水晶（不无敌）；invulnerable=放出的为无敌水晶（Invulnerable=1 且显示底部板，与原版复活龙时推出的无敌水晶相同）。两种模式放出的水晶光束均指向固定坐标 (0,128,0)。放在普通黑曜石/基岩上的水晶不受影响 |
| **类型** | `string` |
| **默认值** | `false` |
| **参考选项** | `false`, `true`, `invulnerable` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

**工作原理**：原版 `EndCrystalItem.useOn` 只接受黑曜石/基岩作为基座，规则开启时把点击的哭泣黑曜石替换为黑曜石状态骗过校验，其余放置逻辑（上方空间检查、实体碰撞检查、生成、扣物品）全部复用原版。生成的水晶被设置光束指向点 `(0, 128, 0)`；无敌模式下再被设为 `Invulnerable`（无法被攻击/爆炸摧毁，仍可被活塞推动、创造模式破坏）并显示底部板。客户端与服务端共用同一份 `useOn`，本地预测与服务端判定一致；纯原版客户端在服务器上也可用（1.19+ 客户端先发送交互包再做本地预测）。

---

## 玩家缩放

### playerScale - 玩家随地大小变

为 Player 注册 `minecraft:scale` 属性，并通过 `/scale set|reset|info` 命令调节玩家体型大小（value 仅要求大于 0，不设上下限；本模组放行 SCALE 属性的任意有限正值，保证命令反馈值与实际生效值一致）；同时补偿缩放带来的视野（FOV）变化（需客户端安装本模组）。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerScale` |
| **描述** | 为 Player 注册 minecraft:scale 属性并启用 /scale set\|reset\|info；value 仅需大于 0，受 playerScaleMin/Max 约束。false=隐藏命令；self=仅能调自己；true=可调自己且管理员可调任意玩家；everyone=人人可互调。FOV 补偿需客户端安装 |
| **类型** | `string` |
| **默认值** | `"false"` |
| **参考选项** | `false`, `true`, `self`, `everyone` |
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

所有玩家执行 `/scale set` 时可设置的最小 scale 值（含管理员，软边界约束所有人），管理员可通过修改本规则调整边界。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerScaleMin` |
| **描述** | 所有玩家执行 /scale set 时可设置的最小 scale 值，管理员可通过修改本规则调整边界 |
| **类型** | `double` |
| **默认值** | `0.1` |
| **参考选项** | `0.1`, `0.01`, `0.25`, `0.5` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### playerScaleMax - 玩家大小最大值

所有玩家执行 `/scale set` 时可设置的最大 scale 值（含管理员，软边界约束所有人），管理员可通过修改本规则调整边界。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerScaleMax` |
| **描述** | 所有玩家执行 /scale set 时可设置的最大 scale 值，管理员可通过修改本规则调整边界 |
| **类型** | `double` |
| **默认值** | `1.5` |
| **参考选项** | `1.5`, `2.0`, `5.0`, `10.0`, `16.0` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |

---

### playerScalePhysics - 更真实的玩家大小变

开启后玩家物理特性随体型联动，提供四种模式。需配合玩家随地大小变规则使用。

| **规则名** | `playerScalePhysics` |
| **描述** | 玩家物理随体型联动（需 playerScale）：true=平缓，关键属性按 √scale 缩放；safety=平缓+小体型保底（速度/重力 0.3×，跳跃/台阶/交互/摔落 0.5×，推荐）；strict=严格等比 ×scale，跳高与体型成正比。数值细节见规则文档 |
| **类型** | `string` |
| **默认值** | `false` |
| **参考选项** | `false`, `true`, `safety`, `strict` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

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

### playerScaleLinkedEntities - 玩家大小变联动实体

玩家使用物品直接生成的生物实体继承玩家当前体型。需配合玩家随地大小变规则使用（体型来源也可以是 /attribute 设置的 minecraft:scale）。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerScaleLinkedEntities` |
| **描述** | 玩家用物品直接生成的生物实体继承玩家体型快照（盔甲架、刷怪蛋生物、铁/雪/铜傀儡等，比例与幼崽等原修饰符叠加）。仅限生物实体与玩家手持来源；发射器/刷怪笼与非生物实体不联动 |
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
