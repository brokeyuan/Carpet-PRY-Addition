package me.primaryuan.carpet.handler.fakegames.games;

import me.primaryuan.carpet.handler.fakegames.GameMember;
import me.primaryuan.carpet.handler.fakegames.GameSession;
import me.primaryuan.carpet.handler.fakegames.SessionPhase;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 贪吃蛇：区域内随机出现无主假人食物；玩家持续行走，停留超过容错时间出局。
 * 碰到食物后它加入尾巴，尾巴沿玩家历史路线依次前进（不使用寻路，不会抄近路）。
 * 撞到自己的尾巴或其他玩家尾巴出局；出局后尾巴停在原地变回无主假人。
 *
 * <p>表现使用隐形、标记、无重力的盔甲架 + 玩家头颅，不生成会拾取/掉落物品、
 * 参与世界操作的普通假人；所有实体与历史路径都有上限并在结束时清理。</p>
 */
public final class SnakeGame implements me.primaryuan.carpet.handler.fakegames.GameLogic {

    private static final int HISTORY_LIMIT = 300;
    private static final int TAIL_DELAY_TICKS = 4;
    private static final int COLLISION_GRACE = 20;
    private static final double FOOD_PICKUP = 1.6;
    private static final double TAIL_COLLISION = 0.9;
    private static final int STILL_LIMIT_TICKS = 60;

    private static final class Food {
        final Vec3 pos;
        ArmorStand entity;

        Food(Vec3 pos) {
            this.pos = pos;
        }
    }

    private static final class TailNode {
        final ArmorStand entity;
        Vec3 pos;

        TailNode(ArmorStand entity, Vec3 pos) {
            this.entity = entity;
            this.pos = pos;
        }
    }

    private static final class SnakePlayer {
        final Deque<Vec3> history = new ArrayDeque<>();
        final Deque<TailNode> tail = new ArrayDeque<>();
        int stillTicks;
        int grace;
    }

    private final List<Food> foods = new ArrayList<>();
    private final Map<UUID, SnakePlayer> snakes = new LinkedHashMap<>();
    private int maxLength = 20;
    private int foodTarget = 12;
    private int timeLeft;
    private int hudCooldown;

