package com.adaptive_nemesis.adaptive_nemesismod.client;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 客户端事件处理器
 *
 * 处理客户端专属初始化：
 * - 注册配置界面工厂（模组列表中的"配置"按钮）
 * - 客户端资源初始化
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
@EventBusSubscriber(modid = AdaptiveNemesisMod.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    /**
     * 客户端设置事件处理
     * 注册配置屏幕工厂，使模组列表中显示"配置"按钮
     *
     * @param event 客户端设置事件
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 获取当前模组的 ModContainer
        event.enqueueWork(() -> {
            net.neoforged.fml.ModList.get().getModContainerById(AdaptiveNemesisMod.MODID)
                .ifPresent(modContainer -> {
                    // 注册配置屏幕工厂
                    // IConfigScreenFactory 是函数式接口，构造函数参数为 (ModContainer, Screen)
                    modContainer.registerExtensionPoint(
                        IConfigScreenFactory.class,
                        (container, parent) -> new AdaptiveNemesisConfigScreen(parent)
                    );

                    AdaptiveNemesisMod.LOGGER.info("✅ 配置界面已注册到模组列表");
                });
        });
    }
}
