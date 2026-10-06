# Changelog

All notable changes to **Carpet-PRY-Addition** are documented in this file.

## 未发布

### 功能
- **米塔字幕节奏放慢（`textAnimation`）**：打字由 1 字/tick 改为 2 tick 一字（字幕会话与大服降级 actionbar 打字机同口径），停留默认 40→80 tick（2s→4s）
- **谁在叫我自定义扩展（`whoCalledMeSound`/`whoCalledMeHighlight`）**：音效开关改选项——`ding`（经验球三连叮，默认）/`levelup`/`bell`/`pling`/`hit` 五种提示音（字段实名与类型 11 版本 javap 核实一致，`pling` 本身即 Holder 直接传包），`false`=无音效，旧值 `true` 兼容等同 `ding`，三连结构与音高阶梯全音效共用；高亮色默认 `gold`→`aqua`，新增 16 原版色全量与 `rainbow`（名字逐字符循环彩虹渐变，聊天框与 title 同步、按码点切分防代理对劈开），`false`=不改变颜色（`none` 已取消静默别名，按未知值回落默认水蓝）；title 超长消息按显示宽度截断（半角 1、CJK/全角 2 单位，上限 40 目测初值）——首个命中名字完整保留（被切时以名字为中心取窗口、被切侧补 `…`），原版 title 单行不换行、超宽会溢出屏幕外
- **穿透点击（`clickThrough`）**：右键墙告示牌/挂墙木牌/墙横幅/展示框/发光展示框/画直接打开背后贴挂的容器（箱/木桶/潜影盒/漏斗/发射器等一切原版能右键打开的），不潜行=穿透、潜行=原版交互（编辑/染色告示牌、旋转展示框/放物品），背后无容器同样走原版——潜行即误穿透逃生门；打开走 `BlockState.getMenuProvider` 与原版右键同一入口，猫占/上方被堵等"箱子打不开"校验天然一致；服务端 Fabric API UseBlock/UseEntity 事件实现，零 mixin，原版客户端即用（参考 clickthrough-server 的服务端路线与 gbl/ClickThrough 的行为语义，代码自写零搬运；装独立服务端模组 clickthrough_server 时整体让路防双开，客户端版 gbl 改准星发普通右键包、天然兼容）；立式告示牌/站立横幅无水平朝向不处理，旁观者不处理
- **字幕打字期跟随视角 + 感叹号增益去封顶（`textAnimation`）**：打字期间整组跟随玩家实时视角——锚点按玩家当前位置与朝向每 tick 重算，已生成字形同步平移转向且整行保持刚性（玩家未动零包，变更检测；转向/位移走 2 tick 插值），打字完成即冻结在世界坐标、照常停留坠落；结尾感叹号增益去掉 ×2.5 封顶（越多字越大），新增距离增益每个 ×1.15（越多越远，防大字怼脸；增速低于字号增益保持"更大"观感）
- **红包手气王、价值表与体验包（`redPacket`）**：领完/过期时结算——**手气王**全服广播（仅拼手气/口令两类随机切分类型、且**红包只含单种物品**（混合物品没有可比的手气；`hasSingleItemType()` 空份额不计种类），按**稀有度**计价最大者当选、并列取先领取、空份额不参评——稀有度五档代码内置（常见/铁器/钻石级/合金级/鞘翅级），档间跨度 10^9 大于单红包物理物品上限（45 格 × 64 = 2880），结构性保证 **1 件高稀有 > 任意数量低稀有**（用户点名语义：1 钻石 > 无限泥土，含 2880 泥土对 1 钻石的单测）；同档内按价值表 × 数量排序，config/carpet-pry-values.json 可自定义同档内单件价值（未列出按 1/件，只读不回写、改后重启生效）；`claimed` 集合改 LinkedHashSet 保领取顺序与份额下标一一对应，判定抽为 `luckKingIndex(价值函数)` 纯函数并配 6 项 JUnit 契约测试（直调真实 itemValue）；发送者收到**领取明细**（在线时单行摘要 + 悬浮展开全部"名字：物品"，上限 50 行防刷屏，领取者离线后名字仍可查——领取时同步记录）；普通（平均分配）与专属（单人）类型无手气语义，仅发明细。**体验包**：专属红包对目标玩家以金色专属样式呈现并附提示音；`/redpacket list` 列出本人进行中的红包；`/redpacket again` 按上次成功发放配置重开投放 GUI；有效期截止前 30 秒广播提醒（剩余份数 > 0）；投放确认时物品总量少于份数提示"部分份额将是空的"（不阻断）；停服时未过期红包的未领份额退还发送者（消除重启窗口物品损失，退不掉的记日志）
- **个人开关命令 toggle 化**：`/pvp`、`/riding`、`/picking`、`/patnod`、`/redpacket mute` 无参执行 = 翻转自己的状态，回执即新状态（此前 /pvp 与 /patnod 无参是查看状态、/riding 与 /picking 无参无行为）——最高频的"切换我自己"一条命令完成；显式 `on|off`（及 /pvp 的跨玩家/@a、/redpacket unmute）全部保留给明确语义场景；跨玩家与全服操作不做 toggle（混合状态无明确目标态）。回执复用既有 on/off 文案，无新增 lang 键
- **/text 定向目标参数与发送冷却（`textAnimation`）**：命令改为**目标前缀必填**——`/text @a <文本>`（全服广播）或 `/text <玩家名> <文本>`（定向单个在线玩家，按当前在线表不区分大小写匹配），无旧式无目标写法（首词非目标时报 need_target 错误，含其它 `@` 开头写法）；单 greedy 参数处理器内剥离首个空格切分（EntityArgument 独立分支方案被否：其 parse 不查在线、执行期对非在线名抛 "No player was found" 且 Brigadier 不回落其它分支，旧式中文消息首词非玩家名即报废——RCON 实证）。控制台同样可用，定向后只对该玩家播放、生成点仍以其位置朝向为基准；消息以在线玩家名开头且想全服广播时需显式 `@a`（歧义如实注明）。**每人 10 秒发送冷却**（控制台不受限，成功发起才计时），防单人连发刷满全服屏幕；冷却命中提示剩余秒数
- **红包领取反馈、专属直达与缺省值（`redPacket`）**：**领取全服广播**——每有人领取，全服收到"xx 抢到了 yy 的红包（第 n/m 份）：物品"（社交展示；取代原发送者私有回执；退订者不收；专属单份无份序）；**专属直达** `/redpacket [份数] @玩家 [祝福语]` 跳过类型选择与头像页直接投放（大服在线玩家超过头像页 54 格时的唯一入口；@ 前缀在处理器内剥离——Brigadier 先注册的 greedy 分支恒先匹配，独立 target 参数分支不可达；目标按名不区分大小写匹配在线玩家、不能是自己）；**缺省值**——`/redpacket` 裸命令份数默认 = 当前在线人数（不含假人）、祝福语默认 = 恭喜发财（lang 键随全局语言），`/redpacket 15` 仅指定份数，份数与祝福语均可省略；**`/redpacket mute|unmute` 个人退订红包广播**（含领完/过期状态广播，退订后自然也无法领取；按玩家名持久化于 config/carpet-pry-redpacket.json，UTF-8+原子写，模式同 PvpManager）；发送冷却提示带剩余秒数；口令错误提示带正确口令字数；红包卡片悬浮补玩法提示（每人限领一份、口令红包聊天框输入）
- **红包/谁在叫我体验优化批次（`redPacket` / `whoCalledMe`，全量审查驱动）**：红包——领完/过期时对全服广播灰色状态行，领完不再立即删除而是标记保留（后续点击能收到"已被领完"反馈，修复领完静默）；断线时投放 GUI 内的物品自动进离线暂存（重进补发，修复断线物品蒸发；drain 限玩家物品区防图标流出）；发出与领取提示音（经验球音，与摸摸头同款已验证配方）；广播祝福语支持 & 色码（与 /text 心智统一，复用新增的公共 ColorText 解析器，textAnimation 同步接入）；口令红包点击链接后 60 秒内打错口令会得到"口令不正确"提示（普通聊天不受影响）。谁在叫我——主规则改 string：`true`（子串+最长名优先）/ `mention`（仅 @名字 触发，高亮 @+名字 整体）；新增子规则 `whoCalledMeSound`（三声提示音开关）与 `whoCalledMeHighlight`（高亮色 gold/yellow/aqua/green/red/none，title 与聊天框联动，none 时聊天装饰器整体透传）。字幕——options 数值 clamp（distance 1-10 / scale 0.5-4 / spacing 0.05-2 / hold 20-600，堵巨型字符与长占广播上限的滥用；clamp 仅作用于显式传入值，未传时 null 走默认路径——初版对所有 local clamp 在未传值时拆箱 NPE 报 "unexpected error"，本地 RCON 实证修复）；大服降级：单条广播直接生成字幕的玩家超过 12 人时，超出部分改用 actionbar 打字机（零实体纯包，打完保持期每 10 tick 重发对抗自动淡出）；玩家死亡/断线/换维度时其字幕会话立即终止；坠落期 setPos 每 2 tick 一次（客户端插值平滑不变，多人包量减半）
- **红包三缺陷修复（`redPacket`，生产实证）**：①**专属红包完全不可用**——确认发放未携带两次点击选中的目标（`createAndBroadcast` 恒传 null target），任何人（含目标玩家）点击广播领取即 NPE 静默失败，红包 100% 过期退回；修复为确认时传递 `session.targetId`。②**GUI 图标无限复制 + 玩家物品蒸发**——保护原做在 Container 层（removeItem/setItem/canPlaceItem 拦截），而菜单交互在 Slot 层直接读写绕过全部拦截：shift-click 图标（`ChestMenu.quickMoveStack` 源槽清空被 `setItem` 拦截吞掉、icon 原地保留且副本进背包）每点一次复制一个红潜影盒/按钮图标/玩家头颅；玩家物品 shift-click 进保护空格（`moveItemStackTo` 目标 set 被拦、源缩减不拦）直接蒸发——修复为自定义 `RedPacketMenu`（Slot 层保护：保护格 `mayPlace/mayPickup=false`+`removeItem` 返空+`set` 空操作）+ `clicked` 覆写对保护格只放行左键转回调、shift-click/数字键交换/拖拽/扔出/克隆整体屏蔽（26.1.2 起 `ClickType` 改名 `ContainerInput` 按宏分叉）；quickMoveStack 自写（玩家物品只能进投放区、容器格 shift-click 收背包）。③**giveItems 部分放置物品蒸发**——`Inventory.add` 返回值语义为"至少放入一件"（字节码 `return count < countBefore`）且就地缩减，背包只装得下一部分时返回 true 而剩余既没进包也没掉落；修复为 add 后无条件检查剩余并掉落（不依赖返回值）
- **米塔字幕观感调优 + 指令重排（`textAnimation`）**：默认值按反馈重调——distance 5.0→**2.5**、scale 1.2→**2.2**、默认色米塔黄→**白 #FFFFFF**；新增**结尾感叹号整句增益**（尾部连续 `!`/`！` 每个 +0.3、封顶 ×2.5，作用于整句——初版 ×1.12 视觉无感，按反馈加大；末尾空白不打断计数）；字间距改为与最终 scale 等比自动派生（0.15×scale，修复 scale 放大后字符重叠，显式 spacing 优先）。**指令结构重排**：原 `text <string> [options:greedy]` 中 string 遇空格截断、剩余文本被误拆进 options 报错（带空格消息必须引号）——改为**单 greedy 参数 + 行内 `|` 调参**：`/text 消息内容|scale=2.5;hold=60`，空格自由无需引号，`||`/全角「｜｜」转义字面竖线
- **米塔字幕广播化（`textAnimation`）**：/text 从"仅在执行者眼前"改为**向所有在线真人玩家（carpet 假人在入口排除）广播**——每名玩家一份独立会话、以各自的位置与朝向为基准生成字幕（聊天形式，与米塔效果的使用场景一致），控制台/命令方块同样可用（不再依赖执行者位置）；无任何可接收玩家时明确报错，区块未加载的玩家被跳过（原版会把加进非实体刻区块的实体按 UNLOADED_TO_CHUNK 写回区块）；并发上限从会话改为广播计（≤8 条，广播内每玩家一个会话）；假人在线时 /text 不再产生字幕（本地 RCON 实证排除逻辑）
- **红包（`redPacket`）**：新增 `/redpacket <份数> <祝福语>`（份数 1-100、祝福语 1-32 字过滤 § 与换行）——类型选择 GUI（4 个红色潜影盒图标：拼手气/普通/专属/口令）→ 投放物品 GUI（6 行大箱子：槽 0-44 放物品、45 取消、49 确认发放、53 清空；空不允许发放、未确认关闭原样退回背包满掉脚下、确认后物品锁定归红包）→ 聊天框广播 `xxx发了个类型 [红包：祝福语]`（整段亮红可点击领取，悬浮显示祝福语/类型/剩余份数/剩余时间）。四类分配在发出时一次性切好、总量守恒：拼手气与口令按星与条随机组成（物品数少于份数时随机抽份各拿 1、其余为空），普通按种类数量平分、余数随机落份（10 钻 3 份=3/3/4），专属两次点击玩家头选中确认、仅目标可领、整体给出、份数强制 1、他人点击提示"这是给 xxx 的专属红包"；口令经铁砧 GUI 设置（1-32 字符、mayPickup 恒真零经验消耗、关闭未设置则物品退回），领取方聊天框打出与口令完全一致的文本即领（复用 ServerMessageEvents.CHAT_MESSAGE）。领取校验顺序：存在→未过期→有剩余→非本人→未领过→专属→口令，每红包每人限领一次，领完立即结束；有效期 3 分钟，过期未领份额原样退回发送者（离线暂存上线补发，背包满掉脚下）。防滥用：领取点击防抖 10 tick、发送冷却 10 秒、每人同时最多 3 个未结束红包。实现：全程服务端原版容器菜单（箱子/铁砧子类）+ 容器层槽位保护与点击拦截（客户端预测 ghost 由 broadcastChanges 纠正），玩家头颅经 PROFILE 组件按名解析（1.21.10 起 ResolvableProfile 工厂/1.21~1.21.8 record 构造、26.2 起染色物品并入 ColorCollection.pick(DyeColor)，均 javap 核实分叉）；聊天可点击组件 1.21.5 起 ClickEvent/HoverEvent 改接口+record（1.21~1.21.4 类构造）按宏分叉；份额切分为纯函数并以 9 项 JUnit 契约测试覆盖守恒/余数/空份/多物品独立平分。**重启失效语义**：进行中红包、离线退回暂存、冷却与防抖均为内存态，重启即清空（如实注明）
- **聊天框玩家名金色高亮（`whoCalledMe`）**：经原版 `ChatDecorator` 官方扩展点（@Inject 接管 `MinecraftServer.getChatDecorator`，调用点 ServerGamePacketListenerImpl.handleChat，1.21/1.21.11/26.3 三版本签名一致 javap 核实）把玩家聊天文本中出现的在线玩家名渲染为金色——发 "xxx来一下" 全体玩家聊天框里 xxx 变金色；区间贪心最长优先非重叠（"timy tim" 各自高亮、同位重叠取最长名），非名字段保留原样式；仅玩家聊天过装饰器（/say 与系统消息不受影响），规则关闭或无命中原样透传；正版签名环境装饰内容带"无法验证"标记（离线服不受影响）；区间选择与匹配契约单测覆盖
- **谁在叫我改子串匹配 + 最长名优先（`whoCalledMe`）**：提及匹配从 ASCII 词边界改为**子串**——只要对话里出现名字就提醒，名字紧邻字母/数字（"Brokeyuan1"/"abcBrokeyuan"/"steve_alice"）同样命中（此前被词边界挡住，表现为"名字旁要有空格/非字母数字才触发"）；命中若被更长玩家名完整覆盖则不提醒——服内同时有 Tim/Timy 时 "timy 来一下" **只提醒 Timy**（优先完整的名字），而 "timy tim" 中独立的 "tim" 仍提醒 Tim；三级前缀级联（tim/timy/timothy）逐层覆盖正确；提及下标同时驱动 title 名字金色高亮，单测覆盖覆盖关系与级联
- **谁在叫我提示音无声修复 + title 分色（`whoCalledMe`）**：提示音从紫水晶叮声（生产服实证无声）换为**经验球音**——摸摸头同款 `ClientboundSoundPacket` 定向配方在生产服已被证实可听（音效事件是两者唯一实质差异变量；编码层经 JVM 单测排除——direct/registry holder 均可正常编码）；三声 pitch 1.2/1.6/2.0 递进、volume 0.8、首声同步发。title 分色：被点名者自己的名字**金黄加粗高亮**、正文白色（一眼看到是谁在叫）；mentionsName 重构为返回命中下标的 mentionIndex（boolean 语义保留供单测）
- **谁在叫我自己命中修复（`whoCalledMe`）**：移除 `player == sender` 过滤——自己发消息提到自己同样连响三声+title（此前被静默跳过，单机自测时表现为"完全无提醒"）；首声提示音改为立即同步发（不再依赖调度，消除调度语义依赖），其余两声按 6 tick 间隔
- **米塔字幕（`textAnimation`）**：新增 `/text <text> [options]`——在执行者眼前（脚部+视线×distance、高度+1.3）逐字弹出对话文本，停留后整句坠落消散，复刻米塔游戏的文字显示效果。时间线：1 字/tick 弹出（每字一个原版 text_display 实体，随机歪斜 ±45°+1.8 倍缩放经客户端 10 tick 变换插值收拢，逐字点击声 volume 1.0/pitch 1.2）→ 停留 40 tick → 坠落（服务端自算重力 0.03/tick²、阻力 0.99、落地一次 0.28 反弹、起落一次性随机翻滚，坠落 24 tick 起透明度 -8/tick 渐隐，播完即删）；长文本按标点断组（≤25 字/组、断点向后 10 字内找标点、非末组追加 " - "），上一组开始坠落时下一组接续打字，组间随机偏航 ±22.5° 与随机抬高；字色默认米塔黄 #FFFF55，支持 & 色码（&& 转义，色码按原版 § 语义重置样式）。options 串 k=v;k=v 可调 distance/scale/spacing/hold/glow/sound/drop。护栏：单句 ≤128 字、全服并发 ≤8 条、目标区块必须 ENTITY_TICKING（玩家执行恒成立，空服控制台明确报错——原版区块系统会把加进非实体刻区块的实体按 UNLOADED_TO_CHUNK 写回区块，本地 E2E 实证）。实现：原版从未提供展示实体的程序化接口（文本/变换/插值/透明度设置器均私有，1.21/1.21.11/26.3 javap 核实签名一致），经新增两枚 @Invoker mixin（DisplayInvoker/TextDisplayInvoker）驱动；26.1.2 起实体标签读取改名 entityTags、26.2 起实体类型常量迁入 EntityTypes（复数），按预处理宏分叉；崩溃/强杀残留的孤儿实体带 pry_textanim 标记，启动 + 每 100 tick 周期清扫（有活跃会话时跳过——清扫误杀正在播放的字形已本地复现并修复）。本地 1.21.11 dev + RCON 实测：假人执行 39 字实体的逐秒计数与设计逐项吻合（21→33→39→6→0），坠落物理落地反弹、分组接续、播完清零全部验证；11 版本构建绿
- **谁在叫我（`whoCalledMe`）**：聊天消息中出现其他玩家的名字时，被点名的玩家连响三声提示音（紫水晶叮声定向单发，发声点在头顶仅本人可闻，首声当 tick 末尾、余下每 6 tick 一声）并弹出 title 显示消息原文（0.25s 淡入 / 3s 停留 / 0.5s 淡出）；名字匹配大小写不敏感、按 ASCII 玩家名字符集取词边界不误伤子串（"Tim" 不会被 "Timy" 点到），与汉字粘连视为点名（中文无空格分词，"来一下Alex"/"小明哥"均算），点自己名字与 carpet 假人（作为被点名者）不提醒；实现为 Fabric `ServerMessageEvents.CHAT_MESSAGE` 单监听（注入点为 `PlayerManager.broadcast` HEAD，签名/未签名聊天共同汇聚点，离线服未签名聊天同样触发），零 Mixin 纯服务端
- **假人脑子命令增强（中文模式名 / keep 保持 / 名字后缀）**：`/player <name> brain <模式> [keep]`——**模式名跟随 `/carpet language`**：中文语言下补全与输入直接用三语显示名（`brain 僵尸`/`殭屍`），英文键恒可用、两种写法均可加 `keep`；mode 参数改 greedy 字符串（Brigadier 未引号参数只认 ASCII 字节码核实，中文必须 greedy 才能不带引号；解析时剥离尾部 keep，未知名先行干净报错不进 switch）。**keep 保持**：假人下线重上（重新 spawn，UUID 按名不变）后由周期扫描 1 秒内自动恢复同一模式（狼含主人 UUID），`brain off` 清除，服务器重启后重新 spawn 亦可恢复；规则关闭期间不补挂、重开后自动续上。**名字后缀**：挂载后假人头顶名牌/Tab 列表出现 `[模式名]` 后缀（三语随语言切换），颜色按敌对性三档——红=见面即打（僵尸/骷髅/凋灵骷髅/溺尸/掠夺者/卫道士/蛮兵/史莱姆/岩浆怪）、蓝=中立（蜘蛛/末影人/猪灵/僵尸猪灵/狼）、绿=不敌对（铁傀儡/村民/猪/鱼）；实现为**每假人一条**计分板队伍 `pry_brain_<mode>_<uuid>`（挂载前记录原属队伍、卸载恢复，本模组队伍随卸载删除；**原队伍前缀随挂载带到头顶**——职衔类前缀挂载期间不消失，且每假人独立避免同模式互相泄漏；不设队色，颜色挂后缀组件自身）；命令回执简化为一行"来源 为 假人 安装了脑子"（状态看头顶，假人名字保持默认色）。CustomName 路线经字节码核实不可行（`Player.getDisplayName()` 覆写且只走队伍装饰、无视 CustomName）故用队伍
- **假人脑子新增 8 种模式（`fakePlayerBrain`，现共 19 种）**：`drowned`（溺尸——持三叉戟远程投掷：蓄力 10 tick 后 `releaseUsingItem` 走原生 `TridentItem.releaseUsing`，掷出的即主手真三叉戟，忠诚/耐久/命中全原生零凭空造物，空手/持剑近战兜底；目标链对齐原版含村民透墙/铁傀儡/美西螈/幼年海龟）、`zombiepiglin`（僵尸猪灵——中立，被打才反击，并经新增 `PlayerGroupHurtByTargetGoal` 把仇恨广播给 20 格内同类脑假人，即原版 `HurtByTargetGoal.setAlertOthers` 的脑间等价实现：仅唤醒无目标同伴、玩家类攻击者受创造/旁观/和平门禁）、`witherskeleton`（凋灵骷髅——近战玩家/铁傀儡与猪灵类（真实 `AbstractPiglin` + 猪灵脑假人）、规避狼；攻击不附凋零，身体附伤红线不做）、`vindicator`（卫道士——近战玩家/村民/铁傀儡）、`piglinbrute`（猪灵蛮兵——恒敌对近战无视金装）、`slime`/`magmacube`（史莱姆/岩浆怪——跳行移动：无寻路，idle 连续起跳 + 锁定后直线跳行追击，目标仅玩家/铁傀儡且无 HurtBy，对齐原版）、`fish`（鱼——鳕鱼/鲑鱼/热带鱼同款：水中游动、受伤逃窜、8 格内避人（`PlayerAvoidEntityGoal` 补自身排除——规避玩家类时会扫到假人本体）、离水扑腾 `PlayerFlopGoal`（原版 aiStep 离水分支的 Goal 化：原生起跳 + 随机转向漂移，被冲上岸不迈步逃命）；带**海豚式换气**——假人身体是玩家的肺，憋气不足（air<100）自动独占移动链上浮出水回气（跳跃输入走原版 `jumpInLiquid(WATER)` 路径），回满再下潜，零身体数据改动；水中游动 `PlayerRandomSwimGoal` 刻意不走寻路器（A* 以"可站立格"为节点、水层中间不成节点，且卡死重算节奏会把缓慢的水中加速度反复打断），直线游到浸水选点、撞壁/离水即止）。**移动控制器新增游泳层**（对全部模式生效）：水中时目标点高于脚下即置跳跃输入——原版 `aiStep` 消费 jumping 走 `jumpInLiquid(WATER)` 上浮路径（字节码核实假人身上无 vanilla 写点竞争、`applyInput` 仅输入衰减），下潜交给自然沉降，等价真人按住跳跃键游泳；`tick` 头统一复位防残留连跳。群体警报落点为直写同伴目标字段（零额外实体）；铁傀儡 `isHostileFake` 判定扩至 14 种（口径对齐原版 `Enemy` 标记接口，`Monster implements Enemy` 字节码核实）。全部装配逐条 javap 核实 26.3 未混淆 jar（`Drowned.addBehaviourGoals`、`ZombifiedPiglin`/`WitherSkeleton`/`Vindicator` registerGoals、`Slime.addTargetingGoals`、`AbstractFish` 装配与离水数值、`TridentItem` THROW_THRESHOLD_TIME=10）。刻意不做：苦力怕（爆炸=破坏方块）、烈焰人/女巫/唤魔者/守卫者（弹射物/实体凭空造物）、幻翼/蜜蜂等飞行生物、鱿鱼/海豚（招牌皆为水中身体效果）、溺尸水中导航三 Goal 与僵尸猪灵愤怒状态机（范围说明见 docs）
- **骑乘/捡起交互统一（ridingPlayers / pickupPlayers）**：主手不死图腾右键玩家的分流从物品组合改为点击部位——点**头部**（命中点距目标脚底 ≥ 碰撞箱高度 65%，与摸摸头 `HEAD_ZONE_MIN_FRACTION` 同口径）骑到对方头上（ridingPlayers），点**腿脚**（< 37.5%，原版玩家模型腿部占比，缩放体型自动适配）把对方捡起骑到自己头上（pickupPlayers），点**躯干**无交互；**副手金胡萝卜条件删除**，副手不再检查任何物品；假人（`EntityPlayerMPFake`）与摸摸头"仅真人"同口径双向排除；两条规则、`/riding`、`/picking` 与 RIDE/PICKUP 许可保持不变，仍按部位对应侧分别校验；仅带坐标的 INTERACT_AT 包触发（裸 INTERACT 包放行原版，天然单次触发），对应侧规则关闭或点躯干时整体 PASS 不冷却，摸摸头等后续监听不受影响
- **摸摸头（patPatPlayers / patPatPlayersHeadBob）**：右键点击其他**真人**玩家的头部像撸猫一样抚摸——每次右键一次抚摸脉冲：发起者挥手、目标头顶冒爱心、目标随节奏**往复蹲起**（自己按住潜行则保持蹲，松开恢复往复，停止点击立即交还自主），被摸者屏幕内眼前冒爱心、轻响从发起者方向传来（3D 定位，转头即知谁在摸你）、镜头柔和点头。纯服务端实现（Fabric `UseEntityCallback`，零 Mixin，同 ridingPlayers 钩子），原版客户端即用，仅对**真人**生效（carpet 假人在入口即排除，判据与 mcstats-push 的 isFakePlayer 同语义、以硬依赖 instanceof 实现）；**不要求空手且恒不消费交互（火后不管）**——触发判定只认带命中坐标的交互包（客户端 use 流程先发 INTERACT_AT、26.x 合并为带坐标单包，字节码核实；其后补发的裸 INTERACT 包 hitResult 为 null 直接放行原版，天然单次触发），命中点须落在目标碰撞箱顶部约 1/3，冷却为连点加速制（慢速点击 10 tick 一次；连点每命中一次递减 2 tick、下限 3 tick ≈ 6.6 次脉冲/秒；停手 20 tick 重置）；返回 PASS 而非 SUCCESS 的依据：Fabric 事件链非 PASS 即短路（组合 lambda 字节码核实），消费交互会同时压掉原版 dispatch 与后续模组监听。被摸者视角：沿其视线水平前方 0.45 格的爱心（任意俯仰必入画，不钻目标模型）、`ClientboundSoundPacket` 定向高音贴耳音效（其他人听广播版）、镜头柔和点头。挥手由服务端显式发起（`swing(hand, true)` 尾参=发给自己，1.21.x `broadcastAndSend`/26.3 `sendToTrackingPlayersAndSelf` 语义对齐，26.3 三参按宏分叉）。蹲起脉冲状态机：脉冲时目标自己按着 shift 则跳过（保持蹲），站立则强制蹲 3 tick 后解除（往复蹲起），状态自由迁移、停止即解除——真人目标为标志同步（他人可见、本人无感，文档注明）。`/patnod on|off` 按玩家开关接不接受被摸（拒绝时摸头对其完全不生效，状态持久化于 config/carpet-pry-patnod.json）；镜头轻点序列已按反馈整体移除，被摸者反馈收敛为眼前爱心、方向性轻响与蹲起动作
- **适配 Minecraft 26.3**：新增 26.3 子项目（`carpet 26.3+v260915` / `fabric-api 0.161.0+26.3`，26.3 无混淆线沿用 Java 25；fabric-loader 全局升至 0.19.5——carpet 26.3 依赖要求）。26.3 原版 API 变更处理：五处——`Entity.hurtMarked` 字段移除（拆分为 `needsSync`/`syncVelocity`，`betterSnowball` 击退同步改写 `syncVelocity`）、`LivingEntity.swing(hand)` 移除（改为三参 `swing(hand, SwingAnimation.DEFAULT, sendToSelf=false)`，等价旧"仅广播观察者"语义）、`Entity.setInvulnerable` 拆分为永久/临时两种（`moreEndCrystalTypes` 无敌水晶改 `setPermanentlyInvulnerable`）、`drop` 第三参改 `Prediction` 枚举且 `dropAround` 固定 false（`DropSlotScheduler` 改 `drop(stack, true, SERVER_ONLY)`，保持"不散落+记录 thrower"语义并随 26.3 新行为附带挥手广播）、`Player.startSleepInBed` 增至四参（新增 `AbstractBedBlock`/`BlockState`/`BedRule` 前导参数，`sleepingDuringTheDay` 的 HEAD 注入签名同步扩展——26.2 的版本覆盖文件会沿预处理链级联到 26.3，故新增 26.3 专属覆盖 `MixinPlayerBase`）；26.3 的 carpet 正式版暂未同步到 masa maven，CI 增加预下步骤到 `libs/`（flatDir 兜底），单元测试基线随最新版本移至 26.3。本地 runServer + RCON 实测：服务器正常启动零 mixin 错误，僵尸脑子假人完整走通锁定→追击→击杀（"Pryb was slain by prya"），26.3 新 swing 调用在真实攻击路径上工作正常
- **猪灵模式拾取物品（用户核心需求，不限于金类）**：新增移植版 `PlayerPickupItemsGoal`——主动搜寻 12 格内掉落物并走过去，假人走到物品旁由**玩家原生碰撞拾取**完成入包（零搬运，物品守恒）；金质物品（`ItemTags.PIGLIN_LOVED`，即 Wiki 的 #piglin_loved 列表）优先，其余物品同样捡拾。捡到后按 Wiki 装备（"端详片刻"节奏简化为每 64 tick 一件）：副手空装盾牌、空盔甲槽穿对应 `*_ARMOR` 标签物品（金质优先、已装备槽不换）、主手空拿金剑/弩；**物品守恒**：全部经 Inventory 槽位移出 + `setItemSlot` 装入（等价玩家背包拖动穿戴，不生成/不销毁物品，被替换的旧装备退回背包、满则原生掉落）；被攻击后 20 秒（400 tick）内不捡不装（Wiki 同款冷却）；有战斗目标时拾取让位（战斗优先）。本地 RCON 实测：丢金剑/铁头盔/泥土三件掉落物，猪灵假人全部捡走——金剑装主手、铁头盔戴头上（NBT Slot 103）、泥土进背包
- **猪灵模式对齐 Wiki 行为**（`fakePlayerBrain`，纯"脑子"层面）：目标谓词加和平难度门控（Wiki：和平难度不与玩家敌对）；立刻敌对 16 格内的凋灵骷髅（新增目标类型，`WitherSkeleton` 1.21.11 迁包 `monster.skeleton` 已按根方言宏处理）；**武器决定战斗方式**——金剑近战/弩远程/26.1.2+ 金矛冲锋三种行为 Goal 并存、靠 canUse 的持械判定自然互斥，换武器自动切换；远程 Goal 新增走位开关——猪灵持弩射击时不左右移动（Wiki：与弓类生物不同），只保留前后进退拉开距离；非敌对态主动远离僵尸猪灵（`PlayerAvoidEntityGoal`，战斗激活时被抢占）。不做（红线/范围说明）：僵尸化转化（身体行为）、以物易物与拾取装备（物品系统状态机）、灵魂火方块规避（无方块感知规避引擎）、疣猪兽群攻协调（群体状态）
- **假人脑子僵尸模式对齐原版行为细节**（对照 Minecraft Wiki 僵尸条目，以 26.2 未混淆原版字节码为准）：目标选择器扩展为原版同款五级优先级——被攻击者（HurtBy）> 最近的玩家 > 最近的村民/流浪商人（`mustSee=false`，还原"僵尸可透过方块定位村民"）> 最近的铁傀儡 > 幼年海龟（`isBaby` 过滤）；玩家头戴僵尸头时对其追踪距离减半（20→10 格，babyzombie 为 16→8，双并列目标 goal 实现）；自研 A* 寻路加入危险地形代价——熔岩/火焰格（含头顶）禁行（两者无碰撞箱，可站立判定天然不排除，需显式拦截），铁轨格高代价软惩罚（原版僵尸"不尝试穿过铁轨"，绕不过才走），逐格 ±1 高差的搜索结构天然保证不生成下落超过 3 格的坠崖路径。刻意不还原两项：着火传染与踩碎海龟蛋——两者均属原版僵尸"身体"（`doHurtTarget`/`mob_griefing` 方块交互）而非脑子，假人攻击管线锁定玩家原生 `Player#attack`，手动补点火/破坏方块即违反"零凭空造物"红线；"被攻击后只转移一次目标"为原版目标锁定的自身局限，保留本模组更优的周期复扫
- **假人脑子新增 4 种模式与长矛支持**（`fakePlayerBrain`，现共 11 种模式）：`babyzombie`（小僵尸——1.25 疾跑近战追击，行为对齐原版幼年僵尸的速度加成，身体锁定红线约束下不缩小模型）、`pig`（猪——完全中立无仇恨，被打/着火恐慌逃跑 + 随机漫步）、`piglin`（猪灵——复用原版 `PiglinAi` 金装判定（1.21~1.21.1 为 `isWearingGold`、1.21.3+ 更名 `isWearingSafeArmor`，预处理宏分支），敌视不穿金装的玩家、对金装玩家中立、被谁打都还手）；**26.1.2+ 僵尸模式支持原版长矛**：与原版 26.x 僵尸同款装配（移植版 `SpearUseGoal` 挂优先级 2、近战降为 3 兜底）——主手持矛（动能武器 `KINETIC_WEAPON` 组件）时接近→举矛蓄力→全速冲刺（刺击伤害由 `LivingEntity` 在使用期间的原版动能判定结算，零凭空造物）→后撤循环，1.21.x 无长矛物品该 Goal 不编入不受影响；铁傀儡的敌对假人判定同步纳入 babyzombie/piglin
### 功能
- **假人脑子**（`fakePlayerBrain`）：给 `/player <name>` 追加 brain 子命令，把原版生物式 AI"脑子"挂到假人身上，提供 8 种模式——`zombie`（近战追击最近玩家，原生 `attack()` 挥砍）、`skeleton`/`pillager`（主手持弓/弩时远程锁定与风筝走位，走原生 `startUsingItem → releaseUsingItem` 流程消耗背包真实箭矢）、`irongolem`（攻击敌对生物——不攻击苦力怕——与攻击过自己的假人）、`spider`（昼中立夜敌对）、`wolf`（跟随执行命令的主人并仇恨同步：主人被谁打就咬谁）、`villager`（随机漫步、被攻击恐慌、遇僵尸反向逃跑）、`enderman`（被凝视激怒——原版 `isStaredAt` 点积算法——后锁定并 `setSprinting(true)` 疾跑扑击）。核心设计遵循"只换脑子、不换身体、零额外实体、零凭空造物"：假人始终保持 `ServerPlayer` 类型与全部原生属性；寻路为自研紧凑 A*（原版 `MobNavigation` 构造器强绑定 `Mob` 实例，为守住零实体红线在方块网格上等价复刻节点推进/卡死重算语义）；AI 移动全部经注入的 `PlayerMoveControl` 折算为玩家原生按键输入（`zza/xxa`+限速转向+原生跳跃），交由玩家原生 `travel()` 物理执行，绝不直接改坐标/速度，避免玩家物理与生物瞬移式移动打架导致的"鬼畜抽搐"；架构为策略模式——mixin 在 `ServerPlayer` 初始化时以 `@Implements` 嫁接 `PryMob` 假面接口，导航器/移动控制/视线控制/双目标选择器按需惰性挂载（真人零开销），各模式向双选择器装配移植 Goal；挂载期间 Carpet 手动移动/攻击指令屏蔽（actionPack 停摆），`brain off`/切换模式/关闭规则/假人下线死亡均自动卸载且无残留；视觉同步（行走/疾跑/挥手/拉弓/视角）由原版实体同步机制驱动，纯服务端，客户端无需安装任何模组

