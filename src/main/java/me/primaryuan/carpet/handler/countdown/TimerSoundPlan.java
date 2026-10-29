package me.primaryuan.carpet.handler.countdown;

import java.util.Optional;

/**
 * 音效决策（纯 Java，零 MC 依赖；SoundId → Holder 映射在 CountdownDisplay）。
 *
 * 层级：NONE 全无声（silent / sound off / simple 默认）；NORMAL 基础反馈
 * （3/2/1 短音、结束音）；FANCY 完整配方（开场、60/30 节点、10..1 递增音高、强结束音）；
 * LEGACY 为旧 /countdown 入口保留的原始配方。静音永远覆盖一切。
 */
public final class TimerSoundPlan {

    public enum Tier { NONE, NORMAL, FANCY, LEGACY }

    public enum Id {
        /** 开场过渡（fancy） */
        OPEN_CHIME,
        /** fancy 60/30 秒节点一声 */
        MILESTONE_CHIME,
        /** 旧入口长程节拍钟声 */
        MILESTONE_BELL,
        /** normal 3/2/1 短音 */
        TICK_HAT,
        /** fancy 与旧入口逐秒倒数 */
        TICK_PLING,
        /** 结束音（normal 音量 0.6 / fancy 0.8） */
        FINISH_BELL,
        /** 旧入口归零提示音 */
        FINISH_LEVELUP
    }

    /** 一次性声音事件：id 选择配方，volume/pitch 为最终发送值 */
    public record Cue(Id id, float volume, float pitch) {
    }

    private TimerSoundPlan() {}

    /** fancy 10..1 递增音高（半音阶），normal 3/2/1 短音；窗口外一律无声 */
    public static Optional<Cue> tick(Tier tier, int sec) {
        return switch (tier) {
            case NORMAL -> sec <= 3 && sec >= 1
                    ? Optional.of(new Cue(Id.TICK_HAT, 0.4F, 1.0F)) : Optional.empty();
            case FANCY -> sec <= 10 && sec >= 1
                    ? Optional.of(new Cue(Id.TICK_PLING, 0.6F, (float) Math.pow(2.0, (10 - sec) / 12.0)))
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    /** 长程节点：fancy 60/30 秒一声；旧入口钟声由 LEGACY 配方承担 */
    public static Optional<Cue> milestone(Tier tier) {
        return tier == Tier.FANCY ? Optional.of(new Cue(Id.MILESTONE_CHIME, 0.5F, 1.0F)) : Optional.empty();
    }

    /** 旧入口节拍钟声（LEGACY 专属） */
    public static Optional<Cue> legacyMilestone() {
        return Optional.of(new Cue(Id.MILESTONE_BELL, 1.0F, 1.0F));
    }

    /** 开场音：仅 fancy，且启动已处于倒数窗口时改发倒数音、不叠加开场音 */
    public static Optional<Cue> opening(Tier tier, int startSec, boolean startInTickWindow) {
        if (tier != Tier.FANCY) {
            return Optional.empty();
        }
        if (startInTickWindow) {
            return tick(tier, startSec);
        }
        return Optional.of(new Cue(Id.OPEN_CHIME, 0.5F, 1.0F));
    }

    /** down 自然归零结束音 */
    public static Optional<Cue> finish(Tier tier) {
        return switch (tier) {
            case NORMAL -> Optional.of(new Cue(Id.FINISH_BELL, 0.6F, 1.0F));
            case FANCY -> Optional.of(new Cue(Id.FINISH_BELL, 0.8F, 1.0F));
            case LEGACY -> Optional.of(new Cue(Id.FINISH_LEVELUP, 1.0F, 1.0F));
            default -> Optional.empty();
        };
    }

    /** up 手动停止结算音（与结束音同配方；simple/无声层不发） */
    public static Optional<Cue> upStop(Tier tier) {
        return switch (tier) {
            case NORMAL -> Optional.of(new Cue(Id.FINISH_BELL, 0.6F, 1.0F));
            case FANCY -> Optional.of(new Cue(Id.FINISH_BELL, 0.8F, 1.0F));
            default -> Optional.empty();
        };
    }
}
