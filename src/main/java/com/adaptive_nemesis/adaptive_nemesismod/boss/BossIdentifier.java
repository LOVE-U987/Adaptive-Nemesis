package com.adaptive_nemesis.adaptive_nemesismod.boss;

import net.minecraft.world.entity.LivingEntity;

/**
 * Boss识别策略接口
 * 
 * 定义统一的Boss识别标准，支持多种识别策略。
 * 所有Boss识别相关的模块都应通过此接口进行判断，
 * 避免在各模块中重复实现相同的识别逻辑。
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public interface BossIdentifier {

    /** 末影龙Boss类型标识 */
    String BOSS_TYPE_DRAGON = "ender_dragon";
    /** 凋灵Boss类型标识 */
    String BOSS_TYPE_WITHER = "wither";
    /** 监守者Boss类型标识 */
    String BOSS_TYPE_WARDEN = "warden";
    /** 高血量Boss类型标识 */
    String BOSS_TYPE_HIGH_HEALTH = "high_health_boss";

    /**
     * 判断实体是否为Boss
     * 
     * @param entity 目标实体
     * @return 如果是Boss返回true，否则返回false
     */
    boolean isBoss(LivingEntity entity);

    /**
     * 获取Boss类型标识
     * 
     * @param entity Boss实体
     * @return Boss类型标识字符串，如果无法识别则返回null
     */
    default String getBossType(LivingEntity entity) {
        return null;
    }
}