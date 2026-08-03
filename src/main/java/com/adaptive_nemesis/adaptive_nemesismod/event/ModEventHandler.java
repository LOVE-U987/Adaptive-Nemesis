package com.adaptive_nemesis.adaptive_nemesismod.event;

import java.util.UUID;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.boss.BossDamageCapHandler;
import com.adaptive_nemesis.adaptive_nemesismod.data.WorldStageDataLoader;
import com.adaptive_nemesis.adaptive_nemesismod.data.WorldStageSavedData;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.DifficultyTracker;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisDataLoader;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EntityFilterHelper;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EnemyScalingHandler;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.WorldStageManager;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionDataLoader;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;
import com.adaptive_nemesis.adaptive_nemesismod.protection.NewbieProtectionHandler;
import com.adaptive_nemesis.adaptive_nemesismod.watchdog.WatchdogService;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
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
 * @version 1.1.0
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
        AdaptiveNemesisMod.LOGGER.debug("🌐 Adaptive Nemesis 服务器端已启动");

        // 加载世界阶段数据
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            try {
                WorldStageSavedData.load(level);
                int stage = WorldStageManager.getInstance().getWorldStage();
                AdaptiveNemesisMod.LOGGER.debug("世界阶段数据已加载，当前阶段: {}", stage);
                
                // 设置ServerLevel引用用于后续自动保存
                WorldStageManager.getInstance().setServerLevel(level);
            } catch (Exception e) {
                AdaptiveNemesisMod.LOGGER.warn("加载世界阶段数据失败: {}", e.getMessage());
            }
            break; // 只需要从一个维度加载（全服共享）
        }
    }

    /**
     * 注册数据包重新加载监听器
     *
     * @param event 重新加载监听器事件
     */
    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(InvasionDataLoader.getInstance());
        event.addListener(WorldStageDataLoader.getInstance());
        event.addListener(NemesisDataLoader.getInstance());
        AdaptiveNemesisMod.LOGGER.debug("入侵、世界阶段与宿敌数据包加载器已注册");
    }

    /**
     * 服务器关闭事件
     *
     * @param event 服务器关闭事件
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        AdaptiveNemesisMod.LOGGER.debug("🛑 Adaptive Nemesis 服务器正在关闭...");

        // 保存世界阶段数据
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            try {
                WorldStageSavedData.save(level);
                AdaptiveNemesisMod.LOGGER.debug("世界阶段数据已保存");
            } catch (Exception e) {
                AdaptiveNemesisMod.LOGGER.error("保存世界阶段数据失败", e);
            }
            break; // 只需要保存一次（全服共享）
        }

        // 清理缓存数据
        PlayerStrengthEvaluator.getInstance().clearAllCache();

        // 停止看门狗服务
        WatchdogService.getInstance().stop();
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

        try {
            // 处理Boss生成
            if (event.getEntity() instanceof Mob mob) {
                // 检查黑名单 - 被ban的实体跳过Boss限伤和Boss加成
                if (EntityFilterHelper.getInstance().isBlocked(mob)) {
                    return;
                }

                if (BossDamageCapHandler.getInstance().isBoss(mob)) {
                    // 看门狗：记录 Boss buff 处理状态
                    if (Config.ENABLE_WATCHDOG.get()) {
                        WatchdogService.getInstance().updateBossBuffProcessing(
                            mob.getName().getString(),
                            mob.getType().getDescriptionId(),
                            String.format("[%.0f, %.0f, %.0f]", mob.getX(), mob.getY(), mob.getZ()),
                            mob.level().dimension().location().toString(),
                            "preApply"
                        );
                    }
                    BossDamageCapHandler.getInstance().applyBossBuffs(mob);
                }
            }
        } catch (Exception e) {
            // 🛡️ 防御性异常捕获：避免与第三方模组（如 Integrated Cataclysm）交互时
            // 的未预期异常传播到事件总线导致服务端崩溃或死锁
            AdaptiveNemesisMod.LOGGER.error(
                "处理实体 {} (类型: {}) 加入世界事件时发生异常: {}",
                event.getEntity().getName().getString(),
                event.getEntity().getType().getDescriptionId(),
                e.getMessage()
            );
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.error("异常堆栈:", e);
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
        AdaptiveNemesisMod.LOGGER.debug(
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
        AdaptiveNemesisMod.LOGGER.debug(
            "👋 玩家 {} 离开游戏",
            player.getName().getString()
        );

        // 清理玩家相关缓存
        PlayerStrengthEvaluator.getInstance().clearCache(playerId);
        NemesisMemorySystem.getInstance().clearProfile(playerId);
        NewbieProtectionHandler.getInstance().clearProtectionData(playerId);
        
        // 清理玩家难度缓动状态
        DifficultyTracker.getInstance().clearPlayerState(playerId);
    }

    /**
     * 服务器Tick事件 - 定期执行维护任务
     *
     * @param event 服务器Tick事件
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        serverTickCount++;

        // 看门狗：更新服务端 tick 时间戳
        if (Config.ENABLE_WATCHDOG.get()) {
            WatchdogService.getInstance().updateServerTick();
        }

        // 每600 tick（30秒）检查一次不活跃玩家的浮动倍率
        if (serverTickCount % 600 == 0) {
            // 可以在这里添加定期维护逻辑
        }

        // 每1200 tick（60秒）自动保存一次世界阶段数据
        if (serverTickCount % 1200 == 0) {
            if (event.getServer().isRunning() && Config.ENABLE_WORLD_STAGE.get()) {
                ServerLevel level = event.getServer().overworld();
                if (level != null) {
                    WorldStageSavedData.save(level);
                }
            }
        }
    }
}
