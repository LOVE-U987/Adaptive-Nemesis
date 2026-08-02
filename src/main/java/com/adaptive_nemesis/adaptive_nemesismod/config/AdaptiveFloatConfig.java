package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 智能浮动系统配置
 */
public class AdaptiveFloatConfig {

    /**
    * 浮动范围最小值
    */
    public final ForgeConfigSpec.DoubleValue FLOAT_MIN;

    /**
    * 浮动范围最大值
    */
    public final ForgeConfigSpec.DoubleValue FLOAT_MAX;

    /**
    * 连续击杀时浮动倍数增加量
    */
    public final ForgeConfigSpec.DoubleValue KILL_STREAK_MULTIPLIER_INCREASE;

    /**
    * 频繁死亡时浮动倍数减少量
    */
    public final ForgeConfigSpec.DoubleValue DEATH_STREAK_MULTIPLIER_DECREASE;

    /**
    * 长时间未战斗后重置浮动倍数的时间（分钟）
    */
    public final ForgeConfigSpec.IntValue FLOAT_RESET_TIME_MINUTES;

    /**
    * 空闲衰减速率 - 每分钟自动降低的倍率（0.02 = 每分钟降低2%）
    * 当玩家长时间未战斗时，难度会自动缓慢下降
    */
    public final ForgeConfigSpec.DoubleValue IDLE_DECAY_RATE;

    /**
    * 战斗效率阈值 - 低于此值时触发难度下调（0.3 = 30%命中率）
    * 用于检测玩家战斗效率低下的情况
    */
    public final ForgeConfigSpec.DoubleValue COMBAT_EFFICIENCY_THRESHOLD;

    /**
    * 基于效率的难度下调量 - 战斗效率低下时每次下调的倍率
    */
    public final ForgeConfigSpec.DoubleValue EFFICIENCY_BASED_DECREASE;

    /**
    * 空闲衰减检查间隔（秒）- 每多少秒检查一次空闲衰减
    */
    public final ForgeConfigSpec.IntValue IDLE_DECAY_CHECK_INTERVAL;

    /**
    * 是否启用基于时间的空闲衰减（推荐启用）
    */
    public final ForgeConfigSpec.BooleanValue ENABLE_IDLE_DECAY;

    /**
    * 是否启用基于战斗效率的难度调整
    */
    public final ForgeConfigSpec.BooleanValue ENABLE_EFFICIENCY_ADJUSTMENT;

    public AdaptiveFloatConfig(ForgeConfigSpec.Builder builder) {
        builder.push("adaptiveFloat");
        FLOAT_MIN = builder.comment("浮动范围最小值 (0.8 = 80%)").defineInRange("floatMin", 0.8, 0.1, 1.0);
        FLOAT_MAX = builder.comment("浮动范围最大值 (1.2 = 120%)").defineInRange("floatMax", 1.2, 1.0, 5.0);
        KILL_STREAK_MULTIPLIER_INCREASE = builder.comment("连续击杀时浮动倍数增加量 (0.1 = +10%)").defineInRange("killStreakMultiplierIncrease", 0.1, 0.0, 1.0);
        DEATH_STREAK_MULTIPLIER_DECREASE = builder.comment("频繁死亡时浮动倍数减少量 (0.15 = -15%)").defineInRange("deathStreakMultiplierDecrease", 0.15, 0.0, 1.0);
        FLOAT_RESET_TIME_MINUTES = builder.comment("长时间未战斗后重置浮动倍数的时间（分钟）").defineInRange("floatResetTimeMinutes", 10, 1, 60);
        ENABLE_IDLE_DECAY = builder.comment("是否启用基于时间的空闲衰减").define("enableIdleDecay", true);
        IDLE_DECAY_RATE = builder.comment("空闲衰减速率 - 每分钟自动降低的倍率 (0.02 = 每分钟降低2%)").defineInRange("idleDecayRate", 0.02, 0.0, 0.5);
        IDLE_DECAY_CHECK_INTERVAL = builder.comment("空闲衰减检查间隔（秒）").defineInRange("idleDecayCheckInterval", 5, 1, 60);
        ENABLE_EFFICIENCY_ADJUSTMENT = builder.comment("是否启用基于战斗效率的难度调整").define("enableEfficiencyAdjustment", true);
        COMBAT_EFFICIENCY_THRESHOLD = builder.comment("战斗效率阈值 - 低于此值时触发难度下调 (0.3 = 30%命中率)").defineInRange("combatEfficiencyThreshold", 0.3, 0.0, 1.0);
        EFFICIENCY_BASED_DECREASE = builder.comment("基于效率的难度下调量").defineInRange("efficiencyBasedDecrease", 0.05, 0.0, 0.5);
        builder.pop();
    }
}