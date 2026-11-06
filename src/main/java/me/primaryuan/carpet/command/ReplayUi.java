package me.primaryuan.carpet.command;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * 聊天面板 UI 构造：按钮/提示文本的点击与悬停事件（javap 核实两版差异）。
 * 1.21 线：ClickEvent(Action, String) + HoverEvent(Action, Component)；
 * 26.3：ClickEvent.RunCommand/SuggestCommand record + HoverEvent.ShowText record。
 * 面板文案与既有命令反馈同风格（服务器字面量）。
 */
public final class ReplayUi
{
    private ReplayUi() {}

    /** 可点击按钮：[标签] */
    public static MutableComponent button(String label, String command, String hoverKey, ChatFormatting color)
    {
        MutableComponent c = Component.literal("[" + label + "]");
        c.withStyle(style -> style
                .withColor(color)
                .withClickEvent(runCommand(command))
                .withHoverEvent(showText(hover(label, hoverKey))));
        return c;
    }

    /** 可点击按钮：点击后把命令填入聊天输入框（供补齐参数后回车执行） */
    public static MutableComponent suggest(String label, String command, String hoverKey, ChatFormatting color)
    {
        MutableComponent c = Component.literal("[" + label + "]");
        c.withStyle(style -> style
                .withColor(color)
                .withClickEvent(suggestCommand(command))
                .withHoverEvent(showText(hover(label, hoverKey))));
        return c;
    }

    public static MutableComponent text(String s, ChatFormatting color)
    {
        return Component.literal(s).withStyle(color);
    }

    /** 带悬停提示的文本 */
    public static MutableComponent text(String s, ChatFormatting color, String hover)
    {
        return Component.literal(s).withStyle(color)
                .withStyle(style -> style.withHoverEvent(showText(
                        Component.literal(hover).withStyle(ChatFormatting.GRAY))));
    }

    public static MutableComponent gray(String s)
    {
        return Component.literal(s).withStyle(ChatFormatting.GRAY);
    }

    /** 组合一行：parts 逐个 append */
    public static MutableComponent line(Object... parts)
    {
        MutableComponent root = Component.literal("");
        for (Object p : parts)
        {
            if (p instanceof String s)
            {
                root.append(Component.literal(s));
            }
            else if (p instanceof MutableComponent m)
            {
                root.append(m);
            }
        }
        return root;
    }

    /** 面板分隔行 */
    public static MutableComponent sep()
    {
        return Component.literal("─────────────").withStyle(ChatFormatting.DARK_GRAY);
    }

    private static Component hover(String label, String hover)
    {
        return hover == null || hover.isEmpty() ? Component.literal(label)
                : Component.literal(label + "\n" + hover).withStyle(ChatFormatting.GRAY);
    }

    // 1.21.5 起 ClickEvent/HoverEvent 改接口+record 子类；1.21~1.21.4 为旧构造（javap 实证）
    static ClickEvent runCommand(String command)
    {
        //#if MC >= 12105
        return new ClickEvent.RunCommand(command);
        //#else
        //$$ return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        //#endif
    }

    private static ClickEvent suggestCommand(String command)
    {
        //#if MC >= 12105
        return new ClickEvent.SuggestCommand(command);
        //#else
        //$$ return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
        //#endif
    }

    static HoverEvent showText(Component content)
    {
        //#if MC >= 12105
        return new HoverEvent.ShowText(content);
        //#else
        //$$ return new HoverEvent(HoverEvent.Action.SHOW_TEXT, content);
        //#endif
    }
}
