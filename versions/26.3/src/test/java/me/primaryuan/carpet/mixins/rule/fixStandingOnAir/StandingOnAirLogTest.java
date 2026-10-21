package me.primaryuan.carpet.mixins.rule.fixStandingOnAir;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.primaryuan.carpet.CarpetPrimaryuanSettings;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StandingOnAirLogTest {
    @Test
    void ruleToggleSuppressesSupportLogAndRestoresOriginalArguments() throws Exception {
        // 直接执行注入处理器；启动检查另行验证其确实挂到原版日志调用。
        Method handler = assertDoesNotThrow(() -> ServerGamePacketListenerMixin.class.getDeclaredMethod(
                "fixStandingOnAir$suppressLog", Logger.class, String.class, Object.class, Operation.class));
        handler.setAccessible(true);
        List<String> messages = new ArrayList<>();
        Logger logger = (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class[]{Logger.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("info")) messages.add(args[0] + ":" + args[1]);
                    return null;
                });
        Operation<Void> original = args -> {
            ((Logger) args[0]).info((String) args[1], args[2]);
            return null;
        };
        ServerGamePacketListenerMixin mixin = new ServerGamePacketListenerMixin() {
            @Override
            public void fixStandingOnAir$invokeForceSend() { }
        };
        String message = "Player {} standing on air - force-sending blocks below";
        boolean previous = CarpetPrimaryuanSettings.fixStandingOnAir;
        try {
            CarpetPrimaryuanSettings.fixStandingOnAir = false;
            handler.invoke(mixin, logger, message, "Steve", original);
            assertEquals(List.of(message + ":Steve"), messages);
            CarpetPrimaryuanSettings.fixStandingOnAir = true;
            handler.invoke(mixin, logger, message, "Alex", original);
            assertEquals(1, messages.size());
            CarpetPrimaryuanSettings.fixStandingOnAir = false;
            handler.invoke(mixin, logger, message, "Alex", original);
            assertEquals(List.of(message + ":Steve", message + ":Alex"), messages);
        } finally {
            CarpetPrimaryuanSettings.fixStandingOnAir = previous;
        }
    }
}
