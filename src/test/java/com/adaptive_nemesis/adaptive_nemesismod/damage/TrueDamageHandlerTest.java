package com.adaptive_nemesis.adaptive_nemesismod.damage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TrueDamageHandler 真实伤害比例计算测试
 *
 * 验证分段阈值与线性插值逻辑是否正确，无需 Minecraft 运行时环境。
 */
@DisplayName("真实伤害比例计算测试")
class TrueDamageHandlerTest {

    /**
     * 测试配置：
     * LOW=20 -> 5%
     * MEDIUM=50 -> 15%
     * HIGH=100 -> 25%
     * TURTLE -> 35%
     */
    private static final double LOW_THRESHOLD = 20.0;
    private static final double LOW_PERCENT = 5.0;
    private static final double MEDIUM_THRESHOLD = 50.0;
    private static final double MEDIUM_PERCENT = 15.0;
    private static final double HIGH_THRESHOLD = 100.0;
    private static final double HIGH_PERCENT = 25.0;
    private static final double TURTLE_PERCENT = 35.0;

    @ParameterizedTest(name = "护甲{0} → 真实伤害比例 {1}%")
    @CsvSource({
        // 低护甲区
        "0,   5.0",
        "10,  5.0",
        "20,  5.0",
        // 低→中区插值 (20,5) -> (50,15)
        "35,  10.0",
        "50,  15.0",
        // 中→高区插值 (50,15) -> (100,25)
        "75,  20.0",
        "100, 25.0",
        // 高护甲区
        "150, 35.0",
        "999, 35.0"
    })
    @DisplayName("分段阈值计算")
    void testCalculateTrueDamagePercent(double armorValue, double expected) {
        double result = TrueDamageHandler.calculateTrueDamagePercent(
            armorValue,
            LOW_THRESHOLD, LOW_PERCENT,
            MEDIUM_THRESHOLD, MEDIUM_PERCENT,
            HIGH_THRESHOLD, HIGH_PERCENT,
            TURTLE_PERCENT
        );
        assertEquals(expected, result, 0.001, "护甲 " + armorValue + " 的真实伤害比例错误");
    }
}
