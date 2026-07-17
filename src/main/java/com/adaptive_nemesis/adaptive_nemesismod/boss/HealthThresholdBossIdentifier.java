package com.adaptive_nemesis.adaptive_nemesismod.boss;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

/**
 * 基于血量阈值的Boss识别策略
 * 
 * 通过实体类型的默认最大生命值判断是否为Boss。
 * 血量阈值可通过配置文件动态调整，适用于识别高血量模组Boss。
 * 
 * 🛡️ 防误判机制：
 * 使用实体类型注册时的默认血量（通过 DefaultAttributes 获取）而非当前血量进行判断。
 * 这可以防止被其他模组（如 Apotheosis 精英系统）增强到高血量的普通怪物
 * 被误识别为Boss并获得Boss额外加成。
 * 
 * 例如：末影人原始血量只有40，但被Apotheosis增强后可以达到200+，
 * 如果使用当前血量判断会被误判为Boss，获得5倍血量和3倍伤害的额外加成，
 * 导致属性爆炸。
 * 
 * 注意：动物/傀儡类（Animal 子类）不受此策略影响，
 * 防止高血量铁傀儡等被误识别为Boss吃到敌对加成。
 * 
 * @author Adaptive Nemesis Team
 * @version 1.2.0
 */
public class HealthThresholdBossIdentifier implements BossIdentifier {

    private final double healthThreshold;

    /**
     * 创建基于血量阈值的Boss识别策略
     * 
     * @param healthThreshold 血量阈值，实体类型默认最大生命值超过此值即视为Boss
     */
    public HealthThresholdBossIdentifier(double healthThreshold) {
        this.healthThreshold = healthThreshold;
    }

    @Override
    public boolean isBoss(LivingEntity entity) {
        // 🚫 动物/傀儡类不参与血量阈值 Boss 识别
        // 防止高血量铁傀儡、高血量动物等被误认为Boss
        if (entity instanceof Animal || entity instanceof AbstractGolem) {
            return false;
        }

        // 🛡️ 使用实体类型的默认血量进行判断，而非当前血量
        // 防止被其他模组（如Apotheosis）增强的普通怪物误判为Boss
        double defaultHealth = getDefaultHealth(entity);

        return defaultHealth >= healthThreshold;
    }

    /**
     * 获取实体类型的默认最大生命值
     * 
     * 通过 DefaultAttributes 查询该实体类型注册时的默认血量，
     * 而非使用当前实体的血量（可能已被其他模组修改）。
     * 
     * @param entity 目标实体
     * @return 实体类型的默认最大生命值，如果查询失败返回当前血量
     */
    private double getDefaultHealth(LivingEntity entity) {
        try {
            @SuppressWarnings("unchecked")
            EntityType<? extends LivingEntity> entityType = (EntityType<? extends LivingEntity>) entity.getType();
            var supplier = DefaultAttributes.getSupplier(entityType);
            if (supplier != null) {
                return supplier.getBaseValue(Attributes.MAX_HEALTH);
            }
        } catch (Exception e) {
            // 查询失败时回退到当前血量
        }

        return entity.getMaxHealth();
    }

    @Override
    public String getBossType(LivingEntity entity) {
        if (isBoss(entity)) {
            return BOSS_TYPE_HIGH_HEALTH;
        }
        return null;
    }

    /**
     * 获取当前使用的血量阈值
     * 
     * @return 血量阈值
     */
    public double getHealthThreshold() {
        return healthThreshold;
    }
}