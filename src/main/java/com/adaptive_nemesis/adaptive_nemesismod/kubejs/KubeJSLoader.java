package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

/**
 * KubeJS 安全加载检测
 *
 * 不直接引用任何 KubeJS 类，避免类加载失败导致整个模组崩溃。
 * 仅在需要时通过 Class.forName 检查 KubeJS 是否可用。
 */
public class KubeJSLoader {

    private static Boolean kubejsLoaded = null;

    /**
     * 检查 KubeJS 是否已加载
     * 使用反射方式避免类加载失败
     */
    public static boolean isKubeJSLoaded() {
        if (kubejsLoaded == null) {
            try {
                Class.forName("dev.latvian.mods.kubejs.plugin.KubeJSPlugin", false, KubeJSLoader.class.getClassLoader());
                kubejsLoaded = true;
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                kubejsLoaded = false;
            }
        }
        return kubejsLoaded;
    }

    /**
     * 创建 KubeJS 事件实例（安全方式）
     */
    public static Object createEvent(String className, Class<?>... paramTypes) {
        if (!isKubeJSLoaded()) {
            return null;
        }
        try {
            Class<?> eventClass = Class.forName("com.adaptive_nemesis.adaptive_nemesismod.kubejs." + className);
            return eventClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 清除缓存（在重载时调用）
     */
    public static void reset() {
        kubejsLoaded = null;
    }
}