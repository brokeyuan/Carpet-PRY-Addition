# Changelog

All notable changes to **Carpet-PRY-Addition** are documented in this file.

## [未发布]

### 新增

- **玩家缩放全规则前移支持 1.21~1.21.4**：`playerScale`（含 `/scale` 命令与 FOV 补偿）、`realisticPlayerScale`（四模式物理联动）、`playerScaleLinkedEntities` 三条规则及其 7 个 mixin 此前仅注册于 1.21.5+，现覆盖全部受支持版本。前移依据：`minecraft:scale` 属性与各联动属性（移速/跳跃/台阶/交互距离/摔落安全距离/重力）实际自 1.20.5（快照 23w51a）起即由原版提供并默认存在于所有生物（含玩家）的属性表——此前"SCALE 属性 1.21.5 才加入原版"的注释与文档表述系误记，已一并修正。逐版本映射后字节码核实：`Attributes.SCALE` 等字段 1.21~1.21.4 均为 `Holder<Attribute>` 且签名与 1.21.5+ 一致；`RangedAttribute.sanitizeValue(double)`、`ServerPlayerGameMode.useItem/useItemOn`、`ServerLevel.addFreshEntity` 全版本一致；`AbstractClientPlayer.getFieldOfViewModifier` 在 1.21~1.21.1 为无参签名（FOV 补偿处理器相应按版本分支捕获参数）；鞘翅联动注入点按版本分支——1.21.3+ 注入 `travelFallFlying`，1.21~1.21.1 无此拆分、改注入 `travel` 的 move 调用点并以 `isFallFlying()` 甄别（travel 内 move 为各移动分支共用）。1.21 服务端实机启动验证通过（Done + 0 mixin 错误），各版本 `mixins.json` 已注册
- `playerScale` 的 `PlayerMixin` 注释澄清：scale 属性 1.20.5+ 已在原版默认属性表中，该 mixin 的注册为幂等兜底保险

### 移除

- `/scale` 在低版本上的"版本不支持"降级分支与 `carpetprimaryuan.command.scale.unsupported_version` 文案键（三语言），`/scale set|reset|info` 全版本行为一致

### 文档

- README（中/英）、规则与命令文档同步移除"仅 1.21.5+"标记并修正属性引入版本表述；版本支持矩阵更新为全版本功能一致（仅白日做梦白天上床差异保留）

## [1.2.0] - 2026-09-12

### 新增

- **`playerScaleLinkedEntities` 玩家大小变联动实体**：玩家使用物品直接生成的生物实体继承玩家当前体型（写入 minecraft:scale 基础值快照）——0.5 的玩家放出的盔甲架也是 0.5 大小，刷怪蛋生物、摆出的铁/雪/铜傀儡同理（放南瓜的构造生成同步发生在使用漏斗内；幼崽等原有修饰符在此基础上叠加比例）。实现上经 `ServerPlayerGameMode` 的 useItem/useItemOn 漏斗记录操作玩家，在 `ServerLevel.addFreshEntity` 收口处写入体型（仅 1.21.5+ 注册）。仅覆盖有 scale 属性的生物实体，不触碰渲染与碰撞箱；投掷物、掉落物、物品展示框、船、矿车、TNT 等非生物实体不联动；发射器、刷怪笼等非玩家来源不联动；写入为快照不随玩家后续变化，玩家体型为 1.0 时不做任何改动
- **`realisticPlayerScale` 物理联动模式化**：规则由布尔改为 `false / true / safety / strict` 四模式，覆盖三种真实化取向：
  - `true`（平缓）：移动/飞行/鞘翅烟花速度、跳跃、台阶、交互距离、摔落安全距离均按 √scale 曲线缩放（16 倍体型移速 4 倍而非 16 倍），无保底
  - `safety`（平缓+保底，推荐）：曲线同 true，为小体型（scale<1.0）加保底——速度/飞行/重力 0.3×、跳跃/台阶/交互/摔落 0.5×，极端缩小（如 0.1）仍可玩
  - `strict`（严格等比）：速度/台阶/交互/摔落严格 ×scale；跳跃初速 ×scale^0.75，与重力 √scale 配套后跳高与体型等比放大（2 倍体型跳 2 倍高、16 倍体型跳 16 倍高）；无任何保底，完全按几何比例行动
  - 重力在三种模式下均 ×√scale（加速度量按平方根联动；线性缩放会使 16 倍体型终端速度约 62 格/tick 失控）
