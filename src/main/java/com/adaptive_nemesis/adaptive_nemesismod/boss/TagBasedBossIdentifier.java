package com.adaptive_nemesis.adaptive_nemesismod.boss;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.Registries;

/**
 * 基于实体类型标签的Boss识别策略
 * 
 * 通过检查实体类型是否在BOSS标签中来判断是否为Boss。
 * 这是最可靠的识别方式，因为标签由模组开发者明确定义，
 * 不会像血量或名称那样容易产生误判。
 * 
 * 🎯 核心优势：
 * - 精准：由模组开发者显式标记Boss，避免误判
 * - 兼容：支持所有模组的Boss实体（只要它们正确注册了标签）
 * - 灵活：支持多个标签来源（minecraft:bosses, c:bosses等）
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class TagBasedBossIdentifier implements BossIdentifier {

    /** 原版BOSS标签 */
    private static final TagKey<EntityType<?>> MINECRAFT_BOSSES_TAG = 
        TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("minecraft", "bosses"));
    
    /** 通用BOSS标签（由众多模组使用） */
    private static final TagKey<EntityType<?>> COMMON_BOSSES_TAG = 
        TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("c", "bosses"));

    @Override
    public boolean isBoss(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        
        EntityType<?> entityType = entity.getType();
        
        return entityType.is(MINECRAFT_BOSSES_TAG) 
            || entityType.is(COMMON_BOSSES_TAG);
    }

    @Override
    public String getBossType(LivingEntity entity) {
        if (!isBoss(entity)) {
            return null;
        }
        
        String entityName = EntityType.getKey(entity.getType()).toString().toLowerCase();
        
        if (entityName.contains("dragon")) {
            return BOSS_TYPE_DRAGON;
        }
        if (entityName.contains("wither")) {
            return BOSS_TYPE_WITHER;
        }
        if (entityName.contains("warden")) {
            return BOSS_TYPE_WARDEN;
        }
        
        return BOSS_TYPE_HIGH_HEALTH;
    }
}