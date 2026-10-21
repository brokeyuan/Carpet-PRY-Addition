# Changelog

## 未发布

### 新增

- 玩家头部皮肤（`playerHeadSkin`）：真人与假人戴玩家头颅采用其全身皮肤，摘下或关闭规则恢复原皮肤，纯服务端

### 修复

- `dropall once` 与无参 `dropall` 一次清空背包，修复只丢第一组的问题；定时模式仍每次丢一组
- 文档版本、keep 生命周期、字幕参数和权限说明对齐实现，补齐功能说明并精简文案
- `fixStandingOnAir`（仅 26.3）：回移植 26.4 Snapshot 1 的方块重发时序修复，并额外取消站空 INFO 日志；保留方块重发与 200 tick 冷却

## [1.3.0] - 2026-10-07

### 新增

- 假人脑子（`fakePlayerBrain`）：新增 19 种生物行为模式，支持三语模式名、状态后缀和本次服务器运行内的 `keep` 重上保持。移动和战斗使用玩家自身能力，不生成额外实体或物品；26.1.2+ 支持长矛
- 摸摸头（`patPatPlayers`）：右键其他真人玩家的头部抚摸，对方头顶与眼前冒爱心、轻响从发起者方向传来、被摸者随节奏往复蹲起。`true` 随手可摸，`sneak` 按住潜行才摸；连点加速冷却；`/patnod on|off` 按玩家拒绝被摸，持久化于 config/carpet-pry-patnod.json
- 谁在叫我（`whoCalledMe`）：聊天里出现其他玩家名字时，被点名者连响三声提示音、弹出 title 显示原文，名字在聊天框按规则色高亮。子串匹配、最长名优先（Tim/Timy 同时在场时 "timy" 只提醒 Timy）；`true`=子串匹配，`mention`=仅 @名字 触发；音效五种可选（默认经验球叮声），高亮 16 色 + rainbow 可选（默认水蓝）；自己提到自己同样触发。正版签名环境下被高亮的文本会带"无法验证"标记，离线服不受影响
- 米塔字幕（`textAnimation`）：`/text @a|玩家名 <消息>[|参数]` 播放逐字弹出、停留后坠落的白色字幕，支持色码与感叹号放大。前 12 名接收者显示实体字幕，其余使用 actionbar；每人 10 秒冷却，全服最多 8 条并发
- 穿透点击（`clickThrough`）：右键墙告示牌/挂墙木牌/墙横幅/展示框/画直接打开背后贴挂的容器，潜行保持原版交互；背后无容器、被猫占或打不开时走原版。纯服务端实现，客户端无需安装；装有独立模组 clickthrough server 时自动让路
- 更多种类的末地水晶（`moreEndCrystalTypes`）：允许把末地水晶放在哭泣的黑曜石上，`invulnerable` 选项放出无敌水晶
- 适配 Minecraft 26.3（fabric-loader 全局升至 0.19.5）

### 变更

- 骑乘/捡起交互统一（`ridingPlayers` / `pickupPlayers`）：主手持不死图腾按点击部位分流——点头部骑到对方头上、点腿脚把对方捡起、点躯干无交互；不再要求副手金胡萝卜
- `/pvp`、`/riding`、`/picking`、`/patnod` 无参执行改为翻转自己的状态（显式 on|off 保留）
- `/tpp` 显式声明对 Carpet TIS Addition 的软依赖（依赖其 /player rejoin），未安装时明确提示

### 修复

