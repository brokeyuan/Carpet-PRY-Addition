package me.primaryuan.carpet.settings;

import carpet.CarpetServer;
import carpet.api.settings.SettingsManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Predicate;

public class CarpetRuleRegistrar {
    private static final Logger LOGGER = LogManager.getLogger("CarpetPrimaryuan");

    /** 反射解析出的 Carpet 内部构造器（按参数类型精确匹配后缓存，跨版本签名可能变化） */
    private static Constructor<?> ruleAnnotationCtor;
    private static Constructor<?> parsedRuleCtor;

    public static void register(Class<?> settingsClass) {
        SettingsManager settingsManager = CarpetServer.settingsManager;
        if (settingsManager == null) {
            throw new RuntimeException("CarpetServer.settingsManager is null");
        }

        for (Field field : settingsClass.getDeclaredFields()) {
            Rule rule = field.getAnnotation(Rule.class);
            if (rule != null) {
                registerRule(settingsManager, field, rule);
            }
        }
    }

    private static void registerRule(SettingsManager settingsManager, Field field, Rule rule) {
        String ruleName = field.getName();

        try {
            Object ruleAnnotation = newRuleAnnotation(rule);
            Object carpetRule = newParsedRule(field, ruleAnnotation, settingsManager);
            settingsManager.addCarpetRule((carpet.api.settings.CarpetRule<?>) carpetRule);
            LOGGER.info("Registered rule: " + ruleName);
        } catch (Exception e) {
            LOGGER.error("Failed to register rule " + ruleName, e);
            throw new RuntimeException("Failed to register rule " + ruleName, e);
        }
    }

    /**
     * 构造 carpet.settings.ParsedRule$RuleAnnotation。
     * 按参数类型精确匹配构造器（而非取 getDeclaredConstructors()[0]），
     * 签名不匹配时携带实际签名快速失败。
     * 期望签名: (boolean, ?, ?, ?, String[] categories, String[] options, boolean strict, String, Validator[])
     */
    private static Object newRuleAnnotation(Rule rule) throws ReflectiveOperationException {
        Class<?> annoClass = Class.forName("carpet.settings.ParsedRule$RuleAnnotation");
        if (ruleAnnotationCtor == null) {
            ruleAnnotationCtor = findConstructor(annoClass, ctr ->
                    ctr.getParameterCount() == 9
                    && ctr.getParameterTypes()[0] == boolean.class
                    && ctr.getParameterTypes()[4] == String[].class
                    && ctr.getParameterTypes()[6] == boolean.class);
            ruleAnnotationCtor.setAccessible(true);
        }
        return ruleAnnotationCtor.newInstance(
                false, null, null, null, rule.categories(), rule.options(), rule.strict(), "", rule.validators());
    }

    /**
     * 构造 carpet.settings.ParsedRule。
     * 期望签名: (Field, RuleAnnotation, SettingsManager 或其父类)
     */
    private static Object newParsedRule(Field field, Object ruleAnnotation, SettingsManager settingsManager)
            throws ReflectiveOperationException {
        Class<?> parsedRuleClass = Class.forName("carpet.settings.ParsedRule");
        if (parsedRuleCtor == null) {
            parsedRuleCtor = findConstructor(parsedRuleClass, ctr ->
                    ctr.getParameterCount() == 3
                    && ctr.getParameterTypes()[0] == Field.class
                    && ctr.getParameterTypes()[1] == ruleAnnotation.getClass()
                    && ctr.getParameterTypes()[2].isAssignableFrom(settingsManager.getClass()));
            parsedRuleCtor.setAccessible(true);
        }
        return parsedRuleCtor.newInstance(field, ruleAnnotation, settingsManager);
    }

    private static Constructor<?> findConstructor(Class<?> clazz, Predicate<Constructor<?>> filter) {
        return Arrays.stream(clazz.getDeclaredConstructors())
                .filter(filter)
                .findFirst()
                .orElseThrow(() -> new LinkageError(
                        "Carpet 内部构造器签名已变化，需适配 " + clazz.getName()
                        + "，实际签名: " + Arrays.toString(clazz.getDeclaredConstructors())));
    }
}
