package com.adaptive_nemesis.adaptive_nemesismod.boss;

import net.minecraft.world.entity.EntityType;
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
        return matchesBossKeywords(EntityType.getKey(entity.getType()).toString(), bossKeywords);
    }

    @Override
    public String getBossType(LivingEntity entity) {
        String entityName = EntityType.getKey(entity.getType()).toString().toLowerCase();
        return inferBossType(entityName, bossKeywords);
    }

    /**
     * 判断实体注册名是否匹配给定的 Boss 关键词集合
     * 提取为静态方法以便单元测试，无需构造 Minecraft 实体
     *
     * @param entityKey 实体注册名，例如 "minecraft:zombie" 或 "cataclysm:ignis"
     * @param keywords  Boss 关键词集合
     * @return 是否匹配
     */
    public static boolean matchesBossKeywords(String entityKey, Set<String> keywords) {
        if (entityKey == null || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String lower = entityKey.toLowerCase();
        return keywords.stream().anyMatch(lower::contains);
    }

    /**
     * 根据实体注册名推断 Boss 类型
     *
     * @param entityKey 实体注册名（小写）
     * @param keywords  Boss 关键词集合
     * @return Boss 类型字符串，无法推断时返回 null
     */
    public static String inferBossType(String entityKey, Set<String> keywords) {
        if (entityKey == null) {
            return null;
        }
        String lower = entityKey.toLowerCase();

        if (lower.contains("dragon")) {
            return BOSS_TYPE_DRAGON;
        }
        if (lower.contains("wither")) {
            return BOSS_TYPE_WITHER;
        }
        if (lower.contains("warden")) {
            return BOSS_TYPE_WARDEN;
        }

        if (keywords != null) {
            // 按关键词长度升序匹配，优先返回更具体的名称（如 ignis）而非模组/通用名（如 cataclysm, boss）
            return keywords.stream()
                .sorted(java.util.Comparator.comparingInt(String::length))
                .filter(lower::contains)
                .findFirst()
                .orElse(null);
        }
        return null;
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