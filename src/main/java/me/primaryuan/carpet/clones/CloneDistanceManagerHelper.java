package me.primaryuan.carpet.clones;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * DistanceManager 预注册辅助（mixin 包外普通类，Mixin 0.8.7 禁止直接引用
 * mixins 包内非 mixin 类，否则会抛出 IllegalClassLoadError）。
 *
 * <p>把分身实体登记进出生 chunk 的玩家集合并把 lastSectionPos 预设为出生
 * section，使 placeNewPlayer 内部 ChunkMap.move 的 removePlayer/addPlayer
 * 成对净空。反射实现（accessor mixin 与 ChunkMap$DistanceManager 内部类
 * 映射漂移风险更高）。</p>
 */
public final class CloneDistanceManagerHelper {

    private CloneDistanceManagerHelper() {}

    public static void preRegister(ServerPlayer player) throws Exception {
        var level = (net.minecraft.server.level.ServerLevel) player.level();
        var chunkMap = (net.minecraft.server.level.ChunkMap) (Object) level.getChunkSource().chunkMap;
        Field dmF = net.minecraft.server.level.ChunkMap.class.getDeclaredField("distanceManager");
        dmF.setAccessible(true);
        Object dm = dmF.get(chunkMap);
        var sectionPos = net.minecraft.core.SectionPos.of(player);
        Method addPlayer = null;
        for (Method m : dm.getClass().getMethods()) {
            if (m.getName().equals("addPlayer") && m.getParameterCount() == 2) {
                addPlayer = m;
                break;
            }
        }
        if (addPlayer != null) {
            addPlayer.setAccessible(true);
            addPlayer.invoke(dm, sectionPos, player);
        }
        Method setLsp = net.minecraft.server.level.ServerPlayer.class
                .getDeclaredMethod("setLastSectionPos", net.minecraft.core.SectionPos.class);
        setLsp.setAccessible(true);
        setLsp.invoke(player, sectionPos);
    }
}
