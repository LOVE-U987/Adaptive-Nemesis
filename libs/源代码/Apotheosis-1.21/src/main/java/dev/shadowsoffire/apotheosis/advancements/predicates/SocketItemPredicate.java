package dev.shadowsoffire.apotheosis.advancements.predicates;

import com.mojang.serialization.Codec;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.Apoth.Components;
import dev.shadowsoffire.apotheosis.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.socket.gem.GemInstance;
import net.minecraft.advancements.critereon.SingleComponentItemPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

public class SocketItemPredicate implements SingleComponentItemPredicate<ItemContainerContents>, TypeAwareISP<SocketItemPredicate> {

    public static final Codec<SocketItemPredicate> CODEC = Codec.unit(SocketItemPredicate::new);

    @Override
    public DataComponentType<ItemContainerContents> componentType() {
        return Components.SOCKETED_GEMS;
    }

    @Override
    public boolean matches(ItemStack stack, ItemContainerContents value) {
        return SocketHelper.getGems(stack).stream().anyMatch(GemInstance::isValid);
    }

    @Override
    public Type<SocketItemPredicate> type() {
        return Apoth.ItemSubPredicates.SOCKETED_ITEM;
    }
}
