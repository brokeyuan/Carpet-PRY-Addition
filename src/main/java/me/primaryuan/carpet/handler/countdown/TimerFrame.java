package me.primaryuan.carpet.handler.countdown;

/**
 * 单次显示帧（纯 Java，零 MC 依赖；发包映射在 CountdownDisplay）。
 *
 * <p>不可变值对象：所有字段均可为 null，表示"该渠道本帧无内容/不更新"。
 * Manager 按 tick 组帧，Display 按帧差量发包——顶部名称与进度分开比较，
 * 中央连续帧用 0/25/0 避免重复淡入，一次性阶段由 {@link Anim} 决定动画参数。</p>
 *
 * @param barText      BossBar 名称文本
 * @param barProgress  BossBar 进度
 * @param barColor     BossBar 颜色
 * @param titleText    中央大字（null = 本帧不发中央）
 * @param subtitleText 中央副标题（titleText 非 null 时生效，可为 null 表示清空副标题）
 * @param actionText   动作栏文本
 * @param anim         动画阶段
 */
public record TimerFrame(
        String barText,
        Float barProgress,
        TimerColor barColor,
        String titleText,
        String subtitleText,
        String actionText,
        Anim anim
) {

    /** 动画阶段：连续帧 0/25/0；一次性闪烁 5/30/5；结果 5/50/5（3 秒窗口） */
    public enum Anim { CONTINUOUS, FLASH, RESULT }

    /** 是否携带中央内容 */
    public boolean hasCentral() {
        return titleText != null;
    }
}
