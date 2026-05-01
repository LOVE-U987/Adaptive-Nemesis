package dev.shadowsoffire.apotheosis.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import net.neoforged.neoforge.event.EventHooks;

@Mixin(value = EndDragonFight.class, remap = false)
public class EndDragonFightMixin {

    @Inject(method = "createNewDragon", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private void apoth_finalizeEnderDragons(CallbackInfoReturnable<EnderDragon> ci, @Local EnderDragon dragon) {
        DifficultyInstance difficulty = dragon.level().getCurrentDifficultyAt(dragon.blockPosition());
        EventHooks.finalizeMobSpawn(dragon, (ServerLevelAccessor) dragon.level(), difficulty, MobSpawnType.EVENT, null);
    }

}
