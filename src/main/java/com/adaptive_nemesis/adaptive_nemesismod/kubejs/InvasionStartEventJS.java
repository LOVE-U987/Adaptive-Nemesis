package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionSystem;
import dev.latvian.mods.kubejs.event.EventJS;
import net.minecraft.server.level.ServerPlayer;

/**
 * 入侵开始事件
 *
 * 当玩家触发入侵事件时触发。
 * KubeJS 脚本可以监听此事件来：
 * - 修改入侵波次数
 * - 修改难度倍率
 * - 取消特定玩家的入侵
 * - 根据自定义逻辑触发额外效果
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class InvasionStartEventJS extends EventJS {

    /**
     * 触发入侵的玩家
     */
    private final ServerPlayer player;

    /**
     * 入侵类型
     */
    private final InvasionSystem.InvasionType type;

    /**
     * 总波次数（可被修改）
     */
    private int totalWaves;

    /**
     * 难度倍率（可被修改）
     */
    private double difficultyMultiplier;

    /**
     * 是否取消入侵
     */
    private boolean cancelled = false;

    /**
     * 构造函数
     *
     * @param player 触发玩家
     * @param type 入侵类型
     * @param totalWaves 初始总波次数
     * @param difficultyMultiplier 初始难度倍率
     */
    public InvasionStartEventJS(ServerPlayer player, InvasionSystem.InvasionType type,
                                int totalWaves, double difficultyMultiplier) {
        this.player = player;
        this.type = type;
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
     * 获取总波次数
     *
     * @return 总波次数
     */
    public int getTotalWaves() {
        return totalWaves;
    }

    /**
     * 设置总波次数
     *
     * @param totalWaves 新的总波次数
     */
    public void setTotalWaves(int totalWaves) {
        this.totalWaves = Math.max(1, totalWaves);
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
     * 设置难度倍率
     *
     * @param difficultyMultiplier 新的难度倍率
     */
    public void setDifficultyMultiplier(double difficultyMultiplier) {
        this.difficultyMultiplier = Math.max(1.0, difficultyMultiplier);
    }

    /**
     * 取消入侵事件
     */

    /**
     * KubeJS 脚本标准 API：event.cancel()
     * 与 cancelEvent() 同步，并委托基类 EventJS.cancel() 产生 EventResult.interruptFalse。
     */
    @Override
    public Object cancel() throws dev.latvian.mods.kubejs.event.EventExit {
        this.cancelled = true;
        return super.cancel();
    }

    /** 脚本友好别名 */
    public boolean isCancelled() {
        return cancelled;
    }

    public void cancelEvent() {
        this.cancelled = true;
    }

    /**
     * 检查是否已取消
     *
     * @return 如果已取消返回 true
     */
    public boolean isEventCancelled() {
        return cancelled;
    }
}
