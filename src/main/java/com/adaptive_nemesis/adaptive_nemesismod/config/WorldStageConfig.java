package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 世界阶段配置
 */
public class WorldStageConfig {

    /**
    * 是否启用世界阶段系统
    */
    public final ModConfigSpec.BooleanValue ENABLE_WORLD_STAGE;

    /**
    * 每个世界阶段的难度倍率增量
    */
    public final ModConfigSpec.DoubleValue WORLD_STAGE_MULTIPLIER_PER_STAGE;

    /**
    * 最大世界阶段数
    */
    public final ModConfigSpec.IntValue WORLD_STAGE_MAX_STAGE;

    public WorldStageConfig(ModConfigSpec.Builder builder) {
        builder.push("worldStage");
        ENABLE_WORLD_STAGE = builder.comment("是否启用世界阶段系统 - 击杀Boss后永久提升世界难度").comment("Enable world stage system - permanently increase difficulty after boss kills").define("enableWorldStage", true);
        WORLD_STAGE_MULTIPLIER_PER_STAGE = builder.comment("每个世界阶段的难度倍率增量 (0.5 = 每个阶段+50%怪物强度)").comment("Multiplier increment per world stage (0.5 = +50% enemy strength per stage)").defineInRange("worldStageMultiplierPerStage", 0.5, 0.0, 5.0);
        WORLD_STAGE_MAX_STAGE = builder.comment("最大世界阶段数 - 防止无限增长").comment("Maximum world stage - prevents infinite growth").defineInRange("worldStageMaxStage", 10, 1, 100);
        builder.pop();
    }
}