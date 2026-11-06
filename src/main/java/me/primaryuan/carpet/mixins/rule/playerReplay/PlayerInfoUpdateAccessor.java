package me.primaryuan.carpet.mixins.rule.playerReplay;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 幻影 PlayerInfo 包字段写入：包类无公开 Entry 构造路径（javap 核实），
 * 用默认构造 + 反射填充 entries/actions 字段。
 */
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface PlayerInfoUpdateAccessor
{
    @Mutable
    @Accessor("entries")
    void pry$setEntries(List<ClientboundPlayerInfoUpdatePacket.Entry> entries);

    @Mutable
    @Accessor("actions")
    void pry$setActions(java.util.EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions);
}
