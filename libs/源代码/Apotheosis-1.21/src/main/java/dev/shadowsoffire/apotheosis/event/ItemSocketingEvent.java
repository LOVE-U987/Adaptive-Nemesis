package dev.shadowsoffire.apotheosis.event;

import dev.shadowsoffire.apotheosis.socket.SocketingRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

/**
 * Fired when a player sockets a gem into an item through {@link SocketingRecipe}.
 * <p>
 * This event allows modification of the output item, which allows operations such as placing the gem in a different open socket.
 */
public class ItemSocketingEvent extends Event {
    protected final ItemStack stack;
    protected final ItemStack gem;
    protected ItemStack output;

    public ItemSocketingEvent(ItemStack stack, ItemStack gem, ItemStack output) {
        this.stack = stack.copy();
        this.gem = gem.copy();
        this.output = output;
    }

    /**
     * Gets the item being socketed into.
     *
     * @return A copy of the left input item.
     */
    public ItemStack getInputStack() {
        return this.stack;
    }

    /**
     * Gets the gem that is being socketed into {@link #getInputStack()}
     *
     * @return A copy of the right input item.
     */
    public ItemStack getInputGem() {
        return this.gem;
    }

    /**
     * Returns the current result item, which is (by default) a copy of the input item with the gem set in the first open socket.
     */
    public ItemStack getOutput() {
        return this.output.copy();
    }

    /**
     * Sets the output of the socketing operation.<br>
     *
     * @param output The new output.
     * @throws IllegalArgumentException if this event produces an empty output stack. Use {@link CanSocketGemEvent} to prevent the operation.
     */
    public void setOutput(ItemStack output) {
        if (output.isEmpty()) {
            throw new IllegalArgumentException("Setting an empty output is undefined behavior");
        }
        this.output = output.copy();
    }

}
