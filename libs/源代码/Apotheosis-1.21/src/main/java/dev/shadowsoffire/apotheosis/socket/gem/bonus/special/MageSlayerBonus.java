package dev.shadowsoffire.apotheosis.socket.gem.bonus.special;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.apotheosis.affix.Affix;
import dev.shadowsoffire.apotheosis.socket.gem.GemClass;
import dev.shadowsoffire.apotheosis.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.socket.gem.GemView;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.bonus.GemBonus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;

public class MageSlayerBonus extends GemBonus {

    public static Codec<MageSlayerBonus> CODEC = RecordCodecBuilder.create(inst -> inst
        .group(
            gemClass(),
            Purity.mapCodec(Codec.floatRange(0, 1)).fieldOf("values").forGetter(a -> a.values))
        .apply(inst, MageSlayerBonus::new));

    protected final Map<Purity, Float> values;

    public MageSlayerBonus(GemClass gemClass, Map<Purity, Float> values) {
        super(gemClass);
        this.values = values;
    }

    @Override
    public float onHurt(GemInstance inst, DamageSource src, LivingEntity user, float amount) {
        float value = this.values.get(inst.purity());
        if (src.is(Tags.DamageTypes.IS_MAGIC)) {
            user.heal(amount * value);
            return amount * (1 - value);
        }
        return super.onHurt(inst, src, user, amount);
    }

    @Override
    public Codec<? extends GemBonus> getCodec() {
        return CODEC;
    }

    @Override
    public Component getSocketBonusTooltip(GemView inst, AttributeTooltipContext ctx) {
        float value = this.values.get(inst.purity());
        return Component.translatable("bonus." + this.getTypeKey() + ".desc", Affix.fmt(value * 100)).withStyle(ChatFormatting.YELLOW);
    }

    @Override
    public boolean supports(Purity purity) {
        return this.values.containsKey(purity);
    }

}
