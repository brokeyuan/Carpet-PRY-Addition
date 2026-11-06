# Changelog

## [未发布]

### 新增


- 聊天惩罚（`chatPunishment`）：管理员经 /chatpunish 对指定玩家的公共聊天施加八种惩罚（禁言/胶带/猫娘/呆子/屏蔽词/村民/结巴/反转，每玩家一种），支持限时（s/m/h/d，缺省永久，按真实时间到期自动解除）；玩家名走原版档案解析、按 UUID 存于存档 `chatpunish.json`，重启与重连不丢失；改写走原版 ChatDecorator 扩展点、禁言经 ALLOW_CHAT_MESSAGE 取消广播，签名校验不受影响，不影响命令与私聊
- 同UUID假人分身（`fakePlayerClones`）：多个真正的 Carpet 假人共享同一 UUID 与皮肤同时在线，独立 Entity ID/位置/维度，整组共享背包（权威状态，同 Tick 修改不复制）；`/player <name> spawn` 增量生成分身，`/fakeclone kill` 全组正常退出并存档快照，`/fakeclone rejoin` 跨重启恢复整组位置与背包；同 UUID 真人登录保护；分身真实参与区块加载与怪物生成判定；规则关闭时普通假人行为不变

- 跨服传送（`serverTransfer`）：`/tpto` 展示可点击的服务器列表，点击即以本人身份走原版 transfer 协议转移到他服（一服一地址）；`/tptoset` 管理服务器列表（addserver / removeserver / reload），配置存于 `config/carpet-pry-addition/tpto.json`；目标服需开启 `accepts-transfers`
- 计时器（`countdown`）：新增 /timer 统一入口，正计时（up）与倒计时（down）共用暂停/恢复/停止控制，四种展示位置（bar/center/actionbar/both）与三档效果（simple/normal/fancy）自由搭配；最短 `/timer down 60s`、`/timer up` 即可用，选项词任意顺序，`--` 后整段为名称，silent 一词全静音。fancy 档 60/30 秒节点提示、最后 10 秒逐秒递增音高；倒计时归零展示 3 秒并留结果，正计时停止结算毫秒级用时；最后一份结果存内存可经 /timer 查看。旧 /countdown 语法保留为兼容入口，与 /timer 共用同一会话。默认开启，规则关闭立即清理显示且不产生虚假完成结果
- `SERVER` 规则类别：标记该类别的规则值跟随服务器（钉值存于 `config/carpet-pry-addition/server-rules.json`，启动时压过存档 carpet.conf），权限类规则不再随存档漂移
- 数据文件版本号信封：降级运行旧版模组不回写、不破坏新格式文件
- 假人脑五脑扩容与村民农民（`fakePlayerBrain`，26.3 字节码对齐）：villager 认领堆肥桶转农民（名牌 [村民-农民]），工作时段收割成熟作物、真实种子补种、堆肥每种保留 10 个且满级先取骨粉，收割/补种/堆肥全走玩家原生破坏与物品使用流程；新增 coppergolem（铜箱取货单趟 ≤16，分类放入空箱或已有同物的箱子，玩家容器菜单点击完成，仅 1.21.10+）、allay（副手样品按物品匹配收集匹配掉落物，交付给执行命令玩家或 30 秒喜好音符盒）、fox（采莓/吃背包食物/捕食鸡兔/避狼）、witch（真实库存药水的喝/投原版决策树，缺药不投）；原版村民直写方块、女巫凭空合成药水、铜傀儡直读容器等身体能力不映射并在类注与文档披露
- 假人战斗队伍（`fakePlayerBrain`）：`/player <name> brain team` 查询、`brain team <队名>` 加入或更换（中英文队名，Tab 补全）、`brain team off` 退出；同队假人互为友军（跨脑模式）——不互相索敌、不反击、不参与群体围攻、凝视不激怒、出手不命中，战斗中改同队立即撤销非法目标；异队/无队按各模式原有规则交战，被动模式与真人不受影响；队伍独立于脑子与名牌后缀队伍，换脑/brain off/下线重上保留，重启清空

### 新增（行为）

