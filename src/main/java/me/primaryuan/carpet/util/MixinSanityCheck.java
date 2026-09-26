package me.primaryuan.carpet.util;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * require=0 注入点的启动自检。
 *
 * <p>require=0 的注入在目标方法缺失/签名漂移时会被 Mixin 静默跳过：
 * 规则开关照常、无任何日志，功能悄悄变成摆设。本类在启动与规则变更时
 * 对这些注入点做运行时核验，失效即以 ERROR 点名具体规则——既不无反馈，
 * 也不至于为可跳过的注入点牺牲启动。</p>
 *
 * <p>核验方式：在目标类 {@code getDeclaredMethods()} 里按<b>参数类型形状 +
 * static 性</b>匹配（类型用本模组代码里的 Class 字面量比较——构建期 loom 已把
 * 类引用重映射到运行时命名空间，类型相等性在 dev/生产 intermediary/26.x
 * 未混淆下均成立，无需按方法名跨命名空间换算）。这能覆盖现实失败模式
 * （MC 更新移除/移动方法、参数增删、实例化）；@Local ordinal 一类的深漂移
 * 无法静态检出，不在此列。</p>
 *
 * <p>每个站点每次启动至多报一次（reported 去重）；规则关闭时不核验
 * （注入跳过与否此时无差别）。</p>
 */
public final class MixinSanityCheck {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    /** 已报过问题的站点（每次启动至多报一次） */
    private static final Set<String> reported = new HashSet<>();
    private static boolean initialized = false;

    private MixinSanityCheck() {}

    /** CarpetPrimaryuanServer.onGameStarted 调用 */
    public static void init() {
        if (initialized) return;
        initialized = true;
        checkAll();
    }

    /** 规则变更时回调（只需关心本类核验涉及的规则名） */
    public static void onRuleChanged(String ruleName) {
        if (ruleName.equals("playerHat") || ruleName.equals("ridingPlayers")
                || ruleName.equals("ridingPlayersClientInteract") || ruleName.equals("fixXaeroLib")) {
            checkAll();
        }
    }

    private static void checkAll() {
        checkPlayerHat();
        checkRidingClientInteract();
        checkFixXaeroLib();
    }

    /** playerHat：LivingEntity.isEquippableInSlot(ItemStack, EquipmentSlot)Z（实例方法） */
    private static void checkPlayerHat() {
        if (!CarpetPrimaryuanSettings.playerHat) {
            return;
        }
        if (methodExists(LivingEntity.class,
                new Class<?>[]{ItemStack.class, EquipmentSlot.class}, false)) {
            return;
        }
        if (reported.add("playerHat")) {
            LOGGER.error("[pry] playerHat 规则无效：LivingEntity.isEquippableInSlot 签名漂移，"
                    + "头部装备槽注入被静默跳过，请向模组作者反馈");
        }
    }

    /** ridingPlayers：ProjectileUtil.getEntityHitResult 6 参静态方法（仅客户端注入） */
    private static void checkRidingClientInteract() {
        if (!CarpetPrimaryuanSettings.ridingPlayers || !CarpetPrimaryuanSettings.ridingPlayersClientInteract) {
            return;
        }
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        if (methodExists(ProjectileUtil.class,
                new Class<?>[]{Entity.class, Vec3.class, Vec3.class, AABB.class, Predicate.class, double.class},
                true)) {
            return;
        }
        if (reported.add("ridingPlayersClientInteract")) {
            LOGGER.error("[pry] ridingPlayers 规则的客户端交互修正无效：ProjectileUtil.getEntityHitResult "
                    + "签名漂移，注入被静默跳过，请向模组作者反馈");
        }
    }

    /**
     * fixXaeroLib：xaero.lib 的 handle 方法须为静态双参（handler 的签名假设，
     * 即审查 #29——此前无法本地验证，现于运行时核验）。目标类缺失属"未安装"
     * 而非失效，降为 warn。
     */
    private static void checkFixXaeroLib() {
        if (!CarpetPrimaryuanSettings.fixXaeroLib) {
            return;
        }
        Class<?> target;
        try {
            target = Class.forName(
                    "xaero.lib.common.player.config.permission.PlayerConfigChannelPermissionUpdater",
                    false, MixinSanityCheck.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            if (reported.add("fixXaeroLib:absent")) {
                LOGGER.warn("[pry] fixXaeroLib 已开启但未检测到 xaero.lib（未安装时本规则不起作用）");
            }
            return;
        }
        Method handle = null;
        for (Method m : target.getDeclaredMethods()) {
            if (m.getName().equals("handle") && m.getParameterCount() == 2) {
                handle = m;
                break;
            }
        }
        if (handle == null || !Modifier.isStatic(handle.getModifiers())) {
            if (reported.add("fixXaeroLib")) {
                LOGGER.error("[pry] fixXaeroLib 规则无效：xaero.lib 的 handle 方法缺失或非静态，"
                        + "注入被静默跳过，请向模组作者反馈");
            }
        }
    }

    /**
     * 按参数类型形状 + static 性在 owner 的声明方法中匹配；
     * 类型比较用 Class 引用（运行时同命名空间，跨版本/跨映射环境成立）。
     */
    private static boolean methodExists(Class<?> owner, Class<?>[] paramTypes, boolean expectStatic) {
        try {
            for (Method m : owner.getDeclaredMethods()) {
                if (m.getParameterCount() != paramTypes.length) {
                    continue;
                }
                if (Modifier.isStatic(m.getModifiers()) != expectStatic) {
                    continue;
                }
                Class<?>[] actual = m.getParameterTypes();
                boolean allMatch = true;
                for (int i = 0; i < actual.length; i++) {
                    if (actual[i] != paramTypes[i]) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
