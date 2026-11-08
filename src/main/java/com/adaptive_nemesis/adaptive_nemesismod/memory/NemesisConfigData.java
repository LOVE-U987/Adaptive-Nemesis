package com.adaptive_nemesis.adaptive_nemesismod.memory;

/**
 * 宿敌全局配置数据
 *
 * 定义宿敌系统的全局加成参数，可被数据包覆盖。
 * 包含各战斗风格抗性上限、攻击/速度/生命加成上限、称号档位阈值与加成系数
 * 以及等级计算公式参数。
 *
 * 字段均为不可变值，通过 Builder 构建；构建时对非法输入做下限钳制：
 * 各上限/系数/阈值/半径不小于 0，等级相关参数不小于 1（F-39/F-44）。
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class NemesisConfigData {

    /** 近战抗性加成上限 */
    private final double meleeResistanceCap;

    /** 远程抗性加成上限 */
    private final double rangedResistanceCap;

    /** 魔法抗性加成上限 */
    private final double magicResistanceCap;

    /** 攻击加成上限 */
    private final double attackBonusCap;

    /** 速度加成上限 */
    private final double speedBonusCap;

    /** 生命加成上限 */
    private final double healthBonusCap;

    /** 等级计算：每多少击杀提供 1 级基础等级 */
    private final int killsPerLevel;

    /** 等级计算：每多少死亡提供 1 级额外等级 */
    private final int deathsPerLevel;

    /** 最大宿敌等级 */
    private final int maxLevel;

    /** 称号后缀进入史诗档的强化倍率阈值（F-44：原硬编码 3.0） */
    private final double suffixEpicThreshold;

    /** 称号后缀进入传说档的强化倍率阈值（F-44：原硬编码 2.5） */
    private final double suffixLegendaryThreshold;

    /** 攻击加成系数：玩家每死亡一次提供的攻击加成（F-44：原硬编码 0.02） */
    private final double attackBonusPerDeath;

    /** 速度加成系数：玩家 KDA 每超出阈值 1.0 提供的速度加成（F-44：原硬编码 0.03） */
    private final double speedBonusPerKda;

    /** 速度加成触发的玩家 KDA 阈值（F-44：原硬编码 3.0） */
    private final double kdaSpeedThreshold;

    /** 生命加成系数：玩家每击杀一个敌人提供的生命加成（F-44：原硬编码 0.01） */
    private final double healthBonusPerKill;

    /** 宿敌出现/消失通知的最近玩家搜索半径（F-44：原硬编码 32.0） */
    private final double notificationRadius;

    /**
     * 默认构造函数 - 使用模组内置默认值
     */
    public NemesisConfigData() {
        this(new Builder());
    }

    /**
     * 私有完整构造函数 - 仅由 Builder 调用，构建时完成下限钳制
     *
     * @param builder 已填充各参数的构建器
     */
    private NemesisConfigData(Builder builder) {
        this.meleeResistanceCap = Builder.clampNonNegative(builder.meleeResistanceCap);
        this.rangedResistanceCap = Builder.clampNonNegative(builder.rangedResistanceCap);
        this.magicResistanceCap = Builder.clampNonNegative(builder.magicResistanceCap);
        this.attackBonusCap = Builder.clampNonNegative(builder.attackBonusCap);
        this.speedBonusCap = Builder.clampNonNegative(builder.speedBonusCap);
        this.healthBonusCap = Builder.clampNonNegative(builder.healthBonusCap);
        this.killsPerLevel = Builder.clampAtLeastOne(builder.killsPerLevel);
        this.deathsPerLevel = Builder.clampAtLeastOne(builder.deathsPerLevel);
        this.maxLevel = Builder.clampAtLeastOne(builder.maxLevel);
        this.suffixEpicThreshold = Builder.clampNonNegative(builder.suffixEpicThreshold);
        this.suffixLegendaryThreshold = Builder.clampNonNegative(builder.suffixLegendaryThreshold);
        this.attackBonusPerDeath = Builder.clampNonNegative(builder.attackBonusPerDeath);
        this.speedBonusPerKda = Builder.clampNonNegative(builder.speedBonusPerKda);
        this.kdaSpeedThreshold = Builder.clampNonNegative(builder.kdaSpeedThreshold);
        this.healthBonusPerKill = Builder.clampNonNegative(builder.healthBonusPerKill);
        this.notificationRadius = Builder.clampNonNegative(builder.notificationRadius);
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

    /** 获取称号后缀进入史诗档的强化倍率阈值 */
    public double getSuffixEpicThreshold() {
        return suffixEpicThreshold;
    }

    /** 获取称号后缀进入传说档的强化倍率阈值 */
    public double getSuffixLegendaryThreshold() {
        return suffixLegendaryThreshold;
    }

    /** 获取攻击加成的每死亡系数 */
    public double getAttackBonusPerDeath() {
        return attackBonusPerDeath;
    }

    /** 获取速度加成的 KDA 超额系数 */
    public double getSpeedBonusPerKda() {
        return speedBonusPerKda;
    }

    /** 获取速度加成触发的 KDA 阈值 */
    public double getKdaSpeedThreshold() {
        return kdaSpeedThreshold;
    }

    /** 获取生命加成的每击杀系数 */
    public double getHealthBonusPerKill() {
        return healthBonusPerKill;
    }

    /** 获取宿敌出现/消失通知的最近玩家搜索半径 */
    public double getNotificationRadius() {
        return notificationRadius;
    }

    /**
     * 创建使用内置默认值的配置构建器
     *
     * @return 新的构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 宿敌配置构建器
     *
     * 集中维护全部字段的默认值，缺省项沿用默认值，
     * build 时统一执行下限钳制（cap 与系数不小于 0，等级参数不小于 1）。
     */
    public static final class Builder {

        private double meleeResistanceCap = 0.3;
        private double rangedResistanceCap = 0.3;
        private double magicResistanceCap = 0.3;
        private double attackBonusCap = 0.25;
        private double speedBonusCap = 0.2;
        private double healthBonusCap = 0.5;
        private int killsPerLevel = 10;
        private int deathsPerLevel = 5;
        private int maxLevel = 50;
        private double suffixEpicThreshold = 3.0;
        private double suffixLegendaryThreshold = 2.5;
        private double attackBonusPerDeath = 0.02;
        private double speedBonusPerKda = 0.03;
        private double kdaSpeedThreshold = 3.0;
        private double healthBonusPerKill = 0.01;
        private double notificationRadius = 32.0;

        public Builder meleeResistanceCap(double value) { this.meleeResistanceCap = value; return this; }

        public Builder rangedResistanceCap(double value) { this.rangedResistanceCap = value; return this; }

        public Builder magicResistanceCap(double value) { this.magicResistanceCap = value; return this; }

        public Builder attackBonusCap(double value) { this.attackBonusCap = value; return this; }

        public Builder speedBonusCap(double value) { this.speedBonusCap = value; return this; }

        public Builder healthBonusCap(double value) { this.healthBonusCap = value; return this; }

        public Builder killsPerLevel(int value) { this.killsPerLevel = value; return this; }

        public Builder deathsPerLevel(int value) { this.deathsPerLevel = value; return this; }

        public Builder maxLevel(int value) { this.maxLevel = value; return this; }

        public Builder suffixEpicThreshold(double value) { this.suffixEpicThreshold = value; return this; }

        public Builder suffixLegendaryThreshold(double value) { this.suffixLegendaryThreshold = value; return this; }

        public Builder attackBonusPerDeath(double value) { this.attackBonusPerDeath = value; return this; }

        public Builder speedBonusPerKda(double value) { this.speedBonusPerKda = value; return this; }

        public Builder kdaSpeedThreshold(double value) { this.kdaSpeedThreshold = value; return this; }

        public Builder healthBonusPerKill(double value) { this.healthBonusPerKill = value; return this; }

        public Builder notificationRadius(double value) { this.notificationRadius = value; return this; }

        /**
         * 构建配置对象（执行下限钳制）
         *
         * @return 钳制后的不可变配置
         */
        public NemesisConfigData build() {
            return new NemesisConfigData(this);
        }

        /**
         * 将数值钳制为不小于 0
         *
         * @param value 原始值
         * @return 钳制后的值
         */
        private static double clampNonNegative(double value) {
            return Math.max(0.0, value);
        }

        /**
         * 将整数钳制为不小于 1
         *
         * @param value 原始值
         * @return 钳制后的值
         */
        private static int clampAtLeastOne(int value) {
            return Math.max(1, value);
        }
    }
}
