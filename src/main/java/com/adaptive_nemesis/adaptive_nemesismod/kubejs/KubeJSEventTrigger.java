package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisProfile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * KubeJS 事件触发器
 *
 * 负责在模组的关键位置触发 KubeJS 事件。
 * 通过反射安全调用 KubeJSInitializer，即使 KubeJS 未安装也不会崩溃。
 *
 * @author Adaptive Nemesis Team
 * @version 1.2.0
 */
public class KubeJSEventTrigger {

    private static final String INITIALIZER_CLASS = "com.adaptive_nemesis.adaptive_nemesismod.kubejs.KubeJSInitializer";
    private static Boolean initializerAvailable = null;

    /**
     * 通过反射调用 KubeJSInitializer 的静态方法
     *
     * @param methodName 方法名
     * @param paramTypes 参数类型数组
     * @param args 参数值数组
     * @return 方法返回值，失败时返回 null
     */
    private static Object invokeStatic(String methodName, Class<?>[] paramTypes, Object[] args) {
        if (!KubeJSLoader.isKubeJSLoaded()) return null;
        if (initializerAvailable == null) {
            try {
                Class.forName(INITIALIZER_CLASS);
                initializerAvailable = true;
            } catch (ClassNotFoundException e) {
                initializerAvailable = false;
                return null;
            }
        }
        if (!initializerAvailable) return null;

        try {
            Class<?> clazz = Class.forName(INITIALIZER_CLASS);
            Method method = clazz.getMethod(methodName, paramTypes);
            return method.invoke(null, args);
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.warn("反射调用 {} 失败: {}", methodName, e.getMessage());
            return null;
        }
    }

    /**
     * 触发实体强化事件
     *
     * @param entity 被强化的实体
     * @param multiplier 当前强化倍率
     * @return 事件处理后的倍率，如果取消则返回 -1
     */
    public static double triggerEntityScale(Mob entity, double multiplier) {
        if (!KubeJSLoader.isKubeJSLoaded()) {
            return multiplier;
        }

        try {
            Object result = invokeStatic("fireEntityScale",
                new Class<?>[]{Mob.class, double.class},
                new Object[]{entity, multiplier});
            if (result instanceof Double) {
                return (Double) result;
            }
        } catch (Exception e) {
            // fall through
        }
        return multiplier;
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
        if (!KubeJSLoader.isKubeJSLoaded()) {
            return calculatedDamage;
        }

        try {
            Object result = invokeStatic("fireDamageCalculation",
                new Class<?>[]{LivingEntity.class, LivingEntity.class, float.class, float.class, double.class},
                new Object[]{attacker, target, originalDamage, calculatedDamage, armorMultiplier});
            if (result instanceof Float) {
                return (Float) result;
            }
        } catch (Exception e) {
            // fall through
        }
        return calculatedDamage;
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
        if (!KubeJSLoader.isKubeJSLoaded()) {
            return baseStrength;
        }

        try {
            Object result = invokeStatic("firePlayerStrengthEvaluation",
                new Class<?>[]{ServerPlayer.class, double.class, double.class, double.class, double.class, double.class},
                new Object[]{player, baseStrength, defenseStrength, attackStrength, magicStrength, combatStrength});
            if (result instanceof Double) {
                return (Double) result;
            }
        } catch (Exception e) {
            // fall through
        }
        return baseStrength;
    }

    /**
     * 触发宿敌记忆更新事件
     *
     * @param playerUUID 玩家UUID
     * @param playerName 玩家名称
     * @param profile 宿敌档案
     */
    public static void triggerNemesisMemoryUpdate(UUID playerUUID, String playerName,
                                                   NemesisProfile profile) {
        if (!KubeJSLoader.isKubeJSLoaded()) {
            return;
        }

        try {
            invokeStatic("fireNemesisMemoryUpdate",
                new Class<?>[]{UUID.class, String.class, NemesisProfile.class},
                new Object[]{playerUUID, playerName, profile});
        } catch (Exception e) {
            // ignore
        }
    }

    /**
     * 检查 KubeJS 是否已加载
     *
     * @return 如果 KubeJS 已加载返回 true
     */
    public static boolean isKubeJSActive() {
        return KubeJSLoader.isKubeJSLoaded();
    }
}