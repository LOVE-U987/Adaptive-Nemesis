package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 难度缓动配置
 */
public class DifficultySmoothingConfig {

    /**
    * 是否启用难度缓动
    */
    public final ForgeConfigSpec.BooleanValue ENABLE_DIFFICULTY_SMOOTHING;

    /**
    * 难度缓动因子 (0.0-1.0)，越大越快对齐
    */
    public final ForgeConfigSpec.DoubleValue DIFFICULTY_SMOOTHING_FACTOR;

    /**
    * 难度缓动更新间隔（tick数，20tick=1秒）
    */
    public final ForgeConfigSpec.IntValue DIFFICULTY_SMOOTHING_TICK_INTERVAL;

    public DifficultySmoothingConfig(ForgeConfigSpec.Builder builder) {
        builder.push("difficultySmoothing");
        ENABLE_DIFFICULTY_SMOOTHING = builder.comment("是否启用难度缓动 - 让怪物强度平滑变化，不会瞬间对齐玩家强度").comment("Enable difficulty smoothing - let monster strength change smoothly instead of instantly matching player strength").define("enableDifficultySmoothing", true);
        DIFFICULTY_SMOOTHING_FACTOR = builder.comment("难度缓动因子 (0.01-0.5)，越大对齐越快。推荐0.05：约8秒对齐；0.1：约4秒对齐").comment("Difficulty smoothing factor (0.01-0.5), higher = faster alignment.0.05 ~8s, 0.1 ~4s").defineInRange("difficultySmoothingFactor", 0.05, 0.01, 0.5);
        DIFFICULTY_SMOOTHING_TICK_INTERVAL = builder.comment("难度缓动更新间隔（tick数，20tick=1秒）。默认5tick（0.25秒更新一次）").comment("Smoothing update interval in ticks (20 ticks = 1s).Default 5 ticks").defineInRange("difficultySmoothingTickInterval", 5, 1, 40);
        builder.pop();
    }
}