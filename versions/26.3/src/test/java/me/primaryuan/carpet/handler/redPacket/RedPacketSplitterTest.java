package me.primaryuan.carpet.handler.redPacket;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import me.primaryuan.carpet.util.TestRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 红包切分的核心契约：总量守恒（不能多不能少）、份数正确、余数落份规则、
 * 物品数少于份数时的空份分布、普通分配的种类平分。
 */
public class RedPacketSplitterTest {

    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    private static ItemStack diamonds(int count) {
        return new ItemStack(Items.DIAMOND, count);
    }

    private static int total(List<List<ItemStack>> shares) {
        return shares.stream().flatMap(List::stream).mapToInt(ItemStack::getCount).sum();
    }

    @Test
    void luckyConservesTotal() {
        Random random = new Random(42);
        for (int trial = 0; trial < 200; trial++) {
            int items = random.nextInt(130);
            int shares = 1 + random.nextInt(100);
            List<List<ItemStack>> out = RedPacketSplitter.lucky(
                    items > 0 ? List.of(diamonds(items)) : List.of(), shares, random);
            assertEquals(items, total(out), "总量守恒失败: items=" + items + " shares=" + shares);
        }
    }

    @Test
    void luckyFewerItemsThanSharesGivesDistinctSingletons() {
        // 10 钻石 5 份：每份非空可不等；物品数 < 份数时非空份各拿 1
        List<List<ItemStack>> out = RedPacketSplitter.lucky(List.of(diamonds(3)), 5, new Random(7));
        assertEquals(5, out.size());
        long nonEmpty = out.stream().filter(s -> !s.isEmpty()).count();
        assertEquals(3, nonEmpty);
        for (List<ItemStack> share : out) {
            if (!share.isEmpty()) {
                assertEquals(1, share.size());
                assertEquals(1, share.get(0).getCount());
            }
        }
        assertEquals(3, total(out));
    }

    @Test
    void luckySingleShareKeepsWhole() {
        List<List<ItemStack>> out = RedPacketSplitter.lucky(List.of(diamonds(10)), 1, new Random(1));
        assertEquals(1, out.size());
        assertEquals(10, out.get(0).get(0).getCount());
    }

    @Test
    void evenSplitsWithRandomRemainder() {
        // 10 钻石 3 份：3/3/4，余数 1 随机落在某一份
        List<List<ItemStack>> out = RedPacketSplitter.even(List.of(diamonds(10)), 3, new Random(3));
        assertEquals(3, out.size());
        int[] counts = out.stream().mapToInt(s -> s.get(0).getCount()).toArray();
        java.util.Arrays.sort(counts);
        assertEquals(3, counts[0]);
        assertEquals(3, counts[1]);
        assertEquals(4, counts[2]);
        assertEquals(10, total(out));
    }

    @Test
    void evenRemainderZeroSplitsEvenly() {
        List<List<ItemStack>> out = RedPacketSplitter.even(List.of(diamonds(12)), 4, new Random(5));
        for (List<ItemStack> share : out) {
            assertEquals(1, share.size());
            assertEquals(3, share.get(0).getCount());
        }
    }

    @Test
    void evenFewerItemsThanSharesGivesSingletons() {
        // 2 钻石 5 份：base=0 余数 2 → 2 份各拿 1，其余为空
        List<List<ItemStack>> out = RedPacketSplitter.even(List.of(diamonds(2)), 5, new Random(9));
        assertEquals(5, out.size());
        assertEquals(2, out.stream().filter(s -> !s.isEmpty()).count());
        assertEquals(2, total(out));
    }

    @Test
    void evenKeepsItemTypesSeparate() {
        // 多种物品独立平分，各自的种类与数量守恒
        List<List<ItemStack>> out = RedPacketSplitter.even(
                List.of(diamonds(7), new ItemStack(Items.APPLE, 5)), 3, new Random(11));
        int diamondsTotal = 0;
        int applesTotal = 0;
        for (List<ItemStack> share : out) {
            for (ItemStack stack : share) {
                if (stack.is(Items.DIAMOND)) {
                    diamondsTotal += stack.getCount();
                } else if (stack.is(Items.APPLE)) {
                    applesTotal += stack.getCount();
                }
            }
        }
        assertEquals(7, diamondsTotal);
        assertEquals(5, applesTotal);
    }

    @Test
    void wholeGivesSingleShare() {
        List<List<ItemStack>> out = RedPacketSplitter.whole(List.of(diamonds(10)));
        assertEquals(1, out.size());
        assertEquals(1, out.get(0).size());
        assertEquals(10, out.get(0).get(0).getCount());
        assertTrue(out.get(0).get(0).getCount() == 10);
    }

    @Test
    void luckyLargeStacksConserveAcrossManyShares() {
        // 100 份上限压力：两组物品合计守恒
        List<List<ItemStack>> out = RedPacketSplitter.lucky(
                List.of(diamonds(64), new ItemStack(Items.EMERALD, 17)), 100, new Random(99));
        assertEquals(100, out.size());
        assertEquals(64, out.stream().flatMap(List::stream)
                .filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum());
        assertEquals(17, out.stream().flatMap(List::stream)
                .filter(s -> s.is(Items.EMERALD)).mapToInt(ItemStack::getCount).sum());
    }
}
