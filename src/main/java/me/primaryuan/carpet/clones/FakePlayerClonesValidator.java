package me.primaryuan.carpet.clones;

import carpet.api.settings.CarpetRule;
import carpet.api.settings.Validator;
import net.minecraft.commands.CommandSourceStack;

/**
 * fakePlayerClones 关闭保护：仍有在线/退出中成员时拒绝关闭。
 *
 * <p>规则关掉后命令与新建入口都会消失，但已存在的共享库存仍需继续按
 * 组语义运行；先让用户整组退出再关闭，避免保护逻辑被半途停用。</p>
 */
public final class FakePlayerClonesValidator extends Validator<Boolean> {

    @Override
    public Boolean validate(CommandSourceStack source, CarpetRule<Boolean> rule,
                            Boolean newValue, String userInput) {
        if (Boolean.FALSE.equals(newValue) && CloneGroupRegistry.hasLiveMembers()) {
            return null;
        }
        return newValue;
    }

    @Override
    public String description() {
        return " (group members still online; run /fakeclone kill <name> first)";
    }
}
