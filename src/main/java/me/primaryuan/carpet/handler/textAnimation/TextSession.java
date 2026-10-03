package me.primaryuan.carpet.handler.textAnimation;

import com.mojang.math.Transformation;
import me.primaryuan.carpet.mixins.rule.textAnimation.DisplayInvoker;
import me.primaryuan.carpet.mixins.rule.textAnimation.TextDisplayInvoker;
import me.primaryuan.carpet.util.ServerTickScheduler;
import me.primaryuan.carpet.handler.textAnimation.TextAnimationHandler.Group;
import me.primaryuan.carpet.handler.textAnimation.TextAnimationHandler.Segment;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 一条 /text 的播放会话：逐字弹出（1 字/tick，随机歪斜 + 放大插值收拢 + 逐字点击音）
 * → 停留 → 结局（坠落：服务端自算重力/阻力/地面反弹 + 随机翻滚，同时逐字渐隐）。
 *
 * <p>长文本按标点分组接续播放：上一组开始坠落时下一组才开打（组间随机偏航/抬高）。
 * 时间线与观感配方对齐 maplegrove-misidechat（弹出 1.8 倍→终态 10 tick 插值、停留
 * 40 tick、坠落重力 0.03/阻力 0.99/一次反弹、渐隐 -8/tick）；坠落物理不复用掉落物
 * 骑乘（那是 Bukkit 平台的限制），由本会话每 tick 直接驱动实体坐标。</p>
 */
final class TextSession implements ServerTickScheduler.TickTask {

    /** 弹出插值时长（tick） */
    private static final int POP_TICKS = 10;
    /** 坠落翻滚插值时长（tick） */
    private static final int TUMBLE_TICKS = 10;
    /** 位置/朝向同步插值（tick，坐标每 tick 变动的客户端平滑） */
    private static final int POS_ROT_INTERP = 2;
    /** 坠落重力（格/tick²） */
    private static final float GRAVITY = 0.03f;
    /** 坠落空气阻力 */
    private static final float DRAG = 0.99f;
    /** 反弹速度保留比例 */
    private static final float BOUNCE = 0.28f;
    /** 开始渐隐前坠落持续（tick）；drop=false 时原地渐隐延迟 */
    private static final int FADE_DELAY_FALL = 24;
    private static final int FADE_DELAY_FADE = 4;
    /** 每 tick 渐隐量 */
    private static final int FADE_STEP = 8;
    /** 单字坠落兜底清除（tick） */
    private static final int DROP_FAILSAFE = 100;

    private final ServerPlayer target;
    private final ServerLevel level;
    private final Vec3 origin;
    private final float pitch;
    private final float yaw;
    private final TextOptions options;
    private final Deque<Group> pending;
    private final List<Group> falling;
    private final Random random = new Random();
    private Group current;

    TextSession(ServerPlayer target, List<Group> groups, TextOptions options,
                TextAnimationHandler.Broadcast broadcast) {
        this.target = target;
        this.level = target.level();
        this.origin = target.position();
        this.yaw = target.getYRot();
        this.pitch = target.getXRot();
        this.options = options;
        this.pending = new ArrayDeque<>(groups);
        this.falling = new ArrayList<>();
    }

    @Override
    public boolean tick(MinecraftServer server) {
        // 玩家断线/死亡/换维度即终止：字幕以发起时的位置与维度为基准，跟随已无意义
        if (target.hasDisconnected() || target.isDeadOrDying() || target.level() != level) {
            TextAnimationHandler.onSessionEnded(this);
            return false;
        }
        if (current == null && pending.isEmpty() && falling.isEmpty()) {
            TextAnimationHandler.onSessionEnded(this);
            return false;
        }

        // 上一组开始坠落时，下一组接续打字（maplegrove-misidechat 同款节奏）
        if (current == null && !pending.isEmpty()) {
            current = pending.poll();
            placeGroup(current);
        }

        if (current != null) {
            if (current.phase == Group.Phase.TYPING) {
                spawnGlyph(current, current.segments.get(current.typed));
                current.typed++;
                if (current.typed >= current.segments.size()) {
                    current.phase = Group.Phase.HOLDING;
                    current.holdLeft = options.hold;
                }
            } else if (--current.holdLeft <= 0) {
                beginDrop(current);
                falling.add(current);
                current = null;
            }
        }

        // 推进当前组（弹出过渡）与坠落组（物理+渐隐）
        if (current != null) {
            current.glyphs.removeIf(this::tickGlyph);
        }
        Iterator<Group> it = falling.iterator();
        while (it.hasNext()) {
            Group g = it.next();
            g.glyphs.removeIf(this::tickGlyph);
            if (g.glyphs.isEmpty()) {
                it.remove();
            }
        }
        return true;
    }

