package dev.shadowsoffire.apotheosis.mixin;

import java.util.LinkedHashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.neoforged.neoforge.common.conditions.WithConditions;
import net.neoforged.neoforge.common.data.GlobalLootModifierProvider;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;

// Fixes https://github.com/neoforged/NeoForge/issues/1828
@Mixin(value = GlobalLootModifierProvider.class, remap = false)
public class GLMProviderMixin {

    @Shadow
    private final Map<String, WithConditions<IGlobalLootModifier>> toSerialize = new LinkedHashMap<>();
}
