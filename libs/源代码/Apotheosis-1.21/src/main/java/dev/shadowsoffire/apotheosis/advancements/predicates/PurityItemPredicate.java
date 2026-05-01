package dev.shadowsoffire.apotheosis.advancements.predicates;

import java.util.Set;

import com.mojang.serialization.Codec;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.Apoth.Components;
import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.placebo.codec.PlaceboCodecs;
import net.minecraft.advancements.critereon.SingleComponentItemPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

public record PurityItemPredicate(Set<Purity> purities) implements SingleComponentItemPredicate<Purity>, TypeAwareISP<PurityItemPredicate> {

    public static final Codec<PurityItemPredicate> CODEC = PlaceboCodecs.setOf(Purity.CODEC).fieldOf("purities").xmap(PurityItemPredicate::new, PurityItemPredicate::purities).codec();

    @Override
    public DataComponentType<Purity> componentType() {
        return Components.PURITY;
    }

    @Override
    public boolean matches(ItemStack stack, Purity value) {
        return this.purities.contains(value);
    }

    @Override
    public Type<PurityItemPredicate> type() {
        return Apoth.ItemSubPredicates.ITEM_WITH_PURITY;
    }
}
