package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 史诗战斗缩放配置
 */
public class EpicFightScalingConfig {

    /**
    * 重量最小加值
    */
    public final ForgeConfigSpec.DoubleValue WEIGHT_MIN_BONUS;

    /**
    * 每单位难度重量增量
    */
    public final ForgeConfigSpec.DoubleValue WEIGHT_PER_MULTIPLIER;

    public EpicFightScalingConfig(ForgeConfigSpec.Builder builder) {
        builder.push("epicFightScaling");
        WEIGHT_MIN_BONUS = builder.comment("重量最小加值 (15.0) - 怪物重量至少比原值增加多少，防止史诗战斗模式被击飞").comment("Minimum weight bonus (15.0) - minimum weight added to prevent knockback in Epic Fight mode").defineInRange("weightMinBonus", 15.0, 0.0, 100.0);
        WEIGHT_PER_MULTIPLIER = builder.comment("每单位难度倍率增加的重量 (20.0) - 控制怪物抵抗击退的能力").comment("Weight per difficulty multiplier (20.0) - controls knockback resistance").defineInRange("weightPerMultiplier", 20.0, 0.0, 200.0);
        builder.pop();
    }
}