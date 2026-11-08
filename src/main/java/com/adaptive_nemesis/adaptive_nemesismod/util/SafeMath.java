package com.adaptive_nemesis.adaptive_nemesismod.util;

/**
 * 公共安全数值工具
 *
 * 统一原先分散在 compat 包内、默认值互相矛盾的 NaN/Infinity 防护实现
 * （IronsSpellsCompat 回退 0.0，EpicFightCompat 回退 1.0），
 * 保证倍率语境下的失效语义一致：回退到中性倍率 1.0，即"不加成也不清零"。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public final class SafeMath {

    /**
     * 倍率语境下的安全回退值：1.0 为乘算中性值，
     * NaN/Infinity 传入时用它替代可避免属性被清零或爆表
     */
    public static final double MULTIPLIER_FALLBACK = 1.0;

    /**
     * 私有构造函数 - 工具类禁止实例化
     */
    private SafeMath() {}

    /**
     * 安全获取双精度浮点数（倍率语境）- 防止 NaN/Infinity 传播
     *
     * @param value 待检查的值
     * @return 有效值原样返回；NaN/Infinity 返回 1.0（倍率中性值）
     */
    public static double safeDouble(double value) {
        return safeDouble(value, MULTIPLIER_FALLBACK);
    }

    /**
     * 安全获取双精度浮点数 - 防止 NaN/Infinity 传播
     *
     * @param value 待检查的值
     * @param fallback 值非法（NaN/Infinity）时使用的回退值
     * @return 有效值原样返回；NaN/Infinity 返回 fallback
     */
    public static double safeDouble(double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return fallback;
        }
        return value;
    }
}
