package me.primaryuan.carpet.mixins.rule.whoCalledMe;

import me.primaryuan.carpet.handler.whoCalledMe.WhoCalledMeHandler;
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
 * 装饰器——内部实现（1.21.x 为 getstatic PLAIN）不构成依赖；本模组规则关闭或
 * 消息不含玩家名时透传原样文本，与原行为一致。
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Inject(method = "getChatDecorator", at = @At("HEAD"), cancellable = true)
    private void pry$wrapDecorator(CallbackInfoReturnable<ChatDecorator> cir) {
        cir.setReturnValue(WhoCalledMeHandler.wrapChatDecorator(ChatDecorator.PLAIN));
    }
}
