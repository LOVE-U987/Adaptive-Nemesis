package com.adaptive_nemesis.adaptive_nemesismod.invasion;

/**
 * 入侵系统修复期新增配置段
 *
 * 说明：config/ 包由其他修复 Worker 并行占用，为避免文件冲突，本批新增配置项
 * 集中定义于此（常量形式，默认值即计划规定的默认值）；后续如需进入 TOML 配置，
 * 可整体平移至 config/InvasionConfig 而不改语义。
 *
 * 同时承载波次表、难度表迁移后的默认值（F-13）与倍率/波数钳制等纯逻辑工具，
 * 这些静态方法不依赖 Minecraft 运行时，供单元测试直接验证（T-04）。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public final class InvasionFixConfig {

    /** 入侵失败或终止时是否清理由 mobsLeft 追踪的存活实体（F-08，默认开启） */
    public static final boolean CLEAR_MOBS_ON_FAILURE = true;

    /** 目标玩家连续离线多久（tick）后判入侵失败并走失败清理路径（F-11，默认 6000 tick = 5 分钟） */
    public static final int OFFLINE_ABORT_TICKS = 6000;

    /** 入侵系统消息的通知半径（方块）（F-44；原实现通知与生成区域共用半径 70，按修复计划拆分为独立配置项） */
    public static final double NOTIFICATION_RADIUS = 32.0;

    /** 入侵难度倍率下限（F-14，防止 0/负数倍率产生 0 血或非法属性实体） */
    public static final double MIN_DIFFICULTY_MULTIPLIER = 0.1;

    /**
     * 私有构造函数，禁止实例化工具类
     */
    private InvasionFixConfig() {
    }

    /**
     * 获取指定世界阶段的默认入侵波次数
     * 迁移自 calculateWaveCount 的硬编码表（F-13），数据包可覆盖
     *
     * @param stage 世界阶段编号（0 起，越界回退到基础值）
     * @return 默认波次数
     */
    public static int stageWaveCount(int stage) {
        if (stage <= 0) {
            return 3;
        }
        if (stage == 1) {
            return 5;
        }
        return 6;
    }

    /**
     * 获取指定世界阶段的默认难度倍率
     * 迁移自 getDifficultyMultiplier 的硬编码表（F-13），数据包可覆盖
     *
     * @param stage 世界阶段编号（0 起，越界取最高档）
     * @return 默认难度倍率
     */
    public static double stageDifficultyMultiplier(int stage) {
        return switch (stage) {
            case 0 -> 1.0;   // Normal Mode
            case 1 -> 1.5;   // Expert Mode（进入下界）
            case 2 -> 2.0;   // Master Mode（击败末影龙）
            default -> 2.5;  // 更高阶段
        };
    }

    /**
     * 钳制难度倍率下限（F-14）
     * 低于下限时取下限，防止 0/负数倍率产生 0 血或非法属性实体
     *
     * @param multiplier 原始难度倍率
     * @return 钳制后的倍率（不低于 {@link #MIN_DIFFICULTY_MULTIPLIER}）
     */
    public static double clampMultiplier(double multiplier) {
        return Math.max(MIN_DIFFICULTY_MULTIPLIER, multiplier);
    }

    /**
     * 计算移动速度增强系数（F-14 速度项同步钳制）
     * 原实现为 0.8 + 倍率 * 0.2，倍率经下限钳制后速度系数不低于 0.82
     *
     * @param multiplier 原始难度倍率
     * @return 速度增强系数
     */
    public static double speedEnhancementFactor(double multiplier) {
        return 0.8 + clampMultiplier(multiplier) * 0.2;
    }

    /**
     * 钳制入侵波次数量
     * 下限 1；上限取配置上限，配置上限非法（小于 1）时按 1 处理
     *
     * @param maxWaves 数据包或默认表给出的波次数
     * @param configLimit 配置中的波次上限
     * @return 钳制后的波次数
     */
    public static int clampWaveCount(int maxWaves, int configLimit) {
        int upper = Math.max(1, configLimit);
        return Math.max(1, Math.min(maxWaves, upper));
    }
}
