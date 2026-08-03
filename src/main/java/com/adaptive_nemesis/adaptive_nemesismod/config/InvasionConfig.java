package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 入侵事件配置类
 * 
 * 包含入侵事件的触发概率、波次配置、敌人生成参数等配置项
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class InvasionConfig {

    /**
     * 是否启用入侵事件系统
     */
    public final ForgeConfigSpec.BooleanValue ENABLE_INVASION;

    /**
     * 入侵事件自然触发概率（每分钟）
     */
    public final ForgeConfigSpec.DoubleValue INVASION_TRIGGER_CHANCE;

    /**
     * 基础波次数量（受难度加成，上限6波）
     */
    public final ForgeConfigSpec.IntValue BASE_WAVE_COUNT;

    /**
     * 每波最大敌人数量
     */
    public final ForgeConfigSpec.IntValue MAX_ENEMIES_PER_WAVE;

    /**
     * 敌人在玩家周围的生成距离（方块）
     */
    public final ForgeConfigSpec.IntValue SPAWN_DISTANCE;

    /**
     * 是否允许在水面生成敌人时穿戴冰霜行者
     */
    public final ForgeConfigSpec.BooleanValue ENABLE_FROST_WALKER_ON_WATER;

    /**
     * 是否让入侵敌人自带发光buff
     */
    public final ForgeConfigSpec.BooleanValue ENABLE_GLOWING_EFFECT;

    /**
     * 击败入侵后降低的难度值
     */
    public final ForgeConfigSpec.DoubleValue DIFFICULTY_DECREASE_ON_VICTORY;

    /**
     * 击败入侵后战利品稀有物品概率提升量
     */
    public final ForgeConfigSpec.DoubleValue LOOT_RARITY_BONUS;

    /**
     * 战利品稀有物品概率提升持续时间（分钟）
     */
    public final ForgeConfigSpec.IntValue LOOT_RARITY_BONUS_DURATION;

    /**
     * 是否启用自定义入侵难度
     */
    public final ForgeConfigSpec.BooleanValue ENABLE_CUSTOM_DIFFICULTY;

    /**
     * 自定义入侵难度倍率
     */
    public final ForgeConfigSpec.DoubleValue CUSTOM_DIFFICULTY_MULTIPLIER;

    /**
     * 入侵事件难度加成上限（波次）
     */
    public final ForgeConfigSpec.IntValue MAX_WAVE_COUNT;

    /**
     * 入侵事件间隔时间（分钟）- 两次入侵之间的最小间隔
     */
    public final ForgeConfigSpec.IntValue INVASION_COOLDOWN_MINUTES;

    /**
     * 是否在入侵开始时通知玩家
     */
    public final ForgeConfigSpec.BooleanValue ENABLE_PLAYER_NOTIFICATION;

    /**
     * 是否在入侵期间显示波次进度
     */
    public final ForgeConfigSpec.BooleanValue SHOW_WAVE_PROGRESS;

    /**
     * 是否允许入侵敌人携带战利品
     */
    public final ForgeConfigSpec.BooleanValue ENABLE_INVASION_LOOT;

    /**
     * 入侵敌人基础掉落物数量
     */
    public final ForgeConfigSpec.IntValue BASE_LOOT_COUNT;

    /**
     * 是否启用KubeJS支持
     */
    public final ForgeConfigSpec.BooleanValue ENABLE_KUBEJS_SUPPORT;

    /**
     * 是否启用数据包自定义入侵事件
     */
    public final ForgeConfigSpec.BooleanValue ENABLE_DATA_PACK_SUPPORT;

    /**
     * 服务器启动后延迟多久开始可能触发入侵（分钟）
     */
    public final ForgeConfigSpec.IntValue INITIAL_COOLDOWN_MINUTES;

    /**
     * 入侵开始前的警告时间（秒）
     */
    public final ForgeConfigSpec.IntValue WARNING_SECONDS_BEFORE_INVASION;

    public InvasionConfig(ForgeConfigSpec.Builder builder) {
        builder.push("invasion");
        
        ENABLE_INVASION = builder.comment("是否启用入侵事件系统")
            .define("enableInvasion", true);
        
        INVASION_TRIGGER_CHANCE = builder.comment("入侵事件自然触发概率（每分钟）")
            .defineInRange("invasionTriggerChance", 0.05, 0.0, 1.0);
        
        BASE_WAVE_COUNT = builder.comment("基础波次数量（受难度加成，上限6波）")
            .defineInRange("baseWaveCount", 3, 1, 10);
        
        MAX_WAVE_COUNT = builder.comment("入侵事件难度加成上限（波次）")
            .defineInRange("maxWaveCount", 6, 1, 20);
        
        MAX_ENEMIES_PER_WAVE = builder.comment("每波最大敌人数量")
            .defineInRange("maxEnemiesPerWave", 10, 1, 50);
        
        SPAWN_DISTANCE = builder.comment("敌人在玩家周围的生成距离（方块）")
            .defineInRange("spawnDistance", 40, 10, 100);
        
        ENABLE_FROST_WALKER_ON_WATER = builder.comment("是否允许在水面生成敌人时穿戴冰霜行者")
            .define("enableFrostWalkerOnWater", true);
        
        ENABLE_GLOWING_EFFECT = builder.comment("是否让入侵敌人自带发光buff")
            .define("enableGlowingEffect", true);
        
        DIFFICULTY_DECREASE_ON_VICTORY = builder.comment("击败入侵后降低的难度值")
            .defineInRange("difficultyDecreaseOnVictory", 0.1, 0.0, 0.5);
        
        LOOT_RARITY_BONUS = builder.comment("击败入侵后战利品稀有物品概率提升量")
            .defineInRange("lootRarityBonus", 0.15, 0.0, 1.0);
        
        LOOT_RARITY_BONUS_DURATION = builder.comment("战利品稀有物品概率提升持续时间（分钟）")
            .defineInRange("lootRarityBonusDuration", 30, 1, 120);
        
        ENABLE_CUSTOM_DIFFICULTY = builder.comment("是否启用自定义入侵难度")
            .define("enableCustomDifficulty", false);
        
        CUSTOM_DIFFICULTY_MULTIPLIER = builder.comment("自定义入侵难度倍率")
            .defineInRange("customDifficultyMultiplier", 1.5, 0.1, 10.0);
        
        INVASION_COOLDOWN_MINUTES = builder.comment("入侵事件间隔时间（分钟）- 两次入侵之间的最小间隔")
            .defineInRange("invasionCooldownMinutes", 15, 1, 120);
        
        ENABLE_PLAYER_NOTIFICATION = builder.comment("是否在入侵开始时通知玩家")
            .define("enablePlayerNotification", true);
        
        SHOW_WAVE_PROGRESS = builder.comment("是否在入侵期间显示波次进度")
            .define("showWaveProgress", true);
        
        ENABLE_INVASION_LOOT = builder.comment("是否允许入侵敌人携带战利品")
            .define("enableInvasionLoot", true);
        
        BASE_LOOT_COUNT = builder.comment("入侵敌人基础掉落物数量")
            .defineInRange("baseLootCount", 2, 1, 10);
        
        ENABLE_KUBEJS_SUPPORT = builder.comment("是否启用KubeJS支持")
            .define("enableKubejsSupport", true);
        
        ENABLE_DATA_PACK_SUPPORT = builder.comment("是否启用数据包自定义入侵事件")
            .define("enableDataPackSupport", true);
        
        INITIAL_COOLDOWN_MINUTES = builder.comment("服务器启动后延迟多久开始可能触发入侵（分钟）")
            .defineInRange("initialCooldownMinutes", 5, 1, 60);
        
        WARNING_SECONDS_BEFORE_INVASION = builder.comment("入侵开始前的警告时间（秒）")
            .defineInRange("warningSecondsBeforeInvasion", 5, 1, 60);
        
        builder.pop();
    }
}
