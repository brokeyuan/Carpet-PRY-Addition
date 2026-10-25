package me.primaryuan.carpet.handler.countdown;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * 倒计时目标选择器参数：解析走原版 {@link EntityArgument#players()}（校验与补全
 * 均为原生行为），同时捕获原始输入文本——用于判定"独立 @a"的动态全服模式
 * （其余选择器一律执行时快照一次，运行期不重复计算）。
 */
public final class CountdownSelectorType implements ArgumentType<CountdownSelectorType.SelectorInput> {

    /** 选择器输入：原始文本片段 + 解析结果 */
    public record SelectorInput(String raw, EntitySelector selector) {

        /** 独立 @a（无过滤参数）= 动态全服 */
        public boolean isAllPlayers() {
            return raw.equals("@a");
        }
    }

    private static final CountdownSelectorType INSTANCE = new CountdownSelectorType();

    private CountdownSelectorType() {}

    public static CountdownSelectorType players() {
        return INSTANCE;
    }

    public static SelectorInput get(CommandContext<CommandSourceStack> ctx, String name) {
        return ctx.getArgument(name, SelectorInput.class);
    }

    @Override
    public SelectorInput parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        EntitySelector selector = EntityArgument.players().parse(reader);
        String raw = reader.getString().substring(start, reader.getCursor());
        return new SelectorInput(raw, selector);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return EntityArgument.players().listSuggestions(context, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EntityArgument.players().getExamples();
    }
}
