package com.adaptive_nemesis.adaptive_nemesismod.event;

import java.util.UUID;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.boss.BossDamageCapHandler;
import com.adaptive_nemesis.adaptive_nemesismod.data.WorldStageDataLoader;
import com.adaptive_nemesis.adaptive_nemesismod.data.WorldStageSavedData;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.AdaptiveFloatSystem;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.DifficultyTracker;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisDataLoader;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EntityFilterHelper;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EnemyScalingHandler;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.WorldStageManager;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionDataLoader;
import com.adaptive_nemesis.adaptive_nemesismod.nemesis.NemesisLootDataLoader;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;
import com.adaptive_nemesis.adaptive_nemesismod.protection.NewbieProtectionHandler;
import com.adaptive_nemesis.adaptive_nemesismod.watchdog.WatchdogService;

import net.minecraft.server.MinecraftServer;
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
        AdaptiveNemesisMod.LOGGER.debug("Adaptive Nemesis 服务器端已启动");

        // 加载世界阶段数据（F-06）：统一走 overworld 的 SavedData，
        // setServerLevel 移出 try 块，加载异常时也保证后续自动保存引用可用
        MinecraftServer server = event.getServer();
        WorldStageManager manager = WorldStageManager.getInstance();
        try {
            WorldStageSavedData.load(server);
            AdaptiveNemesisMod.LOGGER.debug("世界阶段数据已加载，当前阶段: {}", manager.getWorldStage());
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.warn("加载世界阶段数据失败: {}", e.getMessage());
        }
        manager.setServerLevel(server.overworld());
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
        event.addListener(NemesisLootDataLoader.getInstance());
        AdaptiveNemesisMod.LOGGER.debug("入侵、世界阶段与宿敌数据包加载器已注册");
    }

    /**
     * 服务器关闭事件
     *
     * @param event 服务器关闭事件
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        AdaptiveNemesisMod.LOGGER.debug("Adaptive Nemesis 服务器正在关闭...");

        // 保存世界阶段数据（F-06）：统一走 overworld 的 SavedData，
        // setDirty 后随世界存档落盘；随后释放 ServerLevel 引用防止跨存档残留
        MinecraftServer server = event.getServer();
        try {
            WorldStageSavedData.save(server);
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("保存世界阶段数据失败", e);
        }
        WorldStageManager.getInstance().setServerLevel(null);

        // 清理缓存数据
        PlayerStrengthEvaluator.getInstance().clearAllCache();
        
        // 关键修复：重置 EnchantmentScalingHandler 的缓存状态
        // 防止服务器重启时使用失效的 Holder.Reference<Enchantment> 引用
        // 这会导致第二次进存档时网络编码失败
        com.adaptive_nemesis.adaptive_nemesismod.enemy.EnchantmentScalingHandler.resetCaches();

        // 停止看门狗服务
        WatchdogService.getInstance().stop();

        // 归零服务器 tick 计数器（F-62）：避免下一轮服务端沿用上一轮的计数相位，
        // 导致自动保存间隔相位偏移
        serverTickCount = 0;
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
            // 防御性异常捕获：避免与第三方模组（如 Integrated Cataclysm）交互时
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
            "玩家 {} 加入游戏",
            player.getName().getString()
        );

        // 立即评估玩家强度
        PlayerStrengthEvaluator.getInstance().updatePlayerStrength(player);
    }

    /**
     * 玩家登出事件
     *
     * 登出清理链说明（F-05/P-16）：宿敌档案由 NemesisMemorySystem 自行序列化入
     * SavedData 后从内存移除，此处不再调用 clearProfile；
     * 浮动倍率数据（F-23）经 clearPlayerData 从内存清除，防长期运行内存无界增长。
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
            "玩家 {} 离开游戏",
            player.getName().getString()
        );

        // 清理玩家相关缓存
        PlayerStrengthEvaluator.getInstance().clearCache(playerId);
        AdaptiveFloatSystem.getInstance().clearPlayerData(playerId);
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

        // 每1200 tick（60秒）自动保存一次世界阶段数据（F-06：经 overworld SavedData）
        if (serverTickCount % 1200 == 0) {
            if (event.getServer().isRunning() && Config.ENABLE_WORLD_STAGE.get()) {
                WorldStageSavedData.save(event.getServer());
            }
        }
    }
}
