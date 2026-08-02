package com.adaptive_nemesis.adaptive_nemesismod.compat;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 史诗战斗兼容 (Forge 1.20.1) — 纯反射，编译期不依赖 epicfight。
 * 包名/字段名在 1.20.1 与 1.21 可能不同，见 FORGE_PORT.md。
 */
public class EpicFightCompat {

    public EpicFightCompat() {}

    private static void safeSetAttribute(AttributeInstance attr, double value, double fallback) {
        if (attr == null) return;
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            attr.setBaseValue(fallback);
            return;
        }
        attr.setBaseValue(value);
    }

    private static double safeDouble(double value) {
        return (Double.isNaN(value) || Double.isInfinite(value)) ? 1.0 : value;
    }

    private static Attribute resolveAttr(String fieldName) {
        String[] classNames = {
                "yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes",
                "yesman.epicfight.registry.entries.EpicFightAttributes",
                "yesman.epicfight.main.EpicFightAttributes"
        };
        for (String cn : classNames) {
            try {
                Class<?> clazz = Class.forName(cn);
                Field f = clazz.getField(fieldName);
                return unwrapAttribute(f.get(null));
            } catch (Throwable ignored) {}
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Attribute unwrapAttribute(Object ref) {
        if (ref == null) return null;
        if (ref instanceof Attribute a) return a;
        try {
            Method get = ref.getClass().getMethod("get");
            Object v = get.invoke(ref);
            if (v instanceof Attribute a) return a;
            if (v != null) {
                try {
                    Method value = v.getClass().getMethod("value");
                    Object vv = value.invoke(v);
                    if (vv instanceof Attribute a2) return a2;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable ignored) {}
        try {
            Method value = ref.getClass().getMethod("value");
            Object v = value.invoke(ref);
            if (v instanceof Attribute a) return a;
        } catch (Throwable ignored) {}
        return null;
    }

    private static AttributeInstance getAttr(Mob mob, String fieldName) {
        Attribute attr = resolveAttr(fieldName);
        return attr == null ? null : mob.getAttribute(attr);
    }

    public double getPlayerCombatStrength(ServerPlayer player) {
        double strength = 0.0;
        try {
            Class<?> caps = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities");
            Class<?> patchClz = Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch");
            Method getPatch = caps.getMethod("getEntityPatch", net.minecraft.world.entity.Entity.class, Class.class);
            Object patch = getPatch.invoke(null, player, patchClz);
            if (patch != null) {
                try {
                    Method getMaxStamina = patch.getClass().getMethod("getMaxStamina");
                    Object maxStamina = getMaxStamina.invoke(patch);
                    if (maxStamina instanceof Number n) {
                        strength += safeDouble(n.doubleValue()) * 0.5;
                    }
                } catch (NoSuchMethodException ignored) {}
            }

            Attribute impact = resolveAttr("IMPACT");
            if (impact != null) strength += player.getAttributeValue(impact) * 2.0;
            Attribute armorNeg = resolveAttr("ARMOR_NEGATION");
            if (armorNeg != null) strength += player.getAttributeValue(armorNeg) * 1.5;

            ItemStack mainHand = player.getMainHandItem();
            if (!mainHand.isEmpty()) {
                String itemId = String.valueOf(mainHand.getItem());
                if (itemId.contains("katana") || itemId.contains("longsword") ||
                        itemId.contains("greatsword") || itemId.contains("spear")) {
                    strength += 15.0;
                } else if (itemId.contains("sword") || itemId.contains("axe")) {
                    strength += 8.0;
                }
            }

            int armorCount = 0;
            for (ItemStack armor : player.getArmorSlots()) {
                if (!armor.isEmpty()) armorCount++;
            }
            strength += armorCount * 3.0;
        } catch (Throwable t) {
            return 0.0;
        }
        return strength;
    }

    public void applyMobBuffs(Mob mob, double multiplier) {
        if (multiplier <= 1.0) return;
        try {
            scale(mob, "STUN_ARMOR", multiplier, Config.MAX_HIT_RESIST_MULTIPLIER.get());
            scale(mob, "IMPACT", multiplier, null);
            scale(mob, "ARMOR_NEGATION", multiplier, null);
            scale(mob, "MAX_STRIKES", multiplier, null);
            scale(mob, "WEIGHT", Math.max(1.0, Config.WEIGHT_MIN_BONUS.get() + (multiplier - 1.0) * Config.WEIGHT_PER_MULTIPLIER.get()), null);
            scale(mob, "MAX_STAMINA", Math.min(multiplier, Config.MAX_STAMINA_MULTIPLIER.get()), null);
            scale(mob, "STAMINA_REGEN", multiplier, null);
            scale(mob, "ASSASSINATION_RESISTANCE", Math.min(multiplier, Config.MAX_KNOCKDOWN_RESIST_MULTIPLIER.get()), null);
        } catch (Throwable t) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug("EpicFightCompat.applyMobBuffs failed: {}", t.toString());
            }
        }
    }

    private void scale(Mob mob, String field, double multiplier, Double cap) {
        AttributeInstance inst = getAttr(mob, field);
        if (inst == null) return;
        double m = multiplier;
        if (cap != null) m = Math.min(m, cap);
        double base = inst.getBaseValue();
        if (base <= 0) base = 1.0;
        safeSetAttribute(inst, base * m, base);
    }
}
