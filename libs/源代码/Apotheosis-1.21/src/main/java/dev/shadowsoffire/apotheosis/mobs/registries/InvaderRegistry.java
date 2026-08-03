package dev.shadowsoffire.apotheosis.mobs.registries;

import org.jetbrains.annotations.Nullable;

import dev.shadowsoffire.apotheosis.Apotheosis;
import dev.shadowsoffire.apotheosis.mobs.types.Invader;
import dev.shadowsoffire.apotheosis.tiers.Constraints;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.apotheosis.tiers.TieredDynamicRegistry;

public class InvaderRegistry extends TieredDynamicRegistry<Invader> {

    public static final InvaderRegistry INSTANCE = new InvaderRegistry();

    public InvaderRegistry() {
        super(Apotheosis.LOGGER, "apothic_invaders", false, false);
    }

    @Override
    protected void registerBuiltinCodecs() {
        this.registerDefaultCodec(Apotheosis.loc("invader"), Invader.CODEC);
    }

    @Override
    @Nullable
    public Invader getRandomItem(GenContext ctx) {
        return this.getRandomItem(ctx, Constraints.eval(ctx));
    }

}
