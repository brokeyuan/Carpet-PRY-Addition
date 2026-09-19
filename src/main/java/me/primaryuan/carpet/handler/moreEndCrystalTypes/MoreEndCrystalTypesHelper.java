package me.primaryuan.carpet.handler.moreEndCrystalTypes;

import me.primaryuan.carpet.CarpetPrimaryuanSettings;

/**
 * moreEndCrystalTypes 规则的模式解析。
 *
 * 规则值为 String（strict=false 允许任意输入），未知值一律回退 OFF（原版行为），
 * 与 playerScale 等多选项规则的处理方式一致。
 */
public final class MoreEndCrystalTypesHelper {

    public enum Mode {
        /** 关：原版行为，水晶只能放在黑曜石/基岩上 */
        OFF,
        /** 开：允许放在哭泣的黑曜石上，放出的为普通水晶（不无敌，光束指向 (0,128,0)） */
        ON,
        /** 无敌的：允许放在哭泣的黑曜石上，放出的为无敌水晶（Invulnerable=1 且显示底部板，光束指向 (0,128,0)） */
        INVULNERABLE
    }

    private MoreEndCrystalTypesHelper() {}

    public static Mode mode() {
        String value = CarpetPrimaryuanSettings.moreEndCrystalTypes;
        if ("true".equals(value)) {
            return Mode.ON;
        }
        if ("invulnerable".equals(value)) {
            return Mode.INVULNERABLE;
        }
        return Mode.OFF;
    }
}
