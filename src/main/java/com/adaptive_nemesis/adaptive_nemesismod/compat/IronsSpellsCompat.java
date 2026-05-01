package com.adaptive_nemesis.adaptive_nemesismod.compat;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;

/**
 * 铁魔法 (Iron's Spells 'n Spellbooks) 兼容处理器
 *
 * 提供与铁魔法模组的交互功能：
 * - 获取玩家法力值
 * - 获取玩家法术强度
 * - 评估玩家法术装备
 * - 为怪物添加铁魔法属性加成
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class IronsSpellsCompat {

    /**
     * 默认构造函数
     */
    public IronsSpellsCompat() {}

    /**
     * 获取玩家的法术强度评估值
     *
     * @param player 目标玩家
     * @return 法术强度值
     */
    public double getPlayerSpellStrength(ServerPlayer player) {
        double strength = 0.0;

        try {
            // 获取玩家魔法数据
            MagicData magicData = MagicData.getPlayerMagicData(player);

            if (magicData != null) {
                // 法力值贡献 - 使用getMana()作为近似值
                double maxMana = magicData.getMana(); // 当前法力值作为参考
                strength += maxMana * 0.05;

                // 尝试获取法术强度（通过其他方式）
                // 1.21 API可能不同，这里使用安全的方式
                try {
                    // 如果有getSpellPower方法则使用
                    // strength += magicData.getSpellPower() * 2.0;
                } catch (Exception ignored) {}
            }

            // 检查装备中的法术书
            ItemStack mainHand = player.getMainHandItem();
            ItemStack offHand = player.getOffhandItem();

            strength += evaluateSpellItem(mainHand);
            strength += evaluateSpellItem(offHand);

            // 检查护甲上的法术相关属性
            for (ItemStack armor : player.getArmorSlots()) {
                strength += evaluateSpellItem(armor);
            }

        } catch (Exception e) {
            // 如果铁魔法API调用失败，返回基础值
            return 0.0;
        }

        return strength;
    }

    /**
     * 评估物品中的法术相关强度
     *
     * @param stack 物品堆叠
     * @return 法术强度贡献值
     */
    private double evaluateSpellItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }

        double value = 0.0;

        // 检查是否是法术书
        if (stack.getItem() instanceof SpellBook) {
            value += 10.0;
        }

        // 检查物品ID是否包含法术相关关键词
        String itemId = stack.getItem().toString().toLowerCase();
        if (itemId.contains("ring") || itemId.contains("amulet") || itemId.contains("talisman")) {
            value += 3.0;
        }
        if (itemId.contains("mana") || itemId.contains("spell") || itemId.contains("magic")) {
            value += 5.0;
        }

        return value;
    }

    /**
     * 为怪物应用铁魔法属性加成
     *
     * @param mob 目标怪物
     * @param multiplier 强化倍率
     */
    public void applyMobBuffs(Mob mob, double multiplier) {
        try {
            // 1. 增加法术强度 (Spell Power) - 如果有法术攻击
            // NeoForge 1.21.1: DeferredHolder可以直接作为Holder<Attribute>传入
            AttributeInstance spellPowerAttr = mob.getAttribute(AttributeRegistry.SPELL_POWER);
            if (spellPowerAttr != null) {
                double originalPower = spellPowerAttr.getBaseValue();
                double newPower = originalPower * multiplier;
                spellPowerAttr.setBaseValue(newPower);

                AdaptiveNemesisMod.LOGGER.debug(
                    "怪物 {} 法术强度: {} -> {}",
                    mob.getName().getString(),
                    String.format("%.2f", originalPower),
                    String.format("%.2f", newPower)
                );
            }

            // 2. 增加法力值 (Max Mana)
            AttributeInstance maxManaAttr = mob.getAttribute(AttributeRegistry.MAX_MANA);
            if (maxManaAttr != null) {
                double originalMana = maxManaAttr.getBaseValue();
                double newMana = originalMana * multiplier;
                maxManaAttr.setBaseValue(newMana);

                AdaptiveNemesisMod.LOGGER.debug(
                    "怪物 {} 法力值: {} -> {}",
                    mob.getName().getString(),
                    String.format("%.2f", originalMana),
                    String.format("%.2f", newMana)
                );
            }

            // 3. 增加法力恢复 (Mana Regen)
            AttributeInstance manaRegenAttr = mob.getAttribute(AttributeRegistry.MANA_REGEN);
            if (manaRegenAttr != null) {
                double originalRegen = manaRegenAttr.getBaseValue();
                double newRegen = originalRegen * (1.0 + (multiplier - 1.0) * 0.5);
                manaRegenAttr.setBaseValue(newRegen);
            }

            // 4. 增加冷却缩减 (Cooldown Reduction)
            AttributeInstance cooldownAttr = mob.getAttribute(AttributeRegistry.COOLDOWN_REDUCTION);
            if (cooldownAttr != null) {
                double originalCooldown = cooldownAttr.getBaseValue();
                double newCooldown = originalCooldown * (1.0 + (multiplier - 1.0) * 0.3);
                cooldownAttr.setBaseValue(newCooldown);
            }

            // 5. 增加施法时间缩减 (Cast Time Reduction)
            AttributeInstance castTimeAttr = mob.getAttribute(AttributeRegistry.CAST_TIME_REDUCTION);
            if (castTimeAttr != null) {
                double originalCastTime = castTimeAttr.getBaseValue();
                double newCastTime = originalCastTime * (1.0 + (multiplier - 1.0) * 0.3);
                castTimeAttr.setBaseValue(newCastTime);
            }

            // 6. 增加各系魔法抗性
            applyMagicResistance(mob, multiplier);

            // 7. 增加各系法术强度
            applySpellPower(mob, multiplier);

        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("应用铁魔法属性加成失败: {}", e.getMessage());
        }
    }

    /**
     * 应用魔法抗性加成
     *
     * @param mob 目标怪物
     * @param multiplier 强化倍率
     */
    private void applyMagicResistance(Mob mob, double multiplier) {
        try {
            // 火焰抗性
            AttributeInstance fireResist = mob.getAttribute(AttributeRegistry.FIRE_MAGIC_RESIST);
            if (fireResist != null) {
                fireResist.setBaseValue(fireResist.getBaseValue() + (multiplier - 1.0) * 5.0);
            }

            // 冰霜抗性
            AttributeInstance iceResist = mob.getAttribute(AttributeRegistry.ICE_MAGIC_RESIST);
            if (iceResist != null) {
                iceResist.setBaseValue(iceResist.getBaseValue() + (multiplier - 1.0) * 5.0);
            }

            // 闪电抗性
            AttributeInstance lightningResist = mob.getAttribute(AttributeRegistry.LIGHTNING_MAGIC_RESIST);
            if (lightningResist != null) {
                lightningResist.setBaseValue(lightningResist.getBaseValue() + (multiplier - 1.0) * 5.0);
            }

            // 神圣抗性
            AttributeInstance holyResist = mob.getAttribute(AttributeRegistry.HOLY_MAGIC_RESIST);
            if (holyResist != null) {
                holyResist.setBaseValue(holyResist.getBaseValue() + (multiplier - 1.0) * 5.0);
            }

            // 末影抗性
            AttributeInstance enderResist = mob.getAttribute(AttributeRegistry.ENDER_MAGIC_RESIST);
            if (enderResist != null) {
                enderResist.setBaseValue(enderResist.getBaseValue() + (multiplier - 1.0) * 5.0);
            }

            // 血魔法抗性
            AttributeInstance bloodResist = mob.getAttribute(AttributeRegistry.BLOOD_MAGIC_RESIST);
            if (bloodResist != null) {
                bloodResist.setBaseValue(bloodResist.getBaseValue() + (multiplier - 1.0) * 5.0);
            }

            // 召唤抗性
            AttributeInstance evocationResist = mob.getAttribute(AttributeRegistry.EVOCATION_MAGIC_RESIST);
            if (evocationResist != null) {
                evocationResist.setBaseValue(evocationResist.getBaseValue() + (multiplier - 1.0) * 5.0);
            }

            // 自然抗性
            AttributeInstance natureResist = mob.getAttribute(AttributeRegistry.NATURE_MAGIC_RESIST);
            if (natureResist != null) {
                natureResist.setBaseValue(natureResist.getBaseValue() + (multiplier - 1.0) * 5.0);
            }

            // 远古抗性
            AttributeInstance eldritchResist = mob.getAttribute(AttributeRegistry.ELDRITCH_MAGIC_RESIST);
            if (eldritchResist != null) {
                eldritchResist.setBaseValue(eldritchResist.getBaseValue() + (multiplier - 1.0) * 5.0);
            }

        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("应用魔法抗性加成失败: {}", e.getMessage());
        }
    }

    /**
     * 应用各系法术强度加成
     *
     * @param mob 目标怪物
     * @param multiplier 强化倍率
     */
    private void applySpellPower(Mob mob, double multiplier) {
        try {
            // 火焰法术强度
            AttributeInstance firePower = mob.getAttribute(AttributeRegistry.FIRE_SPELL_POWER);
            if (firePower != null) {
                firePower.setBaseValue(firePower.getBaseValue() * multiplier);
            }

            // 冰霜法术强度
            AttributeInstance icePower = mob.getAttribute(AttributeRegistry.ICE_SPELL_POWER);
            if (icePower != null) {
                icePower.setBaseValue(icePower.getBaseValue() * multiplier);
            }

            // 闪电法术强度
            AttributeInstance lightningPower = mob.getAttribute(AttributeRegistry.LIGHTNING_SPELL_POWER);
            if (lightningPower != null) {
                lightningPower.setBaseValue(lightningPower.getBaseValue() * multiplier);
            }

            // 神圣法术强度
            AttributeInstance holyPower = mob.getAttribute(AttributeRegistry.HOLY_SPELL_POWER);
            if (holyPower != null) {
                holyPower.setBaseValue(holyPower.getBaseValue() * multiplier);
            }

            // 末影法术强度
            AttributeInstance enderPower = mob.getAttribute(AttributeRegistry.ENDER_SPELL_POWER);
            if (enderPower != null) {
                enderPower.setBaseValue(enderPower.getBaseValue() * multiplier);
            }

            // 血魔法法术强度
            AttributeInstance bloodPower = mob.getAttribute(AttributeRegistry.BLOOD_SPELL_POWER);
            if (bloodPower != null) {
                bloodPower.setBaseValue(bloodPower.getBaseValue() * multiplier);
            }

            // 召唤法术强度
            AttributeInstance evocationPower = mob.getAttribute(AttributeRegistry.EVOCATION_SPELL_POWER);
            if (evocationPower != null) {
                evocationPower.setBaseValue(evocationPower.getBaseValue() * multiplier);
            }

            // 自然法术强度
            AttributeInstance naturePower = mob.getAttribute(AttributeRegistry.NATURE_SPELL_POWER);
            if (naturePower != null) {
                naturePower.setBaseValue(naturePower.getBaseValue() * multiplier);
            }

            // 远古法术强度
            AttributeInstance eldritchPower = mob.getAttribute(AttributeRegistry.ELDRITCH_SPELL_POWER);
            if (eldritchPower != null) {
                eldritchPower.setBaseValue(eldritchPower.getBaseValue() * multiplier);
            }

        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("应用法术强度加成失败: {}", e.getMessage());
        }
    }

    /**
     * 获取玩家当前法力值
     *
     * @param player 目标玩家
     * @return 当前法力值
     */
    public double getPlayerCurrentMana(ServerPlayer player) {
        try {
            MagicData magicData = MagicData.getPlayerMagicData(player);
            if (magicData != null) {
                return magicData.getMana();
            }
        } catch (Exception e) {
            return 0.0;
        }
        return 0.0;
    }
}
