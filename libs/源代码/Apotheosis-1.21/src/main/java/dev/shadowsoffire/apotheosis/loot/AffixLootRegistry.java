package dev.shadowsoffire.apotheosis.loot;

import org.jetbrains.annotations.Nullable;

import com.google.common.base.Preconditions;

import dev.shadowsoffire.apotheosis.Apotheosis;
import dev.shadowsoffire.apotheosis.tiers.Constraints;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.apotheosis.tiers.TieredDynamicRegistry;
import net.minecraft.resources.ResourceLocation;

/**
 * Core loot registry. Handles the management of all Affixes, LootEntries, and generation of loot items.
 */
public class AffixLootRegistry extends TieredDynamicRegistry<AffixLootEntry> {

    public static final AffixLootRegistry INSTANCE = new AffixLootRegistry();

    private AffixLootRegistry() {
        super(Apotheosis.LOGGER, "affix_loot_entries", false, false);
    }

    @Override
    protected void registerBuiltinCodecs() {
        this.registerDefaultCodec(Apotheosis.loc("affix_loot_entry"), AffixLootEntry.CODEC);
    }

    @Override
    protected void validateItem(ResourceLocation key, AffixLootEntry item) {
        super.validateItem(key, item);
        Preconditions.checkArgument(!item.getType().isNone(), "Items without a valid loot category are not permitted.");
    }

    @Override
    @Nullable
    public AffixLootEntry getRandomItem(GenContext ctx) {
        return this.getRandomItem(ctx, Constraints.eval(ctx));
    }

}
