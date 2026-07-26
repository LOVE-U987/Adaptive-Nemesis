package com.adaptive_nemesis.adaptive_nemesismod.data;

/**
 * 世界阶段数据配置
 *
 * 每个阶段定义全局难度参数，可被数据包覆盖。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class WorldStageDataConfig {

    /**
     * 阶段编号
     */
    private final int stage;

    /**
     * 阶段难度倍率
     */
    private final double multiplier;

    /**
     * 最大血量倍率上限
     */
    private final double maxHealthMultiplier;

    /**
     * 最大伤害倍率上限
     */
    private final double maxDamageMultiplier;

    /**
     * 最大护甲倍率上限
     */
    private final double maxArmorMultiplier;

    /**
     * 浮动难度最小值
     */
    private final double floatMin;

    /**
     * 浮动难度最大值
     */
    private final double floatMax;

    /**
     * 入侵事件最大波次数
     */
    private final int invasionMaxWaves;

    /**
     * 默认空配置
     *
     * @param stage 阶段编号
     */
    public WorldStageDataConfig(int stage) {
        this(stage, -1.0, -1.0, -1.0, -1.0, -1.0, -1.0, -1);
    }

    /**
     * 完整构造函数
     *
     * @param stage 阶段编号
     * @param multiplier 阶段难度倍率
     * @param maxHealthMultiplier 最大血量倍率上限
     * @param maxDamageMultiplier 最大伤害倍率上限
     * @param maxArmorMultiplier 最大护甲倍率上限
     * @param floatMin 浮动难度最小值
     * @param floatMax 浮动难度最大值
     * @param invasionMaxWaves 入侵事件最大波次数
     */
    public WorldStageDataConfig(int stage, double multiplier,
                                 double maxHealthMultiplier, double maxDamageMultiplier,
                                 double maxArmorMultiplier, double floatMin, double floatMax,
                                 int invasionMaxWaves) {
        this.stage = stage;
        this.multiplier = multiplier;
        this.maxHealthMultiplier = maxHealthMultiplier;
        this.maxDamageMultiplier = maxDamageMultiplier;
        this.maxArmorMultiplier = maxArmorMultiplier;
        this.floatMin = floatMin;
        this.floatMax = floatMax;
        this.invasionMaxWaves = invasionMaxWaves;
    }

    public int getStage() {
        return stage;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public double getMaxHealthMultiplier() {
        return maxHealthMultiplier;
    }

    public double getMaxDamageMultiplier() {
        return maxDamageMultiplier;
    }

    public double getMaxArmorMultiplier() {
        return maxArmorMultiplier;
    }

    public double getFloatMin() {
        return floatMin;
    }

    public double getFloatMax() {
        return floatMax;
    }

    public int getInvasionMaxWaves() {
        return invasionMaxWaves;
    }

    /**
     * 判断是否有有效倍率
     *
     * @return 倍率大于 0 返回 true
     */
    public boolean hasMultiplier() {
        return multiplier > 0.0;
    }

    /**
     * 判断是否有有效血量上限
     *
     * @return 上限大于 0 返回 true
     */
    public boolean hasMaxHealthMultiplier() {
        return maxHealthMultiplier > 0.0;
    }

    /**
     * 判断是否有有效伤害上限
     *
     * @return 上限大于 0 返回 true
     */
    public boolean hasMaxDamageMultiplier() {
        return maxDamageMultiplier > 0.0;
    }

    /**
     * 判断是否有有效护甲上限
     *
     * @return 上限大于 0 返回 true
     */
    public boolean hasMaxArmorMultiplier() {
        return maxArmorMultiplier > 0.0;
    }

    /**
     * 判断是否有有效浮动范围
     *
     * @return 最小值和最大值都大于 0 返回 true
     */
    public boolean hasFloatRange() {
        return floatMin > 0.0 && floatMax > 0.0;
    }

    /**
     * 判断是否有有效入侵波次
     *
     * @return 波次数大于 0 返回 true
     */
    public boolean hasInvasionMaxWaves() {
        return invasionMaxWaves > 0;
    }
}
