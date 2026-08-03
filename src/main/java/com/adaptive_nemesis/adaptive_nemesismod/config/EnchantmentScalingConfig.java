package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 装备附魔强化配置
 */
public class EnchantmentScalingConfig {

    /**
    * 是否启用怪物装备/附魔强化
    */
    public final ForgeConfigSpec.BooleanValue ENABLE_ENCHANTMENT_SCALING;

    /**
    * 附魔概率基础值
    */
    public final ForgeConfigSpec.DoubleValue ENCHANTMENT_CHANCE_BASE;

    /**
    * 每单位难度附魔概率增量
    */
    public final ForgeConfigSpec.DoubleValue ENCHANTMENT_CHANCE_PER_DIFFICULTY;

    /**
    * 每单位难度附魔等级增量
    */
    public final ForgeConfigSpec.DoubleValue ENCHANTMENT_LEVEL_PER_DIFFICULTY;

    /**
    * 最高附魔等级
    */
    public final ForgeConfigSpec.IntValue ENCHANTMENT_MAX_LEVEL;

    public EnchantmentScalingConfig(ForgeConfigSpec.Builder builder) {
        builder.push("enchantmentScaling");
        ENABLE_ENCHANTMENT_SCALING = builder.comment("是否启用怪物装备/附魔强化 - 难度越高，怪物装备越好、附魔等级越高").comment("Enable mob equipment/enchantment scaling - higher difficulty = better gear and enchants").define("enableEnchantmentScaling", true);
        ENCHANTMENT_CHANCE_BASE = builder.comment("附魔概率基础值 (0.2 = 20%)").defineInRange("enchantmentChanceBase", 0.2, 0.0, 1.0);
        ENCHANTMENT_CHANCE_PER_DIFFICULTY = builder.comment("每单位难度倍率增加的附魔概率 (0.05 = 每1倍率+5%)").defineInRange("enchantmentChancePerDifficulty", 0.05, 0.0, 0.5);
        ENCHANTMENT_LEVEL_PER_DIFFICULTY = builder.comment("每单位难度倍率增加的附魔等级 (1.0 = 每1倍率+1级)").defineInRange("enchantmentLevelPerDifficulty", 1.0, 0.0, 5.0);
        ENCHANTMENT_MAX_LEVEL = builder.comment("最高附魔等级上限").defineInRange("enchantmentMaxLevel", 5, 1, 10);
        builder.pop();
    }
}