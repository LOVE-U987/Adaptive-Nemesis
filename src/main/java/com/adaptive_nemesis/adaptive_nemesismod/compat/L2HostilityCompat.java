package com.adaptive_nemesis.adaptive_nemesismod.compat;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.fml.ModList;

/**
 * L2Hostility (莱特兰恶意) 兼容处理器
 *
 * 当 L2Hostility 模组在场时，双方都会对怪物做属性强化，
 * 血量等乘法属性会双重叠加以致血量爆炸。本类提供跳过
 * 自适应缩放的判定：已被 L2Hostility 强化过（TANK 特质）的
 * 实体不再叠加本模组的血量/速度缩放。
 *
 * 加载判定与配置开关分离：{@link #isLoaded()} 缓存 ModList
 * 探测结果（探测结果运行期不变），配置开关
 * {@link Config#MOD_COMPAT_L2HOSTILITY_ENABLED} 在
 * {@link #shouldSkipHealthAndSpeedScaling()} 中每次现读。
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class L2HostilityCompat {

    /**
     * L2Hostility 模组加载状态缓存（null 表示尚未探测）
     */
    private static Boolean loaded = null;

    /**
     * L2Hostility TANK 特质添加的血量修改器 ID：
     * MAX_HEALTH 上名为 "l2hostility:tank_health" 的属性修改器
     */
    private static final ResourceLocation TANK_HEALTH_ID = ResourceLocation.parse("l2hostility:tank_health");

    /**
     * 私有构造函数 - 纯静态工具类，禁止实例化
     */
    private L2HostilityCompat() {}

    /**
     * 检查 L2Hostility 模组是否已加载
     *
     * 探测结果仅查询一次并缓存；未探测前调用 {@code ModList.get()}。
     *
     * @return 如果已加载返回 true
     */
    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded("l2hostility");
            if (loaded) {
                AdaptiveNemesisMod.LOGGER.info("检测到 L2Hostility (莱特兰恶意) 模组，已启用兼容模式");
            }
        }
        return loaded;
    }

    /**
     * 检查是否应跳过自适应模组的血量/速度缩放
     * 需同时满足：
     * 1. L2Hostility 模组已加载
     * 2. 配置中的 L2Hostility 兼容模式已启用（每次现读，运行期切换即时生效）
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
