package dev.shadowsoffire.apotheosis.affix;

import java.util.function.IntFunction;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;

public enum AffixType implements StringRepresentable {
    STAT("stat"),
    BASIC_EFFECT("basic_effect"),
    ABILITY("ability");

    public static final IntFunction<AffixType> BY_ID = ByIdMap.continuous(Enum::ordinal, values(), ByIdMap.OutOfBoundsStrategy.CLAMP);
    public static final Codec<AffixType> CODEC = StringRepresentable.fromValues(AffixType::values);
    public static final StreamCodec<ByteBuf, AffixType> STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Enum::ordinal);

    private String name;

    private AffixType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

}
