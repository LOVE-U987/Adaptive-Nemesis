package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionRewardData;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionSystem;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisProfile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KubeJS 事件触发器
 *
 * 负责在模组的关键位置触发 KubeJS 事件。
 * 通过反射安全调用 KubeJSInitializer，即使 KubeJS 未安装也不会崩溃。
 *
 * 调用失败加固（F-51）：
 * - 方法缓存键包含参数类型签名，避免同名不同参方法互相覆盖；
 * - 单个事件反射调用连续失败达到上限后，本会话禁用该事件，
 *   并一次性输出包含完整堆栈的错误日志（不再逐次 warn 刷屏）。
 *
 * @author Adaptive Nemesis Team
 * @version 1.3.0
 */
public class KubeJSEventTrigger {

    private static final String INITIALIZER_CLASS = "com.adaptive_nemesis.adaptive_nemesismod.kubejs.KubeJSInitializer";

    /**
     * 单个事件反射调用连续失败次数上限，达到后本会话禁用该事件
     */
    private static final int MAX_INVOKE_FAILURES = 3;

    private static Boolean initializerAvailable = null;

    /**
     * 反射方法缓存 - 避免每次调用都执行 Class.forName + getMethod；
     * 键为"方法名 + 参数类型签名"（见 {@link #cacheKey}）
     */
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();

    /**
     * 各事件的反射调用连续失败计数（键同 METHOD_CACHE）
     */
    private static final Map<String, Integer> FAILURE_COUNTS = new ConcurrentHashMap<>();

    /**
     * 本会话已被禁用的事件缓存键集合
     */
    private static final Set<String> DISABLED_EVENTS = ConcurrentHashMap.newKeySet();

    /**
     * 生成方法缓存键：方法名 + 参数类型签名（F-51），
     * 避免同名不同参的方法在缓存中互相覆盖导致调用错位
     *
     * @param methodName 方法名
     * @param paramTypes 参数类型数组
     * @return 缓存键，形如 "fireEntityScale#net.minecraft...Mob#double"
     */
    private static String cacheKey(String methodName, Class<?>[] paramTypes) {
        StringBuilder key = new StringBuilder(methodName);
        for (Class<?> type : paramTypes) {
            key.append('#').append(type.getName());
        }
        return key.toString();
    }

