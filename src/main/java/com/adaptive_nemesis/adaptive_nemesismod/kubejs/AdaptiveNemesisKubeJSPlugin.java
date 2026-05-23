package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

/**
 * KubeJS 插件桥接类
 *
 * 此类仅作为桥接标记，不直接引用任何 KubeJS 类以避免类加载冲突。
 * 实际的 KubeJS 注册逻辑在 {@link KubeJSInitializer} 中实现，
 * 该类通过 KubeJS 的 ServiceLoader 机制自动发现和加载。
 *
 * 当 KubeJS 未安装时，此类始终安全运行，不会触发任何类加载错误。
 * 当 KubeJS 已安装时，KubeJSInitializer 通过 META-INF/services 自动注册。
 *
 * @author Adaptive Nemesis Team
 * @version 1.2.0
 */
public class AdaptiveNemesisKubeJSPlugin {

    private static boolean initialized = false;

    /**
     * 初始化 KubeJS 桥接
     * 仅在 KubeJS 已加载时通过反射初始化 KubeJSInitializer
     */
    public static void ensureInitialized() {
        if (initialized) return;
        if (!KubeJSLoader.isKubeJSLoaded()) return;

        try {
            Class.forName("com.adaptive_nemesis.adaptive_nemesismod.kubejs.KubeJSInitializer");
            initialized = true;
        } catch (ClassNotFoundException e) {
            AdaptiveNemesisMod.LOGGER.warn("KubeJSInitializer 类未找到，KubeJS 事件不可用");
        }
    }

    /**
     * 检查 KubeJS 是否已初始化
     */
    public static boolean isInitialized() {
        return initialized;
    }
}