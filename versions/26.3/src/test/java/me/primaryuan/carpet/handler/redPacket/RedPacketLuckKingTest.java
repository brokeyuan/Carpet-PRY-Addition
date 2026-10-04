package me.primaryuan.carpet.handler.redPacket;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import me.primaryuan.carpet.util.TestRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手气王结算契约（稀有度语义）：稀有度档由代码内置——1 件高稀有 > 任意数量低稀有
 * （单红包物理上限 45 格 × 64 = 2880 < 档间跨度）；同档内按价值表 × 数量排序；
 * 并列取先领取、空份不参评、无人领取返回 -1、领取顺序与份额下标一一对应。
 * 直调 {@link RedPacketManager#itemValue} 测真实函数。
 */
public class RedPacketLuckKingTest {

    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    private static RedPacket packet(List<List<ItemStack>> shares) {
        return new RedPacket(1, UUID.randomUUID(), "sender", RedPacket.Type.LUCKY,
                "test", shares, null, null, 0);
    }

    private static UUID claim(RedPacket packet) {
        UUID uuid = UUID.randomUUID();
        packet.takeShare(uuid);
        return uuid;
    }

    @Test
    void oneDiamondBeatsAnyDirt() {
        // 用户语义：1 颗钻石 > "无限"泥土——2880 = 单红包物理上限（45 格 × 64）
        RedPacket packet = packet(List.of(
                List.of(new ItemStack(Items.DIRT, 2880)),
                List.of(new ItemStack(Items.DIAMOND, 1))));
        claim(packet);
        claim(packet);
        assertEquals(1, packet.luckKingIndex(RedPacketManager::itemValue));
    }

    @Test
    void twoDiamondsBeatOne() {
        // 同档内按价值表 × 数量排序：2 颗钻石 > 1 颗钻石
        RedPacket packet = packet(List.of(
                List.of(new ItemStack(Items.DIAMOND, 1)),
                List.of(new ItemStack(Items.DIAMOND, 2))));
        claim(packet);
        claim(packet);
        assertEquals(1, packet.luckKingIndex(RedPacketManager::itemValue));
    }

    @Test
    void tieGoesToFirstClaimer() {
        RedPacket packet = packet(List.of(
                List.of(new ItemStack(Items.DIAMOND, 1)),
                List.of(new ItemStack(Items.DIAMOND, 1))));
        claim(packet);
        claim(packet);
        assertEquals(0, packet.luckKingIndex(RedPacketManager::itemValue));
    }

    @Test
    void emptySharesLose() {
        RedPacket packet = packet(List.of(
                List.of(),
                List.of(),
                List.of(new ItemStack(Items.DIAMOND, 1))));
        claim(packet);
        claim(packet);
        claim(packet);
        assertEquals(2, packet.luckKingIndex(RedPacketManager::itemValue));
    }

    @Test
    void noClaimsReturnsMinusOne() {
        RedPacket packet = packet(List.of(List.of(new ItemStack(Items.DIAMOND, 1))));
        assertEquals(-1, packet.luckKingIndex(RedPacketManager::itemValue));
    }

    @Test
    void singleItemTypeOnlyForPurePackets() {
        RedPacket single = packet(List.of(
                List.of(new ItemStack(Items.DIAMOND, 1)),
                List.of()));
        assertTrue(single.hasSingleItemType()); // 空份额不算第二种物品

        RedPacket mixed = packet(List.of(
                List.of(new ItemStack(Items.DIAMOND, 1)),
                List.of(new ItemStack(Items.DIRT, 5))));
        assertTrue(!mixed.hasSingleItemType()); // 混合物品无手气王
    }

    @Test
    void claimOrderMatchesShareIndex() {
        RedPacket packet = packet(List.of(
                List.of(new ItemStack(Items.DIAMOND, 1)),
                List.of(new ItemStack(Items.DIAMOND, 2))));
        UUID first = claim(packet);
        UUID second = claim(packet);
        assertEquals(first, packet.claimerAt(0));
        assertEquals(second, packet.claimerAt(1));
    }
}
