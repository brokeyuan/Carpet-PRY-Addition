# Changelog

All notable changes to **Carpet-PRY-Addition** are documented in this file.

## 未发布

### 功能
- **假人脑子新增 4 种模式与长矛支持**（`fakePlayerBrain`，现共 11 种模式）：`babyzombie`（小僵尸——1.25 疾跑近战追击，行为对齐原版幼年僵尸的速度加成，身体锁定红线约束下不缩小模型）、`pig`（猪——完全中立无仇恨，被打/着火恐慌逃跑 + 随机漫步）、`piglin`（猪灵——复用原版 `PiglinAi` 金装判定（1.21~1.21.1 为 `isWearingGold`、1.21.3+ 更名 `isWearingSafeArmor`，预处理宏分支），敌视不穿金装的玩家、对金装玩家中立、被谁打都还手）；**26.1.2+ 僵尸模式支持原版长矛**：与原版 26.x 僵尸同款装配（移植版 `SpearUseGoal` 挂优先级 2、近战降为 3 兜底）——主手持矛（动能武器 `KINETIC_WEAPON` 组件）时接近→举矛蓄力→全速冲刺（刺击伤害由 `LivingEntity` 在使用期间的原版动能判定结算，零凭空造物）→后撤循环，1.21.x 无长矛物品该 Goal 不编入不受影响；铁傀儡的敌对假人判定同步纳入 babyzombie/piglin
### 功能
- **假人脑子**（`fakePlayerBrain`）：给 `/player <name>` 追加 brain 子命令，把原版生物式 AI"脑子"挂到假人身上，提供 8 种模式——`zombie`（近战追击最近玩家，原生 `attack()` 挥砍）、`skeleton`/`pillager`（主手持弓/弩时远程锁定与风筝走位，走原生 `startUsingItem → releaseUsingItem` 流程消耗背包真实箭矢）、`irongolem`（攻击敌对生物——不攻击苦力怕——与攻击过自己的假人）、`spider`（昼中立夜敌对）、`wolf`（跟随执行命令的主人并仇恨同步：主人被谁打就咬谁）、`villager`（随机漫步、被攻击恐慌、遇僵尸反向逃跑）、`enderman`（被凝视激怒——原版 `isStaredAt` 点积算法——后锁定并 `setSprinting(true)` 疾跑扑击）。核心设计遵循"只换脑子、不换身体、零额外实体、零凭空造物"：假人始终保持 `ServerPlayer` 类型与全部原生属性；寻路为自研紧凑 A*（原版 `MobNavigation` 构造器强绑定 `Mob` 实例，为守住零实体红线在方块网格上等价复刻节点推进/卡死重算语义）；AI 移动全部经注入的 `PlayerMoveControl` 折算为玩家原生按键输入（`zza/xxa`+限速转向+原生跳跃），交由玩家原生 `travel()` 物理执行，绝不直接改坐标/速度，避免玩家物理与生物瞬移式移动打架导致的"鬼畜抽搐"；架构为策略模式——mixin 在 `ServerPlayer` 初始化时以 `@Implements` 嫁接 `PryMob` 假面接口，导航器/移动控制/视线控制/双目标选择器按需惰性挂载（真人零开销），各模式向双选择器装配移植 Goal；挂载期间 Carpet 手动移动/攻击指令屏蔽（actionPack 停摆），`brain off`/切换模式/关闭规则/假人下线死亡均自动卸载且无残留；视觉同步（行走/疾跑/挥手/拉弓/视角）由原版实体同步机制驱动，纯服务端，客户端无需安装任何模组

- **更多种类的末地水晶**（`moreEndCrystalTypes`）：允许把末地水晶放在哭泣的黑曜石上。`true` = 放出的为普通水晶（不无敌）；`invulnerable` = 放出的为无敌水晶（`Invulnerable=1` 且显示底部板，与原版复活龙过程中推出柱子、打断复活得到的水晶相同）。两种模式放出的水晶光束均指向固定坐标 `(0,128,0)`。实现上仅把点击的哭泣黑曜石替换为黑曜石状态骗过原版基座校验，其余放置逻辑全部复用原版；客户端/服务端共用同一份逻辑，纯原版客户端在服务器上也可用。放在普通黑曜石/基岩上的水晶不受影响