- **更多种类的末地水晶**（`moreEndCrystalTypes`）：允许把末地水晶放在哭泣的黑曜石上。`true` = 放出的为普通水晶（不无敌）；`invulnerable` = 放出的为无敌水晶（`Invulnerable=1` 且显示底部板，与原版复活龙过程中推出柱子、打断复活得到的水晶相同）。两种模式放出的水晶光束均指向固定坐标 `(0,128,0)`。实现上仅把点击的哭泣黑曜石替换为黑曜石状态骗过原版基座校验，其余放置逻辑全部复用原版；客户端/服务端共用同一份逻辑，纯原版客户端在服务器上也可用。放在普通黑曜石/基岩上的水晶不受影响

### 修复
- **米塔字幕无下坠原地消散（`textAnimation`）**：落地判定按字形所在列的高度表取地面——头顶有任何非树叶方块（室内天花板/屋檐/桥洞/悬挑）时高度表比生成点还高，坠落首 tick 即满足 `y<=ground` 瞬间"落地"，翻滚照播、1.2s 后原地渐隐（玩家可见症状：无下坠原地消散，户外空旷处不复现）；落地高度改取列高度表与发起者脚位的较小值，室内落到发起者所站地面、跨出顶悬边缘落到真实地面；顺带把逐 tick 高度表查询缓存到坠落起手一次
- **谁在叫我 title 只高亮首个名字**：`mentionIndex` 单命中下标喂给 title 分色，"Brokeyuan 123 Brokeyuan" 只色第一处；命中提取改 `uncoveredHits` 返回全部未覆盖区间（提醒判定改列表非空），title 对全部区间着色，mention 模式同步高亮 @+名字 整体（与聊天框一致）
- **红包已领完后到期重复广播与手气王二次结算**：tick 到期分支只判 `!expired` 未判 `!done`，提前领完的红包在原到期时刻向全服误播"已过期"并二次结算手气王；补 `!done` 守卫，清理时序不变
- **全项目审查缺陷修复批次（10-05 审查 32 项，任务清单见 .zcode/specs/audit-fixes）**：红包——同玩家开新会话先退还旧会话持有物品（投放区余量+口令阶段锁定 payload，覆盖 /redpacket、专属直达、again 三入口，菜单关闭回调身份检查不再重复退款）；投放区以外末行六格全保护（原只保护三个按钮，存入即丢失）；停服退款改用监听器入参 server（静态引用被先注册的监听器提前置空，在线发送者被误判离线、退回暂存后随重启清空）；口令铁砧四项——stillValid 恒真（原版判定要求脚下是铁砧，普通地点下一 tick 自动关闭）、onTake 复刻消耗输入槽并清零成品但**不扣经验**（javap 26.3：super 首行 giveExperienceLevels(-cost)，免费提交被扣 9 级）、成品纸随提交清零不再进背包（safeTake 副本路径 javap 核实）、口令纸归属改 CustomData 标记（改名的成品纸带标记可回收、玩家同名纸无标记不误删，输入槽点击全阻断防 UI 纸取出）。假人脑——1.21~1.21.4 弩蓄满即释放（javap：releaseUsing 才 tryLoadProjectiles 装填，等 isCharged 死锁不射击；1.21.5+ onUseTick 自动装填不变）；远程执行手与 isHoldingWeapon 认可面同手（副手弩能启动目标却永不射击）；近战挥砍补视线门（followingTargetEvenIfNotSeen 模式隔墙命中）；detachAll 恢复原队伍（停服保存残留临时队伍与后缀）；挂脑临时队继承原队伍行为面（颜色/友伤/隐身可见/名字与死亡消息可见性/碰撞规则，队色 26.2 起 Optional<TeamColor> 按宏分叉）；目标保持与近战/远程入口补跨世界检查（/tp 后旧世界目标失效）；索敌记忆只缓冲当前目标（新候选必须直接可见，不再凭残余记忆锁定隔墙目标）；拾取/装备的受伤冷却改纯时间戳门控（lastHurtByMob 引用约 100 tick 被原版清除，400 tick 窗口提前 5 秒失效）；无脑子时 brain off 同样清 keep（规则自动卸载→重开→sweep 补挂前 off，残留记录会把脑子挂回来）；规避逃跑注视改为跟随逃跑路径（玩家身体朝向=前进输入，与移动控制转向互相抵消曾致朝危险前进）；A* 直线裁剪补熔岩/火焰禁行（与 expand 同口径，安全绕行不再被直抄穿火）。交互/命令——穿透点击补潜影盒开盖空间判定（复刻原版 canOpen：动画中放行、盖子伸出体积 noCollision 才可开，1.21~1.21.3 四参+move 与 1.21.4+ 五参按宏分叉）与打开统计/猪灵仇恨（awardStat + angerNearbyPiglins，1.21~1.21.1 两参与 1.21.3+ 三参分叉）；/text 选项补全按完整命令绝对位置 createOffset（原传相对位置实测覆盖正文与分隔符）、跳过 || 转义、支持 ; 多选项已用键去重；/player brain keep 补全同族修复（原把模式名一并替换掉）；dropall 四条英文反馈改位置参数（实参为 名字+数值+槽位，原文按 名字+槽位+数值 取参错位显示）；草丛隐身归属加 ambient 位（原版命令不可伪造，规则关闭时不再误删管理员授予的同类隐身）；/tppset spawn 拒绝同名已在线假人并先等生成成功再计时（原不检查生成结果即进 kill 排程，同名在线或无权限时会下线既有假人）；字幕淡出透明度 byte→int（255 以 byte 存为 -1，首次递减即回绕消失无渐隐）；字幕清扫补 LAST_TEXT 清理（跨世界进入新维度冷却异常拉长）；actionbar 降级会话保持期改用构造参数（原固定 40 tick 无视 hold 设置）；CI 汇总脚本缺产物分支补 file_size 缺省（首个版本缺产物抛异常、后续版本显示上一版本大小）。新增 5 项回归测试（口令纸标记、keep 清理、铁砧虚拟有效性/免费提交/槽位保护/会话退还），共 68 测试全绿
- **`/player <真人名> dropall` 缺假人校验**：resolvePlayer 按名解析任意在线玩家而全链路无 `instanceof EntityPlayerMPFake` 检查，commandPlayer 开放时任何玩家可对真人持续清空背包；once/start/stop 三分支补校验（同 brain/sendto 口径，新增三语提示）
- **字幕会话崩溃泄漏并发额度**：调度器异常隔离直接淘汰任务、不回调业务，Broadcast.remaining 永不归零——累计 8 次崩溃后 /text 恒报并发已满且孤儿清扫被"有活跃广播"条件停摆；注册层包 try/catch、崩溃时回报结束，额度回收收敛进 Broadcast.partFinished（顺带修复降级 actionbar 会话最后结束时广播不移除的同型泄漏）
- **谁在叫我小写化下标漂移**：匹配区间在 toLowerCase 结果上计算、拿回原文切分，聊天含 İ（U+0130，小写化变两字符）等字符时高亮错位甚至越界；改 regionMatches 原文大小写不敏感匹配，下标恒为原文坐标（单测覆盖膨胀场景）
- **中央调度器 registerDelayed 一次性契约**：到期后透传内部任务返回值，内部返回 true 即退化为每 tick 重复执行；改为到期强制注销（现有 6 处调用方均返回 false 不受影响，新增单测）
- **红包份数超长数字串报 unexpected error**：isDigit 预检放行超长数字串、parseInt 溢出抛未捕获 NFE；补 9 位长度预检，超长走 count_invalid 提示
- **红包口令铁砧成任意物品复制机（10-04 审查发现，P0）**：`PasswordAnvilMenu.onTake` 覆写后未调 `super.onTake`——原版 onTake 正是消耗输入槽与重置费用的地方，漏调即输入物品永不消耗：把任意物品（如钻石剑）放进输入槽改名，点成品即可白拿副本且原物随菜单关闭退回，每轮 `/redpacket` 可重复；且每次点成品都触发 `onPasswordSet → createAndBroadcast`，冷却/上限分支把整包红包物品原样退回、冷却过后重发整包。三重修复：onTake 补调 super（恢复原版消耗语义）；口令提交加 `awaitingPassword` 一次性门（重复点取成品不再重入）；输入槽白名单——经 `slotsChanged` 拦截（javap 核实 1.21.11 输入容器 setChanged → menu.slotsChanged，任意点击路径均经此回调），槽 0 只留标记口令纸、槽 1 一律清空，外来物品弹回背包（放不下掉脚下），堵死"借红包铁砧免费改名/合成/修理"的滥用面（mayPickup 恒真绕过了原版全部经验费）
- **`/redpacket` 与 `/text` 规则门控失效**：命令树的 `.requires` 用了 `!"false".equals(...)`——那是 String 三态规则的写法，对 boolean 规则（`redPacket`/`textAnimation`）的自动装箱值恒为 false、取反后恒 true，两棵命令树无视规则常开，默认 false 形同虚设；改为直接读 boolean
- **红包口令铁砧阶段断线/停服物品蒸发**：DISCONNECT 钩子只在物品投放菜单打开（`itemInputOpen`）时退款，而口令阶段物品已锁定在 `session.payload`、容器已清空，且断线不触发铁砧的 `removed` 回调（生产实证）——payload 无人认领凭空蒸发；断线与 SERVER_STOPPING 两条路径均补上 payload 退款（进离线暂存/直接 giveItems），附带清理断线时未清的口令提示痕迹 `LAST_HINT`
- **中央调度器逐任务异常隔离**：`runTasks` 此前无 try/catch，任一任务抛异常（如专属红包双击头像后同 tick 断线的 null 引用窗口，已补空判）会上抛 END_SERVER_TICK 直接崩服；现逐任务隔离——异常记 error 日志并淘汰该任务，与 BrainManager 单脑崩溃摘除同思路
- **文档计数与残留清理**：README×2 / Description / docs/rules×2 头部计数对齐实际口径（33 条规则 / 10 个命令）；两份 README 与 Description.MD 补上缺失的 fakePlayerBrain（假人脑子）特性条目；docs/rules_en.md whoCalledMe 节删除编辑残留的旧表格与旧段落、`## 生存功能---` 粘连标题与重复节头修复（中英同款）；Description.MD 修复重复短语与版本范围（26.2→26.3、10→11 版本）
- **旧 loader（≤0.19.3）加载 1.21.11+/26.x 版本时 `ServerPlayer` mixin 转换崩溃**（`ClassCastException: ArrayList cannot be cast to AnnotationNode`，来自 MixinExtras `FactoryRedirectWrapperMixinTransformer`）：1.21.11+ 的 sponge-mixin 将 `Redirect.at()` 改为 `At[]`（字节码里解析为 ArrayList），loader 0.19.3 捆绑的 MixinExtras 0.5.4 仍按单个 AnnotationNode 强转——排队到同一目标类的任意 `@Redirect`（含本模组 sleepingDuringTheDay 的 `MixinPlayer`）都会在 preApply 阶段炸掉整个类的 mixin 转换。现于产物内 jij 捆绑修复后的 MixinExtras 0.5.5（io.github.llamalad7 坐标自 Maven Central 解析；旧专用 maven maven.llamalad7.dev 已不可达），Fabric loader 取进程内最高版本，旧 loader 环境与受影响的其他 @Redirect 模组一并修复；11 版本产物逐 jar 验证嵌入
- **假人皮肤完全不生效（5f1448f 回归，生产实证"全部默认皮肤+零告警"）**：出生前注入重构引入三类静默放弃路径——① summon 模式改为内存直拷召唤者 profile 纹理，但离线服真人 profile 本就无 textures 属性（皮肤靠 skinrestorer join 换上），快照为空即静默放弃，丢失了旧实现"按召唤者名走 provider（自带 profile 缓存）"的可用路径；② 控制台/命令方块执行 summon 无召唤者，同样静默放弃（本地 RCON 复现：假人上线、零日志、默认皮肤）；③ 真人名假人按 UUID v3/v4 分流跳过换肤，叠加生产网络下 Carpet 拉 profile 失败时 profile 无纹理，直接显示默认皮肤。本地 dev 1.21.11 + skinrestorer 2.11.0 复现矩阵：same_skin 链路通（另暴露预热竞态——假人上线早于预热完成 2 秒，退化为兜底闪换）；summon 控制台路径复现生产症状。现修复：废除 v3/v4 分流，所有假人统一出生前注入（真人保护双保险不变——save=false 从不落库 + 压制仅对在线假人实例生效）；summon 来源回退链（快照 → 按召唤者名 provider → 控制台回退 fakePlayerSkinSet 统一皮肤），全部回退留 INFO 日志；构造器注入异常/预热未就绪/兜底超时（5 秒→30 秒，防慢 profile 拉取丢注入）均有日志，零静默分支。文档与三语描述同步
- **玩家塔跨维度被甩客（基座珍珠穿传送门/走地狱门后整塔散伙）**：原版 `ServerPlayer.teleport`（1.21.3+）/`changeDimension`（1.21/1.21.1）的跨维度分支没有 `Entity.teleportCrossDimension` 的乘客处理（快照乘客→`ejectPassengers`→按"落点+塔内相对偏移"逐个传送→载具落位后 `startRiding(force)` 重组），玩家作载具（ridingPlayers/pickupPlayers 建塔）跨维度时，乘客在 `removePlayerImmediately`→`setRemoved` 的逐个下车处被留在原维度；同维度分支只移包不摘除实体，乘客随骑乘粘滞照常跟随，故只有跨维度散塔。现于跨维度传送入口 HEAD 按原版非玩家载具同一模式补齐：弹下直系乘客并各自传送（乘客自身的传送递归触发同钩子，子塔自动随行），载具落位（`addDuringTeleport`）后 force 重组骑乘链；乘客落点用原版 `calculatePassengerTransition` 同式（珍珠 relatives=ROTATION+DELTA 时各自保持朝向、位置=落点+原塔内偏移）。附带语义披露：骑乘中的玩家本人触发不了传送门为原版门禁（`canUsePortal(false)` 对 `isPassenger` 恒 false，坐猪同理），塔随基座整体过门；`/tp` 换维度同样整塔随行；1.21/1.21.1 与 1.21.3+ 两条传送线、`startRiding` 双参/三参均按宏分叉，注入点 11 版本全量 javap 核实，require=0 配 MixinSanityCheck 自检
- **假人皮肤"先旧后新"闪变与真人名假人被换肤**（`fakePlayerSkinMode`）：旧实现在生成命令结束（TAIL）后调 SkinRestorer `setSkinAsync(save=false)` 换肤，而 Carpet 假人生成是两段式——先异步向 Mojang 拉取 profile（真人名假人拉到的就是真人纹理），回调里构造实体并发出生包——换肤落在出生包之后，观战者必然先见真人旧皮肤再闪到目标皮肤（26.x 覆盖副本还为此用 5 秒裸线程轮询找新假人，定位也不精确）。现改为出生前注入：假人构造器 HEAD 就地替换其 profile 的 textures 属性（构造器收到的正是出生包所带 profile），观战者首帧即目标皮肤；summon 模式内存直拷召唤者在线 profile 纹理（零网络），same_skin 模式经 skinrestorer 的 mojang provider 启动时预解析缓存（不落库）。**语义变更**：规则仅对非真人名的假人生效——真人按 UUID 版本判定（Mojang 正版 v4、离线合成名 v3），与真人同名的假人保持该玩家本来皮肤（SkinRestorer join 钩子按其 UUID 套用的存储皮正是其真实长相），summon/same_skin 对其不再生效；另压制 SkinService.applySkin，防 SkinRestorer join 钩子用存储皮（含历史 save=true 时代合成名落库残留）盖回注入皮肤，真人玩家不受影响。皮肤来源未就绪（spawn 失败 / same_skin 预热未完成 / 注入点漂移）时退化为出生后换肤兜底（观感同旧行为）。附带修复：≤1.21.11 的旧 TAIL 实现在异步生成期间找不到新假人、皮肤模式静默失效的问题。26.x 覆盖副本（裸线程轮询版）已删除，由根模板统一实现。文档与三语描述已同步
- **多假人 keep 补挂健壮性**：keep 表为本次服务器运行内有效（重启即作废，重生的假人需重新挂载——文档已如实注明）；多个 keep 假人集中死亡后快速重生时，旧版 sweep 的"按 UUID 查到即跳过"会让死会话占坑（join 快于扫描周期时丢脑丢后缀，假人越多概率越高），现以会话归属判定（brain.player != 在线玩家）换血，且补挂循环逐假人 try/catch 隔离、单个异常不连累同轮其余假人。RCON 实测：三假人 keep 背靠背全灭全生全部自动恢复
- **keep 假人死后重生，脑子与名字后缀丢失**：keep 假人死亡后立即重生命名/UUID 相同（皮肤缓存时 join 快于一个扫描周期），此时会话表里还挂着死对象的旧会话——补挂循环按 UUID 查到"已挂载"即跳过，清理循环又把旧会话当在线放过，旧会话永久占坑：新假人没脑子没后缀，且旧会话每 tick 继续驱动死对象。修法：sweep 引入会话归属判定（`brain.player != 在线玩家` 即换血，按会话自带玩家的名字清队伍——对象移除后名字仍可读，顺带修掉"玩家列表已查无此人导致队伍残留"的相邻洞）；`onPlayerTick`/`hasBrain` 同步加归属校验（旧会话不打新假人、ActionPack 取消不误判）。RCON 实测 kill+spawn 背靠背三轮全部自动恢复脑子与队伍
- **骷髅/掠夺者脑漏挂原版目标链基座，不对铁傀儡和狼反应**：骷髅脑当初只装配了"玩家+被打反击"，漏掉原版 `AbstractSkeleton` 目标链基座——铁傀儡（pri 3）、幼年海龟（pri 3）与规避狼 goal（原版基座 avoid pri3 压过武器 Goal pri4，骷髅见狼是逃不是射；移植版远程在 pri1，规避置 pri0 等效）；掠夺者脑同样漏掉原版 `Pillager` 目标链的村民（pri 3，透墙）与铁傀儡（pri 3）。补齐后与僵尸/凋灵骷髅/溺尸/卫道士/史莱姆等已对齐模式同口径。RCON 实测：骷髅对铁傀儡开弓（血 100→95）、掠夺者开弩（100→81）、僵尸近战回归（100→95）、骷髅遇狼拉开距离（2.59→3.77 格）
- **摸摸头×骑乘玩家双触发与蹲下脉冲拆塔**：主手图腾点头的骑乘交互被服务端消费后，客户端预测为 PASS 仍补发**副手跟随包**（带命中坐标）——摸摸头不挑手，副手包命中头部区即触发，蹲下脉冲落到刚成为载具的目标头上触发"蹲下卸客"，表现为"拿着图腾上了一下头立马被下"。三层修复：①骑乘/捡起规则开启时，主手持图腾的点击整体让路（摸头跳过——图腾换副手即可正常摸）；②目标卷入玩家骑乘（作为载具或乘客）时跳过蹲下脉冲（爱心/轻响/挥手等其余效果不受影响）；③骑乘/捡起挂塔前提前释放双方身上未结束的蹲脉冲（连点抚摸把目标压在蹲姿时 pickup 不再"蹲着上车立即下车"）。共享 UseEntityCallback 分发（骑乘先行、摸摸头火后不管）维持不变
- **假人脑子被打不反击（共享近战 Goal 私货偏离原版）**：移植版 `PlayerMeleeAttackGoal.stop()` 在原版"停导航+收攻击姿态"之外多写了一步 `setTarget(null)`（原版 `MeleeAttackGoal.stop` 从不清目标，目标生命周期归 HurtBy/Nearest 等 TARGET 旗标 Goal 管）。当攻击者被地形挡住视线时形成死循环：melee 因丢失视线 stop → 清目标 → HurtBy 见空目标随之 stop → 2 tick 冷却后重启 → 再次 stop……表现为被打方每 2 tick 闪断一次、原地挨打直至死亡（所有近战模式受影响，此前实测均为开阔地直视场景故未暴露）。现对齐原版仅停导航与姿态；本地 RCON 竞技场复测：僵尸猪灵被打后稳定反击反杀
- **摸摸头在 `pickupPlayers` 开启时不可达**：UseEntityCallback 共享监听中捡起分支无条件 return（PASS 也返回），pickupPlayers=true 的服务器上摸头逻辑永远执行不到（本地 E2E 因未开 pickup 未暴露，用户服务器探针日志定位）。改为仅非 PASS 时短路；另于文档补明摸头受原版 3 格交互距离限制（超出静默拒绝）
- **`betterSnowball` 击退绕过无敌帧成倍叠加**：原实现无条件 `push`，多雪球同帧命中同一目标时，无敌帧拦下了重复伤害却拦不住击退位移，成倍叠加把人推飞。现仅在 `hurt` 真正生效（返回 true）时施加方向击退；无敌帧内重复命中或目标本身无敌（创造/旁观）一律不再 push
- **`entitiesRidingPlayers` 旁观者可骑乘/被骑乘 + 交互连点刷屏**：原版 `startRiding` 整条门禁链（couldAcceptPassenger/canSerialize/canRide/canAddPassenger）与服务端交互包处理都不含游戏模式检查，旁观者可骑乘与被骑乘；现 handler 公共前置显式门禁旁观者（双向），并在 `startRiding` HEAD 事件驱动拦截兜底（`canSerialize` WrapOperation 放行"玩家载具"以保持玩家可被骑行为，1.21~1.21.8 双参/1.21.10+ 三参签名按宏分叉，26.2 覆盖同步）；交互新增 10 tick 冷却（无论放行/拒绝/失败都计），防连点刷字幕与高频重复交互
- **假人脑子近战对假人零伤害（关键接口分派缺陷）**：`PryMob.doHurtTarget` 原为接口 default 方法——Mixin 的接口合并（`implements` 嫁接）环境下接口 default 体分派不可达，`Player#attack` 从未被执行，导致所有近战模式（zombie/babyzombie/irongolem/spider/wolf/enderman）对假人目标零伤害（对玩家/生物目标此前未验证，同样受影响）。本地 RCON 竞技场逐层插桩定位（attack 分支确认执行 → doHurtTarget 未达 → 接口 default 分派问题），现改为抽象方法由 `ServerPlayerMobMixin` 直接实现，方法体落在目标类上必然可达；实测鸡骑士骑鸡将目标假人砍至 9.3 血。附带发现：`ServerPlayer#hurt` 带 `spawnInvulnerableTime`（出生保护 60 tick）与 Fabric `ALLOW_DAMAGE` 事件（本模组 pvp 拦截）等正常关卡，均非本次根因
- **假人脑子"小僵尸=鸡骑士"**（Wiki 对齐）：`babyzombie` 模式挂载后自动检索 5 格内最近的鸡并骑乘（实体原生 `startRiding`，Carpet `/player mount` 同路径，零实体生成）；鸡作为载具的缓慢下落/免摔伤由鸡原生物理免费获得；骑乘态移动把追击意图喂给鸡的原生导航器（每 tick 强制喂食，防止被鸡自己的漫步 goal 抢占导航）——等价原版"幼年僵尸控制鸡"；落水立即 `stopRiding`（Wiki：视线碰到水被赶下骑乘位置）；附近无鸡时保持地面小僵尸行为（呼应原版"5% 概率附近有鸡才成鸡骑士"）；移动速度按 Wiki"比成年僵尸快 50%"调至 1.5。碰撞箱缩小/盔甲外观缩小属身体红线范围不做
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
