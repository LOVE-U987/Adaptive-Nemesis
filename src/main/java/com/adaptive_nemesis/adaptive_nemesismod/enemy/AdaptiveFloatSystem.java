package com.adaptive_nemesis.adaptive_nemesismod.enemy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 智能浮动系统
 * 
 * 根据玩家表现动态调整难度浮动倍数：
 * - 连续击杀 → 浮动倍数 +10%
 * - 频繁死亡 → 浮动倍数 -15%
 * - 长时间未战斗 → 浮动倍数重置基准
 * 
 * 目标：让玩家始终感到"有点难，但不多"
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class AdaptiveFloatSystem {
    
    /**
     * 单例实例
     */
    private static AdaptiveFloatSystem INSTANCE;
    
    /**
     * 玩家浮动数据映射
     * Key: 玩家UUID, Value: 浮动数据
     */
    private final Map<UUID, PlayerFloatData> playerFloatData = new HashMap<>();
    
    /**
     * 私有构造函数 - 单例模式
     */
    private AdaptiveFloatSystem() {}
    
    /**
     * 获取单例实例
     * 
     * @return AdaptiveFloatSystem 实例
     */
    public static synchronized AdaptiveFloatSystem getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AdaptiveFloatSystem();
        }
        return INSTANCE;
    }
    
    /**
     * 获取指定玩家的当前浮动倍率
     * 
     * @param playerId 玩家UUID
     * @return 浮动倍率
     */
    public double getFloatMultiplier(UUID playerId) {
        PlayerFloatData data = playerFloatData.get(playerId);
        if (data == null) {
            return 1.0;
        }
        return data.getCurrentMultiplier();
    }
    
    /**
     * 获取默认浮动倍率（用于没有特定玩家数据的情况）
     * 
     * @return 默认浮动倍率
     */
    public double getFloatMultiplier() {
        // 返回所有在线玩家的平均浮动倍率
        if (playerFloatData.isEmpty()) {
            return 1.0;
        }
        
        double total = 0.0;
        for (PlayerFloatData data : playerFloatData.values()) {
            total += data.getCurrentMultiplier();
        }
        
        return total / playerFloatData.size();
    }
    
    /**
     * 处理玩家击杀事件
     * 
     * @param event 生物死亡事件
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        // 检查是否是玩家击杀的敌对生物
        if (event.getSource() == null || event.getSource().getEntity() == null) {
            return;
        }
        
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        UUID playerId = player.getUUID();
        PlayerFloatData data = getOrCreateFloatData(playerId);
        
        // 增加连续击杀计数
        data.addKill();
        
        // 每次击杀都增加浮动倍率（不再只连续5次才加）
        double newMultiplier = data.getCurrentMultiplier() + Config.KILL_STREAK_MULTIPLIER_INCREASE.get();
        data.setMultiplier(Math.min(newMultiplier, Config.FLOAT_MAX.get()));
        
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "玩家 {} 击杀敌人，浮动倍率提升至 {} (连杀: {})",
                player.getName().getString(),
                String.format("%.2f", data.getCurrentMultiplier()),
                data.getKillStreak()
            );
        }
        
        // 更新最后战斗时间
        data.updateLastCombatTime();
    }
    
    /**
     * 处理玩家死亡事件
     * 
     * @param event 玩家死亡事件
     */
    @SubscribeEvent
    public void onPlayerDeath(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        UUID playerId = player.getUUID();
        PlayerFloatData data = getOrCreateFloatData(playerId);
        
        // 增加死亡计数
        data.addDeath();
        
        // 减少浮动倍率
        double newMultiplier = data.getCurrentMultiplier() - Config.DEATH_STREAK_MULTIPLIER_DECREASE.get();
        data.setMultiplier(Math.max(newMultiplier, Config.FLOAT_MIN.get()));
        
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "玩家 {} 死亡，浮动倍率降低至 {}",
                player.getName().getString(),
                String.format("%.2f", data.getCurrentMultiplier())
            );
        }
        
        // 更新最后战斗时间
        data.updateLastCombatTime();
    }
    
    /**
     * 获取或创建玩家的浮动数据
     * 
     * @param playerId 玩家UUID
     * @return 玩家浮动数据
     */
    private PlayerFloatData getOrCreateFloatData(UUID playerId) {
        return playerFloatData.computeIfAbsent(playerId, k -> new PlayerFloatData());
    }
    
    /**
     * 检查并重置长时间未战斗玩家的浮动倍率
     * 
     * @param playerId 玩家UUID
     */
    public void checkAndResetIfInactive(UUID playerId) {
        PlayerFloatData data = playerFloatData.get(playerId);
        if (data == null) {
            return;
        }
        
        long inactiveTime = System.currentTimeMillis() - data.getLastCombatTime();
        long resetTimeMs = Config.FLOAT_RESET_TIME_MINUTES.get() * 60 * 1000;
        
        if (inactiveTime > resetTimeMs) {
            data.resetMultiplier();
            
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "玩家 {} 长时间未战斗，浮动倍率已重置为 {}",
                    playerId,
                    String.format("%.2f", data.getCurrentMultiplier())
                );
            }
        }
    }
    
    /**
     * 清除指定玩家的浮动数据
     * 
     * @param playerId 玩家UUID
     */
    public void clearPlayerData(UUID playerId) {
        playerFloatData.remove(playerId);
    }
    
    /**
     * 玩家浮动数据内部类
     */
    private static class PlayerFloatData {
        
        /**
         * 当前浮动倍率
         */
        private double currentMultiplier = 1.0;
        
        /**
         * 连续击杀计数
         */
        private int killStreak = 0;
        
        /**
         * 连续死亡计数
         */
        private int deathStreak = 0;
        
        /**
         * 最后战斗时间戳
         */
        private long lastCombatTime = System.currentTimeMillis();
        
        /**
         * 获取当前浮动倍率
         * 
         * @return 当前倍率
         */
        public double getCurrentMultiplier() {
            return currentMultiplier;
        }
        
        /**
         * 设置浮动倍率
         * 
         * @param multiplier 新倍率
         */
        public void setMultiplier(double multiplier) {
            this.currentMultiplier = Math.max(
                Config.FLOAT_MIN.get(),
                Math.min(multiplier, Config.FLOAT_MAX.get())
            );
        }
        
        /**
         * 重置浮动倍率为基准值
         */
        public void resetMultiplier() {
            this.currentMultiplier = 1.0;
            this.killStreak = 0;
            this.deathStreak = 0;
        }
        
        /**
         * 增加击杀计数
         */
        public void addKill() {
            this.killStreak++;
            this.deathStreak = 0;
        }
        
        /**
         * 增加死亡计数
         */
        public void addDeath() {
            this.deathStreak++;
            this.killStreak = 0;
        }
        
        /**
         * 获取连续击杀数
         * 
         * @return 连续击杀数
         */
        public int getKillStreak() {
            return killStreak;
        }
        
        /**
         * 重置连续击杀计数
         */
        public void resetKillStreak() {
            this.killStreak = 0;
        }
        
        /**
         * 获取连续死亡数
         * 
         * @return 连续死亡数
         */
        public int getDeathStreak() {
            return deathStreak;
        }
        
        /**
         * 更新最后战斗时间
         */
        public void updateLastCombatTime() {
            this.lastCombatTime = System.currentTimeMillis();
        }
        
        /**
         * 获取最后战斗时间
         * 
         * @return 时间戳
         */
        public long getLastCombatTime() {
            return lastCombatTime;
        }
    }
}
