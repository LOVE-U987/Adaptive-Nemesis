package dev.shadowsoffire.apotheosis.tiers;

import java.util.Set;

import com.mojang.serialization.Codec;

import dev.shadowsoffire.placebo.codec.PlaceboCodecs;

/**
 * Player attachment which records the world tiers that the player currently has unlocked.
 * TODO: Implement this. Currently world tier unlocks are bound to specific advancements being obtained.
 */
public record UnlockedTiers(Set<WorldTier> tiers) {

    public static final Codec<UnlockedTiers> CODEC = PlaceboCodecs.setOf(WorldTier.CODEC).xmap(UnlockedTiers::new, UnlockedTiers::tiers);

    public boolean contains(WorldTier tier) {
        return this.tiers.contains(tier);
    }

}
