package dev.shadowsoffire.apotheosis.socket.gem.cutting;

import java.util.List;

import javax.annotation.Nullable;

import org.spongepowered.include.com.google.common.base.Preconditions;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingRecipe.CuttingRecipeInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

public interface GemCuttingRecipe extends Recipe<CuttingRecipeInput> {

    /**
     * Reduces the count of the inputs, based on how many items should be consumed.
     * <p>
     * Note, the {@link CuttingRecipeInput#getBase() base item} does not need to
     * be decremented, as it will be replaced.
     *
     * @param input The recipe input that was just matched.
     * @param level The level.
     */
    void decrementInputs(CuttingRecipeInput input, Level level);

    /**
     * Checks if {@code stack} is a valid item to be placed in the base slot.
     */
    boolean isValidBaseItem(CuttingRecipeInput input, ItemStack stack);

    /**
     * Checks if {@code stack} is a valid item to be placed in the top slot.
     */
    boolean isValidTopItem(CuttingRecipeInput input, ItemStack stack);

    /**
     * Checks if {@code stack} is a valid item to be placed in the left slot.
     */
    boolean isValidLeftItem(CuttingRecipeInput input, ItemStack stack);

    /**
     * Checks if {@code stack} is a valid item to be placed in the right slot.
     */
    boolean isValidRightItem(CuttingRecipeInput input, ItemStack stack);

    @Override
    default boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    default RecipeType<?> getType() {
        return Apoth.RecipeTypes.GEM_CUTTING;
    }

    @Nullable
    public static SizedIngredient getMatch(ItemStack stack, List<SizedIngredient> ingredients) {
        for (SizedIngredient si : ingredients) {
            if (si.test(stack)) {
                return si;
            }
        }
        return null;
    }

    public static SizedIngredient getMatchOrThrow(ItemStack stack, List<SizedIngredient> ingredients) {
        return Preconditions.checkNotNull(getMatch(stack, ingredients), "Failed to find a match for " + stack);
    }

    public static boolean anyMatch(ItemStack stack, List<SizedIngredient> ingredients) {
        return getMatch(stack, ingredients) != null;
    }

    public static class CuttingRecipeInput extends RecipeWrapper {

        public CuttingRecipeInput(IItemHandler inv) {
            super(inv);
        }

        public ItemStack getBase() {
            return this.getItem(GemCuttingMenu.BASE_SLOT);
        }

        public ItemStack getTop() {
            return this.getItem(GemCuttingMenu.TOP_SLOT);
        }

        public ItemStack getLeft() {
            return this.getItem(GemCuttingMenu.LEFT_SLOT);
        }

        public ItemStack getRight() {
            return this.getItem(GemCuttingMenu.RIGHT_SLOT);
        }

    }
}