- **`realisticPlayerScale` 鞘翅滑翔/烟花联动**：新增鞘翅位移缩放（`LivingEntityMixin`，仅 1.21.5+ 注册）——鞘翅滑翔中 `move()` 的位移按移动速度联动因子等比缩放（因子随模式变化：true/safety 为 √scale、strict 为线性 scale）。原版滑翔位移、烟花加速的极速（速度被拉向视线方向 ×1.5 的固定控制器）与转向项均为硬编码、与体型无关；现大体型滑翔/烟花极速随体型放大、转向半径更大（几何相似），小体型更慢更飘。只缩放传给 move 的位移、不改写存储速度，物理无正反馈、任意体型稳定收敛；生效判定与 FOV 补偿一致（以移动速度属性上的 scale_speed 修改器为准），服务端（含假人）与客户端本地预测一致；已知取舍：鞘翅撞墙伤害按存储速度（原版量级）计算，不随位移缩放
- **`realisticPlayerScale` 重力联动（下落速度）**：下落加速度随体型平方根缩放（×√scale；仅 safety 模式对小体型 0.3 保底）——大体型下落明显更快（scale 4→2×、16→4×），缩小更飘逸（0.25→0.5×）。创造飞行时原版会以飞行前竖直速度覆盖重力、重力属性无效；鞘翅滑翔的重力项则随重力属性生效（此前文档称鞘翅不受重力影响，与 1.21.5+ 实际实现不符，已修正），滑翔/烟花位移另由鞘翅联动 mixin 缩放

### 行为变更

- **`realisticPlayerScale` 类型由 `boolean` 改为字符串选项**：`false/true/safety/strict`；旧配置中的 `true` 直接映射为新 `true`（平缓）模式，原"小体型 √+保底、大体型线性"的混合行为由 `safety`（缩小场景）与 `strict`（放大场景）分别承接
- **`/scale` 硬边界改为仅要求大于 0**：value 参数不再设固定上下限（原对齐原版属性的 0.0625–16.0），任何大于 0 的有限值均可直接设置生效；`RangedAttributeMixin` 相应放行 SCALE 属性的任意有限正值，非正值/非有限值（NaN/Infinity）回落原版夹紧兜底，服务端命令反馈值与实际生效值一致。注意：纯原版客户端（未安装本模组）在超出原版范围（<0.0625 或 >16.0）时显示仍会被客户端侧夹紧，需客户端安装本模组；极端缩放值可能引发生物碰撞箱与渲染异常，请自行斟酌
- **`/scale` 软边界调整**：`playerScaleMin/Max` 软边界统一约束所有玩家（原管理员"调他人"不受限，现含管理员在内均受限；管理员可通过 `/carpet` 修改这两条规则调整边界本身）；软边界默认值由 0.1–10.0 调整为 **0.01–16.0**（下限与硬边界对齐，上限对齐原版属性声明范围）
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

- **隐身草离开草地后隐身永久残留**：归属标记误用 `Integer.MAX_VALUE` 作时长，而原版对有限时长每 tick 递减，标记在添加后第一 tick 即失效，移除分支永远无法命中。改用原版无限时长表示（`INFINITE_DURATION = -1`，不递减）作归属标记，并叠加"无粒子"位区分药水来源；规则中途关闭时的残留清理一并修复
- **TPP 配置原子写**：`config/carpet-pry-tpp.json` 改为先写临时文件再原子替换，避免写一半崩溃导致配置损坏；空配置文件不再抛 NPE；日志统一到 log4j
- **sendto `once` 无链接时的崩溃**：反馈文案含两个 `%s` 但只传了一个参数，会抛 `MissingFormatArgumentException`（随命令树重构一并修复）

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
