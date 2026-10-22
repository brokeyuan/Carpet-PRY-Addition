package me.primaryuan.carpet.handler.playerHat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/** 自动戴帽只接收没有使用功能的物品；手动 /hat 不受此策略影响。 */
public final class HatUsePolicy {
    private static final Logger LOG = LoggerFactory.getLogger("Carpet-PRY-Addition/PlayerHat");
    //#if MC >= 12111
    public static final TagKey<Item> BLACKLIST = TagKey.create(Registries.ITEM,
            net.minecraft.resources.Identifier.fromNamespaceAndPath("carpet-pry-addition", "hat_use_blacklist"));
    //#else
    //$$ public static final TagKey<Item> BLACKLIST = TagKey.create(Registries.ITEM,
    //$$         net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("carpet-pry-addition", "hat_use_blacklist"));
    //#endif

    private static final ClassValue<Boolean> CUSTOM_INTERACTION = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                int found = 0;
                // 按参数和返回值找运行时方法，避免依赖开发环境的方法名。
                for (Method base : Item.class.getDeclaredMethods()) {
                    if (Modifier.isStatic(base.getModifiers()) || !interactionShape(base)) continue;
                    found++;
                    Method actual = type.getMethod(base.getName(), base.getParameterTypes());
                    Class<?> owner = actual.getDeclaringClass();
                    if (owner == Item.class) continue;
                    if (owner == BlockItem.class && Arrays.equals(base.getParameterTypes(), new Class<?>[]{UseOnContext.class})) continue;
                    return true;
                }
                if (found != 3) throw new IllegalStateException("交互方法形状数量异常: " + found);
                return false;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError problem) {
                LOG.error("无法识别物品交互，禁用该类型自动戴帽: {}", type.getName(), problem);
                return true;
            }
        }
    };

    private HatUsePolicy() {}

    public static boolean allows(ItemStack stack) {
        if (stack.isEmpty() || stack.is(BLACKLIST) || stack.has(DataComponents.FOOD)
                || stack.has(DataComponents.TOOL) || stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES) || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(Items.MACE) || stack.is(Items.TRIDENT)) return false;
        //#if MC >= 12103
        if (stack.has(DataComponents.CONSUMABLE) || stack.has(DataComponents.USE_REMAINDER)
                || stack.has(DataComponents.USE_COOLDOWN)) return false;
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.swappable()) return false;
        //#endif
        //#if MC >= 12105
        if (equippable != null && equippable.equipOnInteract()) return false;
        if (stack.has(DataComponents.BLOCKS_ATTACKS) || stack.has(DataComponents.WEAPON)) return false;
        //#endif
        //#if MC >= 12111
        if (stack.has(DataComponents.KINETIC_WEAPON) || stack.has(DataComponents.PIERCING_WEAPON)) return false;
        //#endif
        return !hasCustomInteraction(stack.getItem().getClass());
    }

    static boolean hasCustomInteraction(Class<? extends Item> type) {
        return CUSTOM_INTERACTION.get(type);
    }

    private static boolean interactionShape(Method method) {
        Class<?>[] params = method.getParameterTypes();
        //#if MC >= 12103
        Class<?> airResult = InteractionResult.class;
        //#else
        //$$ Class<?> airResult = net.minecraft.world.InteractionResultHolder.class;
        //#endif
        return (method.getReturnType() == airResult && Arrays.equals(params,
                new Class<?>[]{Level.class, Player.class, InteractionHand.class}))
                || (method.getReturnType() == InteractionResult.class && (Arrays.equals(params,
                new Class<?>[]{UseOnContext.class}) || Arrays.equals(params,
                new Class<?>[]{ItemStack.class, Player.class, LivingEntity.class, InteractionHand.class})));
    }
}
