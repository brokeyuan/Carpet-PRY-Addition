package me.primaryuan.carpet.mixins.rule.fakePlayerNameSuggestions;

import carpet.commands.PlayerCommand;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(PlayerCommand.class)
public class PlayerCommandMixin {
    @Inject(
            method = "getPlayerSuggestions",
            at = @At("HEAD"),
            remap = false,
            cancellable = true
    )
    private static void overwriteSuggestsPlayerList(CommandSourceStack source, CallbackInfoReturnable<Collection<String>> cir) {
        String suggestionList = CarpetPrimaryuanSettings.fakePlayerNameSuggestions;
        // 规则关闭（空值）：不覆盖，保持 Carpet 原版建议
        if (suggestionList == null || suggestionList.isBlank()) {
            return;
        }
        // 逐项 trim 并丢弃空段：规则值里 "Steve, Alex"（逗号后带空格）若直接
        // split 会产生带前导空格的补全项，选中即因 word() 参数类型解析失败
        Set<String> players = new LinkedHashSet<>();
        for (String raw : suggestionList.split(",")) {
            String name = raw.trim();
            if (!name.isEmpty()) {
                players.add(name);
            }
        }
        players.addAll(source.getOnlinePlayerNames());
        cir.setReturnValue(players);
    }
}
