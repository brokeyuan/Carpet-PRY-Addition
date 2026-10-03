package me.primaryuan.carpet.mixins.rule.textAnimation;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Display.TextDisplay（文本展示实体）私有文本设置器访问器：
 * 设置逐字文本、透明度（渐隐动画）、背景色与样式标志（阴影）。
 * 成员名跨版本一致（javap 实证），loom 自动重映射。
 */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayInvoker {

    @Invoker("setText")
    void pry$setText(Component text);

    @Invoker("setTextOpacity")
    void pry$setTextOpacity(byte opacity);

    @Invoker("setBackgroundColor")
    void pry$setBackgroundColor(int argb);

    @Invoker("setFlags")
    void pry$setFlags(byte flags);
}
