package me.primaryuan.carpet.handler.fakegames;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * 假游戏聊天面板的按钮与文本构造。全部使用原版聊天组件，按钮绑定具体场次 ID。
 * 1.21~1.21.4 与 1.21.5+ 的 ClickEvent/HoverEvent 构造差异在这里统一处理。
 */
public final class FakeGameUi {

    private FakeGameUi() {}

    public static MutableComponent button(String label, String command, String hover) {
        return button(label, command, hover, ChatFormatting.GREEN);
    }

    public static MutableComponent button(String label, String command, String hover,
                                          ChatFormatting color) {
        MutableComponent component = Component.literal("[" + label + "]");
        component.withStyle(style -> style
                .withColor(color)
                .withClickEvent(runCommand(command))
                .withHoverEvent(showText(hover == null || hover.isEmpty()
                        ? Component.literal(label).withStyle(ChatFormatting.GRAY)
                        : Component.literal(hover).withStyle(ChatFormatting.GRAY))));
        return component;
    }

    public static MutableComponent suggest(String label, String command, String hover,
                                           ChatFormatting color) {
        MutableComponent component = Component.literal("[" + label + "]");
        component.withStyle(style -> style
                .withColor(color)
                .withClickEvent(suggestCommand(command))
                .withHoverEvent(showText(hover == null || hover.isEmpty()
                        ? Component.literal(label).withStyle(ChatFormatting.GRAY)
                        : Component.literal(hover).withStyle(ChatFormatting.GRAY))));
        return component;
    }

    public static MutableComponent text(String value, ChatFormatting color) {
        return Component.literal(value).withStyle(color);
    }

    public static MutableComponent line(Object... parts) {
        MutableComponent root = Component.literal("");
        for (Object part : parts) {
            if (part instanceof Component component) {
                root.append(component);
            } else if (part != null) {
                root.append(Component.literal(part.toString()));
            }
        }
        return root;
    }

    public static MutableComponent separator() {
        return Component.literal("─────────────").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static ClickEvent runCommand(String command) {
        //#if MC >= 12105
        return new ClickEvent.RunCommand(command);
        //#else
        //$$ return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        //#endif
    }

    private static ClickEvent suggestCommand(String command) {
        //#if MC >= 12105
        return new ClickEvent.SuggestCommand(command);
        //#else
        //$$ return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
        //#endif
    }

    private static HoverEvent showText(Component content) {
        //#if MC >= 12105
        return new HoverEvent.ShowText(content);
        //#else
        //$$ return new HoverEvent(HoverEvent.Action.SHOW_TEXT, content);
        //#endif
    }
}
