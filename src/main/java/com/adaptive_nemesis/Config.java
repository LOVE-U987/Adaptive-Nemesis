package com.adaptive_nemesis.adaptive_nemesismod;

import java.io.IOException;
import java.nio.file.Path;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Adaptive Nemesis 模组配置类
 * 包含动态难度平衡系统的所有配置项
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ==================== 基础难度配置 ====================
    
    /**
     * 难度系数基准 - 控制整体难度倍数
     */
    public static final ModConfigSpec.DoubleValue DIFFICULTY_BASE_MULTIPLIER;

    // ==================== 真实伤害转化配置 ====================
    
    /**
     * 是否启用真实伤害转化
     */
    public static final ModConfigSpec.BooleanValue ENABLE_TRUE_DAMAGE;
    
    /**
     * 低护甲阈值 (< 20)
     */
    public static final ModConfigSpec.IntValue LOW_ARMOR_THRESHOLD;
    
    /**
     * 低护甲真实伤害比例 (%)
     */
    public static final ModConfigSpec.DoubleValue LOW_ARMOR_TRUE_DAMAGE_PERCENT;
    
    /**
     * 中护甲阈值 (20-50)
     */
    public static final ModConfigSpec.IntValue MEDIUM_ARMOR_THRESHOLD;
    
    /**
     * 中护甲真实伤害比例 (%)
     */
    public static final ModConfigSpec.DoubleValue MEDIUM_ARMOR_TRUE_DAMAGE_PERCENT;
    
    /**
     * 高护甲阈值 (50-100)
     */
    public static final ModConfigSpec.IntValue HIGH_ARMOR_THRESHOLD;
    
    /**
     * 高护甲真实伤害比例 (%)
     */
    public static final ModConfigSpec.DoubleValue HIGH_ARMOR_TRUE_DAMAGE_PERCENT;
    
    /**
     * 铁乌龟真实伤害比例 (%) - 超过高护甲阈值
     */
    public static final ModConfigSpec.DoubleValue TURTLE_TRUE_DAMAGE_PERCENT;

    // ==================== Boss 特殊机制配置 ====================
    
    /**
     * 是否启用 Boss 伤害上限
     */
    public static final ModConfigSpec.BooleanValue ENABLE_BOSS_DAMAGE_CAP;
    
    /**
     * Boss 伤害上限值
     */
    public static final ModConfigSpec.DoubleValue BOSS_DAMAGE_CAP;
    
    /**
     * Boss 生命值额外倍率
     */
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH_MULTIPLIER;
    
    /**
     * Boss 伤害额外倍率
     */
    public static final ModConfigSpec.DoubleValue BOSS_DAMAGE_MULTIPLIER;

    /**
     * Boss限伤排除实体列表（按实体ID，逗号分隔）
     */
    public static final ModConfigSpec.ConfigValue<String> BOSS_DAMAGE_CAP_EXCLUSIONS;

    // ==================== Boss 识别配置 ====================

    /**
     * Boss识别关键词列表（逗号分隔）
     */
    public static final ModConfigSpec.ConfigValue<String> BOSS_IDENTIFICATION_KEYWORDS;

    /**
     * Boss血量识别阈值
     */
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH_THRESHOLD;

    // ==================== 智能浮动系统配置 ====================
    
    /**
     * 浮动范围最小值
     */
    public static final ModConfigSpec.DoubleValue FLOAT_MIN;
    
    /**
     * 浮动范围最大值
     */
    public static final ModConfigSpec.DoubleValue FLOAT_MAX;
    
    /**
     * 连续击杀时浮动倍数增加量
     */
    public static final ModConfigSpec.DoubleValue KILL_STREAK_MULTIPLIER_INCREASE;
    
    /**
     * 频繁死亡时浮动倍数减少量
     */
    public static final ModConfigSpec.DoubleValue DEATH_STREAK_MULTIPLIER_DECREASE;
    
    /**
     * 长时间未战斗后重置浮动倍数的时间（分钟）
     */
    public static final ModConfigSpec.IntValue FLOAT_RESET_TIME_MINUTES;

    // ==================== 新手保护机制配置 ====================
    
    /**
     * 是否启用新手保护
     */
    public static final ModConfigSpec.BooleanValue ENABLE_NEWBIE_PROTECTION;
    
    /**
     * 新手保护强度阈值
     */
    public static final ModConfigSpec.DoubleValue NEWBIE_STRENGTH_THRESHOLD;
    
    /**
     * 新手保护默认持续时间（分钟）
     */
    public static final ModConfigSpec.IntValue NEWBIE_PROTECTION_DURATION;
    
    /**
     * 新手保护时怪物属性减免比例
     */
    public static final ModConfigSpec.DoubleValue NEWBIE_PROTECTION_REDUCTION;
    
    /**
     * 首次死亡增加的保护时间（分钟）
     */
    public static final ModConfigSpec.IntValue DEATH_PROTECTION_BONUS;
    
    /**
     * 连续死亡次数触发强制保护的阈值
     */
    public static final ModConfigSpec.IntValue DEATH_STREAK_THRESHOLD;

    // ==================== 敌人加成上限配置 ====================
    
    /**
     * 是否启用敌人加成上限
     */
    public static final ModConfigSpec.BooleanValue ENABLE_ENEMY_BONUS_CAP;
    
    /**
     * 血量加成上限倍率
     */
    public static final ModConfigSpec.DoubleValue MAX_HEALTH_MULTIPLIER;
    
    /**
     * 伤害加成上限倍率
     */
    public static final ModConfigSpec.DoubleValue MAX_DAMAGE_MULTIPLIER;
    
    /**
     * 护甲加成上限倍率
     */
    public static final ModConfigSpec.DoubleValue MAX_ARMOR_MULTIPLIER;
    
    /**
     * 法术强度加成上限倍率
     */
    public static final ModConfigSpec.DoubleValue MAX_SPELL_POWER_MULTIPLIER;
    
    /**
     * 法术抗性加成上限倍率
     */
    public static final ModConfigSpec.DoubleValue MAX_SPELL_RESIST_MULTIPLIER;
    
    /**
     * 受击抗性加成上限倍率
     */
    public static final ModConfigSpec.DoubleValue MAX_HIT_RESIST_MULTIPLIER;
    
    /**
     * 击倒抗性加成上限倍率
     */
    public static final ModConfigSpec.DoubleValue MAX_KNOCKDOWN_RESIST_MULTIPLIER;
    
    /**
     * 耐力值加成上限倍率
     */
    public static final ModConfigSpec.DoubleValue MAX_STAMINA_MULTIPLIER;

    // ==================== 玩家强度评估权重配置 ====================
    
    /**
     * 防御能力权重
     */
    public static final ModConfigSpec.DoubleValue DEFENSE_WEIGHT;
    
    /**
     * 输出能力权重
     */
    public static final ModConfigSpec.DoubleValue DAMAGE_WEIGHT;
    
    /**
     * 神话词条权重
     */
    public static final ModConfigSpec.DoubleValue APOTHEOSIS_WEIGHT;
    
    /**
     * 铁魔法权重
     */
    public static final ModConfigSpec.DoubleValue IRONS_SPELLS_WEIGHT;
    
    /**
     * 史诗战斗权重
     */
    public static final ModConfigSpec.DoubleValue EPIC_FIGHT_WEIGHT;

    // ==================== 多人联机配置 ====================
    
    /**
     * 区域同步范围（区块）
     */
    public static final ModConfigSpec.IntValue AREA_SYNC_RANGE;

    // ==================== 随机分布配置 ====================
    
    /**
     * 是否启用属性随机分布
     */
    public static final ModConfigSpec.BooleanValue ENABLE_RANDOM_DISTRIBUTION;
    
    /**
     * 随机分布最小因子 (0.7 = 70%)
     */
    public static final ModConfigSpec.DoubleValue RANDOM_MIN_FACTOR;
    
    /**
     * 随机分布最大因子 (1.3 = 130%)
     */
    public static final ModConfigSpec.DoubleValue RANDOM_MAX_FACTOR;
    
    /**
     * 是否固定速度加成为0
     */
    public static final ModConfigSpec.BooleanValue FIX_SPEED_BONUS_TO_ZERO;

    // ==================== 难度缓动配置 ====================
    
    /**
     * 是否启用难度缓动
     */
    public static final ModConfigSpec.BooleanValue ENABLE_DIFFICULTY_SMOOTHING;
    
    /**
     * 难度缓动因子 (0.0-1.0)，越大越快对齐
     */
    public static final ModConfigSpec.DoubleValue DIFFICULTY_SMOOTHING_FACTOR;
    
    /**
     * 难度缓动更新间隔（tick数，20tick=1秒）
     */
    public static final ModConfigSpec.IntValue DIFFICULTY_SMOOTHING_TICK_INTERVAL;

    // ==================== 装备附魔强化配置 ====================
    
    /**
     * 是否启用怪物装备/附魔强化
     */
    public static final ModConfigSpec.BooleanValue ENABLE_ENCHANTMENT_SCALING;
    
    /**
     * 附魔概率基础值
     */
    public static final ModConfigSpec.DoubleValue ENCHANTMENT_CHANCE_BASE;
    
    /**
     * 每单位难度附魔概率增量
     */
    public static final ModConfigSpec.DoubleValue ENCHANTMENT_CHANCE_PER_DIFFICULTY;
    
    /**
     * 每单位难度附魔等级增量
     */
    public static final ModConfigSpec.DoubleValue ENCHANTMENT_LEVEL_PER_DIFFICULTY;
    
    /**
     * 最高附魔等级
     */
    public static final ModConfigSpec.IntValue ENCHANTMENT_MAX_LEVEL;

    // ==================== 装备生成缩放配置 ====================

    /**
     * 装备生成基础概率
     */
    public static final ModConfigSpec.DoubleValue EQUIPMENT_BASE_CHANCE;

    /**
     * 每单位难度装备生成概率增量
     */
    public static final ModConfigSpec.DoubleValue EQUIPMENT_CHANCE_PER_DIFFICULTY;

    /**
     * 装备品质跳级概率
     */
    public static final ModConfigSpec.DoubleValue EQUIPMENT_TIER_UPGRADE_CHANCE;

    /**
     * 模组装备替换概率
     */
    public static final ModConfigSpec.DoubleValue EQUIPMENT_MOD_COMPAT_CHANCE;

    // ==================== 史诗战斗缩放配置 ====================

    /**
     * 重量最小加值
     */
    public static final ModConfigSpec.DoubleValue WEIGHT_MIN_BONUS;

    /**
     * 每单位难度重量增量
     */
    public static final ModConfigSpec.DoubleValue WEIGHT_PER_MULTIPLIER;

    // ==================== 世界阶段配置 ====================
    
    /**
     * 是否启用世界阶段系统
     */
    public static final ModConfigSpec.BooleanValue ENABLE_WORLD_STAGE;
    
    /**
     * 每个世界阶段的难度倍率增量
     */
    public static final ModConfigSpec.DoubleValue WORLD_STAGE_MULTIPLIER_PER_STAGE;
    
    /**
     * 最大世界阶段数
     */
    public static final ModConfigSpec.IntValue WORLD_STAGE_MAX_STAGE;

    // ==================== 其他模组兼容性配置 ====================

    /**
     * 是否启用 L2Hostility (莱特兰恶意) 兼容模式
     * 启用后，当 L2Hostility 加载时，自适应模组会跳过血量与速度缩放，
     * 交由 L2Hostility 管理，防止 ADD_MULTIPLIED_TOTAL 导致血量爆炸
     */
    public static final ModConfigSpec.BooleanValue MOD_COMPAT_L2HOSTILITY_ENABLED;

    /**
     * 是否启用史诗战斗 (Epic Fight) 兼容模式
     * 启用后，会应用专用权重计算逻辑，防止怪物被击飞过远
     */
    public static final ModConfigSpec.BooleanValue MOD_COMPAT_EPIC_FIGHT_ENABLED;

    /**
     * 是否启用铁魔法 (Irons Spells) 兼容模式
     * 启用后，会抑制刷屏式 DEBUG 日志输出
     */
    public static final ModConfigSpec.BooleanValue MOD_COMPAT_IRONS_SPELLS_ENABLED;

    /**
     * 是否启用神化 (Apotheosis) 兼容模式
     * 启用后会在玩家强度评估中考虑神话词条加成
     */
    public static final ModConfigSpec.BooleanValue MOD_COMPAT_APOTHEOSIS_ENABLED;

    // ==================== 实体过滤配置 ====================

    /**
     * 是否启用实体过滤功能 - 关闭后黑白名单不生效，所有实体均受自适应缩放影响
     */
    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_FILTER;

    /**
     * 实体黑名单 - 在黑名单中的实体不会被自适应系统缩放
     * 支持 * 通配符，例如：minecraft:zombie,minecraft:iron_golem,alexsmobs:*
     */
    public static final ModConfigSpec.ConfigValue<String> ENTITY_BLACKLIST;

    // ==================== 调试配置 ====================
    
    /**
     * 是否启用调试日志
     */
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOG;
    
    /**
     * 是否启用详细调试模式（输出更多信息）
     */
    public static final ModConfigSpec.BooleanValue ENABLE_VERBOSE_DEBUG;
    
    /**
     * 是否将调试日志输出到文件
     */
    public static final ModConfigSpec.BooleanValue DEBUG_LOG_TO_FILE;
    
    /**
     * 调试日志文件路径
     */
    public static final ModConfigSpec.ConfigValue<String> DEBUG_LOG_FILE_PATH;
    
    /**
     * 日志输出级别 (OFF, ERROR, WARN, INFO, DEBUG)
     */
    public static final ModConfigSpec.ConfigValue<String> LOG_OUTPUT_LEVEL;

    // ==================== 静态初始化块 ====================
    
    static {
        // 基础难度配置
        DIFFICULTY_BASE_MULTIPLIER = BUILDER
            .comment("难度系数基准 - 控制整体难度倍数")
            .comment("Difficulty base multiplier - controls overall difficulty scaling")
            .defineInRange("difficultyBaseMultiplier", 0.5, 0.1, 20.0);

        // 真实伤害转化配置
        BUILDER.push("trueDamage");
        
        ENABLE_TRUE_DAMAGE = BUILDER
            .comment("是否启用真实伤害转化 - 针对高护甲玩家的铁乌龟终结者机制")
            .comment("Enable true damage conversion - anti-turtle mechanism for high armor players")
            .define("enableTrueDamage", true);
        
        LOW_ARMOR_THRESHOLD = BUILDER
            .comment("低护甲阈值")
            .defineInRange("lowArmorThreshold", 20, 0, 100);
        
        LOW_ARMOR_TRUE_DAMAGE_PERCENT = BUILDER
            .comment("低护甲时的真实伤害比例 (%)")
            .defineInRange("lowArmorTrueDamagePercent", 5.0, 0.0, 100.0);
        
        MEDIUM_ARMOR_THRESHOLD = BUILDER
            .comment("中护甲阈值")
            .defineInRange("mediumArmorThreshold", 50, 0, 200);
        
        MEDIUM_ARMOR_TRUE_DAMAGE_PERCENT = BUILDER
            .comment("中护甲时的真实伤害比例 (%)")
            .defineInRange("mediumArmorTrueDamagePercent", 15.0, 0.0, 100.0);
        
        HIGH_ARMOR_THRESHOLD = BUILDER
            .comment("高护甲阈值")
            .defineInRange("highArmorThreshold", 100, 0, 500);
        
        HIGH_ARMOR_TRUE_DAMAGE_PERCENT = BUILDER
            .comment("高护甲时的真实伤害比例 (%)")
            .defineInRange("highArmorTrueDamagePercent", 25.0, 0.0, 100.0);
        
        TURTLE_TRUE_DAMAGE_PERCENT = BUILDER
            .comment("铁乌龟状态时的真实伤害比例 (%)")
            .defineInRange("turtleTrueDamagePercent", 35.0, 0.0, 100.0);
        
        BUILDER.pop();

        // Boss 特殊机制配置
        BUILDER.push("boss");
        
        ENABLE_BOSS_DAMAGE_CAP = BUILDER
            .comment("是否启用 Boss 伤害上限 - 防止玩家秒杀 Boss")
            .comment("Enable boss damage cap - prevents players from one-shotting bosses")
            .define("enableBossDamageCap", true);
        
        BOSS_DAMAGE_CAP = BUILDER
            .comment("Boss 单次受到的伤害上限")
            .defineInRange("bossDamageCap", 100.0, 1.0, 10000.0);
        
        BOSS_HEALTH_MULTIPLIER = BUILDER
            .comment("Boss 生命值额外倍率")
            .defineInRange("bossHealthMultiplier", 5.0, 1.0, 20.0);
        
        BOSS_DAMAGE_MULTIPLIER = BUILDER
            .comment("Boss 伤害额外倍率")
            .defineInRange("bossDamageMultiplier", 3.0, 1.0, 20.0);
        
        BOSS_DAMAGE_CAP_EXCLUSIONS = BUILDER
            .comment("不受Boss限伤影响的实体ID列表（逗号分隔），例如：minecraft:zombie,minecraft:skeleton")
            .comment("Entity IDs excluded from boss damage cap (comma-separated), e.g.: minecraft:zombie,minecraft:skeleton")
            .define("bossDamageCapExclusions", "");

        BOSS_IDENTIFICATION_KEYWORDS = BUILDER
            .comment("Boss识别关键词列表（逗号分隔），用于通过实体名称识别模组Boss")
            .comment("Boss identification keywords (comma-separated) for identifying mod bosses by name")
            .define("bossIdentificationKeywords", "boss,dragon,wither,warden");

        BOSS_HEALTH_THRESHOLD = BUILDER
            .comment("Boss血量识别阈值，实体最大生命值超过此值将被识别为Boss（即使不含Boss关键词）")
            .comment("Boss health threshold - entities with max health above this value are identified as bosses")
            .defineInRange("bossHealthThreshold", 200.0, 0.0, 10000.0);

        BUILDER.pop();

        // 智能浮动系统配置
        BUILDER.push("adaptiveFloat");
        
        FLOAT_MIN = BUILDER
            .comment("浮动范围最小值 (0.8 = 80%)")
            .defineInRange("floatMin", 0.8, 0.1, 1.0);
        
        FLOAT_MAX = BUILDER
            .comment("浮动范围最大值 (1.2 = 120%)")
            .defineInRange("floatMax", 1.2, 1.0, 5.0);
        
        KILL_STREAK_MULTIPLIER_INCREASE = BUILDER
            .comment("连续击杀时浮动倍数增加量 (0.1 = +10%)")
            .defineInRange("killStreakMultiplierIncrease", 0.1, 0.0, 1.0);
        
        DEATH_STREAK_MULTIPLIER_DECREASE = BUILDER
            .comment("频繁死亡时浮动倍数减少量 (0.15 = -15%)")
            .defineInRange("deathStreakMultiplierDecrease", 0.15, 0.0, 1.0);
        
        FLOAT_RESET_TIME_MINUTES = BUILDER
            .comment("长时间未战斗后重置浮动倍数的时间（分钟）")
            .defineInRange("floatResetTimeMinutes", 10, 1, 60);
        
        BUILDER.pop();

        // 新手保护机制配置
        BUILDER.push("newbieProtection");
        
        ENABLE_NEWBIE_PROTECTION = BUILDER
            .comment("是否启用新手保护机制")
            .comment("Enable newbie protection mechanism")
            .define("enableNewbieProtection", true);
        
        NEWBIE_STRENGTH_THRESHOLD = BUILDER
            .comment("触发新手保护的玩家综合强度阈值")
            .defineInRange("newbieStrengthThreshold", 50.0, 0.0, 1000.0);
        
        NEWBIE_PROTECTION_DURATION = BUILDER
            .comment("新手保护默认持续时间（分钟）")
            .defineInRange("newbieProtectionDuration", 30, 0, 120);
        
        NEWBIE_PROTECTION_REDUCTION = BUILDER
            .comment("新手保护时怪物属性减免比例 (0.3 = -30%)")
            .defineInRange("newbieProtectionReduction", 0.3, 0.0, 1.0);
        
        DEATH_PROTECTION_BONUS = BUILDER
            .comment("首次死亡增加的保护时间（分钟）")
            .defineInRange("deathProtectionBonus", 10, 0, 60);
        
        DEATH_STREAK_THRESHOLD = BUILDER
            .comment("连续死亡次数触发强制保护的阈值")
            .defineInRange("deathStreakThreshold", 3, 1, 10);
        
        BUILDER.pop();

        // 敌人加成上限配置
        BUILDER.push("enemyBonusCaps");
        
        ENABLE_ENEMY_BONUS_CAP = BUILDER
            .comment("是否启用敌人加成上限 - 防止敌人属性无限增长")
            .comment("Enable enemy bonus cap - prevents enemy stats from growing indefinitely")
            .define("enableEnemyBonusCap", true);
        
        MAX_HEALTH_MULTIPLIER = BUILDER
            .comment("血量加成上限倍率 (5.0 = 500%)")
            .defineInRange("maxHealthMultiplier", 5.0, 1.0, 100.0);
        
        MAX_DAMAGE_MULTIPLIER = BUILDER
            .comment("伤害加成上限倍率 (5.0 = 500%)")
            .defineInRange("maxDamageMultiplier", 5.0, 1.0, 100.0);
        
        MAX_ARMOR_MULTIPLIER = BUILDER
            .comment("护甲加成上限倍率 (3.0 = 300%)")
            .defineInRange("maxArmorMultiplier", 3.0, 1.0, 100.0);
        
        MAX_SPELL_POWER_MULTIPLIER = BUILDER
            .comment("法术强度加成上限倍率 (4.0 = 400%)")
            .defineInRange("maxSpellPowerMultiplier", 4.0, 1.0, 100.0);
        
        MAX_SPELL_RESIST_MULTIPLIER = BUILDER
            .comment("法术抗性加成上限倍率 (3.0 = 300%)")
            .defineInRange("maxSpellResistMultiplier", 3.0, 1.0, 100.0);
        
        MAX_HIT_RESIST_MULTIPLIER = BUILDER
            .comment("受击抗性加成上限倍率 (2.0 = 200%)")
            .defineInRange("maxHitResistMultiplier", 2.0, 1.0, 100.0);
        
        MAX_KNOCKDOWN_RESIST_MULTIPLIER = BUILDER
            .comment("击倒抗性加成上限倍率 (2.0 = 200%)")
            .defineInRange("maxKnockdownResistMultiplier", 2.0, 1.0, 100.0);
        
        MAX_STAMINA_MULTIPLIER = BUILDER
            .comment("耐力值加成上限倍率 (3.0 = 300%)")
            .defineInRange("maxStaminaMultiplier", 3.0, 1.0, 100.0);
        
        BUILDER.pop();

        // 玩家强度评估权重配置
        BUILDER.push("playerStrengthWeights");
        
        DEFENSE_WEIGHT = BUILDER
            .comment("防御能力（护甲值、血量上限）评估权重")
            .defineInRange("defenseWeight", 1.0, 0.0, 5.0);
        
        DAMAGE_WEIGHT = BUILDER
            .comment("输出能力（伤害数值）评估权重")
            .defineInRange("damageWeight", 1.0, 0.0, 5.0);
        
        APOTHEOSIS_WEIGHT = BUILDER
            .comment("神话词条（品质与等级）评估权重")
            .defineInRange("apotheosisWeight", 0.7, 0.0, 5.0);
        
        IRONS_SPELLS_WEIGHT = BUILDER
            .comment("铁魔法（法力值、法术强度）评估权重")
            .defineInRange("ironsSpellsWeight", 0.7, 0.0, 5.0);
        
        EPIC_FIGHT_WEIGHT = BUILDER
            .comment("史诗战斗（耐力值）评估权重")
            .defineInRange("epicFightWeight", 0.7, 0.0, 5.0);
        
        BUILDER.pop();

        // 多人联机配置
        BUILDER.push("multiplayer");
        
        AREA_SYNC_RANGE = BUILDER
            .comment("区域同步范围（区块）- 同一区域内取在线玩家平均难度")
            .defineInRange("areaSyncRange", 8, 1, 32);
        
        BUILDER.pop();

        // 随机分布配置
        BUILDER.push("randomDistribution");
        
        ENABLE_RANDOM_DISTRIBUTION = BUILDER
            .comment("是否启用属性随机分布 - 让每次生成的怪物属性有随机波动")
            .comment("Enable random distribution of attributes - adds variation to each spawned mob")
            .define("enableRandomDistribution", true);
        
        RANDOM_MIN_FACTOR = BUILDER
            .comment("随机分布最小因子 (0.7 = 基础值的70%)")
            .defineInRange("randomMinFactor", 0.7, 0.1, 1.0);
        
        RANDOM_MAX_FACTOR = BUILDER
            .comment("随机分布最大因子 (1.3 = 基础值的130%)")
            .defineInRange("randomMaxFactor", 1.3, 1.0, 2.0);
        
        FIX_SPEED_BONUS_TO_ZERO = BUILDER
            .comment("是否固定移动速度加成为0 - 防止怪物跑得太快")
            .comment("Fix movement speed bonus to zero - prevents mobs from running too fast")
            .define("fixSpeedBonusToZero", true);
        
        BUILDER.pop();

        // 难度缓动配置
        BUILDER.push("difficultySmoothing");
        
        ENABLE_DIFFICULTY_SMOOTHING = BUILDER
            .comment("是否启用难度缓动 - 让怪物强度平滑变化，不会瞬间对齐玩家强度")
            .comment("Enable difficulty smoothing - let monster strength change smoothly instead of instantly matching player strength")
            .define("enableDifficultySmoothing", true);
        
        DIFFICULTY_SMOOTHING_FACTOR = BUILDER
            .comment("难度缓动因子 (0.01-0.5)，越大对齐越快。推荐0.05：约8秒对齐；0.1：约4秒对齐")
            .comment("Difficulty smoothing factor (0.01-0.5), higher = faster alignment. 0.05 ~8s, 0.1 ~4s")
            .defineInRange("difficultySmoothingFactor", 0.05, 0.01, 0.5);
        
        DIFFICULTY_SMOOTHING_TICK_INTERVAL = BUILDER
            .comment("难度缓动更新间隔（tick数，20tick=1秒）。默认5tick（0.25秒更新一次）")
            .comment("Smoothing update interval in ticks (20 ticks = 1s). Default 5 ticks")
            .defineInRange("difficultySmoothingTickInterval", 5, 1, 40);
        
        BUILDER.pop();

        // 装备附魔强化配置
        BUILDER.push("enchantmentScaling");
        
        ENABLE_ENCHANTMENT_SCALING = BUILDER
            .comment("是否启用怪物装备/附魔强化 - 难度越高，怪物装备越好、附魔等级越高")
            .comment("Enable mob equipment/enchantment scaling - higher difficulty = better gear and enchants")
            .define("enableEnchantmentScaling", true);
        
        ENCHANTMENT_CHANCE_BASE = BUILDER
            .comment("附魔概率基础值 (0.2 = 20%)")
            .defineInRange("enchantmentChanceBase", 0.2, 0.0, 1.0);
        
        ENCHANTMENT_CHANCE_PER_DIFFICULTY = BUILDER
            .comment("每单位难度倍率增加的附魔概率 (0.05 = 每1倍率+5%)")
            .defineInRange("enchantmentChancePerDifficulty", 0.05, 0.0, 0.5);
        
        ENCHANTMENT_LEVEL_PER_DIFFICULTY = BUILDER
            .comment("每单位难度倍率增加的附魔等级 (1.0 = 每1倍率+1级)")
            .defineInRange("enchantmentLevelPerDifficulty", 1.0, 0.0, 5.0);
        
        ENCHANTMENT_MAX_LEVEL = BUILDER
            .comment("最高附魔等级上限")
            .defineInRange("enchantmentMaxLevel", 5, 1, 10);

        BUILDER.pop();

        // 装备生成缩放配置
        BUILDER.push("equipmentScaling");

        EQUIPMENT_BASE_CHANCE = BUILDER
            .comment("装备生成基础概率 (0.15 = 15%) - 怪物空手时自动生成装备的基础概率")
            .comment("Equipment base spawn chance (0.15 = 15%)")
            .defineInRange("equipmentBaseChance", 0.15, 0.0, 1.0);

        EQUIPMENT_CHANCE_PER_DIFFICULTY = BUILDER
            .comment("每单位难度倍率增加的装备生成概率 (0.10 = 每1倍率+10%)")
            .comment("Equipment chance increase per difficulty multiplier (0.10 = +10% per unit)")
            .defineInRange("equipmentChancePerDifficulty", 0.10, 0.0, 1.0);

        EQUIPMENT_TIER_UPGRADE_CHANCE = BUILDER
            .comment("装备品质跳级概率 (0.15 = 15%) - 概率获得高一档品质的装备")
            .comment("Tier upgrade chance (0.15 = 15%) - chance to get one tier higher equipment")
            .defineInRange("equipmentTierUpgradeChance", 0.15, 0.0, 1.0);

        EQUIPMENT_MOD_COMPAT_CHANCE = BUILDER
            .comment("模组装备替换概率 (0.30 = 30%) - 用其他模组的装备替换原版装备的概率")
            .comment("Mod equipment replacement chance (0.30 = 30%) - chance to replace vanilla gear with modded gear")
            .defineInRange("equipmentModCompatChance", 0.30, 0.0, 1.0);

        BUILDER.pop();

        // 史诗战斗缩放配置
        BUILDER.push("epicFightScaling");

        WEIGHT_MIN_BONUS = BUILDER
            .comment("重量最小加值 (15.0) - 怪物重量至少比原值增加多少，防止史诗战斗模式被击飞")
            .comment("Minimum weight bonus (15.0) - minimum weight added to prevent knockback in Epic Fight mode")
            .defineInRange("weightMinBonus", 15.0, 0.0, 100.0);

        WEIGHT_PER_MULTIPLIER = BUILDER
            .comment("每单位难度倍率增加的重量 (20.0) - 控制怪物抵抗击退的能力")
            .comment("Weight per difficulty multiplier (20.0) - controls knockback resistance")
            .defineInRange("weightPerMultiplier", 20.0, 0.0, 200.0);

        BUILDER.pop();

        // 世界阶段配置
        BUILDER.push("worldStage");
        
        ENABLE_WORLD_STAGE = BUILDER
            .comment("是否启用世界阶段系统 - 击杀Boss后永久提升世界难度")
            .comment("Enable world stage system - permanently increase difficulty after boss kills")
            .define("enableWorldStage", true);
        
        WORLD_STAGE_MULTIPLIER_PER_STAGE = BUILDER
            .comment("每个世界阶段的难度倍率增量 (0.5 = 每个阶段+50%怪物强度)")
            .comment("Multiplier increment per world stage (0.5 = +50% enemy strength per stage)")
            .defineInRange("worldStageMultiplierPerStage", 0.5, 0.0, 5.0);
        
        WORLD_STAGE_MAX_STAGE = BUILDER
            .comment("最大世界阶段数 - 防止无限增长")
            .comment("Maximum world stage - prevents infinite growth")
            .defineInRange("worldStageMaxStage", 10, 1, 100);
        
        BUILDER.pop();

        // 其他模组兼容性配置
        BUILDER.push("modCompatibility");

        MOD_COMPAT_L2HOSTILITY_ENABLED = BUILDER
            .comment("是否启用 L2Hostility (莱特兰恶意) 兼容模式")
            .comment("启用后跳过血量与速度缩放，防止 ADD_MULTIPLIED_TOTAL 导致血量爆炸")
            .comment("Enable L2Hostility compat mode - skips health/speed scaling to prevent HP explosion")
            .define("modCompatL2HostilityEnabled", true);

        MOD_COMPAT_EPIC_FIGHT_ENABLED = BUILDER
            .comment("是否启用史诗战斗 (Epic Fight) 兼容模式")
            .comment("启用后使用专用权重计算，防止怪物被击飞过远")
            .comment("Enable Epic Fight compat mode - uses specialized weight calculation")
            .define("modCompatEpicFightEnabled", true);

        MOD_COMPAT_IRONS_SPELLS_ENABLED = BUILDER
            .comment("是否启用铁魔法 (Irons Spells) 兼容模式")
            .comment("启用后抑制刷屏式 DEBUG 日志输出")
            .comment("Enable Irons Spells compat mode - suppresses spamming DEBUG logs")
            .define("modCompatIronsSpellsEnabled", true);

        MOD_COMPAT_APOTHEOSIS_ENABLED = BUILDER
            .comment("是否启用神化 (Apotheosis) 兼容模式")
            .comment("启用后会在玩家强度评估中考虑神话词条加成")
            .comment("Enable Apotheosis compat mode - includes mythic affix bonuses in player strength evaluation")
            .define("modCompatApotheosisEnabled", true);

        BUILDER.pop();

        // ==================== 实体过滤配置 ====================
        BUILDER.push("entityFilter");

        ENABLE_ENTITY_FILTER = BUILDER
            .comment("是否启用实体过滤功能 - 关闭后黑白名单不生效，所有实体均受自适应缩放影响")
            .comment("Enable entity filter - when disabled, all entities are subject to adaptive scaling")
            .define("enableEntityFilter", true);

        ENTITY_BLACKLIST = BUILDER
            .comment("实体黑名单（逗号分隔），在黑名单中的生物不会被自适应系统缩放加成")
            .comment("支持 * 通配符匹配，例如：minecraft:zombie,minecraft:iron_golem,alexsmobs:*")
            .comment("Entity blacklist (comma-separated). Entities in this list are excluded from all adaptive scaling.")
            .comment("Supports * wildcard, e.g.: minecraft:zombie,minecraft:iron_golem,alexsmobs:*")
            .define("entityBlacklist", "");

        BUILDER.pop();

        // 调试配置
        BUILDER.push("debug");
        
        ENABLE_DEBUG_LOG = BUILDER
            .comment("是否启用调试日志输出")
            .define("enableDebugLog", false);
        
        ENABLE_VERBOSE_DEBUG = BUILDER
            .comment("是否启用详细调试模式 - 输出更多详细信息")
            .comment("Enable verbose debug mode - outputs more detailed information")
            .define("enableVerboseDebug", false);
        
        DEBUG_LOG_TO_FILE = BUILDER
            .comment("是否将调试日志输出到文件")
            .comment("Enable debug log output to file")
            .define("debugLogToFile", false);
        
        DEBUG_LOG_FILE_PATH = BUILDER
            .comment("调试日志文件路径（相对于游戏目录）")
            .comment("Debug log file path (relative to game directory)")
            .define("debugLogFilePath", "logs/adaptive_nemesis_debug.log");
        
        LOG_OUTPUT_LEVEL = BUILDER
            .comment("日志输出级别: OFF(关闭), ERROR(错误), WARN(警告), INFO(信息), DEBUG(调试)")
            .comment("Log output level: OFF, ERROR, WARN, INFO, DEBUG")
            .define("logOutputLevel", "INFO");
        
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * ModConfig引用 - 用于保存配置到文件
     */
    public static ModConfig MOD_CONFIG;

    /**
     * 保存配置到文件
     * 在配置界面修改后调用，确保修改持久化
     */
    public static void saveToFile() {
        try {
            if (MOD_CONFIG != null) {
                MOD_CONFIG.getLoadedConfig().save();
                if (ENABLE_DEBUG_LOG.get()) {
                    LOGGER.info("配置文件已手动保存到磁盘");
                }
            } else {
                LOGGER.warn("MOD_CONFIG 尚未初始化，无法保存配置文件");
            }
        } catch (Exception e) {
            LOGGER.error("保存配置文件失败", e);
        }
    }

    /**
     * 获取配置文件路径
     */
    public static Path getConfigPath() {
        if (MOD_CONFIG != null) {
            return MOD_CONFIG.getFullPath();
        }
        return null;
    }

    /**
     * 日志记录器 - 用于配置保存日志
     */
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Config");
}
