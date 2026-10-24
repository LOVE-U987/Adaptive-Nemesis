package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

/**
 * KubeJS 桥接标记类（遗留，无注册作用）。
 *
 * <p><strong>注意</strong>：本类<strong>不实现</strong> {@code dev.latvian.mods.kubejs.plugin.KubeJSPlugin}
 * 接口，因此不能也不应写进 {@code kubejs.plugins.txt} —— KubeJS 加载插件时用
 * {@code KubeJSPlugin.class::isAssignableFrom} 过滤非实现类（静默跳过）。
 * 历史上本类曾被错误地注册在 {@code kubejs.plugins.txt} 中，导致整个 KubeJS 联动
 * （事件组注册）静默失效：脚本中监听 {@code adaptive_nemesis.*} 事件完全无效。</p>
 *
 * <p>实际的 KubeJS 注册入口是 {@link KubeJSInitializer}（实现 {@code KubeJSPlugin}），
 * 通过 {@code src/main/resources/kubejs.plugins.txt} 注册。KubeJS 2101.x 不使用
 * Java ServiceLoader（{@code META-INF/services} 注册方式无效，已移除）。</p>
 *
 * <p>本类当前无调用者，仅保留作历史标记；未来可安全删除。</p>
 *
 * @author Adaptive Nemesis Team
 * @version 1.2.1
 */
public class AdaptiveNemesisKubeJSPlugin {

    private static boolean initialized = false;

    /**
     * 初始化 KubeJS 桥接（遗留方法，当前无调用者）。
     * 仅在 KubeJS 已加载时通过反射触发 KubeJSInitializer 类加载。
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
