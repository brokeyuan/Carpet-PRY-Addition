package me.primaryuan.carpet.mixins.rule.fakePlayerSkin;

import carpet.commands.PlayerCommand;
import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.google.common.collect.Iterables;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import me.primaryuan.carpet.util.FakePlayerSkinManager;
import me.primaryuan.carpet.util.ServerTickScheduler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * PlayerCommandSkinMixin - 皮肤来源入队与兜底（全版本唯一实现）。
 *
 * <p>spawn HEAD（TIS Addition 的 rejoin @Shadow 复用本方法，同样生效）按假人名入队
 * 皮肤来源。summon 模式来源回退链：召唤者在线 profile 有 textures → 内存直拷快照
 * （零网络）；无 textures（离线服真人 profile 本就无纹理，皮肤靠 skinrestorer join
 * 换上）→ 入队空快照，兜底按召唤者名走 provider 解析（旧路径，离线服可用）；控制台/
 * 命令方块执行（无召唤者）→ 回退 fakePlayerSkinSet 统一皮肤。所有回退均留 INFO 日志，
 * 无静默放弃。same_skin 模式使用 fakePlayerSkinSet（skinrestorer provider 后台预热）。</p>
 *
 * <p>实际注入由 EntityPlayerMPFakeSkinMixin 在假人构造器 RETURN 完成（经 accessor
 * 整体替换 gameProfile 字段，出生包直接带目标皮肤）。构造器未注入（快照缺失 / 预热
 * 未就绪 / 注入点漂移）时 TAIL 安装兜底循环：等假人上线后走 SkinRestorer
 * setSkinAsync(save=false) 后置换肤（观感同旧行为闪一次），30 秒未上线（生成失败）
 * 则放弃并 WARN。save=false 不写入 SkinRestorer 持久存储——假人 UUID 与同名真人
 * 相同时落库会导致真人上线被换肤。</p>
 */
@Mixin(PlayerCommand.class)
public class PlayerCommandSkinMixin {

    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    /** 兜底等待假人上线的超时（tick）：30 秒——慢 profile 拉取（网络不佳）下构造器回调可能晚于 5 秒 */
    private static final int FALLBACK_TIMEOUT_TICKS = 600;

    @Inject(
            method = "spawn",
            at = @At("HEAD"),
            remap = false
    )
    private static void beforeSpawn(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
        String mode = CarpetPrimaryuanSettings.fakePlayerSkinMode;
        if ("default".equals(mode)) {
            return;
        }
        try {
            String fakeName = StringArgumentType.getString(context, "player");
            if ("summon".equals(mode)) {
                ServerPlayer summoner = null;
                try {
                    summoner = context.getSource().getPlayerOrException();
                } catch (Exception ignored) {
                    // 控制台/命令方块执行：无召唤者
                }
                if (summoner == null) {
                    String skinSet = CarpetPrimaryuanSettings.fakePlayerSkinSet;
                    if (skinSet == null || skinSet.isEmpty()) {
                        LOGGER.warn("[pry] fakePlayerSkin: summon 模式由控制台执行且 fakePlayerSkinSet 为空，"
                                + "假人 {} 无皮肤来源，保持默认皮肤", fakeName);
                        return;
                    }
                    LOGGER.info("[pry] fakePlayerSkin: summon 模式由控制台执行，假人 {} 回退统一皮肤 {}",
                            fakeName, skinSet);
                    FakePlayerSkinManager.enqueue(fakeName, null, skinSet, skinSet);
                    return;
                }
                //#if MC >= 12110
                Property skin = Iterables.getFirst(summoner.getGameProfile().properties().get("textures"), null);
                //#else
                //$$ Property skin = Iterables.getFirst(summoner.getGameProfile().getProperties().get("textures"), null);
                //#endif
                String summonerName;
                //#if MC >= 12110
                summonerName = summoner.getGameProfile().name();
                //#else
                //$$ summonerName = summoner.getGameProfile().getName();
                //#endif
                if (skin == null) {
                    // 离线服等场景召唤者 profile 无纹理：入队空快照，兜底按召唤者名走 provider（旧路径，离线服可用）
                    LOGGER.info("[pry] fakePlayerSkin: 召唤者 {} profile 无皮肤纹理（离线服常见），"
                            + "假人 {} 将由兜底按名解析皮肤", summonerName, fakeName);
                    FakePlayerSkinManager.enqueue(fakeName, null, null, summonerName);
                    return;
                }
                FakePlayerSkinManager.enqueue(fakeName, skin, null, summonerName);
            } else if ("same_skin".equals(mode)) {
                String skinName = CarpetPrimaryuanSettings.fakePlayerSkinSet;
                if (skinName == null || skinName.isEmpty()) {
                    LOGGER.warn("[pry] fakePlayerSkin: same_skin 模式但 fakePlayerSkinSet 为空，"
                            + "假人 {} 无皮肤来源，保持默认皮肤", fakeName);
                    return;
                }
                FakePlayerSkinManager.enqueue(fakeName, null, skinName, skinName);
            }
        } catch (Exception e) {
            LOGGER.error("[pry] fakePlayerSkin: 入队假人皮肤来源失败", e);
        }
    }

    @Inject(
            method = "spawn",
            at = @At("TAIL"),
            remap = false
    )
    private static void afterSpawn(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
        String mode = CarpetPrimaryuanSettings.fakePlayerSkinMode;
        if ("default".equals(mode)) {
            return;
        }
        try {
            String fakeName = StringArgumentType.getString(context, "player");
            FakePlayerSkinManager.PendingSkin entry = FakePlayerSkinManager.peek(fakeName);
            if (entry == null) {
                return;
            }
            // 构造器注入发生在 profile 异步拉取完成后（晚于本 TAIL）：兜底循环等构造器给出
            // 结果——注入成功则清理；未注入（快照缺失/预热未就绪/注入点漂移）才后置换肤
            final String name = fakeName;
            final FakePlayerSkinManager.PendingSkin entryRef = entry;
            final int[] waited = {0};
            ServerTickScheduler.register(server -> {
                if (entryRef.injected) {
                    FakePlayerSkinManager.remove(name);
                    return false;
                }
                ServerPlayer spawned = server.getPlayerList().getPlayerByName(name);
                if (spawned instanceof EntityPlayerMPFake fake) {
                    LOGGER.info("[pry] fakePlayerSkin: 假人 {} 出生前未注入，兜底后置换肤 -> {}",
                            name, entryRef.fallbackName);
                    FakePlayerSkinManager.applyPostSpawn(server, fake, entryRef.fallbackName);
                    FakePlayerSkinManager.remove(name);
                    return false;
                }
                // spawn 失败（名字被占用等）时假人不会上线，超时放弃
                if (++waited[0] >= FALLBACK_TIMEOUT_TICKS) {
                    LOGGER.warn("[pry] fakePlayerSkin: 假人 {} 30 秒内未上线，放弃皮肤兜底（生成可能失败）", name);
                    FakePlayerSkinManager.remove(name);
                    return false;
                }
                return true;
            });
        } catch (Exception e) {
            LOGGER.error("[pry] fakePlayerSkin: 安装皮肤兜底循环失败", e);
        }
    }
}
