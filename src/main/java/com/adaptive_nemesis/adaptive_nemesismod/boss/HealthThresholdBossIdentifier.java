package com.adaptive_nemesis.adaptive_nemesismod.boss;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;

/**
 * 基于血量阈值的Boss识别策略
 * 
 * 通过实体最大生命值判断是否为Boss。
 * 血量阈值可通过配置文件动态调整，适用于识别高血量模组Boss。
 * 
 * 注意：动物/傀儡类（Animal 子类）不受此策略影响，
 * 防止高血量铁傀儡等被误识别为Boss吃到敌对加成。
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class HealthThresholdBossIdentifier implements BossIdentifier {

    private final double healthThreshold;

    /**
     * 创建基于血量阈值的Boss识别策略
     * 
     * @param healthThreshold 血量阈值，实体最大生命值超过此值即视为Boss
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
        return entity.getMaxHealth() >= healthThreshold;
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