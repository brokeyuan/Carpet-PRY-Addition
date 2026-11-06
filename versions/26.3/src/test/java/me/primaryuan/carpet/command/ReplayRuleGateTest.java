package me.primaryuan.carpet.command;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** fakePlayerScript 单独开启时录像列表/详情入口仍可用；幻影播放仍只认 playerReplay。 */
class ReplayRuleGateTest
{
    @Test
    void anyReplayRuleEnablesSharedList()
    {
        boolean replay = CarpetPrimaryuanSettings.playerReplay;
        boolean script = CarpetPrimaryuanSettings.fakePlayerScript;
        try
        {
            CarpetPrimaryuanSettings.playerReplay = false;
            CarpetPrimaryuanSettings.fakePlayerScript = false;
            assertFalse(PlayerReplayCommand.anyReplayRuleEnabled());

            CarpetPrimaryuanSettings.fakePlayerScript = true;
            assertTrue(PlayerReplayCommand.anyReplayRuleEnabled(), "只开假人脚本也要能进列表");
            assertFalse(PlayerReplayCommand.phantomEnabled(), "幻影播放不得随之放开");

            CarpetPrimaryuanSettings.fakePlayerScript = false;
            CarpetPrimaryuanSettings.playerReplay = true;
            assertTrue(PlayerReplayCommand.anyReplayRuleEnabled());
            assertTrue(PlayerReplayCommand.phantomEnabled());
        }
        finally
        {
            CarpetPrimaryuanSettings.playerReplay = replay;
            CarpetPrimaryuanSettings.fakePlayerScript = script;
        }
    }
}
