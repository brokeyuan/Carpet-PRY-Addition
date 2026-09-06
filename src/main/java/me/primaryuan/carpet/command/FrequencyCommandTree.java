package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.primaryuan.carpet.util.ScheduleMode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * /player &lt;name&gt; 下"频率控制"子命令树的通用构建器（dropall 与 sendto 共用）：
 * <pre>
 *   &lt;literal&gt;（顶层 executes = once）
 *     once | continuous | interval &lt;ticks&gt; | after &lt;ticks&gt;
 *     | perTick &lt;times&gt; | randomly &lt;min&gt; &lt;max&gt; | stop
 * </pre>
 * 模式落地方式由 {@link Handler} 实现方决定；本类只负责统一的命令树形状、
 * 参数解析（含 randomly 的 min/max 归一化与 PERTICK 换算）与模式规格装配。
 * 反馈消息由实现方按 {@code started_<keySuffix>} 拼 i18n key 发送。
 */
public final class FrequencyCommandTree {

    private FrequencyCommandTree() {}

    /** 一次频率设置请求：调度参数 + 反馈 key 后缀（started_&lt;keySuffix&gt;）+ 反馈展示参数 */
    public record ModeSpec(ScheduleMode mode, int interval, int min, int max,
                           String keySuffix, Object... displayArgs) {}

    /** 频率命令的业务处理（消息发送由实现方负责，本类不做任何反馈） */
    public interface Handler {
        /** once / 顶层 executes */
        int once(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;

        /** 频率模式落地；返回 0 表示失败（失败消息由实现方发送） */
        int startMode(CommandContext<CommandSourceStack> ctx, ModeSpec spec) throws CommandSyntaxException;

        /** stop 子命令 */
        int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(String literal, Handler handler) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(literal);
        // 顶层无参数等价 once
        root.executes(handler::once);
        root.then(Commands.literal("once").executes(handler::once));
        root.then(Commands.literal("continuous").executes(ctx -> handler.startMode(ctx,
                new ModeSpec(ScheduleMode.CONTINUOUS, 1, 0, 0, "continuous"))));
        root.then(Commands.literal("interval")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                            return handler.startMode(ctx,
                                    new ModeSpec(ScheduleMode.INTERVAL, ticks, 0, 0, "interval", ticks));
                        })));
        root.then(Commands.literal("after")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                        .executes(ctx -> {
                            int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                            return handler.startMode(ctx,
                                    new ModeSpec(ScheduleMode.AFTER, ticks, 0, 0, "after", ticks));
                        })));
        root.then(Commands.literal("perTick")
                .then(Commands.argument("times", IntegerArgumentType.integer(1, 20))
                        .executes(ctx -> {
                            int times = IntegerArgumentType.getInteger(ctx, "times");
                            return handler.startMode(ctx, new ModeSpec(ScheduleMode.PERTICK,
                                    ScheduleMode.perTickInterval(times), 0, 0, "perTick", times));
                        })));
        root.then(Commands.literal("randomly")
                .then(Commands.argument("min", IntegerArgumentType.integer(1))
                        .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int a = IntegerArgumentType.getInteger(ctx, "min");
                                    int b = IntegerArgumentType.getInteger(ctx, "max");
                                    int min = Math.min(a, b);
                                    int max = Math.max(a, b);
                                    // RANDOMLY 首次延迟取 min（保证最小延迟）
                                    return handler.startMode(ctx,
                                            new ModeSpec(ScheduleMode.RANDOMLY, min, min, max, "randomly", min, max));
                                }))));
        root.then(Commands.literal("stop").executes(handler::stop));
        return root;
    }

    /** 拼装反馈参数：头参数（假人名）+ 模式展示参数 + 可选尾参 */
    public static Object[] concat(Object first, Object[] middle, Object... last) {
        Object[] args = new Object[1 + middle.length + last.length];
        args[0] = first;
        System.arraycopy(middle, 0, args, 1, middle.length);
        System.arraycopy(last, 0, args, 1 + middle.length, last.length);
        return args;
    }
}
