package dev.shadowsoffire.apotheosis.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SpawnData;

@Mixin(value = BaseSpawner.class, remap = false)
public interface BaseSpawnerAccessor {

    @Invoker
    void callSetNextSpawnData(@Nullable Level level, BlockPos pos, SpawnData data);

}
