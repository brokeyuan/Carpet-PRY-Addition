package me.primaryuan.carpet.replay.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 自动假人名必须避开脚本占用与在线玩家（例如正在操作设置页的假人本身）。 */
class ScriptNamingTest
{
    @Test
    void autoNameSkipsOnlinePlayer()
    {
        assertEquals("ReplayBot2",
                ScriptScheduler.nextFreeBotName(name -> name.equals("ReplayBot")));
    }

    @Test
    void autoNameSkipsScriptOwnedName()
    {
        CarpetScriptSession session = new CarpetScriptSession("s", "ReplayBot", 1,
                ScriptTestSupport.data(1), new ScriptTestSupport.StubDriver(),
                CarpetScriptSession.RunMode.ONCE, 1, true, false);
        ScriptScheduler.bindSession(session);
        try
        {
            assertEquals("ReplayBot2", ScriptScheduler.nextFreeBotName());
        }
        finally
        {
            ScriptScheduler.shutdownAll();
        }
    }
}
