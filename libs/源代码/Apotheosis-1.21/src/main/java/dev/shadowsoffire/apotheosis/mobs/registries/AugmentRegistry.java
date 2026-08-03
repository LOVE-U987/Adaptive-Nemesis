package dev.shadowsoffire.apotheosis.mobs.registries;

import java.util.Set;

import dev.shadowsoffire.apotheosis.Apotheosis;
import dev.shadowsoffire.apotheosis.mobs.types.Augmentation;
import dev.shadowsoffire.placebo.reload.DynamicRegistry;

public class AugmentRegistry extends DynamicRegistry<Augmentation> {

    public static final AugmentRegistry INSTANCE = new AugmentRegistry();

    public AugmentRegistry() {
        super(Apotheosis.LOGGER, "apothic_augments", false, false);
    }

    @Override
    protected void registerBuiltinCodecs() {
        this.registerDefaultCodec(Apotheosis.loc("augment"), Augmentation.CODEC);
    }

    public static Set<Augmentation> getAll() {
        return INSTANCE.registry.values();
    }

}
