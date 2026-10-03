package me.primaryuan.carpet.handler.textAnimation;

import net.minecraft.world.entity.Display;
import org.joml.Quaternionf;

/**
 * 一个已生成字形：对应一个 text_display 实体与其坠落物理状态。
 * 坐标为实体自身位置（弹出偏移走 transformation，不走实体坐标）。
 */
final class Glyph {

    final Display.TextDisplay entity;
    final double x;
    final double z;
    /** 弹出终态朝向（面向执行者），坠落翻滚在此基础上叠加 */
    final Quaternionf finalRot;
    double y;
    double vy;
    boolean bounced;
    boolean landed;
    boolean dead;
    boolean dropping;
    int popAge;
    int age;
    byte opacity = (byte) 255;

    Glyph(Display.TextDisplay entity, double x, double y, double z, Quaternionf finalRot) {
        this.entity = entity;
        this.x = x;
        this.y = y;
        this.z = z;
        this.finalRot = finalRot;
    }
}
