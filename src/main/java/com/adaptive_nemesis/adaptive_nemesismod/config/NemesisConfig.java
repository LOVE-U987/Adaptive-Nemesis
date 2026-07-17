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
        
        MELEE_NEMESIS_PREFIXES = builder.comment("近战克星称号前缀列表（逗号分隔）")
            .define("meleeNemesisPrefixes", "近战克星,战神,屠夫,狂战士,嗜血者");
        
        RANGED_NEMESIS_PREFIXES = builder.comment("远程克星称号前缀列表（逗号分隔）")
            .define("rangedNemesisPrefixes", "弓术克星,狙击者,鹰眼,暗影猎手,迅捷杀手");
        
        MAGIC_NEMESIS_PREFIXES = builder.comment("魔法克星称号前缀列表（逗号分隔）")
            .define("magicNemesisPrefixes", "魔法克星,弑神者,咒术师,虚空行者,奥术大师");
        
        NEMESIS_SUFFIXES = builder.comment("通用称号后缀列表（逗号分隔）")
            .define("nemesisSuffixes", "—末日使者,—暗影领主,—死亡骑士,—深渊行者,—地狱使者,—黑暗先知,—亡灵统帅,—恐惧化身");
        
        SHOW_NEMESIS_NAME = builder.comment("是否显示宿敌名称（自定义名称）")
            .define("showNemesisName", true);
        
        NEMESIS_NAME_ALWAYS_VISIBLE = builder.comment("是否让宿敌名称始终可见")
            .define("nemesisNameAlwaysVisible", true);
        
        NEMESIS_NAME_COLOR = builder.comment("宿敌名称颜色（十六进制，如 FF0000 = 红色）")
            .define("nemesisNameColor", "FF0000");
        
        builder.pop();
    }
}
