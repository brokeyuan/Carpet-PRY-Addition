package me.primaryuan.carpet.mixins.rule.fixEndCrystalSync;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * fixEndCrystalSync 规则 —— 修复活塞推动末地水晶后客户端位置与服务端不同步。
 *
 * 原版把 END_CRYSTAL 的跟踪间隔注册为 updateInterval=Integer.MAX_VALUE
 * （EntityType 注册处字节码核实），而 ServerEntity.sendChanges 的位置同步
 * 分支仅在 tickCount % updateInterval == 0（1.21.11 起另有 entity.needsSync）
 * 时执行——对水晶而言，除出生包自带坐标外永不触发。活塞推实体时，客户端
 * 与服务端各自在 PistonMovingBlockEntity.moveCollidedEntities 里独立模拟
 * （两端都执行，无 isClientSide 门控），任何一次模拟分歧（客户端漏收活塞
 * BlockEvent、区块加载时序、碰撞结算差异等）都会永久滞留；Entity.needsSync
 * 仅由 Entity.push 与 Entity.load 写入，活塞路径不写（活塞类对该字段零
 * 引用），因此没有自愈途径，只有重新跟踪（重进/重启后重发出生包）才能
 * 拿回真实坐标。
 *
 * 修法：sendChanges HEAD 注入，跟踪本实体上一次出现的位置；位置发生变化
 * 时把 tickCount 置为 0——0 % updateInterval == 0，本次调用即命中原版位置
 * 同步分支，数据包构造（增量 MoveEntityPacket / 绝对 EntityPositionSyncPacket
 * 或旧版 TeleportEntityPacket）与广播全部复用原版逻辑，客户端 1 tick 内
 * 对齐，同时覆盖重进服务器时"活塞先于水晶实体 tick"的出生时序竞争。
 *
 * 版本差异（sendChanges 内 tickCount 写点，逐一反编译核实）：
 *  - 1.21 / 1.21.3 / 1.21.4 / 1.21.5 / 1.21.8 / 1.21.10 / 1.21.11 / 26.1.2 /
 *    26.2 / 26.3：无条件自增均在方法末尾（1.21 偏移 1183、1.21.11 偏移 1146、
 *    26.1.2 偏移 1152、26.3 偏移 957），门控在自增前读取 → 归零为 0 即命中。
 *    （旧注释曾记 26.2"自增移至方法开头"——经 26.2/26.3 jar 反编译核实，偏移
 *    238 处的 putfield 是门控未命中时的对齐写回分支，无条件自增仍在末尾，
 *    全版本行为一致。曾按失实注释对 26.2+ 置 -1，导致首次同步晚 1 tick。）
 * tickCount/entity 字段名与 sendChanges 方法签名在 1.21~26.3 一致。
 */
@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {

    @Shadow
    @Final
    private Entity entity;

    @Shadow
    private int tickCount;

    /** 本 ServerEntity 上一次见到的水晶位置；null = 尚未记录（出生包已带正确坐标） */
    @Unique
    private Vec3 fixEndCrystalSync$lastSeenPos;

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void fixEndCrystalSync$forceSyncOnMove(CallbackInfo ci) {
        if (!CarpetPrimaryuanSettings.fixEndCrystalSync || !(this.entity instanceof EndCrystal)) {
            return;
        }
        Vec3 current = this.entity.position();
        Vec3 last = this.fixEndCrystalSync$lastSeenPos;
        this.fixEndCrystalSync$lastSeenPos = current;
        if (last == null || last.equals(current)) {
            return;
        }
        // 全版本一致：门控在方法末尾的自增前读取 tickCount，置 0 当 tick 即命中
        this.tickCount = 0;
    }
}
