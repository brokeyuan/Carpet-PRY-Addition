package me.primaryuan.carpet.brain;

/**
 * 村民昼夜日程（原版数据驱动日程的时间线等价移植）。
 *
 * <p>原版 {@code villager_schedule} 时间线（26.3 数据包 json，周期 24000 tick，
 * overworld 时钟）：成年村民 10→idle / 2000→work / 9000→meet / 11000→idle /
 * 12000→rest；幼年村民 10→idle / 3000→play / 6000→idle / 10000→play / 12000→rest。
 * rest 段跨越 24000 回绕，持续到次日 10。</p>
 *
 * <p>活动语义：IDLE = 村民自主游荡（原版 idle 活动下挂 RandomStroll 同构）；
 * WORK = 前往职业工作站；MEET = 前往钟（集合点）社交；REST = 回床入睡（跨夜）；
 * PLAY = 幼年玩耍（移植版未实现互动，见披露）。</p>
 */
public final class VillagerSchedule {

    public enum Activity { IDLE, WORK, MEET, REST, PLAY }

    private VillagerSchedule() {
    }

    /** 世界昼钟时间（原版日程的 overworld 时钟；26.x 起走时钟管理器） */
    public static long dayTime(net.minecraft.world.level.Level level) {
        //#if MC >= 260102
        //$$ return level.getOverworldClockTime();
        //#else
        return level.getDayTime();
        //#endif
    }

    /** 原版关键帧（adult：10/2000/9000/11000/12000）→ 当前活动 */
    public static Activity activityFor(long dayTime, boolean baby) {
        int t = (int) (((dayTime % 24000L) + 24000L) % 24000L);
        if (baby) {
            if (t < 10) {
                return Activity.REST;
            }
            if (t < 3000) {
                return Activity.IDLE;
            }
            if (t < 6000) {
                return Activity.PLAY;
            }
            if (t < 10000) {
                return Activity.IDLE;
            }
            if (t < 12000) {
                return Activity.PLAY;
            }
            return Activity.REST;
        }
        if (t < 10) {
            return Activity.REST; // 24000 回绕尾段：rest 持续到次日 10
        }
        if (t < 2000) {
            return Activity.IDLE;
        }
        if (t < 9000) {
            return Activity.WORK;
        }
        if (t < 11000) {
            return Activity.MEET;
        }
        if (t < 12000) {
            return Activity.IDLE;
        }
        return Activity.REST;
    }
}
