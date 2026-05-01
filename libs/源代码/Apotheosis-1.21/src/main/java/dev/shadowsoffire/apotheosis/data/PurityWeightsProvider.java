package dev.shadowsoffire.apotheosis.data;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

import dev.shadowsoffire.apotheosis.socket.gem.Purity;
import dev.shadowsoffire.apotheosis.socket.gem.PurityWeightsRegistry;
import dev.shadowsoffire.apotheosis.socket.gem.PurityWeightsRegistry.PurityWeights;
import dev.shadowsoffire.apotheosis.tiers.TieredWeights.Weight;
import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import dev.shadowsoffire.placebo.util.data.DynamicRegistryProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;

public class PurityWeightsProvider extends DynamicRegistryProvider<PurityWeights> {

    public PurityWeightsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, PurityWeightsRegistry.INSTANCE);
    }

    @Override
    public String getName() {
        return "Purity Weights";
    }

    @Override
    public void generate() {
        // The total base weight for each tier is 100

        this.add(b -> b
            .tier(WorldTier.HAVEN, c -> c
                .with(Purity.CRACKED, 550, 0)
                .with(Purity.CHIPPED, 410, 2.5F)
                .with(Purity.FLAWED, 40, 5F)
                .with(Purity.NORMAL, 0, 0)
                .with(Purity.FLAWLESS, 0, 0)
                .with(Purity.PERFECT, 0, 0))
            .tier(WorldTier.FRONTIER, c -> c
                .with(Purity.CRACKED, 230, 0)
                .with(Purity.CHIPPED, 540, 0)
                .with(Purity.FLAWED, 180, 2.5F)
                .with(Purity.NORMAL, 50, 1F)
                .with(Purity.FLAWLESS, 0, 0)
                .with(Purity.PERFECT, 0, 0))
            .tier(WorldTier.ASCENT, c -> c
                .with(Purity.CRACKED, 20, 0)
                .with(Purity.CHIPPED, 250, 0)
                .with(Purity.FLAWED, 560, 2.5F)
                .with(Purity.NORMAL, 160, 5F)
                .with(Purity.FLAWLESS, 10, 1F)
                .with(Purity.PERFECT, 0, 0))
            .tier(WorldTier.SUMMIT, c -> c
                .with(Purity.CRACKED, 0, 0)
                .with(Purity.CHIPPED, 80, 0)
                .with(Purity.FLAWED, 310, 2.5F)
                .with(Purity.NORMAL, 580, 5F)
                .with(Purity.FLAWLESS, 20, 5F)
                .with(Purity.PERFECT, 10, 0))
            .tier(WorldTier.PINNACLE, c -> c
                .with(Purity.CRACKED, 0, 0)
                .with(Purity.CHIPPED, 0, 0)
                .with(Purity.FLAWED, 100, 0)
                .with(Purity.NORMAL, 330, 0)
                .with(Purity.FLAWLESS, 470, 0)
                .with(Purity.PERFECT, 100, 5F)));

    }

    private void add(UnaryOperator<Builder> config) {
        this.add(PurityWeightsRegistry.TARGET_FILE, config.apply(new Builder()).build());
    }

    private static class Builder {
        Map<WorldTier, Map<Purity, Weight>> weightsMap = new HashMap<>();

        public Builder tier(WorldTier tier, UnaryOperator<InnerBuilder> config) {
            this.weightsMap.put(tier, config.apply(new InnerBuilder()).innerMap);
            return this;
        }

        public PurityWeights build() {
            return new PurityWeights(this.weightsMap);
        }

    }

    private static class InnerBuilder {
        Map<Purity, Weight> innerMap = new HashMap<>();

        InnerBuilder with(Purity purity, int weight, float quality) {
            this.innerMap.put(purity, new Weight(weight, quality));
            return this;
        }
    }
}