- 各敌对模式补原版同位空闲行为：看人（LookAtPlayer）与随机张望（RandomLookAround）；僵尸/小僵尸/骷髅/凋灵骷髅/掠夺者/卫道士/蜘蛛/末影人/铁傀儡/猪
- 村民日程作息（一期骨架）：按原版 `villager_schedule` 时间线（2000 上工 / 9000 钟下集合 / 12000 回床）驱动床位/工作站/集合点占用登记（容量与释放/失效/停服清理对齐原版占用语义），到位后走玩家原生 `startSleeping` 真睡、天亮起；被打恐慌与僵尸规避按原优先级抢占、险情结束日程自动恢复；换脑/下线/死亡释放全部占用并唤醒。职业交易、繁殖、幼年玩耍未实现（后续阶段）
- 三期修正（外部审查 12 项）：骷髅武器装配改 NONE/MELEE/RANGED 三态（修复空手/持剑首次挂脑没有任何攻击 Goal）；选择器对齐原版"先判旗标资格再 canUse"（被恐慌挡住的候选不再触发评估副作用）；村民 POI 占用移到 start 提交、stop 只释放自己名下按获取时维度的资源（消灭幽灵占用）；床规范化到床头并复核真实 OCCUPIED（不抢真人/原版村民已用床，床头床尾同一资源）；不可达工作站/床退避换点（时长制黑名单，环境改变自动重试）；集合点容量对齐原版 meeting=32（原为实际 1 人）；工作站补盔甲匠高炉；共享调度逐计数审计——Nearest 构造器折半（randomInterval 10→5 次评估）、失视线记忆 60 tick 口径、RandomLook 递减移回 tick（rUE 每 tick）、史莱姆跳跃等待对齐原版 nextInt(20)+10 激怒 ÷3（原固定 12）、张望/看人恢复原版概率掷骰（不再固定节拍）；狼羊驼规避按原版 !isTame 门移除（驯服态模式与野生狼行为互斥）
- 四期修正（三期复核 5 项）：床头规范化接入睡眠主流程全链（扫描→去重→认领→入睡→释放统一用床头，离床尾更近也按床头登记，getSleepingPos 床头/床尾都算本人占用，不再出现同床两键与醒睡循环）；史莱姆空闲跳行等待跨落地周期保留（start 不再清零，canContinueToUse 复用 canUse 常驻运行，落地从剩余等待继续，不立即重跳）；漫步与随机游泳恢复原版 nextInt(reducedTickDelay(120)) 掷骰（删固定 60 次评估节流），凝视激怒同步对齐 Nearest 基座掷骰；POI 寻路"连续失败"改真连续序列（实际进展即清零，与无进展宽限、总寻路预算三通道分离，到位停留暂停预算）；无进展宽限改 gameTime 差值 20 游戏刻（原次数制实际约 40 刻），start/stop 全状态复位
- 五期修正（四期复核 3 项）：床连接方向纠正——getConnectedDirection(HEAD) 已内部反向指向床尾，删除移植层多余的 getOpposite（双版本字节码核实；旧实现检查床头前方格子，完整床被误拒）；史莱姆空闲跳行双时钟——Goal 改 rUE（跳跃等待每游戏 tick 递减，不再慢一倍），方向重掷在 tick 内奇偶分频保持原版每 2 tick 评估（不因 rUE 加倍）；睡眠寻路预算在入睡期间持续刷新差值基准（夜间睡眠时长不进预算，醒来重新寻路不会立即拉黑有效床）。测试侧新增生产 readBedState 读块缝（真实 RED_BED BlockState 四 FACING 全链验证，不再覆写生产方向计算），床方块 Holder 手动绑定 #minecraft:beds 标签对齐服务端语义
- 六期修正（五期复核 3 项）：史莱姆空闲跳行 tick 顶部加 onGround/目标双门（腾空与目标出现时不递减等待、不输出移动——原版 MoveControl onGround 前置门对齐）；床配对检查补全——生产 isOtherHalfStillBed/isOtherHalfOf 增加 PART=FOOT、FACING 一致、FOOT 回指床头三重校验（旁边另一床的 HEAD 或朝向不符的 FOOT 不能冒充床尾），probeBed 映射点非床头拒绝；小僵尸骑鸡载客资格——搜索与上车前排除已有乘客（isVehicle）/正在骑乘其它实体/死亡/移除/幼年/跨维的鸡，骑乘关系登记表只记本脑建立的，新增 PlayerList.remove HEAD 注入在 save 与 RootVehicle 卸载链前驱散骑乘（下线时鸡留在世界不随玩家卸载），换脑/off/onDetach 只释放本脑关系

