package me.primaryuan.carpet.handler.fakegames;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * 小游戏表现层：虚拟图腾淘汰动画、Title/ActionBar、定向音效与可点击加入链接。
 * 全部使用原版包与原版资源，不引入客户端组件。
 */
public final class FakeGameEffects {
    private FakeGameEffects() {}

    /**
     * 虚拟不死图腾淘汰动画：向追踪玩家与本人广播实体事件 35。
     * 客户端在 ClientPacketListener.handleEntityEvent 的 35 分支播放
     * TOTEM_OF_UNDYING 跟踪粒子、TOTEM_USE 音效，并仅对本地玩家补 displayItemActivation
     * 手部动画（1.21.11 与 26.3 javap 核实，与原版 checkTotemDeathProtection
     * 的广播路径一致）。纯客户端表现，不消耗图腾、不触发任何服务端逻辑。
     */
    public static void playTotemAnimation(ServerPlayer player) {
        player.level().broadcastEntityEvent(player, (byte) 35);
    }

    /** 中央大字 + 副标题（原版契约：副标题必须随 Title 一并下发才显示） */
    public static void title(ServerPlayer player, Component title, Component subtitle,
                             int fadeIn, int stay, int fadeOut) {
        if (player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    public static void actionbar(ServerPlayer player, Component message) {
        if (player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundSetActionBarTextPacket(message));
    }

    /** 定向音效：只发给目标玩家本人（NOTE_BLOCK_* 系常量各版本均为 Holder.Reference） */
    public static void sound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        if (player.connection == null) {
            return;
        }
        player.connection.send(new ClientboundSoundPacket(
                sound, SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(),
                volume, pitch, player.getRandom().nextLong()));
    }

    /** 可点击命令组件（跨版本分界：1.21.5 起 ClickEvent 改为 record 式） */
    public static MutableComponent clickCommand(String command, MutableComponent label) {
        //#if MC < 12105
        //$$ return label.withStyle(style -> style.withClickEvent(
        //$$         new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
        //#else
        return label.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command)));
        //#endif
    }
}
