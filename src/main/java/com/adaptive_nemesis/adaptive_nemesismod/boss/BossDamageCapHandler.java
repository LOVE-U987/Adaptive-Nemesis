package com.adaptive_nemesis.adaptive_nemesismod.boss;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EnemyScalingHandler;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Boss伤害上限处理器
 * 
 * 防止玩家秒杀Boss的特殊机制：
 * - 伤害上限：玩家对Boss的单次伤害存在上限
 * - 属性增幅：Boss生命与伤害获得额外倍率加成
 * - 阶段进化：Boss根据战斗时长动态调整攻击模式
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class BossDamageCapHandler {
    
    /**
     * 单例实例
     */
    private static BossDamageCapHandler INSTANCE;
    
    /**
     * Boss战斗数据NBT标签键
     */
    public static final String BOSS_FIGHT_START_TAG = "adaptive_nemesis_boss_fight_start";

    /**
     * Boss已受伤害NBT标签键
     */
    public static final String BOSS_DAMAGE_TAKEN_TAG = "adaptive_nemesis_boss_damage_taken";

    /**
     * Boss Buff已应用标记 - 防止EntityJoinLevelEvent重复触发导致HP爆炸
     * Boss的EntityJoinLevelEvent可能在区块重新加载、玩家传送、维度切换时多次触发，
     * 该标记确保Boss Buff只应用一次
     */
    public static final String BOSS_BUFF_APPLIED_TAG = "adaptive_nemesis_boss_buff_applied";

    /**
     * Boss原始生命值NBT标签键 - 存储未经任何缩放修改的原始基础生命值
     * 用于与EnemyScalingHandler的缩放倍率叠加，确保计算始终基于同一原始值
     */
    public static final String BOSS_ORIGINAL_HEALTH_TAG = "adaptive_nemesis_boss_original_health";

    /**
     * Boss原始伤害NBT标签键 - 存储未经任何缩放修改的原始基础伤害
     */
    public static final String BOSS_ORIGINAL_DAMAGE_TAG = "adaptive_nemesis_boss_original_damage";

    /**
     * 缓存的排除实体ID集合
     */
    private Set<String> cachedExclusions = null;

    /**
     * 上次缓存更新的配置值
     */
    private String cachedExclusionConfigValue = "";
    
    /**
     * 私有构造函数 - 单例模式
     */
    private BossDamageCapHandler() {}
    
    /**
     * 获取单例实例
     * 
     * @return BossDamageCapHandler 实例
     */
    public static synchronized BossDamageCapHandler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BossDamageCapHandler();
        }
        return INSTANCE;
    }
    
    /**
     * 处理实体受到伤害事件
     * 
     * @param event 实体受到伤害事件
     */
    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        // 检查是否启用Boss伤害上限
        if (!Config.ENABLE_BOSS_DAMAGE_CAP.get()) {
            return;
        }
        
        LivingEntity target = event.getEntity();
        
        // 检查目标是否是Boss
        if (!isBoss(target)) {
            return;
        }

        // 检查该Boss是否在排除列表中（配置中指定的实体不受限伤影响）
        if (isExcludedFromCap(target)) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "Boss {} 在限伤排除列表中，跳过伤害限制",
                    target.getName().getString()
                );
            }
            return;
        }
        
        // 检查伤害来源是否是玩家
        if (event.getSource().getEntity() == null || 
            !(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        // 初始化Boss战斗数据
        initializeBossFightData(target);
        
        // 应用伤害上限
        float originalDamage = event.getAmount();
        float cappedDamage = applyDamageCap(target, originalDamage);
        
        if (cappedDamage < originalDamage) {
            event.setAmount(cappedDamage);
            
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "Boss {} 伤害被限制: 原始={}, 限制后={}, 上限={}",
                    target.getName().getString(),
                    String.format("%.2f", originalDamage),
                    String.format("%.2f", cappedDamage),
                    String.format("%.2f", Config.BOSS_DAMAGE_CAP.get())
                );
            }
        }
        
        // 记录已受伤害
        recordDamageTaken(target, cappedDamage);
    }
    
    /**
     * 检查实体是否是Boss
     * 
     * 委托给 BossIdentificationService 进行统一判断，
     * 采用策略模式组合多种识别方式（类型识别、名称识别、血量阈值识别）。
     * 
     * @param entity 目标实体
     * @return 如果是Boss返回true
     */
    public boolean isBoss(LivingEntity entity) {
        return BossIdentificationService.getInstance().isBoss(entity);
    }

    /**
     * 检查实体是否在限伤排除列表中
     *
     * @param entity 目标实体
     * @return 如果在排除列表中返回true，跳过限伤
     */
    public boolean isExcludedFromCap(LivingEntity entity) {
        String configValue = Config.BOSS_DAMAGE_CAP_EXCLUSIONS.get();
        if (configValue == null || configValue.trim().isEmpty()) {
            return false;
        }

        // 更新缓存
        if (cachedExclusions == null || !configValue.equals(cachedExclusionConfigValue)) {
            cachedExclusions = new HashSet<>();
            for (String id : configValue.split(",")) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) {
                    cachedExclusions.add(trimmed);
                }
            }
            cachedExclusionConfigValue = configValue;
        }

        // 获取实体的注册ID
        EntityType<?> entityType = entity.getType();
        ResourceLocation entityId = EntityType.getKey(entityType);
        String fullId = entityId.toString();

        if (cachedExclusions.contains(fullId)) {
            return true;
        }

        // 也检查短名称（不含命名空间）
        String shortId = entityId.getPath();
        return cachedExclusions.contains(shortId);
    }

    /**
     * 初始化Boss战斗数据
     * 
     * @param boss Boss实体
     */
    private void initializeBossFightData(LivingEntity boss) {
        var persistentData = boss.getPersistentData();
        
        if (!persistentData.contains(BOSS_FIGHT_START_TAG)) {
            persistentData.putLong(BOSS_FIGHT_START_TAG, System.currentTimeMillis());
            persistentData.putFloat(BOSS_DAMAGE_TAKEN_TAG, 0.0f);
            
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "Boss {} 战斗开始，已初始化战斗数据",
                    boss.getName().getString()
                );
            }
        }
    }
    
    /**
     * 应用伤害上限
     * 
     * @param boss Boss实体
     * @param originalDamage 原始伤害值
     * @return 限制后的伤害值
     */
    private float applyDamageCap(LivingEntity boss, float originalDamage) {
        double damageCap = Config.BOSS_DAMAGE_CAP.get();
        
        // 根据Boss已受伤害动态调整上限（受伤越多，上限越高，允许玩家加速击杀）
        float damageTaken = boss.getPersistentData().getFloat(BOSS_DAMAGE_TAKEN_TAG);
        double healthPercent = boss.getHealth() / boss.getMaxHealth();
        
        // Boss血量越低，伤害上限越高（最多提高到2倍）
        double dynamicCap = damageCap * (1.0 + (1.0 - healthPercent));
        
        return Math.min(originalDamage, (float) dynamicCap);
    }
    
    /**
     * 记录Boss已受伤害
     * 
     * @param boss Boss实体
     * @param damage 本次伤害值
     */
    private void recordDamageTaken(LivingEntity boss, float damage) {
        var persistentData = boss.getPersistentData();
        float currentDamage = persistentData.getFloat(BOSS_DAMAGE_TAKEN_TAG);
        persistentData.putFloat(BOSS_DAMAGE_TAKEN_TAG, currentDamage + damage);
    }
    
    /**
     * 获取Boss战斗持续时间（毫秒）
     * 
     * @param boss Boss实体
     * @return 战斗持续时间，如果未开始返回0
     */
    public long getFightDuration(LivingEntity boss) {
        var persistentData = boss.getPersistentData();
        if (!persistentData.contains(BOSS_FIGHT_START_TAG)) {
            return 0;
        }
        
        long startTime = persistentData.getLong(BOSS_FIGHT_START_TAG);
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * 获取Boss已受伤害总量
     * 
     * @param boss Boss实体
     * @return 已受伤害总量
     */
    public float getTotalDamageTaken(LivingEntity boss) {
        return boss.getPersistentData().getFloat(BOSS_DAMAGE_TAKEN_TAG);
    }
    
    /**
     * 应用Boss属性增幅
     * 在Boss生成时由 ModEventHandler.onEntityJoinLevel() 调用
     * 
     * 🛡️ 防HP爆炸机制：
     * 1. BOSS_BUFF_APPLIED_TAG 标记确保Buff只应用一次
     *    防止 EntityJoinLevelEvent 在区块重新加载/玩家传送/维度切换时重复触发
     * 2. 存储原始血量/伤害值在 NBT 中，始终从原始值计算
     *    防止与 EnemyScalingHandler 的缩放倍率产生乘法叠加
     * 
     * @param boss Boss实体
     */
    public void applyBossBuffs(LivingEntity boss) {
        if (!isBoss(boss)) {
            return;
        }

        var data = boss.getPersistentData();

        // 🛡️ 防HP爆炸：Boss Buff 已应用过则跳过
        // EntityJoinLevelEvent 可能在以下场景多次触发同一实体：
        //   - 玩家传送离开后区块卸载，返回后区块重新加载
        //   - 玩家切换维度后实体重新加载
        //   - 服务器重启后实体从磁盘加载
        // 没有此检查，血量会在每次重新加载时乘以 BOSS_HEALTH_MULTIPLIER，指数级爆炸
        if (data.getBoolean(BOSS_BUFF_APPLIED_TAG)) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "Boss {} Buff已应用，跳过重复增幅（防HP爆炸）",
                    boss.getName().getString()
                );
            }
            return;
        }

        // 获取或存储原始基础生命值
        // 优先从 EnemyScalingHandler 的存储值读取（如果它已先执行）
        // 这样无论两个Handler的执行顺序如何，都能基于同一原始值计算
        double originalHealth;
        if (data.contains(BOSS_ORIGINAL_HEALTH_TAG)) {
            originalHealth = data.getDouble(BOSS_ORIGINAL_HEALTH_TAG);
        } else {
            var healthAttr = boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            originalHealth = (healthAttr != null) ? healthAttr.getBaseValue() : 20.0;
            data.putDouble(BOSS_ORIGINAL_HEALTH_TAG, originalHealth);
        }

        // 获取或存储原始基础伤害值
        double originalDamage;
        if (data.contains(BOSS_ORIGINAL_DAMAGE_TAG)) {
            originalDamage = data.getDouble(BOSS_ORIGINAL_DAMAGE_TAG);
        } else {
            var damageAttr = boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            originalDamage = (damageAttr != null) ? damageAttr.getBaseValue() : 1.0;
            data.putDouble(BOSS_ORIGINAL_DAMAGE_TAG, originalDamage);
        }

        // 读取 EnemyScalingHandler 已应用的缩放倍率（如果存在）
        // 确保 Boss 属性增幅与自适应缩放正确叠加
        double existingScaleMultiplier = Math.max(1.0, data.getDouble(EnemyScalingHandler.SCALE_MULTIPLIER_TAG));

        // 应用生命值倍率：原始值 * 已存在的缩放倍率 * Boss倍率
        double healthMultiplier = Config.BOSS_HEALTH_MULTIPLIER.get();
        double newMaxHealth = originalHealth * existingScaleMultiplier * healthMultiplier;
        var healthAttr = boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(newMaxHealth);
            boss.setHealth((float) newMaxHealth);
        }

        // 应用伤害倍率：原始值 * 已存在的缩放倍率 * Boss倍率
        double damageMultiplier = Config.BOSS_DAMAGE_MULTIPLIER.get();
        var damageAttr = boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            double newDamage = originalDamage * existingScaleMultiplier * damageMultiplier;
            damageAttr.setBaseValue(newDamage);
        }

        // 标记为已应用，防止后续 EntityJoinLevelEvent 重复触发
        data.putBoolean(BOSS_BUFF_APPLIED_TAG, true);

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "Boss {} 已获得属性增幅: 原始血量={}, 原始伤害={}, " +
                "自适应倍率={}, Boss倍率(血量={}, 伤害={}), 最终血量={}",
                boss.getName().getString(),
                String.format("%.2f", originalHealth),
                String.format("%.2f", originalDamage),
                String.format("%.2f", existingScaleMultiplier),
                String.format("%.2f", healthMultiplier),
                String.format("%.2f", damageMultiplier),
                String.format("%.2f", newMaxHealth)
            );
        }
    }
}