    /**
     * 通过反射调用 KubeJSInitializer 的静态方法
     * 使用 METHOD_CACHE 缓存 Method 对象以提升性能；
     * 连续失败达到上限的事件在本会话内被禁用（直接短路返回）
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

        String cacheKey = cacheKey(methodName, paramTypes);
        if (DISABLED_EVENTS.contains(cacheKey)) {
            return null;
        }

        try {
            Method method = METHOD_CACHE.get(cacheKey);
            if (method == null) {
                Class<?> clazz = Class.forName(INITIALIZER_CLASS);
                method = clazz.getMethod(methodName, paramTypes);
                METHOD_CACHE.put(cacheKey, method);
            }
            return method.invoke(null, args);
        } catch (Exception e) {
            onInvokeFailure(cacheKey, methodName, e);
            return null;
        }
    }

    /**
     * 反射调用失败处理（F-51）
     *
     * 每次失败累加计数并输出 debug 日志；连续失败达到
     * {@link #MAX_INVOKE_FAILURES} 后将事件加入本会话禁用集合，
     * 并一次性输出包含完整堆栈（含目标方法真实异常原因）的 error 日志，
     * 替代原先每次失败都 warn 的刷屏行为。
     *
     * @param cacheKey 事件缓存键
     * @param methodName 方法名（日志展示用）
     * @param e 反射调用抛出的异常
     */
    private static void onInvokeFailure(String cacheKey, String methodName, Exception e) {
        int failures = FAILURE_COUNTS.merge(cacheKey, 1, Integer::sum);
        if (failures < MAX_INVOKE_FAILURES) {
            AdaptiveNemesisMod.LOGGER.debug("反射调用 {} 失败（第 {} 次）: {}", methodName, failures, e.getMessage());
            return;
        }
        DISABLED_EVENTS.add(cacheKey);
        // 解包 InvocationTargetException，输出目标方法内部的真实异常堆栈
        Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
        AdaptiveNemesisMod.LOGGER.error(
            "KubeJS 事件 {} 反射调用连续失败 {} 次，本会话禁用该事件", methodName, failures, cause);
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
     * 取消标记由 {@link DamageCalculationResult#isCancelled()} 显式承载，
     * 不再以"返回值等于原始伤害"作为取消约定：脚本把真实伤害金额设为
     * 与原始伤害相同的值时仍是正常修改，可按全额真实伤害转化。
     *
     * @param attacker 攻击者
     * @param target 目标
     * @param originalDamage 原始伤害
     * @param calculatedDamage 计算后的伤害
     * @param armorMultiplier 护甲倍率
     * @return 伤害计算事件结果（含取消标记与事件处理后的真实伤害金额）
     */
    public static DamageCalculationResult triggerDamageCalculation(LivingEntity attacker, LivingEntity target,
                                                                    float originalDamage, float calculatedDamage,
                                                                    double armorMultiplier) {
        DamageCalculationResult fallback = new DamageCalculationResult(false, calculatedDamage);
        if (!KubeJSLoader.isKubeJSLoaded()) {
            return fallback;
        }

        try {
            Object result = invokeStatic("fireDamageCalculation",
                new Class<?>[]{LivingEntity.class, LivingEntity.class, float.class, float.class, double.class},
                new Object[]{attacker, target, originalDamage, calculatedDamage, armorMultiplier});
            if (result instanceof DamageCalculationResult damageResult) {
                return damageResult;
            }
        } catch (Exception e) {
            // fall through
        }
        return fallback;
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
     * 触发世界阶段变化事件
     *
     * @param player 触发玩家
     * @param oldStage 旧阶段
     * @param newStage 新阶段
     * @param stageMultiplier 阶段倍率
     * @param defeatedBossCount 已击杀 Boss 数量
     * @return 事件处理后的阶段倍率
     */
    public static double triggerWorldStageChange(ServerPlayer player, int oldStage, int newStage,
                                                  double stageMultiplier, int defeatedBossCount) {
        if (!KubeJSLoader.isKubeJSLoaded()) {
            return stageMultiplier;
        }

        try {
            Object result = invokeStatic("fireWorldStageChange",
                new Class<?>[]{ServerPlayer.class, int.class, int.class, double.class, int.class},
                new Object[]{player, oldStage, newStage, stageMultiplier, defeatedBossCount});
            if (result instanceof Double) {
                return (Double) result;
            }
        } catch (Exception e) {
            // fall through
        }
        return stageMultiplier;
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

    /**
     * 触发入侵开始事件
     *
     * @param player 触发玩家
     * @param type 入侵类型
     * @param totalWaves 总波次数
     * @param difficultyMultiplier 难度倍率
     * @return 事件处理后的配置，如果取消则返回 null
     */
    public static InvasionStartResult triggerInvasionStart(ServerPlayer player, InvasionSystem.InvasionType type,
                                                            int totalWaves, double difficultyMultiplier) {
        if (!KubeJSLoader.isKubeJSLoaded()) {
            return new InvasionStartResult(totalWaves, difficultyMultiplier);
        }

        try {
            Object result = invokeStatic("fireInvasionStart",
                new Class<?>[]{ServerPlayer.class, InvasionSystem.InvasionType.class, int.class, double.class},
                new Object[]{player, type, totalWaves, difficultyMultiplier});
            // 返回 null 表示 KubeJS 事件取消了入侵
            if (result == null) {
                return null;
            }
            // 处理 double[] 数组返回值
            if (result instanceof double[] values) {
                if (values.length >= 2) {
                    return new InvasionStartResult((int) values[0], values[1]);
                }
                AdaptiveNemesisMod.LOGGER.warn(
                    "KubeJS 入侵开始事件返回的 double[] 长度不足 ({} < 2)，使用默认配置", values.length
                );
                return new InvasionStartResult(totalWaves, difficultyMultiplier);
            }
            // 其他类型均视为意外返回，使用默认配置而非取消入侵
            AdaptiveNemesisMod.LOGGER.warn(
                "KubeJS 入侵开始事件返回了意外类型 {}，使用默认配置", result.getClass().getName()
            );
            return new InvasionStartResult(totalWaves, difficultyMultiplier);
        } catch (Exception e) {
            return new InvasionStartResult(totalWaves, difficultyMultiplier);
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
    public static void triggerInvasionWaveStart(ServerPlayer player, InvasionSystem.InvasionType type,
                                                 int currentWave, int totalWaves, double difficultyMultiplier) {
        if (!KubeJSLoader.isKubeJSLoaded()) {
            return;
        }

        try {
            invokeStatic("fireInvasionWaveStart",
                new Class<?>[]{ServerPlayer.class, InvasionSystem.InvasionType.class, int.class, int.class, double.class},
                new Object[]{player, type, currentWave, totalWaves, difficultyMultiplier});
        } catch (Exception e) {
            // ignore
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
    public static InvasionRewardData triggerInvasionEnd(ServerPlayer player, InvasionSystem.InvasionType type,
                                                         boolean victory, int totalWaves,
                                                         double difficultyMultiplier, int wavesCompleted,
                                                         InvasionRewardData rewards) {
        if (!KubeJSLoader.isKubeJSLoaded()) {
            return rewards;
        }

        try {
            Object result = invokeStatic("fireInvasionEnd",
                new Class<?>[]{ServerPlayer.class, InvasionSystem.InvasionType.class, boolean.class,
                    int.class, double.class, int.class, InvasionRewardData.class},
                new Object[]{player, type, victory, totalWaves, difficultyMultiplier, wavesCompleted, rewards});
            if (result instanceof InvasionRewardData modifiedRewards) {
                return modifiedRewards;
            }
            return rewards;
        } catch (Exception e) {
            return rewards;
        }
    }

    /**
     * 伤害计算事件结果
     *
     * 以独立的取消标记承载 KubeJS 脚本的取消意图，替代旧实现
     * "返回值等于原始伤害即视为取消"的隐式约定。脚本把真实伤害
     * 设为与原始伤害相同的值表示全额真实伤害，不会被误判为取消。
     */
    public static final class DamageCalculationResult {

        /**
         * 是否取消真实伤害转换
         */
        private final boolean cancelled;

        /**
         * 事件处理后的真实伤害金额
         */
        private final float damage;

        /**
         * 构造伤害计算事件结果
         *
         * @param cancelled 是否取消真实伤害转换
         * @param damage 事件处理后的真实伤害金额
         */
        public DamageCalculationResult(boolean cancelled, float damage) {
            this.cancelled = cancelled;
            this.damage = damage;
        }

        /**
         * 检查事件是否被取消
         *
         * @return 如果脚本调用了 cancelEvent() 返回 true
         */
        public boolean isCancelled() {
            return cancelled;
        }

        /**
         * 获取事件处理后的真实伤害金额
         *
         * @return 真实伤害金额（cancelled 为 true 时该值不参与计算）
         */
        public float getDamage() {
            return damage;
        }
    }

    /**
     * 入侵开始事件结果
     */
    public static class InvasionStartResult {
        public final int totalWaves;
        public final double difficultyMultiplier;

        public InvasionStartResult(int totalWaves, double difficultyMultiplier) {
            this.totalWaves = totalWaves;
            this.difficultyMultiplier = difficultyMultiplier;
        }
    }
}