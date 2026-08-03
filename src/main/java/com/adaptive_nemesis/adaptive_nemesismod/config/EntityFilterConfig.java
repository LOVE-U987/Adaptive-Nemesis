package com.adaptive_nemesis.adaptive_nemesismod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 实体过滤配置
 */
public class EntityFilterConfig {

    /**
     * 是否启用实体过滤功能 - 关闭后黑白名单不生效，所有实体均受自适应缩放影响
     */
    public final ModConfigSpec.BooleanValue ENABLE_ENTITY_FILTER;

    /**
     * 实体黑名单 - 在黑名单中的实体不会被自适应系统缩放
     * 支持 * 通配符，例如：minecraft:zombie,minecraft:iron_golem,alexsmobs:*
     */
    public final ModConfigSpec.ConfigValue<String> ENTITY_BLACKLIST;

    public EntityFilterConfig(ModConfigSpec.Builder builder) {
        builder.push("entityFilter");
        ENABLE_ENTITY_FILTER = builder
            .comment("是否启用实体过滤功能 - 关闭后黑白名单不生效，所有实体均受自适应缩放影响")
            .comment("Enable entity filter - when disabled, all entities are subject to adaptive scaling")
            .define("enableEntityFilter", true);
        ENTITY_BLACKLIST = builder
            .comment("实体黑名单（逗号分隔），在黑名单中的生物不会被自适应系统缩放加成")
            .comment("支持 * 通配符匹配，例如：minecraft:zombie,minecraft:iron_golem,alexsmobs:*")
            .comment("Entity blacklist (comma-separated). Entities in this list are excluded from all adaptive scaling.")
            .comment("Supports * wildcard, e.g.: minecraft:zombie,minecraft:iron_golem,alexsmobs:*")
            .define("entityBlacklist", "");
        builder.pop();
    }
}