    /** 组生成点：脚部 + 视线方向 × distance，高度脚部 +1.3；第二组起随机偏航 ±22.5° 与随机抬高 */
    private void placeGroup(Group group) {
        float jitter = group.index == 0 ? 0.0f : random.nextFloat(45.0f) - 22.5f;
        group.yaw = yaw + jitter;
        group.pitch = pitch;
        group.baseY = origin.y + 1.3 + (group.index == 0 ? random.nextFloat(0.05f) : random.nextFloat(0.3f));
        Vec3 view = Vec3.directionFromRotation(group.pitch, group.yaw);
        group.baseX = origin.x + view.x * options.distance;
        group.baseZ = origin.z + view.z * options.distance;
        group.phase = Group.Phase.TYPING;
    }

    private void spawnGlyph(Group group, Segment seg) {
        // 右向量取偏航 +180° 的正交方向：首个字符落在执行者视角的左侧（阅读方向左→右）
        double rightX = Math.cos(Math.toRadians(group.yaw + 180.0));
        double rightZ = Math.sin(Math.toRadians(group.yaw + 180.0));
        double off = seg.centerOffset * options.spacing;
        double x = group.baseX + rightX * off;
        double z = group.baseZ + rightZ * off;
        double y = group.baseY;

        Display.TextDisplay entity = new Display.TextDisplay(TextAnimationHandler.TEXT_DISPLAY_TYPE, level);
        entity.setPos(x, y, z);
        TextDisplayInvoker text = (TextDisplayInvoker) entity;
        text.pry$setText(Component.literal(String.valueOf(seg.c)).setStyle(seg.style));
        text.pry$setTextOpacity((byte) 255);
        text.pry$setBackgroundColor(0);
        text.pry$setFlags(Display.TextDisplay.FLAG_SHADOW);
        entity.setGlowingTag(options.glow);

        DisplayInvoker display = (DisplayInvoker) entity;
        display.pry$setBillboardConstraints(Display.BillboardConstraints.FIXED);
        display.pry$setBrightnessOverride(Brightness.FULL_BRIGHT);
        display.pry$setTransformationInterpolationDuration(POP_TICKS);
        display.pry$setPosRotInterpolationDuration(POS_ROT_INTERP);

        // 初始弹出变换：随机歪斜 ±45° + 1.8 倍放大 + 随机 y 抖动；下一 tick 插值收拢到终态
        float startRotYaw = -(group.yaw + 180f) + (random.nextInt(90) - 45);
        float startRotPitch = group.pitch + (random.nextInt(90) - 45);
        float startOffsetY = random.nextFloat(0.25f) - 0.125f;
        display.pry$setTransformation(new Transformation(
                new Vector3f(0, startOffsetY, 0),
                new Quaternionf()
                        .rotateY((float) Math.toRadians(startRotYaw))
                        .rotateX((float) Math.toRadians(startRotPitch)),
                new Vector3f(options.scale * 1.8f),
                new Quaternionf()));
        level.addFreshEntity(entity);
        entity.addTag(TextAnimationHandler.ENTITY_TAG);

        if (options.sound) {
            level.playSound(null, x, y, z, SoundEvents.STONE_BUTTON_CLICK_ON,
                    SoundSource.BLOCKS, 1.0f, 1.2f);
        }

        Quaternionf finalRot = new Quaternionf()
                .rotateY((float) Math.toRadians(-(group.yaw + 180f)))
                .rotateX((float) Math.toRadians(group.pitch));
        group.glyphs.add(new Glyph(entity, x, y, z, finalRot));
    }

