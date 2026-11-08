package com.adaptive_nemesis.adaptive_nemesismod.damage;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组伤害类型注册器
 *
 * 注册 adaptive_nemesis:true_damage 伤害类型，供真实伤害转化机制使用：
 * - bypasses_armor 标签：真实伤害不受护甲减免（铁乌龟终结者的核心语义，
 *   与原版 magic 类型同路径，processor 侧跳过护甲吸收）
 * - bypasses_cooldown 标签：真实伤害不进入伤害无敌帧冷却。
 *   注意：该标签自 1.21.2 起才有消费点，1.21.1 下仅注册数据不生效，
 *   无敌帧绕过由 TrueDamageHandler 在延迟任务中反射清零 invulnerableTime 兜底
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class ModDamageTypes {

    /**
     * true_damage 伤害类型的注册键
     * 供伤害源构建与 LivingIncomingDamageEvent 再入排除检测使用
     */
    public static final ResourceKey<DamageType> TRUE_DAMAGE = ResourceKey.create(
        Registries.DAMAGE_TYPE,
        ResourceLocation.fromNamespaceAndPath(AdaptiveNemesisMod.MODID, "true_damage")
    );

    /**
     * 将 true_damage 伤害类型注册到模组事件总线
     *
     * 注册动作整体延迟到本方法调用时（模组构造阶段）执行：类静态初始化仅保留
     * 纯键常量（ResourceKey），不触注册表与 DeferredHolder 绑定，
     * 保证纯 JVM 环境（单元测试）可安全加载本类。
     *
     * DamageType 实例参数与原版 magic 类型保持一致（exhaustion 0.1、
     * 非玩家施压时随难度缩放），保证玩家饥饿值消耗与难度缩放行为与旧版 magic 源等价
     *
     * @param modEventBus 模组事件总线
     */
    public static void register(IEventBus modEventBus) {
        DeferredRegister<DamageType> damageTypes =
            DeferredRegister.create(Registries.DAMAGE_TYPE, AdaptiveNemesisMod.MODID);
        damageTypes.register(
            "true_damage",
            () -> new DamageType(
                "adaptive_nemesis.true_damage",
                DamageScaling.WHEN_CAUSED_BY_LIVING_NON_PLAYER,
                0.1F
            )
        );
        damageTypes.register(modEventBus);
    }
}
