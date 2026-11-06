package me.primaryuan.carpet.handler.playerHead;

import me.primaryuan.carpet.i18n.ServerI18n;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** 头颅物品的创建与外观生成：档案写 CUSTOM_DATA，Lore 只作为档案的显示。 */
public final class HeadItems {

    private HeadItems() {}

    public static ItemStack universal(int count) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD, count);
        HeadData data = HeadData.universal();
        data.write(stack);
        applyLore(stack, data);
        return stack;
    }

    /** 把档案派生的 Lore 写回物品；外部自定义名称与 PROFILE 不受影响。 */
    public static void applyLore(ItemStack stack, HeadData data) {
        if (data == null) return;
        List<Component> lines = new ArrayList<>(4);
        if (data.kind() == HeadData.Kind.UNIVERSAL) {
            lines.add(line(data.hasTexture()
                    ? ServerI18n.tr("carpetprimaryuan.head.lore.source", data.sourceName())
                    : ServerI18n.tr("carpetprimaryuan.head.lore.unset")));
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    /** 把皮肤快照写入原版 PROFILE；只在结果物品上调用，不改动铁砧输入。 */
    public static void applySkin(ItemStack stack, HeadSkin skin) {
        if (stack == null || stack.isEmpty() || skin == null || !skin.hasTexture()) return;
        stack.set(DataComponents.PROFILE, skin.resolvable());
    }

    public static boolean isUniversal(ItemStack stack) {
        HeadData data = HeadData.read(stack);
        return data != null && data.kind() == HeadData.Kind.UNIVERSAL;
    }

    private static Component line(Component text) {
        return text.copy().withStyle(ChatFormatting.GRAY);
    }
}
