package me.primaryuan.carpet.handler.textAnimation;

import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;

/**
 * 一个已生成字形：对应一个 text_display 实体与其坠落物理状态。
 * 坐标为实体自身位置（弹出偏移走 transformation，不走实体坐标）；
 * 打字期间随组锚点跟随玩家视角更新，坠落物理在最终锚点基础上推进。
 */
final class Glyph {

    final Display.TextDisplay entity;
    /** 实体位置：跟随期间随组锚点重算，坠落阶段由物理推进 */
    double x;
    double z;
    /** 组内水平偏移（格，centerOffset×spacing）：跟随重锚时由此重算位置 */
    final double offset;
    /** 弹出终态朝向（面向执行者），跟随期间随组朝向刷新；坠落翻滚在此基础上叠加 */
    Quaternionf finalRot;
    double y;
    double vy;
    boolean bounced;
    boolean landed;
    boolean dead;
    boolean dropping;
    int popAge;
    int age;
    byte opacity = (byte) 255;

    Glyph(Display.TextDisplay entity, double x, double y, double z, double offset, Quaternionf finalRot) {
        this.entity = entity;
        this.x = x;
        this.y = y;
        this.z = z;
        this.offset = offset;
        this.finalRot = finalRot;
    }
}
