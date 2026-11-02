package me.primaryuan.carpet.mixins.rule.chatPunishment;

import me.primaryuan.carpet.handler.chatPunishment.ChatPunishmentManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 公共聊天文本改写入口：只改服务端公共聊天解码回调中传给 ChatDecorator.decorate
 * 的 Component。1.21.x 的回调是 method_44900，26.x 是 lambda$handleChat$0；
 * 两者都是 ServerboundChatPacket 的异步处理体，外层 handleChat 只组装 Runnable，
 * 注入外层会零匹配。私聊由 MessageArgument 另行调用 decorate，/say、/me 等命令
 * 走 handleChatCommand，均不经过本回调，因此不会被本规则改写或取消。
 *
 * <p>1.21.x 的 method_44900 已是运行时名；26.x 未混淆使用 lambda$handleChat$0。
 * 方法名按版本分支，描述符与桥接由发布 remap 后再核实。注入失败必须启动报错，
 * 不使用 require=0 隐藏。</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin {

    //#if MC >= 260102
    //$$ @ModifyArg(
    //$$         method = "lambda$handleChat$0(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;Ljava/util/Optional;)V",
    //$$         at = @At(
    //$$                 value = "INVOKE",
    //$$                 target = "Lnet/minecraft/network/chat/ChatDecorator;decorate(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;"
    //$$         ),
    //$$         index = 1
    //$$ )
    //$$ private Component chatPunishment$transformPublicChat(ServerPlayer sender, Component message) {
    //$$     return ChatPunishmentManager.transformPublicChat(sender, message);
    //$$ }
    //#else
    @ModifyArg(
            method = "method_44900(Lnet/minecraft/network/protocol/game/ServerboundChatPacket;Ljava/util/Optional;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/ChatDecorator;decorate(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;"
            ),
            index = 1
    )
    private Component chatPunishment$transformPublicChat(ServerPlayer sender, Component message) {
        return ChatPunishmentManager.transformPublicChat(sender, message);
    }
    //#endif
}
