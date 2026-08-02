package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Boss 特殊机制配置
 */
public class BossConfig {

    /**
     * 是否启用 Boss 伤害上限
     */
    public final ForgeConfigSpec.BooleanValue ENABLE_BOSS_DAMAGE_CAP;

    /**
     * Boss 伤害上限值
     */
    public final ForgeConfigSpec.DoubleValue BOSS_DAMAGE_CAP;

    /**
     * Boss 生命值额外倍率
     */
    public final ForgeConfigSpec.DoubleValue BOSS_HEALTH_MULTIPLIER;

    /**
     * Boss 伤害额外倍率
     */
    public final ForgeConfigSpec.DoubleValue BOSS_DAMAGE_MULTIPLIER;

    /**
     * Boss限伤排除实体列表（按实体ID，逗号分隔）
     */
    public final ForgeConfigSpec.ConfigValue<String> BOSS_DAMAGE_CAP_EXCLUSIONS;

    /**
     * Boss识别关键词列表（逗号分隔）
     */
    public final ForgeConfigSpec.ConfigValue<String> BOSS_IDENTIFICATION_KEYWORDS;

    /**
     * Boss血量识别阈值
     */
    public final ForgeConfigSpec.DoubleValue BOSS_HEALTH_THRESHOLD;

    public BossConfig(ForgeConfigSpec.Builder builder) {
        builder.push("boss");
        ENABLE_BOSS_DAMAGE_CAP = builder
            .comment("是否启用 Boss 伤害上限 - 防止玩家秒杀 Boss")
            .comment("Enable boss damage cap - prevents players from one-shotting bosses")
            .define("enableBossDamageCap", true);
        BOSS_DAMAGE_CAP = builder
            .comment("Boss 单次受到的伤害上限")
            .comment("Boss damage cap per hit")
            .defineInRange("bossDamageCap", 100.0, 1.0, 10000.0);
        BOSS_HEALTH_MULTIPLIER = builder
            .comment("Boss 生命值额外倍率")
            .defineInRange("bossHealthMultiplier", 5.0, 1.0, 20.0);
        BOSS_DAMAGE_MULTIPLIER = builder
            .comment("Boss 伤害额外倍率")
            .defineInRange("bossDamageMultiplier", 3.0, 1.0, 20.0);
        BOSS_DAMAGE_CAP_EXCLUSIONS = builder
            .comment("不受Boss限伤影响的实体ID列表（逗号分隔），例如：minecraft:zombie,minecraft:skeleton")
            .comment("Entity IDs excluded from boss damage cap (comma-separated), e.g.: minecraft:zombie,minecraft:skeleton")
            .define("bossDamageCapExclusions", "");
        BOSS_IDENTIFICATION_KEYWORDS = builder
            .comment("Boss识别关键词列表（逗号分隔），用于通过实体名称识别模组Boss")
            .comment("Boss identification keywords (comma-separated) for identifying mod bosses by name")
            .define("bossIdentificationKeywords", "boss,dragon,wither,warden");
        BOSS_HEALTH_THRESHOLD = builder
            .comment("Boss血量识别阈值，实体最大生命值超过此值将被识别为Boss（即使不含Boss关键词）")
            .comment("Boss health threshold - entities with max health above this value are identified as bosses")
            .defineInRange("bossHealthThreshold", 200.0, 0.0, 10000.0);
        builder.pop();
    }
}
