package com.adaptive_nemesis.adaptive_nemesismod.boss;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;

/**
 * 基于实体类型的Boss识别策略
 * 
 * 通过 instanceof 检查直接识别原版Boss实体类型。
 * 优先级最高，适用于识别末影龙、凋灵、监守者等原版Boss。
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class TypeBasedBossIdentifier implements BossIdentifier {

    @Override
    public boolean isBoss(LivingEntity entity) {
        return entity instanceof EnderDragon
            || entity instanceof WitherBoss
            || entity instanceof Warden;
    }

    @Override
    public String getBossType(LivingEntity entity) {
        if (entity instanceof EnderDragon) {
            return BOSS_TYPE_DRAGON;
        }
        if (entity instanceof WitherBoss) {
            return BOSS_TYPE_WITHER;
        }
        if (entity instanceof Warden) {
            return BOSS_TYPE_WARDEN;
        }
        return null;
    }
}