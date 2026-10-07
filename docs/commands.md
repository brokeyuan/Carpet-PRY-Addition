# 命令文档

> **Mod ID**: carpet-pry-addition  
> **版本**: 1.2.0

---

## 快速导航

- [假人珍珠传送命令](#假人珍珠传送命令)
  - [/tpp - 假人珍珠传送](#tpp---假人珍珠传送)
  - [/tppset - 站点管理](#tppset---站点管理)
- [/hat - 玩家帽子](#hat---玩家帽子)
  - [语法](#语法)
  - [权限](#权限)
  - [功能描述](#功能描述)
  - [相关规则](#相关规则)
  - [使用示例](#使用示例)
- [假人持续清空背包](#假人持续清空背包)
  - [命令语法](#命令语法)
  - [权限](#权限)
  - [相关规则](#相关规则)
  - [与原版 dropStack 的关系](#与原版-dropstack-的关系)
  - [使用示例](#使用示例)
  - [自动停止条件](#自动停止条件)
- [/player sendto - 假人背包链接](#player-sendto---假人背包链接)
- [/player brain - 假人脑子](#player-brain---假人脑子)
  - [命令语法](#命令语法)
  - [转移行为](#转移行为)
  - [使用示例](#使用示例)
- [玩家随地大小变](#玩家随地大小变)
  - [/scale - 玩家大小调节](#scale---玩家大小调节)
- [骑乘权限命令](#骑乘权限命令)
  - [/riding - 骑乘权限管理](#riding---骑乘权限管理)
  - [/picking - 捡起权限管理](#picking---捡起权限管理)
- [/pvp - 和平的玩家](#pvp---和平的玩家)
- [/patnod - 被摸互动开关](#patnod---被摸互动开关)
- [/text - 米塔字幕](#text---米塔字幕)

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

将主手物品戴在头上，与头上物品交换。

### 相关规则

**playerHat** - 启用时，头部槽位放置不死图腾后，玩家受到致命伤害时先触发不死图腾的正常复活效果，再额外附加以下状态：
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

## /player sendto - 假人背包链接

> **所属规则**：`fakePlayerSendto`

通过 Mixin 在 Carpet 自带的 `/player <name>` 命令树下追加独立的 `sendto` 子命令，建立假人之间**单向**的背包物品流链接：源假人持续把背包物品转移给目标。

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
/player <name> brain [zombie|babyzombie|skeleton|witherskeleton|drowned|zombiepiglin|pillager|vindicator|irongolem|spider|piglin|piglinbrute|slime|magmacube|fish|enderman|wolf|villager|pig|off] [keep]
```

模式名跟随 `/carpet language`：语言为中文（zh_cn/zh_tw）时补全与输入用中文（如 `brain 僵尸`），英文键恒可用；两种写法均可加 `keep`。

| 参数 | 行为 |
|------|------|
| (无参数) | 查询当前 AI 模式 |
| `<模式> keep` | 保持脑子：假人下线重上后自动恢复同一模式（含狼的主人）；`brain off` 清除 |
| `zombie` | 僵尸模式：近战追击最近玩家，原生 `attack()` 挥砍 |
| `skeleton` | 骷髅模式：主手持弓时远程射击 + 风筝走位，原生拉弓消耗背包箭矢 |
| `pillager` | 掠夺者模式：同骷髅但持弩（上弦 25 tick） |
| `irongolem` | 铁傀儡模式：攻击敌对生物（不攻击苦力怕）与敌对假人（含报复攻击过自己的玩家） |
| `spider` | 蜘蛛模式：昼中立、夜敌对 |
| `wolf` | 狼模式：跟随执行命令的玩家并仇恨同步（主人被谁打就咬谁） |
| `villager` | 村民模式：随机漫步、被攻击恐慌、遇僵尸反向逃跑 |
| `enderman` | 末影人模式：被凝视激怒后疾跑扑击 |
| `babyzombie` | 小僵尸模式：更快的近战追击 |
| `witherskeleton` | 凋灵骷髅模式：近战追击玩家/铁傀儡与猪灵类，规避狼 |
| `drowned` | 溺尸模式：持三叉戟远程投掷 + 空手近战，群体仇恨广播 |
| `zombiepiglin` | 僵尸猪灵模式：中立，被打才反击并唤醒同类 |
| `vindicator` | 卫道士模式：近战追击玩家/村民/铁傀儡 |
| `piglinbrute` | 猪灵蛮兵模式：恒敌对近战，无视金装 |
| `slime` | 史莱姆模式：跳行移动 + 跳行追击 |
| `magmacube` | 岩浆怪模式：同史莱姆 |
| `fish` | 鱼模式：水中游动/避人、离水扑腾、海豚式换气 |
| `pig` | 猪模式：纯中立，被打恐慌逃跑 |
| `piglin` | 猪灵模式：敌视不穿金装者，武器决定战斗方式，捡拾装备 |
| `off` | 卸载脑子，恢复 Carpet 手动控制 |

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

# 卸载脑子，恢复 Carpet 手动控制
/player Steve brain off
```

### 自动卸载条件

- 执行 `/player <name> brain off`
- 切换为其他模式（先卸旧脑再挂新脑）
- 关闭 `fakePlayerBrain` 规则（下一 tick 自动卸载）
- 假人死亡、下线或被 `/player <name> kill`

卸载后假人恢复静止木桩状态，无任何残留实体或状态（模块不生成任何额外实体）。

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
| `true`  | 玩家只能 `set/reset` 自己；只有 OP 可以 `set/reset/info` 别人。`set <value> <player>` 的 tab 补全仅显示自己名字 |
| `everyone` | 所有人都可以 `set/reset/info` 任意在线玩家；tab 补全显示所有在线玩家 |

- 规则切换时通过 Carpet `RuleObserver` 立即刷新命令树，无需玩家重新登录
- `info` 查询的可见性比 modify 宽松：`true` 模式下非 OP 也可以 `info` 别人（info 不改变状态）；`self` 模式下所有人只能 `info` 自己；`set/reset` 仍需权限

#### 范围控制

- 硬边界：value 仅要求大于 0（不设上下限），对所有路径统一生效
- `playerScaleMin`（默认 0.1）：所有玩家允许设置的最小值
- `playerScaleMax`（默认 1.5）：所有玩家允许设置的最大值
- 软边界约束所有玩家（含管理员调自己与调他人）；管理员需要更大范围时，通过 `/carpet playerScaleMin` / `/carpet playerScaleMax` 调整边界本身

#### 功能描述

为 `Player` 注册 `minecraft:scale` 属性，通过统一的三层子命令 `/scale set|reset|info` 管理。支持四种模式切换（false / self / true / everyone）。self 模式下所有人（无论 OP）都只能调整自己；true 模式下管理员可调任意玩家；everyone 模式下所有人可互相调节。范围与权限分离：tab 补全根据当前身份过滤可见的玩家，范围限制根据身份分级。

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
/scale set 2.0 Steve
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

按玩家开关"接不接受被摸"的个人偏好：不接受时其他玩家无法摸你（摸头对其完全不生效）。状态按玩家名持久化于 config/carpet-pry-patnod.json，跨重启保留。

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
| `distance` | 小数 | `2.5` | 生成点距玩家眼睛视线的距离（格） |
| `scale` | 小数 | `2.2` | 字符缩放（弹出终态） |
| `spacing` | 小数 | 自动（0.15×scale） | 字间距单位（格/宽度单位） |
| `hold` | 整数 | `80` | 打字完成后坠落前的停留（tick） |
| `glow` | true/false | `false` | 字符发光描边 |
| `sound` | true/false | `true` | 逐字点击音效 |
| `drop` | true/false | `true` | 停留后是否坠落（false = 原地渐隐） |

### 行为细节

- 逐字弹出：2 tick 一字，每字随机歪斜 ±45°、1.8 倍缩放收拢、随机 y 抖动，伴随点击声（音量 1.0 / pitch 1.2）
- **感叹号整句增益（无封顶）**：结尾连续 `!`/`！` 越多整句越大、生成点越远——字号每个 +0.3（无上限），生成距离每个 ×1.15；末尾空白不打断，被其他字符打断则不计
- 默认白色（#FFFFFF），`&` 色码换色
- 坠落：重力 0.03/tick²、空气阻力 0.99、落地一次 0.28 反弹后静止渐隐、起落随机翻滚；坠落 24 tick 后按 -8 透明度/tick 渐隐
- 分组：≤25 字/组，断点向后 10 字内找标点；非末组追加 " - "；上一组开始坠落时下一组接续，组间随机偏航 ±22.5° 与随机抬高
- 生成点：各在线玩家脚部 + 视线方向 × distance，高度脚部 +1.3（每名玩家独立一份，以各自位置朝向为基准）；字符平面竖直固定（不随俯仰倾斜），位置与朝向出现时定死，之后不随执行者移动或转头变化
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
