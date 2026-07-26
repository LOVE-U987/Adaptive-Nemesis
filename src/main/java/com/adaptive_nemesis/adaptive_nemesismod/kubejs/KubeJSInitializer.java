package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionRewardData;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionSystem;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionSystem.InvasionType;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisProfile;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * KubeJS 初始化器
 *
 * 此类包含所有对 KubeJS API 的直接依赖，通过 ServiceLoader 机制由 KubeJS 自动发现。
 * 当 KubeJS 未安装时，此类不会被加载，从而避免类加载失败导致的崩溃。
 * 模组其他代码通过 KubeJSLoader 检测 KubeJS 可用性后，通过反射调用此类的方法。
 *
 * @author Adaptive Nemesis Team
 * @version 1.2.0
 */
public class KubeJSInitializer implements KubeJSPlugin {

    /** 事件组 - 自适应宿敌模组的所有 KubeJS 事件 */
    public static final EventGroup ADAPTIVE_NEMESIS_EVENTS = EventGroup.of("adaptive_nemesis");

    /** 实体强化事件 */
    public static final EventHandler ENTITY_SCALE =
        ADAPTIVE_NEMESIS_EVENTS.server("entity_scale", () -> EntityScaleEventJS.class);

    /** 伤害计算事件 */
    public static final EventHandler DAMAGE_CALCULATION =
        ADAPTIVE_NEMESIS_EVENTS.server("damage_calculation", () -> DamageCalculationEventJS.class);

    /** 玩家强度评估事件 */
    public static final EventHandler PLAYER_STRENGTH_EVALUATION =
        ADAPTIVE_NEMESIS_EVENTS.server("player_strength_evaluation", () -> PlayerStrengthEvaluationEventJS.class);

    /** 世界阶段变化事件 */
    public static final EventHandler WORLD_STAGE_CHANGE =
        ADAPTIVE_NEMESIS_EVENTS.server("world_stage_change", () -> WorldStageChangeEventJS.class);

    /** 宿敌记忆更新事件 */
    public static final EventHandler NEMESIS_MEMORY_UPDATE =
        ADAPTIVE_NEMESIS_EVENTS.server("nemesis_memory_update", () -> NemesisMemoryUpdateEventJS.class);

    /** 入侵开始事件 */
    public static final EventHandler INVASION_START =
        ADAPTIVE_NEMESIS_EVENTS.server("invasion_start", () -> InvasionStartEventJS.class);

    /** 入侵波次开始事件 */
    public static final EventHandler INVASION_WAVE_START =
        ADAPTIVE_NEMESIS_EVENTS.server("invasion_wave_start", () -> InvasionWaveStartEventJS.class);

    /** 入侵结束事件 */
    public static final EventHandler INVASION_END =
        ADAPTIVE_NEMESIS_EVENTS.server("invasion_end", () -> InvasionEndEventJS.class);

    @Override
    public void init() {
        AdaptiveNemesisMod.LOGGER.info("Adaptive Nemesis KubeJS 插件已加载！");
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(ADAPTIVE_NEMESIS_EVENTS);
        AdaptiveNemesisMod.LOGGER.info("Adaptive Nemesis KubeJS 事件已注册");
    }

    /**
     * 触发实体强化事件
     *
     * @param entity 被强化的实体
     * @param multiplier 当前强化倍率
     * @return 事件处理后的倍率，如果取消则返回 -1
     */
    public static double fireEntityScale(Mob entity, double multiplier) {
        try {
            EntityScaleEventJS event = new EntityScaleEventJS(entity, multiplier);
            ENTITY_SCALE.post(event);
            if (event.isEventCancelled()) {
                return -1;
            }
            return event.getMultiplier();
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("触发 KubeJS 实体强化事件失败: {}", e.getMessage());
            return multiplier;
        }
    }

