package com.adaptive_nemesis.adaptive_nemesismod;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
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
            .defineInRange("difficultyBaseMultiplier", 3.0, 0.1, 20.0);

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
}
