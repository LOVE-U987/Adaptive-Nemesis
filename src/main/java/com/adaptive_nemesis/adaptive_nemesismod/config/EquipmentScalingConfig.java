package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 装备生成缩放配置
 */
public class EquipmentScalingConfig {

    /**
    * 装备生成基础概率
    */
    public final ModConfigSpec.DoubleValue EQUIPMENT_BASE_CHANCE;

    /**
    * 每单位难度装备生成概率增量
    */
    public final ModConfigSpec.DoubleValue EQUIPMENT_CHANCE_PER_DIFFICULTY;

    /**
    * 装备品质跳级概率
    */
    public final ModConfigSpec.DoubleValue EQUIPMENT_TIER_UPGRADE_CHANCE;

    /**
    * 模组装备替换概率
    */
    public final ModConfigSpec.DoubleValue EQUIPMENT_MOD_COMPAT_CHANCE;

    public EquipmentScalingConfig(ModConfigSpec.Builder builder) {
        builder.push("equipmentScaling");
        EQUIPMENT_BASE_CHANCE = builder.comment("装备生成基础概率 (0.15 = 15%) - 怪物空手时自动生成装备的基础概率").comment("Equipment base spawn chance (0.15 = 15%)").defineInRange("equipmentBaseChance", 0.15, 0.0, 1.0);
        EQUIPMENT_CHANCE_PER_DIFFICULTY = builder.comment("每单位难度倍率增加的装备生成概率 (0.10 = 每1倍率+10%)").comment("Equipment chance increase per difficulty multiplier (0.10 = +10% per unit)").defineInRange("equipmentChancePerDifficulty", 0.10, 0.0, 1.0);
        EQUIPMENT_TIER_UPGRADE_CHANCE = builder.comment("装备品质跳级概率 (0.15 = 15%) - 概率获得高一档品质的装备").comment("Tier upgrade chance (0.15 = 15%) - chance to get one tier higher equipment").defineInRange("equipmentTierUpgradeChance", 0.15, 0.0, 1.0);
        EQUIPMENT_MOD_COMPAT_CHANCE = builder.comment("模组装备替换概率 (0.30 = 30%) - 用其他模组的装备替换原版装备的概率").comment("Mod equipment replacement chance (0.30 = 30%) - chance to replace vanilla gear with modded gear").defineInRange("equipmentModCompatChance", 0.30, 0.0, 1.0);
        builder.pop();
    }
}