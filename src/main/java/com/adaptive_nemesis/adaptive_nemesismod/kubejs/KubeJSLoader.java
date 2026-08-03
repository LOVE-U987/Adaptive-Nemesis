package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

/**
 * KubeJS 安全加载检测 (Forge 1.20.1 / KubeJS 2001.x)
 */
public class KubeJSLoader {

    private static Boolean kubejsLoaded = null;

    public static boolean isKubeJSLoaded() {
        if (kubejsLoaded == null) {
            kubejsLoaded = probe("dev.latvian.mods.kubejs.KubeJS")
                    || probe("dev.latvian.mods.kubejs.KubeJSPlugin")
                    || probe("dev.latvian.mods.kubejs.script.ScriptType");
        }
        return kubejsLoaded;
    }

    private static boolean probe(String className) {
        try {
            Class.forName(className, false, KubeJSLoader.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void reset() {
        kubejsLoaded = null;
    }
}
