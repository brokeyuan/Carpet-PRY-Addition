package me.primaryuan.carpet.mixins.rule.whoCalledMe;

import me.primaryuan.carpet.handler.chatPunishment.ChatPunishmentManager;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 聊天装饰器接管：玩家聊天显示文本经 ChatDecorator.decorate 产出（原版滤嘴同款
 * 官方扩展点，调用点为 ServerGamePacketListenerImpl.handleChat；三版本
 * getChatDecorator 同名同签名 javap 核实）。@Inject HEAD 直接短路返回本模组
 * 组合装饰器——内部实现（1.21.x 为 getstatic PLAIN）不构成依赖；装饰链为
 * chatPunishment 文本转换 → whoCalledMe 名字高亮（见 ChatPunishmentManager.
 * wrapChatDecorator），两规则关闭或消息不含玩家名时逐层透传，与原行为一致。
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "getChatDecorator", at = @At("HEAD"), cancellable = true)
    private void pry$wrapDecorator(CallbackInfoReturnable<ChatDecorator> cir) {
        cir.setReturnValue(ChatPunishmentManager.wrapChatDecorator(ChatDecorator.PLAIN));
    }
}
