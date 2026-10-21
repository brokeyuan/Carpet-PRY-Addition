# Carpet Pry Addition 规则文档

> Mod ID: `carpet-pry-addition` | 版本: `1.3.0`
>
> 共 **35 条**规则（`whoCalledMeSound` / `whoCalledMeHighlight` 作为配套子规则内联在 whoCalledMe 节）
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
  - [fixStandingOnAir - 修复站空误判](#fixstandingonair---修复站空误判)
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
  - [playerHeadSkin - 玩家头部皮肤](#playerheadskin---玩家头部皮肤)
  - [betterSnowball - 更好的雪球](#bettersnowball---更好的雪球)
  - [invisibleInTallGrass - 隐身草](#invisibleintallgrass---隐身草)
  - [moreEndCrystalTypes - 更多种类的末地水晶](#moreendcrystaltypes---更多种类的末地水晶)
  - [textAnimation - 米塔字幕](#textanimation---米塔字幕)
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
| **描述** | 用假人触发珍珠传送站，提供 /tpp 和 /tppset。/tpp 需要 Carpet TIS Addition。 |
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

安装前置 [skinrestorer](https://modrinth.com/mod/skinrestorer) 后，可以设置假人的皮肤。default=不更改假人皮肤，summon=假人使用召唤者的皮肤，same_skin=假人使用统一皮肤。皮肤已就绪时在出生前应用；尚在解析时可能短暂显示默认皮肤。仅对当前假人会话生效，不写入 skinrestorer 存储，不影响真人。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerSkinMode` |
| **描述** | 通过 skinrestorer 设置假人皮肤，使用召唤者或指定玩家的皮肤。不写入皮肤存储，不影响真人。 |
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

> `summon` 优先使用召唤者当前皮肤，无纹理时按名字查询；控制台或命令方块召唤时使用 `fakePlayerSkinSet`。`same_skin` 在启动时解析指定玩家皮肤，未就绪时可能短暂显示默认皮肤。

> 皮肤只对假人当前会话生效，不写入 skinrestorer 存储，不覆盖同名真人自行设置的皮肤。

> [Carpet TIS Addition](https://modrinth.com/mod/carpet-tis-addition) 的 `/player <name> rejoin` 同样应用这些设置；`summon` 模式采用命令执行者的皮肤。

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
| **描述** | 添加 /player 的 dropall 子命令，立即清空假人背包或按设定节奏逐组丢出物品。 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `BOT`, `COMMAND` |

---

### fakePlayerSendto - 假人背包链接

给 /player `<name>` 追加 sendto 子命令，建立假人之间单向的背包物品流链接。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerSendto` |
| **描述** | 添加 /player 的 sendto 子命令，在假人间按设定节奏单向转移背包物品。链接不跨重启保留。 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `BOT`, `COMMAND` |

---

### fakePlayerBrain - 假人脑子

用 `/player <name> brain` 给 Carpet 假人挂载 19 种生物行为。假人保留玩家的身体、背包与战斗方式，不生成额外实体或物品；仅需服务端安装。

可用模式（`off` 卸载并恢复 Carpet 手动控制；26.1.2+ 的僵尸与僵尸猪灵模式还支持长矛）：

| 模式 | 行为 |
|------|------|
| `zombie` | 近战追击玩家。 |
| `skeleton` | 持弓远程射击并走位，消耗背包箭矢。 |
| `pillager` | 持弩远程射击，上弦 25 tick。 |
| `irongolem` | 攻击敌对生物与敌对假人，不主动攻击苦力怕；被攻击时反击。 |
| `spider` | 白天中立，夜间敌对并疾跑追击。 |
| `wolf` | 跟随执行命令的主人并协助战斗，不传送跟随。 |
| `villager` | 随机漫步，被攻击时逃跑，并躲避僵尸。 |
| `enderman` | 被凝视后激怒，疾跑追击。 |
| `babyzombie` | 比普通僵尸更快的近战追击。 |
| `witherskeleton` | 近战追击玩家、铁傀儡和猪灵类，躲避狼；攻击不附凋零。 |
| `drowned` | 持三叉戟时远程投掷，否则近战；被攻击时唤醒附近溺尸和僵尸猪灵脑假人。 |
| `zombiepiglin` | 被攻击后反击，并唤醒附近同类。 |
| `vindicator` | 近战追击玩家、村民和铁傀儡。 |
| `piglinbrute` | 始终敌对，近战攻击，无视金装。 |
| `slime` | 持续跳行，锁定目标后直线跳行追击。 |
| `magmacube` | 与史莱姆相同，以跳行移动和追击。 |
| `fish` | 水中游动，躲避 8 格内玩家；受伤逃窜，缺氧上浮，离水扑腾。 |
| `pig` | 闲时漫步，被攻击时逃跑。 |
| `piglin` | 敌视未穿金装的玩家，按武器近战、用弩或长矛；捡拾并穿戴装备。 |
| `off` | 卸载脑子，恢复 Carpet 手动控制 |

挂载脑子期间暂停 Carpet 的手动移动、攻击与使用指令，`brain off` 或关闭规则后恢复。死亡、下线和切换模式会卸载旧脑子。

**状态后缀与 keep**：头顶名牌及 Tab 显示模式后缀，红色为敌对、蓝色为中立、绿色为非敌对。文字随 `/carpet language` 切换，英文模式键始终可用。保留原队伍前缀，卸载时恢复原队伍。`brain <模式> keep` 在本次服务器运行内保留模式与狼的主人，假人重上后 1 秒内恢复；服务器重启或 `brain off` 会清除。

| 属性 | 值 |
|------|-----|
| **规则名** | `fakePlayerBrain` |
| **描述** | 添加 /player 的 brain 子命令，让假人按原版生物的行为自主行动。 |
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
| **描述** | 修复活塞推动末地水晶后，客户端显示位置与服务端不一致的问题。 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `BUGFIX` |

开启后，水晶位置变化会同步给观察者，无敌水晶同样生效。关闭后恢复原版行为。

---

### fixStandingOnAir - 修复站空误判

修复站空恢复时误发脚下方块，并取消 `Player <x> standing on air - force-sending blocks below` INFO 日志。仅 26.3 生效。

| 属性 | 值 |
|------|-----|
| **规则名** | `fixStandingOnAir` |
| **描述** | 修复 26.3 站空恢复时误发脚下方块的问题，并取消站空 INFO 日志。仅 26.3 生效。 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `BUGFIX` |

将脚下方块重发延后到玩家移动后，并取消站空 INFO 日志。保留方块重发与 200 tick 冷却；关闭规则恢复原版行为。仅 26.3 生效。

- [MC-311474](https://bugs.mojang.com/browse/MC/issues/MC-311474) — standing on air 日志误报刷屏
- [MC-311727](https://mojira.dev/MC-311727) — 检测先于移动的根因，官方修于 26.4 Snapshot 1

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
| **描述** | 用 /pvp 按玩家开关 PVP，关闭后不能攻击其他玩家，也不受玩家伤害。状态跨重启保留。 |
| **类型** | `string` |
| **默认值** | `"false"` |
| **参考选项** | `false`, `true`, `self`, `everyone` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `COMMAND` |


---

### patPatPlayers - 摸摸头

右键其他真人玩家的头部摸摸头，显示爱心、播放轻响，并让对方短暂蹲起。双方须非旁观者；不要求空手，不拦截原版交互，Carpet 假人不参与。参考 [PatPat](https://github.com/LopyMine/PatPat-Plugin)。

| 属性 | 值 |
|------|-----|
| **规则名** | `patPatPlayers` |
| **描述** | 右键其他真人玩家的头部摸摸头，显示爱心、播放轻响并使对方短暂蹲起。无需空手，不拦截原版交互。 |
| **类型** | `string` |
| **默认值** | `"false"` |
| **参考选项** | `false`, `true`, `sneak` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

`true` 随手可摸，`sneak` 须按住潜行。冷却从 10 tick 起，连点每次缩短 2 tick，最低 3 tick；停手 20 tick 后重置。交互距离遵循原版。

`/patnod off` 可拒绝被摸，偏好跨重启保存。被摸者听到的轻响来自发起者方向。

每次强制蹲下持续 3 tick，动作由其他玩家看见。自己按住潜行时保持蹲下；正在骑乘或载人时跳过蹲起，避免拆散骑乘塔，爱心与音效照常。

---

### whoCalledMe - 谁在叫我

聊天消息中出现其他玩家的名字时，被点名的玩家收到提醒：连响三声提示音（叮 叮 叮），并弹出 title 显示消息原文。

| 属性 | 值 |
|------|-----|
| **规则名** | `whoCalledMe` |
| **描述** | 聊天提到玩家名字时，用提示音和标题提醒对方，并高亮消息中的名字。支持只匹配 @提及。 |
| **类型** | `string` |
| **默认值** | `false` |
| **参考选项** | `false`, `true`, `mention` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

匹配不区分大小写。`true` 为子串匹配，长名字优先（`timy` 不会同时提醒 `Tim`）；`mention` 只匹配 `@名字`。自己提到自己也会提醒，假人不接收提醒。

**配套子规则**：`whoCalledMeSound` 默认 `ding`，可选 `false`、`true`（等同 `ding`）、`ding`、`levelup`、`bell`、`pling`、`hit`，每次提醒连响三声，间隔 6 tick。`whoCalledMeHighlight` 默认 `aqua`，支持 16 种原版颜色、`rainbow` 和 `false`；`false` 不改色，旧值 `none` 等未知值回落默认。

聊天与标题中的在线玩家名按规则色高亮，其余文字保留原样式；`/say` 等系统消息不处理。标题淡入 0.25 秒、停留 3 秒、淡出 0.5 秒；超长内容按 40 个显示宽度单位截断（中文算 2），保留首个命中名字。正版签名聊天可能出现“无法验证”标记，离线服无签名消息不受影响。

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

### playerHeadSkin - 玩家头部皮肤

真人与 Carpet 假人在头部槽位戴上玩家头颅后，采用头颅的全身皮肤；直接换头更新皮肤，摘下、换其他装备或关闭规则时恢复佩戴前皮肤（包括假人统一皮肤）。带头上线、重生或开启规则时同样生效。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerHeadSkin` |
| **描述** | 真人与假人戴玩家头颅时采用其全身皮肤，摘下或关闭规则后恢复原皮肤 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

- 优先使用头颅自带皮肤；只有名字或 UUID 时通过原版服务异步解析，等待或失败时保留原皮肤。无有效皮肤来源的玩家头颅与生物头不换肤。
- 仅改变皮肤，名字、UUID、游戏状态不变，头颅仍显示。纯服务端，不需要换肤模组；不依赖 `playerHat`，正常戴上玩家头颅即可。
- 真人自身刷新可能短暂加载；打开外部容器或鼠标拿着物品时，延后至关闭容器、放回物品后刷新自身，其他玩家即时可见。

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
| **描述** | 允许在哭泣的黑曜石上放置末地水晶，也可放置无敌水晶。不影响普通黑曜石和基岩。 |
| **类型** | `string` |
| **默认值** | `false` |
| **参考选项** | `false`, `true`, `invulnerable` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

原版客户端可用。哭泣的黑曜石上放出的水晶光束指向 `(0, 128, 0)`；无敌水晶保留底部板，可被活塞推动、创造模式可破坏。空间、碰撞与物品消耗仍遵循原版。

---

### textAnimation - 米塔字幕

/text 向在线玩家（假人除外；@a 全服或在线玩家名定向，目标必填）各自眼前逐字弹出对话文本，停留后整句坠落消散（米塔游戏的文字显示效果）。

| 属性 | 值 |
|------|-----|
| **规则名** | `textAnimation` |
| **描述** | 用 /text 在指定玩家眼前播放逐字弹出的米塔字幕，停留后坠落消散。原版客户端可用。 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `FEATURE`, `COMMAND` |

使用 `/text @a <文本>` 或 `/text <玩家名> <文本>`，目标必填，控制台与命令方块也可用。默认白字，支持 `&` 色码。前 12 名符合条件的接收者显示实体字幕，其余改为 actionbar 打字机。单条最多 128 字，全服最多 8 条并发；详见[命令与参数](commands.md#text---米塔字幕)。

---

### clickThrough - 穿透点击

右键墙告示牌/挂墙木牌/墙横幅/展示框/画直接打开背后贴挂的容器，潜行时保持原版交互。

| 属性 | 值 |
|------|-----|
| **规则名** | `clickThrough` |
| **描述** | 右键墙告示牌/挂墙木牌/墙横幅/展示框/画直接打开背后贴挂的容器，潜行时保持原版交互，背后无容器也走原版行为 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

潜行保留编辑告示牌、旋转展示框等交互；立式告示牌和横幅不处理，旁观者不能触发。容器仍遵循猫占、顶部被堵等原版限制。安装 ClickThrough Server（`clickthrough_server`）时停用本规则的处理，避免重复交互。

---

## 玩家缩放

### playerScale - 玩家随地大小变

用 `/scale set|reset|info` 调整体型。数值须为有限正值，并受 `playerScaleMin` 与 `playerScaleMax` 限制；视野补偿需客户端安装。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerScale` |
| **描述** | 用 /scale 调整玩家体型，范围由 playerScaleMin 和 playerScaleMax 控制。视野补偿需客户端安装。 |
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

| 属性 | 值 |
|------|-----|
| **规则名** | `playerScalePhysics` |
| **描述** | 玩家的移动、跳跃、交互距离和重力随体型变化，可为小体型保留最低能力。 |
| **类型** | `string` |
| **默认值** | `false` |
| **参考选项** | `false`, `true`, `safety`, `strict` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

| 模式 | 行为 |
|------|------|
| `false` | 关闭（默认） |
| `true` | 平缓：所有联动量按 √scale 曲线缩放，无保底 |
| `safety` | 平缓+保底（推荐）：同 true 曲线，小体型（scale<1.0）加保底，极端缩小仍可玩 |
| `strict` | 移动、交互距离与跳高等比变化，重力仍为 √scale，无保底 |

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

属性修改不写入存档；关闭规则或体型恢复 1.0 后移除。

- `safety` 仅在体型小于 1.0 时使用表中保底。
- 创造飞行覆盖竖直重力。鞘翅与烟花位移跟随移速倍率，撞墙伤害仍按原版存储速度计算，不随位移缩放。


### playerScaleLinkedEntities - 玩家大小变联动实体

玩家使用物品直接生成的生物实体继承玩家当前体型。需配合玩家随地大小变规则使用（体型来源也可以是 /attribute 设置的 minecraft:scale）。

| 属性 | 值 |
|------|-----|
| **规则名** | `playerScaleLinkedEntities` |
| **描述** | 玩家使用物品生成的生物继承其当时的体型。发射器、刷怪笼及非生物实体不受影响。 |
| **类型** | `boolean` |
| **默认值** | `false` |
| **参考选项** | `false`, `true` |
| **分类** | `PRIMARYUAN`, `SURVIVAL`, `FEATURE` |

体型取生成时的快照，之后不随玩家变化，幼崽等原有修饰符继续叠加。玩家体型为 1.0 时不改写生物的自定义体型。

**覆盖范围**：

- 盔甲架、刷怪蛋生物与玩家搭建的傀儡继承体型。
- 投掷物、掉落物、展示框、船、矿车、TNT、末地水晶和画不受影响。
- 发射器、刷怪笼等方块装置不触发。
