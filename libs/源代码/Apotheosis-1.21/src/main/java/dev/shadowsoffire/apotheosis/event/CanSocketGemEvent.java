package dev.shadowsoffire.apotheosis.event;

import dev.shadowsoffire.apotheosis.socket.SocketingRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * This event is fired when a player attempts to socket a gem into an item.
 * <p>
 * It can be used to prevent gems that would otherwise be applicable from being inserted into the item.
 * <p>
 * If you wish to perform more complex logic, consider making a custom {@link SocketingRecipe}.
 */
public class CanSocketGemEvent extends Event implements ICancellableEvent {
    protected final ItemStack stack;
    protected final ItemStack gem;

    public CanSocketGemEvent(ItemStack stack, ItemStack gem) {
        this.stack = stack.copy();
        this.gem = gem.copy();
    }

    /**
     * Returns the item being socketed into.
     */
    public ItemStack getInputStack() {
        return this.stack;
    }

    /**
     * Returns the gem that is being socketed into {@link #getInputStack()}
     */
    public ItemStack getInputGem() {
        return this.gem;
    }

    /**
     * Cancels the event, preventing application of the gem into the target item.
     */
    @Override
    public void setCanceled(boolean canceled) {
        ICancellableEvent.super.setCanceled(canceled);
    }

}
