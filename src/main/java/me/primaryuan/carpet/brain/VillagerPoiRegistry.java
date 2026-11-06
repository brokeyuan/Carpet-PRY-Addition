package me.primaryuan.carpet.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 村民兴趣点（POI）占用登记表——原版 {@code PoiManager} 占用语义的自建等价。
 *
 * <p>原版村民通过世界 POI 数据（区块驱动的 PoiRecord）获取床位/工作站/集合点并
 * 按类型计数占用；移植版假人是玩家实体，直接复用世界的 POI 数据引擎涉及
 * 11 个版本的 API 漂移，故以"真实方块扫描 + 本登记表"表达同一语义面：</p>
 * <ul>
 *   <li><b>获取</b>：{@link #claim} 按容量登记（床/工作站 1、集合点 32，原版字节码核定）；</li>
 *   <li><b>占用</b>：{@link #isOccupied} 供其他村民跳过已被占的点；</li>
 *   <li><b>失效</b>：目标方块不再是 POI（被破坏/替换）时 {@link #invalidate} 释放；</li>
 *   <li><b>释放</b>：{@link #releaseAll} 在换脑/下线/死亡卸载钩子与停服时清空，
 *       不留幽灵占用。</li>
 * </ul>
 *
 * <p><b>所有权按实例</b>：生产 Goal 以 {@code Player} 实例登记，同时记 UUID 供
 * 查询/诊断；同 UUID 的旧实例下线只能释放自己实例名下的 claim，不能清新实例
 * 岗位。测试/命令以 UUID 登记时按 UUID 语义工作。</p>
 *
 * <p>与原版的披露差异：占用只在本服运行期内有效（不落盘），世界重载后自然清空；
 * 原版 PoiRecord 的区块级生命周期由方块扫描的有效性复核替代。</p>
 */
public final class VillagerPoiRegistry {

    /**
     * POI 种类及占用容量（原版 {@code PoiTypes.bootstrap} 字节码逐项核定：
     * register(..., maxNearbyTickets, validRange)——home(BEDS 标签)=1、
     * meeting(BELL)=32、13 种职业工作站=1）。
     */
    public enum Kind {
        BED(1), WORKSTATION(1), MEETING(32);

        final int capacity;

        Kind(int capacity) {
            this.capacity = capacity;
        }
    }

    private record Key(ResourceKey<Level> world, BlockPos pos) {
    }

    /** 一次占用：实例身份 + 同时记录的 UUID（供按 UUID 的诊断/测试查询） */
    private record Claim(Object owner, UUID uuid) {
    }

    private static final Map<Key, Set<Claim>> CLAIMS = new ConcurrentHashMap<>();

    private VillagerPoiRegistry() {
    }

    /** 登记占用：点未满才成功；同一 Owner 重复登记同点幂等成功 */
    public static boolean claim(Object owner, Level world, BlockPos pos, Kind kind) {
        Set<Claim> holders = CLAIMS.computeIfAbsent(
                new Key(world.dimension(), pos.immutable()), k -> ConcurrentHashMap.newKeySet());
        synchronized (holders) {
            for (Claim claim : holders) {
                if (claim.owner() == owner) {
                    return true;
                }
            }
            if (holders.size() >= kind.capacity) {
                return false;
            }
            UUID uuid = owner instanceof Player player ? player.getUUID()
                    : owner instanceof UUID id ? id : null;
            holders.add(new Claim(owner, uuid));
            return true;
        }
    }

    /** 释放某实例的全部占用（卸载钩子用），返回是否清掉了至少一处 */
    public static boolean releaseAll(Object owner) {
        boolean released = false;
        for (Set<Claim> holders : CLAIMS.values()) {
            if (holders.removeIf(claim -> matchesOwnerForRelease(claim, owner))) {
                released = true;
            }
        }
        return released;
    }

    /** 释放某实例对特定点的占用 */
    public static void release(Object owner, Level world, BlockPos pos) {
        release(owner, world.dimension(), pos);
    }

    /** 按维度键释放（控制器只保存岗位维度时使用，避免强引用世界对象） */
    public static void release(Object owner, ResourceKey<Level> world, BlockPos pos) {
        Set<Claim> holders = CLAIMS.get(new Key(world, pos.immutable()));
        if (holders != null) {
            holders.removeIf(claim -> matchesOwnerForRelease(claim, owner));
        }
    }

    /** 某实例是否持有该点占用（职业有效性复核：不只查方块还在） */
    public static boolean isClaimedBy(Object owner, ResourceKey<Level> world, BlockPos pos) {
        Set<Claim> holders = CLAIMS.get(new Key(world, pos.immutable()));
        if (holders == null) {
            return false;
        }
        for (Claim claim : holders) {
            if (matchesForQuery(claim, owner)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isClaimedBy(Object owner, Level world, BlockPos pos) {
        return isClaimedBy(owner, world.dimension(), pos);
    }

    /** 点剩余容量是否为 0（候选筛选用，只读不占） */
    public static boolean isFull(Level world, BlockPos pos, Kind kind) {
        Set<Claim> holders = CLAIMS.get(new Key(world.dimension(), pos.immutable()));
        return holders != null && holders.size() >= kind.capacity;
    }

    /** 点是否被任何村民占用 */
    public static boolean isOccupied(Level world, BlockPos pos) {
        Set<Claim> holders = CLAIMS.get(new Key(world.dimension(), pos.immutable()));
        return holders != null && !holders.isEmpty();
    }

    /** 点失效（方块被破坏/替换）时清空该点占用 */
    public static void invalidate(Level world, BlockPos pos) {
        CLAIMS.remove(new Key(world.dimension(), pos.immutable()));
    }

    /** 停服清空（与 BrainTeams 的停服钩子同点调用） */
    public static void clear() {
        CLAIMS.clear();
    }

    static int claimCount() {
        return CLAIMS.size();
    }

    /**
     * 释放匹配：只认实例身份（UUID 仅在调用方显式传 UUID 时匹配）。
     * 同 UUID 旧实例的事件不得清新实例 claim。
     */
    private static boolean matchesOwnerForRelease(Claim claim, Object owner) {
        if (claim.owner() == owner) {
            return true;
        }
        return owner instanceof UUID id && claim.uuid() != null && claim.uuid().equals(id);
    }

    /** 查询匹配：实例身份或 UUID（职业复核/测试断言按 UUID 可见） */
    private static boolean matchesForQuery(Claim claim, Object owner) {
        if (claim.owner() == owner) {
            return true;
        }
        if (owner instanceof Player player) {
            return claim.uuid() != null && claim.uuid().equals(player.getUUID());
        }
        return owner instanceof UUID id && claim.uuid() != null && claim.uuid().equals(id);
    }
}
