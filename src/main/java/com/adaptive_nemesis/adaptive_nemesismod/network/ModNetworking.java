package com.adaptive_nemesis.adaptive_nemesismod.network;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 模组网络系统
 * 
 * 负责注册和管理网络通信
 * 用于客户端-服务端数据同步
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
        
        AdaptiveNemesisMod.LOGGER.info("📡 网络系统已注册");
    }
    
    /**
     * 注册数据包处理器
     * 
     * @param event 注册数据包处理器事件
     */
    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(AdaptiveNemesisMod.MODID)
            .versioned(PROTOCOL_VERSION);
        
        // 在这里注册自定义数据包
        // 例如：同步玩家强度数据、同步配置等
        
        AdaptiveNemesisMod.LOGGER.debug("数据包处理器已注册");
    }
}
