package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisProfile;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

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

    /** 宿敌记忆更新事件 */
    public static final EventHandler NEMESIS_MEMORY_UPDATE =
        ADAPTIVE_NEMESIS_EVENTS.server("nemesis_memory_update", () -> NemesisMemoryUpdateEventJS.class);

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
}