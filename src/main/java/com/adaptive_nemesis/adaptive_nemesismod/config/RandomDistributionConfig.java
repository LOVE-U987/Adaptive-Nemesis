package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 随机分布配置
 */
public class RandomDistributionConfig {

    /**
    * 是否启用属性随机分布
    */
    public final ModConfigSpec.BooleanValue ENABLE_RANDOM_DISTRIBUTION;

    /**
    * 随机分布最小因子 (0.7 = 70%)
    */
    public final ModConfigSpec.DoubleValue RANDOM_MIN_FACTOR;

    /**
    * 随机分布最大因子 (1.3 = 130%)
    */
    public final ModConfigSpec.DoubleValue RANDOM_MAX_FACTOR;

    /**
    * 是否固定速度加成为0
    */
    public final ModConfigSpec.BooleanValue FIX_SPEED_BONUS_TO_ZERO;

    public RandomDistributionConfig(ModConfigSpec.Builder builder) {
        builder.push("randomDistribution");
        ENABLE_RANDOM_DISTRIBUTION = builder.comment("是否启用属性随机分布 - 让每次生成的怪物属性有随机波动").comment("Enable random distribution of attributes - adds variation to each spawned mob").define("enableRandomDistribution", true);
        RANDOM_MIN_FACTOR = builder.comment("随机分布最小因子 (0.7 = 基础值的70%)").defineInRange("randomMinFactor", 0.7, 0.1, 1.0);
        RANDOM_MAX_FACTOR = builder.comment("随机分布最大因子 (1.3 = 基础值的130%)").defineInRange("randomMaxFactor", 1.3, 1.0, 2.0);
        FIX_SPEED_BONUS_TO_ZERO = builder.comment("是否固定移动速度加成为0 - 防止怪物跑得太快").comment("Fix movement speed bonus to zero - prevents mobs from running too fast").define("fixSpeedBonusToZero", true);
        builder.pop();
    }
}