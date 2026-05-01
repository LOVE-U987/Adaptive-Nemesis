package com.adaptive_nemesis.adaptive_nemesismod.compat;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

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
 * @version 1.0.0
 */
public class EpicFightCompat {

    /**
     * 默认构造函数
     */
    public EpicFightCompat() {}

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
                float stamina = playerPatch.getStamina();
                float maxStamina = playerPatch.getMaxStamina();
                strength += maxStamina * 0.5;

                // 获取冲击值（impact）- 使用Holder<Attribute>传入
                double impact = player.getAttributeValue(EpicFightAttributes.IMPACT);
                strength += impact * 2.0;

                // 获取护甲穿透
                double armorNegation = player.getAttributeValue(EpicFightAttributes.ARMOR_NEGATION);
                strength += armorNegation * 1.5;
            }

            // 评估武器类型（史诗战斗对武器类型有特殊处理）
            ItemStack mainHand = player.getMainHandItem();
            if (!mainHand.isEmpty()) {
                String itemId = mainHand.getItem().toString();

                // 长剑、太刀等高级武器类型给予更高评分
                if (itemId.contains("katana") || itemId.contains("longsword") ||
                    itemId.contains("greatsword") || itemId.contains("spear")) {
                    strength += 15.0;
                } else if (itemId.contains("sword") || itemId.contains("axe")) {
                    strength += 8.0;
                }
            }

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
     * 为怪物应用史诗战斗属性加成
     *
     * @param mob 目标怪物
     * @param multiplier 强化倍率
     */
    public void applyMobBuffs(Mob mob, double multiplier) {
        try {
            // 1. 增加受击抗性 (Stun Armor) - 防止被无限硬直
            // NeoForge 1.21.1: DeferredHolder可以直接作为Holder<Attribute>传入
            AttributeInstance stunArmorAttr = mob.getAttribute(EpicFightAttributes.STUN_ARMOR);
            if (stunArmorAttr != null) {
                double originalStunArmor = stunArmorAttr.getBaseValue();
                // 受击抗性随倍率增加，最高增加大量
                double newStunArmor = originalStunArmor + (multiplier - 1.0) * 5.0;
                stunArmorAttr.setBaseValue(newStunArmor);

                AdaptiveNemesisMod.LOGGER.debug(
                    "怪物 {} 受击抗性: {} -> {}",
                    mob.getName().getString(),
                    String.format("%.2f", originalStunArmor),
                    String.format("%.2f", newStunArmor)
                );
            }

            // 2. 增加冲击值 (Impact) - 更容易打断玩家攻击
            AttributeInstance impactAttr = mob.getAttribute(EpicFightAttributes.IMPACT);
            if (impactAttr != null) {
                double originalImpact = impactAttr.getBaseValue();
                double newImpact = originalImpact * (1.0 + (multiplier - 1.0) * 0.3);
                impactAttr.setBaseValue(newImpact);

                AdaptiveNemesisMod.LOGGER.debug(
                    "怪物 {} 冲击值: {} -> {}",
                    mob.getName().getString(),
                    String.format("%.2f", originalImpact),
                    String.format("%.2f", newImpact)
                );
            }

            // 3. 增加护甲穿透 (Armor Negation) - 无视部分护甲
            AttributeInstance armorNegationAttr = mob.getAttribute(EpicFightAttributes.ARMOR_NEGATION);
            if (armorNegationAttr != null) {
                double originalNegation = armorNegationAttr.getBaseValue();
                double newNegation = originalNegation + (multiplier - 1.0) * 2.0;
                armorNegationAttr.setBaseValue(Math.min(newNegation, 100.0)); // 最高100%

                AdaptiveNemesisMod.LOGGER.debug(
                    "怪物 {} 护甲穿透: {} -> {}",
                    mob.getName().getString(),
                    String.format("%.2f", originalNegation),
                    String.format("%.2f", newNegation)
                );
            }

            // 4. 增加最大连击数 (Max Strikes) - 可以连续攻击更多次
            AttributeInstance maxStrikesAttr = mob.getAttribute(EpicFightAttributes.MAX_STRIKES);
            if (maxStrikesAttr != null) {
                double originalStrikes = maxStrikesAttr.getBaseValue();
                double newStrikes = originalStrikes + (multiplier - 1.0) * 0.5;
                maxStrikesAttr.setBaseValue(newStrikes);

                AdaptiveNemesisMod.LOGGER.debug(
                    "怪物 {} 最大连击: {} -> {}",
                    mob.getName().getString(),
                    String.format("%.2f", originalStrikes),
                    String.format("%.2f", newStrikes)
                );
            }

            // 5. 增加重量 (Weight) - 更难被击退
            AttributeInstance weightAttr = mob.getAttribute(EpicFightAttributes.WEIGHT);
            if (weightAttr != null) {
                double originalWeight = weightAttr.getBaseValue();
                double newWeight = originalWeight + (multiplier - 1.0) * 10.0;
                weightAttr.setBaseValue(newWeight);

                AdaptiveNemesisMod.LOGGER.debug(
                    "怪物 {} 重量: {} -> {}",
                    mob.getName().getString(),
                    String.format("%.2f", originalWeight),
                    String.format("%.2f", newWeight)
                );
            }

            // 6. 增加耐力值 (Stamina) - 更多耐力进行战斗
            AttributeInstance maxStaminaAttr = mob.getAttribute(EpicFightAttributes.MAX_STAMINA);
            if (maxStaminaAttr != null) {
                double originalStamina = maxStaminaAttr.getBaseValue();
                double newStamina = originalStamina * multiplier;
                maxStaminaAttr.setBaseValue(newStamina);

                AdaptiveNemesisMod.LOGGER.debug(
                    "怪物 {} 耐力: {} -> {}",
                    mob.getName().getString(),
                    String.format("%.2f", originalStamina),
                    String.format("%.2f", newStamina)
                );
            }

            // 7. 增加耐力恢复 (Stamina Regen)
            AttributeInstance staminaRegenAttr = mob.getAttribute(EpicFightAttributes.STAMINA_REGEN);
            if (staminaRegenAttr != null) {
                double originalRegen = staminaRegenAttr.getBaseValue();
                double newRegen = originalRegen * (1.0 + (multiplier - 1.0) * 0.5);
                staminaRegenAttr.setBaseValue(newRegen);
            }

            // 8. 增加处决抗性 (Execution Resistance) - 防止被处决
            AttributeInstance executionResistAttr = mob.getAttribute(EpicFightAttributes.ASSASSINATION_RESISTANCE);
            if (executionResistAttr != null) {
                double originalResist = executionResistAttr.getBaseValue();
                double newResist = originalResist + (multiplier - 1.0) * 1.0;
                executionResistAttr.setBaseValue(newResist);
            }

        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("应用史诗战斗属性加成失败: {}", e.getMessage());
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
