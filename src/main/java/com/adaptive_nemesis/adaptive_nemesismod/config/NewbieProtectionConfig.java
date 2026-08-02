package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 新手保护机制配置
 */
public class NewbieProtectionConfig {

    /**
    * 是否启用新手保护
    */
    public final ForgeConfigSpec.BooleanValue ENABLE_NEWBIE_PROTECTION;

    /**
    * 新手保护强度阈值
    */
    public final ForgeConfigSpec.DoubleValue NEWBIE_STRENGTH_THRESHOLD;

    /**
    * 新手保护默认持续时间（分钟）
    */
    public final ForgeConfigSpec.IntValue NEWBIE_PROTECTION_DURATION;

    /**
    * 新手保护时怪物属性减免比例
    */
    public final ForgeConfigSpec.DoubleValue NEWBIE_PROTECTION_REDUCTION;

    /**
    * 首次死亡增加的保护时间（分钟）
    */
    public final ForgeConfigSpec.IntValue DEATH_PROTECTION_BONUS;

    /**
    * 连续死亡次数触发强制保护的阈值
    */
    public final ForgeConfigSpec.IntValue DEATH_STREAK_THRESHOLD;

    public NewbieProtectionConfig(ForgeConfigSpec.Builder builder) {
        builder.push("newbieProtection");
        ENABLE_NEWBIE_PROTECTION = builder.comment("是否启用新手保护机制").comment("Enable newbie protection mechanism").define("enableNewbieProtection", true);
        NEWBIE_STRENGTH_THRESHOLD = builder.comment("触发新手保护的玩家综合强度阈值").defineInRange("newbieStrengthThreshold", 50.0, 0.0, 1000.0);
        NEWBIE_PROTECTION_DURATION = builder.comment("新手保护默认持续时间（分钟）").defineInRange("newbieProtectionDuration", 30, 0, 120);
        NEWBIE_PROTECTION_REDUCTION = builder.comment("新手保护时怪物属性减免比例 (0.3 = -30%)").defineInRange("newbieProtectionReduction", 0.3, 0.0, 1.0);
        DEATH_PROTECTION_BONUS = builder.comment("首次死亡增加的保护时间（分钟）").defineInRange("deathProtectionBonus", 10, 0, 60);
        DEATH_STREAK_THRESHOLD = builder.comment("连续死亡次数触发强制保护的阈值").defineInRange("deathStreakThreshold", 3, 1, 10);
        builder.pop();
    }
}