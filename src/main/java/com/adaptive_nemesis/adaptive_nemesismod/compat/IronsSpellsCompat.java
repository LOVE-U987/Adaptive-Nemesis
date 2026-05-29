package com.adaptive_nemesis.adaptive_nemesismod.compat;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
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
     * 默认构造函数（非空壳）
     * 工具类，方法由外部直接调用（如 EnemyScalingHandler），不持有内部状态，故无需初始化。
     * 保留 public 构造以允许外部实例化。
     */
    public IronsSpellsCompat() {}

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
     * 安全获取双精度浮点数 - 防止 NaN/Infinity 传播
     *
     * @param value 待检查的值
     * @return 如果值有效则返回原值，否则返回 0.0
     */
    private static double safeDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return value;
    }

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
            // 确保倍率不低于 1.0，防止随机因子导致属性降低
            double effectiveMultiplier = safeDouble(Math.max(1.0, multiplier));

            // 1. 增加法术强度 (Spell Power) - 如果有法术攻击
            // NeoForge 1.21.1: DeferredHolder可以直接作为Holder<Attribute>传入
            AttributeInstance spellPowerAttr = mob.getAttribute(AttributeRegistry.SPELL_POWER);
            if (spellPowerAttr != null) {
                double originalPower = spellPowerAttr.getBaseValue();
                double newPower = Math.max(originalPower, originalPower * effectiveMultiplier);
                safeSetAttribute(spellPowerAttr, newPower, originalPower);

                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug(
                        "怪物 {} 法术强度: {} -> {}",
                        mob.getName().getString(),
                        String.format("%.2f", originalPower),
                        String.format("%.2f", newPower)
                    );
                }
            }

            // 2. 增加法力值 (Max Mana)
            AttributeInstance maxManaAttr = mob.getAttribute(AttributeRegistry.MAX_MANA);
            if (maxManaAttr != null) {
                double originalMana = maxManaAttr.getBaseValue();
                double newMana = Math.max(originalMana, originalMana * effectiveMultiplier);
                safeSetAttribute(maxManaAttr, newMana, originalMana);

                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug(
                        "怪物 {} 法力值: {} -> {}",
                        mob.getName().getString(),
                        String.format("%.2f", originalMana),
                        String.format("%.2f", newMana)
                    );
                }
            }

            // 3. 增加法力恢复 (Mana Regen)
            AttributeInstance manaRegenAttr = mob.getAttribute(AttributeRegistry.MANA_REGEN);
            if (manaRegenAttr != null) {
                double originalRegen = manaRegenAttr.getBaseValue();
                double newRegen = Math.max(originalRegen, originalRegen * (1.0 + (effectiveMultiplier - 1.0) * 0.5));
                safeSetAttribute(manaRegenAttr, newRegen, originalRegen);
            }

            // 4. 增加冷却缩减 (Cooldown Reduction)
            AttributeInstance cooldownAttr = mob.getAttribute(AttributeRegistry.COOLDOWN_REDUCTION);
            if (cooldownAttr != null) {
                double originalCooldown = cooldownAttr.getBaseValue();
                double newCooldown = Math.max(originalCooldown, originalCooldown * (1.0 + (effectiveMultiplier - 1.0) * 0.3));
                safeSetAttribute(cooldownAttr, newCooldown, originalCooldown);
            }

            // 5. 增加施法时间缩减 (Cast Time Reduction)
            AttributeInstance castTimeAttr = mob.getAttribute(AttributeRegistry.CAST_TIME_REDUCTION);
            if (castTimeAttr != null) {
                double originalCastTime = castTimeAttr.getBaseValue();
                double newCastTime = Math.max(originalCastTime, originalCastTime * (1.0 + (effectiveMultiplier - 1.0) * 0.3));
                safeSetAttribute(castTimeAttr, newCastTime, originalCastTime);
            }

            // 6. 增加各系魔法抗性
            applyMagicResistance(mob, effectiveMultiplier);

            // 7. 增加各系法术强度
            applySpellPower(mob, effectiveMultiplier);

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
            double safeMult = safeDouble(multiplier);

            // 火焰抗性
            AttributeInstance fireResist = mob.getAttribute(AttributeRegistry.FIRE_MAGIC_RESIST);
            if (fireResist != null) {
                double original = fireResist.getBaseValue();
                safeSetAttribute(fireResist, Math.max(original, original + (safeMult - 1.0) * 5.0), original);
            }

            // 冰霜抗性
            AttributeInstance iceResist = mob.getAttribute(AttributeRegistry.ICE_MAGIC_RESIST);
            if (iceResist != null) {
                double original = iceResist.getBaseValue();
                safeSetAttribute(iceResist, Math.max(original, original + (safeMult - 1.0) * 5.0), original);
            }

            // 闪电抗性
            AttributeInstance lightningResist = mob.getAttribute(AttributeRegistry.LIGHTNING_MAGIC_RESIST);
            if (lightningResist != null) {
                double original = lightningResist.getBaseValue();
                safeSetAttribute(lightningResist, Math.max(original, original + (safeMult - 1.0) * 5.0), original);
            }

            // 神圣抗性
            AttributeInstance holyResist = mob.getAttribute(AttributeRegistry.HOLY_MAGIC_RESIST);
            if (holyResist != null) {
                double original = holyResist.getBaseValue();
                safeSetAttribute(holyResist, Math.max(original, original + (safeMult - 1.0) * 5.0), original);
            }

            // 末影抗性
            AttributeInstance enderResist = mob.getAttribute(AttributeRegistry.ENDER_MAGIC_RESIST);
            if (enderResist != null) {
                double original = enderResist.getBaseValue();
                safeSetAttribute(enderResist, Math.max(original, original + (safeMult - 1.0) * 5.0), original);
            }

            // 血魔法抗性
            AttributeInstance bloodResist = mob.getAttribute(AttributeRegistry.BLOOD_MAGIC_RESIST);
            if (bloodResist != null) {
                double original = bloodResist.getBaseValue();
                safeSetAttribute(bloodResist, Math.max(original, original + (safeMult - 1.0) * 5.0), original);
            }

            // 召唤抗性
            AttributeInstance evocationResist = mob.getAttribute(AttributeRegistry.EVOCATION_MAGIC_RESIST);
            if (evocationResist != null) {
                double original = evocationResist.getBaseValue();
                safeSetAttribute(evocationResist, Math.max(original, original + (safeMult - 1.0) * 5.0), original);
            }

            // 自然抗性
            AttributeInstance natureResist = mob.getAttribute(AttributeRegistry.NATURE_MAGIC_RESIST);
            if (natureResist != null) {
                double original = natureResist.getBaseValue();
                safeSetAttribute(natureResist, Math.max(original, original + (safeMult - 1.0) * 5.0), original);
            }

            // 远古抗性
            AttributeInstance eldritchResist = mob.getAttribute(AttributeRegistry.ELDRITCH_MAGIC_RESIST);
            if (eldritchResist != null) {
                double original = eldritchResist.getBaseValue();
                safeSetAttribute(eldritchResist, Math.max(original, original + (safeMult - 1.0) * 5.0), original);
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
            double safeMult = safeDouble(multiplier);

            // 火焰法术强度
            AttributeInstance firePower = mob.getAttribute(AttributeRegistry.FIRE_SPELL_POWER);
            if (firePower != null) {
                double original = firePower.getBaseValue();
                safeSetAttribute(firePower, Math.max(original, original * safeMult), original);
            }

            // 冰霜法术强度
            AttributeInstance icePower = mob.getAttribute(AttributeRegistry.ICE_SPELL_POWER);
            if (icePower != null) {
                double original = icePower.getBaseValue();
                safeSetAttribute(icePower, Math.max(original, original * safeMult), original);
            }

            // 闪电法术强度
            AttributeInstance lightningPower = mob.getAttribute(AttributeRegistry.LIGHTNING_SPELL_POWER);
            if (lightningPower != null) {
                double original = lightningPower.getBaseValue();
                safeSetAttribute(lightningPower, Math.max(original, original * safeMult), original);
            }

            // 神圣法术强度
            AttributeInstance holyPower = mob.getAttribute(AttributeRegistry.HOLY_SPELL_POWER);
            if (holyPower != null) {
                double original = holyPower.getBaseValue();
                safeSetAttribute(holyPower, Math.max(original, original * safeMult), original);
            }

            // 末影法术强度
            AttributeInstance enderPower = mob.getAttribute(AttributeRegistry.ENDER_SPELL_POWER);
            if (enderPower != null) {
                double original = enderPower.getBaseValue();
                safeSetAttribute(enderPower, Math.max(original, original * safeMult), original);
            }

            // 血魔法法术强度
            AttributeInstance bloodPower = mob.getAttribute(AttributeRegistry.BLOOD_SPELL_POWER);
            if (bloodPower != null) {
                double original = bloodPower.getBaseValue();
                safeSetAttribute(bloodPower, Math.max(original, original * safeMult), original);
            }

            // 召唤法术强度
            AttributeInstance evocationPower = mob.getAttribute(AttributeRegistry.EVOCATION_SPELL_POWER);
            if (evocationPower != null) {
                double original = evocationPower.getBaseValue();
                safeSetAttribute(evocationPower, Math.max(original, original * safeMult), original);
            }

            // 自然法术强度
            AttributeInstance naturePower = mob.getAttribute(AttributeRegistry.NATURE_SPELL_POWER);
            if (naturePower != null) {
                double original = naturePower.getBaseValue();
                safeSetAttribute(naturePower, Math.max(original, original * safeMult), original);
            }

            // 远古法术强度
            AttributeInstance eldritchPower = mob.getAttribute(AttributeRegistry.ELDRITCH_SPELL_POWER);
            if (eldritchPower != null) {
                double original = eldritchPower.getBaseValue();
                safeSetAttribute(eldritchPower, Math.max(original, original * safeMult), original);
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
