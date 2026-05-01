package dev.shadowsoffire.apotheosis.affix.reforging;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.shadowsoffire.apotheosis.Apoth.RecipeTypes;
import dev.shadowsoffire.apotheosis.loot.LootRarity;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public record ReforgingRecipe(DynamicHolder<LootRarity> rarity, int matCost, int sigilCost, int levelCost, HolderSet<Block> tables) implements Recipe<RecipeInput> {

    public static final MapCodec<ReforgingRecipe> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
        RarityRegistry.INSTANCE.holderCodec().fieldOf("rarity").forGetter(ReforgingRecipe::rarity),
        Codec.intRange(1, 99).fieldOf("material_cost").forGetter(ReforgingRecipe::matCost),
        Codec.intRange(0, 99).fieldOf("sigil_cost").forGetter(ReforgingRecipe::sigilCost),
        Codec.intRange(0, 65536).fieldOf("level_cost").forGetter(ReforgingRecipe::levelCost),
        RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("tables").forGetter(ReforgingRecipe::tables))
        .apply(inst, ReforgingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReforgingRecipe> STREAM_CODEC = StreamCodec.composite(
        RarityRegistry.INSTANCE.holderStreamCodec(), ReforgingRecipe::rarity,
        ByteBufCodecs.VAR_INT, ReforgingRecipe::matCost,
        ByteBufCodecs.VAR_INT, ReforgingRecipe::sigilCost,
        ByteBufCodecs.VAR_INT, ReforgingRecipe::levelCost,
        ByteBufCodecs.holderSet(Registries.BLOCK), ReforgingRecipe::tables,
        ReforgingRecipe::new);

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public RecipeType<?> getType() {
        return RecipeTypes.REFORGING;
    }

    public static class Serializer implements RecipeSerializer<ReforgingRecipe> {

        public static final Serializer INSTANCE = new Serializer();

        @Override
        public MapCodec<ReforgingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ReforgingRecipe> streamCodec() {
            return STREAM_CODEC;
        }

    }

    @Override
    @Deprecated
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    @Deprecated
    public ItemStack assemble(RecipeInput input, Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    @Deprecated
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    @Deprecated
    public ItemStack getResultItem(Provider registries) {
        return ItemStack.EMPTY;
    }

}
