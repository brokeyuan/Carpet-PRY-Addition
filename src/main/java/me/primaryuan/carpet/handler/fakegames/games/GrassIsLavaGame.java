package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.FakeGameBoundary;
import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 草地是岩浆：开局有撤离时间；固定模式始终使用同一危险方块，轮换模式从
 * 常见方块池中挑选场地内实际存在的危险项，切换前预告并再次给撤离时间。
 *
 * <p>水按流体接触判断（玩家身体进入水或脚下方块含水），不只看支撑方块；
 * 场地检查限定在已加载、区域内的采样点，每次切换最多 64 个采样，不逐 tick 扫描。</p>
 */
public final class GrassIsLavaGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    /** 可选参数白名单：不引入注册表解析，跨版本零风险 */
    private static final Map<String, Block> HAZARD_OPTIONS = Map.ofEntries(
            Map.entry("grass_block", Blocks.GRASS_BLOCK),
            Map.entry("sand", Blocks.SAND),
            Map.entry("red_sand", Blocks.RED_SAND),
            Map.entry("stone", Blocks.STONE),
            Map.entry("dirt", Blocks.DIRT),
            Map.entry("gravel", Blocks.GRAVEL),
            Map.entry("oak_planks", Blocks.OAK_PLANKS),
            Map.entry("clay", Blocks.CLAY),
            Map.entry("snow", Blocks.SNOW_BLOCK),
            Map.entry("snow_block", Blocks.SNOW_BLOCK));

    /** 水不是 Block，单独用特殊 id 表示 */
    private static final String WATER = "water";

    /** 轮换池顺序固定，便于测试与面板展示 */
    private static final List<String> HAZARD_IDS = List.of(
            "grass_block", "dirt", "stone", "sand", "red_sand", "gravel",
            "oak_planks", "clay", "snow_block", WATER);

    private final Set<String> pool = new LinkedHashSet<>();
    private String hazardId = "grass_block";
    private boolean rotating;
    private int rotateTicks;
    private int rotateLeft;
    private int graceTicks;
    private int hintCooldown;
    private final List<String> usablePool = new ArrayList<>();

    public static boolean isValidOption(String option) {
        return option != null && (WATER.equals(option) || HAZARD_OPTIONS.containsKey(option));
    }

    public static String optionsText() {
        return String.join(", ", HAZARD_OPTIONS.keySet()) + ", " + WATER;
    }

    @Override
    public void onStart(GameSession session) {
        rotating = "rotate".equals(session.option("mode"));
        rotateTicks = Math.max(5, session.intOption("interval", 15)) * 20;
        graceTicks = Math.max(1, session.intOption("evacuate", 5)) * 20;
        hintCooldown = 40;
        pool.clear();
        pool.addAll(HAZARD_IDS);
        hazardId = normalize(session.option("hazard"));
        if (!isValidOption(hazardId)) {
            hazardId = "grass_block";
        }
        usablePool.clear();
        usablePool.addAll(collectUsable(session));
        if (usablePool.isEmpty()) {
            usablePool.add(hazardId);
        }
        session.broadcast(ServerI18n.tr(rotating
                        ? "carpetprimaryuan.fakegames.grass.start_rotate"
                        : "carpetprimaryuan.fakegames.grass.start",
                hazardName(), graceTicks / 20));
        announce(session);
    }

    private String normalize(String value) {
        if (value == null) {
            return "grass_block";
        }
        // 兼容旧版 snow 别名
        return "snow".equals(value) ? "snow_block" : value;
    }

    @Override
    public void onTick(GameSession session) {
        if (graceTicks > 0) {
            graceTicks--;
            if (graceTicks == 0) {
                session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.grass.active",
                        hazardName()));
            }
            tickHint(session);
            return;
        }
        List<ServerPlayer> violators = new ArrayList<>();
        for (ServerPlayer player : session.activePlayers()) {
            if (isHazard(session, player)) {
                violators.add(player);
            }
        }
        if (!violators.isEmpty()) {
            session.eliminateBatch(violators, "carpetprimaryuan.fakegames.reason.hazard",
                    hazardName());
            if (session.phase != me.primaryuan.carpet.handler.fakegames.SessionPhase.RUNNING) {
                return;
            }
        }
        if (rotating && --rotateLeft <= 0) {
            rotate(session);
        }
        tickHint(session);
    }

    private void tickHint(GameSession session) {
        if (--hintCooldown <= 0) {
            hintCooldown = 60;
            session.actionbarActive(ServerI18n.tr("carpetprimaryuan.fakegames.grass.hint",
                    hazardName()));
            if (rotating) {
                FakeGameBoundary.markCircle(session,
                        session.center.getX() + 0.5, session.center.getZ() + 0.5,
                        session.center.getY(), Math.max(2, session.radius * 0.25),
                        ParticleTypes.FLAME);
            }
        }
    }

    /** 选择下一个危险项，先预告再给撤离时间。 */
    private void rotate(GameSession session) {
        if (usablePool.isEmpty()) {
            session.cancel("cancel_no_hazard");
            return;
        }
        String next = usablePool.get(session.level.getRandom().nextInt(usablePool.size()));
        // 尽量避免连续重复：池足够大时换一个
        if (usablePool.size() > 1 && next.equals(hazardId)) {
            next = usablePool.get((usablePool.indexOf(next) + 1) % usablePool.size());
        }
        hazardId = next;
        rotateLeft = rotateTicks;
        graceTicks = Math.max(20, session.intOption("evacuate", 5) * 20);
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.grass.switch_notice",
                hazardName(), graceTicks / 20));
        announce(session);
        for (ServerPlayer player : session.activePlayers()) {
            me.primaryuan.carpet.handler.fakegames.FakeGameEffects.title(player,
                    ServerI18n.tr("carpetprimaryuan.fakegames.grass.switch_title"),
                    ServerI18n.tr("carpetprimaryuan.fakegames.grass.switch_sub", hazardName()),
                    0, 30, 10);
        }
    }

    private void announce(GameSession session) {
        session.titleActive(ServerI18n.tr("carpetprimaryuan.fakegames.grass.notice_title"),
                ServerI18n.tr("carpetprimaryuan.fakegames.grass.notice_sub", hazardName()),
                5, 40, 10);
    }

    private String hazardName() {
        if (WATER.equals(hazardId)) {
            return ServerI18n.tr("carpetprimaryuan.fakegames.grass.water").getString();
        }
        Block block = HAZARD_OPTIONS.getOrDefault(hazardId, Blocks.GRASS_BLOCK);
        return block.getName().getString();
    }

    /** 危险判定：水按流体接触；方块按脚底支撑方块精确匹配。 */
    private boolean isHazard(GameSession session, ServerPlayer player) {
        if (player.level() != session.level) {
            return false;
        }
        if (WATER.equals(hazardId)) {
            if (player.isInWater()) {
                return true;
            }
            BlockPos feet = player.blockPosition();
            return session.level.getFluidState(feet).is(FluidTags.WATER)
                    || session.level.getFluidState(feet.below()).is(FluidTags.WATER);
        }
        Block block = HAZARD_OPTIONS.get(hazardId);
        if (block == null || !player.onGround()) {
            return false;
        }
        return session.level.getBlockState(player.getOnPos()).is(block);
    }

    /**
     * 区域内可用危险项采样：只检查已加载区块，最多 64 个点；水检查表面流体，
     * 方块检查 MOTION_BLOCKING 高度处及其下一格。返回顺序稳定，便于测试。
     */
    static List<String> selectUsableHazards(GameSession session, java.util.Collection<String> candidates,
                                           int samples) {
        List<String> result = new ArrayList<>();
        Random random = new Random(session.center.asLong() ^ session.level.getGameTime());
        for (String candidate : candidates) {
            if (existsInRegion(session, candidate, random, samples)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private List<String> collectUsable(GameSession session) {
        // 固定模式只检查当前危险项，避免无谓扫描；轮换模式检查完整池
        if (!rotating) {
            return existsInRegion(session, hazardId, new Random(session.center.asLong()), 64)
                    ? List.of(hazardId) : List.of(hazardId);
        }
        return selectUsableHazards(session, pool, 48);
    }

    private static boolean existsInRegion(GameSession session, String candidate,
                                          Random random, int samples) {
        boolean water = WATER.equals(candidate);
        Block block = HAZARD_OPTIONS.get(candidate);
        if (!water && block == null) {
            return false;
        }
        int radius = session.radius;
        for (int i = 0; i < samples; i++) {
            int x = session.center.getX() + random.nextInt(radius * 2 + 1) - radius;
            int z = session.center.getZ() + random.nextInt(radius * 2 + 1) - radius;
            double dx = x + 0.5 - (session.center.getX() + 0.5);
            double dz = z + 0.5 - (session.center.getZ() + 0.5);
            if (dx * dx + dz * dz > (double) radius * radius) {
                continue;
            }
            BlockPos column = new BlockPos(x, session.center.getY(), z);
            if (!session.level.hasChunkAt(column)) {
                continue;
            }
            int y = session.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (water) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (session.level.getFluidState(new BlockPos(x, y + dy, z))
                            .is(FluidTags.WATER)) {
                        return true;
                    }
                }
            } else {
                if (session.level.getBlockState(new BlockPos(x, y, z)).is(block)
                        || session.level.getBlockState(new BlockPos(x, y - 1, z)).is(block)
                        || session.level.getBlockState(new BlockPos(x, y - 2, z)).is(block)) {
                    return true;
                }
            }
        }
        return false;
    }
}
