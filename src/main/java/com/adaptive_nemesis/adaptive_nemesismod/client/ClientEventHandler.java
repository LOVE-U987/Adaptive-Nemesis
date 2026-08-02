package com.adaptive_nemesis.adaptive_nemesismod.client;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端事件处理器 — 注册配置界面
 */
@Mod.EventBusSubscriber(modid = AdaptiveNemesisMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ModList.get().getModContainerById(AdaptiveNemesisMod.MODID).ifPresent(container ->
                    container.registerExtensionPoint(
                            ConfigScreenHandler.ConfigScreenFactory.class,
                            () -> new ConfigScreenHandler.ConfigScreenFactory(
                                    (mc, parent) -> new AdaptiveNemesisConfigScreen(parent)
                            )
                    )
            );
            AdaptiveNemesisMod.LOGGER.debug("✅ 配置界面已注册到模组列表");
        });
    }
}