- `playerReplay` 玩家回放：录制行为并召唤幻影按原视觉轨迹重演，幻影为纯数据包实体，不改动世界；新增无参数聊天面板（录制/列表/详情/回放按钮），录制支持暂停续录（段边界不插值），离线服自动回填皮肤（/playereplay）。操作成功后自动刷新面板，录制时长走动作栏提示；录像列表单行条目、操作集中详情页；新增按玩家持久化的录像收藏（/playereplay fav|unfav|favlist）。
- `fakePlayerScript` 假人脚本：录制操作后交给自动召唤的 Carpet 假人按原时间轴重演操作，不复制结果、不寻路、轮内不纠偏；支持设置页（假人名/次数/无限/轮间回起点或接续）、完成自动下线、brain 互斥、无参数聊天面板（/playerscript）。
- `playerReplay` / `fakePlayerScript` 多人回放系统：准备页先选录制对象（普通邀请需同意、管理员可直接添加且被添加者不能主动退出），任务 UUID 稳定，录制中可继续邀请并从加入时刻采集；一份录像一个共同时间轴，每名参与者独立视觉/输入轨道与在线片段，离线空档不写帧、重连按 UUID 续录；公开/私有访问、个人收藏、创作者/管理员管理与按观看者独立的多人幻影实例；停服保存并结束、开服不恢复，崩溃只恢复最近检查点并标记异常恢复。

### 修复

- `fakePlayerScript`：快捷栏槽位按 Carpet 1~9 接口转换、丢弃改为当前选中槽；真实松弓 `USE_UP.hand=-1` 可释放；末帧与轮末帧留一个实体 Tick 消费后再结算/下线，轮间回起点不再提前一帧；暂停恢复重建挖掘/使用长按，录制暂停期松开不再遗留；补实体右键（INTERACT/INTERACT_AT 去重）；外部假人接管先清原动作并强制首帧；暂停状态检查死亡/断线；保存后轮换数量改为“当前-上限”；设置绑定录像 ID 并自动重绘；仅开假人脚本也能进录像列表；输入轨道摊销扩容、事件游标与同 Tick 序号计数
- `fakePlayerScript`：1.21/1.21.1 修正 xxa/zza 轴映射；普通步行不发送输入包，`run` 默认拒绝并需 `force` 显式执行骑乘/静止脚本

### 变更

- 配置分层：`/tppset` 站点、`/pvp` 和平名单、`/patnod` 偏好随存档迁移至 `<存档>/carpet-pry-addition/`，旧版 `config/carpet-pry-*.json` 首次启动自动收编；配置文件懒创建，未使用功能不再生成空文件
- 规则注册改用 carpet 官方注解解析（`parseSettingsClass`），移除反射注册器；启动不再逐条输出 `Registered rule` 日志
- `playerHat` 支持主手对空气右键整组换帽，避让工具、使用功能和副手，新增数据包排除标签
- `playerHat` 头部已有正常头盔（含海龟壳）或不死图腾时右键快捷换帽不再触发，防止误摘防护装备；`/hat` 命令不受限
- TPP 站点支持按内部名或显示名前缀补全显示名，如 `qwp` 补全为 `全物品`
- 同UUID假人分身：共享背包改为槽位级权威存储（原地消耗/耐久/容器操作同一物品状态），整组退出先快照并只保存一次权威存档，`rejoin` 预检维度并原子恢复；新增 `/fakeclone kill <name> <cloneId>` 单体正常退出；规则有在线/退出中成员时拒绝关闭

### 修复

