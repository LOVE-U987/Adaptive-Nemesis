package dev.shadowsoffire.apotheosis.loot.conditions;

import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.MapCodec;

import dev.shadowsoffire.apotheosis.Apoth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Checks that the {@link LootContextParams#ATTACKING_ENTITY attacker} in a loot context is a real player.
 * <p>
 * Fake players (instances of {@link FakePlayer}) will fail this check.
 */
public class KilledByRealPlayerCondition implements LootItemCondition {

    public static final KilledByRealPlayerCondition INSTANCE = new KilledByRealPlayerCondition();
    public static final MapCodec<KilledByRealPlayerCondition> CODEC = MapCodec.unit(INSTANCE);

    private KilledByRealPlayerCondition() {}

    @Override
    public LootItemConditionType getType() {
        return Apoth.LootConditions.KILLED_BY_REAL_PLAYER;
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return ImmutableSet.of(LootContextParams.ATTACKING_ENTITY);
    }

    @Override
    public boolean test(LootContext context) {
        Entity attacker = context.getParamOrNull(LootContextParams.ATTACKING_ENTITY);
        return attacker instanceof Player && !(attacker instanceof FakePlayer);
    }

    public static LootItemCondition.Builder killedByPlayer() {
        return () -> INSTANCE;
    }
}
