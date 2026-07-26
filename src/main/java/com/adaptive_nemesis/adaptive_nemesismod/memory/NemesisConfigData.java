package com.adaptive_nemesis.adaptive_nemesismod.memory;

/**
 * 宿敌全局配置数据
 *
 * 定义宿敌系统的全局加成参数，可被数据包覆盖。
 * 包含各战斗风格抗性上限、攻击/速度/生命加成上限以及等级计算公式参数。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class NemesisConfigData {

    /**
     * 近战抗性加成上限
     */
    private final double meleeResistanceCap;

    /**
     * 远程抗性加成上限
     */
    private final double rangedResistanceCap;

    /**
     * 魔法抗性加成上限
     */
    private final double magicResistanceCap;

    /**
     * 攻击加成上限
     */
    private final double attackBonusCap;

    /**
     * 速度加成上限
     */
    private final double speedBonusCap;

    /**
     * 生命加成上限
     */
    private final double healthBonusCap;

    /**
     * 等级计算：每多少击杀提供 1 级基础等级
     */
    private final int killsPerLevel;

    /**
     * 等级计算：每多少死亡提供 1 级额外等级
     */
    private final int deathsPerLevel;

    /**
     * 最大宿敌等级
     */
    private final int maxLevel;

    /**
     * 默认构造函数 - 使用模组内置默认值
     */
    public NemesisConfigData() {
        this(0.3, 0.3, 0.3, 0.25, 0.2, 0.5, 10, 5, 50);
    }

    /**
     * 完整构造函数
     *
     * @param meleeResistanceCap 近战抗性上限
     * @param rangedResistanceCap 远程抗性上限
     * @param magicResistanceCap 魔法抗性上限
     * @param attackBonusCap 攻击加成上限
     * @param speedBonusCap 速度加成上限
     * @param healthBonusCap 生命加成上限
     * @param killsPerLevel 每级所需击杀数
     * @param deathsPerLevel 每级额外等级所需死亡数
     * @param maxLevel 最大宿敌等级
     */
    public NemesisConfigData(double meleeResistanceCap, double rangedResistanceCap,
                              double magicResistanceCap, double attackBonusCap,
                              double speedBonusCap, double healthBonusCap,
                              int killsPerLevel, int deathsPerLevel, int maxLevel) {
        this.meleeResistanceCap = meleeResistanceCap;
        this.rangedResistanceCap = rangedResistanceCap;
        this.magicResistanceCap = magicResistanceCap;
        this.attackBonusCap = attackBonusCap;
        this.speedBonusCap = speedBonusCap;
        this.healthBonusCap = healthBonusCap;
        this.killsPerLevel = killsPerLevel;
        this.deathsPerLevel = deathsPerLevel;
        this.maxLevel = maxLevel;
    }

    public double getMeleeResistanceCap() {
        return meleeResistanceCap;
    }

    public double getRangedResistanceCap() {
        return rangedResistanceCap;
    }

    public double getMagicResistanceCap() {
        return magicResistanceCap;
    }

    public double getAttackBonusCap() {
        return attackBonusCap;
    }

    public double getSpeedBonusCap() {
        return speedBonusCap;
    }

    public double getHealthBonusCap() {
        return healthBonusCap;
    }

    public int getKillsPerLevel() {
        return killsPerLevel;
    }

    public int getDeathsPerLevel() {
        return deathsPerLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }
}
