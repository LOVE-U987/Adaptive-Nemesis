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

    /**
     * 是否启用宿敌自动消失功能
     * 开启后，宿敌在存在指定时间后自动从世界中消失
     */
    public final ModConfigSpec.BooleanValue ENABLE_NEMESIS_AUTO_DISAPPEAR;

    /**
     * 宿敌存在时间（秒）
     * 宿敌生成后经过此时间会自动消失，仅当 ENABLE_NEMESIS_AUTO_DISAPPEAR 为 true 时生效
     */
    public final ModConfigSpec.IntValue NEMESIS_AUTO_DISAPPEAR_SECONDS;

    /**
     * 宿敌消失时是否向附近玩家发送提示消息
     */
    public final ModConfigSpec.BooleanValue NEMESIS_DISAPPEAR_MESSAGE;

    /**
     * 是否启用宿敌额外自定义掉落
     * 关闭时宿敌死亡只掉落原版掉落物
     */
    public final ModConfigSpec.BooleanValue NEMESIS_LOOT_ENABLED;

    /**
     * 宿敌额外掉落战利品表 ID 列表（逗号分隔）
     * 宿敌死亡时 roll 所有配置的战利品表并将产物掉落在死亡位置
     * 也可通过数据包 data/<namespace>/nemesis_loot/<name>.json 扩展，两者合并生效
     */
    public final ModConfigSpec.ConfigValue<String> NEMESIS_LOOT_TABLES;

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
        
        ENABLE_NEMESIS_AUTO_DISAPPEAR = builder.comment("是否启用宿敌自动消失功能，开启后宿敌会在存在指定时间后自动消失")
            .define("enableNemesisAutoDisappear", true);
        
        NEMESIS_AUTO_DISAPPEAR_SECONDS = builder.comment("宿敌存在时间 (秒),仅当 enableNemesisAutoDisappear 为 true 时生效")
            .defineInRange("nemesisAutoDisappearSeconds", 300, 60, 3600);
        
        NEMESIS_DISAPPEAR_MESSAGE = builder.comment("宿敌消失时是否向附近玩家发送提示消息")
            .define("nemesisDisappearMessage", true);

        NEMESIS_LOOT_ENABLED = builder.comment("是否启用宿敌额外自定义掉落")
            .define("nemesisLootEnabled", false);

        NEMESIS_LOOT_TABLES = builder.comment("宿敌额外掉落战利品表 ID 列表（逗号分隔），数据包 nemesis_loot 配置会与其合并生效")
            .define("nemesisLootTables", "");

        builder.pop();
    }
}
