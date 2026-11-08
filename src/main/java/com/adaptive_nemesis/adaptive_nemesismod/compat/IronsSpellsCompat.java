package com.adaptive_nemesis.adaptive_nemesismod.compat;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.nemesis.NemesisSystem;
import com.adaptive_nemesis.adaptive_nemesismod.util.SafeMath;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.item.SpellBook;
import net.minecraft.core.registries.BuiltInRegistries;
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
 * - 为怪物添加铁魔法属性加成（双通道：施法怪获得法术强度/法力/施法效率，
 *   宿敌获得通用法术抗性，普通怪不加成）
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
                // 法力值贡献 - 使用最大法力值作为参考（当前法力值会随施法波动，
                // 导致玩家强度评估不稳定，施法耗蓝后怪物缩放反而下降）
                float maxMana = (float) player.getAttributeValue(AttributeRegistry.MAX_MANA);
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

        // 基于注册表 ID 的子串匹配（F-50）：Item.toString() 的返回格式无契约保证，
        // 注册 ID（如 "irons_spellbooks:ring_of_arcane"）才是稳定标识；
        // 命名空间前缀对下方关键词子串匹配无影响，故不做命名空间剥离
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase();
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
     * 双通道判定：
     * - 施法怪通道：实现 IMagicEntity 的实体（铁魔法 AbstractSpellCastingMob 系等）
     *   获得法术强度/法力/施法效率加成，与是否宿敌无关；
     * - 宿敌通道：打有 NEMESIS_TAG 标记的实体获得法术抗性加成。
     * 两者皆非的普通怪物不应用任何法术类加成，
     * 避免法师玩家的多段、召唤类法术在打群怪时被逐只减免。
     * 法术强度与法术抗性的加成倍率分别受
     * maxSpellPowerMultiplier / maxSpellResistMultiplier 配置上限约束。
     *
     * @param mob 目标怪物
     * @param multiplier 强化倍率
     */
    public void applyMobBuffs(Mob mob, double multiplier) {
        try {
            boolean isSpellcaster = mob instanceof IMagicEntity;
            boolean isNemesis = mob.getPersistentData().getBoolean(NemesisSystem.NEMESIS_TAG);

            // 普通怪（非施法且非宿敌）不应用铁魔法法术类加成
            if (!isSpellcaster && !isNemesis) {
                return;
            }

            // 倍率语境统一使用 SafeMath（F-47）：NaN/Infinity 回退 1.0（中性倍率），
            // 旧实现回退 0.0 会把加成整体清零
            double effectiveMultiplier = SafeMath.safeDouble(Math.max(1.0, multiplier));

            // 法术强度/施法资源加成倍率受 maxSpellPowerMultiplier 上限约束
            double powerCapped = Math.min(effectiveMultiplier, Config.MAX_SPELL_POWER_MULTIPLIER.get());
            // 法术抗性加成倍率受 maxSpellResistMultiplier 上限约束
            double resistCapped = Math.min(effectiveMultiplier, Config.MAX_SPELL_RESIST_MULTIPLIER.get());

            // 施法怪通道：法术强度/法力/施法效率（旧公式保持不动）
            if (isSpellcaster) {
                applyCasterBuffs(mob, effectiveMultiplier, powerCapped);
            }

            // 宿敌通道：通用法术抗性（温和乘算，倍率受 maxSpellResistMultiplier 上限约束）
            if (isNemesis) {
                applySpellResistance(mob, resistCapped);
            }

        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("应用铁魔法属性加成失败: {}", e.getMessage());
        }
    }

    /**
     * 应用施法怪专属加成（编排：输出资源 + 施法效率两组）
     *
     * 仅对实现 IMagicEntity 的施法怪生效。法术伤害公式为
     * 基础值 x 通用法术强度 x 学派法术强度，本通道只乘通用
     * SPELL_POWER，不再触碰九大学派强度，避免两因子同乘导致
     * 实际伤害按倍率平方增长。
     *
     * @param mob 目标怪物
     * @param effectiveMultiplier 已做下限保护的强化倍率（供施法效率温和公式使用）
     * @param powerCapped 受 maxSpellPowerMultiplier 封顶的法术强度倍率
     */
    private void applyCasterBuffs(Mob mob, double effectiveMultiplier, double powerCapped) {
        // 1. 法术输出资源：通用法术强度 + 法力上限
        applyCasterPowerBuffs(mob, powerCapped);

        // 2. 施法效率：法力恢复 / 冷却缩减 / 施法时间缩减（既有温和公式）
        applyCasterEfficiencyBuffs(mob, effectiveMultiplier);
    }

    /**
     * 应用施法怪的输出资源加成（通用法术强度 + 法力上限）
     *
     * @param mob 目标怪物
     * @param powerCapped 受 maxSpellPowerMultiplier 封顶的法术强度倍率
     */
    private void applyCasterPowerBuffs(Mob mob, double powerCapped) {
        // 增加通用法术强度 (Spell Power)
        // NeoForge 1.21.1: DeferredHolder可以直接作为Holder<Attribute>传入
        AttributeInstance spellPowerAttr = mob.getAttribute(AttributeRegistry.SPELL_POWER);
        if (spellPowerAttr != null) {
            double originalPower = spellPowerAttr.getBaseValue();
            double newPower = Math.max(originalPower, originalPower * powerCapped);
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

        // 增加法力值 (Max Mana)
        AttributeInstance maxManaAttr = mob.getAttribute(AttributeRegistry.MAX_MANA);
        if (maxManaAttr != null) {
            double originalMana = maxManaAttr.getBaseValue();
            double newMana = Math.max(originalMana, originalMana * powerCapped);
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
    }

    /**
     * 应用施法怪的施法效率加成（法力恢复/冷却缩减/施法时间缩减）
     *
     * 三项均沿用既有温和公式（(mult-1)*0.5 / (mult-1)*0.3），不扩大改动面。
     *
     * @param mob 目标怪物
     * @param effectiveMultiplier 已做下限保护的强化倍率
     */
    private void applyCasterEfficiencyBuffs(Mob mob, double effectiveMultiplier) {
        // 增加法力恢复 (Mana Regen)
        AttributeInstance manaRegenAttr = mob.getAttribute(AttributeRegistry.MANA_REGEN);
        if (manaRegenAttr != null) {
            double originalRegen = manaRegenAttr.getBaseValue();
            double newRegen = Math.max(originalRegen, originalRegen * (1.0 + (effectiveMultiplier - 1.0) * 0.5));
            safeSetAttribute(manaRegenAttr, newRegen, originalRegen);
        }

        // 增加冷却缩减 (Cooldown Reduction)
        AttributeInstance cooldownAttr = mob.getAttribute(AttributeRegistry.COOLDOWN_REDUCTION);
        if (cooldownAttr != null) {
            double originalCooldown = cooldownAttr.getBaseValue();
            double newCooldown = Math.max(originalCooldown, originalCooldown * (1.0 + (effectiveMultiplier - 1.0) * 0.3));
            safeSetAttribute(cooldownAttr, newCooldown, originalCooldown);
        }

        // 增加施法时间缩减 (Cast Time Reduction)
        AttributeInstance castTimeAttr = mob.getAttribute(AttributeRegistry.CAST_TIME_REDUCTION);
        if (castTimeAttr != null) {
            double originalCastTime = castTimeAttr.getBaseValue();
            double newCastTime = Math.max(originalCastTime, originalCastTime * (1.0 + (effectiveMultiplier - 1.0) * 0.3));
            safeSetAttribute(castTimeAttr, newCastTime, originalCastTime);
        }
    }

    /**
     * 应用通用法术抗性加成（宿敌通道）
     *
     * 铁魔法法术减伤公式为 2 - softCap(学派抗性 x 通用抗性)，
     * 本方法只乘通用 SPELL_RESIST（属性定义基线 1.0），
     * 不再触碰九大学派抗性：学派抗性的种族特性
     * （如特定怪物自身的高火抗）完全交还铁魔法自身语义；
     * 乘算倍率经 softCap 公式天然渐近封顶，不会接近免疫。
     *
     * 注意：调用方（applyMobBuffs）传入的 multiplier 已受
     * maxSpellResistMultiplier 配置封顶，此处直接使用即可。
     *
     * @param mob 目标怪物
     * @param multiplier 已封顶的强化倍率
     */
    private void applySpellResistance(Mob mob, double multiplier) {
        try {
            // 倍率语境统一使用 SafeMath（F-47）
            double safeMult = SafeMath.safeDouble(multiplier);

            AttributeInstance resistAttr = mob.getAttribute(AttributeRegistry.SPELL_RESIST);
            if (resistAttr != null) {
                double original = resistAttr.getBaseValue();
                double newResist = Math.max(original, original * safeMult);
                safeSetAttribute(resistAttr, newResist, original);

                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug(
                        "怪物 {} 法术抗性: {} -> {}",
                        mob.getName().getString(),
                        String.format("%.2f", original),
                        String.format("%.2f", newResist)
                    );
                }
            }

        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("应用法术抗性加成失败: {}", e.getMessage());
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
