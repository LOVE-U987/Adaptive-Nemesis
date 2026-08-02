package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 玩家强度评估权重配置
 */
public class PlayerStrengthConfig {

    /**
    * 防御能力权重
    */
    public final ForgeConfigSpec.DoubleValue DEFENSE_WEIGHT;

    /**
    * 输出能力权重
    */
    public final ForgeConfigSpec.DoubleValue DAMAGE_WEIGHT;

    /**
    * 神话词条权重
    */
    public final ForgeConfigSpec.DoubleValue APOTHEOSIS_WEIGHT;

    /**
    * 铁魔法权重
    */
    public final ForgeConfigSpec.DoubleValue IRONS_SPELLS_WEIGHT;

    /**
    * 史诗战斗权重
    */
    public final ForgeConfigSpec.DoubleValue EPIC_FIGHT_WEIGHT;

    public PlayerStrengthConfig(ForgeConfigSpec.Builder builder) {
        builder.push("playerStrengthWeights");
        DEFENSE_WEIGHT = builder.comment("防御能力（护甲值、血量上限）评估权重").defineInRange("defenseWeight", 1.0, 0.0, 5.0);
        DAMAGE_WEIGHT = builder.comment("输出能力（伤害数值）评估权重").defineInRange("damageWeight", 1.0, 0.0, 5.0);
        APOTHEOSIS_WEIGHT = builder.comment("神话词条（品质与等级）评估权重").defineInRange("apotheosisWeight", 0.7, 0.0, 5.0);
        IRONS_SPELLS_WEIGHT = builder.comment("铁魔法（法力值、法术强度）评估权重").defineInRange("ironsSpellsWeight", 0.7, 0.0, 5.0);
        EPIC_FIGHT_WEIGHT = builder.comment("史诗战斗（耐力值）评估权重").defineInRange("epicFightWeight", 0.7, 0.0, 5.0);
        builder.pop();
    }
}