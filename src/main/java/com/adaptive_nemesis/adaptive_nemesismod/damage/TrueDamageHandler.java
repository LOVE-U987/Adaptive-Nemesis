package com.adaptive_nemesis.adaptive_nemesismod.damage;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.kubejs.KubeJSEventTrigger;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 真实伤害转化处理器
 *
 * 铁乌龟终结者机制：
 * 针对高护甲玩家，将部分伤害强制转化为真实伤害（无视护甲）
 *
 * 真实伤害比例基于护甲值相对于基准护甲的倍率计算：
 * - 护甲值 <= 基准护甲 → 基础比例
 * - 护甲值 > 基准护甲 → 按比例增加真实伤害
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class TrueDamageHandler {

    /**
     * 单例实例
     */
    private static TrueDamageHandler INSTANCE;

    /**
     * 私有构造函数 - 单例模式
     */
    private TrueDamageHandler() {}

    /**
     * 获取单例实例
     *
     * @return TrueDamageHandler 实例
     */
    public static synchronized TrueDamageHandler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TrueDamageHandler();
        }
        return INSTANCE;
    }

    /**
     * 处理实体受到伤害事件
     *
     * @param event 实体受到伤害事件
     */
    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        // 检查是否启用真实伤害
        if (!Config.ENABLE_TRUE_DAMAGE.get()) {
            return;
        }

        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        // 只处理玩家受到伤害的情况
        if (!(target instanceof ServerPlayer player)) {
            return;
        }

        // 检查伤害来源是否是生物攻击
        if (source.getEntity() == null || !(source.getEntity() instanceof LivingEntity attacker)) {
            return;
        }

        // 跳过已无视护甲或本身就是魔法/真实伤害的伤害源
        // 这些伤害已经不受护甲减免，无需再转化，也可避免递归或重复计算
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }

        // 获取玩家护甲值
        double armorValue = player.getAttributeValue(Attributes.ARMOR);

        // 根据护甲值计算真实伤害比例
        double trueDamagePercent = getTrueDamagePercent(armorValue);

        if (trueDamagePercent <= 0) {
            return;
        }

        // 计算原始伤害
        float originalDamage = event.getAmount();

        // 计算真实伤害部分
        float trueDamageAmount = originalDamage * (float) (trueDamagePercent / 100.0);
        
        // 触发 KubeJS 伤害计算事件
        float modifiedDamage = KubeJSEventTrigger.triggerDamageCalculation(
            attacker, player, originalDamage, trueDamageAmount, armorValue / Config.LOW_ARMOR_THRESHOLD.get()
        );
        
        // 如果 KubeJS 取消了真实伤害转换
        if (modifiedDamage == originalDamage) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "玩家 {} 的真实伤害转化被 KubeJS 事件取消",
                    player.getName().getString()
                );
            }
            return;
        }
        
        // 使用 KubeJS 修改后的伤害
        trueDamageAmount = modifiedDamage;
        float reducedDamage = originalDamage - trueDamageAmount;

        // 如果真实伤害大于0，直接对玩家造成无视护甲的伤害
        if (trueDamageAmount > 0.01f) {
            // 创建无视护甲的伤害源
            DamageSource trueDamageSource = player.level().damageSources().magic();

            // 先取消原始事件的部分伤害
            event.setAmount(reducedDamage);

            // 应用真实伤害（延迟一tick执行，避免递归）
            final float finalTrueDamage = trueDamageAmount;
            player.getServer().execute(() -> {
                if (player.isAlive()) {
                    player.hurt(trueDamageSource, finalTrueDamage);
                }
            });

            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "玩家 {} 受到真实伤害转化: 原始={}, 真实比例={}%, 真实伤害={}, 剩余={}",
                    player.getName().getString(),
                    String.format("%.2f", originalDamage),
                    String.format("%.1f", trueDamagePercent),
                    String.format("%.2f", trueDamageAmount),
                    String.format("%.2f", reducedDamage)
                );
            }
        }
    }

    /**
     * 根据护甲值获取真实伤害比例
     *
     * 按配置中的低/中/高护甲阈值分段计算真实伤害比例，
     * 使真实伤害与护甲等级匹配，避免线性插值偏离设计预期。
     *
     * @param armorValue 玩家护甲值
     * @return 真实伤害百分比
     */
    public double getTrueDamagePercent(double armorValue) {
        return calculateTrueDamagePercent(
            armorValue,
            Config.LOW_ARMOR_THRESHOLD.get(),
            Config.LOW_ARMOR_TRUE_DAMAGE_PERCENT.get(),
            Config.MEDIUM_ARMOR_THRESHOLD.get(),
            Config.MEDIUM_ARMOR_TRUE_DAMAGE_PERCENT.get(),
            Config.HIGH_ARMOR_THRESHOLD.get(),
            Config.HIGH_ARMOR_TRUE_DAMAGE_PERCENT.get(),
            Config.TURTLE_TRUE_DAMAGE_PERCENT.get()
        );
    }

    /**
     * 计算真实伤害比例（纯静态逻辑，便于单元测试）
     *
     * @param armorValue     玩家护甲值
     * @param lowThreshold   低护甲阈值
     * @param lowPercent     低护甲真实伤害比例
     * @param mediumThreshold 中护甲阈值
     * @param mediumPercent  中护甲真实伤害比例
     * @param highThreshold  高护甲阈值
     * @param highPercent    高护甲真实伤害比例
     * @param turtlePercent  超高护甲真实伤害比例
     * @return 真实伤害百分比
     */
    public static double calculateTrueDamagePercent(
        double armorValue,
        double lowThreshold, double lowPercent,
        double mediumThreshold, double mediumPercent,
        double highThreshold, double highPercent,
        double turtlePercent
    ) {
        if (armorValue <= lowThreshold) {
            return lowPercent;
        }
        if (armorValue <= mediumThreshold) {
            return lerp(armorValue, lowThreshold, mediumThreshold, lowPercent, mediumPercent);
        }
        if (armorValue <= highThreshold) {
            return lerp(armorValue, mediumThreshold, highThreshold, mediumPercent, highPercent);
        }
        return turtlePercent;
    }

    /**
     * 线性插值辅助方法
     *
     * @param value 当前值
     * @param minValue 区间下限
     * @param maxValue 区间上限
     * @param minResult 区间下限对应结果
     * @param maxResult 区间上限对应结果
     * @return 插值结果
     */
    private static double lerp(double value, double minValue, double maxValue, double minResult, double maxResult) {
        if (maxValue <= minValue) {
            return minResult;
        }
        double t = (value - minValue) / (maxValue - minValue);
        return minResult + t * (maxResult - minResult);
    }

    /**
     * 获取护甲等级描述
     *
     * @param armorValue 玩家护甲值
     * @return 护甲等级描述
     */
    public String getArmorLevelDescription(double armorValue) {
        double baseArmor = Config.LOW_ARMOR_THRESHOLD.get();
        double armorMultiplier = armorValue / baseArmor;

        if (armorMultiplier <= 1.0) {
            return "标准护甲";
        } else if (armorMultiplier <= 2.0) {
            return "强化护甲";
        } else if (armorMultiplier <= 3.0) {
            return "高护甲";
        } else if (armorMultiplier <= 5.0) {
            return "铁乌龟";
        } else {
            return "终极铁乌龟";
        }
    }
}
