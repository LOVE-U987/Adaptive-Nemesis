package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 基础难度配置
 */
public class GeneralConfig {

    /**
     * 难度系数基准 - 控制整体难度倍数
     */
    public final ModConfigSpec.DoubleValue DIFFICULTY_BASE_MULTIPLIER;

    /**
     * 构造基础难度配置
     *
     * @param builder 配置构建器，由 Config 聚合类统一传入
     */
    public GeneralConfig(ModConfigSpec.Builder builder) {
        builder.push("general");
        DIFFICULTY_BASE_MULTIPLIER = builder
            .comment("难度系数基准 - 控制整体难度倍数")
            .comment("Difficulty base multiplier - controls overall difficulty scaling")
            .defineInRange("difficultyBaseMultiplier", 0.5, 0.1, 20.0);
        builder.pop();
    }
}
