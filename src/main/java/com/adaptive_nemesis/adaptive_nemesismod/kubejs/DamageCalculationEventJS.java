package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * 伤害计算事件
 *
 * 当自适应宿敌模组计算真实伤害时触发。
 * KubeJS 脚本可以监听此事件来：
 * - 修改最终伤害数值
 * - 根据自定义条件调整伤害
 * - 添加额外的伤害效果
 * - 完全取消真实伤害转换
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class DamageCalculationEventJS implements KubeEvent {

    /**
     * 攻击者
     */
    private final LivingEntity attacker;

    /**
     * 目标
     */
    private final LivingEntity target;

    /**
     * 原始伤害
     */
    private final float originalDamage;

    /**
     * 计算后的伤害（可被修改）
     */
    private float calculatedDamage;

    /**
     * 护甲倍率
     */
    private final double armorMultiplier;

    /**
     * 是否取消真实伤害转换
     */
    private boolean cancelled = false;

    /**
     * 构造函数
     *
     * @param attacker 攻击者
     * @param target 目标
     * @param originalDamage 原始伤害
     * @param calculatedDamage 计算后的伤害
     * @param armorMultiplier 护甲倍率
     */
    public DamageCalculationEventJS(LivingEntity attacker, LivingEntity target,
                                     float originalDamage, float calculatedDamage,
                                     double armorMultiplier) {
        this.attacker = attacker;
        this.target = target;
        this.originalDamage = originalDamage;
        this.calculatedDamage = calculatedDamage;
        this.armorMultiplier = armorMultiplier;
    }

    /**
     * 获取攻击者
     *
     * @return 攻击者实体
     */
    public LivingEntity getAttacker() {
        return attacker;
    }

    /**
     * 获取目标
     *
     * @return 目标实体
     */
    public LivingEntity getTarget() {
        return target;
    }

    /**
     * 获取原始伤害
     *
     * @return 原始伤害值
     */
    public float getOriginalDamage() {
        return originalDamage;
    }

    /**
     * 获取计算后的伤害
     *
     * @return 当前计算伤害值
     */
    public float getCalculatedDamage() {
        return calculatedDamage;
    }

    /**
     * 设置计算后的伤害
     *
     * @param damage 新的伤害值
     */
    public void setCalculatedDamage(float damage) {
        this.calculatedDamage = Math.max(0.0f, damage);
    }

    /**
     * 增加伤害
     *
     * @param amount 增加的数值
     */
    public void addDamage(float amount) {
        this.calculatedDamage += amount;
    }

    /**
     * 获取护甲倍率
     *
     * @return 护甲倍率
     */
    public double getArmorMultiplier() {
        return armorMultiplier;
    }

    /**
     * 检查攻击者是否是玩家
     *
     * @return 如果是玩家返回 true
     */
    public boolean isAttackerPlayer() {
        return attacker instanceof Player;
    }

    /**
     * 检查目标是否是玩家
     *
     * @return 如果是玩家返回 true
     */
    public boolean isTargetPlayer() {
        return target instanceof Player;
    }

    /**
     * 获取攻击者名称
     *
     * @return 攻击者名称
     */
    public String getAttackerName() {
        return attacker.getName().getString();
    }

    /**
     * 获取目标名称
     *
     * @return 目标名称
     */
    public String getTargetName() {
        return target.getName().getString();
    }

    /**
     * 取消真实伤害转换
     */
    public void cancelEvent() {
        this.cancelled = true;
    }

    /**
     * 检查是否已取消
     *
     * @return 如果已取消返回 true
     */
    public boolean isEventCancelled() {
        return cancelled;
    }
}