- 版本配置：26.1.2/26.2 的 mixin 配置必须完整继承主线注册，补回聊天惩罚与 21 项假人分身；新增源码配置集合与重复项回归检查，最终 JAR 逐版核对引用类存在性。
- 名牌队伍：临时队按实际 PlayerTeam 对象及会话创建身份记录；同名既有队伍不认领、不改写、不删除，删除后同名重建不受旧会话影响，临时队仍有其他成员时保留队伍。
- 会话：自动下线/detach 先核对控制器绑定实例，同 UUID 旧实例不再清新实例的脑、keep、队伍与岗位；鸡骑士只释放本脑建立的载具关系，手动换乘船/矿车不再被强制下车。
- 铜傀儡：暂停按有无余货恢复寻目的地/寻来源，运行中的 SEEK_DEST 同样遵守 140t 冷却；取货按真实库存差量记账（原栈 60、只并入 4 只记 4），放货前保存点击前计数，只投本趟数量并保护私人/异组件库存；双箱两半归一到同一容器身份，菜单按真实容器对象校验。
- 村民：堆肥桶岗位由控制器持有，集合/睡眠/恐慌暂停不释放；职业有效性同时核查维度、方块与实例持有者，损毁/跨维/卸脑释放；岗位登记改为实例级所有权，旧 UUID 实例不得清新实例岗位。
- 女巫：索敌排除自身与创造/旁观玩家；自救只喝饮用型药水，攻击药按原版 60t 节拍、10 格射程持续接近/瞄准/出手，出手前复核目标、同队、视线与射程；治疗喷溅不再因自身低血扔向敌人。
- 狐狸/悦灵：狐狸按玩家真实饥饿与 FOOD 组件进食，一轮结束即让出 MOVE；悦灵音符盒监听随脑会话并记录注册世界，跨维先注销再注册、喜好点带维度；交付冷却在执行路径生效，副手样品永不被整组交付。
- 悦灵与名牌队伍：`brain allay` 使用执行者 UUID 作为默认交付对象（控制台仍允许无主人，只对有效音符盒交付），keep 补挂复用同一 owner；计分板临时队按会话记录实际队名，只有假人仍属本会话临时队时才恢复原队伍，外部改队/退队保留，重复卸载与同 UUID 旧实例事件不再误恢复或残留临时队。
- 引诱（pig）：冷却调度对齐原版（calmDown 在 canUse 内自减），修复外层间隔放大导致收起食物后长时间才重新被引诱的问题（二期修正见下）
- 狼：优先级对齐原版（受伤先逃跑，扑咬让位），补扑击（2~4 格扑跳）、乞食（8 格内持食物歪头注视）、闲逛与观察行为
- 骷髅：补避日决策（二期已分离 RestrictSun/FleeSun 并纠正免疫日照的错误结论，见下）
- 蜘蛛：补扑击（贴近目标扑跳）
- 末影人：凝视激怒与末影螨索敌半径修正为原版的 64 格
- 假人脑子停止行动：tick 异常不再卸脑清 keep（保活 + 限频日志，连续崩溃达阈值才卸脑）；sweep 改按实例身份判定会话有效（同 UUID 影子假人/真人不再误拆健康会话）；索敌链新增陈旧目标回收（外部直写目标死亡/跨维度后不再粘滞）
- 寻路可达性：新增水面涉水节点与 2~3 格下落（追进水里的目标不再站桩）；卡死检测只看水平位移（撞墙跳不再无限循环）；`moveTo(path)` 终点修正与近战启动即接管导航
- 逐种模式行为对齐原版字节码（26.3/1.21.11 双段核实）：索敌范围按各自原版 FOLLOW_RANGE 口径修正（26.3 字节码逐模式核实：zombie 系与 drowned 35、skeleton 系 16、pillager 32、vindicator 与 piglinbrute 12、enderman 64（含凝视检测）、spider/irongolem/piglin 基座 16——原实现误写 16~24 或 20，统一后与原版一致）；僵尸头减半口径按原版 MOB_VISIBILITY 组件语义（35→17.5）；骷髅系攻击间隔 20→40、射程 8→15；蜘蛛近战 1.25 并补铁傀儡目标与犰狳规避；掠夺者/卫道士补 Creaking 规避与 Raider 群体警报；铁傀儡补反击链与清怪透墙口径；末影人补末影螨目标、移除随机漫游（原版概率 0）；狼补恐慌 1.5、跟随 10/2 启停阈值（二期修正为纯阈值，无慢速分档）；猪补食物引诱（#pig_food）；鱼恐慌 1.25、游动 1.0；村民规避距离对齐 8 格
- `/player <name> brain` 状态查询输出诊断行（目标/寻路/移动四态），区分"脑子被卸载/有脑无目标/导航受阻/移动停滞"
- 二期调度链修正（1.21/1.21.11/26.3 三版字节码核实）：原版 `Mob.serverAiStep` 以 `(tickCount+id)%2` 奇偶门驱动选择器——偶相清理+候选评估+推进，奇相仅推进 `requiresUpdateEveryTick` 目标；`reducedTickDelay` 是"游戏 tick→评估次数"的换算。移植选择器同构实现，近战/远程/随机张望按原版标记 rUE，各 Goal interval 改按评估次数口径
- 狼恐慌按伤害类型标签（原版 `TamableAnimalPanicGoal(1.5, PANIC_ENVIRONMENTAL_CAUSES)`）：普通近战/远程/箭矢挨打不恐慌、走 HurtBy 反击；火/岩浆/冰冻/雷击等环境伤害才恐慌；恐慌窗口对齐原版 40 tick
- 引诱冷却与续行：calmDown=reducedTickDelay(100)=50 次评估（恰 100 游戏刻重扫，旧实现 ~200 tick）；`canContinueToUse` 末尾复跑 canUse 重扫最近持食物玩家（走远/收起食物/死亡即结束或切换）；停走距离对齐原版 2.5；猪 canScare=false 不受玩家动作吓跑（纠正此前误加的受惊语义）
- 狼跟随主人：10/2 为启动/结束阈值（旧实现"超过 2 格就启动"且无依据的 0.6 近距慢速分档一并移除）；跟随期间水路代价临时置 0（原版 setPathfindingMalus 同款）
- 扑击生命周期（蜘蛛/狼）：旗标补 MOVE（腾空期间压制移动类目标）、概率 `nextInt(reducedTickDelay(5))`、`canContinueToUse=!onGround` 腾空持续至落地
- 骷髅避日分离双决策：RestrictSun（不要求着火，开寻路避日开关并把路径截断到露天节点前）与 FleeSun（着火后 10 次随机候选找荫蔽）；纠正"1.21.5+ 骷髅免疫日照"的错误结论——1.21.11 与 26.3 字节码均有 BURN_IN_DAYLIGHT 日照点燃（玩家身体不点燃，属身体差异如实披露）；候选范围对齐原版（x/z ±10、y -3~+2，非露天且怪物走位价值<0）
- 骷髅索敌与武器形态分离：索敌谓词去除持弓要求（持剑/空手照常索敌并近战接敌）；`reassessWeaponGoal` 持弓/卸弓即时切换远程/近战形态（原版同位 4）
- 狼乞食：骨头或狼食物都触发（1.21.11/26.3 字节码无驯服门）；注视时长按 adjustedTickDelay 折算（40~80 游戏刻）
- 猪引诱补胡萝卜钓竿（原版同位双 TemptGoal：#pig_food 与 carrot_on_a_stick）
- 羊驼规避（狼）：24 格、羊驼强度 ≥ nextInt(5) 掷骰触发、逃跑期间持续清空攻击目标（原版 Wolf$WolfAvoidEntityGoal 逐条对齐）
- 规避类 Goal 续行对齐原版（路径走完/威胁消失/逃出范围三条件）
- `chatPunishment`：惩罚只处理普通公共聊天，私聊与 `/say`、`/me` 等命令不再被改写或取消；空发送者不再报错；时长乘法与到期加法溢出按非法时长拒绝
- `chatPunishment`：数据文件损坏或版本过新时只读并保留原文件，设置/解除按落盘结果反馈、列表报只读，失败不误报成功；文档不再承诺隐藏签名原文
- `whoCalledMe`：改接 Fabric 装饰事件，避免接管 `getChatDecorator` 旁路其他模组的装饰器；提醒与高亮改看转换后的显示文本

## [1.3.1] - 2026-10-21

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
