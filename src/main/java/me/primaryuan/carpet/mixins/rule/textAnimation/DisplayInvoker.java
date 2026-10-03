package me.primaryuan.carpet.mixins.rule.textAnimation;

import com.mojang.math.Transformation;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Display（展示实体基类）私有变换设置器访问器：米塔字幕逐字生成/弹出/坠落时
 * 驱动 transformation、插值时长/起点、billboard 与亮度。
 *
 * <p>原版从未提供程序化接口（Bukkit 的 setter 是平台包装）；成员名与签名在
 * 1.21~26.3 全分支一致（javap 实证），单份 invoker 跨版本共用，class 字面量
 * 与方法名由 loom 按版本自动重映射。</p>
 */
@Mixin(Display.class)
public interface DisplayInvoker {

    @Invoker("setTransformation")
    void pry$setTransformation(Transformation transformation);

    @Invoker("setTransformationInterpolationDuration")
    void pry$setTransformationInterpolationDuration(int ticks);

    @Invoker("setTransformationInterpolationDelay")
    void pry$setTransformationInterpolationDelay(int ticks);

    @Invoker("setPosRotInterpolationDuration")
    void pry$setPosRotInterpolationDuration(int ticks);

    @Invoker("setBillboardConstraints")
    void pry$setBillboardConstraints(Display.BillboardConstraints constraints);

    @Invoker("setBrightnessOverride")
    void pry$setBrightnessOverride(Brightness brightness);
}