### 修复
- **假人脑子"跑着跑着不动/莫名丢目标/杀完不索敌"系列**：目标选择与行为目标存在双重节流（`canStart` 间隔 × `canUse` 内部概率掷骰相乘，索敌有效频率被拖到平均 100 tick 以上、漫步拖到分钟级），现统一由 `canStart` 单层节流；骷髅/掠夺者 `stop()` 漏清移动控制器，风筝走位的 STRAFE 指令在目标停止后永久粘滞（表现为"目标没了还朝固定方向蹭/原地冻结"），补 `nav.stop()` 并在调度层加兜底保险（无 MOVE 旗标目标运行且路径走完即强制复位）；索敌增加 25% 滞后回差（搜索/丢失同半径导致目标在边缘走位时锁定闪烁）；近战在路径走完但仍够不着时强制重算 + A* 判定不可达且可见时直线逼近兜底（目标站住不动/水面台阶等场景不再罚站）；骷髅接近阶段 A* 重算从每 tick 降为每 5 tick（多假人 TPS）；被击反击追击上限 48 格（原版无上限会追到世界边缘）。本地 RCON 竞技场实测：骷髅假人 8 秒完成锁定→放箭→击杀僵尸假人
- **假人脑子四轮实测修复**：`1.21~1.21.10` 七个版本的按版本覆盖 `mixins.json` 漏加三条 brain mixin 条目，导致这些版本的构建产物完全没有脑子（26.x 不受影响）——现已补齐并核验产物；玩家攻击一次后永久哑火（移植版近战 Goal 漏了攻击冷却的每 tick 递减）；掠夺者拉弓不打（弩蓄满时被原版 `completeUsingItem` 收走使用态，改为按 `CrossbowItem.isCharged` 状态驱动击发）；目标锁死不切换（补 20 tick 运行中复扫）；攻击无挥手动画（`Player#attack` 不含 swing，`doHurtTarget` 补挥手）；丢失目标后原地挂机（全部战斗脑补挂原版同款 `RandomStrollGoal` 漫步）；被打不还手（新增 `PlayerHurtByTargetGoal` 被击反击目标，狼模式豁免主人）；狼只咬"主人被谁打"不咬"主人打谁"（补 `OwnerHurtTargetGoal` 语义，同步 `getLastHurtMob()`）；村民被恐吓后永远奔跑（`getLastHurtByMob` 永不清除，恐慌补 100 tick 时间盒，对齐原版 `getLastDamageSource` 的时限语义）；骷髅/掠夺者"先挂脑后给装备"得到空脑子（装配不再要求挂脑瞬间持武器，改由谓词动态判定）；目标选择器补齐原版抢占语义（高优先级目标可打断低优先级的进行中目标，漫步中遇到敌人立即转入战斗）；挂载时假人处于创造模式则主动提示（创造模式假人与原版生物一样不参与索敌）

