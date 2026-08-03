package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * 世界阶段变化事件
 *
 * 当世界阶段提升时触发。KubeJS 脚本可以监听此事件来：
 * - 在世界阶段提升时执行自定义逻辑
 * - 根据新旧阶段调整全局难度
 * - 发送自定义通知或奖励
 * - 取消或修改阶段提升后的行为
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class WorldStageChangeEventJS implements KubeEvent {

    /**
     * 触发阶段提升的玩家
     */
    private final ServerPlayer player;

    /**
     * 旧的世界阶段
     */
    private final int oldStage;

    /**
     * 新的世界阶段
     */
    private final int newStage;

    /**
     * 当前阶段难度倍率
     */
    private double stageMultiplier;

    /**
     * 已击杀的 Boss 种类数量
     */
    private final int defeatedBossCount;

    /**
     * 是否取消后续默认行为
     */
    private boolean cancelled = false;

    /**
     * 构造函数
     *
     * @param player 触发玩家
     * @param oldStage 旧阶段
     * @param newStage 新阶段
     * @param stageMultiplier 阶段难度倍率
     * @param defeatedBossCount 已击杀 Boss 数量
     */
    public WorldStageChangeEventJS(ServerPlayer player, int oldStage, int newStage,
                                    double stageMultiplier, int defeatedBossCount) {
        this.player = player;
        this.oldStage = oldStage;
        this.newStage = newStage;
        this.stageMultiplier = stageMultiplier;
        this.defeatedBossCount = defeatedBossCount;
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
     * 获取旧阶段
     *
     * @return 旧的世界阶段
     */
    public int getOldStage() {
        return oldStage;
    }

    /**
     * 获取新阶段
     *
     * @return 新的世界阶段
     */
    public int getNewStage() {
        return newStage;
    }

    /**
     * 获取阶段提升的差值
     *
     * @return 新阶段减旧阶段
     */
    public int getStageDelta() {
        return newStage - oldStage;
    }

    /**
     * 获取当前阶段难度倍率
     *
     * @return 阶段倍率
     */
    public double getStageMultiplier() {
        return stageMultiplier;
    }

    /**
     * 设置阶段难度倍率
     *
     * @param multiplier 新的阶段倍率
     */
    public void setStageMultiplier(double multiplier) {
        this.stageMultiplier = Math.max(1.0, multiplier);
    }

    /**
     * 获取已击杀 Boss 数量
     *
     * @return Boss 数量
     */
    public int getDefeatedBossCount() {
        return defeatedBossCount;
    }

    /**
     * 取消事件
     */
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
