package me.primaryuan.carpet.handler.redPacket;

import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 口令纸归属标记（CustomData 组件）：改名不丢、玩家同名纸不混淆 */
class PasswordPaperMarkerTest {
    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    @Test
    void uiPaperIsIdentifiedByMarkerNotName() throws Exception {
        Method isPasswordPaper = RedPacketGui.PasswordAnvilMenu.class
                .getDeclaredMethod("isPasswordPaper", ItemStack.class);
        isPasswordPaper.setAccessible(true);

        // UI 用纸：带标记，识别为口令纸
        ItemStack uiPaper = RedPacketGui.PasswordAnvilMenu.passwordPaper();
        assertTrue((boolean) isPasswordPaper.invoke(null, uiPaper));

        // 铁砧改名 = 在同一组件集上覆盖 CUSTOM_NAME：标记组件保留，改名后仍可回收
        ItemStack renamed = uiPaper.copyWithCount(1);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("口令"));
        assertFalse(RedPacketGui.PASSWORD_PAPER_NAME.equals(renamed.getHoverName().getString()));
        assertTrue((boolean) isPasswordPaper.invoke(null, renamed), "改名后的 UI 纸必须仍被识别");

        // 玩家自己的同名纸：无标记，关闭铁砧时不得被回收
        ItemStack realPaper = new ItemStack(Items.PAPER);
        realPaper.set(DataComponents.CUSTOM_NAME, Component.literal(RedPacketGui.PASSWORD_PAPER_NAME));
        assertFalse((boolean) isPasswordPaper.invoke(null, realPaper), "同名普通纸不得识别为 UI 纸");
    }

    @Test
    void markerLivesInCustomData() {
        // 直接校验组件内容，防止实现退回"按名字识别"
        ItemStack uiPaper = RedPacketGui.PasswordAnvilMenu.passwordPaper();
        assertTrue(uiPaper.has(DataComponents.CUSTOM_DATA));
        assertTrue(uiPaper.get(DataComponents.CUSTOM_DATA).copyTag().contains("pry_redpacket_paper"));

        ItemStack plainPaper = new ItemStack(Items.PAPER);
        assertFalse(plainPaper.has(DataComponents.CUSTOM_DATA));
    }
}
