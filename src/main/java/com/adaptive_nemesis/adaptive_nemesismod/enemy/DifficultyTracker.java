package com.adaptive_nemesis.adaptive_nemesismod.enemy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
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
     * 应用标准 Smoothstep 缓动函数
     *
     * 标准 Smoothstep 定义在 [0,1] 区间：
     *   t = clamp(x, 0, 1)
     *   smoothstep(t) = 3t² - 2t³
     *
     * 此处将配置中的 easingFactor 视为单步最大进度比例，
     * 先对 factor 本身做 smoothstep，再乘以当前与目标之间的差值 delta，
     * 使难度变化在起点/终点处速度接近 0，过渡更加自然。
     *
     * @param delta  当前值与目标值的差（target - current）
     * @param factor 单步进度比例，通常取自配置 DIFFICULTY_SMOOTHING_FACTOR
     * @return 缓动后的单步增量
     */
    private double applySmoothstep(double delta, double factor) {
        // 把 factor 限制在 [0,1]，作为标准 smoothstep 的输入 t
        double t = Mth.clamp(factor, 0.0, 1.0);

        // 标准 Smoothstep 公式: 3t² - 2t³
        double smoothedT = t * t * (3.0 - 2.0 * t);

        // 用缓动后的进度比例作用于剩余差值
        return delta * smoothedT;
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
