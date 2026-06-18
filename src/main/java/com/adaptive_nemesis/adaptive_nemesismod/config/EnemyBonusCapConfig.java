package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 敌人加成上限配置
 */
public class EnemyBonusCapConfig {

    /**
    * 是否启用敌人加成上限
    */
    public final ModConfigSpec.BooleanValue ENABLE_ENEMY_BONUS_CAP;

    /**
    * 血量加成上限倍率
    */
    public final ModConfigSpec.DoubleValue MAX_HEALTH_MULTIPLIER;

    /**
    * 伤害加成上限倍率
    */
    public final ModConfigSpec.DoubleValue MAX_DAMAGE_MULTIPLIER;

    /**
    * 护甲加成上限倍率
    */
    public final ModConfigSpec.DoubleValue MAX_ARMOR_MULTIPLIER;

    /**
    * 法术强度加成上限倍率
    */
    public final ModConfigSpec.DoubleValue MAX_SPELL_POWER_MULTIPLIER;

    /**
    * 法术抗性加成上限倍率
    */
    public final ModConfigSpec.DoubleValue MAX_SPELL_RESIST_MULTIPLIER;

    /**
    * 受击抗性加成上限倍率
    */
    public final ModConfigSpec.DoubleValue MAX_HIT_RESIST_MULTIPLIER;

    /**
    * 击倒抗性加成上限倍率
    */
    public final ModConfigSpec.DoubleValue MAX_KNOCKDOWN_RESIST_MULTIPLIER;

    /**
    * 耐力值加成上限倍率
    */
    public final ModConfigSpec.DoubleValue MAX_STAMINA_MULTIPLIER;

    public EnemyBonusCapConfig(ModConfigSpec.Builder builder) {
        builder.push("enemyBonusCaps");
        ENABLE_ENEMY_BONUS_CAP = builder.comment("是否启用敌人加成上限 - 防止敌人属性无限增长").comment("Enable enemy bonus cap - prevents enemy stats from growing indefinitely").define("enableEnemyBonusCap", true);
        MAX_HEALTH_MULTIPLIER = builder.comment("血量加成上限倍率 (5.0 = 500%)").defineInRange("maxHealthMultiplier", 5.0, 1.0, 100.0);
        MAX_DAMAGE_MULTIPLIER = builder.comment("伤害加成上限倍率 (5.0 = 500%)").defineInRange("maxDamageMultiplier", 5.0, 1.0, 100.0);
        MAX_ARMOR_MULTIPLIER = builder.comment("护甲加成上限倍率 (3.0 = 300%)").defineInRange("maxArmorMultiplier", 3.0, 1.0, 100.0);
        MAX_SPELL_POWER_MULTIPLIER = builder.comment("法术强度加成上限倍率 (4.0 = 400%)").defineInRange("maxSpellPowerMultiplier", 4.0, 1.0, 100.0);
        MAX_SPELL_RESIST_MULTIPLIER = builder.comment("法术抗性加成上限倍率 (3.0 = 300%)").defineInRange("maxSpellResistMultiplier", 3.0, 1.0, 100.0);
        MAX_HIT_RESIST_MULTIPLIER = builder.comment("受击抗性加成上限倍率 (2.0 = 200%)").defineInRange("maxHitResistMultiplier", 2.0, 1.0, 100.0);
        MAX_KNOCKDOWN_RESIST_MULTIPLIER = builder.comment("击倒抗性加成上限倍率 (2.0 = 200%)").defineInRange("maxKnockdownResistMultiplier", 2.0, 1.0, 100.0);
        MAX_STAMINA_MULTIPLIER = builder.comment("耐力值加成上限倍率 (3.0 = 300%)").defineInRange("maxStaminaMultiplier", 3.0, 1.0, 100.0);
        builder.pop();
    }
}