    @Override
    public void onStart(GameSession session) {
        maxLength = Math.max(5, session.intOption("max_length", 20));
        foodTarget = Math.max(4, session.intOption("food_count", 12));
        timeLeft = 20 * 300;
        clearAll();
        for (ServerPlayer player : session.activePlayers()) {
            SnakePlayer snake = new SnakePlayer();
            snakes.put(player.getUUID(), snake);
            for (int i = 0; i < 4; i++) {
                snake.history.addLast(player.position());
            }
        }
        for (int i = 0; i < foodTarget; i++) {
            spawnFood(session);
        }
        session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.snake.start",
                foodTarget, maxLength, STILL_LIMIT_TICKS / 20));
    }

    @Override
    public void onTick(GameSession session) {
        if (--timeLeft <= 0) {
            timeUp(session);
            return;
        }
        for (ServerPlayer player : session.activePlayers()) {
            SnakePlayer snake = snakes.get(player.getUUID());
            if (snake == null) {
                continue;
            }
            if (snake.grace > 0) {
                snake.grace--;
            }
            snake.history.addFirst(player.position());
            while (snake.history.size() > HISTORY_LIMIT) {
                snake.history.removeLast();
            }
            updateStillness(session, player, snake);
            if (session.phase != SessionPhase.RUNNING) {
                return;
            }
            updateTail(session, player, snake);
            checkFood(session, player, snake);
            if (session.phase != SessionPhase.RUNNING) {
                return;
            }
            checkTailCollision(session, player, snake);
            if (session.phase != SessionPhase.RUNNING) {
                return;
            }
        }
        if (--hudCooldown <= 0) {
            hudCooldown = 20;
            sendHud(session);
        }
    }

    private void updateStillness(GameSession session, ServerPlayer player, SnakePlayer snake) {
        boolean eligible = player.onGround() && !player.isPassenger()
                && !player.isInWater() && !player.isInLava();
        if (!eligible) {
            snake.stillTicks = 0;
            return;
        }
        if (player.getDeltaMovement().horizontalDistanceSqr() < 0.0025) {
            snake.stillTicks++;
        } else {
            snake.stillTicks = 0;
        }
        if (snake.stillTicks > STILL_LIMIT_TICKS) {
            session.eliminate(player, "carpetprimaryuan.fakegames.reason.snake_still");
        }
    }

    /** 尾巴节点取历史路径中按固定延迟的点，沿玩家真实路线前进。 */
    private void updateTail(GameSession session, ServerPlayer player, SnakePlayer snake) {
        List<Vec3> path = new ArrayList<>(snake.history);
        int index = 0;
        for (TailNode node : snake.tail) {
            int wanted = Math.min(path.size() - 1, (index + 1) * TAIL_DELAY_TICKS);
            if (wanted >= 0 && wanted < path.size()) {
                node.pos = path.get(wanted);
            }
            moveEntity(node.entity, node.pos);
            index++;
        }
    }

    private void checkFood(GameSession session, ServerPlayer player, SnakePlayer snake) {
        for (int i = foods.size() - 1; i >= 0; i--) {
            Food food = foods.get(i);
            if (player.position().distanceToSqr(food.pos) <= FOOD_PICKUP * FOOD_PICKUP) {
                foods.remove(i);
                if (food.entity != null) {
                    food.entity.discard();
                }
                addTailNode(session, player, snake, food.pos);
                spawnFood(session);
                session.broadcast(ServerI18n.tr("carpetprimaryuan.fakegames.snake.eat",
                        player.getName().getString(), snake.tail.size(), maxLength));
                return;
            }
        }
    }

    private void addTailNode(GameSession session, ServerPlayer player, SnakePlayer snake, Vec3 pos) {
        if (snake.tail.size() >= maxLength) {
            return;
        }
        ArmorStand stand = spawnVisual(session, pos, false);
        if (stand != null) {
            snake.tail.addLast(new TailNode(stand, pos));
        }
        snake.grace = COLLISION_GRACE;
    }

    private void checkTailCollision(GameSession session, ServerPlayer player, SnakePlayer snake) {
        if (snake.grace > 0) {
            return;
        }
        // 跳过最近两节，避免刚收集/转弯时误判
        int skip = 2;
        for (TailNode node : snake.tail) {
            if (skip-- > 0) {
                continue;
            }
            if (player.position().distanceToSqr(node.pos) <= TAIL_COLLISION * TAIL_COLLISION) {
                session.eliminate(player, "carpetprimaryuan.fakegames.reason.snake_self");
                return;
            }
        }
        for (Map.Entry<UUID, SnakePlayer> entry : snakes.entrySet()) {
            if (entry.getKey().equals(player.getUUID())) {
                continue;
            }
            int skipOther = 1;
            for (TailNode node : entry.getValue().tail) {
                if (skipOther-- > 0) {
                    continue;
                }
                if (player.position().distanceToSqr(node.pos) <= TAIL_COLLISION * TAIL_COLLISION) {
                    session.eliminate(player, "carpetprimaryuan.fakegames.reason.snake_other");
                    return;
                }
            }
        }
    }

    private void spawnFood(GameSession session) {
        if (foods.size() >= foodTarget) {
            return;
        }
        for (int attempt = 0; attempt < 40; attempt++) {
            double angle = session.level.getRandom().nextDouble() * Math.PI * 2;
            double distance = 4 + session.level.getRandom().nextDouble()
                    * Math.max(4.0, session.radius - 6.0);
            int x = (int) Math.round(session.center.getX() + 0.5 + Math.cos(angle) * distance);
            int z = (int) Math.round(session.center.getZ() + 0.5 + Math.sin(angle) * distance);
            if (!session.level.hasChunkAt(new BlockPos(x, session.center.getY(), z))) {
                continue;
            }
            int y = session.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockState ground = session.level.getBlockState(pos.below());
            if (ground.isAir() || !ground.getFluidState().isEmpty()) {
                continue;
            }
            Vec3 location = new Vec3(x + 0.5, y + 0.2, z + 0.5);
            boolean tooClose = false;
            for (Food other : foods) {
                if (other.pos.distanceToSqr(location) < 4.0) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                continue;
            }
            Food food = new Food(location);
            food.entity = spawnVisual(session, location, true);
            foods.add(food);
            return;
        }
    }

    /** 隐形标记盔甲架 + 玩家头颅；食物与尾巴共用。 */
    private ArmorStand spawnVisual(GameSession session, Vec3 pos, boolean food) {
        ArmorStand stand = new ArmorStand(session.level, pos.x, pos.y, pos.z);
        stand.setInvisible(true);
        stand.setNoBasePlate(true);
        stand.setNoGravity(true);
        stand.setSilent(true);
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.PLAYER_HEAD));
        session.level.addFreshEntity(stand);
        return stand;
    }

    private void moveEntity(ArmorStand stand, Vec3 pos) {
        if (stand == null || pos == null) {
            return;
        }
        if (stand.position().distanceToSqr(pos) > 0.0001) {
            stand.teleportTo(pos.x, pos.y, pos.z);
        }
    }

    private void sendHud(GameSession session) {
        for (ServerPlayer player : session.activePlayers()) {
            SnakePlayer snake = snakes.get(player.getUUID());
            if (snake == null) {
                continue;
            }
            GameMember member = session.member(player.getUUID());
            me.primaryuan.carpet.handler.fakegames.FakeGameEffects.actionbar(player,
                    ServerI18n.tr("carpetprimaryuan.fakegames.snake.hud",
                            snake.tail.size(), maxLength, timeLeft / 20,
                            member == null ? 0 : member.score));
        }
    }

    private void timeUp(GameSession session) {
        int best = -1;
        UUID bestId = null;
        boolean tie = false;
        for (ServerPlayer player : session.activePlayers()) {
            SnakePlayer snake = snakes.get(player.getUUID());
            int score = snake == null ? 0 : snake.tail.size();
            GameMember member = session.member(player.getUUID());
            if (member != null) {
                member.score = score;
            }
            if (score > best) {
                best = score;
                bestId = player.getUUID();
                tie = false;
            } else if (score == best) {
                tie = true;
            }
        }
        if (bestId == null || tie) {
            session.finishDraw(ServerI18n.tr("carpetprimaryuan.fakegames.snake.draw"));
        } else {
            session.finish(bestId);
        }
    }

    @Override
    public void onPlayerLeave(GameSession session, UUID playerId, ServerPlayer player, boolean eliminated) {
        SnakePlayer snake = snakes.remove(playerId);
        if (snake == null) {
            return;
        }
        // 尾巴停在原地，变回无主假人供其他人收集
        for (TailNode node : snake.tail) {
            if (node.entity != null) {
                node.entity.discard();
            }
            if (foods.size() < foodTarget) {
                Food food = new Food(node.pos);
                food.entity = spawnVisual(session, node.pos, true);
                foods.add(food);
            }
        }
        snake.tail.clear();
        if (session.phase == SessionPhase.RUNNING && snakes.size() <= 1
                && session.activeCount() <= 1) {
            UUID winner = null;
            for (UUID id : snakes.keySet()) {
                winner = id;
            }
            if (winner == null) {
                session.finishDraw(ServerI18n.tr("carpetprimaryuan.fakegames.snake.draw"));
            } else {
                session.finish(winner);
            }
        }
    }

    @Override
    public void onEnd(GameSession session) {
        clearAll();
    }

    private void clearAll() {
        for (Food food : foods) {
            if (food.entity != null) {
                food.entity.discard();
            }
        }
        foods.clear();
        for (SnakePlayer snake : snakes.values()) {
            for (TailNode node : snake.tail) {
                if (node.entity != null) {
                    node.entity.discard();
                }
            }
            snake.tail.clear();
            snake.history.clear();
        }
        snakes.clear();
    }
}
