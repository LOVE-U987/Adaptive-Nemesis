package com.adaptive_nemesis.adaptive_nemesismod.boss;

import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 基于实体名称的Boss识别策略
 * 
 * 通过实体注册ID或名称中的关键词识别模组添加的Boss。
 * 关键词列表可通过配置文件动态调整，支持模组兼容性扩展。
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class NameBasedBossIdentifier implements BossIdentifier {

    private final Set<String> bossKeywords;

    /**
     * 创建基于名称的Boss识别策略
     * 
     * @param bossKeywords Boss识别关键词集合，用于匹配实体名称
     */
    public NameBasedBossIdentifier(Set<String> bossKeywords) {
        this.bossKeywords = new HashSet<>(bossKeywords);
    }

    @Override
    public boolean isBoss(LivingEntity entity) {
        String entityName = entity.getType().toString().toLowerCase();
        return bossKeywords.stream().anyMatch(entityName::contains);
    }

    @Override
    public String getBossType(LivingEntity entity) {
        String entityName = entity.getType().toString().toLowerCase();

        if (entityName.contains("dragon")) {
            return BOSS_TYPE_DRAGON;
        }
        if (entityName.contains("wither")) {
            return BOSS_TYPE_WITHER;
        }
        if (entityName.contains("warden")) {
            return BOSS_TYPE_WARDEN;
        }

        return bossKeywords.stream()
            .filter(entityName::contains)
            .findFirst()
            .orElse(null);
    }

    /**
     * 获取当前使用的关键词集合（只读视图）
     * 
     * @return 不可修改的关键词集合
     */
    public Set<String> getKeywords() {
        return Collections.unmodifiableSet(bossKeywords);
    }
}