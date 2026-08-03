package com.adaptive_nemesis.adaptive_nemesismod.network;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 模组网络系统占位类 (Forge 1.20.1 SimpleChannel)
 */
public class ModNetworking {

    public static final String PROTOCOL_VERSION = "1.0.0";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(AdaptiveNemesisMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    private static int nextId() {
        return packetId++;
    }

    public static void register() {
        // TODO: register payloads via CHANNEL.messageBuilder when needed
        AdaptiveNemesisMod.LOGGER.debug("网络通道已初始化 (protocol {})", PROTOCOL_VERSION);
    }
}