    private void beginDrop(Group group) {
        for (Glyph glyph : group.glyphs) {
            glyph.dropping = true;
        }
    }

    /** @return true = 已结束（删除） */
    private boolean tickGlyph(Glyph glyph) {
        if (glyph.dead || !glyph.entity.isAlive()) {
            discard(glyph);
            return true;
        }

        // 弹出过渡：生成后的下一 tick 应用终态，客户端 10 tick 插值收拢
        if (glyph.popAge < 1) {
            glyph.popAge++;
            DisplayInvoker display = (DisplayInvoker) glyph.entity;
            display.pry$setTransformationInterpolationDelay(0);
            display.pry$setTransformation(new Transformation(
                    new Vector3f(0, 0, 0), glyph.finalRot,
                    new Vector3f(options.scale), new Quaternionf()));
            return false;
        }

        if (!glyph.dropping) {
            return false;
        }

        glyph.age++;
        if (options.drop && !glyph.landed) {
            glyph.vy = (glyph.vy - GRAVITY) * DRAG;
            glyph.y += glyph.vy;
            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) Math.floor(glyph.x), (int) Math.floor(glyph.z));
            if (glyph.y <= groundY && glyph.vy < 0) {
                if (!glyph.bounced && glyph.vy < -0.06) {
                    glyph.y = groundY;
                    glyph.vy = -glyph.vy * BOUNCE;
                    glyph.bounced = true;
                } else {
                    glyph.y = groundY;
                    glyph.landed = true;
                }
            }
            // 同步降频：posRotInterp=2 下每 2 tick 一次 setPos，多人场景包量减半
            if (glyph.age % 2 == 0) {
                glyph.entity.setPos(glyph.x, glyph.y, glyph.z);
            }
        }

        // 坠落起手一次性随机翻滚（对齐 maplegrove-misidechat 的 tumbling 配方）
        if (options.drop && glyph.age == 1) {
            Quaternionf tumble = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(270 + random.nextInt(90)),
                    (float) Math.toRadians(random.nextInt(90) - 45),
                    (float) Math.toRadians(random.nextInt(90) - 45));
            DisplayInvoker display = (DisplayInvoker) glyph.entity;
            display.pry$setTransformationInterpolationDelay(0);
            display.pry$setTransformationInterpolationDuration(TUMBLE_TICKS);
            display.pry$setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new Quaternionf(glyph.finalRot).mul(tumble),
                    new Vector3f(options.scale), new Quaternionf()));
        }

        int fadeDelay = options.drop ? FADE_DELAY_FALL : FADE_DELAY_FADE;
        if (glyph.age >= fadeDelay) {
            glyph.opacity = (byte) (glyph.opacity - FADE_STEP);
            if (glyph.opacity <= 0) {
                discard(glyph);
                return true;
            }
            ((TextDisplayInvoker) glyph.entity).pry$setTextOpacity(glyph.opacity);
        }
        if (glyph.age >= DROP_FAILSAFE) {
            discard(glyph);
            return true;
        }
        return false;
    }

    private void discard(Glyph glyph) {
        glyph.dead = true;
        if (glyph.entity.isAlive()) {
            glyph.entity.discard();
        }
    }

    /** 停服兜底：立即清除本会话全部实体 */
    void killAll() {
        if (current != null) {
            for (Glyph glyph : current.glyphs) {
                discard(glyph);
            }
            current.glyphs.clear();
            current = null;
        }
        for (Group group : falling) {
            for (Glyph glyph : group.glyphs) {
                discard(glyph);
            }
            group.glyphs.clear();
        }
        falling.clear();
        pending.clear();
    }
}
