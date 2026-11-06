package me.primaryuan.carpet.brain;

import me.primaryuan.carpet.brain.goal.PlayerAllayCollectGoal;
import me.primaryuan.carpet.brain.goal.PlayerLookAtPlayerGoal;
import me.primaryuan.carpet.brain.goal.PlayerRandomStrollGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;

/**
 * 悦灵模式脑（装配器）：副手样品的步行收集与交付，无仇恨、不飞行。
 *
 * <p>收集/交付契约与玩家身体映射见 {@link PlayerAllayCollectGoal}（26.3
 * {@code Allay} 匹配规则字节码对齐）。默认交付对象 = 执行 brain 命令的玩家
 * （{@code ownerUuid}）；控制台挂脑时只对有效音符盒交付，无点则等待。</p>
 *
 * <p><b>音符盒监听生命周期</b>：监听器随本脑会话（attach/detach）存在，不依赖
 * 收集 Goal 是否运行；记录注册时的 {@link ServerLevel}，跨维先从原世界注销
 * 再向新世界注册并清空旧维度喜好点。卸脑/死亡/停服统一注销。</p>
 */
public class AllayBrain extends PlayerBrainController {

    private final java.util.UUID assembledOwnerUuid;
    private final PlayerAllayCollectGoal collectGoal;

    /** 会话级音符盒监听（原版悦灵振动监听同机制） */
    private DynamicGameEventListener<SessionNoteListener> noteListener;
    private ServerLevel noteListenerLevel;

    public AllayBrain(ServerPlayer player, java.util.UUID ownerUuid) {
        super(player);
        this.assembledOwnerUuid = ownerUuid;
        this.collectGoal = new PlayerAllayCollectGoal(this.prowler, ownerUuid);
    }

    @Override
    public String modeKey() {
        return "allay";
    }

    @Override
    protected void assemble() {
        // 收集交付（MOVE）；悦灵为友好友善型，无索敌目标
        this.goalSelector.addGoal(2, this.collectGoal);
        this.goalSelector.addGoal(6, new PlayerRandomStrollGoal(this.prowler, 1.0));
        this.goalSelector.addGoal(7, new PlayerLookAtPlayerGoal(this.prowler,
                net.minecraft.world.entity.player.Player.class, 6.0F));
    }

    @Override
    public void onAttach() {
        super.onAttach();
        this.registerNoteListener();
    }

    @Override
    public void tick() {
        this.syncNoteListenerWorld();
        // 位置/区块变化时更新监听注册；挂脑时目标区块尚未加载的情况下，
        // 后续加载后也能真正注册到该区块的监听表。
        if (this.noteListener != null && this.player.level() instanceof ServerLevel level) {
            this.noteListener.move(level);
        }
        super.tick();
    }

    @Override
    public void onDetach() {
        this.unregisterNoteListener();
        this.collectGoal.clearNoteMemory();
        super.onDetach();
    }

    /** 跨维：从旧世界注销，再在新世界注册；旧维度喜好点立即失效 */
    protected void syncNoteListenerWorld() {
        if (!(this.player.level() instanceof ServerLevel current)) {
            this.unregisterNoteListener();
            return;
        }
        if (current == this.noteListenerLevel) {
            return;
        }
        this.unregisterNoteListener();
        this.collectGoal.clearNoteMemory();
        this.registerNoteListener();
    }

    protected void registerNoteListener() {
        if (this.noteListener != null) {
            return;
        }
        if (this.player.level() instanceof ServerLevel level) {
            this.noteListener = new DynamicGameEventListener<>(new SessionNoteListener());
            this.noteListener.add(level);
            this.noteListenerLevel = level;
        }
    }

    protected void unregisterNoteListener() {
        if (this.noteListener != null && this.noteListenerLevel != null) {
            this.noteListener.remove(this.noteListenerLevel);
        }
        this.noteListener = null;
        this.noteListenerLevel = null;
    }

    /** 会话级监听器：只认 NOTE_BLOCK_PLAY 游戏事件（不把唱片机当音符盒） */
    final class SessionNoteListener implements GameEventListener {
        @Override
        public PositionSource getListenerSource() {
            return new net.minecraft.world.level.gameevent.EntityPositionSource(
                    AllayBrain.this.prowler.asLiving(), 0.0F);
        }

        @Override
        public int getListenerRadius() {
            return PlayerAllayCollectGoal.noteBlockHearRadius();
        }

        @Override
        public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> event,
                                       GameEvent.Context context, Vec3 pos) {
            if (!event.is(GameEvent.NOTE_BLOCK_PLAY)) {
                return false;
            }
            AllayBrain.this.collectGoal.noteHeard(BlockPos.containing(pos),
                    level.dimension(),
                    level.getGameTime() + PlayerAllayCollectGoal.likedNoteBlockTicks());
            return true;
        }
    }

    // ===== 测试观测缝（会话生命周期） =====

    boolean hasNoteListener() {
        return this.noteListener != null;
    }

    ServerLevel noteListenerLevelForTest() {
        return this.noteListenerLevel;
    }

    PlayerAllayCollectGoal collectGoalForTest() {
        return this.collectGoal;
    }

    /** 测试观测缝：构造时收到的默认交付对象 UUID（控制台挂脑为 null） */
    java.util.UUID assembledOwnerUuidForTest() {
        return this.assembledOwnerUuid;
    }
}
