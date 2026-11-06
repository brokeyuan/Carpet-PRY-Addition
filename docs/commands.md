# 命令文档

> **Mod ID**: carpet-pry-addition  
> **版本**: 1.3.1

---

## 快速导航

- [假人珍珠传送命令](#假人珍珠传送命令)
  - [/tpp](#tpp---假人珍珠传送)
  - [/tppset](#tppset---站点管理)
- [玩家回放命令](#玩家回放命令)
  - [/playereplay](#playereplay---录制与幻影回放)
- [假人脚本命令](#假人脚本命令)
  - [/playerscript](#playerscript---录制与假人执行)
- [/hat - 玩家帽子](#hat---玩家帽子)
- [假人持续清空背包](#假人持续清空背包)
- [/player sendto - 假人背包链接](#player-sendto---假人背包链接)
- [/player brain - 假人脑子](#player-brain---假人脑子)
- [/fakeclone - 同UUID假人分身](#fakeclone---同uuid假人分身)
- [玩家随地大小变](#玩家随地大小变)
  - [/scale](#scale---玩家大小调节)
- [骑乘权限命令](#骑乘权限命令)
  - [/riding](#riding---骑乘权限管理)
  - [/picking](#picking---捡起权限管理)
- [/pvp - 和平的玩家](#pvp---和平的玩家)
- [/patnod - 被摸互动开关](#patnod---被摸互动开关)
- [/text - 米塔字幕](#text---米塔字幕)
- [/countdown - 通用倒计时](#countdown---通用倒计时)
- [跨服传送命令](#跨服传送命令)
  - [/tpto](#tpto---跨服传送)
  - [/tptoset](#tptoset---服务器管理)
- [/fakegame - 假游戏](#fakegame---假游戏)
- [/timer - 计时器](#timer---计时器)
- [/countdown - 旧版倒计时（兼容入口）](#countdown---旧版倒计时兼容入口)
- [/chatpunish - 聊天惩罚](#chatpunish---聊天惩罚)

---

## 假人珍珠传送命令

### /tpp - 假人珍珠传送

> **所属规则**：`fakePlayerTpp`

#### 语法

```
/tpp
/tpp <station>
```

无参数时列出全部可用站点（显示名优先）。

站点补全按内部名或显示名前缀匹配，忽略大小写，填入显示名（无显示名则填内部名）。例如 `/tppset set qwp 全物品` 后，输入 `/tpp q`、`/tpp qwp` 或 `/tpp 全` 均建议 `全物品`；缩写取自内部名，不自动生成拼音。`/tppset spawn`、`remove` 和 `rule use` 的站点参数使用相同补全。

#### 权限

需要启用 `fakePlayerTpp` 规则。

#### 功能描述

传送到指定站点（通过假人中转）。

#### 参数说明

| 参数 | 类型 | 说明 |
|------|------|------|
| `station` | 字符串 | 目标传送站点名称（支持内部名或显示名） |

#### 工作流程

1. 构建假人名：别名（如有）或玩家名 + `_` + 站点名，总长不超过 16 字符——玩家名按剩余空间动态截断（别名最多 10 字符，站点名必须完整保留；站点过长时玩家名部分为空）
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

> **所属规则**：`fakePlayerTpp`

#### 权限

大部分子命令需要管理员权限。

#### 功能描述

管理 TPP 传送站点、玩家别名和规则配置。

#### 子命令

##### `/tppset spawn <station>`

在当前位置设置该站点的假人生成点，立即生成假人并在 3 秒后自动下线。

- **权限**: 需要启用 `fakePlayerTpp` 规则
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
  - `alias` - 别名（最多 10 字符，不含空格，支持中文）

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

> **所属规则**：`playerHat`

### 语法

```
/hat
```

### 权限

- 规则 `playerHat` 开启时所有人可用（含管理员）
- 规则关闭时对所有人隐藏（无管理员豁免）

### 功能描述

交换主手和头部的整组物品，保留数量及组件。`/hat` 不受自动戴帽的物品排除限制。

开启 `playerHat` 后也可主手对空气右键整组换帽：副手须为空，工具和有使用功能的物品不触发，生存模式遵守绑定诅咒。头部已有正常头盔、海龟壳或不死图腾时右键不触发，命令不受此限制。数据包可用物品标签 `#carpet-pry-addition:hat_use_blacklist` 额外排除类型；外部脚本若没有可识别的使用信号，应加入此标签。背包头部槽的拖放容量仍为一个。

### 相关规则

**playerHat** - 手中未触发图腾保护时，头部图腾可抵挡致命伤害。消耗一个图腾，清除负面效果、恢复至 1 点生命，并获得：

- 再生 II（45 秒）
- 伤害吸收 II（5 秒）
- 抗火 I（40 秒）

**playerHeadSkin** - 同时开启时，手持玩家头颅执行 `/hat` 可采用其全身皮肤，摘下或关闭规则恢复原皮肤。本规则不依赖 `playerHat`，正常戴上玩家头颅即可；无有效皮肤的头颅与生物头不触发。

### 使用示例

```bash
# 手持钻石块，将其戴在头上
/hat
```

---

## 假人持续清空背包

> **所属规则**：`fakePlayerDropAll`

### 命令语法

通过 Mixin 在 Carpet 自带的 `/player <name>` 命令树下追加独立的 `dropall` 子命令，让假人按设定节奏持续丢出背包所有物品：

```
/player <name> dropall [once|continuous|interval <ticks>|after <ticks>|perTick <times>|randomly <min> <max>|stop]
```

`<modifier>` 共 7 个修饰词（表中首行为无修饰词的顶层形式）：

| 修饰参数 | 语法 | 行为 |
|---------|------|------|
| (无参数) | `dropall` | 立即丢一次（等价 `once`） |
| `once` | `dropall once` | 立即清空全部槽位 |
| `continuous` | `dropall continuous` | 每 tick 丢一组，空背包时等待新物品 |
| `interval` | `dropall interval <ticks>` | 每隔 `<ticks>` tick 丢一组 |
| `after` | `dropall after <ticks>` | 在 `<ticks>` tick 后丢一组（一次性） |
| `perTick` | `dropall perTick <times>` | 每秒（20 tick）丢 `<times>` 组 |
| `randomly` | `dropall randomly <min> <max>` | 在 `[min, max]` tick 区间随机取值作为本次间隔，每次重新随机 |
| `stop` | `dropall stop` | 停止持续丢出任务 |

### 权限

沿用 Carpet `/player` 命令本身的权限检查（由 Carpet 的 `commandPlayer` 规则控制），不额外限制。

### 相关规则

- **fakePlayerDropAll** — 控制整个 `dropall` 命令的可见性。
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

# 假人每 tick 丢一组物品（背包清空后保持等待，可随时 stop）
/player Steve dropall continuous

# 每 10 tick 丢一组
/player Steve dropall interval 10

# 20 tick 后丢一组（一次性）
/player Steve dropall after 20

# 每秒丢 4 组
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
- `after` 模式：成功丢出一组后自动结束；若到时机时背包为空，任务保持每 tick 检查，直到有物品可丢出为止。
- 目标假人下线：所有相关任务自动清理，避免 tick 监听泄漏。
- 同一假人已有进行中的 dropall 任务时，再次触发会被拒绝并提示先 `stop`。

---

## /player sendto - 假人背包链接

> **所属规则**：`fakePlayerSendto`

通过 Mixin 在 Carpet 自带的 `/player <name>` 命令树下追加独立的 `sendto` 子命令，建立假人之间**单向**的背包物品流链接：源假人持续把背包物品转移给目标。

### 权限

沿用 Carpet `/player` 命令本身的权限检查（由 Carpet 的 `commandPlayer` 规则控制），不额外限制。

### 命令语法

- `/player <src> sendto <target>`：建立链接并立即开始转移
- `/player <src> sendto once|continuous|interval <ticks>|after <ticks>|perTick <times>|randomly <min> <max>`：调整转移频率
- `/player <src> sendto stop`：停止并移除全部链接

### 转移行为

- 每次转移一组物品，默认每 tick 一组
- 多目标时逐组轮流分配
- 目标背包放不下时，物品留在源背包（不丢不弹）
- 链接仅存内存：假人下线或服务器重启后失效

### 使用示例

```bash
# 建立链接并立即开始转移
/player Steve sendto Alex

# 每 20 tick 转移一组
/player Steve sendto interval 20

# 停止并移除全部链接
/player Steve sendto stop
```

## /player brain - 假人脑子

### 命令语法

通过 Mixin 在 Carpet 自带的 `/player <name>` 命令树下追加独立的 `brain` 子命令，为假人挂载/卸载生物式 AI：

```text
/player <name> brain [zombie|babyzombie|skeleton|witherskeleton|drowned|zombiepiglin|pillager|vindicator|irongolem|spider|piglin|piglinbrute|slime|magmacube|fish|enderman|wolf|villager|pig|allay|fox|witch|coppergolem|off] [keep]
/player <name> brain team [<队名>|off]
```

模式名跟随 `/carpet language`：语言为中文（zh_cn/zh_tw）时补全与输入用中文（如 `brain 僵尸`），英文键恒可用；两种写法均可加 `keep`。

| 参数 | 行为 |
|------|------|
| (无参数) | 查询当前 AI 模式与诊断信息（目标、寻路、移动状态） |
| `<模式> keep` | 本次服务器运行内，假人重上时恢复模式及主人（狼的仇恨对象、悦灵的交付对象）；重启或 `brain off` 清除 |
| `zombie` | 近战追击玩家。 |
| `skeleton` | 持弓远程射击并走位，消耗背包箭矢；白天露天着火时找暗处躲避。 |
| `pillager` | 持弩远程射击，上弦 25 tick。 |
| `irongolem` | 攻击敌对生物与敌对假人，不主动攻击苦力怕；被攻击时反击。 |
| `spider` | 白天中立，夜间敌对并疾跑追击，贴近目标扑跳。 |
| `wolf` | 跟随主人并协助战斗（受伤先逃跑），见手持食物的玩家歪头注视。 |
| `villager` | 日程驱动的过日子：回床入睡、认领堆肥桶后长期保留农民岗位（[村民-农民]；集合/睡眠/恐慌不释放）、钟下集合；工作时段收割成熟作物、真实种子补种（每种保留 10 个投入堆肥、满级先取骨粉）。 |
| `enderman` | 被凝视后激怒，疾跑追击（激怒半径 64 格）。 |
| `babyzombie` | 比普通僵尸更快的近战追击。 |
| `witherskeleton` | 近战追击玩家、铁傀儡和猪灵类，躲避狼；攻击不附凋零。 |
| `drowned` | 持三叉戟时远程投掷，否则近战；被攻击时唤醒附近溺尸和僵尸猪灵脑假人。 |
| `zombiepiglin` | 被攻击后反击，并唤醒附近同类。 |
| `vindicator` | 近战追击玩家、村民和铁傀儡。 |
| `piglinbrute` | 始终敌对，近战攻击，无视金装。 |
| `slime` | 持续跳行，锁定目标后直线跳行追击。 |
| `magmacube` | 与史莱姆相同，以跳行移动和追击。 |
| `fish` | 水中游动，躲避 8 格内玩家；受伤逃窜，缺氧上浮，离水扑腾。 |
| `pig` | 闲时漫步，被攻击时逃跑，见手持胡萝卜的玩家会跟上来。 |
| `piglin` | 敌视未穿金装的玩家，按武器近战、用弩或长矛；捡拾并穿戴装备。 |
| `allay` | 以副手物品为样品（按物品匹配、药水按内容），步行收集匹配掉落物并交付给执行命令的玩家；控制台挂脑无默认对象，只对有效音符盒交付。音符盒监听随脑会话，响起后改向音符盒交付 30 秒，交付冷却 240t。 |
| `fox` | 采摘成熟甜浆果丛、玩家真正饥饿时吃 FOOD 食物、捕食鸡/兔、躲避狼；白天减少活动（躺卧姿态玩家身体无对应，不伪造）。 |
| `witch` | 只用背包里的真实药水：低血/着火/水下喝对应饮用型药水；对目标按原版 60t 节拍、10 格射程投喷溅药水，出手前复核目标/同队/视线，缺药不投。 |
| `coppergolem` | 从铜箱取货（单趟 ≤16，按实际库存差量记账），分类放入空箱或已有同物的箱子；暂停保留余货并恢复寻点，双箱同一容器身份；仅 1.21.10+。 |
| `off` | 卸载脑子，恢复 Carpet 手动控制（战斗队伍保留） |

**战斗队伍**（与脑子独立，未挂脑也可设置）：

| 参数 | 行为 |
|------|------|
| `team` | 查询战斗队伍 |
| `team <队名>` | 加入或更换队伍（队名支持中英文，Tab 补全已有队名） |
| `team off` | 退出队伍 |

同一非空队名的假人互为友军（跨脑模式生效）：不互相索敌、不反击、不参与群体围攻、凝视不触发激怒，出手前也不命中；战斗中改为同队立即撤销彼此的非法目标。异队或无队假人按各模式原有敌对规则交战；被动模式（猪/鱼/村民）不受队伍影响。真人不获得队伍身份；难度、创造/旁观、主人豁免等原有门禁不受队伍影响。**边界**：友军保护在"出手瞬间"判定——已离手的远程弹射物、扫击横扫与已举矛的长矛刺击（≤1 tick 结算窗口）飞行途中不追踪改队，原版弹道不拦截。队伍为运行期数据：换脑、`brain off`、下线重上均保留；服务器重启清空。

### 权限

沿用 Carpet `/player` 命令本身的权限检查（由 Carpet 的 `commandPlayer` 规则控制），不额外限制。狼模式的"主人"是执行命令的真人玩家（控制台执行无主人，狼模式挂载失败并提示）。

### 相关规则

- **fakePlayerBrain** — 控制整个 `brain` 命令的可见性。
  - 规则关闭时：整个 `brain` 命令不可见（tab 补全不到、无法执行），已挂载的脑子会在下一 tick 自动卸载。
  - 规则切换立即生效：通过 Carpet `RuleObserver` 在规则变更时重新下发命令树，玩家无需重新登录。

### 使用示例

```bash
# 查询假人当前 AI 模式
/player Steve brain

# 挂载僵尸模式（给假人一把剑效果更好）
/player Steve brain zombie

# 挂载骷髅模式（记得给假人弓和箭）
/player Steve brain skeleton

# 两个僵尸组队后互不攻击
/player ZombieA brain zombie
/player ZombieB brain zombie
/player ZombieA brain team red
/player ZombieB brain team red

# 卸载脑子，恢复 Carpet 手动控制
/player Steve brain off

# 查询 / 退出战斗队伍（brain off 后队伍仍保留）
/player Steve brain team
/player Steve brain team off
```

### 自动卸载条件

- 执行 `/player <name> brain off`
- 切换为其他模式（先卸旧脑再挂新脑）
- 关闭 `fakePlayerBrain` 规则（下一 tick 自动卸载）
- 假人死亡、下线或被 `/player <name> kill`

卸载后假人恢复静止木桩状态，无任何残留实体或状态（模块不生成任何额外实体）。

---

## /fakeclone - 同UUID假人分身

`fakePlayerClones` 规则开启时可用（等级 2+）。多个真正的 Carpet 假人共享同一 UUID 与皮肤同时在线，独立位置/Entity ID，整组共享背包，真实参与区块加载。

### 命令语法

```
/fakeclone list                          列出全部分身组
/fakeclone status <name>                 单组详情（cloneId/EntityId/位置/维度）
/fakeclone spawn <name>                  为该身份再生成一个分身（等价 /player <name> spawn）
/fakeclone kill <name>                   全组正常退出（非死亡、不掉落），并存档组快照
/fakeclone kill <name> <cloneId>         指定单个分身正常退出，组与共享库存继续运行
/fakeclone rejoin <name>                 按快照恢复整组数量/维度/位置/背包
/fakeclone select <name> <cloneId>       定位指定分身（回显 EntityId 与坐标）
/fakeclone action <action>               对全部分身执行动作（按 cloneId 顺序结算）
/fakeclone action <action> <name>        对该组全部分身执行动作
/fakeclone action <action> <name> <id>   对指定分身执行动作
```

可用动作：`stop` `drop` `dropstack` `swaphands` `jump` `use` `attack` `sneak` `unsneak` `slot1`~`slot9`（复用 Carpet ActionPack；`slotN` 对应快捷栏第 N 格，动作状态与选中槽按分身独立维护，无全局选中态）

### 说明

- `/player <name> spawn` 对已建组的身份每执行一次新增一个分身，不再提示"已在线"；普通假人不受影响。
- 分身退出前把每成员维度/坐标/朝向/游戏模式写入 `<存档>/playerdata/<name>-clones.json`；共享背包走主实例原版 playerdata（含全部数据组件），重启后 `rejoin` 仍可恢复。
- 同 UUID 真人在线期间 `spawn`/`rejoin` 被拒绝；仍有在线/退出中成员时 `carpet fakePlayerClones false` 被拒绝，先整组退出。
- `rejoin` 前校验快照与全部维度；在线/退出中的组拒绝重复恢复，任一成员创建失败只撤销本次新增，不留半组。
- `give`/`clear`/`item`/`enchant`/`loot` 对同组按账号只执行一次；`tp`/`execute`/`effect` 等身体命令仍按实体逐个执行。
- `#cloneId` 组内从 1 递增、不回收复用；快照保存编号，重启恢复后 `cloneId` 与显示 UUID 不变。

---

## 玩家随地大小变

### /scale - 玩家大小调节

> **所属规则**：`playerScale`（范围边界：`playerScaleMin` / `playerScaleMax`）

#### 命令结构（统一三层子命令：先操作，后数值/玩家）

```
scale
  set
    <value>                     # 给自己设置（受 playerScaleMin/Max 限制）
    <value> <player>            # 给指定玩家设置（OP / everyone 模式；self 模式不可用）
  reset
    (无参数)                    # 自己恢复 1.0
    <player>                    # 恢复指定玩家（OP / everyone 模式；self 模式不可用）
  info
    (无参数)                    # 查看自己当前大小 + 允许范围
    <player>                    # 查看指定玩家当前大小（self 模式不可用）
```

#### 语法

```
/scale set <value>                 # 玩家调节自己大小（受范围限制）
/scale set <value> <player>        # 给指定玩家设置大小（权限：OP / everyone；self 模式拒绝）
/scale reset                       # 自己恢复 1.0
/scale reset <player>              # 恢复指定玩家大小（权限：OP / everyone；self 模式拒绝）
/scale info                        # 查看自己当前大小 + 允许范围 + 当前模式
/scale info <player>               # 查看指定玩家当前大小（self 模式拒绝）
```

#### 权限（四档规则）

| 规则值 | 行为 |
|--------|------|
| `false` | 整个 `/scale` 命令不可见 |
| `self`  | 所有人（无论 OP）只能 `set/reset/info` 自己。tab 补全仅显示自己名字 |
| `true` | 玩家可 `set/reset` 自己，OP 可修改他人；所有人可查询他人。非 OP 的修改目标补全仅显示自己，OP 显示全部在线玩家 |
| `everyone` | 所有人都可以 `set/reset/info` 任意在线玩家；tab 补全显示所有在线玩家 |

- 规则切换时通过 Carpet `RuleObserver` 立即刷新命令树，无需玩家重新登录
- `info` 查询的可见性比 modify 宽松：`true` 模式下非 OP 也可以 `info` 别人（info 不改变状态）；`self` 模式下所有人只能 `info` 自己；`set/reset` 仍需权限

#### 范围控制

- 硬边界：value 必须为大于 0 的有限值，对所有路径统一生效
- `playerScaleMin`（默认 0.1）：所有玩家允许设置的最小值
- `playerScaleMax`（默认 1.5）：所有玩家允许设置的最大值
- 软边界约束所有玩家（含管理员调自己与调他人）；管理员需要更大范围时，通过 `/carpet playerScaleMin` / `/carpet playerScaleMax` 调整边界本身

#### 功能描述

为 `Player` 注册 `minecraft:scale` 属性，通过统一的三层子命令 `/scale set|reset|info` 管理。支持四种模式切换（false / self / true / everyone）。self 模式下所有人（无论 OP）都只能调整自己；true 模式下管理员可调任意玩家；everyone 模式下所有人可互相调节。范围与权限分离：tab 补全根据当前身份过滤可见的玩家，相同的范围限制适用于所有人。

> **版本说明**：`minecraft:scale` 属性自 Minecraft 1.20.5（快照 23w51a）起由原版提供，本模组支持的全部版本（1.21~1.21.4、1.21.5+）均可使用，无版本限制。
>
> **搭配建议**：配合 `playerScalePhysics` 规则（`/carpet playerScalePhysics true`）可使速度、跳跃、台阶高度、交互距离、摔落安全距离等物理特性随体型缩放，并补偿视野变化，更真实。

#### Tab 补全行为

| 命令位置 | `self`（任何人） | `true` 模式非 OP | `true` 模式 OP | `everyone` |
|----------|------------------|------------------|---------------|------------|
| `set <value>` 之后补全 `<player>` | 只补全自己 | 只补全自己 | 所有在线玩家 | 所有在线玩家 |
| `reset` 之后补全 `<player>` | 只补全自己 | 只补全自己 | 所有在线玩家 | 所有在线玩家 |
| `info` 之后补全 `<player>` | 只补全自己 | 所有在线玩家 | 所有在线玩家 | 所有在线玩家 |

#### 使用示例

```bash
# 启用规则（管理员）
/carpet playerScale self     # 所有人只能调自己（无论 OP）
/carpet playerScale true     # 玩家调自己，OP 调任意玩家
/carpet playerScale everyone # 所有人可调任意玩家

# 自己变半
/scale set 0.5

# 自己恢复默认
/scale reset

# 查看当前大小与允许范围
/scale info

# 管理员/everyone 模式下调节他人（self 模式不可用）
/scale set 1.5 Steve
/scale reset Steve

# 查看别人当前大小（self 模式不可用）
/scale info Steve
```

#### 提示消息（节选）

- 设置成功（自己）：`你的大小已设为 0.5x`
- 设置成功（他人）：`已将 Steve 的大小设为 2.0x`
- 超出范围：`值 0.05 超出允许范围（0.1 ~ 1.5）`
- 权限不足：`你没有权限调整其他玩家的大小（当前模式仅允许调整自己）`
- 被修改的提示：`管理员 Brokey 将你的大小调整为 2.0x` 或 `玩家 Alice 将你的大小调整为 0.5x`
- `/scale info` 输出示例（self 模式）：
```
你的当前大小：0.5x（默认 1.0x）
允许设置范围：0.1 ~ 1.5
当前模式：self（所有人都只能调自己）
```

---

## 骑乘权限命令

### /riding - 骑乘权限管理

> **所属规则**：`ridingPlayers`

#### 语法

```
/riding        # 翻转：允许 ↔ 禁止其他玩家骑乘你
/riding on     # 允许其他玩家骑乘你
/riding off    # 禁止其他玩家骑乘你
```

#### 权限

- 规则 `ridingPlayers` 开启时所有人可用（含管理员）
- 规则关闭时对所有人隐藏（无管理员豁免）

#### 功能描述

设置是否允许其他玩家骑乘你。你设置为 `on` 后，其他玩家**主手持不死图腾**右键你的**头部**即可骑乘上来（点躯干/腿脚不触发骑乘；假人不参与）。

#### 交互条件

- 骑乘者（上面的人）：主手持**不死图腾**，右键点击你的**头部**
- 被骑乘者（下面的人）：需执行 `/riding on` 允许
- 堆叠上限由 `ridingPlayersStackLimit` 规则控制（默认 16）
- 当 `ridingPlayersAutoDismount` 启用时，游戏模式变更会自动让乘客下车
- 当 `ridingPlayersClientInteract` 启用时（默认），骑乘状态下仍可与方块/实体交互（需客户端安装）

#### 使用示例

```bash
# 允许其他玩家骑乘你
/riding on

# 禁止其他玩家骑乘你
/riding off
```

---

### /picking - 捡起权限管理

> **所属规则**：`pickupPlayers`

#### 语法

```
/picking        # 翻转：允许 ↔ 禁止其他玩家捡起你
/picking on     # 允许其他玩家捡起你
/picking off    # 禁止其他玩家捡起你
```

#### 权限

- 规则 `pickupPlayers` 开启时所有人可用（含管理员）
- 规则关闭时对所有人隐藏（无管理员豁免）

#### 功能描述

设置是否允许其他玩家捡起你（让你骑到他们头上）。你设置为 `on` 后，其他玩家**主手持不死图腾**右键你的**腿脚**即可将你捡起（点头部为骑乘，点躯干不触发；假人不参与）。

#### 交互条件

- 捡起者（下面的人）：主手持**不死图腾**，右键点击你的**腿脚**
- 被捡起者（上面的人）：需执行 `/picking on` 允许
- 堆叠上限由 `ridingPlayersStackLimit` 规则控制（默认 16），与骑乘共用

#### 使用示例

```bash
# 允许其他玩家捡起你
/picking on

# 禁止其他玩家捡起你
/picking off
```

---

## /pvp - 和平的玩家

> **所属规则**：`peacefulPlayers`

按玩家开关 PVP：被关闭的玩家不能攻击玩家，也不会受到任何玩家造成的伤害（自伤不受限制）。被拦截的攻击会在受击方位置播放提示音（双方均可听到），保留以攻击提醒对方的信号作用。

### 命令语法

- `/pvp`：切换自己的 PVP（回执即新状态）
- `/pvp list`：列出所有 PVP 关闭的玩家（含离线已登记玩家）
- `/pvp on|off`：开关自己的 PVP
- `/pvp on|off <玩家>`：开关指定玩家
- `/pvp on|off @a`：全服总开关（新加入玩家跟随全局）

### 权限模式（peacefulPlayers 规则取值）

- `false`：隐藏 /pvp 命令
- `self`：所有人都只能调自己（无论 OP）
- `true`：玩家仅可调自己，管理员可调任意玩家
- `everyone`：所有人可调任意玩家
- `@a` 全局开关仅管理员可用（self 模式除外）

### 全服总开关行为

- 全局关闭期间，所有个人开关操作均被锁定（含管理员），仅 `/pvp on @a` 可解除
- 解除时强制覆盖，所有玩家恢复 PVP 开启
- 玩家 PVP 状态跨服务器重启保留

---

## /patnod - 被摸互动开关

> **所属规则**：`patPatPlayers`

按玩家开关"接不接受被摸"的个人偏好：不接受时其他玩家无法摸你（摸头对其完全不生效）。状态按玩家名持久化于 `<存档>/carpet-pry-addition/patnod.json`（随存档走，旧版 config 路径自动迁移），跨重启保留。

### 命令语法

- `/patnod`：切换自己接不接受被摸（回执即新状态）
- `/patnod on`：接受被摸
- `/patnod off`：不接受被摸

### 权限

- 仅服务器玩家可用（控制台无自身状态）
- `patPatPlayers` 规则关闭时整棵命令树隐藏

### 相关规则

- `patPatPlayers`：摸摸头主规则

### 使用示例

```
/patnod          # 翻转：接受 ↔ 不接受（回执即新状态）
/patnod off      # 拒绝后，其他玩家无法摸你
/patnod on       # 重新接受
```

---

## /text - 米塔字幕

> **所属规则**：`textAnimation`

向在线玩家（假人除外）各自眼前逐字弹出对话文本，停留后整句坠落消散（米塔游戏的文字显示效果）。每字一个原版文本展示实体，播完即删；长文本按标点自动分组接续播放。控制台/命令方块同样可用。**目标参数必填**：`@a` 全服或在线玩家名定向。

### 命令语法

- `/text @a <文本内容>[|options]`：全服广播
- `/text <玩家名> <文本内容>[|options]`：定向单个在线玩家（按当前在线表不区分大小写匹配；仅单目标，多目标请分条发送）
- 目标参数必填：首词既非 `@a` 也非在线玩家名时报错（含以 `@` 开头的其它写法）

文本内字面 `|` 写 `||`；色码用 `&`（如 `&c`），字面 `&` 写 `&&`，色码按原版 § 语义重置样式。

### 权限

- 所有玩家可用（控制台/命令方块同样可用）
- `textAnimation` 规则关闭时整棵命令树隐藏
- 每人 10 秒发送冷却（控制台不受限），冷却命中提示剩余秒数

### options 参数

| 键 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `distance` | 小数 | `2.5` | 沿视线方向的偏移（格），生成高度为脚部 +1.3 格 |
| `scale` | 小数 | `2.2` | 字符缩放（弹出终态） |
| `spacing` | 小数 | 自动（0.15×scale） | 字间距单位（格/宽度单位） |
| `hold` | 整数 | `80` | 打字完成后坠落前的停留（tick） |
| `glow` | true/false | `false` | 字符发光描边 |
| `sound` | true/false | `true` | 逐字点击音效 |
| `drop` | true/false | `true` | 停留后是否坠落（false = 原地渐隐） |

### 行为细节

- 逐字弹出：2 tick 一字，每字随机歪斜 ±45°、1.8 倍缩放收拢、随机 y 抖动，伴随点击声（音量 1.0 / pitch 1.2）
- **感叹号整句增益（无封顶）**：结尾连续 `!`/`！` 越多整句越大、生成点越远——最终字号 = 基础字号 × (1 + 0.3 × 数量)，距离 = 基础距离 × (1 + 0.15 × 数量)；末尾空白不打断，被其他字符打断则不计
- 默认白色（#FFFFFF），`&` 色码换色
- 坠落：重力 0.03/tick²、空气阻力 0.99、落地一次 0.28 反弹后静止渐隐、起落随机翻滚；坠落 24 tick 后按 -8 透明度/tick 渐隐
- 分组：≤25 字/组，断点向后 10 字内找标点；非末组追加 " - "；上一组开始坠落时下一组接续，组间随机偏航 ±22.5° 与随机抬高
- 生成点：各在线玩家脚部 + 视线方向 × distance，高度脚部 +1.3（每名玩家独立一份，以各自位置朝向为基准）；字符平面竖直固定（不随俯仰倾斜），位置与朝向出现时定死，之后不随执行者移动或转头变化
- 接收人数：前 12 名符合条件的接收者显示实体字幕，其余使用 actionbar 打字机。
- 护栏：单句 ≤128 字、全服并发 ≤8 条广播；无在线真人时报"没有可接收字幕的在线玩家"，区块未加载的玩家被跳过；定向后只对该玩家播放（生成点仍以其位置朝向为基准）

### 使用示例

```
/text @a 全体看过来                         # 全服广播
/text @a 完了!!!                           # 感叹号增益，整句放大
/text @a &c红色&e黄色&b青色                 # & 色码
/text @a 你好|scale=3;hold=60              # 行内调参
/text @a 静止文本|drop=false                # 原地渐隐，不坠落
/text @a 发光字幕|glow=true;sound=false      # 发光、无声
/text brokeyuan 私发提醒                     # 定向单个玩家
```

---

## /timer - 计时器

> **所属规则**：`countdown`（默认开启）

正计时与倒计时的统一入口：全局同一时刻只有一个活动计时，两种方向共用暂停/恢复/停止控制；四种展示位置与三档效果自由搭配，常用场景一条短命令完成。计时基于真实经过时间（TPS 低不拉长时长），纯服务端数据包实现，原版客户端可用。

### 权限

- 权限等级 2：管理员与命令方块可用，普通玩家不可见
- `countdown` 规则关闭时不隐藏命令，执行时提示"功能已关闭"

### 命令语法

- `/timer down <时间> [选项…] [-- 名称]`：启动倒计时（1 秒 ~ 24 小时）
- `/timer up [选项…] [-- 名称]`：启动正计时（从零开始，无上限，不自动结束）
- `/timer pause` / `resume` / `stop` / `status`：暂停、恢复、停止、查看状态
- `/timer time <时间>`：直接设置剩余时间（仅倒计时）
- `/timer add <时间>` / `remove <时间>`：加时 / 减时（仅倒计时，减至归零视为完成）
- `/timer style <simple|normal|fancy>`：修改效果档位
- `/timer display <bar|center|actionbar|both>`：修改展示位置
- `/timer sound <on|off>`：开关本会话音效
- `/timer label <名称>`：修改名称
- `/timer players <选择器>`：修改显示对象
- `/timer`：有计时显示简短状态；无计时显示用法示例与上次结果

### 选项与默认值

选项词顺序自由、大小写不敏感，每类至多一个；`--` 后整段为名称（1–128 字符），不再解析选项。

| 类别 | 可输入值 | 说明 |
|------|------|------|
| 效果 | `simple` / `normal` / `fancy` | 默认 simple |
| 位置 | `bar` / `center` / `actionbar` / `both` | 默认 down=bar、up=actionbar；fancy 未显式选位置时用 both |
| 静音 | `silent` | 禁用本次计时全部声音 |
| 目标 | `@s`、`@a`、`@a[team=red]` 等原版选择器 | down 默认动态全服；up 玩家执行默认本人，控制台/命令方块默认全服 |

最常用：`/timer down 60s`、`/timer up`、`/timer down 5m fancy -- 活动开始`、`/timer down 5m fancy bar silent -- 安静活动`。

### 展示位置

| 位置 | 常态 | 事件 |
|------|------|------|
| `bar` | 顶部进度条"名称 · 剩余/已用 时间"，down 有进度、up 恒满条 | 全部事件只在顶部更新 |
| `center` | 中央大字时间 + 副标题名称，全程显示；倒计时最后 10 秒改单秒数 | 暂停/继续在副标题标记 |
| `actionbar` | 动作栏"名称 · 剩余/已用 时间" | 状态与结果只在动作栏 |
| `both` | 顶部全程显示，中央平时不占屏 | 开始/暂停/恢复各 2 秒、结束 3 秒中央提示；倒计时最后 5 秒（fancy 10 秒）中央倒数 |

### 三档效果

| 内容 | simple | normal | fancy |
|------|--------|--------|-------|
| 配色 | 白字白条 | 青条，最后 10 秒黄、3 秒红 | 青条，60 秒内金、10 秒内黄、3 秒内红 |
| 开始 | 直接出现 | 短淡入 | 开场过渡与钟声 |
| 节点 | 无 | 无 | 60/30 秒各提示一次 |
| 最后倒数 | both 最后 5 秒无声 | both 最后 5 秒，3/2/1 短音 | both 最后 10 秒，音高递增 |
| 结束 | 静态结果无声 | 绿色结果与短音 | 绿色结果与更强结束音 |

simple 默认无声，`/timer sound on` 可为当前会话显式开启；up 正计时不播放倒数音，只有开始/停止音。`silent` 与 `sound off` 始终覆盖全部声音。

### 目标玩家

- down 省略或独立 `@a`：动态全服（新加入玩家自动获得显示）；up 玩家执行省略时默认只给本人，减少无意打扰
- 其余选择器执行时快照一次，退出玩家移除显示、重新上线自动恢复；Carpet 假人一律排除，无匹配真人报错
- 选择器以 `@` 开头整体识别，括号与引号内的空格不会被拆散；裸玩家名不作为目标词（用 `-- 名称` 区分名称）

### 行为细节

- 已有计时再次 up/down 明确拒绝；结果展示 3 秒期间可直接启动新计时替换旧展示
- 暂停冻结真实时间，所有位置显示"已暂停"；恢复后从冻结点继续
- up 不允许 time/add/remove（报"正计时不支持修改已用时间"）；down 减至归零视为完成
- down 自然归零：显示"名称 · 计时结束" 3 秒并播一次结束音；`/timer stop` 手动取消只给操作者反馈"已停止，剩余 xx"，不播完成音、不冒充自然结束；up stop 结算最终用时（毫秒）并展示 3 秒
- 时间跳改（time/add/remove）不补发被跳过的数字、节点或声音；新加入玩家只补当前帧，不重播开场
- 最后一份结束结果保存在内存，`/timer` 或 `/timer status` 可查看；重启清除
- 与 /text、whoCalledMe 共存安全：只移除自己的显示，不主动清除其他功能的 Title/ActionBar；同一位置的其他通知可能临时覆盖计时显示

### 使用示例

```
/timer down 60s                              # 全服倒计时（顶部进度条）
/timer up                                    # 自己可见的正计时（动作栏）
/timer down 5m fancy -- 活动开始              # 华丽效果，中央+顶部
/timer down 5m fancy bar silent -- 安静活动   # 华丽视觉、仅顶部、无声
/timer up @a[team=red] -- 红队测试            # 定向正计时
/timer pause / resume / stop / status        # 运行控制
/timer add 30s / remove 10s / time 60s       # 倒计时调整
/timer style fancy / display both            # 运行中换效果与位置
/timer sound off / label 决赛 / players @a   # 声音、名称与对象
```

---

## /countdown - 旧版倒计时（兼容入口）

> **所属规则**：`countdown`（默认开启）

旧语法保留，与新 /timer 共用同一管理器与活动会话（互相查看与控制同一计时）。新用法请优先使用 /timer。

### 语法

- `/countdown start <时间> [显示组合] [目标]`：启动倒计时；省略显示组合时默认 `bossbar+title`、最后 10 秒中央窗口，基础效果映射 normal
- `/countdown pause` / `resume` / `stop` / `status`：暂停、恢复、立即取消（无完成提示）、状态
- `/countdown add <时间>` / `remove <时间>` / `set time <时间>`：调整剩余时间（对正计时会话同样拒绝）
- `/countdown set display <bossbar+title+subtitle+actionbar 组合>`：按渠道组合渲染
- `/countdown set players <选择器>` / `set label <文本>`：对象与标题
- `/countdown set last <时间|all>`：中央大字窗口
- `/countdown set milestones <on|off>`：长程节拍表（30m/15m/10m/5m/1m/30s/15s 各一次，仍遵守已选显示渠道）
- `/countdown set sound <on|off>`：音效开关

时间格式、选择器与权限同 /timer（权限等级 2，规则关闭提示"功能已关闭"）。
## 跨服传送命令

### /tpto - 跨服传送

> **所属规则**：`serverTransfer`（默认关闭）

#### 语法

```
/tpto
/tpto <server>
```

无参数时列出全部可转移服务器（按添加顺序）；名称可点击传送，悬停显示目标地址与端口。

#### 权限

所有玩家可用（规则开启时）。

#### 参数说明

| 参数 | 类型 | 说明 |
|------|------|------|
| `server` | 文本 | 服务器英文标识或展示名；裸中文、含空格展示名直接可用，也可用成对引号包围整个名称 |

#### 行为

- 传送走原版 transfer 协议：服务端向执行玩家本人下发目标地址，客户端断开并直接连入目标服；无需代理端与客户端模组
- 命令永远只转移执行者本人，不能指定他人；控制台 / RCON 等非玩家来源不可执行
- 按标识或展示名定位（忽略大小写），未找到时失败并列出可用项
- **目标服必须在 `server.properties` 开启 `accepts-transfers=true`**，否则转移连接会被目标服以 `transfers_disabled` 踢出
- 补全按标识或展示名前缀匹配（忽略大小写），插入英文标识，悬停显示展示名

### /tptoset - 服务器管理

> **所属规则**：`serverTransfer`（默认关闭）

#### 语法

```
/tptoset
/tptoset addserver <地址:端口> <标识> [展示名]
/tptoset removeserver <服务器>
/tptoset reload
```

#### 权限

管理员（权限等级 4）。规则关闭时整棵命令不可见；命令方块不获得管理权限。

#### 子命令说明

| 子命令 | 说明 |
|--------|------|
| `/tptoset` | 展示已配置服务器（标识、展示名、地址:端口），每行 `[删除]` 可点击 |
| `/tptoset addserver <地址:端口> <标识> [展示名]` | 添加服务器（一服一地址）；`地址:端口` 裸写，支持域名、IPv4、`[IPv6]:端口`、localhost；`标识` 为唯一英文标识（字母/数字/`_-.+`）；展示名省略时用标识，可含中文与空格。标识或展示名与现有记录忽略大小写重名时拒绝；修改地址须先 removeserver 再 addserver |
| `/tptoset removeserver <服务器>` | 删除整条记录（按唯一标识或展示名） |
| `/tptoset reload` | 从盘重读配置（手改 tpto.json 后无需重启）；失败保留当前列表并暂停写入，修复后 reload 恢复 |

#### 配置文件

`config/carpet-pry-addition/tpto.json`（跟随服务器，不随存档迁移）。数据版本 3，一条记录对应一个地址和端口；v1（单 host）与 v2（多线路）首次加载自动扁平化：v2 取 defaultLine 指向的入口（不可用取首条有效入口），其余入口与 playerLines 不导入，首次回写前自动备份原文件为 `tpto.json.pre-v3-<时间戳>-<序号>.bak`。

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

手改文件须遵守：servers 必须存在且为对象；host 为字符串、port 为 1–65535 的整数；标识/展示名不得互相冲突（忽略大小写）；混入 lines/defaultLine/playerLines 或版本超前时拒绝加载并暂停写入，直到修复后 reload 成功。

#### 使用示例

```
/tpto                                         # 列出服务器，点击传送
/tpto sc                                      # 按标识传送（也可写展示名「生存服」）
/tptoset                                      # 查看已配置服务器
/tptoset addserver survival.example.com:25565 sc 生存服
/tpto sc                                      # 一服一地址，直接转移
/tptoset removeserver sc                      # 删除服务器
/tptoset reload                               # 手改 tpto.json 后重读
```

---

## /fakegame - 假游戏

规则 `fakeGames` 控制整棵命令可见性（关闭时不可见）。`/fakegame` 是主入口，`/fg` 是同一棵树的快捷别名。主面板提供游戏列表、当前游戏、排行榜、我的战绩和管理入口（管理仅管理员可见）；所有按钮绑定具体场次 ID，旧邀请或旧按钮会提示场次失效。

```
/fakegame                          # 主面板
/fakegame list                     # 全部游戏、人数、简介与可用状态
/fakegame info <游戏>              # 玩法、胜利条件、可调设置与依赖
/fakegame create <游戏> [预设]      # 创建准备场次（默认 standard 预设）
/fakegame join [场次]               # 加入等待中的场次
/fakegame start [场次]              # 发起者/管理员点击开始，进入 3 秒倒计时
/fakegame leave [场次]              # 退出本局（随时可用）
/fakegame cancel [场次]             # 结束并清理本局（发起者/管理员）
/fakegame status [场次]             # 准备/进行面板、成员、检查与设置
/fakegame invite [场次]             # 向附近玩家重新发送带场次 ID 的邀请
/fakegame set <场次> <键> <值>      # 准备阶段修改本局设置
/fakegame guess <词>                # 你演我猜：提交猜测
/fakegame goal                      # 123木头人：发起者/管理员在脚下设终点
/fakegame top [游戏]                # 排行榜（按游戏/模式/难度分榜）
/fakegame me [游戏]                 # 我的累计战绩
/fakegame admin                     # 管理员设置面板
/fakegame admin tempRules on|off    # 局内临时规则总开关（默认关闭）
/fakegame admin radius <8-128>      # 区域半径上限
/fakegame admin boundary <4-40>     # 边界粒子刷新周期（tick）
/fakegame admin preset <名> <半径> <minY> <maxY>
```

准备阶段不再因人数满足自动开局：发起者在准备面板点击“开始”后才进入倒计时；倒计时期间人数不足、成员离线/死亡/跨维度或 123木头人起点圈失效会返回准备并说明原因。

### 区域与边界

- 区域由管理员预设（`small` / `standard` / `large`，默认半径 32）与服务器级最大半径约束；普通发起者只能选择预设或管理员允许的范围，不能任意扩大。
- 边界用只发给参与者的粒子弧段持续显示，接近时 ActionBar 提示距离；越界保留 100 tick 宽限，判定与显示共用同一半径和高度。
- 鞘翅等飞行玩法额外显示并判定高度范围；不生成真实围墙、不修改世界边界、不用强制传送代替提示。

### 游戏列表

| 游戏 | 关键设置 | 玩法 | 胜利条件 |
|------|---------|------|---------|
| `grass_is_lava` | mode=fixed/rotate, hazard, interval, evacuate | 固定或轮换危险方块；切换前预告并给撤离时间；水按流体接触判定 | 最后存活者 |
| `rising_lava` | display=particles/blocks/flood, start_offset, rise_seconds, accelerate | 虚拟液面从场地下方上涨；无人淘汰时逐渐加速，有人淘汰后恢复基础速度 | 最后存活者 |
| `red_light_green_light` | start_radius, goal=auto/manual | 准备阶段全员进入起点圈；绿灯走红灯停；终点检查水平距离与高度差 | 第一名抵达终点 |
| `random_bans` | actions=all/basic/oneshot, round_seconds | 持续要求（跳跃/停下/潜行/移动）与限时完成（跳一次/抬头/低头），公布后有宽限 | 最后存活者 |
| `safe_spot` | spots, rounds, round_seconds | 每轮 1~4 个点，先有效到达者立即得分并关闭该点；已领取归属不重分配 | 固定轮数后积分最高 |
| `hide_and_seek` | mode=classic/shrink/herb, hide_seconds, seek_seconds | 寻找者躲藏阶段留在起点等待区（越界预警 3 秒后取消）；经典/隐身草抓捕需视线；缩小感染用有效攻击命中转换身份；隐身草模式高草隐身 | 按阵营与时限 |
| `tag` | mode=freeze/infection, rescue_seconds, duration | 鬼持续接触生效；冰冻解救需持续接触，中断立即重置；感染入伙 | 鬼清场或幸存者撑到时限 |
| `hot_potato` | fuse, pass_lock | 只有有效攻击命中才传递；短暂禁止立即传回上一名持有者；引线不因传递重置 | 最后存活者 |
| `king_of_the_hill` | mode=single/rotate/simultaneous, points, rotate_seconds | 1~4 个点独立开启/争夺/得分；关闭的点不得分；多人争夺暂停得分 | 积分最高 |
| `charades` | rounds | 表演者用动作演题，其他人 `/fakegame guess` 猜词 | 积分最高 |
| `shooting` | mode=training/speed/accuracy/versus, targets, duration | 准星瞄准，原版换手动作发射（服务端消费，不交换双手）；虚拟射线检查遮挡/距离/频率；不消耗物品。versus 为多人同场比靶，不是玩家互射 | 比分最高；训练单人可玩 |
| `elytra` | mode=rings/collect, rings, duration | 用自己的鞘翅按顺序穿环或自由收集；线段求交避免高速漏判 | 完成环线或收集最多 |
| `snake` | speed, max_length, food_count | 持续行走；收集无主假人后尾巴沿历史路线前进；撞尾巴出局，尾巴落地变回无主假人 | 最后存活或尾巴最长 |
| `murder_mystery` | script, round_seconds | 当前不可用：剧本导入与阶段机未完成本轮适配 | - |
| `werewolf` | day_seconds, night_seconds | 当前不可用：阶段机未完成本轮适配 | - |
| `draw_guess` | mode=classic/relay/same, rounds | 当前不可用：神笔马良尚未并入正式主线 | - |

### 判定与保护

- 虚拟淘汰播放原版不死图腾动画与音效，不消耗图腾、不触发真实死亡、不清背包与经验。
- 真实死亡（怪物/摔落/真实岩浆）按原版流程处理，同时该玩家自动退出本局。
- 击退、水流（含熔岩）与攀爬滑落在红灯与跳跃判定中豁免；活塞推力无服务端信号可区分，不在豁免范围。
- 加入需存活、非旁观、同维度且在区域内；等待/倒计时期间死亡、掉线、跨维度、旁观自动退出，运行期越界保留 100 tick 宽限。
- 同 tick 多人违规按同批淘汰结算；同 tick 抢点按距离、再按 UUID 稳定排序，不声称超过服务端 tick 精度。
- 局内临时能力只使用已开启规则或管理员总开关允许的局部适配；退出、淘汰、掉线、跨维度、结束、规则关闭和停服都会清理本局添加的效果。
- 结果按游戏/模式/关键难度分榜，使用 UUID 入账；取消局不记正常胜场，同局只入账一次。

## /chatpunish - 聊天惩罚

> **所属规则**：`chatPunishment`

### 语法

```
/chatpunish set <玩家> <模式> [时长]
/chatpunish clear <玩家>
/chatpunish list
```

`<模式>` 为 `mute` / `tape` / `cat` / `idiot` / `censor` / `villager` / `stutter` / `reverse` 之一（字面量，自动补全）。`[时长]` 为 `数字+s/m/h/d`（如 `10m`、`1h`、`1d`），缺省永久；到期自动解除，按真实时间计算。

### 权限

- 仅管理员可用（OP 等级 4），控制台可用
- 规则 `chatPunishment` 关闭时整棵命令树隐藏

### 功能描述

对指定玩家的公共聊天施加惩罚，每名玩家同时只生效一种模式，再次 `set` 直接覆盖。八种模式的效果与示例见[规则文档](rules.md#chatpunishment---聊天惩罚)。禁言模式下公共消息不再广播，发送者收到「你已被禁言」提示；其余模式把默认聊天显示替换为转换后的文本。签名原文仍随消息保留，客户端开启安全聊天或查看修改标记时可能看到原文，本规则不承诺内容保密或过滤。

玩家名走原版档案解析（同 /ban）：在线自动补全，离线玩家经档案缓存解析 UUID，未知玩家由原版报错，不凭猜测建档。惩罚按 UUID 存于 `<存档>/carpet-pry-addition/chatpunish.json`，重启与重连不丢失；到期读取时惰性判定，落盘时清理。

不影响玩家的命令、私聊与系统消息；规则关闭时不改聊天、不清记录。

### 相关规则

**chatPunishment** - 开关整体功能；关闭时命令隐藏、已存惩罚保留（重新开启后未到期者继续生效）。

### 使用示例

```
/chatpunish set Steve tape            # Steve 的聊天固定为胶带文本（永久）
/chatpunish set Steve cat 1h          # Steve 的聊天追加 喵~，1 小时后自动解除
/chatpunish set Steve mute 30m        # 禁言 30 分钟
/chatpunish list                      # 查看未到期惩罚（名称、模式、剩余时间或永久）
/chatpunish clear Steve               # 解除 Steve 的惩罚
```

## 玩家回放命令

### /playereplay - 录制与幻影回放

#### 语法

```
/playereplay                                  # 打开主面板（开始录制/录像列表）
/playereplay record [名称] | on [名称]        # 进入准备页（默认只录自己）
/playereplay record begin                     # 开始采集已加入者
/playereplay record objects                   # 录制对象：邀请/直接添加/移除自己
/playereplay record invite <玩家>             # 普通玩家邀请；管理员直接添加并通知
/playereplay record uninvite <玩家UUID>       # 取消尚未回复的邀请
/playereplay record self on|off               # 加入/移除自己（管理员可只录他人）
/playereplay record settings                  # 时长与访问范围
/playereplay record duration <秒>             # 设置录制时长（受服务器上限约束）
/playereplay record access public|private     # 设置访问范围（邀请后锁定）
/playereplay record pause|resume              # 暂停/继续（冻结共同时间轴）
/playereplay record stop | stop               # 结束并保存
/playereplay record retry                     # 保存失败后重试
/playereplay record leave                     # 普通受邀者主动退出（管理员直接添加者不可退出）
/playereplay record discard [confirm <任务ID>] # 放弃（二次确认）
/playereplay invite accept|deny <任务ID> <令牌>
/playereplay start [名称] [here] | startid <ID> [here]
/playereplay loop <名称> | loopid <ID>
/playereplay pause|resume|end <回放ID> | playings | endall
/playereplay list [页] | list mine [页] | favlist [页]
/playereplay info <ID> [来源] [页]             # 详情
/playereplay rename <ID> <新名称>              # 创作者/管理员
/playereplay access <ID> public|private        # 创作者/管理员切换访问范围
/playereplay fav <ID> | unfav <ID>             # 个人收藏（只存引用）
/playereplay delete <ID> confirm               # 删除（先确认）
/playereplay panel                            # 刷新面板
```

#### 权限

- `/playereplay` 根命令：所有玩家可用，需开启 `playerReplay` 或 `fakePlayerScript`。
- 开始/查看/播放/收藏：按录像访问范围校验；公开录像面向所有具备功能权限的玩家，私有录像仅创作者与管理员。
- 改名/访问范围/删除：创作者本人或等级 2 管理员；被录制者不会因参与自动取得管理权。
- 幻影播放与控制：需开启 `playerReplay`；`pause/resume/end` 只能控制自己的观看实例，管理员可控制全部。
- `endall`：管理员结束全部；普通玩家只结束自己的实例。

#### 功能描述

两条规则共用同一套录制、保存、列表和权限。`record` 先进入准备页，此时不采集；默认录制对象是自己，普通玩家邀请在线玩家并由对方点击同意，管理员可直接添加且无需同意。准备阶段未回复的邀请在点击开始时失效，已同意者加入；录制中仍可继续邀请，接受后从加入时刻开始，不补录此前行为。开始只采集已加入者，不等待未回复邀请。

一份录像使用一个共同时间轴，每名参与者独立视觉/输入轨道和在线片段。正常下线记录离开时刻、幻影消失；仍具参与关系者重连按 UUID 续录，离线前后不连线；普通受邀者主动退出后重连不自动续录。创作者下线不结束任务。暂停冻结共同时间轴，恢复时同步输入/槽位/装备基线并开启新段，不补录暂停期间点击。

主面板空闲时保持“玩家回放”“开始录制”“录像列表”；运行中回放控制放在列表底部或 `playings` 次级页。录制中面板显示录制对象与暂停/结束/放弃/对象/设置按钮，有效时长走动作栏。列表每条整行可点击，格式为 `名称 · yyyy/MM/dd · HH:mm:ss · mm:ss`，支持公开/私有过滤、只看我的、收藏和分页，详情保留来源页码。

#### 参数说明

| 参数 | 说明 |
|---|---|
| 名称 | 录像名（1~32 字符，中英文/数字/下划线/连字符），缺省按时间自动命名；身份以稳定录像 ID 为准 |
| here | 以命令执行者位置与朝向为锚点整体平移+旋转轨迹（仅同维度录像） |
| 回放ID | 观看实例编号，`start`/`loop` 成功时返回 |
| 任务ID / 令牌 | 准备任务 UUID 与单次邀请独立令牌；旧按钮不能批准新邀请 |

#### 使用示例

```
/playereplay record 我的录像       # 进入准备页
/playereplay record begin          # 开始录制
/playereplay record stop           # 结束并保存
/playereplay list                  # 可访问录像列表
/playereplay info <ID>             # 详情：播放/假人执行/收藏/管理
/playereplay start <ID>            # 用自己的独立实例播放
/playereplay loop <ID>             # 无限循环
/playereplay pause 1               # 暂停自己的回放 1
/playereplay end 1                 # 结束自己的回放 1
```

#### 限制

- 单段最长 10 分钟（12000 Tick），最短 30 秒；每人最多 20 段；达到时长自动结束保存。
- 多人录像参与者上限 16 人；录像按参与者分段保存，离线空档不写帧。
- 录像保存在 `<存档>/carpet-pry-addition/replay/recordings/<录像ID>.repry`；收藏在 `favorites/<玩家UUID>-favorites.json`，只保存引用。
- 停服保存已有内容并结束任务，开服不恢复录制/邀请/假人执行；崩溃只恢复最近自动检查点并标记“异常恢复”。
- 容器 GUI 内拖拽不录制；鼠标视角增量精度为每 Tick 末采样；离线服按玩家名向 Mojang 回填皮肤。

## 假人脚本命令

### /playerscript - 录制与假人执行

#### 语法

```
/playerscript                                 # 打开脚本面板
/playerscript record [名称] | record stop     # 与玩家回放共用准备/录制/保存
/playerscript setup <录像ID>                  # 选择参与者并打开设置页
/playerscript setup <录像ID> participant <参与者UUID>
/playerscript setup <录像ID> bot <假人名> | count <次数> | inf | mode reset|continue
/playerscript run <录像ID>                    # 自动召唤假人并执行，完成自动下线
/playerscript run <录像ID> force              # 1.21/1.21.1：显式执行仅骑乘/静止交互脚本
/playerscript run <名称> as <假人>            # 兼容：绑定已在线假人（停止后不下线）
/playerscript pause|resume|stop|status <假人>
/playerscript scripts                         # 运行中脚本面板
/playerscript list
```

#### 权限

等级 2（游戏管理级）；需开启 `fakePlayerScript`。脚本控制另校验操作者是会话发起人或管理员。

#### 功能描述

从录像中提取指定参与者实际在线的输入片段，去掉离线空档后顺接为脚本，交给自动召唤的 Carpet 假人重演**操作**而非结果：按 W 就尝试前进（被墙挡住也继续），目标方块换成泥土就挖泥土，不等待原结果、不寻路、轮内不纠偏。多人录像先在设置页选择参与者；设置按操作者、录像 ID、参与者 UUID 隔离。没有有效输入内容时拒绝执行。

片段边界先让上一段末帧经过实体 Tick 消费，再释放旧持续动作并重建记录基线；不按录像位置在片段间传送。自动召唤假人结束后自动下线；外部绑定假人只解除控制，不擅自下线。

#### 使用示例

```
/playerscript record 麦塔                 # 与玩家回放共用的准备入口
/playereplay record begin                  # 开始录制
/playereplay record stop                   # 保存
/playerscript setup <ID>                   # 多人时先选参与者
/playerscript setup <ID> count 10          # 执行 10 轮
/playerscript setup <ID> mode reset        # 每轮回到起点（默认接续当前位置）
/playerscript run <ID>                     # 自动召唤 ReplayBot 执行，完成后自动下线
/playerscript pause Bot                    # 暂停（假人仍受重力/碰撞；瞬时事件不重复）
/playerscript status Bot                   # 查看脚本/Tick/轮次/状态
/playerscript stop Bot                     # 停止并下线（外部绑定的假人不下线）
```

#### 语义约定

- 时间轴为半开区间 `[0, 时长)`；每轮循环恢复录像首帧的绝对朝向；“回到起点”仅在轮间做明确的位置重置，轮内不纠偏。
- 不复制录像装备、不补材料、不重置背包/耐久/血量；物品不足按原版结果处理。
- 暂停时持续使用动作（弓/进食/盾）按原版语义释放，可能有副作用；暂停期间假人仍受重力与碰撞影响。
- 设置页修改假人名/次数/无限/循环位置后自动重绘，设置与当前录像 ID、参与者 UUID 绑定，不跨录像或跨参与者串用。
- 暂停恢复会重新开始持续使用（弓/进食/盾）：不恢复旧蓄力，也不返还已消耗材料；恢复不会重发暂停期间的瞬时事件。
- 1.21/1.21.1 普通步行不发送输入包，`run` 默认拒绝；仅骑乘/静止交互确需执行时追加 `force`。
- 已知限制：副手使用/攻击降级为主手尝试；容器 GUI 拖拽不录制；攻击冷却由服务端物理约束（与录制时节奏可略有偏差）。
