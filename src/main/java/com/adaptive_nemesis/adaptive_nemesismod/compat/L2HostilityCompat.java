package com.adaptive_nemesis.adaptive_nemesismod.compat;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.fml.ModList;

public class L2HostilityCompat {

    private static Boolean loaded = null;

    private static final ResourceLocation TANK_HEALTH_ID = ResourceLocation.parse("l2hostility:tank_health");

    private L2HostilityCompat() {}

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("l2hostility");
            if (loaded) {
                AdaptiveNemesisMod.LOGGER.info("✅ 检测到 L2Hostility (莱特兰恶意) 模组，已启用兼容模式");
            }
        }
        return loaded;
    }

    /**
     * 检查是否应跳过自适应模组的血量/速度缩放
     * 需同时满足：
     * 1. L2Hostility 模组已加载
     * 2. 配置中的 L2Hostility 兼容模式已启用
     * 
     * @return 如果应跳过缩放则返回 true
     */
    public static boolean shouldSkipHealthAndSpeedScaling() {
        return isLoaded() && Config.MOD_COMPAT_L2HOSTILITY_ENABLED.get();
    }

    /**
     * 检测生物是否已被 L2Hostility 的 TANK 特质处理过
     * 
     * L2Hostility 的 TANK 特质会对 MAX_HEALTH 添加一个名为 "tank_health" 
     * 的 ADD_MULTIPLIED_TOTAL 属性修改器。当 Adaptive-Nemesis 同时修改基础血量时，
     * 两者会乘法叠加导致血量爆炸。
     * 
     * @param entity 目标生物
     * @return 如果生物已被 L2Hostility 强化则返回 true
     */
    public static boolean hasHostilityScaling(LivingEntity entity) {
        if (!isLoaded()) {
            return false;
        }

        AttributeInstance healthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) {
            return false;
        }

        for (AttributeModifier modifier : healthAttr.getModifiers()) {
            if (TANK_HEALTH_ID.equals(modifier.id())) {
                return true;
            }
        }
        return false;
    }
}