    /**
     * 触发伤害计算事件
     *
     * @param attacker 攻击者
     * @param target 目标
     * @param originalDamage 原始伤害
     * @param calculatedDamage 计算后的伤害
     * @param armorMultiplier 护甲倍率
     * @return 事件处理后的伤害，如果取消则返回原始伤害
     */
    public static float fireDamageCalculation(LivingEntity attacker, LivingEntity target,
                                               float originalDamage, float calculatedDamage,
                                               double armorMultiplier) {
        try {
            DamageCalculationEventJS event = new DamageCalculationEventJS(
                attacker, target, originalDamage, calculatedDamage, armorMultiplier
            );
            DAMAGE_CALCULATION.post(event);
            if (event.isEventCancelled()) {
                return originalDamage;
            }
            return event.getCalculatedDamage();
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("触发 KubeJS 伤害计算事件失败: {}", e.getMessage());
            return calculatedDamage;
        }
    }

    /**
     * 触发玩家强度评估事件
     *
     * @param player 被评估的玩家
     * @param baseStrength 基础强度
     * @param defenseStrength 防御强度
     * @param attackStrength 攻击强度
     * @param magicStrength 魔法强度
     * @param combatStrength 战斗强度
     * @return 事件处理后的最终强度
     */
    public static double firePlayerStrengthEvaluation(ServerPlayer player, double baseStrength,
                                                       double defenseStrength, double attackStrength,
                                                       double magicStrength, double combatStrength) {
        try {
            PlayerStrengthEvaluationEventJS event = new PlayerStrengthEvaluationEventJS(
                player, baseStrength, defenseStrength, attackStrength, magicStrength, combatStrength
            );
            PLAYER_STRENGTH_EVALUATION.post(event);
            return event.getFinalStrength();
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("触发 KubeJS 玩家强度评估事件失败: {}", e.getMessage());
            return baseStrength;
        }
    }

    /**
     * 触发世界阶段变化事件
     *
     * @param player 触发玩家
     * @param oldStage 旧阶段
     * @param newStage 新阶段
     * @param stageMultiplier 阶段倍率
     * @param defeatedBossCount 已击杀 Boss 数量
     * @return 事件处理后的阶段倍率
     */
    public static double fireWorldStageChange(ServerPlayer player, int oldStage, int newStage,
                                               double stageMultiplier, int defeatedBossCount) {
        try {
            WorldStageChangeEventJS event = new WorldStageChangeEventJS(
                player, oldStage, newStage, stageMultiplier, defeatedBossCount
            );
            WORLD_STAGE_CHANGE.post(event);
            if (event.isEventCancelled()) {
                return stageMultiplier;
            }
            return event.getStageMultiplier();
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("触发 KubeJS 世界阶段变化事件失败: {}", e.getMessage());
            return stageMultiplier;
        }
    }

    /**
     * 触发宿敌记忆更新事件
     *
     * @param playerUUID 玩家UUID
     * @param playerName 玩家名称
     * @param profile 宿敌档案
     */
    public static void fireNemesisMemoryUpdate(UUID playerUUID, String playerName,
                                                NemesisProfile profile) {
        try {
            NemesisMemoryUpdateEventJS event = new NemesisMemoryUpdateEventJS(
                playerUUID,
                playerName,
                profile.getTotalKills(),
                profile.getTotalDeaths(),
                profile.getNemesisLevel(),
                profile.getAttackBonus(),
                profile.getSpeedBonus(),
                profile.getHealthBonus()
            );
            NEMESIS_MEMORY_UPDATE.post(event);
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("触发 KubeJS 宿敌记忆更新事件失败: {}", e.getMessage());
        }
    }

    /**
     * 手动触发亡灵入侵事件
     *
     * @param player 目标玩家
     */
    public static void triggerUndeadInvasion(Player player) {
        try {
            if (player != null) {
                InvasionSystem.getInstance().triggerInvasion(player.level(), player, InvasionType.UNDEAD);
            }
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("触发 KubeJS 亡灵入侵事件失败: {}", e.getMessage());
        }
    }

