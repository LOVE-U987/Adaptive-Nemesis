package dev.shadowsoffire.apotheosis.compat.jei;

import java.util.Arrays;

import dev.shadowsoffire.apotheosis.compat.jei.GemCuttingCategory.GemCuttingExtension;
import dev.shadowsoffire.apotheosis.socket.gem.cutting.BasicGemCuttingRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

public class BasicGemCuttingExtension implements GemCuttingExtension<BasicGemCuttingRecipe> {

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BasicGemCuttingRecipe recipe, IFocusGroup focuses) {

        builder.addSlot(RecipeIngredientRole.INPUT, 48, 37).addIngredients(VanillaTypes.ITEM_STACK, Arrays.asList(recipe.base().getItems()));

        builder.addSlot(RecipeIngredientRole.INPUT, 48, 4).addIngredients(VanillaTypes.ITEM_STACK, recipe.top().stream().map(SizedIngredient::getItems).flatMap(Arrays::stream).toList());
        builder.addSlot(RecipeIngredientRole.INPUT, 19, 56).addIngredients(VanillaTypes.ITEM_STACK, recipe.left().stream().map(SizedIngredient::getItems).flatMap(Arrays::stream).toList());
        builder.addSlot(RecipeIngredientRole.INPUT, 76, 56).addIngredients(VanillaTypes.ITEM_STACK, recipe.right().stream().map(SizedIngredient::getItems).flatMap(Arrays::stream).toList());

        builder.addSlot(RecipeIngredientRole.OUTPUT, 117, 35).addIngredient(VanillaTypes.ITEM_STACK, recipe.output());

    }

}
