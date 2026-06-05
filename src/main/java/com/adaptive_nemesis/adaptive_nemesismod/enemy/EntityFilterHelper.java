package com.adaptive_nemesis.adaptive_nemesismod.enemy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * 实体黑白名单过滤工具类
 *
 * 用于检测指定实体是否在配置的黑/白名单中，
 * 支持 * 通配符匹配（如 minecraft:zombie, alexsmobs:*）
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class EntityFilterHelper {

    private static EntityFilterHelper INSTANCE;

    /**
     * 上次解析时配置的内容哈希，用于缓存失效判断
     */
    private String lastConfigHash = "";

    /**
     * 编译好的黑名单正则列表
     */
    private List<Pattern> blacklistPatterns = new ArrayList<>();

    private EntityFilterHelper() {}

    /**
     * 获取单例实例
     *
     * @return EntityFilterHelper 实例
     */
    public static synchronized EntityFilterHelper getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EntityFilterHelper();
        }
        return INSTANCE;
    }

    /**
     * 检查指定实体是否在黑名单中
     *
     * @param entity 待检查的实体
     * @return 如果实体在黑名单中返回 true
     */
    public boolean isBlocked(Entity entity) {
        if (entity == null) {
            return false;
        }

        EntityType<?> entityType = entity.getType();
        ResourceLocation key = EntityType.getKey(entityType);
        if (key == null) {
            return false;
        }

        String entityId = key.toString();
        refreshPatterns();

        for (Pattern pattern : blacklistPatterns) {
            if (pattern.matcher(entityId).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取实体的完整注册 ID
     *
     * @param entity 实体
     * @return 实体的注册 ID（如 minecraft:zombie）
     */
    public static String getEntityId(Entity entity) {
        if (entity == null) {
            return "";
        }
        ResourceLocation key = EntityType.getKey(entity.getType());
        return key != null ? key.toString() : "";
    }

    /**
     * 刷新黑名单模式列表
     * 当配置更改时重新解析和编译正则模式
     */
    private void refreshPatterns() {
        String raw = Config.ENTITY_BLACKLIST.get();
        if (raw == null) {
            raw = "";
        }
        raw = raw.trim();

        // 如果配置没有变化，跳过重新解析
        if (lastConfigHash.equals(raw)) {
            return;
        }

        lastConfigHash = raw;
        blacklistPatterns.clear();

        if (raw.isEmpty()) {
            return;
        }

        String[] parts = raw.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            try {
                // 将通配符 * 转换为正则 .*
                String regex = "\\Q" + trimmed.replace("*", "\\E.*\\Q") + "\\E";
                blacklistPatterns.add(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                AdaptiveNemesisMod.LOGGER.warn(
                    "实体黑名单条目解析失败: '{}', 原因: {}",
                    trimmed, e.getMessage()
                );
            }
        }

        if (Config.ENABLE_DEBUG_LOG.get() && !blacklistPatterns.isEmpty()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "实体黑名单已加载: {} 条规则, 原始配置='{}'",
                blacklistPatterns.size(), raw
            );
        }
    }
}