package dev.shadowsoffire.apotheosis.advancements.predicates;

import com.mojang.serialization.Codec;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.Apoth.Components;
import dev.shadowsoffire.apotheosis.affix.ItemAffixes;
import net.minecraft.advancements.critereon.SingleComponentItemPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

public class AffixItemPredicate implements SingleComponentItemPredicate<ItemAffixes>, TypeAwareISP<AffixItemPredicate> {

    public static final Codec<AffixItemPredicate> CODEC = Codec.unit(AffixItemPredicate::new);

    @Override
    public DataComponentType<ItemAffixes> componentType() {
        return Components.AFFIXES;
    }

    @Override
    public boolean matches(ItemStack stack, ItemAffixes value) {
        return !value.isEmpty();
    }

    @Override
    public Type<AffixItemPredicate> type() {
        return Apoth.ItemSubPredicates.AFFIXED_ITEM;
    }

}
