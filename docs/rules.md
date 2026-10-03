# Carpet Pry Addition 规则文档

> Mod ID: `carpet-pry-addition` | 版本: `1.2.0`
>
> 共 **29 条**规则
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
  - [patPatPlayers - 摸摸头](#patpatplayers---摸摸头)
  - [whoCalledMe - 谁在叫我](#whocalledme---谁在叫我)
- [生存功能](#生存功能)
  - [playerHat - 玩家帽子](#playerhat---玩家帽子)
  - [betterSnowball - 更好的雪球](#bettersnowball---更好的雪球)
  - [invisibleInTallGrass - 隐身草](#invisibleintallgrass---隐身草)
  - [moreEndCrystalTypes - 更多种类的末地水晶](#moreendcrystaltypes---更多种类的末地水晶)
  - [textAnimation - 米塔字幕](#textanimation---米塔字幕)
  - [redPacket - 红包](#redpacket---红包)
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

安装前置 [skinrestorer](https://modrinth.com/mod/skinrestorer) 后，可以设置假人的皮肤。default=不更改假人皮肤，summon=假人使用召唤者的皮肤，same_skin=假人使用统一皮肤。所有假人（含真人名）出生即穿目标皮肤，观战者不会看到先旧后新的闪变；不写入 skinrestorer 的持久存储，真人玩家的皮肤不受任何影响。

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

> `summon` 模式优先直接复制召唤者在线 profile 的皮肤纹理（无网络请求）；召唤者 profile 无纹理时（离线服常见，真人皮肤由 skinrestorer 在 join 时换上）自动回退为按召唤者名向 provider 解析；控制台/命令方块执行时回退 `fakePlayerSkinSet` 统一皮肤。`same_skin` 模式经 skinrestorer 的 mojang provider 启动时预解析并缓存。皮肤来源未就绪时本次生成退化为出生后换肤（假人先显示默认皮肤再切换），所有回退均记录日志。

> 皮肤仅应用到假人当前会话，不写入 skinrestorer 的持久存储：假人与同名真人玩家共用 UUID（服务器用户缓存命中或离线服场景下），一旦持久化，真人玩家上线时会被换肤，自行通过 `/skin` 设置的皮肤也会被覆盖。因此本规则从不持久化皮肤，真人玩家的皮肤不受任何影响。

> 若安装 [Carpet TIS Addition](https://modrinth.com/mod/carpet-tis-addition)，其提供的 `/player <name> rejoin`（假人在下线位置与朝向重生）同样会应用上述皮肤：rejoin 内部复用 Carpet 原版 spawn 逻辑与假人生成路径，行为与普通 spawn 完全一致（`summon` 模式使用执行 rejoin 命令者的皮肤）。

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

给 `/player <name>` 下追加 brain 子命令，把原版生物式 AI"脑子"挂到假人身上（19 种模式）。核心设计是**只换脑子、不换身体、零额外实体**：假人始终保持 `ServerPlayer` 实体类型，血量/攻击/背包/交互全部为玩家原生属性；AI 的移动指令全部折算为玩家原生按键输入（`zza/xxa` + 限速转向 + 原生跳跃），交由玩家原生 `travel()` 物理执行，绝不直接改写坐标或速度。寻路为自研紧凑 A*（原版 `MobNavigation` 构造器强绑定 `Mob` 实例，为守住"零额外实体"红线，在方块网格上等价复刻了原版寻路的节点推进与卡死重算语义）。架构为策略模式：mixin 在 `ServerPlayer` 初始化时嫁接 `PryMob` 假面接口（导航器/移动控制/视线控制/双目标选择器按需惰性挂载，真人零开销），各模式往双选择器装配移植 Goal，纯服务端实现、客户端无需安装任何模组。

可用模式（`/player <name> brain <mode>`，`off` 为卸载；26.1.2+ 的僵尸/僵尸猪灵模式额外支持原版长矛：主手持矛时由移植版 `SpearUseGoal` 接管——接近→举矛蓄力冲刺→原版动能判定刺中→后撤循环，1.21.x 无长矛物品不受影响）：

| 模式 | 行为 |
|------|------|
| `zombie` | 近战追击最近的玩家（原版 `MeleeAttackGoal` 语义），进入玩家原生攻击距离后调用原生 `attack()` 挥砍 |
| `skeleton` | 主手持弓时激活：远程锁定 + 风筝走位，原生 `startUsingItem → releaseUsingItem` 拉弓消耗背包真实箭矢 |
| `pillager` | 同骷髅但持弩（上弦 25 tick） |
| `irongolem` | 攻击周围敌对生物（`Monster`，不攻击苦力怕，对齐原版铁傀儡）与敌对假人（处于敌对 AI 模式的假人，含报复攻击过自己的玩家） |
| `spider` | 昼中立、夜敌对（对齐原版蜘蛛），夜间疾跑追击 |
| `wolf` | 跟随主人（执行命令的玩家）并仇恨同步：主人被谁打就咬谁；不做原版 >12 格的传送跟随（红线不改坐标，靠走路追回） |
| `villager` | 无仇恨：随机漫步、被攻击恐慌逃离、遇僵尸反向逃跑（原版 `PanicGoal`/`AvoidEntityGoal` 语义） |
| `enderman` | 被凝视激怒（原版 `isStaredAt` 点积算法）后锁定目标并 `setSprinting(true)` 疾跑扑击 |
| `babyzombie` | 小僵尸：更快的近战追击（1.25 疾跑）与更急的索敌 |
| `witherskeleton` | 凋灵骷髅：近战追击玩家/铁傀儡与猪灵类（真实猪灵 + 猪灵脑假人）、规避狼；攻击不附凋零效果（身体附伤，红线不做） |
| `drowned` | 溺尸：持三叉戟远程投掷（蓄力 10 tick 原生掷戟，忠诚/耐久全原生）+ 空手近战；被打会唤醒周围溺尸/僵尸猪灵脑假人的群体仇恨 |
| `zombiepiglin` | 僵尸猪灵：中立，被打才反击并唤醒周围同类群体仇恨（原版愤怒广播语义） |
| `vindicator` | 卫道士：近战追击玩家/村民/铁傀儡 |
| `piglinbrute` | 猪灵蛮兵：恒敌对近战，无视金装 |
| `slime` | 史莱姆：跳行移动——无寻路，idle 连续起跳、锁定后直线跳行追击（对齐原版） |
| `magmacube` | 岩浆怪：同史莱姆（原版继承装配，仅属性差异） |
| `fish` | 鱼（鳕鱼/鲑鱼/热带鱼同款）：水中游动、受伤逃窜、8 格内避人；离水扑腾（不会迈步逃命）；带海豚式换气——憋气不足自动上浮出水回气（假人身体是玩家的肺，不改身体数据）。水中上浮由移动控制器的游泳层翻译为原版跳跃输入（对全部模式生效：任何脑过河都会游泳） |
| `pig` | 猪：纯中立，被打恐慌逃跑，闲时散步 |
| `piglin` | 猪灵：敌视不穿金装的玩家，武器决定战斗方式（金剑近战/弩远程/26.1.2+ 金矛冲锋），捡拾装备并穿戴 |
| `off` | 卸载脑子，恢复 Carpet 手动控制 |

挂载期间 Carpet 的手动移动/攻击指令（`/player <name> move|attack|use` 等）会被屏蔽（actionPack 停摆），`brain off` 或关闭规则后立即恢复；规则关闭、模式切换、假人下线/死亡均自动卸载。视觉同步（行走/疾跑/挥手/拉弓/视角）全部由原版实体同步机制驱动，客户端零依赖。

**状态后缀与 keep**：挂载后假人头顶名牌（及 Tab 列表）出现 `[模式名]` 后缀，颜色按敌对性分档——红（僵尸/骷髅/溺尸/卫道士等见面即打）、蓝（中立：蜘蛛/末影人/猪灵/僵尸猪灵/狼）、绿（不敌对：铁傀儡/村民/猪/鱼）；后缀文字随 `/carpet language` 切换（[僵尸]/[Zombie]/[殭屍]）。实现为每假人一条计分板队伍（`pry_brain_<模式>_<uuid>`）：挂载时假人原队伍的**前缀也会带到头顶**（职衔类前缀挂载期间不消失），原属队伍在挂载前记录、卸载时恢复，本模组队伍随卸载删除，原队属性全程不受影响。`brain <模式> keep` 保持脑子：假人下线重上（重新 spawn）后 1 秒内自动恢复同一模式与后缀（含原前缀），`brain off` 清除保持；keep 为本次服务器运行内有效：服务器重启即作废，重生的假人需重新挂载。模式名在 carpet 中文语言下直接用中文输入（`brain 僵尸`），英文键恒可用。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerBrain` |
| **描述** | 给 /player <name> 追加 brain 子命令，为假人注入原版生物式 AI：19 种模式（zombie/babyzombie/skeleton/witherskeleton/drowned/zombiepiglin/pillager/vindicator/irongolem/spider/piglin/piglinbrute/slime/magmacube/fish/enderman/wolf/villager/pig），纯服务端、零额外实体、不直接改坐标；brain off 或关闭规则即卸载。各模式行为见下表 |
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

主手持不死图腾时，右键玩家头部骑到对方头上（点躯干无反应，假人不参与）。

| 属性 | 值 |
|------|-----|
| **规则名** | `ridingPlayers` |
| **描述** | 主手持不死图腾时，右键玩家头部骑到对方头上（点躯干无反应，假人不参与） |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

---

### pickupPlayers - 捡起玩家

主手持不死图腾时，右键玩家腿脚把对方捡起骑到自己头上（点躯干无反应，假人不参与）。

| 属性 | 值 |
|------|-----|
| **规则名** | `pickupPlayers` |
| **描述** | 主手持不死图腾时，右键玩家腿脚把对方捡起骑到自己头上（点躯干无反应，假人不参与） |
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

### patPatPlayers - 摸摸头

右键点击其他**真人**玩家的头部像撸猫一样抚摸：每次右键一次抚摸脉冲——目标随节奏往复蹲起、头顶冒爱心，发起者挥手；被摸者屏幕内眼前冒爱心、贴耳轻响（从发起者方向传来）、镜头柔和点头。参考 [PatPat](https://github.com/LopyMine/PatPat-Plugin) 模，但为纯服务端实现，原版客户端即可使用。不要求空手，且永不消费交互——本次右键的原版行为与其它模组的处理不受任何影响。carpet 假人在入口即被排除，无法被摸。

| 属性 | 值 |
|------|-----|
| **规则名** | `patPatPlayers` |
| **描述** | 右键点击其他玩家的头部像撸猫一样抚摸：目标随节奏往复蹲起、头顶冒爱心；被摸者眼前冒爱心、贴耳轻响、镜头柔和点头。不要求空手、不改变任何原版交互。true=随手可摸；sneak=按下潜行键右键才摸。纯服务端实现，仅对真人生成（假人排除） |
| **类型** | `string` |
| **默认值** | `"false"` |
| **参考选项** | `false`, `true`, `sneak` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

**工作原理**：复用 ridingPlayers 同款 Fabric `UseEntityCallback`（零 Mixin），挂在骑乘/捡起之后。触发判定只认**带命中坐标的交互包**——客户端 use 流程先发 `INTERACT_AT`（携带命中点，26.x 已合并为带坐标单包），其后补发的裸 `INTERACT` 包 `hitResult` 为 `null` 直接放行原版，因此每次右键天然只触发一次；命中点（实体碰撞箱表面交点）须落在目标碰撞箱顶部约 1/3（头部区域，随潜行/缩放按比例成立），目标须为**真人**（carpet 假人在入口即排除）+ 双方非旁观者 + 连点加速冷却通过后才生效（慢速点击 10 tick 一次；连点每命中一次冷却递减 2 tick、下限 3 tick ≈ 6.6 次脉冲/秒；停手 20 tick 重置）；另受**原版交互距离限制**（3 格，超出时原版在事件触发前静默拒绝，无任何反馈）。**恒返回 PASS（火后不管）**：Fabric 事件链是非 PASS 即短路，消费交互会同时压掉原版 dispatch 与后续监听器，摸头只做效果不拦截。

**效果层（每次脉冲）**：发起者挥手（`swing(hand, true)` 尾参=发给自己，1.21.x 为 `broadcastAndSend`，26.3 为 `sendToTrackingPlayersAndSelf`，三参签名 `swing(hand, SwingAnimation.DEFAULT, true)` 按宏分叉）；目标头顶 1 颗爱心；目标可见的蹲起动作（见下）；贴耳轻响（`ClientboundSoundPacket` 定向高音版只发被摸者且**发声点在发起者位置**——转头即知谁在摸你，其他人听广播版）；被摸者眼前爱心（沿其视线**水平**前方 0.75 格、眼位上方 0.05 生成——任意俯仰都在画面中部升起，不钻进目标模型）；被摸者镜头柔和点头（-2°×2 步低头、保持 2 tick、+2°×2 步回正，各步按当前视角叠加增量，动鼠标不被拽回）。

**蹲下脉冲状态机（撸猫的按头手感）**：每次脉冲检查目标当前潜行来源——目标**自己按着 shift** 则跳过蹲下操作（保持蹲、不弹起）；目标**未潜行**则服务端强制蹲 3 tick 后解除，模型随抚摸节奏往复蹲起。抚摸进行中状态自由迁移：潜行中松开 shift 恢复往复，站立中按下 shift 停止往复；抚摸停止（停止点击）强制蹲到期自动解除，完全交还自主。蹲起为服务端实体标志同步——周围玩家可见其蹲起，被摸者本人画面无蹲感，其客户端在自身 shift 变化时会覆盖标志（如实注明）。仅真人可被摸（carpet 假人在入口即排除）。目标卷入玩家骑乘（作为载具或乘客）时跳过蹲下脉冲——强制蹲会触发骑乘侧"蹲下卸客"/原版"乘客潜行下车"把骑乘塔拆掉；骑乘/捡起挂塔前也会提前释放目标身上未结束的蹲脉冲，防"蹲着被挂塔→立即下车"。

---

### whoCalledMe - 谁在叫我

聊天消息中出现其他玩家的名字时，被点名的玩家收到提醒：连响三声提示音（叮 叮 叮），并弹出 title 显示消息原文。

| 属性 | 值 |
|------|-----|
| **规则名** | `whoCalledMe` |
| **描述** | 聊天消息中出现其他玩家的名字时，被点名的玩家连响三声提示音并弹出 title 显示消息原文 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

**工作原理**：挂在 Fabric `ServerMessageEvents.CHAT_MESSAGE`（注入点为 `PlayerManager.broadcast` 的 HEAD——签名与非签名聊天共同汇聚点，离线服未签名聊天同样触发），零 Mixin 纯服务端。名字匹配大小写不敏感，按 ASCII 玩家名字符集（字母/数字/下划线）取词边界——名字是另一玩家名字的子串时不误伤（"Tim" 不会被 "Timy" 里的 "Tim" 点到）；中文名及名字与汉字粘连（"来一下Alex"/"小明哥"）视为点名（中文无空格分词，粘连即意图）。自己打自己名字、carpet 假人（作为被点名者）不提醒。提示音为紫水晶叮声定向单发（发声点在被听者头顶，仅本人可闻，pitch 2.0），首声当 tick 末尾、余下每 6 tick（300ms）一声共三声；title 固定 0.25s 淡入 / 3s 停留 / 0.5s 淡出，文本即聊天框打出的原文。

## 生存功能---

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

### textAnimation - 米塔字幕

/text 在执行者眼前逐字弹出对话文本，停留后整句坠落消散（米塔游戏的文字显示效果）。

| 属性 | 值 |
|------|-----|
| **规则名** | `textAnimation` |
| **描述** | /text 在执行者眼前逐字弹出对话文本，停留后整句坠落消散（米塔游戏的文字显示效果）；每字一个原版文本展示实体，播完即删，纯服务端实现，客户端无需安装 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `FEATURE`, `COMMAND` |

**工作原理**：命令语法与参数见命令文档 `/text`。时间线：逐字弹出（1 字/tick，每字一个原版 text_display 实体，生成时随机歪斜 ±45° + 1.8 倍缩放，经客户端 10 tick 变换插值收拢到终态，伴随逐字点击声）→ 停留 40 tick → 坠落（服务端自算重力 0.03/tick²、空气阻力 0.99、落地一次 0.28 反弹，起落时一次性随机翻滚，自坠落第 24 tick 起按 -8 透明度/tick 渐隐，播完即删）。长文本按标点断组（≤25 字/组，断点向后 10 字内找标点），非末组追加 " - " 连接符；上一组开始坠落时下一组接续打字，组间随机偏航 ±22.5° 与随机抬高。生成点 = 执行者脚部 + 视线方向 × distance、高度脚部 +1.3；字色默认米塔黄 #FFFF55，支持 & 色码（&c 等，&& 为字面 &，色码按原版 § 语义重置样式）。护栏：单句 ≤128 字、全局并发 ≤8 条会话、目标区块必须处于 ENTITY_TICKING（玩家执行恒成立，空服控制台会得到明确报错——原版区块系统会把加进非实体刻区块的实体按 UNLOADED_TO_CHUNK 写回区块，本地 E2E 实证）。原版从未提供展示实体的程序化接口（文本/变换/插值/透明度设置器均为私有成员，1.21/1.21.11/26.3 javap 核实签名一致），经两枚 @Invoker mixin 访问器驱动；26.1.2 起实体标签读取改名 entityTags、26.2 起实体类型常量迁入 EntityTypes（复数），按预处理宏分叉。崩溃/强杀残留的孤儿实体带 `pry_textanim` 标记，启动 + 每 100 tick 周期清扫（有活跃会话时跳过，防误杀正在播放的字形——周期清扫误杀已本地复现并修复）。

---

### redPacket - 红包

/redpacket 发红包（拼手气/普通/专属/口令四类），聊天框广播可点击领取，过期未领退回。

| 属性 | 值 |
|------|-----|
| **规则名** | `redPacket` |
| **描述** | /redpacket 发红包（拼手气/普通/专属/口令四类），聊天框广播可点击领取，过期未领退回；服务端原版容器 GUI，客户端无需安装 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `FEATURE`, `COMMAND` |

**工作原理**：命令语法、四类分配规则与 GUI 槽位见命令文档 `/redpacket`。全程服务端原版容器菜单（箱子/铁砧）实现，按钮与头颅槽经容器层拦截保护（不可取出/放入），点击转回调，客户端预测的 ghost 由下一次 broadcastChanges 纠正；聊天广播为亮红可点击组件（RUN_COMMAND 打开 `/redpacket claim <id>`），口令红包经聊天框逐字匹配领取（复用 ServerMessageEvents.CHAT_MESSAGE）。份额在发出时按类型一次性切好，总量守恒（星与条随机组成/余数随机落份，JUnit 契约测试覆盖）；有效期 3 分钟，过期未领份额原样退回发送者，离线暂存上线补发；领完立即结束。防滥用：领取点击防抖 10 tick、发送冷却 10 秒、每人同时最多 3 个未结束红包。玩家头颅经 PROFILE 组件按名字异步解析（1.21.10 起为 ResolvableProfile 工厂方法，1.21~1.21.8 为 record 构造，javap 核实分叉）；26.2 起染色物品常量并入 ColorCollection 按 pick(DyeColor) 取值。**重启失效语义**：进行中的红包、离线退回暂存、冷却与防抖状态均为内存态，服务器重启即清空（与假人脑 keep 同款如实注明）。

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
