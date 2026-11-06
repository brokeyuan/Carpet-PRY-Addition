package me.primaryuan.carpet.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.handler.playerHead.HeadData;
import me.primaryuan.carpet.handler.playerHead.HeadItems;
import me.primaryuan.carpet.handler.playerHead.HeadSkinResolver;
import me.primaryuan.carpet.i18n.ServerI18n;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * /head：管理员头颅入口。
 *
 * <p>根命令在任一相关规则开启且来源为管理员时可见；各子命令分别受对应规则
 * 控制。接收玩家与纪念档案中的“颁发对象”是两个独立概念。</p>
 */
public final class HeadCommand {

    enum Validation { OK, BAD_SOURCE, BAD_NAME, BAD_EVENT, BAD_DATE, BAD_RECIPIENT, BAD_NOTE }

    private HeadCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("head")
                        .requires(HeadCommand::rootVisible)
                        .then(universal())
                        .then(memorial())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> universal() {
        return Commands.literal("universal")
                .requires(HeadCommand::universalVisible)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> giveUniversal(ctx, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> giveUniversal(ctx,
                                        IntegerArgumentType.getInteger(ctx, "count")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> memorial() {
        var note = Commands.argument("note", StringArgumentType.greedyString())
                .executes(ctx -> createMemorial(ctx, StringArgumentType.getString(ctx, "note")));
        var recipient = Commands.argument("recipient", StringArgumentType.string())
                .executes(ctx -> createMemorial(ctx, null))
                .then(note);
        var date = Commands.argument("date", StringArgumentType.word()).then(recipient);
        var event = Commands.argument("event", StringArgumentType.string()).then(date);
        var name = Commands.argument("name", StringArgumentType.string()).then(event);
        var source = Commands.argument("source", StringArgumentType.word()).then(name);
        return Commands.literal("fixed")
                .requires(HeadCommand::memorialVisible)
                .then(Commands.argument("target", EntityArgument.player()).then(source));
    }

    static boolean rootVisible(CommandSourceStack source) {
        return (CarpetPrimaryuanSettings.universalHead || CarpetPrimaryuanSettings.memorialHead)
                && CommandSupport.isAdmin(source);
    }

    static boolean universalVisible(CommandSourceStack source) {
        return CarpetPrimaryuanSettings.universalHead && CommandSupport.isAdmin(source);
    }

    static boolean memorialVisible(CommandSourceStack source) {
        return CarpetPrimaryuanSettings.memorialHead && CommandSupport.isAdmin(source);
    }

    private static int giveUniversal(CommandContext<CommandSourceStack> ctx, int count) {
        CommandSourceStack source = ctx.getSource();
        if (!CarpetPrimaryuanSettings.universalHead || !CommandSupport.isAdmin(source)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.no_permission"));
            return 0;
        }
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "target");
        } catch (CommandSyntaxException e) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.target_not_found"));
            return 0;
        }
        // 首版不做部分发放或掉地：空间不足直接拒绝。
        Inventory inventory = target.getInventory();
        ItemStack stack = HeadItems.universal(count);
        if (inventory.getFreeSlot() == -1 && inventory.getSlotWithRemainingSpace(stack) == -1) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.no_space", CommandSupport.profileName(target)));
            return 0;
        }
        if (!inventory.add(stack)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.no_space", CommandSupport.profileName(target)));
            return 0;
        }
        target.containerMenu.broadcastChanges();
        String name = CommandSupport.profileName(target);
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.head.universal.given", name, count), false);
        return count;
    }

    private static int createMemorial(CommandContext<CommandSourceStack> ctx, String note) {
        CommandSourceStack source = ctx.getSource();
        if (!CarpetPrimaryuanSettings.memorialHead || !CommandSupport.isAdmin(source)) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.no_permission"));
            return 0;
        }
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "target");
        } catch (CommandSyntaxException e) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.target_not_found"));
            return 0;
        }
        String skinSource = StringArgumentType.getString(ctx, "source").trim();
        String formalName = StringArgumentType.getString(ctx, "name").trim();
        String eventName = StringArgumentType.getString(ctx, "event").trim();
        String eventDate = StringArgumentType.getString(ctx, "date").trim();
        String recipient = StringArgumentType.getString(ctx, "recipient").trim();
        // greedyString 保留原文引号；整体被引号包住时去掉一层，与 string() 参数一致。
        String archiveNote = unquote(note == null ? "" : note.trim());

        Validation validation = validateMemorial(skinSource, formalName, eventName, eventDate, recipient, archiveNote);
        if (validation != Validation.OK) {
            source.sendFailure(ServerI18n.tr(validationKey(validation), eventDate));
            return 0;
        }
        if (target.getInventory().getFreeSlot() == -1) {
            source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.no_space",
                    CommandSupport.profileName(target)));
            return 0;
        }

        MinecraftServer server = source.getServer();
        if (server == null) return 0;
        UUID targetId = target.getUUID();
        source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.head.memorial.creating", skinSource), false);
        HeadSkinResolver.resolve(server, skinSource,
                skin -> {
                    // 异步成功后再核对规则、权限、目标在线状态与背包空间。
                    if (!CarpetPrimaryuanSettings.memorialHead) {
                        source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.memorial.rule_off"));
                        return;
                    }
                    if (!CommandSupport.isAdmin(source)) {
                        source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.no_permission"));
                        return;
                    }
                    ServerPlayer online = server.getPlayerList().getPlayer(targetId);
                    if (online == null || online != target || online.isRemoved() || !online.isAlive()) {
                        source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.memorial.target_offline"));
                        return;
                    }
                    HeadData data = HeadData.memorial(formalName, eventName, eventDate, recipient, archiveNote);
                    data.applySkin(skin);
                    ItemStack stack = HeadItems.memorial(data);
                    if (online.getInventory().getFreeSlot() == -1 || !online.getInventory().add(stack)) {
                        source.sendFailure(ServerI18n.tr("carpetprimaryuan.command.head.no_space",
                                CommandSupport.profileName(online)));
                        return;
                    }
                    online.containerMenu.broadcastChanges();
                    String name = CommandSupport.profileName(online);
                    source.sendSuccess(() -> ServerI18n.tr("carpetprimaryuan.command.head.memorial.given",
                            name, formalName), false);
                },
                problem -> source.sendFailure(ServerI18n.tr(
                        "carpetprimaryuan.command.head.memorial.lookup_failed", skinSource)));
        return 1;
    }

    static Validation validateMemorial(String skinSource, String formalName, String eventName,
                                       String eventDate, String recipient, String note) {
        if (skinSource.isEmpty() || skinSource.length() > 32) return Validation.BAD_SOURCE;
        if (formalName.isEmpty() || formalName.length() > 48) return Validation.BAD_NAME;
        if (eventName.isEmpty() || eventName.length() > 32) return Validation.BAD_EVENT;
        if (!validDate(eventDate)) return Validation.BAD_DATE;
        if (recipient.isEmpty() || recipient.length() > 32) return Validation.BAD_RECIPIENT;
        if (note.length() > 100) return Validation.BAD_NOTE;
        return Validation.OK;
    }

    /** 只接受管理员明确输入的 YYYY-MM-DD，且必须是真实存在的日期。 */
    static boolean validDate(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** greedyString 不做引号解析：整体被双引号包住时去掉一层并还原转义。 */
    static String unquote(String value) {
        if (value.length() < 2 || !value.startsWith("\"") || !value.endsWith("\"")) return value;
        String inner = value.substring(1, value.length() - 1);
        return inner.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String validationKey(Validation validation) {
        return switch (validation) {
            case BAD_SOURCE -> "carpetprimaryuan.command.head.memorial.bad_source";
            case BAD_NAME -> "carpetprimaryuan.command.head.memorial.bad_name";
            case BAD_EVENT -> "carpetprimaryuan.command.head.memorial.bad_event";
            case BAD_DATE -> "carpetprimaryuan.command.head.memorial.bad_date";
            case BAD_RECIPIENT -> "carpetprimaryuan.command.head.memorial.bad_recipient";
            case BAD_NOTE -> "carpetprimaryuan.command.head.memorial.bad_note";
            default -> "carpetprimaryuan.command.head.no_permission";
        };
    }
}
