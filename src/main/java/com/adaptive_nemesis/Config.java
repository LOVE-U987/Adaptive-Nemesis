package com.adaptive_nemesis.adaptive_nemesismod;

import java.nio.file.Path;

import com.adaptive_nemesis.adaptive_nemesismod.config.*;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Adaptive Nemesis 模组配置聚合类
 *
 * 所有配置项按功能分组到 {@code config} 包下的子类中，
 * 本类保留对所有历史字段的静态代理，确保现有代码无需修改即可继续通过
 * {@code Config.XXX.get()} 访问配置。
 */
public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ==================== 功能配置子类 ====================

    public static final GeneralConfig GENERAL = new GeneralConfig(BUILDER);
    public static final TrueDamageConfig TRUE_DAMAGE = new TrueDamageConfig(BUILDER);
    public static final BossConfig BOSS = new BossConfig(BUILDER);
    public static final AdaptiveFloatConfig ADAPTIVE_FLOAT = new AdaptiveFloatConfig(BUILDER);
    public static final NewbieProtectionConfig NEWBIE_PROTECTION = new NewbieProtectionConfig(BUILDER);
    public static final EnemyBonusCapConfig ENEMY_BONUS_CAP = new EnemyBonusCapConfig(BUILDER);
    public static final PlayerStrengthConfig PLAYER_STRENGTH = new PlayerStrengthConfig(BUILDER);
    public static final MultiplayerConfig MULTIPLAYER = new MultiplayerConfig(BUILDER);
    public static final RandomDistributionConfig RANDOM_DISTRIBUTION = new RandomDistributionConfig(BUILDER);
    public static final DifficultySmoothingConfig DIFFICULTY_SMOOTHING = new DifficultySmoothingConfig(BUILDER);
    public static final EnchantmentScalingConfig ENCHANTMENT_SCALING = new EnchantmentScalingConfig(BUILDER);
    public static final EquipmentScalingConfig EQUIPMENT_SCALING = new EquipmentScalingConfig(BUILDER);
    public static final WeaponDamageCapConfig WEAPON_DAMAGE_CAP = new WeaponDamageCapConfig(BUILDER);
    public static final EpicFightScalingConfig EPIC_FIGHT_SCALING = new EpicFightScalingConfig(BUILDER);
    public static final WorldStageConfig WORLD_STAGE = new WorldStageConfig(BUILDER);
    public static final ModCompatConfig MOD_COMPAT = new ModCompatConfig(BUILDER);
    public static final EntityFilterConfig ENTITY_FILTER = new EntityFilterConfig(BUILDER);
    public static final DebugConfig DEBUG = new DebugConfig(BUILDER);
    public static final WatchdogConfig WATCHDOG = new WatchdogConfig(BUILDER);
    public static final NemesisConfig NEMESIS = new NemesisConfig(BUILDER);
    public static final InvasionConfig INVASION = new InvasionConfig(BUILDER);

    // ==================== 兼容旧代码的静态字段代理 ====================
    // 以下字段直接代理自对应的功能子类，未来新代码建议直接使用 Config.Xxx.XXX.get()

    public static final ModConfigSpec.DoubleValue DIFFICULTY_BASE_MULTIPLIER = GENERAL.DIFFICULTY_BASE_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_TRUE_DAMAGE = TRUE_DAMAGE.ENABLE_TRUE_DAMAGE;
    public static final ModConfigSpec.IntValue LOW_ARMOR_THRESHOLD = TRUE_DAMAGE.LOW_ARMOR_THRESHOLD;
    public static final ModConfigSpec.DoubleValue LOW_ARMOR_TRUE_DAMAGE_PERCENT = TRUE_DAMAGE.LOW_ARMOR_TRUE_DAMAGE_PERCENT;
    public static final ModConfigSpec.IntValue MEDIUM_ARMOR_THRESHOLD = TRUE_DAMAGE.MEDIUM_ARMOR_THRESHOLD;
    public static final ModConfigSpec.DoubleValue MEDIUM_ARMOR_TRUE_DAMAGE_PERCENT = TRUE_DAMAGE.MEDIUM_ARMOR_TRUE_DAMAGE_PERCENT;
    public static final ModConfigSpec.IntValue HIGH_ARMOR_THRESHOLD = TRUE_DAMAGE.HIGH_ARMOR_THRESHOLD;
    public static final ModConfigSpec.DoubleValue HIGH_ARMOR_TRUE_DAMAGE_PERCENT = TRUE_DAMAGE.HIGH_ARMOR_TRUE_DAMAGE_PERCENT;
    public static final ModConfigSpec.DoubleValue TURTLE_TRUE_DAMAGE_PERCENT = TRUE_DAMAGE.TURTLE_TRUE_DAMAGE_PERCENT;
    public static final ModConfigSpec.BooleanValue ENABLE_BOSS_DAMAGE_CAP = BOSS.ENABLE_BOSS_DAMAGE_CAP;
    public static final ModConfigSpec.DoubleValue BOSS_DAMAGE_CAP = BOSS.BOSS_DAMAGE_CAP;
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH_MULTIPLIER = BOSS.BOSS_HEALTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue BOSS_DAMAGE_MULTIPLIER = BOSS.BOSS_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<String> BOSS_DAMAGE_CAP_EXCLUSIONS = BOSS.BOSS_DAMAGE_CAP_EXCLUSIONS;
    public static final ModConfigSpec.ConfigValue<String> BOSS_IDENTIFICATION_KEYWORDS = BOSS.BOSS_IDENTIFICATION_KEYWORDS;
    public static final ModConfigSpec.DoubleValue BOSS_HEALTH_THRESHOLD = BOSS.BOSS_HEALTH_THRESHOLD;
    public static final ModConfigSpec.DoubleValue FLOAT_MIN = ADAPTIVE_FLOAT.FLOAT_MIN;
    public static final ModConfigSpec.DoubleValue FLOAT_MAX = ADAPTIVE_FLOAT.FLOAT_MAX;
    public static final ModConfigSpec.DoubleValue KILL_STREAK_MULTIPLIER_INCREASE = ADAPTIVE_FLOAT.KILL_STREAK_MULTIPLIER_INCREASE;
    public static final ModConfigSpec.DoubleValue DEATH_STREAK_MULTIPLIER_DECREASE = ADAPTIVE_FLOAT.DEATH_STREAK_MULTIPLIER_DECREASE;
    public static final ModConfigSpec.IntValue FLOAT_RESET_TIME_MINUTES = ADAPTIVE_FLOAT.FLOAT_RESET_TIME_MINUTES;
    public static final ModConfigSpec.BooleanValue ENABLE_IDLE_DECAY = ADAPTIVE_FLOAT.ENABLE_IDLE_DECAY;
    public static final ModConfigSpec.DoubleValue IDLE_DECAY_RATE = ADAPTIVE_FLOAT.IDLE_DECAY_RATE;
    public static final ModConfigSpec.IntValue IDLE_DECAY_CHECK_INTERVAL = ADAPTIVE_FLOAT.IDLE_DECAY_CHECK_INTERVAL;
    public static final ModConfigSpec.BooleanValue ENABLE_EFFICIENCY_ADJUSTMENT = ADAPTIVE_FLOAT.ENABLE_EFFICIENCY_ADJUSTMENT;
    public static final ModConfigSpec.DoubleValue COMBAT_EFFICIENCY_THRESHOLD = ADAPTIVE_FLOAT.COMBAT_EFFICIENCY_THRESHOLD;
    public static final ModConfigSpec.DoubleValue EFFICIENCY_BASED_DECREASE = ADAPTIVE_FLOAT.EFFICIENCY_BASED_DECREASE;
    public static final ModConfigSpec.BooleanValue ENABLE_NEWBIE_PROTECTION = NEWBIE_PROTECTION.ENABLE_NEWBIE_PROTECTION;
    public static final ModConfigSpec.DoubleValue NEWBIE_STRENGTH_THRESHOLD = NEWBIE_PROTECTION.NEWBIE_STRENGTH_THRESHOLD;
    public static final ModConfigSpec.IntValue NEWBIE_PROTECTION_DURATION = NEWBIE_PROTECTION.NEWBIE_PROTECTION_DURATION;
    public static final ModConfigSpec.DoubleValue NEWBIE_PROTECTION_REDUCTION = NEWBIE_PROTECTION.NEWBIE_PROTECTION_REDUCTION;
    public static final ModConfigSpec.IntValue DEATH_PROTECTION_BONUS = NEWBIE_PROTECTION.DEATH_PROTECTION_BONUS;
    public static final ModConfigSpec.IntValue DEATH_STREAK_THRESHOLD = NEWBIE_PROTECTION.DEATH_STREAK_THRESHOLD;
    public static final ModConfigSpec.BooleanValue ENABLE_ENEMY_BONUS_CAP = ENEMY_BONUS_CAP.ENABLE_ENEMY_BONUS_CAP;
    public static final ModConfigSpec.DoubleValue MAX_HEALTH_MULTIPLIER = ENEMY_BONUS_CAP.MAX_HEALTH_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_DAMAGE_MULTIPLIER = ENEMY_BONUS_CAP.MAX_DAMAGE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_ARMOR_MULTIPLIER = ENEMY_BONUS_CAP.MAX_ARMOR_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_SPELL_POWER_MULTIPLIER = ENEMY_BONUS_CAP.MAX_SPELL_POWER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_SPELL_RESIST_MULTIPLIER = ENEMY_BONUS_CAP.MAX_SPELL_RESIST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_HIT_RESIST_MULTIPLIER = ENEMY_BONUS_CAP.MAX_HIT_RESIST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_KNOCKDOWN_RESIST_MULTIPLIER = ENEMY_BONUS_CAP.MAX_KNOCKDOWN_RESIST_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue MAX_STAMINA_MULTIPLIER = ENEMY_BONUS_CAP.MAX_STAMINA_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue DEFENSE_WEIGHT = PLAYER_STRENGTH.DEFENSE_WEIGHT;
    public static final ModConfigSpec.DoubleValue DAMAGE_WEIGHT = PLAYER_STRENGTH.DAMAGE_WEIGHT;
    public static final ModConfigSpec.DoubleValue APOTHEOSIS_WEIGHT = PLAYER_STRENGTH.APOTHEOSIS_WEIGHT;
    public static final ModConfigSpec.DoubleValue IRONS_SPELLS_WEIGHT = PLAYER_STRENGTH.IRONS_SPELLS_WEIGHT;
    public static final ModConfigSpec.DoubleValue EPIC_FIGHT_WEIGHT = PLAYER_STRENGTH.EPIC_FIGHT_WEIGHT;
    public static final ModConfigSpec.IntValue AREA_SYNC_RANGE = MULTIPLAYER.AREA_SYNC_RANGE;
    public static final ModConfigSpec.BooleanValue ENABLE_RANDOM_DISTRIBUTION = RANDOM_DISTRIBUTION.ENABLE_RANDOM_DISTRIBUTION;
    public static final ModConfigSpec.DoubleValue RANDOM_MIN_FACTOR = RANDOM_DISTRIBUTION.RANDOM_MIN_FACTOR;
    public static final ModConfigSpec.DoubleValue RANDOM_MAX_FACTOR = RANDOM_DISTRIBUTION.RANDOM_MAX_FACTOR;
    public static final ModConfigSpec.BooleanValue FIX_SPEED_BONUS_TO_ZERO = RANDOM_DISTRIBUTION.FIX_SPEED_BONUS_TO_ZERO;
    public static final ModConfigSpec.BooleanValue ENABLE_DIFFICULTY_SMOOTHING = DIFFICULTY_SMOOTHING.ENABLE_DIFFICULTY_SMOOTHING;
    public static final ModConfigSpec.DoubleValue DIFFICULTY_SMOOTHING_FACTOR = DIFFICULTY_SMOOTHING.DIFFICULTY_SMOOTHING_FACTOR;
    public static final ModConfigSpec.IntValue DIFFICULTY_SMOOTHING_TICK_INTERVAL = DIFFICULTY_SMOOTHING.DIFFICULTY_SMOOTHING_TICK_INTERVAL;
    public static final ModConfigSpec.BooleanValue ENABLE_ENCHANTMENT_SCALING = ENCHANTMENT_SCALING.ENABLE_ENCHANTMENT_SCALING;
    public static final ModConfigSpec.DoubleValue ENCHANTMENT_CHANCE_BASE = ENCHANTMENT_SCALING.ENCHANTMENT_CHANCE_BASE;
    public static final ModConfigSpec.DoubleValue ENCHANTMENT_CHANCE_PER_DIFFICULTY = ENCHANTMENT_SCALING.ENCHANTMENT_CHANCE_PER_DIFFICULTY;
    public static final ModConfigSpec.DoubleValue ENCHANTMENT_LEVEL_PER_DIFFICULTY = ENCHANTMENT_SCALING.ENCHANTMENT_LEVEL_PER_DIFFICULTY;
    public static final ModConfigSpec.IntValue ENCHANTMENT_MAX_LEVEL = ENCHANTMENT_SCALING.ENCHANTMENT_MAX_LEVEL;
    public static final ModConfigSpec.DoubleValue EQUIPMENT_BASE_CHANCE = EQUIPMENT_SCALING.EQUIPMENT_BASE_CHANCE;
    public static final ModConfigSpec.DoubleValue EQUIPMENT_CHANCE_PER_DIFFICULTY = EQUIPMENT_SCALING.EQUIPMENT_CHANCE_PER_DIFFICULTY;
    public static final ModConfigSpec.DoubleValue EQUIPMENT_TIER_UPGRADE_CHANCE = EQUIPMENT_SCALING.EQUIPMENT_TIER_UPGRADE_CHANCE;
    public static final ModConfigSpec.DoubleValue EQUIPMENT_MOD_COMPAT_CHANCE = EQUIPMENT_SCALING.EQUIPMENT_MOD_COMPAT_CHANCE;
    public static final ModConfigSpec.BooleanValue DISABLE_EQUIPMENT_DROP = EQUIPMENT_SCALING.DISABLE_EQUIPMENT_DROP;
    public static final ModConfigSpec.DoubleValue WEAPON_DAMAGE_BASE_CAP = WEAPON_DAMAGE_CAP.WEAPON_DAMAGE_BASE_CAP;
    public static final ModConfigSpec.DoubleValue WEAPON_DAMAGE_CAP_PER_DIFFICULTY = WEAPON_DAMAGE_CAP.WEAPON_DAMAGE_CAP_PER_DIFFICULTY;
    public static final ModConfigSpec.DoubleValue WEAPON_DAMAGE_MAX_CAP = WEAPON_DAMAGE_CAP.WEAPON_DAMAGE_MAX_CAP;
    public static final ModConfigSpec.DoubleValue WEIGHT_MIN_BONUS = EPIC_FIGHT_SCALING.WEIGHT_MIN_BONUS;
    public static final ModConfigSpec.DoubleValue WEIGHT_PER_MULTIPLIER = EPIC_FIGHT_SCALING.WEIGHT_PER_MULTIPLIER;
    public static final ModConfigSpec.BooleanValue ENABLE_WORLD_STAGE = WORLD_STAGE.ENABLE_WORLD_STAGE;
    public static final ModConfigSpec.DoubleValue WORLD_STAGE_MULTIPLIER_PER_STAGE = WORLD_STAGE.WORLD_STAGE_MULTIPLIER_PER_STAGE;
    public static final ModConfigSpec.IntValue WORLD_STAGE_MAX_STAGE = WORLD_STAGE.WORLD_STAGE_MAX_STAGE;
    public static final ModConfigSpec.BooleanValue MOD_COMPAT_L2HOSTILITY_ENABLED = MOD_COMPAT.MOD_COMPAT_L2HOSTILITY_ENABLED;
    public static final ModConfigSpec.BooleanValue MOD_COMPAT_EPIC_FIGHT_ENABLED = MOD_COMPAT.MOD_COMPAT_EPIC_FIGHT_ENABLED;
    public static final ModConfigSpec.BooleanValue MOD_COMPAT_IRONS_SPELLS_ENABLED = MOD_COMPAT.MOD_COMPAT_IRONS_SPELLS_ENABLED;
    public static final ModConfigSpec.BooleanValue MOD_COMPAT_APOTHEOSIS_ENABLED = MOD_COMPAT.MOD_COMPAT_APOTHEOSIS_ENABLED;
    public static final ModConfigSpec.BooleanValue ENABLE_ENTITY_FILTER = ENTITY_FILTER.ENABLE_ENTITY_FILTER;
    public static final ModConfigSpec.ConfigValue<String> ENTITY_BLACKLIST = ENTITY_FILTER.ENTITY_BLACKLIST;
    public static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOG = DEBUG.ENABLE_DEBUG_LOG;
    public static final ModConfigSpec.BooleanValue ENABLE_VERBOSE_DEBUG = DEBUG.ENABLE_VERBOSE_DEBUG;
    public static final ModConfigSpec.BooleanValue DEBUG_LOG_TO_FILE = DEBUG.DEBUG_LOG_TO_FILE;
    public static final ModConfigSpec.ConfigValue<String> DEBUG_LOG_FILE_PATH = DEBUG.DEBUG_LOG_FILE_PATH;
    public static final ModConfigSpec.ConfigValue<String> LOG_OUTPUT_LEVEL = DEBUG.LOG_OUTPUT_LEVEL;
    public static final ModConfigSpec.BooleanValue ENABLE_WATCHDOG = WATCHDOG.ENABLE_WATCHDOG;
    public static final ModConfigSpec.IntValue WATCHDOG_CHECK_INTERVAL = WATCHDOG.WATCHDOG_CHECK_INTERVAL;
    public static final ModConfigSpec.IntValue WATCHDOG_WARN_THRESHOLD = WATCHDOG.WATCHDOG_WARN_THRESHOLD;
    public static final ModConfigSpec.IntValue WATCHDOG_CRITICAL_THRESHOLD = WATCHDOG.WATCHDOG_CRITICAL_THRESHOLD;

    public static final ModConfigSpec.BooleanValue ENABLE_NEMESIS_SPAWN = NEMESIS.ENABLE_NEMESIS_SPAWN;
    public static final ModConfigSpec.DoubleValue NEMESIS_SPAWN_CHANCE = NEMESIS.NEMESIS_SPAWN_CHANCE;
    public static final ModConfigSpec.DoubleValue NEMESIS_MIN_MULTIPLIER = NEMESIS.NEMESIS_MIN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue NEMESIS_MAX_MULTIPLIER = NEMESIS.NEMESIS_MAX_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue NEMESIS_BASE_MULTIPLIER = NEMESIS.NEMESIS_BASE_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<String> MELEE_NEMESIS_PREFIXES = NEMESIS.MELEE_NEMESIS_PREFIXES;
    public static final ModConfigSpec.ConfigValue<String> RANGED_NEMESIS_PREFIXES = NEMESIS.RANGED_NEMESIS_PREFIXES;
    public static final ModConfigSpec.ConfigValue<String> MAGIC_NEMESIS_PREFIXES = NEMESIS.MAGIC_NEMESIS_PREFIXES;
    public static final ModConfigSpec.ConfigValue<String> NEMESIS_SUFFIXES = NEMESIS.NEMESIS_SUFFIXES;
    public static final ModConfigSpec.BooleanValue SHOW_NEMESIS_NAME = NEMESIS.SHOW_NEMESIS_NAME;
    public static final ModConfigSpec.BooleanValue NEMESIS_NAME_ALWAYS_VISIBLE = NEMESIS.NEMESIS_NAME_ALWAYS_VISIBLE;
    public static final ModConfigSpec.ConfigValue<String> NEMESIS_NAME_COLOR = NEMESIS.NEMESIS_NAME_COLOR;

    public static final ModConfigSpec.BooleanValue ENABLE_INVASION = INVASION.ENABLE_INVASION;
    public static final ModConfigSpec.DoubleValue INVASION_TRIGGER_CHANCE = INVASION.INVASION_TRIGGER_CHANCE;
    public static final ModConfigSpec.IntValue BASE_WAVE_COUNT = INVASION.BASE_WAVE_COUNT;
    public static final ModConfigSpec.IntValue MAX_WAVE_COUNT = INVASION.MAX_WAVE_COUNT;
    public static final ModConfigSpec.IntValue MAX_ENEMIES_PER_WAVE = INVASION.MAX_ENEMIES_PER_WAVE;
    public static final ModConfigSpec.IntValue SPAWN_DISTANCE = INVASION.SPAWN_DISTANCE;
    public static final ModConfigSpec.BooleanValue ENABLE_FROST_WALKER_ON_WATER = INVASION.ENABLE_FROST_WALKER_ON_WATER;
    public static final ModConfigSpec.BooleanValue ENABLE_GLOWING_EFFECT = INVASION.ENABLE_GLOWING_EFFECT;
    public static final ModConfigSpec.DoubleValue DIFFICULTY_DECREASE_ON_VICTORY = INVASION.DIFFICULTY_DECREASE_ON_VICTORY;
    public static final ModConfigSpec.DoubleValue LOOT_RARITY_BONUS = INVASION.LOOT_RARITY_BONUS;
    public static final ModConfigSpec.IntValue LOOT_RARITY_BONUS_DURATION = INVASION.LOOT_RARITY_BONUS_DURATION;
    public static final ModConfigSpec.BooleanValue ENABLE_CUSTOM_DIFFICULTY = INVASION.ENABLE_CUSTOM_DIFFICULTY;
    public static final ModConfigSpec.DoubleValue CUSTOM_DIFFICULTY_MULTIPLIER = INVASION.CUSTOM_DIFFICULTY_MULTIPLIER;
    public static final ModConfigSpec.IntValue INVASION_COOLDOWN_MINUTES = INVASION.INVASION_COOLDOWN_MINUTES;
    public static final ModConfigSpec.BooleanValue ENABLE_PLAYER_NOTIFICATION = INVASION.ENABLE_PLAYER_NOTIFICATION;
    public static final ModConfigSpec.BooleanValue SHOW_WAVE_PROGRESS = INVASION.SHOW_WAVE_PROGRESS;
    public static final ModConfigSpec.BooleanValue ENABLE_INVASION_LOOT = INVASION.ENABLE_INVASION_LOOT;
    public static final ModConfigSpec.IntValue BASE_LOOT_COUNT = INVASION.BASE_LOOT_COUNT;
    public static final ModConfigSpec.BooleanValue ENABLE_KUBEJS_SUPPORT = INVASION.ENABLE_KUBEJS_SUPPORT;
    public static final ModConfigSpec.BooleanValue ENABLE_DATA_PACK_SUPPORT = INVASION.ENABLE_DATA_PACK_SUPPORT;
    public static final ModConfigSpec.IntValue INITIAL_COOLDOWN_MINUTES = INVASION.INITIAL_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue WARNING_SECONDS_BEFORE_INVASION = INVASION.WARNING_SECONDS_BEFORE_INVASION;

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