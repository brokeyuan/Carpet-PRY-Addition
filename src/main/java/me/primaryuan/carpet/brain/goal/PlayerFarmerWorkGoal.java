package me.primaryuan.carpet.brain.goal;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import me.primaryuan.carpet.brain.VillagerPoiRegistry;
import me.primaryuan.carpet.brain.VillagerSchedule;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;

import net.minecraft.world.level.block.state.BlockState;

/**
 * 村民农民工作目标（← 原版 {@code HarvestFarmland} + {@code WorkAtComposter}，
 * 26.3 字节码逐条对齐）。挂在 {@link PlayerGotoPoiGoal} 的工作站认领骨架上：
 * 认领/退避/释放生命周期全部继承（不可破坏既有语义），本类只做"到位之后干活"。
 *
 * <p><b>原版行为依据（javap 核实）</b>：</p>
 * <ul>
 *   <li>HarvestFarmland：MOB_GRIEFING 门；以自身为中心 ±1 的 3×3×3 扫描，
 *       有效位 = (作物且 {@code isMaxAge}) 或 (空气且下方是耕地)；随机取一块；
 *       走到 1.0 格内 {@code destroyBlock(pos, true, self)}（掉落开启）；若原位
 *       变空气且下方是耕地且背包有 {@code #minecraft:villager_plantable_seeds}
 *       的 {@code BlockItem} 则补种；会话上限 200 tick，重选间隔 20 tick、
 *       休整间隔 40 tick；非成熟作物位出队重选。</li>
 *   <li>WorkAtComposter：堆肥物 = 小麦种子 + 甜菜种子（原版硬编码双元素表）；
 *       等级 8 先 {@code extractProduce} 取骨粉；倒序扫背包槽，按种类累计
 *       {@code min(seen-10, 预算20, 数量)} —— <b>每种保留 10 个</b>；等级到 7
 *       结束本轮投放。</li>
 * </ul>
 *
 * <p><b>玩家身体映射（披露差异）</b>：原版村民绕过交互直接写方块
 * （{@code setBlockAndUpdate} 补种 / 直接调 {@code ComposterBlock.insertItem}
 * 投堆肥）——假人是玩家身体，全部动作改为玩家原生物品使用：选物到手 =
 * {@code Inventory.setSelectedItem}/{@code pickSlot}（原版 pick-item 数字键
 * 交换语义）；补种 = 手持真实种子对耕地 {@code useItemOn}（BlockItem 摆放
 * 校验比村民直写更严格，失败不消耗）；堆肥/取骨粉 = 手持真实材料对堆肥桶
 * {@code useItemOn}，chance 表/物品消耗/等级推进/音效全走原版方块逻辑
 * （失败也消耗 1 个，与玩家行为一致）。收割走 {@code gameMode.destroyBlock}
 * （玩家即时破坏，LootTable 正常掉落，拾取走玩家碰撞拾取）。</p>
 *
 * <p><b>未实现（原版有、本轮不做，如实披露）</b>：面包合成（makeBread：
 * 村民无工作台直接把小麦合成面包——玩家身体没有对应免合成能力，超出本轮
 * 允许动作集）；职业交易锁定；自动开垦/扩建农场。出差点不可达时按本项目
 * POI 退避口径拉黑 200 tick（原版无退避会原地反复重选，此处为防卡死补强）。</p>
 */
public class PlayerFarmerWorkGoal extends PlayerGotoPoiGoal {

