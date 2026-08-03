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
 * 铁魔法兼容 (Forge 1.20.1) — 纯反射，编译期不依赖 irons_spellbooks。
 * API 差异大时可能部分属性缩放失效，见 FORGE_PORT.md。
 */
public class IronsSpellsCompat {

    public IronsSpellsCompat() {}

    private static void safeSetAttribute(AttributeInstance attr, double value, double fallback) {
        if (attr == null) return;
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            attr.setBaseValue(fallback);
            return;
        }
        attr.setBaseValue(value);
    }

    private static double safeDouble(double value) {
        return (Double.isNaN(value) || Double.isInfinite(value)) ? 0.0 : value;
    }

    private static Attribute resolveAttr(String fieldName) {
        try {
            Class<?> clazz = Class.forName("io.redspace.ironsspellbooks.api.registry.AttributeRegistry");
            Field f = clazz.getField(fieldName);
            Object ref = f.get(null);
            return unwrapAttribute(ref);
        } catch (Throwable t) {
            return null;
        }
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

    public double getPlayerSpellStrength(ServerPlayer player) {
        double strength = 0.0;
        try {
            Class<?> magicDataClz = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            Method getData = magicDataClz.getMethod("getPlayerMagicData", net.minecraft.world.entity.player.Player.class);
            Object magicData = getData.invoke(null, player);
            if (magicData != null) {
                try {
                    Method getMana = magicData.getClass().getMethod("getMana");
                    Object mana = getMana.invoke(magicData);
                    if (mana instanceof Number n) {
                        strength += safeDouble(n.doubleValue()) * 0.05;
                    }
                } catch (NoSuchMethodException ignored) {}
            }

            strength += evaluateSpellItem(player.getMainHandItem());
            strength += evaluateSpellItem(player.getOffhandItem());
            for (ItemStack armor : player.getArmorSlots()) {
                strength += evaluateSpellItem(armor);
            }
        } catch (Throwable t) {
            return 0.0;
        }
        return strength;
    }

    private double evaluateSpellItem(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;
        double value = 0.0;
        try {
            Class<?> spellBook = Class.forName("io.redspace.ironsspellbooks.item.SpellBook");
            if (spellBook.isInstance(stack.getItem())) {
                value += 10.0;
            }
            String id = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
            if (id.contains("spell") || id.contains("staff") || id.contains("wand") || id.contains("grimoire")) {
                value += 5.0;
            }
        } catch (Throwable ignored) {}
        return value;
    }

    public void applyMobBuffs(Mob mob, double multiplier) {
        if (multiplier <= 1.0) return;
        try {
            scale(mob, "SPELL_POWER", multiplier, Config.MAX_SPELL_POWER_MULTIPLIER.get());
            scale(mob, "MAX_MANA", multiplier, null);
            scale(mob, "MANA_REGEN", multiplier, null);
            // resists
            for (String name : new String[]{
                    "FIRE_MAGIC_RESIST", "ICE_MAGIC_RESIST", "LIGHTNING_MAGIC_RESIST",
                    "HOLY_MAGIC_RESIST", "ENDER_MAGIC_RESIST", "BLOOD_MAGIC_RESIST",
                    "EVOCATION_MAGIC_RESIST", "NATURE_MAGIC_RESIST", "ELDRITCH_MAGIC_RESIST"
            }) {
                scale(mob, name, Math.min(multiplier, Config.MAX_SPELL_RESIST_MULTIPLIER.get()), null);
            }
            for (String name : new String[]{
                    "FIRE_SPELL_POWER", "ICE_SPELL_POWER", "LIGHTNING_SPELL_POWER",
                    "HOLY_SPELL_POWER", "ENDER_SPELL_POWER", "BLOOD_SPELL_POWER",
                    "EVOCATION_SPELL_POWER", "NATURE_SPELL_POWER", "ELDRITCH_SPELL_POWER"
            }) {
                scale(mob, name, Math.min(multiplier, Config.MAX_SPELL_POWER_MULTIPLIER.get()), null);
            }
        } catch (Throwable t) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug("IronsSpellsCompat.applyMobBuffs failed: {}", t.toString());
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
