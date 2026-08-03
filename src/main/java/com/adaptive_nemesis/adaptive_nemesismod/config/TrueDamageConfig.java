package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 真实伤害转化配置
 */
public class TrueDamageConfig {

    /**
    * 是否启用真实伤害转化
    */
    public final ForgeConfigSpec.BooleanValue ENABLE_TRUE_DAMAGE;

    /**
    * 低护甲阈值 (< 20)
    */
    public final ForgeConfigSpec.IntValue LOW_ARMOR_THRESHOLD;

    /**
    * 低护甲真实伤害比例 (%)
    */
    public final ForgeConfigSpec.DoubleValue LOW_ARMOR_TRUE_DAMAGE_PERCENT;

    /**
    * 中护甲阈值 (20-50)
    */
    public final ForgeConfigSpec.IntValue MEDIUM_ARMOR_THRESHOLD;

    /**
    * 中护甲真实伤害比例 (%)
    */
    public final ForgeConfigSpec.DoubleValue MEDIUM_ARMOR_TRUE_DAMAGE_PERCENT;

    /**
    * 高护甲阈值 (50-100)
    */
    public final ForgeConfigSpec.IntValue HIGH_ARMOR_THRESHOLD;

    /**
    * 高护甲真实伤害比例 (%)
    */
    public final ForgeConfigSpec.DoubleValue HIGH_ARMOR_TRUE_DAMAGE_PERCENT;

    /**
    * 铁乌龟真实伤害比例 (%) - 超过高护甲阈值
    */
    public final ForgeConfigSpec.DoubleValue TURTLE_TRUE_DAMAGE_PERCENT;

    public TrueDamageConfig(ForgeConfigSpec.Builder builder) {
        builder.push("trueDamage");
        ENABLE_TRUE_DAMAGE = builder.comment("是否启用真实伤害转化 - 针对高护甲玩家的铁乌龟终结者机制").comment("Enable true damage conversion - anti-turtle mechanism for high armor players").define("enableTrueDamage", true);
        LOW_ARMOR_THRESHOLD = builder.comment("低护甲阈值").defineInRange("lowArmorThreshold", 20, 0, 100);
        LOW_ARMOR_TRUE_DAMAGE_PERCENT = builder.comment("低护甲时的真实伤害比例 (%)").defineInRange("lowArmorTrueDamagePercent", 5.0, 0.0, 100.0);
        MEDIUM_ARMOR_THRESHOLD = builder.comment("中护甲阈值").defineInRange("mediumArmorThreshold", 50, 0, 200);
        MEDIUM_ARMOR_TRUE_DAMAGE_PERCENT = builder.comment("中护甲时的真实伤害比例 (%)").defineInRange("mediumArmorTrueDamagePercent", 15.0, 0.0, 100.0);
        HIGH_ARMOR_THRESHOLD = builder.comment("高护甲阈值").defineInRange("highArmorThreshold", 100, 0, 500);
        HIGH_ARMOR_TRUE_DAMAGE_PERCENT = builder.comment("高护甲时的真实伤害比例 (%)").defineInRange("highArmorTrueDamagePercent", 25.0, 0.0, 100.0);
        TURTLE_TRUE_DAMAGE_PERCENT = builder.comment("铁乌龟状态时的真实伤害比例 (%)").defineInRange("turtleTrueDamagePercent", 35.0, 0.0, 100.0);
        builder.pop();
    }
}