    /**
     * 手动触发亡灵入侵事件（带自定义参数）
     *
     * @param player 目标玩家
     * @param waveCount 波次数量
     * @param difficultyMultiplier 难度倍率
     */
    public static void triggerUndeadInvasion(Player player, int waveCount, double difficultyMultiplier) {
        try {
            if (player != null) {
                InvasionSystem.getInstance().triggerInvasionManual(player, InvasionType.UNDEAD, waveCount, difficultyMultiplier);
            }
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("触发 KubeJS 亡灵入侵事件失败: {}", e.getMessage());
        }
    }

    /**
     * 检查玩家是否正在经历入侵事件
     *
     * @param player 玩家
     * @return 是否在入侵中
     */
    public static boolean isInInvasion(Player player) {
        try {
            if (player == null) {
                return false;
            }
            return InvasionSystem.getInstance().getActiveInvasion(player) != null;
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("KubeJS 检查入侵状态失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取玩家当前的入侵事件进度
     *
     * @param player 玩家
     * @return 进度信息，格式为 "当前波次/总波次"，或 null
     */
    public static String getInvasionProgress(Player player) {
        try {
            if (player == null) {
                return null;
            }
            var invasion = InvasionSystem.getInstance().getActiveInvasion(player);
            if (invasion == null) {
                return null;
            }
            return String.format("%d/%d", invasion.getCurrentWave(), invasion.getTotalWaves());
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("KubeJS 获取入侵进度失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 触发入侵开始事件
     *
     * @param player 触发玩家
     * @param type 入侵类型
     * @param totalWaves 总波次数
     * @param difficultyMultiplier 难度倍率
     * @return 事件处理后的配置数组 [totalWaves, difficultyMultiplier]，取消则返回 null
     */
    public static double[] fireInvasionStart(ServerPlayer player, InvasionSystem.InvasionType type,
                                              int totalWaves, double difficultyMultiplier) {
        try {
            InvasionStartEventJS event = new InvasionStartEventJS(
                player, type, totalWaves, difficultyMultiplier
            );
            INVASION_START.post(event);
            if (event.isEventCancelled()) {
                return null;
            }
            return new double[]{event.getTotalWaves(), event.getDifficultyMultiplier()};
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("触发 KubeJS 入侵开始事件失败: {}", e.getMessage());
            return new double[]{totalWaves, difficultyMultiplier};
        }
    }

    /**
     * 触发入侵波次开始事件
     *
     * @param player 触发玩家
     * @param type 入侵类型
     * @param currentWave 当前波次
     * @param totalWaves 总波次数
     * @param difficultyMultiplier 难度倍率
     */
    public static void fireInvasionWaveStart(ServerPlayer player, InvasionSystem.InvasionType type,
                                              int currentWave, int totalWaves, double difficultyMultiplier) {
        try {
            InvasionWaveStartEventJS event = new InvasionWaveStartEventJS(
                player, type, currentWave, totalWaves, difficultyMultiplier
            );
            INVASION_WAVE_START.post(event);
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("触发 KubeJS 入侵波次开始事件失败: {}", e.getMessage());
        }
    }

    /**
     * 触发入侵结束事件
     *
     * @param player 触发玩家
     * @param type 入侵类型
     * @param victory 是否胜利
     * @param totalWaves 总波次数
     * @param difficultyMultiplier 难度倍率
     * @param wavesCompleted 完成波次数
     * @param rewards 奖励配置
     * @return 可能被 KubeJS 修改过的奖励配置
     */
    public static InvasionRewardData fireInvasionEnd(ServerPlayer player, InvasionSystem.InvasionType type,
                                                      boolean victory, int totalWaves,
                                                      double difficultyMultiplier, int wavesCompleted,
                                                      InvasionRewardData rewards) {
        try {
            InvasionEndEventJS event = new InvasionEndEventJS(
                player, type, victory, totalWaves, difficultyMultiplier, wavesCompleted, rewards
            );
            INVASION_END.post(event);
            return event.getRewards();
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("触发 KubeJS 入侵结束事件失败: {}", e.getMessage());
            return rewards;
        }
    }
}