    /** 原版 WorkAtComposter.COMPOSTABLE_ITEMS（26.3 字节码：小麦种子+甜菜种子） */
    private static final List<Item> COMPOSTABLE_ITEMS = List.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS);

    /** 每种堆肥物保留数量（原版 seen-10 保留语义） */
    private static final int COMPOST_RESERVE_PER_TYPE = 10;
    /** 单轮投放预算（原版 budget=20） */
    private static final int COMPOST_BATCH_BUDGET = 20;
    /** 堆肥桶等级到达即收手的档位（原版投放至 7 结束本轮） */
    private static final int COMPOST_STOP_LEVEL = 7;
    /** 农活会话上限（原版 canStillUse：200 tick） */
    private static final int WORK_SESSION_TICKS = 200;
    /** 重选间隔 / 休整间隔（原版重选 20、stop 后 40） */
    private static final int CHAIN_COOLDOWN_TICKS = 20;
    private static final int REST_COOLDOWN_TICKS = 40;
    /** 出差点退避（项目 POI 退避口径；原版无退避，防卡死补强） */
    private static final long EXCURSION_BLACKLIST_TICKS = 200;
    /** 农活扫描半径（原版：自身 ±1） */
    private static final int FARM_SCAN_RADIUS = 1;

    /** 玩家身体访问（父类 player 字段 private，经假面取用） */
    private net.minecraft.world.entity.player.Player bodyPlayer() {
        return this.mob.asPlayer();
    }

    private Level bodyLevel() {
        return this.mob.asPlayer().level();
    }

    private net.minecraft.world.entity.player.Inventory bodyInventory() {
        return this.mob.asPlayer().getInventory();
    }

    /** 世界读方块缝（离线测试覆写：桩世界读不到真实方块） */
    protected BlockState readStateAt(Level level, BlockPos pos) {
        return level.getBlockState(pos);
    }

    /** 出差退避表：pos → 解封 gameTime */
    private final Map<BlockPos, Long> excursionBlacklist = new HashMap<>();
    /** 当前出差目标（null = 驻站） */
    private BlockPos excursionDest;
    /** 农活重入节奏（原版 nextOkStartTime 语义） */
    private long nextFarmOkTime = Long.MIN_VALUE;
    /** 会话休整截止 gameTime */
    private long restUntil = Long.MIN_VALUE;
    /** 连续农活 tick 数（会话上限用） */
    private int farmSessionTicks;

    /** 认领堆肥桶成功回调（控制器登记职业归属；可为 null） */
    private final java.util.function.Consumer<BlockPos> onComposterClaimed;
    /** 有效长期岗位判定（控制器持有；Goal 暂停不释放） */
    private final java.util.function.BiPredicate<Level, BlockPos> isLongTermStation;

    public PlayerFarmerWorkGoal(PryMob mob, java.util.function.Consumer<BlockPos> onComposterClaimed) {
        this(mob, onComposterClaimed, (level, pos) -> false);
    }

    public PlayerFarmerWorkGoal(PryMob mob,
                                java.util.function.Consumer<BlockPos> onComposterClaimed,
                                java.util.function.BiPredicate<Level, BlockPos> isLongTermStation) {
        super(mob, VillagerSchedule.Activity.WORK, 0.5);
        this.onComposterClaimed = onComposterClaimed;
        this.isLongTermStation = isLongTermStation;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
    }

    @Override
    protected boolean retainsClaimOnStop(BlockPos pos, Level level) {
        // 集合/睡觉/恐慌/工作 Goal 暂停：有效长期岗位不释放；岗位损毁/跨维
        // 由控制器判定为无效，仍然释放。
        return this.isLongTermStation.test(level, pos);
    }

    /** 农民求职只接受堆肥桶：讲台/木桶/高炉等不占本 Goal（八期评审） */
    @Override
    protected boolean isPoiBlock(Level level, BlockPos pos) {
        return this.readStateAt(level, pos).is(Blocks.COMPOSTER);
    }

    @Override
    protected List<BlockPos> nearbyPoiCandidates() {
        return PlayerGotoPoiGoal.scanFor(this.bodyLevel(), this.mob.blockPosition(),
                16, s -> s.is(Blocks.COMPOSTER));
    }

    @Override
    public void start() {
        super.start();
        if (this.onComposterClaimed != null) {
            BlockPos station = this.claimedStationPos();
            if (station != null && this.isFarmerStation()) {
                this.onComposterClaimed.accept(station);
            }
        }
    }

    // ==================== 目的地接缝实现 ====================

    @Override
    protected BlockPos currentDestination() {
        return this.excursionDest != null ? this.excursionDest : this.claimedStationPos();
    }

    /** 认领工作站的状态（职业判定/外部查询；未认领为 null） */
    public BlockState claimedStationState() {
        BlockPos pos = this.claimedStationPos();
        return pos == null ? null : this.readStateAt(this.bodyLevel(), pos);
    }

    @Override
    protected void onArrivedAtStation() {
        long now = this.bodyLevel().getGameTime();
        if (this.farmSessionTicks >= WORK_SESSION_TICKS) {
            this.restUntil = now + REST_COOLDOWN_TICKS; // 会话满：原地休整 40t
            this.farmSessionTicks = 0;
        }
        if (now < this.restUntil) {
            return;
        }
        BlockState station = this.claimedStationState();
        if (station == null) {
            return;
        }
        if (station.is(Blocks.COMPOSTER)) {
            this.compostAt(station, this.claimedStationPos());
        }
        this.tryStartExcursion();
    }

    @Override
    protected void onArrivedAtDestination(BlockPos dest) {
        Level level = this.bodyLevel();
        BlockState state = this.readStateAt(level, dest);
        // 1) 成熟作物：玩家原生破坏（LootTable 正常掉落）；非成熟：出队回站
        if (state.getBlock() instanceof CropBlock crop) {
            if (!crop.isMaxAge(state)) {
                this.giveUpExcursion(dest, CHAIN_COOLDOWN_TICKS);
                return;
            }
            this.mob.pryBreakBlock(dest);
        }
        // 2) 原位空气 + 下方是耕地 + 有真实种子 → 玩家右键补种
        state = this.readStateAt(level, dest);
        if (state.isAir() && this.readStateAt(level, dest.below()).is(Blocks.FARMLAND)) {
            this.replantAt(dest);
        }
        // 3) 连作：原地扫自身 ±1 找下一农位（原版 HarvestFarmland 逐块链作），
        //    不回堆肥桶；无农位才回站（堆肥/休整）
        BlockPos next = this.scanFarmAroundSelf();
        if (next != null && !next.equals(dest)) {
            this.excursionBlacklist.remove(next);
            this.excursionDest = next;
            this.nextFarmOkTime = level.getGameTime() + CHAIN_COOLDOWN_TICKS;
            this.resetDestinationTracking();
            return;
        }
        this.nextFarmOkTime = level.getGameTime() + REST_COOLDOWN_TICKS;
        this.clearExcursion();
    }

    @Override
    protected void onDestinationFailed(BlockPos dest) {
        this.giveUpExcursion(dest, EXCURSION_BLACKLIST_TICKS);
    }

    // ==================== 农活（原版 HarvestFarmland） ====================

    /** 自身 ±1 扫描有效农位（成熟作物 / 空气+耕地），随机取一作出差目标 */
    private void tryStartExcursion() {
        long now = this.bodyLevel().getGameTime();
        if (now < this.nextFarmOkTime || !(this.bodyLevel() instanceof ServerLevel level)
                || !this.mobGriefing(level)) {
            return;
        }
        BlockPos chosen = this.scanFarmAroundSelf();
        if (chosen == null) {
            return;
        }
        this.excursionBlacklist.remove(chosen);
        this.excursionDest = chosen;
        this.farmSessionTicks++;
        this.resetDestinationTracking();
    }

    /** 农位扫描（自身 ±1，退避过滤，随机取一；无则 null） */
    private BlockPos scanFarmAroundSelf() {
        long now = this.bodyLevel().getGameTime();
        this.excursionBlacklist.entrySet().removeIf(e -> now >= e.getValue());
        Level level = this.bodyLevel();
        List<BlockPos> valid = new java.util.ArrayList<>();
        BlockPos origin = this.mob.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-FARM_SCAN_RADIUS, -FARM_SCAN_RADIUS, -FARM_SCAN_RADIUS),
                origin.offset(FARM_SCAN_RADIUS, FARM_SCAN_RADIUS, FARM_SCAN_RADIUS))) {
            if (this.isValidFarmPos(level, pos.immutable())) {
                valid.add(pos.immutable());
            }
        }
        if (valid.isEmpty()) {
            return null;
        }
        BlockPos chosen = valid.get(this.mob.getRandom().nextInt(valid.size()));
        this.excursionBlacklist.remove(chosen);
        return chosen;
    }

    /** 原版 validPos：成熟作物 或 空气+下方耕地 */
    private boolean isValidFarmPos(Level level, BlockPos pos) {
        BlockState state = this.readStateAt(level, pos);
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            return true;
        }
        return state.isAir()
                && this.readStateAt(level, pos.below()).is(Blocks.FARMLAND);
    }

    /** MOB_GRIEFING 门（1.21.11 起游戏规则 API 重做：字段名与读取方法都变了；
     *  桩世界 gamerule 链不可达，离线测试覆写本缝模拟规则开关） */
    protected boolean mobGriefing(ServerLevel level) {
        //#if MC >= 12111
        return level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING);
        //#else
        //$$ return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        //#endif
    }

    /** 玩家右键补种：选中真实 {@code #villager_plantable_seeds} BlockItem 对耕地使用 */
    private void replantAt(BlockPos cropPos) {
        Inventory inv = this.bodyInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS)
                    || !(stack.getItem() instanceof BlockItem)) {
                continue;
            }
            this.equipSlot(inv, i);
            // 摆放成功与否由 BlockItem 原生校验（耕地/光照），失败不消耗
            this.mob.pryUseItemOnBlock(cropPos.below(), InteractionHand.MAIN_HAND);
            return;
        }
    }

    private void equipSlot(Inventory inv, int slot) {
        if (slot < Inventory.getSelectionSize()) {
            pryEquipSlot(inv, slot);
        } else {
            inv.pickSlot(slot);
        }
    }

    // ==================== 堆肥（原版 WorkAtComposter） ====================

    /**
     * 堆肥循环的玩家化转写：满级先取骨粉；倒序扫槽，按种类累计保留 10、
     * 预算 20，逐次右键（每次消耗 1，chance 表原生生效），等级到 7 收手。
     */
    private void compostAt(BlockState station, BlockPos pos) {
        Level level = this.bodyLevel();
        if (station.getValue(ComposterBlock.LEVEL) >= ComposterBlock.MAX_LEVEL) {
            this.mob.pryUseItemOnBlock(pos, InteractionHand.MAIN_HAND); // 取骨粉（弹落自动拾取）
            return;
        }
        Inventory inv = this.bodyInventory();
        int budget = COMPOST_BATCH_BUDGET;
        Map<Item, Integer> seen = new HashMap<>();
        // pickSlot 装备会把栈从背包槽移进快捷栏：按栈身份跳过已装备组，
        // 防止倒序扫描二次命中同一物理栈（seen 翻倍 → 保留额度误判）
        java.util.Set<ItemStack> handled =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int i = inv.getContainerSize() - 1; i >= 0 && budget > 0; i--) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || handled.contains(stack)
                    || !COMPOSTABLE_ITEMS.contains(stack.getItem())) {
                continue;
            }
            Item item = stack.getItem();
            int seenTotal = seen.merge(item, stack.getCount(), Integer::sum);
            int allowed = Math.min(Math.min(seenTotal - COMPOST_RESERVE_PER_TYPE, budget), stack.getCount());
            if (allowed <= 0) {
                continue;
            }
            budget -= allowed;
            this.equipSlot(inv, i);
            handled.add(this.mob.getMainHandItem());
            for (int k = 0; k < allowed; k++) {
                if (this.mob.getMainHandItem().isEmpty()
                        || !this.mob.getMainHandItem().is(item)) {
                    break; // 手上这组耗尽（原生消耗，不回写）
                }
                this.mob.pryUseItemOnBlock(pos, InteractionHand.MAIN_HAND);
                BlockState after = this.readStateAt(level, pos);
                if (after.getValue(ComposterBlock.LEVEL) >= COMPOST_STOP_LEVEL) {
                    return; // 原版语义：等级到 7 结束本轮投放
                }
            }
        }
    }

    // ==================== 出差退避与清理 ====================

    /** 放弃出差：拉黑该点 → 回站休整（岗位认领不动） */
    private void giveUpExcursion(BlockPos dest, long blacklistTicks) {
        this.excursionBlacklist.put(dest,
                this.bodyLevel().getGameTime() + blacklistTicks);
        this.nextFarmOkTime = this.bodyLevel().getGameTime() + REST_COOLDOWN_TICKS;
        this.clearExcursion();
    }

    private void clearExcursion() {
        this.excursionDest = null;
        this.resetDestinationTracking();
    }

    /** 认领岗位是否为堆肥桶（职业判定用） */
    public boolean isFarmerStation() {
        BlockState station = this.claimedStationState();
        return station != null && station.is(Blocks.COMPOSTER);
    }

    /** 堆肥桶岗位是否由本人持有（多假人不重复占岗由 VillagerPoiRegistry 保证） */
    public boolean hasComposterClaim() {
        BlockPos pos = this.claimedStationPos();
        return pos != null && this.isFarmerStation()
                && VillagerPoiRegistry.isClaimedBy(this.mob.asPlayer().getUUID(), this.bodyLevel(), pos);
    }
}
