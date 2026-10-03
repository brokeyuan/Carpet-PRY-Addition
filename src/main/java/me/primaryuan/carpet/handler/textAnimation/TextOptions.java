package me.primaryuan.carpet.handler.textAnimation;

/**
 * /text 的可调参数（经命令 options 串 k=v;k=v 解析）。
 * 默认值取自米塔字幕各参考实现（maplegrove-misidechat）调好的观感。
 */
public final class TextOptions {
    /** 字幕生成点距执行者的距离（格） */
    public final double distance;
    /** 字符缩放（弹出终态） */
    public final float scale;
    /** 字间距单位（格/宽度单位） */
    public final float spacing;
    /** 打字完成后坠落前的停留（tick） */
    public final int hold;
    /** 字符是否发光描边 */
    public final boolean glow;
    /** 逐字点击音效 */
    public final boolean sound;
    /** 停留后是否坠落（false = 原地渐隐） */
    public final boolean drop;

    private TextOptions(double distance, float scale, float spacing, int hold,
                        boolean glow, boolean sound, boolean drop) {
        this.distance = distance;
        this.scale = scale;
        this.spacing = spacing;
        this.hold = hold;
        this.glow = glow;
        this.sound = sound;
        this.drop = drop;
    }

    public static TextOptions defaults() {
        return new TextOptions(5.0, 1.2f, 0.175f, 40, false, true, true);
    }

    public static TextOptions with(TextOptions base, double distance, Float scale, Float spacing,
                                   Integer hold, Boolean glow, Boolean sound, Boolean drop) {
        return new TextOptions(
                distance,
                scale != null ? scale : base.scale,
                spacing != null ? spacing : base.spacing,
                hold != null ? hold : base.hold,
                glow != null ? glow : base.glow,
                sound != null ? sound : base.sound,
                drop != null ? drop : base.drop);
    }
}
