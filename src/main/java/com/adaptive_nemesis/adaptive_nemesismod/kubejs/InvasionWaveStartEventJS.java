package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionSystem;
import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * 入侵波次开始事件
 *
 * 当某一波次进入战斗阶段时触发。
 * KubeJS 脚本可以监听此事件来：
 * - 根据波次执行自定义逻辑
 * - 记录玩家进度
 * - 触发额外效果或奖励
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class InvasionWaveStartEventJS implements KubeEvent {

    /**
     * 触发入侵的玩家
     */
    private final ServerPlayer player;

    /**
     * 入侵类型
     */
    private final InvasionSystem.InvasionType type;

    /**
     * 当前波次编号
     */
    private final int currentWave;

    /**
     * 总波次数
     */
    private final int totalWaves;

    /**
     * 当前波次难度倍率
     */
    private final double difficultyMultiplier;

    /**
     * 构造函数
     *
     * @param player 触发玩家
     * @param type 入侵类型
     * @param currentWave 当前波次编号
     * @param totalWaves 总波次数
     * @param difficultyMultiplier 难度倍率
     */
    public InvasionWaveStartEventJS(ServerPlayer player, InvasionSystem.InvasionType type,
                                    int currentWave, int totalWaves, double difficultyMultiplier) {
        this.player = player;
        this.type = type;
        this.currentWave = Math.max(1, currentWave);
        this.totalWaves = Math.max(1, totalWaves);
        this.difficultyMultiplier = Math.max(1.0, difficultyMultiplier);
    }

    /**
     * 获取触发玩家
     *
     * @return 玩家对象
     */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * 获取玩家名称
     *
     * @return 玩家名称
     */
    public String getPlayerName() {
        return player.getName().getString();
    }

    /**
     * 获取入侵类型
     *
     * @return 入侵类型
     */
    public InvasionSystem.InvasionType getType() {
        return type;
    }

    /**
     * 获取当前波次
     *
     * @return 当前波次编号
     */
    public int getCurrentWave() {
        return currentWave;
    }

    /**
     * 获取总波次数
     *
     * @return 总波次数
     */
    public int getTotalWaves() {
        return totalWaves;
    }

    /**
     * 获取难度倍率
     *
     * @return 难度倍率
     */
    public double getDifficultyMultiplier() {
        return difficultyMultiplier;
    }

    /**
     * 获取进度字符串
     *
     * @return 格式如 "2/6"
     */
    public String getProgress() {
        return String.format("%d/%d", currentWave, totalWaves);
    }
}
