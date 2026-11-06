package me.primaryuan.carpet.handler.fakegames;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 假游戏临时状态：优先使用可独立撤销的属性修饰符，不覆盖玩家基础值。
 *
 * <p>缩小使用固定 ID 的 transient modifier（-40% 总倍率）。transient 不写入玩家存档，
 * 崩溃/重启后不会残留；恢复时只移除本 ID，管理员/药水/其他模组修改的基础值、
 * 永久修饰符与外部效果都保留。若管理员在局中修改基础大小，本局修饰符仍按当前
 * 基础值计算，结束时不会写回开局快照。</p>
 */
public final class FakeGameTempEffects {

    private static final String SHRINK_PATH = "fakegames_shrink";
    private static final double SHRINK_AMOUNT = -0.40;

    private FakeGameTempEffects() {}

    /** 应用缩小；属性不存在时返回 false（调用方不应假装已生效）。 */
    public static boolean applyShrink(ServerPlayer player) {
        return applyShrink(player.getAttribute(Attributes.SCALE));
    }

    public static void removeShrink(ServerPlayer player) {
        removeShrink(player.getAttribute(Attributes.SCALE));
    }

    public static boolean hasShrink(ServerPlayer player) {
        return hasShrink(player.getAttribute(Attributes.SCALE));
    }

    /** 测试/内部入口：对任意缩放属性实例应用。 */
    static boolean applyShrink(AttributeInstance attribute) {
        if (attribute == null) {
            return false;
        }
        //#if MC >= 12111
        net.minecraft.resources.Identifier id =
                net.minecraft.resources.Identifier.fromNamespaceAndPath("carpet-pry-addition", SHRINK_PATH);
        //#else
        //$$ net.minecraft.resources.ResourceLocation id =
        //$$         net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("carpet-pry-addition", SHRINK_PATH);
        //#endif
        attribute.removeModifier(id);
        attribute.addTransientModifier(new AttributeModifier(id, SHRINK_AMOUNT,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        return true;
    }

    static void removeShrink(AttributeInstance attribute) {
        if (attribute == null) {
            return;
        }
        //#if MC >= 12111
        net.minecraft.resources.Identifier id =
                net.minecraft.resources.Identifier.fromNamespaceAndPath("carpet-pry-addition", SHRINK_PATH);
        //#else
        //$$ net.minecraft.resources.ResourceLocation id =
        //$$         net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("carpet-pry-addition", SHRINK_PATH);
        //#endif
        attribute.removeModifier(id);
    }

    static boolean hasShrink(AttributeInstance attribute) {
        if (attribute == null) {
            return false;
        }
        //#if MC >= 12111
        net.minecraft.resources.Identifier id =
                net.minecraft.resources.Identifier.fromNamespaceAndPath("carpet-pry-addition", SHRINK_PATH);
        //#else
        //$$ net.minecraft.resources.ResourceLocation id =
        //$$         net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("carpet-pry-addition", SHRINK_PATH);
        //#endif
        return attribute.getModifier(id) != null;
    }

    /** 纯函数：给测试明确的期望值（基础值 × 0.6）。 */
    static double shrinkValue(double base) {
        return base * (1.0 + SHRINK_AMOUNT);
    }
}
