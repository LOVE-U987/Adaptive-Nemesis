package dev.shadowsoffire.apotheosis.spawner;

import dev.shadowsoffire.apotheosis.Apotheosis;
import dev.shadowsoffire.placebo.reload.WeightedDynamicRegistry;

public class RogueSpawnerRegistry extends WeightedDynamicRegistry<RogueSpawner> {

    public static final RogueSpawnerRegistry INSTANCE = new RogueSpawnerRegistry();

    public RogueSpawnerRegistry() {
        super(Apotheosis.LOGGER, "rogue_spawners", false, false);
    }

    @Override
    protected void registerBuiltinCodecs() {
        this.registerDefaultCodec(Apotheosis.loc("rogue_spawner"), RogueSpawner.CODEC);
    }

}
