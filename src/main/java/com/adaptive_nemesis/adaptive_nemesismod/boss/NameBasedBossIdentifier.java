package com.adaptive_nemesis.adaptive_nemesismod.boss;

import net.minecraft.resources.ResourceLocation;
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
 * 🛡️ 防误判机制：
 * 部分普通怪物（如凋零骷髅 wither_skeleton）的实体注册名中可能包含 Boss 关键词（如"wither"），
 * 通过排除实体列表过滤这些假阳性，防止它们获得Boss额外加成。
 * 
 * 例如：minecraft:wither_skeleton 包含 "wither" 关键词，匹配成功，
 * 但它只是普通怪物，不应获得Boss的5倍血量+3倍伤害加成。
 * 
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class NameBasedBossIdentifier implements BossIdentifier {

    private final Set<String> bossKeywords;

    /**
     * 名称匹配Boss识别排除列表
     * 
     * 这些实体的注册名中包含Boss关键词，但它们并非真正的Boss，
     * 不应通过名称匹配策略被识别为Boss。
     * 例如：
     * - minecraft:wither_skeleton → 包含 "wither"，但只是普通怪物
     */
    private static final Set<String> EXCLUDED_ENTITY_IDS = Collections.unmodifiableSet(new HashSet<>(Set.of(
        "minecraft:wither_skeleton"
    )));

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
        // 🚫 检查是否在排除列表中
        // 防止实体名称中包含 Boss 关键词但不是真正 Boss 的普通怪物被误判
        if (isExcluded(entity)) {
            return false;
        }
        return matchesBossKeywords(EntityType.getKey(entity.getType()).toString(), bossKeywords);
    }

    @Override
    public String getBossType(LivingEntity entity) {
        // 🚫 排除列表中的实体不进行 Boss 类型推断
        if (isExcluded(entity)) {
            return null;
        }
        String entityName = EntityType.getKey(entity.getType()).toString().toLowerCase();
        return inferBossType(entityName, bossKeywords);
    }

    /**
     * 检查实体是否在名称匹配的排除列表中
     * 
     * @param entity 目标实体
     * @return 如果在排除列表中返回true
     */
    private boolean isExcluded(LivingEntity entity) {
        EntityType<?> entityType = entity.getType();
        ResourceLocation key = EntityType.getKey(entityType);
        if (key == null) {
            return false;
        }
        return EXCLUDED_ENTITY_IDS.contains(key.toString());
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