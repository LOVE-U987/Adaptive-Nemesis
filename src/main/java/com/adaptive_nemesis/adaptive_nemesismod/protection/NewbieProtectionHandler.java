package com.adaptive_nemesis.adaptive_nemesismod.protection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 新手保护处理器
 * 
 * 为低强度玩家提供保护机制：
 * - 玩家综合强度 < 阈值 → 怪物属性 -30%
 * - 首次死亡 → 保护时间 +10分钟
 * - 连续死亡 3 次 → 强制开启保护直至击杀任一敌人
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class NewbieProtectionHandler {
    
    /**
     * 单例实例
     */
    private static NewbieProtectionHandler INSTANCE;
    
    /**
     * 玩家保护数据映射
     * Key: 玩家UUID, Value: 保护数据
     */
    private final Map<UUID, ProtectionData> playerProtectionData = new HashMap<>();
    
    /**
     * 私有构造函数 - 单例模式
     */
    private NewbieProtectionHandler() {}
    
    /**
     * 获取单例实例
     * 
     * @return NewbieProtectionHandler 实例
     */
    public static synchronized NewbieProtectionHandler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new NewbieProtectionHandler();
        }
        return INSTANCE;
    }
    
    /**
     * 玩家Tick事件 - 检查并更新保护状态
     * 
     * @param event 玩家Tick事件
     */
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        // 每20tick（1秒）检查一次
        if (player.tickCount % 20 != 0) {
            return;
        }
        
        UUID playerId = player.getUUID();
        ProtectionData data = getOrCreateProtectionData(playerId);
        
        // 检查保护是否过期
        if (data.isProtected() && data.isExpired()) {
            data.setProtected(false);
            
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "玩家 {} 的新手保护已过期",
                    player.getName().getString()
                );
            }
        }
        
        // 检查是否需要自动开启保护（低强度玩家）
        if (!data.isProtected() && Config.ENABLE_NEWBIE_PROTECTION.get()) {
            checkAndEnableAutoProtection(player, data);
        }
    }
    
    /**
     * 处理玩家死亡事件
     * 
     * @param event 玩家死亡事件（通过LivingDeathEvent检测）
     */
    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        UUID playerId = player.getUUID();
        ProtectionData data = getOrCreateProtectionData(playerId);
        
        // 增加死亡计数
        data.addDeath();
        
        // 首次死亡增加保护时间
        if (data.getDeathCount() == 1) {
            long bonusTime = Config.DEATH_PROTECTION_BONUS.get() * 60 * 1000; // 转毫秒
            data.extendProtection(bonusTime);
            
            AdaptiveNemesisMod.LOGGER.info(
                "玩家 {} 首次死亡，新手保护时间增加 {} 分钟",
                player.getName().getString(),
                Config.DEATH_PROTECTION_BONUS.get()
            );
        }
        
        // 检查是否达到连续死亡阈值
        if (data.getDeathCount() >= Config.DEATH_STREAK_THRESHOLD.get()) {
            data.forceEnableProtection();
            
            AdaptiveNemesisMod.LOGGER.info(
                "玩家 {} 连续死亡 {} 次，强制开启新手保护",
                player.getName().getString(),
                data.getDeathCount()
            );
        }
    }
    
    /**
     * 处理玩家击杀事件 - 重置死亡计数
     * 
     * @param event 生物死亡事件
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource() == null || event.getSource().getEntity() == null) {
            return;
        }
        
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        UUID playerId = player.getUUID();
        ProtectionData data = playerProtectionData.get(playerId);
        
        if (data != null) {
            // 重置死亡计数
            data.resetDeathCount();
            
            // 如果是强制保护状态，击杀后解除
            if (data.isForceProtected()) {
                data.setForceProtected(false);
                data.setProtected(false);
                
                AdaptiveNemesisMod.LOGGER.info(
                    "玩家 {} 击杀了敌人，强制保护已解除",
                    player.getName().getString()
                );
            }
        }
    }
    
    /**
     * 处理玩家登录事件
     * 
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        UUID playerId = player.getUUID();
        ProtectionData data = getOrCreateProtectionData(playerId);
        
        // 检查并自动启用保护
        if (Config.ENABLE_NEWBIE_PROTECTION.get()) {
            checkAndEnableAutoProtection(player, data);
        }
    }
    
    /**
     * 检查并自动启用保护（基于玩家强度）
     * 
     * @param player 目标玩家
     * @param data 保护数据
     */
    private void checkAndEnableAutoProtection(ServerPlayer player, ProtectionData data) {
        var strengthData = PlayerStrengthEvaluator.getInstance().getPlayerStrength(player);
        
        if (strengthData == null) {
            return;
        }
        
        double playerStrength = strengthData.getTotalStrength();
        double threshold = Config.NEWBIE_STRENGTH_THRESHOLD.get();
        
        // 如果玩家强度低于阈值，启用保护
        if (playerStrength < threshold && !data.isProtected()) {
            long duration = Config.NEWBIE_PROTECTION_DURATION.get() * 60 * 1000; // 转毫秒
            data.enableProtection(duration);
            
            AdaptiveNemesisMod.LOGGER.info(
                "玩家 {} 强度({})低于阈值({})，已启用新手保护（{}分钟）",
                player.getName().getString(),
                String.format("%.2f", playerStrength),
                String.format("%.2f", threshold),
                Config.NEWBIE_PROTECTION_DURATION.get()
            );
        }
    }
    
    /**
     * 获取或创建玩家保护数据
     * 
     * @param playerId 玩家UUID
     * @return 保护数据
     */
    private ProtectionData getOrCreateProtectionData(UUID playerId) {
        return playerProtectionData.computeIfAbsent(playerId, k -> new ProtectionData());
    }
    
    /**
     * 检查玩家是否处于保护状态
     * 
     * @param playerId 玩家UUID
     * @return 如果受保护返回true
     */
    public boolean isProtected(UUID playerId) {
        ProtectionData data = playerProtectionData.get(playerId);
        return data != null && data.isProtected();
    }
    
    /**
     * 获取玩家的保护减免比例
     * 
     * @param playerId 玩家UUID
     * @return 减免比例（0.0 - 1.0）
     */
    public double getProtectionReduction(UUID playerId) {
        if (!isProtected(playerId)) {
            return 0.0;
        }
        return Config.NEWBIE_PROTECTION_REDUCTION.get();
    }
    
    /**
     * 清除指定玩家的保护数据
     * 
     * @param playerId 玩家UUID
     */
    public void clearProtectionData(UUID playerId) {
        playerProtectionData.remove(playerId);
    }
    
    /**
     * 保护数据内部类
     */
    private static class ProtectionData {
        
        /**
         * 是否处于保护状态
         */
        private boolean isProtected = false;
        
        /**
         * 是否是强制保护（连续死亡触发）
         */
        private boolean isForceProtected = false;
        
        /**
         * 保护过期时间
         */
        private long protectionExpiryTime = 0;
        
        /**
         * 连续死亡计数
         */
        private int deathCount = 0;
        
        /**
         * 检查是否处于保护状态
         * 
         * @return 如果受保护返回true
         */
        public boolean isProtected() {
            return isProtected;
        }
        
        /**
         * 设置保护状态
         * 
         * @param protected 保护状态
         */
        public void setProtected(boolean isProtected) {
            this.isProtected = isProtected;
            if (!isProtected) {
                this.isForceProtected = false;
            }
        }
        
        /**
         * 检查是否是强制保护
         * 
         * @return 如果是强制保护返回true
         */
        public boolean isForceProtected() {
            return isForceProtected;
        }
        
        /**
         * 设置强制保护状态
         * 
         * @param forceProtected 强制保护状态
         */
        public void setForceProtected(boolean forceProtected) {
            this.isForceProtected = forceProtected;
        }
        
        /**
         * 启用保护
         * 
         * @param durationMs 保护持续时间（毫秒）
         */
        public void enableProtection(long durationMs) {
            this.isProtected = true;
            this.protectionExpiryTime = System.currentTimeMillis() + durationMs;
        }
        
        /**
         * 延长保护时间
         * 
         * @param additionalTimeMs 额外时间（毫秒）
         */
        public void extendProtection(long additionalTimeMs) {
            if (!isProtected) {
                enableProtection(additionalTimeMs);
            } else {
                this.protectionExpiryTime += additionalTimeMs;
            }
        }
        
        /**
         * 强制开启保护（无过期时间）
         */
        public void forceEnableProtection() {
            this.isProtected = true;
            this.isForceProtected = true;
            this.protectionExpiryTime = Long.MAX_VALUE;
        }
        
        /**
         * 检查保护是否过期
         * 
         * @return 如果已过期返回true
         */
        public boolean isExpired() {
            if (isForceProtected) {
                return false; // 强制保护不会过期
            }
            return System.currentTimeMillis() > protectionExpiryTime;
        }
        
        /**
         * 增加死亡计数
         */
        public void addDeath() {
            this.deathCount++;
        }
        
        /**
         * 重置死亡计数
         */
        public void resetDeathCount() {
            this.deathCount = 0;
        }
        
        /**
         * 获取死亡计数
         * 
         * @return 死亡计数
         */
        public int getDeathCount() {
            return deathCount;
        }
        
        /**
         * 获取剩余保护时间（毫秒）
         * 
         * @return 剩余时间
         */
        public long getRemainingTime() {
            if (!isProtected || isForceProtected) {
                return isForceProtected ? -1 : 0;
            }
            return Math.max(0, protectionExpiryTime - System.currentTimeMillis());
        }
    }
}