- **`/tpp` 显式声明对 Carpet TIS Addition 的软依赖**：`/tpp` 传送流程内部依赖 `/player rejoin` 让站点假人在下线位置与朝向重生（基础 fabric-carpet 无 rejoin 子命令，由 Carpet TIS Addition 提供，本模组不重复实现）。此前该依赖完全隐式：未安装 TIS 时 `/player rejoin` 直接执行失败，玩家只会看到 10 秒超时后的"假人未能在超时前上线"，无从得知原因。现 fabric.mod.json 以 `suggests` 声明 `carpet-tis-addition`；执行 `/tpp` 时立即检测 TIS 是否在载，缺失时明确提示需安装 Carpet TIS Addition（`/tppset spawn` 走原版 spawn，其余功能不受影响）。同时已核实并写入文档：`fakePlayerSkinMode` 的皮肤在 rejoin 时同样生效——TIS 的 rejoin 内部复用 Carpet 原版 spawn 逻辑，本模组皮肤钩子注入于其尾部，行为与普通 spawn 完全一致（summon 模式使用执行 rejoin 命令者的皮肤）
- **假人皮肤持久化污染同名真人玩家**（`fakePlayerSkinMode`）：`summon` / `same_skin` 模式召唤假人设置皮肤后，同名真人玩家的皮肤会被换成假人刚设置的皮肤，且反复被打回——自行通过 `/skin` 重设，下次召唤假人后又失效。根因是调用 SkinRestorer `SkinService.setSkinAsync` 时 `save` 参数传了 `true`，皮肤会按假人的 UUID 写入其持久存储，而假人 UUID 与同名真人玩家相同（名字命中服务器用户缓存，或离线服真人加入的离线 UUID），SkinRestorer 在玩家上线时检测到已存皮肤即自动套用，覆盖其自行设置的皮肤。现改为 `save=false`，皮肤仅对假人当前会话生效：Carpet 假人不跨重启保留、重新召唤时 mixin 会重新套皮，故无任何功能损失（白名单方案多余）。文档与规则描述已同步说明
- **末地水晶活塞推动位置不同步**（`fixEndCrystalSync`）：用活塞推动末地水晶（含无敌水晶）一段距离后，客户端显示的位置与服务端真实位置永久不同步，只有重进/重启才恢复。根因是原版把末地水晶的跟踪间隔注册为 `updateInterval=Integer.MAX_VALUE`，服务端除出生包外从不向客户端同步其位置，活塞推动由两端在 `PistonMovingBlockEntity` 里各自独立模拟，模拟分歧后无任何自愈途径（`Entity.needsSync` 只有 `Entity.push`/`Entity.load` 会写入，活塞路径不写）。现开启规则后，服务端检测到水晶位置变化即把 `ServerEntity.tickCount` 归零命中原版位置同步分支（26.2 起自增移至门控前，按版本预处理置 -1），客户端 1 tick 内自动对齐，重进服务器时的出生时序竞争同样被覆盖
- **与 Axiom 的创造飞行速度冲突**（[#8](https://github.com/brokeyuan/Carpet-PRY-Addition/issues/8)）：同时安装 Axiom 时，其"创造模式飞行速度"调整会被快速打回 100%。根因是 `playerScalePhysics` 的每 tick 物理联动无条件管理 `Abilities.flyingSpeed`：Axiom 并非纯客户端调速——其服务端部分（单人存档的内置服务端同样加载）会通过 `axiom:set_fly_speed` 自定义包把调速值直接写入服务端玩家能力值，而本模组规则处于默认关闭时，只要发现该值偏离默认（0.05 = 100%）就立即复位并发送能力包，把 Axiom 的调整打回原形（Axiom 的 HUD 按当前值实时渲染百分比，故显示"回到 100%"）。现改为"所有权让位"策略：只接管原版默认值或本模组自己写入过的值；第三方的自定义速度（如 Axiom 调速）不改写、不发能力包，该值回到默认后自动重新接管。规则关闭 / scale 回 1.0 时对残留联动速度的清理行为不变，scale≠1 的飞行速度联动语义不变

## [1.2.2] - 2026-09-14

**与 1.2.1 代码完全相同，无功能性变更**，为验证更新后的 CurseForge 凭证重新走发布流水线。

## [1.2.1] - 2026-09-14

**与 1.2.0 代码完全相同，无功能性变更**，为补齐 GitHub Release 资产重新走发布流水线。

### CI

- release 矩阵关闭 fail-fast，单版本发布失败不再拖垮其余版本

## [1.2.0] - 2026-09-14

### 新增
- **`sleepingDuringTheDay` 白日做梦补齐旧版本实现**：白天入睡的放行此前仅在 1.21.11+ 生效（`BedRule.canSleep` redirect），1.21~1.21.10 上原版禁止白天上床、规则整体不可用。旧版本改经 fabric-entity-events-v1 的 `ALLOW_SLEEP_TIME` 钩子放行——旧版本的检查点（1.21~1.21.4 为 `Level.isDay()`、1.21.5 为 `Level.isBrightOutside()`、1.21.8~1.21.10 为 `ServerLevel.isBrightOutside()`，已逐一核实字节码）已被 fabric 按版本适配占用，本模组再直接 @Redirect 同一调用会冲突，故注册事件监听：规则开启时返回 SUCCESS 放行白天入睡，关闭时 PASS 走原版（怪物检测等其他入睡条件不受影响；白天点床仍会记录重生点，与 1.21.11+ 行为一致）。入睡后的唤醒控制与"醒来切换至夜晚"由 `MixinPlayerBase`（全版本注册）处理。`sleepingDuringTheDay.MixinPlayer` 相应收口为 1.21.11+ 专用。全部受支持版本功能现已一致
- **玩家缩放全规则前移支持 1.21~1.21.4**：`playerScale`（含 `/scale` 命令与 FOV 补偿）、`realisticPlayerScale`（四模式物理联动）、`playerScaleLinkedEntities` 三条规则及其 7 个 mixin 此前仅注册于 1.21.5+，现覆盖全部受支持版本。前移依据：`minecraft:scale` 属性与各联动属性（移速/跳跃/台阶/交互距离/摔落安全距离/重力）实际自 1.20.5（快照 23w51a）起即由原版提供并默认存在于所有生物（含玩家）的属性表——此前"SCALE 属性 1.21.5 才加入原版"的注释与文档表述系误记，已一并修正。逐版本映射后字节码核实：`Attributes.SCALE` 等字段 1.21~1.21.4 均为 `Holder<Attribute>` 且签名与 1.21.5+ 一致；`RangedAttribute.sanitizeValue(double)`、`ServerPlayerGameMode.useItem/useItemOn`、`ServerLevel.addFreshEntity` 全版本一致；`AbstractClientPlayer.getFieldOfViewModifier` 在 1.21~1.21.1 为无参签名（FOV 补偿处理器相应按版本分支捕获参数）；鞘翅联动注入点按版本分支——1.21.3+ 注入 `travelFallFlying`，1.21~1.21.1 无此拆分、改注入 `travel` 的 move 调用点并以 `isFallFlying()` 甄别（travel 内 move 为各移动分支共用）。1.21 服务端实机启动验证通过（Done + 0 mixin 错误），各版本 `mixins.json` 已注册
- `playerScale` 的 `PlayerMixin` 注释澄清：scale 属性 1.20.5+ 已在原版默认属性表中，该 mixin 的注册为幂等兜底保险

- **`peacefulPlayers` 和平的玩家 + `/pvp` 指令**：按玩家开关 PVP（类似群聊禁言模型）——关闭 PVP 的玩家不能攻击玩家、也不会受到玩家伤害（自伤不限），被拦截的攻击播放提示音；`/pvp on|off` 开关自己，`/pvp on|off <玩家>`（权限随模式：self/true/everyone），`/pvp on|off @a` 全服总开关（仅管理员、self 模式除外；全局关闭期间个人操作锁定，解除时强制全员恢复开启），`/pvp list` 列出所有 PVP 关闭的玩家；状态跨重启持久化于 `config/carpet-pry-pvp.json`，新加入玩家跟随全服默认状态
- **`playerScaleLinkedEntities` 玩家大小变联动实体**：玩家使用物品直接生成的生物实体继承玩家当前体型（写入 minecraft:scale 基础值快照）——0.5 的玩家放出的盔甲架也是 0.5 大小，刷怪蛋生物、摆出的铁/雪/铜傀儡同理（放南瓜的构造生成同步发生在使用漏斗内；幼崽等原有修饰符在此基础上叠加比例）。实现上经 `ServerPlayerGameMode` 的 useItem/useItemOn 漏斗记录操作玩家，在 `ServerLevel.addFreshEntity` 收口处写入体型（仅 1.21.5+ 注册）。仅覆盖有 scale 属性的生物实体，不触碰渲染与碰撞箱；投掷物、掉落物、物品展示框、船、矿车、TNT 等非生物实体不联动；发射器、刷怪笼等非玩家来源不联动；写入为快照不随玩家后续变化，玩家体型为 1.0 时不做任何改动
- **`realisticPlayerScale` 物理联动模式化**：规则由布尔改为 `false / true / safety / strict` 四模式，覆盖三种真实化取向：
  - `true`（平缓）：移动/飞行/鞘翅烟花速度、跳跃、台阶、交互距离、摔落安全距离均按 √scale 曲线缩放（16 倍体型移速 4 倍而非 16 倍），无保底
  - `safety`（平缓+保底，推荐）：曲线同 true，为小体型（scale<1.0）加保底——速度/飞行/重力 0.3×、跳跃/台阶/交互/摔落 0.5×，极端缩小（如 0.1）仍可玩
  - `strict`（严格等比）：速度/台阶/交互/摔落严格 ×scale；跳跃初速 ×scale^0.75，与重力 √scale 配套后跳高与体型等比放大（2 倍体型跳 2 倍高、16 倍体型跳 16 倍高）；无任何保底，完全按几何比例行动
  - 重力在三种模式下均 ×√scale（加速度量按平方根联动；线性缩放会使 16 倍体型终端速度约 62 格/tick 失控）
- **`realisticPlayerScale` 鞘翅滑翔/烟花联动**：新增鞘翅位移缩放（`LivingEntityMixin`，仅 1.21.5+ 注册）——鞘翅滑翔中 `move()` 的位移按移动速度联动因子等比缩放（因子随模式变化：true/safety 为 √scale、strict 为线性 scale）。原版滑翔位移、烟花加速的极速（速度被拉向视线方向 ×1.5 的固定控制器）与转向项均为硬编码、与体型无关；现大体型滑翔/烟花极速随体型放大、转向半径更大（几何相似），小体型更慢更飘。只缩放传给 move 的位移、不改写存储速度，物理无正反馈、任意体型稳定收敛；生效判定与 FOV 补偿一致（以移动速度属性上的 scale_speed 修改器为准），服务端（含假人）与客户端本地预测一致；已知取舍：鞘翅撞墙伤害按存储速度（原版量级）计算，不随位移缩放
- **`realisticPlayerScale` 重力联动（下落速度）**：下落加速度随体型平方根缩放（×√scale；仅 safety 模式对小体型 0.3 保底）——大体型下落明显更快（scale 4→2×、16→4×），缩小更飘逸（0.25→0.5×）。创造飞行时原版会以飞行前竖直速度覆盖重力、重力属性无效；鞘翅滑翔的重力项则随重力属性生效（此前文档称鞘翅不受重力影响，与 1.21.5+ 实际实现不符，已修正），滑翔/烟花位移另由鞘翅联动 mixin 缩放

### 行为变更
- **10 条规则更名（破坏性：`carpet.conf` 中旧值失效回落默认，需重新设置）**，统一 lowerCamelCase 命名（首单词小写、后续单词首字母大写）：
  - `TppFakePlayer` → `fakePlayerTpp`
  - `FixXaeroLib` → `fixXaeroLib`
  - `FixBluemap` → `fixBlueMap`（BlueMap 用官方拼法）
  - `playerhat` → `playerHat`
  - `betterSnowBall` → `betterSnowball`
  - `fakePlayerDropStackModifiers` → `fakePlayerDropAll`
  - `realisticPlayerScale` → `playerScalePhysics`
  - `ridingPlayersPickUpLimit` → `ridingPlayersStackLimit`
  - `ridingPlayersDismountOnGameModeChange` → `ridingPlayersAutoDismount`
  - `ridingPlayersClientAllowInteractions` → `ridingPlayersClientInteract`
  - 保留不改：`playerScaleLinkedEntities`、`peacefulPlayers`（命名合规）。README / docs / 语言文件 / mixins.json 包名全部同步

- **`realisticPlayerScale` 类型由 `boolean` 改为字符串选项**：`false/true/safety/strict`；旧配置中的 `true` 直接映射为新 `true`（平缓）模式，原"小体型 √+保底、大体型线性"的混合行为由 `safety`（缩小场景）与 `strict`（放大场景）分别承接
- **`/scale` 硬边界改为仅要求大于 0**：value 参数不再设固定上下限（原对齐原版属性的 0.0625–16.0），任何大于 0 的有限值均可直接设置生效；`RangedAttributeMixin` 相应放行 SCALE 属性的任意有限正值，非正值/非有限值（NaN/Infinity）回落原版夹紧兜底，服务端命令反馈值与实际生效值一致。注意：纯原版客户端（未安装本模组）在超出原版范围（<0.0625 或 >16.0）时显示仍会被客户端侧夹紧，需客户端安装本模组；极端缩放值可能引发生物碰撞箱与渲染异常，请自行斟酌
- **`/scale` 软边界调整**：`playerScaleMin/Max` 软边界统一约束所有玩家（原管理员"调他人"不受限，现含管理员在内均受限；管理员可通过 `/carpet` 修改这两条规则调整边界本身）；软边界默认值由 0.1–10.0 调整为 0.01–16.0，发布前最终调整为 **0.1–1.5**
- **规则改名 `playerScaleModifiers` → `playerScale`**：命令门控与权限模式（false/self/true/everyone）不变；旧配置文件中的规则值失效，需以新名称重新设置
- **FOV 补偿归属调整**：视野（FOV）补偿自 `realisticPlayerScale` 移至 `playerScale` 规则（客户端 mixin 注册随之迁移；触发仍以属性同步中的 scale_speed 修改器为准——该修改器由 realisticPlayerScale 的移速联动施加，故实际补偿行为不变，仅归属与文档描述调整）
- **假人名构建规则**（`TppFakePlayer`）：站点内部名上限从 5 字符放宽至 **16 字符**；总长超限时按新优先级取舍——站点完整保留（必须）→ `_` 分隔符（可省略）→ 玩家名尽可能多。站点占满 16 字符时假人名即站点名本身，该站点所有玩家共用同一假人名
- **`/tppset set` 新增站点名长度校验**（≤16 字符），`/tppset rename` 别名上限统一为 10 字符；旧配置中的超长站点名在执行 `/tpp` 时会被明确拦截并提示
- **规则默认值**：`fakePlayerSendto` 默认改为关闭（`false`）；`fakePlayerNameSuggestions` 默认保持 `Steve,Alex`
- 错误反馈通道修正：站点已存在/别名空/无权限/玩家离线等约 12 处失败提示从 `sendSuccess` 改为 `sendFailure`（红色文本、命令返回 0）；dropall 的 `already_running` 同步改为失败语义
- dropall/sendto 解析目标假人失败时的提示接入三语言 i18n（原为硬编码英文）
- **`TppFakePlayer` 选项顺序统一**：options 从 `false, true` 调整为 `true, false`，与其余布尔规则一致
- **dropall / sendto 在规则关闭时隐藏命令**：`/player <name> dropall` 与 `/player <name> sendto` 此前未挂 requires 谓词——规则关闭时命令仍可见（dropall 甚至实际生效、sendto 可输入但转移被暂停）。现与其他命令一致：规则关闭时整棵子树不可见、不可执行，规则切换时经 RuleObserver 立即刷新命令树

### 修复
- **sendto 多源链接时的服务器崩溃**：tick 任务遍历 `LINKS` 期间，懒清理路径（源假人下线/全部目标失效）会结构性删除条目，抛 `ConcurrentModificationException` 致服务器崩溃——默认配置（`fixBlueMap` 关闭，假人下线不走事件路径）下，≥2 个源假人有链接且其一离线即可触发。改为快照遍历
- **betterSnowBall 单人游戏客户端崩溃**：`onHitEntity` 注入点在单人游戏中客户端线程同样执行，`hurtServer` 的 `(ServerLevel)` 强转遇 `ClientLevel` 直接 ClassCastException。客户端逻辑侧现提前返回，击退与伤害仅由服务端施加
- **白日做梦（sleepingDuringTheDay）昼夜循环破坏**：唤醒分支原以"醒来时是白天 + sleepTimer≥100"判定，而夜间入睡的玩家在黎明被原版唤醒时同样满足这两个条件——时间被错误拨回 13000，白天几乎无法自然到来。现改为在 `startSleepInBed`（`ServerPlayer` 校验通过后调用 super 的路径，全版本核实）记录"入睡时刻是否为白天"，仅白天开始的睡眠由规则接管；夜间开始的睡眠完全放行原版
- **26.2 子项目无法编译**：`MixinPlayerBase` 使用的 `Level.getDayTime()` 在 26.x 已更名为 `getOverworldClockTime()`，26.1.2 有版本覆盖文件而 26.2 遗漏，补齐
- **单 JVM 内服务器重启后调度体系静默停摆**：`ServerTickScheduler` 在 SERVER_STOPPING 清空任务集，但 `DropSlotScheduler`/`SendtoLinkManager`/`FakePlayerSessionManager` 的一次性注册标志仍在，第二次启动后 tick 任务不再注册（dropall/sendto/tpp 全部失效）。现任务集跨服务器实例存活（各任务自校验状态有效性），由各管理器在 SERVER_STOPPING 清理自身业务状态；`DropSlotScheduler.tasks`（强引用 ServerPlayer）的停服泄漏一并修复
- **只开 pickupPlayers 的服务器骑乘/捡起许可不清理**：下线清理被 `ridingPlayers` 规则门控，而许可表（RIDE+PICKUP）是共用的——只开捡起的服务器玩家下线后许可永久残留、同名重进继承旧状态。现下线时无条件清理
- **隐身草联机视觉状态冲突**：`Player.tick` 注入双端执行而规则值不同步到客户端，装了本 mod 的客户端会把服务端施加的隐身药水误判为"规则关闭清理残留"而本地移除。handler 现仅服务端处理
- **骑乘堆叠上限 off-by-one**：原实现实际允许"基座 + 上限+1 名乘客"。现与文档语义对齐：塔内玩家总数（含基座与新乘客）不得超过上限；`startRiding` 因竞争失败时命令如实返回失败而非成功
- **`RangedAttributeMixin` 无规则门控**：放行 SCALE 属性范围的注入原先全局生效，现仅在玩家缩放功能开启（`playerScale`/`playerScalePhysics` 非 false）时放行，全部关闭时回落原版 0.0625–16.0 夹紧
- **假人皮肤在新版 skinrestorer（2.8+ / 2.11 multiloader）下失效**：`SkinService.setSkinAsync` 的集合元素由 ServerPlayer 改为 `SkinTarget` 记录，反射签名因擦除不变、元素错传在其内部抛 ClassCastException（`Failed to set skin 'mojang:xxx'`，皮肤不生效）。现存在 `SkinTarget#of(ServerPlayer)` 时包装传入，旧版 skinrestorer 行为不变
- **1.21.11 的 `carpet_dependency` 笔误**：`>=1.4.100` 修正为 `>=1.4.193`（实际构建绑定版本）
- 4 个 mixin 源码目录名与 package 声明统一（`betterSnowBall`→`betterSnowball`、`playerhat`→`playerHat`、`fakePlayerDropStackModifiers`→`fakePlayerDropAll`、`realisticPlayerScale`→`playerScalePhysics`；更名规则时漏改目录），删除更名残留的空目录 `playerScaleModifiers`
- 类注释与实现对齐：`ScaleCommand` 类头"OP 调他人不受范围限制"（实际软边界约束所有人）、`PvpManager` 类头"禁言期间仅管理员可个别解禁"（实际锁定所有人，仅 @a 可解除）

- **隐身草离开草地后隐身永久残留**：归属标记误用 `Integer.MAX_VALUE` 作时长，而原版对有限时长每 tick 递减，标记在添加后第一 tick 即失效，移除分支永远无法命中。改用原版无限时长表示（`INFINITE_DURATION = -1`，不递减）作归属标记，并叠加"无粒子"位区分药水来源；规则中途关闭时的残留清理一并修复
- **TPP 配置原子写**：`config/carpet-pry-tpp.json` 改为先写临时文件再原子替换，避免写一半崩溃导致配置损坏；空配置文件不再抛 NPE；日志统一到 log4j
- **sendto `once` 无链接时的崩溃**：反馈文案含两个 `%s` 但只传了一个参数，会抛 `MissingFormatArgumentException`（随命令树重构一并修复）

### 移除
- `/scale` 在低版本上的"版本不支持"降级分支与 `carpetprimaryuan.command.scale.unsupported_version` 文案键（三语言），`/scale set|reset|info` 全版本行为一致
- 删除无引用的死类 `SleepUtil`；清理 en_us/zh_tw 中代码未引用的死键 `carpetprimaryuan.command.pvp.state_on/off`（三语言 key 集合对齐至 148 个）
- CI：移除 build.yml 中调用不存在任务 `runServerMixinAudit` 的死 step 与 `mixin_audit` 输入（启动级 mixin 验证由 mixin-boot-check.yml 承担），其版本矩阵补齐 1.21.11 / 26.1.2 / 26.2 三个最新节点
- 开发/CI 环境 fabric-loader 0.18.4 → 0.19.3：carpet 26.2 要求 loader >= 0.19.3，旧 loader 下 26.2 runServer 启动即被 FabricLoader 解析拒绝

### 文档
- **规则文档与命令文档对齐代码现状**：`playerScaleMin`/`playerScaleMax` 默认值 0.01/16.0 → **0.1/1.5**（补列默认选项 1.5）；/hat、/riding、/picking 权限说明修正为"规则开启时所有人可用，关闭时对所有人隐藏"（原"管理员总是可用"与 requires 实现不符）；/pvp 语法清单补漏 `/pvp list`；/tppset rename 别名上限 12 → 10 字符；/tpp 假人名构建说明改为按站点长度动态截断（原固定 10 字符为旧行为）；rules_en.md 的 fakePlayerDropAll/fakePlayerSendto 从 Bug Fixes 组移回 BOT 组（与代码分类及中文版一致）；`FakeplayersSkinMode` 旧名残留清理（表格外正文）；骑乘堆叠上限语义澄清；若干标点/转义/排版修正
- **Description.MD 全面重写**：规则数 16 → 24，清除全部已废弃规则名/命令名（FixXaeroLib、TppFakePlayer、/ride、/pickup 等），补齐 1.2.0 全部新功能（玩家缩放系列、dropall/sendto、/scale、/pvp），修正"所有规则默认关闭"与 `ridingPlayersClientInteract` 默认开启的矛盾
- **README Modrinth 链接统一**为 `carpet-pry-addition`（原同文档内两个 slug 混用）
- **GitHub / CurseForge 链接更正为新 slug**：README、README_en、Description.MD、fabric.mod.json 的 `sources` 统一指向 `brokeyuan/Carpet-PRY-Addition`，CurseForge 链接统一为 `carpet-pry-addition`（原均为改名前旧 slug）

- **规则文档补全**（docs/rules*.md）：补齐缺失的规则小节（中文补 fakePlayerSendto、peacefulPlayers；英文另补 fakePlayerDropStackModifiers），新增"玩家缩放"分组标题，规则总数更正为 24 条，文档版本号更新至 1.2.0，快速导航按正文重建
- **命令文档补全**（docs/commands*.md）：每个命令章节顶部标注**所属规则**（/tpp、/tppset→TppFakePlayer；/hat→playerhat；dropall→fakePlayerDropStackModifiers；/scale→playerScale；/riding→ridingPlayers；/picking→pickupPlayers；sendto→fakePlayerSendto；/pvp→peacefulPlayers），补齐缺失的 `/player sendto - 假人背包链接` 与 `/pvp - 和平的玩家` 两个完整章节（命令语法/权限模式/行为边界/使用示例），文档版本号更新至 1.2.0，快速导航重建
- README（中/英）、规则与命令文档同步移除"仅 1.21.5+"标记并修正属性引入版本表述；版本支持表移除"功能差异"列（全版本功能一致），并补充说明客户端可选安装对应的两项客户端功能（ridingPlayersClientAllowInteractions 与玩家缩放 FOV 补偿）
- **规则描述精简（三语言）**：10 条冗长规则的游戏内描述按「功能一句话 + 取值/模式枚举 + 命令形态」模板重写（TppFakePlayer、fakePlayerNameSuggestions、fakePlayerSkinMode、fakePlayerSkinSet、fakePlayerDropStackModifiers、fakePlayerSendto、playerScale、realisticPlayerScale、playerScaleLinkedEntities、peacefulPlayers）；完整命令语法与联动数值细节移交 docs 命令/规则文档，游戏内描述不再携带版本变更史（如"v1.1.8 起"），并修正 fakePlayerSkinSet 描述中的规则名拼写（FakeplayersSkinMode → fakePlayerSkinMode）；docs/rules*.md 描述行同步

### 重构（内部，行为不变）
- **`ServerTickScheduler`**：TppCommand / DropSlotScheduler / SendtoLinkManager 三处各自的 `END_SERVER_TICK` 惰性注册与双检锁样板收敛为中央调度器；服务器停止时统一放弃任务
- **`FrequencyCommandTree`**：dropall 与 sendto 的频率子命令树（once/continuous/interval/after/perTick/randomly/stop）与 `startMode` 参数拼装去重，新增第三个 `/player` 扩展命令的成本大幅降低
- **`FakePlayerSessionManager`**：/tpp 的假人操作 tick 状态机从 TppCommand 拆出独立管理（TppCommand 565 → 412 行）
- **`CommandSupport` / `ScheduleMode`**：命令层共享工具（目标解析、Tab 补全、管理员判定）与 tick 调度语义统一提取
- 两个 `PlayerCommandMixin`（dropall/sendto 注入）合并为单个 `PlayerCommandExtensionsMixin`，减少对 Carpet 字节码的注入面；同步 7 个版本专属 `mixins.json`
- 线程安全叙事统一：明确"所有状态仅服务器主线程访问"契约，移除无效的 `ConcurrentHashMap` 与防御性拷贝
- `ServerI18n` 删除忽略首参的误导重载（74 处调用点统一）；Tab 补全三处重复收敛为 `suggestMatching`（零分配前缀匹配）；`canHasTranslations` 按语言缓存；`CarpetRuleRegistrar` 反射按参数类型精确匹配并缓存，Carpet 签名变化时快速失败
- 删除 9 个版本专属 `TppCommand` 覆盖（约 4600 行），统一由根模板预处理提供

### 测试与 CI
- 新增 JUnit 单元测试源集（随最新版本子项目构建执行）：覆盖 `ScheduleMode` 调度语义、假人名取舍优先级（含 14/15/16 字符边界与中文）、sendto 原子转移算法（合并/限流/防刷）
- 版本专属 `mixins.json` 由单行紧凑格式统一为与根模板一致的多行格式（内容等价）

---

## [1.1.7] - 2026-08-19

### 新增规则

- **`realisticPlayerScale`**：更真实的玩家大小变——物理特性随体型（`minecraft:scale` 属性）全方位联动：移动/创造飞行速度在 scale<1.0 时用 √scale 曲线（+0.3 软保底）使缩小更平缓，scale≥1.0 线性；跳跃高度随体型平方根缩放（+0.5 保底）；台阶高度、方块与实体交互/攻击距离、摔落安全距离随体型线性缩放（+0.5 保底，确保极端缩小时仍可交互、不被秒杀）；客户端补偿缩小带来的 FOV 视野变窄（需客户端安装本模组，纯服务端则跳过补偿）。瞬态属性修改器实现，不写入存档，规则关闭或体型恢复 1.0 后立即清理；需配合 `playerScaleModifiers` 使用，仅 1.21.5+ 支持

### 修复

- **`FixBluemap`**：为假人手动触发 Fabric `ServerPlayConnectionEvents.JOIN` 事件时改传 no-op `PacketSender`（动态代理）而非 `null`，修复 Kotlin 编写的监听器（如 penguin、Takeitout）因非空参数校验抛出 NullPointerException，导致假人创建失败（`createFake delayed task error`）的问题
- **旧版本 mixin 注册缺失**：补齐 1.21~1.21.10 版本专属 `carpet-primaryuan.mixins.json` 遗漏的 10 条注册——骑乘玩家/捡起玩家（`entitiesRidingPlayers` 系列 4 条，含客户端 `ProjectileUtilMixin`）、隐身草、Unicode 参数支持、`FixXaeroLib`、`FixBluemap`、玩家帽子、更好的雪球此前在这些版本上静默失效；同时为 3 处对旧版本字节码敏感的注入点（`EntityMixin` 的 canSerialize 包装、`ServerPlayerMixin` 的 setGameMode 注入、客户端 `ProjectileUtilMixin`）添加 `require = 0` 防御，目标缺失时静默跳过而非崩溃
- **清理死代码**：移除 26.2 目录中从未注册的孤儿 `RideCommandMixin`（v1.1.6 重命名 `/ride` → `/riding` 时遗留）
- **`/tppset spawn` 在 1.21.3~1.21.5 无实际效果**：这三个版本的 `setSpawnFakePlayer` 覆盖缺失 `/player <name> spawn` 命令调用，只发送提示后 kill 一个从未生成的假人；已按 1.21.8 实现补回 spawn 调用
- **移除冗余版本覆盖**：删除 26.1.2/26.2 中与根模板逐字相同的 `InvisibleInTallGrassHandler`（×2）与 `EntitiesRidingPlayersHandler` 覆盖文件，这些版本自动继承根模板，行为无变化

### CI

- 新增 Mixin Boot Check 工作流（`workflow_dispatch` 手动触发）：矩阵启动 1.21~1.21.10 各版本服务端，检测 mixin 应用错误与服务端启动状态

### 文档

- 规则文档（`docs/rules.md` / `docs/rules_en.md`）补录 5 条规则（`fakePlayerDropStackModifiers`、`playerScaleModifiers`、`playerScaleMin`、`playerScaleMax`、`realisticPlayerScale`）至 21 条，`playerScaleModifiers` 附模式说明表，`sleepingDuringTheDay` 补版本兼容说明
- 中文 `README.md` 补全全部规则分组表与命令表（原仅有漏洞修复 2 条）；两语言 README 简介计数修正（21 条规则 / 6 个命令）；各文档版本号引用同步 1.1.7

---

## [1.1.6] - 2026-08-12

相较于 v1.1.5，本次更新包含 47 次提交，主要新增 3 个规则、2 个独立命令，并完成多版本兼容性重构与若干修复。

### 新增规则

- **`playerScaleModifiers`**：为 Player 注册 `minecraft:scale` 属性，新增统一三层子命令 `/scale set|reset|info`。支持四种模式：
  - `false`：关闭命令（默认）
  - `self`：所有人都只能调自己（无论 OP）
  - `true`：玩家仅可调自己，管理员可调任意玩家
  - `everyone`：所有人可调任意玩家
  - Tab 补全根据模式和权限动态过滤；范围受 `playerScaleMin`（默认 0.1）/ `playerScaleMax`（默认 10.0）限制；仅 1.21.5+ 版本支持
- **`fakePlayerDropStackModifiers`**：让假人持续丢出整组物品，任务在物品栏为空时仍保留，避免频繁重启

### 新增 / 重构命令

- **`/scale`**：玩家大小调节命令（结构：`set <value> [player]` / `reset [player]` / `info [player]`）
- **`/dropall`**：独立命令让假人清空物品栏（v3 重构，原 dropStack 路径）
- **`/picking`**：原 `/pickup` 重命名，保持命名一致性
- **`/riding`**：原 `/ride` 重命名以解决与原版 ride 命令冲突；同时移除 `RideCommandMixin` 恢复原版行为

### 修复

- **多版本兼容性**：
  - 1.21~1.21.4 不存在 `Attributes.SCALE`，通过 `//#if MC >= 12105` 预处理指令隔离，低版本提示不支持
  - 1.21.10 以下使用 `GameProfile.getName()`，1.21.11+ 使用 `name()`
  - 1.21.10 以下使用 `source.hasPermission(4)`，1.21.11+ 使用 `Commands.LEVEL_OWNERS.check`
  - 1.21.3+ 使用 `hurtServer(ServerLevel, DamageSource, float)`，1.21/1.21.1 保留 `hurt(DamageSource, float)`
- **命令树注册**：dropall 命令改为无条件注册并在执行时检查规则，避免规则切换后命令不可见
- **异常处理**：`ScaleCommand.applyScale` 中 `getPlayerOrException()` 用 try-catch 包裹，避免 `CommandSyntaxException` 向上传播
- **类型转换**：`Component` → `String` 使用 `.getString()`，修复 `ServerI18n.tr()` 返回类型不匹配
- **Mixin 注入**：`EntityPlayerActionPack.stopAll()` 改用 `CallbackInfoReturnable` 正确拦截返回值
- **预处理标记**：修正 `//$$` 标记方向，确保 main project 多版本预处理生效

### 重构

- **命令结构统一**：`/scale` 采用"动作 + 参数 + 可选目标"三层结构，避免参数定义重复
- **多版本隔离**：版本专属 `mixin.json` + 预处理指令组合，确保低版本不注册包含 `SCALE` 字段的 Mixin
- **RuleObserver**：规则切换时调用 `CommandHelper.notifyPlayersCommandsChanged` 动态刷新命令树，无需重登
- **Tab 补全策略**：根据规则模式（self / true / everyone）和玩家权限动态过滤可见玩家列表

### 文档

- 新增 `commands_en.md` 英文命令文档
- 重写 `README.md` / `README_en.md`：新增介绍、特性、安装、致谢章节，添加 Modrinth/CurseForge 徽章
- 添加 `Ivan-Carpet-Addition`（fakePlayerNameSuggestions）与 `Liuyue_awa / Carpet-Igny-Addition` 致谢
- 完善 `docs/Description.MD` 规则计数（16 rules）与 `FixBluemap` 条目

### CI

- 升级 GitHub Actions 依赖：
  - `gradle/actions/setup-gradle` v6 → v6.2.0
  - `actions/setup-python` v6 → v7
  - `softprops/action-gh-release` v2 → v3

---

## [1.1.5] - 2026-07-20

初始公开发布版本。
