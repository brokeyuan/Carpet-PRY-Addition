package me.primaryuan.carpet.replay.storage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 保存后轮换数量：新录像已计入 entries，19→20 不删，20→21 只删一份最早未收藏。 */
class ReplayRotationTest
{
    private static ReplayStorage.ReplayEntry entry(String name, long createdAt)
    {
        return new ReplayStorage.ReplayEntry(name, "id-" + name, createdAt, 20,
                "owner", "Owner", me.primaryuan.carpet.replay.common.ReplayAccess.PUBLIC,
                "minecraft:overworld", 0, 0, 0, false);
    }

    /** 按 createdAt 倒序（最新在前），与 listEntries 一致 */
    private static List<ReplayStorage.ReplayEntry> entries(int count)
    {
        List<ReplayStorage.ReplayEntry> out = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            out.add(entry("r" + i, 1000L - i));
        }
        return out;
    }

    @Test
    void savingNineteenthAndTwentiethDeletesNothing()
    {
        assertEquals(0, ReplayStorage.overCount(19));
        assertEquals(0, ReplayStorage.overCount(20), "19→20 不得额外淘汰");
    }

    @Test
    void savingTwentyFirstDeletesExactlyOneOldest()
    {
        assertEquals(1, ReplayStorage.overCount(21));
        List<ReplayStorage.ReplayEntry> all = entries(21);
        List<ReplayStorage.ReplayEntry> picked = ReplayStorage.selectRotationCandidates(
                all, List.of(), "id-r0", 1);
        assertEquals(1, picked.size());
        assertEquals("r20", picked.get(0).name(), "删除最早的一份");
    }

    @Test
    void favoritesAreProtected()
    {
        List<ReplayStorage.ReplayEntry> all = entries(21);
        List<String> favs = List.of("id-r20", "id-r19");
        List<ReplayStorage.ReplayEntry> picked = ReplayStorage.selectRotationCandidates(
                all, favs, "id-r0", 1);
        assertEquals(List.of("r18"), picked.stream().map(ReplayStorage.ReplayEntry::name).toList(),
                "跳过收藏后取最早的未收藏");
    }

    @Test
    void allFavoritedSelectsNothing()
    {
        List<ReplayStorage.ReplayEntry> all = entries(21);
        List<String> favs = all.stream().map(ReplayStorage.ReplayEntry::recordingId).toList();
        assertTrue(ReplayStorage.selectRotationCandidates(all, favs, "id-r0", 1).isEmpty(),
                "全部收藏时宁可不删也不动收藏");
    }

    @Test
    void newlyWrittenFileIsNeverSelectedEvenIfOldestTimestamp()
    {
        List<ReplayStorage.ReplayEntry> all = entries(21);
        // keepName 理论上是刚写入的最新文件；即使时间戳异常也不得淘汰
        List<ReplayStorage.ReplayEntry> picked = ReplayStorage.selectRotationCandidates(
                all, List.of(), "id-r20", 1);
        assertEquals("r19", picked.get(0).name());
    }
}
