package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;

import java.util.UUID;

/**
 * 宿敌记忆更新事件
 *
 * 当宿敌记忆系统更新玩家数据时触发。
 * KubeJS 脚本可以监听此事件来：
 * - 在宿敌记忆更新时执行自定义逻辑
 * - 根据宿敌等级给予玩家奖励
 * - 触发特殊事件或效果
 * - 记录统计数据
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class NemesisMemoryUpdateEventJS implements KubeEvent {

    /**
     * 玩家UUID
     */
    private final UUID playerUUID;

    /**
     * 玩家名称
     */
    private final String playerName;

    /**
     * 击杀数
     */
    private final int killCount;

    /**
     * 死亡数
     */
    private final int deathCount;

    /**
     * 宿敌等级
     */
    private final int nemesisLevel;

    /**
     * 攻击加成
     */
    private final double attackBonus;

    /**
     * 速度加成
     */
    private final double speedBonus;

    /**
     * 生命加成
     */
    private final double healthBonus;

    /**
     * 构造函数
     *
     * @param playerUUID 玩家UUID
     * @param playerName 玩家名称
     * @param killCount 击杀数
     * @param deathCount 死亡数
     * @param nemesisLevel 宿敌等级
     * @param attackBonus 攻击加成
     * @param speedBonus 速度加成
     * @param healthBonus 生命加成
     */
    public NemesisMemoryUpdateEventJS(UUID playerUUID, String playerName,
                                       int killCount, int deathCount, int nemesisLevel,
                                       double attackBonus, double speedBonus, double healthBonus) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.killCount = killCount;
        this.deathCount = deathCount;
        this.nemesisLevel = nemesisLevel;
        this.attackBonus = attackBonus;
        this.speedBonus = speedBonus;
        this.healthBonus = healthBonus;
    }

    /**
     * 获取玩家UUID
     *
     * @return UUID字符串
     */
    public String getPlayerUUID() {
        return playerUUID.toString();
    }

    /**
     * 获取玩家名称
     *
     * @return 玩家名称
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * 获取击杀数
     *
     * @return 击杀数
     */
    public int getKillCount() {
        return killCount;
    }

    /**
     * 获取死亡数
     *
     * @return 死亡数
     */
    public int getDeathCount() {
        return deathCount;
    }

    /**
     * 获取KDA比率
     *
     * @return KDA比率
     */
    public double getKDRatio() {
        return deathCount > 0 ? (double) killCount / deathCount : killCount;
    }

    /**
     * 获取宿敌等级
     *
     * @return 宿敌等级
     */
    public int getNemesisLevel() {
        return nemesisLevel;
    }

    /**
     * 获取攻击加成
     *
     * @return 攻击加成百分比
     */
    public double getAttackBonus() {
        return attackBonus;
    }

    /**
     * 获取速度加成
     *
     * @return 速度加成百分比
     */
    public double getSpeedBonus() {
        return speedBonus;
    }

    /**
     * 获取生命加成
     *
     * @return 生命加成百分比
     */
    public double getHealthBonus() {
        return healthBonus;
    }

    /**
     * 获取总加成值
     *
     * @return 所有加成的总和
     */
    public double getTotalBonus() {
        return attackBonus + speedBonus + healthBonus;
    }

    /**
     * 检查是否达到特定等级
     *
     * @param level 目标等级
     * @return 如果达到或超过返回 true
     */
    public boolean isAtLeastLevel(int level) {
        return nemesisLevel >= level;
    }

    /**
     * 检查是否是新记录（击杀数达到里程碑）
     *
     * @return 如果是里程碑数返回 true
     */
    public boolean isMilestone() {
        return killCount > 0 && killCount % 10 == 0;
    }
}
