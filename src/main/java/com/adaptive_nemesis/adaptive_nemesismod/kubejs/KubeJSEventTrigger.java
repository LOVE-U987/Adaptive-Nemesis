package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisProfile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * KubeJS 事件触发器
 *
 * 负责在模组的关键位置触发 KubeJS 事件。
 * 提供静态方法供其他系统调用，自动处理 KubeJS 是否加载的检查。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class KubeJSEventTrigger {

    /**
     * 触发实体强化事件
     *
     * @param entity 被强化的实体
     * @param multiplier 当前强化倍率
     * @return 事件处理后的倍率，如果取消则返回 -1
     */
    public static double triggerEntityScale(Mob entity, double multiplier) {
        if (!AdaptiveNemesisKubeJSPlugin.isKubeJSLoaded()) {
            return multiplier;
        }

        try {
            EntityScaleEventJS event = new EntityScaleEventJS(entity, multiplier);
            var result = AdaptiveNemesisKubeJSPlugin.ENTITY_SCALE.post(event);

            if (event.isEventCancelled()) {
                return -1; // 表示取消强化
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
    public static float triggerDamageCalculation(LivingEntity attacker, LivingEntity target,
                                                  float originalDamage, float calculatedDamage,
                                                  double armorMultiplier) {
        if (!AdaptiveNemesisKubeJSPlugin.isKubeJSLoaded()) {
            return calculatedDamage;
        }

        try {
            DamageCalculationEventJS event = new DamageCalculationEventJS(
                attacker, target, originalDamage, calculatedDamage, armorMultiplier
            );
            var result = AdaptiveNemesisKubeJSPlugin.DAMAGE_CALCULATION.post(event);

            if (event.isEventCancelled()) {
                return originalDamage; // 取消真实伤害，返回原始伤害
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
    public static double triggerPlayerStrengthEvaluation(ServerPlayer player, double baseStrength,
                                                          double defenseStrength, double attackStrength,
                                                          double magicStrength, double combatStrength) {
        if (!AdaptiveNemesisKubeJSPlugin.isKubeJSLoaded()) {
            return baseStrength;
        }

        try {
            PlayerStrengthEvaluationEventJS event = new PlayerStrengthEvaluationEventJS(
                player, baseStrength, defenseStrength, attackStrength, magicStrength, combatStrength
            );
            var result = AdaptiveNemesisKubeJSPlugin.PLAYER_STRENGTH_EVALUATION.post(event);

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
    public static void triggerNemesisMemoryUpdate(java.util.UUID playerUUID, String playerName,
                                                   NemesisProfile profile) {
        if (!AdaptiveNemesisKubeJSPlugin.isKubeJSLoaded()) {
            return;
        }

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
            var result = AdaptiveNemesisKubeJSPlugin.NEMESIS_MEMORY_UPDATE.post(event);
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("触发 KubeJS 宿敌记忆更新事件失败: {}", e.getMessage());
        }
    }

    /**
     * 检查 KubeJS 是否已加载
     *
     * @return 如果 KubeJS 已加载返回 true
     */
    public static boolean isKubeJSActive() {
        return AdaptiveNemesisKubeJSPlugin.isKubeJSLoaded();
    }
}
