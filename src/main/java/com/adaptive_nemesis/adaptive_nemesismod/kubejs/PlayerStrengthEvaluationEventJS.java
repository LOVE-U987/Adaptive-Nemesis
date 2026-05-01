package com.adaptive_nemesis.adaptive_nemesismod.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * 玩家强度评估事件
 *
 * 当自适应宿敌模组评估玩家强度时触发。
 * KubeJS 脚本可以监听此事件来：
 * - 添加自定义的强度评估逻辑
 * - 修改最终强度值
 * - 根据特定条件调整玩家强度
 * - 为特定玩家添加额外加成
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class PlayerStrengthEvaluationEventJS implements KubeEvent {

    /**
     * 被评估的玩家
     */
    private final ServerPlayer player;

    /**
     * 基础强度值
     */
    private final double baseStrength;

    /**
     * 最终强度值（可被修改）
     */
    private double finalStrength;

    /**
     * 防御强度
     */
    private final double defenseStrength;

    /**
     * 攻击强度
     */
    private final double attackStrength;

    /**
     * 魔法强度
     */
    private final double magicStrength;

    /**
     * 战斗强度
     */
    private final double combatStrength;

    /**
     * 构造函数
     *
     * @param player 被评估的玩家
     * @param baseStrength 基础强度
     * @param defenseStrength 防御强度
     * @param attackStrength 攻击强度
     * @param magicStrength 魔法强度
     * @param combatStrength 战斗强度
     */
    public PlayerStrengthEvaluationEventJS(ServerPlayer player, double baseStrength,
                                            double defenseStrength, double attackStrength,
                                            double magicStrength, double combatStrength) {
        this.player = player;
        this.baseStrength = baseStrength;
        this.finalStrength = baseStrength;
        this.defenseStrength = defenseStrength;
        this.attackStrength = attackStrength;
        this.magicStrength = magicStrength;
        this.combatStrength = combatStrength;
    }

    /**
     * 获取被评估的玩家
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
     * 获取玩家UUID
     *
     * @return UUID字符串
     */
    public String getPlayerUUID() {
        return player.getUUID().toString();
    }

    /**
     * 获取基础强度
     *
     * @return 基础强度值
     */
    public double getBaseStrength() {
        return baseStrength;
    }

    /**
     * 获取最终强度
     *
     * @return 当前最终强度值
     */
    public double getFinalStrength() {
        return finalStrength;
    }

    /**
     * 设置最终强度
     *
     * @param strength 新的强度值
     */
    public void setFinalStrength(double strength) {
        this.finalStrength = Math.max(0.0, strength);
    }

    /**
     * 增加强度
     *
     * @param amount 增加的数值
     */
    public void addStrength(double amount) {
        this.finalStrength += amount;
    }

    /**
     * 乘以强度
     *
     * @param factor 乘数
     */
    public void multiplyStrength(double factor) {
        this.finalStrength *= factor;
    }

    /**
     * 获取防御强度
     *
     * @return 防御强度
     */
    public double getDefenseStrength() {
        return defenseStrength;
    }

    /**
     * 获取攻击强度
     *
     * @return 攻击强度
     */
    public double getAttackStrength() {
        return attackStrength;
    }

    /**
     * 获取魔法强度
     *
     * @return 魔法强度
     */
    public double getMagicStrength() {
        return magicStrength;
    }

    /**
     * 获取战斗强度
     *
     * @return 战斗强度
     */
    public double getCombatStrength() {
        return combatStrength;
    }

    /**
     * 检查玩家是否有特定物品
     *
     * @param itemId 物品ID
     * @return 如果有返回 true
     */
    public boolean hasItem(String itemId) {
        for (var item : player.getInventory().items) {
            if (!item.isEmpty() && item.getItem().toString().equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取玩家等级
     *
     * @return 经验等级
     */
    public int getPlayerLevel() {
        return player.experienceLevel;
    }

    /**
     * 获取玩家生命值
     *
     * @return 当前生命值
     */
    public double getPlayerHealth() {
        return player.getHealth();
    }

    /**
     * 获取玩家最大生命值
     *
     * @return 最大生命值
     */
    public double getPlayerMaxHealth() {
        return player.getMaxHealth();
    }
}
