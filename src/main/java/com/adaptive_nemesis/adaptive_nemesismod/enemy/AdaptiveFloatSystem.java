package com.adaptive_nemesis.adaptive_nemesismod.enemy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 智能浮动系统
 * 
 * 根据玩家表现动态调整难度浮动倍数：
 * - 连续击杀 → 浮动倍数 +10%
 * - 频繁死亡 → 浮动倍数 -15%（弱化，不再是唯一下调依据）
 * - 长时间未战斗 → 浮动倍数自动缓慢下降（新增）
 * - 战斗效率低下 → 浮动倍数适当下调（新增）
 * 
 * 改进的难度下调机制：不以玩家死亡作为唯一依据，
 * 当玩家避免死亡但难度已超标时，通过空闲衰减和战斗效率评估自动降低难度。
 * 
 * 目标：让玩家始终感到"有点难，但不多"
 * 
 * @author Adaptive Nemesis Team
 * @version 2.0.0
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
     * 服务器Tick计数器 - 用于定时检查空闲衰减
     */
    private int tickCounter = 0;
    
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
     * 获取指定位置附近玩家的平均浮动倍率
     *
     * @param serverLevel 服务端世界
     * @param center 中心位置
     * @return 附近玩家的平均浮动倍率，无玩家时返回 1.0
     */
    public double getFloatMultiplier(ServerLevel serverLevel, Vec3 center) {
        double range = Config.AREA_SYNC_RANGE.get() * 16;
        double rangeSq = range * range;

        double total = 0.0;
        int count = 0;
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (player.level() == serverLevel && player.distanceToSqr(center) <= rangeSq) {
                PlayerFloatData data = playerFloatData.get(player.getUUID());
                total += data != null ? data.getCurrentMultiplier() : 1.0;
                count++;
            }
        }

        return count > 0 ? total / count : 1.0;
    }
    
    /**
     * 处理玩家击杀事件
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
        PlayerFloatData data = getOrCreateFloatData(playerId);
        
        data.addKill();
        
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
        
        data.updateLastCombatTime();
        data.resetCombatStats();
    }
    
    /**
     * 处理玩家受伤事件 - 用于计算战斗效率
     *
     * @param event 生物受伤事件
     */
    @SubscribeEvent
    public void onPlayerDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        UUID playerId = player.getUUID();
        PlayerFloatData data = getOrCreateFloatData(playerId);
        data.recordDamageTaken(event.getAmount());
        data.updateLastCombatTime();
    }
    
    /**
     * 处理玩家攻击事件 - 用于计算战斗效率
     *
     * @param event 生物受伤事件
     */
    @SubscribeEvent
    public void onEnemyDamage(LivingIncomingDamageEvent event) {
        if (event.getSource() == null || event.getSource().getEntity() == null) {
            return;
        }
        
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        UUID playerId = player.getUUID();
        PlayerFloatData data = getOrCreateFloatData(playerId);
        data.recordDamageDealt(event.getAmount());
        data.updateLastCombatTime();
    }
    
    /**
     * 处理玩家死亡后的浮动倍率调整
     *
     * 使用 PlayerRespawnEvent 而非 LivingDeathEvent 是为了确保玩家确实进入重生流程后再调整倍率。
     * 
     * 注意：死亡不再是难度下调的唯一依据，仅作为辅助调整。
     *
     * @param event 玩家重生事件
     */
    @SubscribeEvent
    public void onPlayerDeath(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        UUID playerId = player.getUUID();
        PlayerFloatData data = getOrCreateFloatData(playerId);
        
        data.addDeath();
        
        double newMultiplier = data.getCurrentMultiplier() - Config.DEATH_STREAK_MULTIPLIER_DECREASE.get();
        data.setMultiplier(Math.max(newMultiplier, Config.FLOAT_MIN.get()));
        
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "玩家 {} 死亡，浮动倍率降低至 {}",
                player.getName().getString(),
                String.format("%.2f", data.getCurrentMultiplier())
            );
        }
        
        data.updateLastCombatTime();
    }
    
    /**
     * 服务器Tick事件 - 定时检查空闲衰减和战斗效率
     *
     * @param event 服务器Tick事件
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;
        
        int checkInterval = Config.IDLE_DECAY_CHECK_INTERVAL.get() * 20;
        if (tickCounter % checkInterval != 0) {
            return;
        }
        
        processIdleDecay();
        processEfficiencyAdjustment();
    }
    
    /**
     * 处理空闲衰减 - 玩家长时间未战斗时自动降低难度
     */
    private void processIdleDecay() {
        if (!Config.ENABLE_IDLE_DECAY.get()) {
            return;
        }
        
        double decayRate = Config.IDLE_DECAY_RATE.get();
        long currentTime = System.currentTimeMillis();
        
        for (Map.Entry<UUID, PlayerFloatData> entry : playerFloatData.entrySet()) {
            PlayerFloatData data = entry.getValue();
            long inactiveMs = currentTime - data.getLastCombatTime();
            
            if (inactiveMs > 60000) {
                double minutesIdle = inactiveMs / 60000.0;
                double decayAmount = decayRate * minutesIdle / (60.0 / Config.IDLE_DECAY_CHECK_INTERVAL.get());
                
                if (decayAmount > 0.001 && data.getCurrentMultiplier() > 1.0) {
                    double newMultiplier = data.getCurrentMultiplier() - decayAmount;
                    data.setMultiplier(Math.max(newMultiplier, Config.FLOAT_MIN.get()));
                    
                    if (Config.ENABLE_DEBUG_LOG.get()) {
                        AdaptiveNemesisMod.LOGGER.debug(
                            "玩家 {} 空闲衰减，浮动倍率降低至 {} (空闲时间: {}秒)",
                            entry.getKey(),
                            String.format("%.2f", data.getCurrentMultiplier()),
                            inactiveMs / 1000
                        );
                    }
                }
            }
        }
    }
    
    /**
     * 处理战斗效率调整 - 战斗效率低下时自动降低难度
     */
    private void processEfficiencyAdjustment() {
        if (!Config.ENABLE_EFFICIENCY_ADJUSTMENT.get()) {
            return;
        }
        
        double threshold = Config.COMBAT_EFFICIENCY_THRESHOLD.get();
        
        for (Map.Entry<UUID, PlayerFloatData> entry : playerFloatData.entrySet()) {
            PlayerFloatData data = entry.getValue();
            double efficiency = data.calculateCombatEfficiency();
            
            if (efficiency >= 0 && efficiency < threshold && data.getCurrentMultiplier() > 1.0) {
                double newMultiplier = data.getCurrentMultiplier() - Config.EFFICIENCY_BASED_DECREASE.get();
                data.setMultiplier(Math.max(newMultiplier, Config.FLOAT_MIN.get()));
                
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug(
                        "玩家 {} 战斗效率低下 ({}%)，浮动倍率降低至 {}",
                        entry.getKey(),
                        String.format("%.0f", efficiency * 100),
                        String.format("%.2f", data.getCurrentMultiplier())
                    );
                }
            }
            
            data.resetCombatStats();
        }
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
     * 降低玩家的浮动倍率（用于入侵胜利后降低难度）
     * 
     * @param playerId 玩家UUID
     * @param decreaseAmount 降低的倍率值
     */
    public void decreaseMultiplier(UUID playerId, double decreaseAmount) {
        PlayerFloatData data = playerFloatData.get(playerId);
        if (data == null) {
            return;
        }
        
        double newMultiplier = data.getCurrentMultiplier() - decreaseAmount;
        data.setMultiplier(Math.max(newMultiplier, Config.FLOAT_MIN.get()));
        
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "玩家 {} 入侵胜利，浮动倍率降低至 {}",
                playerId,
                String.format("%.2f", data.getCurrentMultiplier())
            );
        }
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
         * 战斗效率统计 - 造成的伤害
         */
        private float damageDealt = 0.0f;
        
        /**
         * 战斗效率统计 - 受到的伤害
         */
        private float damageTaken = 0.0f;
        
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
        
        /**
         * 记录造成的伤害
         * 
         * @param amount 伤害值
         */
        public void recordDamageDealt(float amount) {
            this.damageDealt += amount;
        }
        
        /**
         * 记录受到的伤害
         * 
         * @param amount 伤害值
         */
        public void recordDamageTaken(float amount) {
            this.damageTaken += amount;
        }
        
        /**
         * 计算战斗效率
         * 
         * 战斗效率 = 造成的伤害 / (造成的伤害 + 受到的伤害)
         * 返回 -1 表示没有足够的数据进行计算
         * 
         * @return 战斗效率 (0.0 - 1.0)，-1表示数据不足
         */
        public double calculateCombatEfficiency() {
            if (damageDealt == 0 && damageTaken == 0) {
                return -1;
            }
            if (damageDealt + damageTaken == 0) {
                return -1;
            }
            return (double) damageDealt / (damageDealt + damageTaken);
        }
        
        /**
         * 重置战斗效率统计数据
         */
        public void resetCombatStats() {
            this.damageDealt = 0.0f;
            this.damageTaken = 0.0f;
        }
    }
}
