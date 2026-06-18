package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 智能浮动系统配置
 */
public class AdaptiveFloatConfig {

    /**
    * 浮动范围最小值
    */
    public final ModConfigSpec.DoubleValue FLOAT_MIN;

    /**
    * 浮动范围最大值
    */
    public final ModConfigSpec.DoubleValue FLOAT_MAX;

    /**
    * 连续击杀时浮动倍数增加量
    */
    public final ModConfigSpec.DoubleValue KILL_STREAK_MULTIPLIER_INCREASE;

    /**
    * 频繁死亡时浮动倍数减少量
    */
    public final ModConfigSpec.DoubleValue DEATH_STREAK_MULTIPLIER_DECREASE;

    /**
    * 长时间未战斗后重置浮动倍数的时间（分钟）
    */
    public final ModConfigSpec.IntValue FLOAT_RESET_TIME_MINUTES;

    public AdaptiveFloatConfig(ModConfigSpec.Builder builder) {
        builder.push("adaptiveFloat");
        FLOAT_MIN = builder.comment("浮动范围最小值 (0.8 = 80%)").defineInRange("floatMin", 0.8, 0.1, 1.0);
        FLOAT_MAX = builder.comment("浮动范围最大值 (1.2 = 120%)").defineInRange("floatMax", 1.2, 1.0, 5.0);
        KILL_STREAK_MULTIPLIER_INCREASE = builder.comment("连续击杀时浮动倍数增加量 (0.1 = +10%)").defineInRange("killStreakMultiplierIncrease", 0.1, 0.0, 1.0);
        DEATH_STREAK_MULTIPLIER_DECREASE = builder.comment("频繁死亡时浮动倍数减少量 (0.15 = -15%)").defineInRange("deathStreakMultiplierDecrease", 0.15, 0.0, 1.0);
        FLOAT_RESET_TIME_MINUTES = builder.comment("长时间未战斗后重置浮动倍数的时间（分钟）").defineInRange("floatResetTimeMinutes", 10, 1, 60);
        builder.pop();
    }
}