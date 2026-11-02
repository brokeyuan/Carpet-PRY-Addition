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
