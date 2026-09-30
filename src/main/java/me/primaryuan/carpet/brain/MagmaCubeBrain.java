package me.primaryuan.carpet.brain;

import net.minecraft.server.level.ServerPlayer;

/**
 * 岩浆怪模式脑：与史莱姆行为同源（原版 MagmaCube 继承 Slime 的全部 Goal
 * 装配，仅属性差异——生命/攻击随体型、火焰免疫属身体），复用装配仅换模式名。
 */
public class MagmaCubeBrain extends SlimeBrain {

    public MagmaCubeBrain(ServerPlayer player) {
        super(player, "magmacube");
    }
}
