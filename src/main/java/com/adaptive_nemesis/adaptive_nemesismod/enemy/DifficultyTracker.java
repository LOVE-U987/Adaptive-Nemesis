package com.adaptive_nemesis.adaptive_nemesismod.enemy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 难度缓动追踪器
 *
 * 解决怪物瞬间变强导致的负反馈问题：
 * - 玩家强度提升后，怪物需要一段时间才会达到目标难度
 * - 防止玩家通过临时切换装备控制难度
 * - 使用平滑缓动函数，让难度变化更自然
 *
 * 支持两种模式：
 * - 全局模式（默认）：所有玩家共享缓动
 * - 按玩家模式：每个玩家有独立的缓动（推荐）
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class DifficultyTracker {

    private static DifficultyTracker INSTANCE;

    /**
     * 全局缓动状态（兼容旧版本）
     */
    private final DifficultyState globalState = new DifficultyState();

    /**
     * 玩家级别的缓动状态（UUID -> DifficultyState）
     */
    private final Map<UUID, DifficultyState> playerStates = new HashMap<>();

    /**
     * Tick计数器
     */
    private int tickCounter = 0;

    private DifficultyTracker() {}

    public static synchronized DifficultyTracker getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DifficultyTracker();
        }
        return INSTANCE;
    }

    /**
     * 获取缓动后的倍率（全局模式）
     *
     * @param targetMultiplier 目标倍率
     * @return 缓动后的实际倍率
     */
    public double getEasedMultiplier(double targetMultiplier) {
        if (!Config.ENABLE_DIFFICULTY_SMOOTHING.get()) {
            return targetMultiplier;
        }

        // 更新目标
        globalState.target = targetMultiplier;

        return globalState.current;
    }

    /**
     * 获取特定玩家的缓动倍率
     *
     * @param player 玩家
     * @param targetMultiplier 目标倍率
     * @return 缓动后的倍率
     */
    public double getEasedMultiplierForPlayer(ServerPlayer player, double targetMultiplier) {
        if (!Config.ENABLE_DIFFICULTY_SMOOTHING.get()) {
            return targetMultiplier;
        }

        UUID playerId = player.getUUID();
        DifficultyState state = playerStates.computeIfAbsent(playerId, k -> new DifficultyState());

        state.target = targetMultiplier;
        return state.current;
    }

    /**
     * 获取当前缓动倍率（全局模式，兼容旧代码）
     */
    public double getCurrentMultiplier() {
        return globalState.current;
    }

    /**
     * 获取当前目标倍率（全局模式，兼容旧代码）
     */
    public double getTargetMultiplier() {
        return globalState.target;
    }

    /**
     * 清除玩家状态（玩家退出时调用）
     */
    public void clearPlayerState(UUID playerId) {
        playerStates.remove(playerId);
    }

    /**
     * 服务器Tick事件 - 更新缓动
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!Config.ENABLE_DIFFICULTY_SMOOTHING.get()) {
            return;
        }

        tickCounter++;

        // 检查是否需要更新
        int updateInterval = Config.DIFFICULTY_SMOOTHING_TICK_INTERVAL.get();
        if (tickCounter % updateInterval != 0) {
            return;
        }

        // 更新全局状态
        updateState(globalState);

        // 更新所有玩家状态
        for (DifficultyState state : playerStates.values()) {
            updateState(state);
        }
    }

    /**
     * 更新单个缓动状态
     */
    private void updateState(DifficultyState state) {
        if (state.current == state.target) {
            return;
        }

        double easingFactor = Config.DIFFICULTY_SMOOTHING_FACTOR.get();

        // 使用Smoothstep缓动（更自然）
        double delta = state.target - state.current;
        double smoothDelta = applySmoothstep(delta, easingFactor);

        state.current += smoothDelta;

        // 防止浮点误差
        if (Math.abs(state.current - state.target) < 0.001) {
            state.current = state.target;
        }
    }

    /**
     * 应用Smoothstep缓动函数
     * 使难度变化更自然，快速开始、缓慢结束
     */
    private double applySmoothstep(double delta, double factor) {
        // 计算标准化的进度 (0-1)
        double progress = 1.0 - (Math.abs(delta) / (Math.abs(delta) + 1.0));

        // Smoothstep公式: 3t² - 2t³
        double smoothedProgress = progress * progress * (3.0 - 2.0 * progress);

        // 缓动增量
        double baseDelta = delta * factor;
        return baseDelta * (0.5 + 0.5 * smoothedProgress);
    }

    /**
     * 重置所有缓动状态
     */
    public void resetAll() {
        globalState.current = 1.0;
        globalState.target = 1.0;
        playerStates.clear();
    }

    /**
     * 单个玩家/全局的缓动状态
     */
    private static class DifficultyState {
        double current = 1.0; // 当前倍率
        double target = 1.0;  // 目标倍率
    }
}
