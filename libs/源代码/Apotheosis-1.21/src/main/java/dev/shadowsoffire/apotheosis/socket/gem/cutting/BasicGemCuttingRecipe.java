package dev.shadowsoffire.apotheosis.socket.gem.cutting;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

public record BasicGemCuttingRecipe(Ingredient base, List<SizedIngredient> top, List<SizedIngredient> left, List<SizedIngredient> right, ItemStack output) implements GemCuttingRecipe {

    public static MapCodec<BasicGemCuttingRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        Ingredient.CODEC.fieldOf("base").forGetter(BasicGemCuttingRecipe::base),
        SizedIngredient.FLAT_CODEC.listOf().fieldOf("top").forGetter(BasicGemCuttingRecipe::top),
        SizedIngredient.FLAT_CODEC.listOf().fieldOf("left").forGetter(BasicGemCuttingRecipe::left),
        SizedIngredient.FLAT_CODEC.listOf().fieldOf("right").forGetter(BasicGemCuttingRecipe::right),
        ItemStack.CODEC.fieldOf("output").forGetter(BasicGemCuttingRecipe::output))
        .apply(inst, BasicGemCuttingRecipe::new));

    public static StreamCodec<RegistryFriendlyByteBuf, BasicGemCuttingRecipe> STREAM_CODEC = StreamCodec.composite(
        Ingredient.CONTENTS_STREAM_CODEC, BasicGemCuttingRecipe::base,
        SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), BasicGemCuttingRecipe::top,
        SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), BasicGemCuttingRecipe::left,
        SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), BasicGemCuttingRecipe::right,
        ItemStack.STREAM_CODEC, BasicGemCuttingRecipe::output,
        BasicGemCuttingRecipe::new);

    @Override
    public boolean matches(CuttingRecipeInput input, Level level) {
        if (!this.base.test(input.getBase())) {
            return false;
        }

        return GemCuttingRecipe.anyMatch(input.getTop(), this.top)
            && GemCuttingRecipe.anyMatch(input.getLeft(), this.left)
            && GemCuttingRecipe.anyMatch(input.getRight(), this.right);
    }

    @Override
    public ItemStack assemble(CuttingRecipeInput input, Provider registries) {
        return this.output.copy();
    }

    @Override
    public ItemStack getResultItem(Provider registries) {
        return this.output.copy();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public void decrementInputs(CuttingRecipeInput input, Level level) {
        SizedIngredient top = GemCuttingRecipe.getMatchOrThrow(input.getTop(), this.top);
        SizedIngredient left = GemCuttingRecipe.getMatchOrThrow(input.getLeft(), this.left);
        SizedIngredient right = GemCuttingRecipe.getMatchOrThrow(input.getRight(), this.right);

        input.getTop().shrink(top.count());
        input.getLeft().shrink(left.count());
        input.getRight().shrink(right.count());
    }

    @Override
    public boolean isValidBaseItem(CuttingRecipeInput input, ItemStack stack) {
        return this.base.test(stack);
    }

    @Override
    public boolean isValidTopItem(CuttingRecipeInput input, ItemStack stack) {
        return GemCuttingRecipe.anyMatch(stack, this.top);
    }

    @Override
    public boolean isValidLeftItem(CuttingRecipeInput input, ItemStack stack) {
        return GemCuttingRecipe.anyMatch(stack, this.left);
    }

    @Override
    public boolean isValidRightItem(CuttingRecipeInput input, ItemStack stack) {
        return GemCuttingRecipe.anyMatch(stack, this.right);
    }

    public static class Serializer implements RecipeSerializer<BasicGemCuttingRecipe> {

        public static final Serializer INSTANCE = new Serializer();

        @Override
        public MapCodec<BasicGemCuttingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BasicGemCuttingRecipe> streamCodec() {
            return STREAM_CODEC;
        }

    }

}