- 旧 loader（≤0.19.3）加载 1.21.11+/26.x 产物时 ServerPlayer mixin 转换崩溃，产物内捆绑修复版 MixinExtras 0.5.5
- 假人皮肤：summon 模式、控制台执行、真人名假人等多条路径静默显示默认皮肤；观战者先见旧皮肤再闪变；假人皮肤被持久化、污染同名真人玩家（真人名假人不再换肤，换肤不落库）
- 玩家塔跨维度整塔散伙：原版跨维度传送不搬运乘客，现按原版载具模式补齐，塔随基座整体过传送门，/tp 同样随行
- 假人脑：近战对假人零伤害；被打不反击、原地闪断挨打；跑着跑着不动、莫名丢目标、索敌迟钝；骷髅/掠夺者不对铁傀儡和狼反应；1.21~1.21.10 产物漏注册脑 mixin、完全没有脑子；攻击哑火、拉弓不打、无挥手动画；keep 假人死后重生丢脑子丢后缀
- `/text`：会话异常后并发额度不回收，累计 8 次后命令不可用；头顶有方块（室内/屋檐）时字幕无下坠原地消散；淡出透明度回绕，无渐隐
- `/text` 等命令树无视规则开关常开（requires 谓词对 boolean 规则恒真）
- `/player <真人名> dropall` 缺假人校验，可被任意玩家清空真人背包
- 摸摸头在 `pickupPlayers` 开启时不可达；与骑乘玩家双触发，刚骑上去就被蹲下脉冲卸下来
- 谁在叫我 title 只高亮首个名字；聊天含 İ 等特殊字符时高亮错位
- `betterSnowball` 击退绕过无敌帧成倍叠加，现仅伤害生效时施加
- `entitiesRidingPlayers`：旁观者可骑乘与被骑乘；交互连点刷字幕（加 10 tick 冷却）
- 活塞推动的末地水晶客户端位置永久不同步（新规则 `fixEndCrystalSync`）
- 与 Axiom 冲突：本模组把 Axiom 调整的创造飞行速度打回 100%，现第三方自定义值不再改写（[#8](https://github.com/brokeyuan/Carpet-PRY-Addition/issues/8)）
- `/tppset spawn` 不检查生成结果即排程下线，同名假人在线时误踢既有假人
- 草丛隐身误删管理员授予的同类隐身；dropall 反馈文案参数错位
- 穿透点击：潜影盒开盖动画期间可穿透打开；补打开统计与猪灵仇恨
- 中央调度器：一次性任务退化为每 tick 重复；任务异常上抛崩服，改逐任务隔离

### 移除

- 红包（`redPacket`）：暂不提供

## [1.2.2] - 2026-09-14

与 1.2.1 代码相同，为验证 CurseForge 凭证重新走发布流水线。

## [1.2.1] - 2026-09-14

与 1.2.0 代码相同，为补齐 GitHub Release 资产重新走发布流水线；release 矩阵同时关闭 fail-fast，单版本发布失败不再拖垮其余版本。

## [1.2.0] - 2026-09-14

### 新增

- `peacefulPlayers` 和平的玩家 + `/pvp`：按玩家开关 PVP，关闭者不能攻击玩家、也不受玩家伤害（自伤不限），被拦下的攻击有提示音。`/pvp on|off [玩家|@a]`、`/pvp list`，状态持久化于 config/carpet-pry-pvp.json，新加入玩家跟随全服默认状态
- `playerScaleLinkedEntities`：玩家使用物品直接生成的生物继承玩家当前体型（刷怪蛋生物、铁傀儡/雪傀儡/铜傀儡等；仅 1.21.5+）
- `realisticPlayerScale` 四模式物理联动（`false/true/safety/strict`）：移动/飞行/跳跃/台阶/交互距离/摔落/重力随体型联动。`true` 平缓（速度走 √scale 曲线）；`safety` 平缓并为小体型加保底；`strict` 严格等比、跳高与体型等比放大；重力一律 ×√scale。鞘翅滑翔与烟花位移随体型缩放
- 玩家缩放三规则与白日做梦补齐 1.21~1.21.4 实现，全部受支持版本功能一致

### 变更

- 10 条规则更名统一 lowerCamelCase，`carpet.conf` 旧值失效需重设：TppFakePlayer→fakePlayerTpp、FixXaeroLib→fixXaeroLib、FixBluemap→fixBlueMap、playerhat→playerHat、betterSnowBall→betterSnowball、fakePlayerDropStackModifiers→fakePlayerDropAll、realisticPlayerScale→playerScalePhysics、ridingPlayersPickUpLimit→ridingPlayersStackLimit、ridingPlayersDismountOnGameModeChange→ridingPlayersAutoDismount、ridingPlayersClientAllowInteractions→ridingPlayersClientInteract
- `playerScaleModifiers` 更名 `playerScale`，FOV 补偿移入该规则（触发条件不变）
- `/scale` 硬边界改为任意大于 0 的有限值；纯原版客户端在超出 0.0625–16 时显示仍被客户端夹紧。软边界 `playerScaleMin/Max` 默认 0.1–1.5，含管理员在内所有人受限
- 站点假人名上限放宽至 16 字符（站点名完整保留优先）；`/tppset set` 站点名 ≤16 字符、`rename` 别名 ≤10 字符
- `fakePlayerSendto` 默认改为关闭；dropall/sendto 在规则关闭时隐藏命令；约 12 处失败提示改为红色失败回执并接入三语

### 修复

- sendto 多源链接时遍历与清理竞态致服务器崩溃，改快照遍历
- `betterSnowball` 单人游戏客户端崩溃（击退仅由服务端施加）
- 白日做梦把昼夜循环拨回夜晚：夜间入睡被误接管，现仅白天开始的睡眠由规则接管
- 26.2 子项目无法编译（getDayTime 更名漏同步）
- 单 JVM 内重启服务器后 dropall/sendto/tpp 调度静默停摆
- 只开 `pickupPlayers` 的服务器，骑乘/捡起许可下线后不清理
- 隐身草：装了本模组的客户端误删服务端施加的隐身；离开草地后隐身永久残留
- 骑乘堆叠上限 off-by-one，多容纳一人
- 假人皮肤在 skinrestorer 2.8+ 下失效
- SCALE 属性范围放行未挂规则门控，全部关闭时回落原版夹紧
- TPP 配置改原子写；sendto once 无链接时崩溃

### 移除

- `/scale` 低版本"版本不支持"降级分支，全版本行为一致；清理死类与三语死键

### 文档

- 规则/命令文档对齐代码现状（默认值、权限、语法、版本标记）；Description.MD 重写（规则数 16→24）；仓库链接 slug 统一；10 条冗长规则的游戏内描述三语精简

### 内部

- 调度、命令树、会话管理收敛去重；两个 PlayerCommandMixin 合并为一个；删除 9 个版本专属 TppCommand 覆盖（约 4600 行）；新增 JUnit 测试源集；CI 移除死 step、版本矩阵补齐；开发环境 fabric-loader 升 0.19.3

## [1.1.7] - 2026-08-19

### 新增

- `realisticPlayerScale`：物理特性随体型（minecraft:scale）联动——小体型速度走 √scale 平缓曲线，跳跃/台阶/交互距离/摔落安全距离随体型缩放并带保底；客户端 FOV 补偿（需客户端安装本模组）；仅 1.21.5+

### 修复

- `FixBluemap`：假人加入时给 Fabric JOIN 事件传 no-op PacketSender，修复 Kotlin 监听器因非空校验抛异常、假人创建失败
- 1.21~1.21.10 版本漏注册 10 条 mixin，骑乘玩家/捡起玩家/隐身草/Unicode 参数/fixXaeroLib/fixBlueMap/玩家帽子/更好的雪球在这些版本静默失效；对旧版本字节码敏感的注入点补 require=0
- `/tppset spawn` 在 1.21.3~1.21.5 无实际效果（漏调 spawn 命令）
- 清理孤儿 RideCommandMixin 与冗余版本覆盖

### 文档

- 规则文档补至 21 条；中文 README 补全规则/命令表；两语言简介计数修正

### 内部

- 新增 Mixin Boot Check 工作流（1.21~1.21.10 各版本启动验证）

## [1.1.6] - 2026-08-12

### 新增

- `playerScaleModifiers`：为玩家注册 minecraft:scale 属性，新增 `/scale set|reset|info`（权限模式 false/self/true/everyone，Tab 补全随权限过滤，范围受 playerScaleMin/Max 限制；仅 1.21.5+）
- `fakePlayerDropStackModifiers`：假人持续丢出整组物品，物品栏空后任务保留
- `/dropall` 独立命令；`/ride` 更名 `/riding`（避让原版命令）、`/pickup` 更名 `/picking`

### 修复

- 多版本兼容：SCALE 属性按预处理隔离（1.21~1.21.4 提示不支持），GameProfile.getName、权限判定、hurtServer 等按版本分叉
- 命令树注册、异常处理、类型转换、mixin 注入等若干小修

### 文档

- 新增英文命令文档；README 重写（介绍/特性/安装/致谢），补 Ivan-Carpet-Addition 与 Carpet-Igny-Addition 致谢

### 内部

- GitHub Actions 依赖升级

## [1.1.5] - 2026-07-20

初始公开发布版本。
