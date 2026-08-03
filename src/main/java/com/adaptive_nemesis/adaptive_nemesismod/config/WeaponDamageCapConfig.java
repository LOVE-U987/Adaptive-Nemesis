package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 武器动态伤害上限配置
 */
public class WeaponDamageCapConfig {

    /**
    * 武器伤害基础上限
    */
    public final ModConfigSpec.DoubleValue WEAPON_DAMAGE_BASE_CAP;

    /**
    * 每单位难度倍率增加的伤害上限
    */
    public final ModConfigSpec.DoubleValue WEAPON_DAMAGE_CAP_PER_DIFFICULTY;

    /**
    * 武器伤害绝对上限
    */
    public final ModConfigSpec.DoubleValue WEAPON_DAMAGE_MAX_CAP;

    public WeaponDamageCapConfig(ModConfigSpec.Builder builder) {
        builder.push("weaponDamageCap");
        WEAPON_DAMAGE_BASE_CAP = builder.comment("武器伤害基础上限 (默认12.0 = 6颗心) - 最低难度时的武器伤害上限").comment("Base weapon damage cap (default 12.0 = 6 hearts) - at minimum difficulty").defineInRange("weaponDamageBaseCap", 12.0, 1.0, 100.0);
        WEAPON_DAMAGE_CAP_PER_DIFFICULTY = builder.comment("每单位难度倍率增加的伤害上限 (默认3.0 = 每个倍率+3点伤害)").comment("Damage cap increase per difficulty unit (default 3.0 = +3 damage per unit)").defineInRange("weaponDamageCapPerDifficulty", 3.0, 0.0, 50.0);
        WEAPON_DAMAGE_MAX_CAP = builder.comment("武器伤害绝对上限 (默认40.0 = 20颗心) - 无论难度多高都不超过此值").comment("Absolute weapon damage cap (default 40.0 = 20 hearts) - hard upper limit").defineInRange("weaponDamageMaxCap", 40.0, 1.0, 500.0);
        builder.pop();
    }
}