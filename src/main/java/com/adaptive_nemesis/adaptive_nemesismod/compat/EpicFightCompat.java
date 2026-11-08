package com.adaptive_nemesis.adaptive_nemesismod.compat;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.util.SafeMath;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import yesman.epicfight.registry.entries.EpicFightAttributes;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

/**
 * 史诗战斗 (Epic Fight) 兼容处理器
 *
 * 提供与史诗战斗模组的交互功能：
 * - 获取玩家耐力值
 * - 评估玩家战斗风格
 * - 获取战斗相关属性
 * - 为怪物添加史诗战斗属性加成
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class EpicFightCompat {

    /**
     * 默认构造函数（非空壳）
     * 工具类，方法由外部直接调用（如 EnemyScalingHandler），不持有内部状态，故无需初始化。
     * 保留 public 构造以允许外部实例化。
     */
    public EpicFightCompat() {}

    /**
     * 安全设置属性值 - 防止 NaN/Infinity 导致怪物无法被攻击
     *
     * @param attr 属性实例
     * @param value 要设置的值
     * @param fallback 后备值（NaN/Infinity 时使用）
     */
    private static void safeSetAttribute(AttributeInstance attr, double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            attr.setBaseValue(fallback);
            return;
        }
        attr.setBaseValue(value);
    }

    /**
     * 获取玩家的战斗强度评估值
     *
     * @param player 目标玩家
     * @return 战斗强度值
     */
    public double getPlayerCombatStrength(ServerPlayer player) {
        double strength = 0.0;

        try {
            // 获取玩家的EpicFight能力 - 使用PlayerPatch而不是MobPatch
            var playerPatch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
            if (playerPatch != null) {
                // 获取耐力值 - getStamina()返回float
                float maxStamina = playerPatch.getMaxStamina();
                strength += maxStamina * 0.5;

                // 获取冲击值（impact）- 使用Holder<Attribute>传入
                double impact = player.getAttributeValue(EpicFightAttributes.IMPACT);
                strength += impact * 2.0;

                // 获取护甲穿透
                double armorNegation = player.getAttributeValue(EpicFightAttributes.ARMOR_NEGATION);
                strength += armorNegation * 1.5;
            }

            strength += evaluateWeaponStrength(player.getMainHandItem());

            // 检查护甲（史诗战斗护甲有重量和韧性属性）
            int armorCount = 0;
            for (ItemStack armor : player.getArmorSlots()) {
                if (!armor.isEmpty()) {
                    armorCount++;
                }
            }
            strength += armorCount * 3.0;

        } catch (Exception e) {
            return 0.0;
        }

        return strength;
    }

    /**
     * 评估主手武器的战斗强度贡献
     *
     * 武器分类基于注册表 ID 的子串匹配（F-50）：Item.toString() 的返回格式
     * 无契约保证，注册 ID（如 "epicfight:katana"）才是稳定标识；
     * 命名空间前缀对下方关键词子串匹配无影响，故不做命名空间剥离。
     *
     * @param mainHand 主手物品堆叠
     * @return 武器类型评分
     */
    private double evaluateWeaponStrength(ItemStack mainHand) {
        if (mainHand.isEmpty()) {
            return 0.0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(mainHand.getItem()).toString().toLowerCase();

        // 长剑、太刀等高级武器类型给予更高评分
        if (itemId.contains("katana") || itemId.contains("longsword") ||
            itemId.contains("greatsword") || itemId.contains("spear")) {
            return 15.0;
        }
        if (itemId.contains("sword") || itemId.contains("axe")) {
            return 8.0;
        }
        return 0.0;
    }

    /**
     * 为怪物应用史诗战斗属性加成
     *
     * 按职责拆分为三组（F-41）：防御组（受击抗性/重量/处决抗性）、
     * 进攻组（冲击值/护甲穿透/最大连击）、耐力组（耐力上限/耐力恢复）。
     * 分组应用顺序与旧实现的逐项顺序略有不同，但各组属性相互独立，结果一致。
     *
     * @param mob 目标怪物
     * @param multiplier 强化倍率
     */
    public void applyMobBuffs(Mob mob, double multiplier) {
        try {
            // 倍率语境统一使用 SafeMath（F-47），且下限钳制为 1.0 防止属性变负
            double effectiveMultiplier = SafeMath.safeDouble(Math.max(1.0, multiplier));

            applyDefensiveBuffs(mob, effectiveMultiplier);
            applyOffensiveBuffs(mob, effectiveMultiplier);
            applyStaminaBuffs(mob, effectiveMultiplier);

        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("应用史诗战斗属性加成失败: {}", e.getMessage());
        }
    }

    /**
     * 应用防御组加成：受击抗性、重量、处决抗性
     *
     * @param mob 目标怪物
     * @param multiplier 已做下限保护与 NaN 防护的强化倍率
     */
    private void applyDefensiveBuffs(Mob mob, double multiplier) {
        // 1. 增加受击抗性 (Stun Armor) - 防止被无限硬直
        // NeoForge 1.21.1: DeferredHolder可以直接作为Holder<Attribute>传入
        AttributeInstance stunArmorAttr = mob.getAttribute(EpicFightAttributes.STUN_ARMOR);
        if (stunArmorAttr != null) {
            double original = stunArmorAttr.getBaseValue();
            double updated = original + (multiplier - 1.0) * 5.0;
            safeSetAttribute(stunArmorAttr, Math.max(original, updated), original);

            logAttributeChange(mob, "受击抗性", original, updated);
        }

        // 2. 增加重量 (Weight) - 更难被击退，至少保证基础抗性
        AttributeInstance weightAttr = mob.getAttribute(EpicFightAttributes.WEIGHT);
        if (weightAttr != null) {
            double original = weightAttr.getBaseValue();
            double minWeight = original + Config.WEIGHT_MIN_BONUS.get();
            double updated = original + (multiplier - 1.0) * Config.WEIGHT_PER_MULTIPLIER.get();
            safeSetAttribute(weightAttr, Math.max(minWeight, updated), minWeight);

            logAttributeChange(mob, "重量", original, updated);
        }

        // 3. 增加处决抗性 (Execution Resistance) - 防止被处决
        AttributeInstance execResistAttr = mob.getAttribute(EpicFightAttributes.ASSASSINATION_RESISTANCE);
        if (execResistAttr != null) {
            double original = execResistAttr.getBaseValue();
            double updated = original + (multiplier - 1.0) * 1.0;
            safeSetAttribute(execResistAttr, Math.max(original, updated), original);
        }
    }

    /**
     * 应用进攻组加成：冲击值、护甲穿透、最大连击数
     *
     * @param mob 目标怪物
     * @param multiplier 已做下限保护与 NaN 防护的强化倍率
     */
    private void applyOffensiveBuffs(Mob mob, double multiplier) {
        // 1. 增加冲击值 (Impact) - 更容易打断玩家攻击
        AttributeInstance impactAttr = mob.getAttribute(EpicFightAttributes.IMPACT);
        if (impactAttr != null) {
            double original = impactAttr.getBaseValue();
            double updated = original * (1.0 + (multiplier - 1.0) * 0.3);
            safeSetAttribute(impactAttr, Math.max(original, updated), original);

            logAttributeChange(mob, "冲击值", original, updated);
        }

        // 2. 增加护甲穿透 (Armor Negation) - 无视部分护甲（最高100%）
        AttributeInstance armorNegationAttr = mob.getAttribute(EpicFightAttributes.ARMOR_NEGATION);
        if (armorNegationAttr != null) {
            double original = armorNegationAttr.getBaseValue();
            double updated = original + (multiplier - 1.0) * 2.0;
            safeSetAttribute(armorNegationAttr, Math.min(updated, 100.0), original);

            logAttributeChange(mob, "护甲穿透", original, updated);
        }

        // 3. 增加最大连击数 (Max Strikes) - 可以连续攻击更多次
        AttributeInstance maxStrikesAttr = mob.getAttribute(EpicFightAttributes.MAX_STRIKES);
        if (maxStrikesAttr != null) {
            double original = maxStrikesAttr.getBaseValue();
            double updated = original + (multiplier - 1.0) * 0.5;
            safeSetAttribute(maxStrikesAttr, Math.max(original, updated), original);

            logAttributeChange(mob, "最大连击", original, updated);
        }
    }

    /**
     * 应用耐力组加成：耐力上限、耐力恢复
     *
     * @param mob 目标怪物
     * @param multiplier 已做下限保护与 NaN 防护的强化倍率
     */
    private void applyStaminaBuffs(Mob mob, double multiplier) {
        // 1. 增加耐力值 (Max Stamina) - 更多耐力进行战斗
        AttributeInstance maxStaminaAttr = mob.getAttribute(EpicFightAttributes.MAX_STAMINA);
        if (maxStaminaAttr != null) {
            double original = maxStaminaAttr.getBaseValue();
            double updated = original * multiplier;
            safeSetAttribute(maxStaminaAttr, Math.max(original, updated), original);

            logAttributeChange(mob, "耐力", original, updated);
        }

        // 2. 增加耐力恢复 (Stamina Regen)
        AttributeInstance staminaRegenAttr = mob.getAttribute(EpicFightAttributes.STAMINA_REGEN);
        if (staminaRegenAttr != null) {
            double original = staminaRegenAttr.getBaseValue();
            double updated = original * (1.0 + (multiplier - 1.0) * 0.5);
            safeSetAttribute(staminaRegenAttr, Math.max(original, updated), original);
        }
    }

    /**
     * 输出属性变更的调试日志
     *
     * 仅在 ENABLE_DEBUG_LOG 配置开启时输出。
     *
     * @param mob 目标怪物（取名称用于日志展示）
     * @param attrName 属性显示名称
     * @param original 变更前的值
     * @param updated 变更后的目标值
     */
    private static void logAttributeChange(Mob mob, String attrName, double original, double updated) {
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "怪物 {} {}: {} -> {}",
                mob.getName().getString(),
                attrName,
                String.format("%.2f", original),
                String.format("%.2f", updated)
            );
        }
    }

    /**
     * 检查玩家是否处于史诗战斗模式
     *
     * @param player 目标玩家
     * @return 如果处于战斗模式返回true
     */
    public boolean isInBattleMode(ServerPlayer player) {
        try {
            var playerPatch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
            if (playerPatch != null) {
                return playerPatch.isEpicFightMode();
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取玩家当前耐力值
     *
     * @param player 目标玩家
     * @return 当前耐力值
     */
    public double getPlayerStamina(ServerPlayer player) {
        try {
            var playerPatch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
            if (playerPatch != null) {
                return playerPatch.getStamina();
            }
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * 获取玩家最大耐力值
     *
     * @param player 目标玩家
     * @return 最大耐力值
     */
    public double getPlayerMaxStamina(ServerPlayer player) {
        try {
            var playerPatch = EpicFightCapabilities.getEntityPatch(player, PlayerPatch.class);
            if (playerPatch != null) {
                return playerPatch.getMaxStamina();
            }
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }
}
