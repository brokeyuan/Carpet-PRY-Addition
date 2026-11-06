package me.primaryuan.carpet.brain.goal;

import java.util.EnumSet;
import java.util.List;

import me.primaryuan.carpet.brain.PlayerGoal;
import me.primaryuan.carpet.brain.PryMob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 悦灵收集目标（← 原版 {@code AllayAi} 拾取/交付骨架，26.3 字节码核实）。
 * 玩家身体限制下的步行收集与交付：不飞行、不悬浮、不隔空拾取。
 *
 * <p><b>原版行为依据（javap）</b>：物品匹配 = {@code ItemStack.isSameItem}
 * 且非"内容不符的药水"（26.3 {@code Allay} 字节码 hasNonMatchingPotion 守卫，
 * 同为药水时按内容匹配）——<b>不是完整 NBT 相等</b>；音符盒经
 * {@code GameEvent.NOTE_BLOCK_PLAY} 游戏事件感知（原版悦灵振动监听同机制），
 * 喜好时长 600 tick（原版 30 秒）；到期恢复默认交付对象。</p>
 *
 * <p><b>玩家身体映射（披露差异）</b>：收集 = 步行走近掉落物，玩家碰撞拾取
 * 原生完成（顺路捡起的非样品物品保留在背包、不交付——玩家身体无滤取）；
 * 样品 = 副手真实物品，不被交付/消耗；交付 = 面向目标后 {@code Player.drop}
 * 玩家丢弃流程（真实掉落物、正常拾取延迟），一次一组。交付对象默认 = 执行
 * brain 命令的玩家；其离线/死亡/跨维时持有货物等待，不丢不投错；有效音符盒
 * 优先。控制台挂脑（无默认对象）只对音符盒交付，无有效点则原地等待。</p>
 */
public class PlayerAllayCollectGoal extends PlayerGoal {

    /** 音符盒喜好时长（原版 30 秒） */
    private static final int LIKED_NOTE_BLOCK_TICKS = 600;
    /** 音符盒感知半径（原版悦灵振动监听半径 16） */
    private static final int NOTE_BLOCK_HEAR_RADIUS = 16;
    /** 掉落物搜索半径（原版 AllayAi.DISTANCE_TO_WANTED_ITEM 字段口径，值 32） */
    private static final int ITEM_SEARCH_RADIUS = 32;
    /** 交付接近距离（实体交互范围内） */
    private static final double DELIVER_DIST_SQ = 9.0;
    /** 寻物节流 */
    private static final int SCAN_INTERVAL_TICKS = 20;
    /** 交付后冷却（原版悦灵 give 冷却 240t）：防止放下即被自己重新拾取的循环 */
    private static final int DELIVER_COOLDOWN_TICKS = 240;

    protected final PryMob mob;
    private final ServerPlayer player;
    /** 默认交付对象（执行 brain 命令的玩家；控制台为 null） */
    private final java.util.UUID ownerUuid;

    /** 音符盒喜好点（原版 LIKED_NOTE_BLOCK：pos + 维度 + 到期 gameTime） */
    private BlockPos likedNoteBlock;
    private net.minecraft.resources.ResourceKey<Level> likedNoteBlockDimension;
    private long likedNoteBlockUntil = Long.MIN_VALUE;

    private BlockPos itemTarget;
    private boolean delivering;
    private long nextScanTime;
    private long deliverCooldownUntil = Long.MIN_VALUE;

    public PlayerAllayCollectGoal(PryMob mob, java.util.UUID ownerUuid) {
        this.mob = mob;
        this.player = mob.asPlayer() instanceof ServerPlayer sp ? sp : null;
        this.ownerUuid = ownerUuid;
        this.setFlags(EnumSet.of(PlayerGoal.Flag.MOVE));
        this.setInterval(1);
    }

    // ==================== 样品与匹配（原版规则） ====================

