# fakePlayerClones 验收记录

本文记录 26.3 生存开发服的服务端验收；不是图形客户端验收结论。
临时菜单探针只用于本次验证，已从最终源码移除。

## 环境

- 版本：26.3
- 世界：`accept_world`，超平坦，`difficulty peaceful`
- 模式：每个身体通过 `gamemode survival @s` 设为生存
- 验收方式：RCON 命令 + 服务端日志

## 菜单探针原始回包

以下为 RCON 会话的原始命令与回包（`python .zcode/rcon.py ...`）：

```text
> fakeclone probe C2Bot 2 chest
[clones] probe chest quick=0->1 carried=1xminecraft:emerald returned=true
> fakeclone probe C2Bot 2 craft
[clones] probe craft result=4 planks=0->4
> execute as @e[type=player] run data get entity @s Inventory
C2Bot has the following entity data: [{count: 4, Slot: 7b, id: "minecraft:oak_planks"}, {count: 1, Slot: 8b, id: "minecraft:diamond"}]C2Bot has the following entity data: [{count: 4, Slot: 7b, id: "minecraft:oak_planks"}, {count: 1, Slot: 8b, id: "minecraft:diamond"}]
```

```text
> fakeclone probe Tr5Bot 2 trade
[clones] probe trade emerald=1->0 diamond=0->1
> execute as @e[type=player] run data get entity @s Inventory
Tr5Bot has the following entity data: [{count: 1, Slot: 8b, id: "minecraft:diamond"}]Tr5Bot has the following entity data: [{count: 1, Slot: 8b, id: "minecraft:diamond"}]Tr5Bot has the following entity data: [{count: 1, Slot: 8b, id: "minecraft:diamond"}]
```

探针实现使用原版 `ChestMenu`、`CraftingMenu`、`MerchantMenu` 的
`clicked(..., ContainerInput.QUICK_MOVE / PICKUP)` 与 `quickMoveStack` 路径；
不是客户端数据包流。

## 其他补测原始结果

```text
拾取：3 个身体，地面 1 绿宝石 -> 三个 Inventory 均为 count=1，物品实体消失
换手：钻石剑主手 -> 三个身体 equipment.offhand 均为钻石剑，主手为空；换回恢复
耐久：僵尸 Health 20->17，三个身体钻石剑均 components {"minecraft:damage":1}
死亡：D4Bot 三身体，一个被 magic 杀死；剩余两个 Inventory []，世界 Apple count=3
单体退出：/fakeclone kill SoloBot 2 -> online 2->1，剩余 Inventory [apple 2]
give 去重：三个身体，主实例移到远处；distance=0..10 选中 #2/#3；
          give diamond 1 -> 三个共享 Inventory 均为 count=1（不是 2）
快捷栏/容器槽：item replace hotbar.0 / container.9 均落到对应共享槽
普通假人：规则关闭时 /player NormalBot spawn/kill 正常
```

## 构建与测试

- `./gradlew buildAndGather`：11 个版本 jar 全部生成
- 26.3 测试：项目测试共 198 项通过（0 失败、0 错误）
- 其中 `clones` 包专项测试 3 项：
  - `CloneProfileCopyTest`：profile/textures 属性复制
  - `CloneDisplayIdsTest`：显示 UUID 稳定、不同 cloneId 不冲突
- 其余 195 项是项目既有测试，不是分身专项测试

## 未完成验收

- 两名原版客户端同屏观察：Tab、皮肤、后来进服、重连、跨维度、重新追踪、单体移除
- 真人登录接管与真人/假人竞态
- 普通真实玩家路径、PlayerReplay 回放路径
- 真实客户端菜单数据包流（本次仅服务端菜单方法级探针）
- MSPT、分配量、加载区块数量

## 提交与产物

- 修复提交：`1bd8aaa3eab784288e3a2ed073149191cbc2586a`
- 构建产物 SHA256：

- `build/libs/carpet-pry-addition-v1.3.1-mc1.21.1.jar`  `bee18539197775fce65f97f0a53531bca29db717a275599244ba26b84d7a7e8f`
- `build/libs/carpet-pry-addition-v1.3.1-mc1.21.10.jar`  `0289894af42627798b4a048529eaca070db92ea5a07b64f8daa89d6cf99c4741`
- `build/libs/carpet-pry-addition-v1.3.1-mc1.21.11.jar`  `f273c0b41045dec118249dbc8737be6534236e2923f3d09eda06cab5218ef07b`
- `build/libs/carpet-pry-addition-v1.3.1-mc1.21.3.jar`  `1115a5cfebe8a17e42a6ff24f6e61660fbabb6d0e7ef6c80b34896d776f4d0c1`
- `build/libs/carpet-pry-addition-v1.3.1-mc1.21.4.jar`  `5d375f965f5a63de98719896f0130463a49cafc4b45bbc0b43568b09d8f9c11b`
- `build/libs/carpet-pry-addition-v1.3.1-mc1.21.5.jar`  `279a14e05616218b09715dbaec5ee361bea4faded0dce36e71b564e242473120`
- `build/libs/carpet-pry-addition-v1.3.1-mc1.21.8.jar`  `6acba3944791352e22ac1a974fd40f207233e4bff34b6c26787c72c80891efed`
- `build/libs/carpet-pry-addition-v1.3.1-mc1.21.jar`  `18758b44ff9ee090d20094a78d5b7e7b0435eaf96ef637293878f6c778858cea`
- `build/libs/carpet-pry-addition-v1.3.1-mc26.1.2.jar`  `4f4f39e40db8d639ac03ee0be8944436b28d5655d8546245768dcc3dd87fa411`
- `build/libs/carpet-pry-addition-v1.3.1-mc26.2.jar`  `87a7faaf14ea795123039cc6f9b05b2269fdd697a3619d66404a1a8795328ed1`
- `build/libs/carpet-pry-addition-v1.3.1-mc26.3.jar`  `a266ad6f3d76ee654bd6796cd2d0d4acfc29e0bdb35eae9c70f366eeaed4a75e`
