package com.adaptive_nemesis.adaptive_nemesismod.event;

import java.util.UUID;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.boss.BossDamageCapHandler;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EnemyScalingHandler;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;
import com.adaptive_nemesis.adaptive_nemesismod.protection.NewbieProtectionHandler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 模组通用事件处理器
 * 
 * 处理各种游戏事件，协调各子系统的工作
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class ModEventHandler {
    
    /**
     * 单例实例
     */
    private static ModEventHandler INSTANCE;
    
    /**
     * 服务器tick计数器
     */
    private int serverTickCount = 0;
    
    /**
     * 私有构造函数 - 单例模式
     */
    private ModEventHandler() {}
    
    /**
     * 获取单例实例
     * 
     * @return ModEventHandler 实例
     */
    public static synchronized ModEventHandler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ModEventHandler();
        }
        return INSTANCE;
    }
    
    /**
     * 服务器启动完成事件
     * 
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        AdaptiveNemesisMod.LOGGER.info("🌐 Adaptive Nemesis 服务器端已启动");
    }
    
    /**
     * 服务器关闭事件
     * 
     * @param event 服务器关闭事件
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        AdaptiveNemesisMod.LOGGER.info("🛑 Adaptive Nemesis 服务器正在关闭...");
        
        // 清理缓存数据
        PlayerStrengthEvaluator.getInstance().clearAllCache();
    }
    
    /**
     * 实体加入世界事件 - 处理Boss生成
     * 
     * @param event 实体加入世界事件
     */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        
        // 处理Boss生成
        if (event.getEntity() instanceof Mob mob) {
            if (BossDamageCapHandler.getInstance().isBoss(mob)) {
                BossDamageCapHandler.getInstance().applyBossBuffs(mob);
            }
        }
    }
    
    /**
     * 玩家登录事件
     * 
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        AdaptiveNemesisMod.LOGGER.info(
            "👤 玩家 {} 加入游戏",
            player.getName().getString()
        );
        
        // 立即评估玩家强度
        PlayerStrengthEvaluator.getInstance().updatePlayerStrength(player);
    }
    
    /**
     * 玩家登出事件
     * 
     * @param event 玩家登出事件
     */
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        
        UUID playerId = player.getUUID();
        
        AdaptiveNemesisMod.LOGGER.info(
            "👋 玩家 {} 离开游戏",
            player.getName().getString()
        );
        
        // 清理玩家相关缓存
        PlayerStrengthEvaluator.getInstance().clearCache(playerId);
        NemesisMemorySystem.getInstance().clearProfile(playerId);
        NewbieProtectionHandler.getInstance().clearProtectionData(playerId);
    }
    
    /**
     * 服务器Tick事件 - 定期执行维护任务
     * 
     * @param event 服务器Tick事件
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        serverTickCount++;
        
        // 每600 tick（30秒）检查一次不活跃玩家的浮动倍率
        if (serverTickCount % 600 == 0) {
            // 可以在这里添加定期维护逻辑
        }
    }
}