    /** 样品：副手真实物品（空 = 无样品，不收集不交付） */
    private ItemStack sample() {
        return this.mob.asPlayer().getOffhandItem();
    }

    /** 原版匹配：isSameItem + 药水内容守卫（同为药水时按内容一致才算匹配） */
    static boolean matchesSample(ItemStack stack, ItemStack sample) {
        if (!ItemStack.isSameItem(stack, sample)) {
            return false;
        }
        boolean potionLike = stack.getItem() instanceof net.minecraft.world.item.PotionItem;
        if (potionLike) {
            return ItemStack.isSameItemSameComponents(stack, sample);
        }
        return true;
    }

    /**
     * 待交付组与数量（记录：槽位 + 可交付数）。副手（末位槽）= 样品位：
     * 永不整组交付；26.3 的拾取/给予会落入副手，超出 1 个样品的部分作为
     * 交付物（按数量收缩真栈，样品恒保留 1——不消耗不复制）。
     */
    record Deliverable(boolean fromOffhand, int slot, int count) {}

    /** 测试缝（走生产 findDeliverable） */
    Deliverable findDeliverableForTest() {
        return this.findDeliverable();
    }

    private Deliverable findDeliverable() {
        ItemStack sample = this.sample();
        if (sample.isEmpty()) {
            return null;
        }
        var inv = this.player.getInventory();
        Deliverable best = null;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            // 副手槽（各版本都在 Inventory 枚举内，常量位置 26.3/1.21 一致）：
            // 样品位永不整组交付，超出 1 个的部分在下面单独按数量处理。
            if (i == net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND) {
                continue;
            }
            ItemStack stack = inv.getItem(i);
            if (matchesSample(stack, sample)
                    && (best == null || stack.getCount() > best.count())) {
                best = new Deliverable(false, i, stack.getCount());
            }
        }
        // 副手（26.3 为独立列表，不在 getItem 槽位里）：样品恒保留 1，
        // 超出部分作为交付物（26.3 的拾取/给予会落入副手）
        ItemStack off = this.player.getOffhandItem();
        if (matchesSample(off, sample) && off.getCount() > 1) {
            Deliverable fromOff = new Deliverable(true, -1, off.getCount() - 1);
            if (best == null || fromOff.count() > best.count()) {
                best = fromOff;
            }
        }
        return best;
    }

    /** 最近匹配掉落物（原版 wanted item 语义：可达掉落物） */
    private ItemEntity findWantedItem(Level level) {
        ItemStack sample = this.sample();
        if (sample.isEmpty()) {
            return null;
        }
        List<ItemEntity> items = this.collectItemEntities(level,
                this.mob.getBoundingBox().inflate(ITEM_SEARCH_RADIUS, ITEM_SEARCH_RADIUS, ITEM_SEARCH_RADIUS));
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity e : items) {
            if (!e.isAlive() || e.getItem().isEmpty() || !matchesSample(e.getItem(), sample)) {
                continue;
            }
            double d = this.mob.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** 盒内掉落物查询缝（26.3 ItemEntity 走数据同步器，桩世界不可读 → 测试注入） */
    protected List<ItemEntity> collectItemEntities(Level level, AABB box) {
        return level.getEntitiesOfClass(ItemEntity.class, box);
    }

    // ==================== 音符盒感知（监听器由 AllayBrain 会话持有） ====================

    /** 脑会话的音符盒监听落点：记录位置 + 维度 + 到期游戏刻 */
    public void noteHeard(BlockPos pos, net.minecraft.resources.ResourceKey<Level> dimension, long until) {
        this.likedNoteBlock = pos.immutable();
        this.likedNoteBlockDimension = dimension;
        this.likedNoteBlockUntil = until;
    }

    /** 穿维/卸脑时清除喜好点，禁止坐标跨维串用 */
    public void clearNoteMemory() {
        this.likedNoteBlock = null;
        this.likedNoteBlockDimension = null;
        this.likedNoteBlockUntil = Long.MIN_VALUE;
    }

    /** 监听半径（AllayBrain 注册时使用） */
    public static int noteBlockHearRadius() {
        return NOTE_BLOCK_HEAR_RADIUS;
    }

    /** 喜好时长（tick，AllayBrain 使用） */
    public static int likedNoteBlockTicks() {
        return LIKED_NOTE_BLOCK_TICKS;
    }

    public BlockPos likedNoteBlockForTest() {
        return this.likedNoteBlock;
    }

    public net.minecraft.resources.ResourceKey<Level> likedNoteBlockDimensionForTest() {
        return this.likedNoteBlockDimension;
    }

    // ==================== 目标选择器接口 ====================

    @Override
    public boolean canUse() {
        ItemStack sample = this.sample();
        if (sample.isEmpty()) {
            return false; // 无样品：明确等待（不擅自选物）
        }
        long now = this.bodyLevel().getGameTime();
        if (now < this.nextScanTime || now < this.deliverCooldownUntil) {
            return false;
        }
        this.nextScanTime = now + SCAN_INTERVAL_TICKS;
        return this.findDeliverable() != null || this.findWantedItem(this.bodyLevel()) != null;
    }

    @Override
    public boolean canContinueToUse() {
        return this.phaseActive();
    }

    private boolean phaseActive() {
        return !this.sample().isEmpty()
                && (this.findDeliverable() != null || this.itemTarget != null);
    }

    @Override
    public void start() {
        // 音符盒监听随 AllayBrain 会话注册，不依赖收集 Goal 是否恰好运行。
    }

    @Override
    public void stop() {
        this.itemTarget = null;
        this.delivering = false;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        Level level = this.bodyLevel();
        long now = level.getGameTime();
        // 音符盒喜好到期/跨维 → 恢复默认交付对象（原版行为；坐标不得跨维串用）
        if (this.likedNoteBlock != null
                && (now > this.likedNoteBlockUntil
                || this.likedNoteBlockDimension == null
                || !this.likedNoteBlockDimension.equals(level.dimension()))) {
            this.clearNoteMemory();
        }

        Deliverable deliverable = this.findDeliverable();
        // 交付冷却在执行路径生效：运行中的 Goal 持有多组货物也不能绕过。
        if (now < this.deliverCooldownUntil) {
            this.delivering = deliverable != null;
            this.itemTarget = null;
            this.mob.getNavigation().stop();
            return;
        }
        if (deliverable != null) {
            this.delivering = true;
            Vec3 targetPos = this.deliveryPoint(level);
            if (targetPos == null) {
                // 对象离线/死亡/跨维且无有效音符盒：持有货物等待，不丢不投错
                this.mob.getNavigation().stop();
                return;
            }
            double distSq = this.mob.distanceToSqr(targetPos.x, targetPos.y, targetPos.z);
            if (distSq > DELIVER_DIST_SQ) {
                if (this.mob.getNavigation().isDone()) {
                    this.mob.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.0);
                }
                return;
            }
            this.mob.getNavigation().stop();
            // 面向目标 + 玩家丢弃流程（真实掉落物）
            this.mob.getLookControl().setLookAt(targetPos.x, targetPos.y, targetPos.z, 30.0F, 30.0F);
            this.mob.asPlayer().setYRot(this.yawToward(targetPos));
            this.dropDeliverable(deliverable, targetPos);
            this.delivering = false;
            this.deliverCooldownUntil = level.getGameTime() + DELIVER_COOLDOWN_TICKS;
            return;
        }

        // 无可交付：找最近匹配掉落物，步行走近（碰撞拾取原生完成）
        this.delivering = false;
        ItemEntity wanted = this.findWantedItem(level);
        if (wanted == null) {
            this.itemTarget = null;
            this.mob.getNavigation().stop();
            return;
        }
        this.itemTarget = wanted.blockPosition();
        if (this.mob.distanceToSqr(wanted) > 1.0 && this.mob.getNavigation().isDone()) {
            this.mob.getNavigation().moveTo(wanted.getX(), wanted.getY(), wanted.getZ(), 1.0);
        }
    }

    /** 交付点：有效音符盒优先，否则默认对象玩家（无效则 null = 等待） */
    protected Vec3 deliveryPoint(Level level) {
        if (this.likedNoteBlock != null
                && this.likedNoteBlockDimension != null
                && this.likedNoteBlockDimension.equals(level.dimension())) {
            BlockStateLike check = this.readState(level, this.likedNoteBlock);
            if (check.isNoteBlock()) {
                return new Vec3(this.likedNoteBlock.getX() + 0.5, this.likedNoteBlock.getY() + 1.0, this.likedNoteBlock.getZ() + 0.5);
            }
            this.clearNoteMemory(); // 音符盒被拆：恢复默认对象
        }
        if (this.ownerUuid == null || !(level instanceof ServerLevel serverLevel)) {
            return null; // 控制台挂脑且无有效音符盒：明确等待
        }
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(this.ownerUuid);
        if (owner == null || !owner.isAlive() || owner.level() != level) {
            return null; // 离线/死亡/跨维：等待
        }
        return owner.position();
    }

    /** 世界读方块缝（离线测试覆写） */
    protected BlockStateLike readState(Level level, BlockPos pos) {
        return new BlockStateLike(level.getBlockState(pos));
    }

    record BlockStateLike(net.minecraft.world.level.block.state.BlockState state) {
        boolean isNoteBlock() {
            return this.state.is(net.minecraft.world.level.block.Blocks.NOTE_BLOCK);
        }
    }

    /** 交付动作测试缝：按槽位+数量移除（真 drop 的实体链桩世界不可达） */
    void dropForTest(Deliverable d) {
        if (d.fromOffhand()) {
            ItemStack off = this.player.getOffhandItem();
            off.setCount(off.getCount() - d.count());
            return;
        }
        var inv = this.player.getInventory();
        ItemStack source = inv.getItem(d.slot());
        source.setCount(source.getCount() - d.count());
        if (source.getCount() <= 0) {
            inv.setItem(d.slot(), ItemStack.EMPTY);
        }
    }

    private void dropDeliverable(Deliverable deliverable, Vec3 targetPos) {
        ItemStack copy;
        if (deliverable.fromOffhand()) {
            ItemStack off = this.player.getOffhandItem();
            copy = off.split(deliverable.count());
            this.player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, off);
        } else {
            var inv = this.player.getInventory();
            ItemStack source = inv.getItem(deliverable.slot());
            copy = source.split(deliverable.count());
            if (source.isEmpty()) {
                inv.setItem(deliverable.slot(), ItemStack.EMPTY);
            }
            inv.setChanged();
        }
        this.dropToWorld(copy, targetPos);
        this.mob.prySwingMainHand();
    }

    /**
     * 真实掉落：玩家原生 drop 生成 ItemEntity 后，把落点放到实际交付点
     * （音符盒上方/主人位置），避免物品生成在悦灵脚下被原生碰撞立即拾取，
     * 形成放下→拾取→再交付的回环。
     */
    protected void dropToWorld(ItemStack stack, Vec3 targetPos) {
        //#if MC >= 260300
        //$$ net.minecraft.world.entity.item.ItemEntity dropped =
        //$$         this.player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        //#else
        net.minecraft.world.entity.item.ItemEntity dropped = this.player.drop(stack, false);
        //#endif
        if (dropped != null) {
            dropped.setPos(targetPos.x, targetPos.y, targetPos.z);
            dropped.setDeltaMovement(Vec3.ZERO);
        }
    }

    private float yawToward(Vec3 target) {
        double dx = target.x - this.mob.getX();
        double dz = target.z - this.mob.getZ();
        return (float) (net.minecraft.util.Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
    }

    private Level bodyLevel() {
        return this.mob.asPlayer().level();
    }
}
