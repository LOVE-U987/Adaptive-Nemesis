package com.adaptive_nemesis.adaptive_nemesismod.network;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 模组网络系统占位类
 *
 * 当前版本暂无自定义数据包同步需求，保留此框架以便后续扩展：
 * - 同步玩家强度数据到客户端（用于 HUD 显示）
 * - 同步世界阶段数据到客户端
 * - 同步配置更新
 *
 * TODO: 当新增网络同步需求时，在此处注册具体的 CustomPacketPayload。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class ModNetworking {

    /**
     * 网络协议版本
     */
    public static final String PROTOCOL_VERSION = "1.0.0";

    /**
     * 注册网络处理器
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModNetworking::registerPayloadHandlers);
    }

    /**
     * 注册数据包处理器
     *
     * @param event 注册数据包处理器事件
     */
    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        //noinspection unused
        PayloadRegistrar registrar = event.registrar(AdaptiveNemesisMod.MODID)
            .versioned(PROTOCOL_VERSION);

        // TODO: 在此处注册自定义数据包
    }
}
