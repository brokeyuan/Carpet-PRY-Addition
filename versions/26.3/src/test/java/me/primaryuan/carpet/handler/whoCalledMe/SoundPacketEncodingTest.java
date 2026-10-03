package me.primaryuan.carpet.handler.whoCalledMe;

import io.netty.buffer.Unpooled;
import me.primaryuan.carpet.util.TestRegistries;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.RegistryLayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 提示音无声的决定性实验：ClientboundSoundPacket 的 STREAM_CODEC 是
 * RegistryFriendlyByteBuf 版，验证 Holder.direct(SoundEvent) 是否可正常编码。
 * （2026-10-03 生产实证：title 正常弹出、音效包无声——嫌疑为 direct holder 编码失败）
 */
public class SoundPacketEncodingTest {

    @BeforeAll
    static void bootstrap() {
        TestRegistries.bootstrap();
    }

    private static RegistryFriendlyByteBuf buf() {
        RegistryAccess.Frozen access = RegistryLayer.createRegistryAccess().compositeAccess();
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
    }

    @Test
    void directHolderEncodes() {
        Holder<SoundEvent> direct = Holder.direct(SoundEvents.AMETHYST_BLOCK_CHIME);
        ClientboundSoundPacket packet = new ClientboundSoundPacket(
                direct, SoundSource.PLAYERS, 0, 64, 0, 1.0f, 2.0f, 123L);
        RegistryFriendlyByteBuf buf = buf();
        ClientboundSoundPacket.STREAM_CODEC.encode(buf, packet);
        assertTrue(buf.readableBytes() > 0, "direct holder 应可编码");
    }

    @Test
    void registryHolderEncodes() {
        Optional<Holder.Reference<SoundEvent>> holder =
                BuiltInRegistries.SOUND_EVENT.getResourceKey(SoundEvents.AMETHYST_BLOCK_CHIME)
                        .flatMap(BuiltInRegistries.SOUND_EVENT::get);
        assertTrue(holder.isPresent(), "AMETHYST_BLOCK_CHIME 应能取到 Holder.Reference");
        ClientboundSoundPacket packet = new ClientboundSoundPacket(
                holder.get(), SoundSource.PLAYERS, 0, 64, 0, 1.0f, 2.0f, 123L);
        RegistryFriendlyByteBuf buf = buf();
        ClientboundSoundPacket.STREAM_CODEC.encode(buf, packet);
        assertTrue(buf.readableBytes() > 0, "registry holder 应可编码");
    }
}
