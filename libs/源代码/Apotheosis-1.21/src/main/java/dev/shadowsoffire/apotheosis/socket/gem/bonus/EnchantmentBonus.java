package dev.shadowsoffire.apotheosis.socket.gem.bonus;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.apotheosis.socket.gem.GemClass;
import dev.shadowsoffire.apotheosis.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.socket.gem.GemView;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.placebo.codec.PlaceboCodecs;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;

public class EnchantmentBonus extends GemBonus {

    public static Codec<EnchantmentBonus> CODEC = RecordCodecBuilder.create(inst -> inst
        .group(
            gemClass(),
            Enchantment.CODEC.fieldOf("enchantment").forGetter(a -> a.ench),
            Mode.CODEC.optionalFieldOf("mode", Mode.SINGLE).forGetter(a -> a.mode),
            Purity.mapCodec(Codec.intRange(1, 127)).fieldOf("values").forGetter(a -> a.values))
        .apply(inst, EnchantmentBonus::new));

    protected final Holder<Enchantment> ench;
    protected final Mode mode;
    protected final Map<Purity, Integer> values;

    public EnchantmentBonus(GemClass gemClass, Holder<Enchantment> ench, Mode mode, Map<Purity, Integer> values) {
        super(gemClass);
        this.ench = ench;
        this.values = values;
        this.mode = mode;
    }

    @Override
    public Component getSocketBonusTooltip(GemView gem, AttributeTooltipContext ctx) {
        int level = this.values.get(gem.purity());
        String desc = "bonus." + this.getTypeKey() + ".desc";
        if (this.mode == Mode.GLOBAL) {
            desc += ".global";
        }
        else if (this.mode == Mode.EXISTING) {
            desc += ".mustExist";
        }
        Component enchName = this.ench.value().description().plainCopy();
        return Component.translatable(desc, level, Component.translatable("misc.apotheosis.level" + (level > 1 ? ".many" : "")), enchName).withStyle(ChatFormatting.GREEN);
    }

    @Override
    public void getEnchantmentLevels(GemInstance gem, GetEnchantmentLevelEvent event) {
        ItemEnchantments.Mutable enchantments = event.getEnchantments();
        int level = this.values.get(gem.purity());
        if (this.mode == Mode.GLOBAL) {
            for (Holder<Enchantment> e : enchantments.keySet()) {
                int current = enchantments.getLevel(e);
                if (current > 0) {
                    enchantments.upgrade(e, current + level);
                }
            }
        }
        else if (this.mode == Mode.EXISTING) {
            int current = enchantments.getLevel(this.ench);
            if (current > 0) {
                enchantments.upgrade(this.ench, current + level);
            }
        }
        else {
            enchantments.upgrade(this.ench, enchantments.getLevel(this.ench) + level);
        }
    }

    @Override
    public boolean supports(Purity purity) {
        return this.values.containsKey(purity);
    }

    @Override
    public Codec<? extends GemBonus> getCodec() {
        return CODEC;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static enum Mode {
        SINGLE,
        EXISTING,
        GLOBAL;

        public static final Codec<Mode> CODEC = PlaceboCodecs.enumCodec(Mode.class);
    }

    public static class Builder extends GemBonus.Builder {
        private Holder<Enchantment> enchantment;
        private Mode mode;
        private Map<Purity, Integer> values;

        public Builder() {
            this.values = new HashMap<>();
            this.mode = Mode.SINGLE;
        }

        public Builder enchantment(Holder<Enchantment> enchantment) {
            this.enchantment = enchantment;
            return this;
        }

        public Builder mode(Mode mode) {
            this.mode = mode;
            return this;
        }

        public Builder value(Purity purity, int value) {
            if (value < 1 || value > 127) {
                throw new IllegalArgumentException("EnchantmentBonus is limited to values between 1 and 127 (inclusive).");
            }
            this.values.put(purity, value);
            return this;
        }

        @Override
        public EnchantmentBonus build(GemClass gemClass) {
            return new EnchantmentBonus(gemClass, this.enchantment, this.mode, this.values);
        }
    }

}
