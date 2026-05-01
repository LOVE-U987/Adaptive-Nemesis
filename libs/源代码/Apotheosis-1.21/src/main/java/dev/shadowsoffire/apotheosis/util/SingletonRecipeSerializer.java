package dev.shadowsoffire.apotheosis.util;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

public class SingletonRecipeSerializer<T extends Recipe<?>> implements RecipeSerializer<T> {

    private T recipe;
    private MapCodec<T> codec;
    private StreamCodec<RegistryFriendlyByteBuf, T> streamCodec;

    public SingletonRecipeSerializer(Supplier<T> factory) {
        this.recipe = factory.get();
        this.codec = MapCodec.unit(this.recipe);
        this.streamCodec = StreamCodec.unit(this.recipe);
    }

    @Override
    public MapCodec<T> codec() {
        return this.codec;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
        return this.streamCodec;
    }

}
