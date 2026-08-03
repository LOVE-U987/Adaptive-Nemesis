package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 宿敌系统配置类
 * 
 * 包含宿敌生成概率、强化倍率范围、名称前缀池等配置项
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class NemesisConfig {

    /**
     * 是否启用宿敌日常生成系统
     */
    public final ModConfigSpec.BooleanValue ENABLE_NEMESIS_SPAWN;

    /**
     * 宿敌生成概率（0.01 = 1%）
     * 在普通敌人自然生成时有此概率被转化为宿敌
     */
    public final ModConfigSpec.DoubleValue NEMESIS_SPAWN_CHANCE;

    /**
     * 宿敌最小强化倍率
     */
    public final ModConfigSpec.DoubleValue NEMESIS_MIN_MULTIPLIER;

    /**
     * 宿敌最大强化倍率
     */
    public final ModConfigSpec.DoubleValue NEMESIS_MAX_MULTIPLIER;

    /**
     * 宿敌基础强化倍率（在此基础上受全局难度影响）
     */
    public final ModConfigSpec.DoubleValue NEMESIS_BASE_MULTIPLIER;

    /**
     * 近战克星称号前缀列表（逗号分隔）
     */
    public final ModConfigSpec.ConfigValue<String> MELEE_NEMESIS_PREFIXES;

    /**
     * 远程克星称号前缀列表（逗号分隔）
     */
    public final ModConfigSpec.ConfigValue<String> RANGED_NEMESIS_PREFIXES;

    /**
     * 魔法克星称号前缀列表（逗号分隔）
     */
    public final ModConfigSpec.ConfigValue<String> MAGIC_NEMESIS_PREFIXES;

    /**
     * 通用称号后缀列表（逗号分隔）
     */
    public final ModConfigSpec.ConfigValue<String> NEMESIS_SUFFIXES;

    /**
     * 是否显示宿敌名称（自定义名称）
     */
    public final ModConfigSpec.BooleanValue SHOW_NEMESIS_NAME;

    /**
     * 是否让宿敌名称始终可见（不只是看时显示）
     */
    public final ModConfigSpec.BooleanValue NEMESIS_NAME_ALWAYS_VISIBLE;

    /**
     * 宿敌名称颜色（十六进制，如 FF0000 = 红色）
     */
    public final ModConfigSpec.ConfigValue<String> NEMESIS_NAME_COLOR;

    /**
     * 宿敌转化是否要求目标具有攻击力属性
     * 开启后，缺少 generic.attack_damage 的生物不会被转化为宿敌
     */
    public final ModConfigSpec.BooleanValue NEMESIS_REQUIRE_ATTACK_DAMAGE;

    public NemesisConfig(ModConfigSpec.Builder builder) {
        builder.push("nemesis");
        
        ENABLE_NEMESIS_SPAWN = builder.comment("是否启用宿敌日常生成系统")
            .define("enableNemesisSpawn", true);
        
        NEMESIS_SPAWN_CHANCE = builder.comment("宿敌生成概率 (0.01 = 1%)")
            .defineInRange("nemesisSpawnChance", 0.01, 0.0, 1.0);
        
        NEMESIS_MIN_MULTIPLIER = builder.comment("宿敌最小强化倍率")
            .defineInRange("nemesisMinMultiplier", 1.5, 1.0, 10.0);
        
        NEMESIS_MAX_MULTIPLIER = builder.comment("宿敌最大强化倍率")
            .defineInRange("nemesisMaxMultiplier", 3.0, 1.0, 20.0);
        
        NEMESIS_BASE_MULTIPLIER = builder.comment("宿敌基础强化倍率（在此基础上受全局难度影响）")
            .defineInRange("nemesisBaseMultiplier", 2.0, 1.0, 10.0);
        
        // 名称池默认留空，由 NemesisNameGenerator 回退到语言文件翻译键
        // （adaptive_nemesis.nemesis.name.prefix.* / suffix.*），实现多语言支持。
        // 玩家可在配置中自定义覆盖（支持逗号分隔的任意文本）。
        MELEE_NEMESIS_PREFIXES = builder.comment("近战克星称号前缀列表（逗号分隔），留空则使用语言文件翻译键")
            .define("meleeNemesisPrefixes", "");
        
        RANGED_NEMESIS_PREFIXES = builder.comment("远程克星称号前缀列表（逗号分隔），留空则使用语言文件翻译键")
            .define("rangedNemesisPrefixes", "");
        
        MAGIC_NEMESIS_PREFIXES = builder.comment("魔法克星称号前缀列表（逗号分隔），留空则使用语言文件翻译键")
            .define("magicNemesisPrefixes", "");
        
        NEMESIS_SUFFIXES = builder.comment("通用称号后缀列表（逗号分隔），留空则使用语言文件翻译键")
            .define("nemesisSuffixes", "");
        
        SHOW_NEMESIS_NAME = builder.comment("是否显示宿敌名称（自定义名称）")
            .define("showNemesisName", true);
        
        NEMESIS_NAME_ALWAYS_VISIBLE = builder.comment("是否让宿敌名称始终可见")
            .define("nemesisNameAlwaysVisible", true);
        
        NEMESIS_NAME_COLOR = builder.comment("宿敌名称颜色（十六进制，如 FF0000 = 红色）")
            .define("nemesisNameColor", "FF0000");

        NEMESIS_REQUIRE_ATTACK_DAMAGE = builder.comment("宿敌转化是否要求目标具有攻击力属性，缺失时跳过转化")
            .define("nemesisRequireAttackDamage", true);
        
        builder.pop();
    }
}
