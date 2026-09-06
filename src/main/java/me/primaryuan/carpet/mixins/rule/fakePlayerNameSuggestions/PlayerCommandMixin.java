package me.primaryuan.carpet.mixins.rule.fakePlayerNameSuggestions;

import carpet.commands.PlayerCommand;
import com.google.common.collect.Sets;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.Collection;
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
        Set<String> players = Sets.newLinkedHashSet(Arrays.asList(suggestionList.split(",")));
        players.addAll(source.getOnlinePlayerNames());
        cir.setReturnValue(players);
    }
}
