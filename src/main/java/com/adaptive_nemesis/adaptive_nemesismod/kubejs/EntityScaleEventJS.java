package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.world.entity.Mob;

/**
 * 实体强化事件
 *
 * 当自适应宿敌模组对实体进行属性强化时触发。
 * KubeJS 脚本可以监听此事件来：
 * - 修改强化倍率
 * - 添加自定义属性加成
 * - 取消特定实体的强化
 * - 根据自定义逻辑调整强化效果
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class EntityScaleEventJS implements KubeEvent {

    /**
     * 被强化的实体
     */
    private final Mob entity;

    /**
     * 当前强化倍率（可被修改）
     */
    private double multiplier;

    /**
     * 是否取消强化
     */
    private boolean cancelled = false;

    /**
     * 构造函数
     *
     * @param entity 被强化的实体
     * @param multiplier 初始强化倍率
     */
    public EntityScaleEventJS(Mob entity, double multiplier) {
        this.entity = entity;
        this.multiplier = multiplier;
    }

    /**
     * 获取被强化的实体
     *
     * @return 实体对象
     */
    public Mob getEntity() {
        return entity;
    }

    /**
     * 获取实体ID
     *
     * @return 实体ID字符串
     */
    public String getEntityId() {
        return entity.getType().toString();
    }

    /**
     * 获取实体显示名称
     *
     * @return 实体名称
     */
    public String getEntityName() {
        return entity.getName().getString();
    }

    /**
     * 获取当前强化倍率
     *
     * @return 强化倍率
     */
    public double getMultiplier() {
        return multiplier;
    }

    /**
     * 设置强化倍率
     *
     * @param multiplier 新的强化倍率
     */
    public void setMultiplier(double multiplier) {
        this.multiplier = Math.max(1.0, multiplier);
    }

    /**
     * 增加强化倍率
     *
     * @param amount 增加的数值
     */
    public void addMultiplier(double amount) {
        this.multiplier += amount;
    }

    /**
     * 乘以强化倍率
     *
     * @param factor 乘数
     */
    public void multiplyMultiplier(double factor) {
        this.multiplier *= factor;
    }

    /**
     * 取消强化
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

    /**
     * 获取实体当前生命值
     *
     * @return 生命值
     */
    public double getHealth() {
        return entity.getHealth();
    }

    /**
     * 获取实体最大生命值
     *
     * @return 最大生命值
     */
    public double getMaxHealth() {
        return entity.getMaxHealth();
    }

    /**
     * 获取实体基础攻击力
     *
     * @return 攻击力
     */
    public double getAttackDamage() {
        var attr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        return attr != null ? attr.getBaseValue() : 0.0;
    }

    /**
     * 获取实体基础护甲值
     *
     * @return 护甲值
     */
    public double getArmor() {
        var attr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        return attr != null ? attr.getBaseValue() : 0.0;
    }

    /**
     * 设置实体基础攻击力
     *
     * @param damage 新的攻击力
     */
    public void setAttackDamage(double damage) {
        var attr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (attr != null) {
            attr.setBaseValue(damage);
        }
    }

    /**
     * 设置实体基础生命值
     *
     * @param health 新的生命值
     */
    public void setMaxHealth(double health) {
        var attr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(health);
            entity.setHealth((float) health);
        }
    }
}
