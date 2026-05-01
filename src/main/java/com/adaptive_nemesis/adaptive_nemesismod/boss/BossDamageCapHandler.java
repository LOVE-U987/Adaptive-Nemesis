package com.adaptive_nemesis.adaptive_nemesismod.boss;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

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
     * @param entity 目标实体
     * @return 如果是Boss返回true
     */
    public boolean isBoss(LivingEntity entity) {
        // 原版Boss
        if (entity instanceof EnderDragon || 
            entity instanceof WitherBoss || 
            entity instanceof Warden) {
            return true;
        }
        
        // 通过名称检测其他Boss（如模组添加的Boss）
        String entityName = entity.getType().toString().toLowerCase();
        return entityName.contains("boss") || 
               entityName.contains("dragon") ||
               entityName.contains("wither") ||
               entityName.contains("warden") ||
               entity.getMaxHealth() >= 200; // 血量超过200的也视为Boss
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
     * 在Boss生成时调用
     * 
     * @param boss Boss实体
     */
    public void applyBossBuffs(LivingEntity boss) {
        if (!isBoss(boss)) {
            return;
        }
        
        // 应用生命值倍率
        double healthMultiplier = Config.BOSS_HEALTH_MULTIPLIER.get();
        double newMaxHealth = boss.getMaxHealth() * healthMultiplier;
        boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
            .setBaseValue(newMaxHealth);
        boss.setHealth((float) newMaxHealth);
        
        // 应用伤害倍率
        double damageMultiplier = Config.BOSS_DAMAGE_MULTIPLIER.get();
        var damageAttr = boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() * damageMultiplier);
        }
        
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "Boss {} 已获得属性增幅: 血量倍率={}, 伤害倍率={}",
                boss.getName().getString(),
                String.format("%.2f", healthMultiplier),
                String.format("%.2f", damageMultiplier)
            );
        }
    }
}
