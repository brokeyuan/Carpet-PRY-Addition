package me.primaryuan.carpet.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 村民昼夜日程锁：里程碑按原版 {@code villager_schedule} 数据包 json
 * （26.3：10 idle / 2000 work / 9000 meet / 11000 idle / 12000 rest，周期
 * 24000，rest 跨 24000 回绕至次日 10；幼年 3000/6000/10000 play 交替）。
 * 变异验证：改任一关键帧 → 对应里程碑用例失败。
 */
class VillagerScheduleTest {

    @Test
    void adultTimelineMatchesVanillaData() {
        assertEquals(VillagerSchedule.Activity.REST, VillagerSchedule.activityFor(0, false),
                "0~9：rest 回绕尾段（12000 起持续到次日 10）");
        assertEquals(VillagerSchedule.Activity.IDLE, VillagerSchedule.activityFor(10, false),
                "10：起床 idle");
        assertEquals(VillagerSchedule.Activity.IDLE, VillagerSchedule.activityFor(1999, false));
        assertEquals(VillagerSchedule.Activity.WORK, VillagerSchedule.activityFor(2000, false),
                "2000：上工");
        assertEquals(VillagerSchedule.Activity.WORK, VillagerSchedule.activityFor(8999, false));
        assertEquals(VillagerSchedule.Activity.MEET, VillagerSchedule.activityFor(9000, false),
                "9000：钟下集合");
        assertEquals(VillagerSchedule.Activity.MEET, VillagerSchedule.activityFor(10999, false));
        assertEquals(VillagerSchedule.Activity.IDLE, VillagerSchedule.activityFor(11000, false),
                "11000：散场 idle");
        assertEquals(VillagerSchedule.Activity.REST, VillagerSchedule.activityFor(12000, false),
                "12000：回床 rest");
        assertEquals(VillagerSchedule.Activity.REST, VillagerSchedule.activityFor(23999, false));
    }

    @Test
    void babyTimelineMatchesVanillaData() {
        assertEquals(VillagerSchedule.Activity.PLAY, VillagerSchedule.activityFor(3000, true),
                "幼年 3000~5999：play");
        assertEquals(VillagerSchedule.Activity.IDLE, VillagerSchedule.activityFor(6000, true),
                "幼年 6000~9999：idle");
        assertEquals(VillagerSchedule.Activity.PLAY, VillagerSchedule.activityFor(10000, true),
                "幼年 10000~11999：play");
        assertEquals(VillagerSchedule.Activity.WORK, VillagerSchedule.activityFor(2000, false),
                "成年 2000：work（幼年不工作，对照）");
        assertEquals(VillagerSchedule.Activity.REST, VillagerSchedule.activityFor(12000, true),
                "幼年 12000：rest");
    }

    @Test
    void wrapsBeyondOneDay() {
        assertEquals(VillagerSchedule.activityFor(2000, false), VillagerSchedule.activityFor(2000 + 24000, false));
        assertEquals(VillagerSchedule.activityFor(13000, false), VillagerSchedule.activityFor(-11000, false),
                "负数时刻按正模回绕");
    }
}
