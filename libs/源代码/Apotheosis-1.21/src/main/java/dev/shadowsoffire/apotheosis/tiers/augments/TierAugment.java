package dev.shadowsoffire.apotheosis.tiers.augments;

import java.util.function.IntFunction;

import com.mojang.serialization.Codec;

import dev.shadowsoffire.apotheosis.tiers.WorldTier;
import dev.shadowsoffire.placebo.codec.CodecProvider;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;

/**
 * Augmentations applied to all spawned entities when a given world tier is active.
 */
public interface TierAugment extends CodecProvider<TierAugment> {

    /**
     * {@return the world tier this augment is applicable to}
     */
    WorldTier tier();

    /**
     * {@return the target of this augment.}
     */
    Target target();

    /**
     * Returns a number used to place this augment in-order on the tier select window.
     * <p>
     * The default suggested value (when ordering is irrelevant) is 1000.
     */
    int sortIndex();

    /**
     * Applies this tier augment to the target entity.
     */
    void apply(ServerLevelAccessor level, LivingEntity entity);

    /**
     * Removes this tier augment to the target entity.
     * <p>
     * Augments are only unwound for players. Monsters with tier augments retain them indefinitely.
     */
    void remove(ServerLevelAccessor level, LivingEntity entity);

    /**
     * Returns a one-line description for this augment that will be displayed in the tier selection window.
     */
    Component getDescription(AttributeTooltipContext ctx);

    public static enum Target implements StringRepresentable {
        PLAYERS("players"),
        MONSTERS("monsters");

        public static final IntFunction<Target> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO);
        public static final Codec<Target> CODEC = StringRepresentable.fromValues(Target::values);
        public static final StreamCodec<ByteBuf, Target> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

        private String name;

        private Target(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

}
