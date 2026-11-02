package me.primaryuan.carpet.mixins.rule.fakePlayerClones;

import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.Entry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * ClientboundPlayerInfoUpdatePacket.entries 字段写访问器：
 * 显示 UUID 改写在构造点完成后回填（entries 为 final List）。
 */
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public interface ClientboundPlayerInfoUpdatePacketAccessor {

    @Accessor("entries")
    void pry$setEntries(List<Entry> entries);
}
