package dev.shadowsoffire.apotheosis.advancements.predicates;

import net.minecraft.advancements.critereon.ItemSubPredicate;

public interface TypeAwareISP<T extends ItemSubPredicate> extends ItemSubPredicate {

    ItemSubPredicate.Type<T> type();

}
