package com.adaptive_nemesis.adaptive_nemesismod.enemy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EnchantmentScalingHandler 核心计算逻辑的单元测试
 *
 * 验证附魔概率和等级计算在各种难度倍率下是否正确。
 * 测试的均为无依赖的静态方法，无需 Minecraft 运行时环境。
 */
@DisplayName("附魔缩放计算测试")
class EnchantmentScalingHandlerTest {

    @Nested
    @DisplayName("calculateEnchantChance() - 附魔概率计算")
    class EnchantChanceTests {

        @ParameterizedTest(name = "难度{0}倍, 基础{1}%, 每单位{2}% → 预期{3}")
        @CsvSource({
            // difficultyMultiplier, baseChance, chancePerDifficulty, expected
            "1.0,  0.20,  0.05,  0.20",   // 基础倍率，无增量
            "2.0,  0.20,  0.05,  0.25",   // +1倍率，+5%
            "3.0,  0.20,  0.05,  0.30",   // +2倍率，+10%
            "5.0,  0.20,  0.05,  0.40",   // +4倍率，+20%
            "10.0, 0.20,  0.05,  0.65",   // +9倍率，+45%
            "0.5,  0.20,  0.05,  0.175",  // 负增量 (-0.5 * 0.05 = -0.025)
            "1.0,  0.00,  0.10,  0.00",   // 基础概率0
            "1.0,  1.00,  0.00,  0.95",   // 上限测试
        })
        @DisplayName("基础概率计算")
        void testBasicEnchantChance(double difficulty, float base, float perDifficulty, float expected) {
            float result = EnchantmentScalingHandler.calculateEnchantChance(difficulty, base, perDifficulty);
            assertEquals(expected, result, 0.001, "概率应与预期一致");
        }

        @Test
        @DisplayName("概率上限 0.95")
        void testChanceCapAt095() {
            // 极高难度下概率保持在0.95
            float result = EnchantmentScalingHandler.calculateEnchantChance(100.0, 0.5f, 0.1f);
            assertEquals(0.95f, result, 0.001, "概率不能超过0.95");
        }

        @Test
        @DisplayName("极高难度下仍然受上限约束")
        void testChanceCapUnderExtremeDifficulty() {
            float result = EnchantmentScalingHandler.calculateEnchantChance(
                Double.MAX_VALUE, 0.5f, 0.5f
            );
            assertEquals(0.95f, result, 0.001, "极端难度下概率仍被上限约束");
        }

        @Test
        @DisplayName("概率不低于0")
        void testChanceNotNegative() {
            float result = EnchantmentScalingHandler.calculateEnchantChance(0.0, 0.1f, 0.2f);
            assertTrue(result >= 0, "概率不应为负数");
        }
    }

    @Nested
    @DisplayName("calculateEnchantLevel() - 附魔等级计算")
    class EnchantLevelTests {

        @ParameterizedTest(name = "难度{0}倍, 每单位{1}级, 上限{2} → {3}级")
        @CsvSource({
            // difficultyMultiplier, levelPerDifficulty, maxLevel, expected
            "1.0,  1.0,  5,  1",    // 基础倍率，最低1级
            "2.0,  1.0,  5,  2",    // +1倍率，+1级
            "3.0,  1.0,  5,  3",    // +2倍率，+2级
            "4.0,  1.0,  5,  4",    // +3倍率，+3级
            "5.0,  1.0,  5,  5",    // +4倍率，+4级 = 上限
            "6.0,  1.0,  5,  5",    // +5倍率 → 上限约束为5
            "10.0, 1.0,  5,  5",    // +9倍率 → 上限约束
            "3.5,  1.0,  5,  3",    // floor((3.5-1)*1) = floor(2.5) = 2, +1 = 3
            "1.0,  2.0,  10, 1",    // 基础倍率，最低1级
            "3.0,  2.0,  10, 5",    // (3-1)*2 = 4, +1 = 5
            "3.0,  2.0,  3,  3",    // 上限约束
        })
        @DisplayName("等级计算")
        void testEnchantLevel(double difficulty, double levelPer, int maxLevel, int expected) {
            int result = EnchantmentScalingHandler.calculateEnchantLevel(difficulty, levelPer, maxLevel);
            assertEquals(expected, result, "等级应与预期一致");
        }

        @Test
        @DisplayName("最低1级")
        void testMinLevelIs1() {
            int result = EnchantmentScalingHandler.calculateEnchantLevel(0.5, 1.0, 5);
            assertTrue(result >= 1, "附魔等级最低为1级");
        }

        @Test
        @DisplayName("不能超过上限")
        void testLevelCappedByMax() {
            // 极高难度下等级被上限约束
            int result = EnchantmentScalingHandler.calculateEnchantLevel(100.0, 5.0, 3);
            assertEquals(3, result, "等级不能超过上限");
        }

        @Test
        @DisplayName("零增量时固定1级")
        void testZeroIncrement() {
            int result = EnchantmentScalingHandler.calculateEnchantLevel(10.0, 0.0, 5);
            assertEquals(1, result, "无增量时保持1级");
        }
    }

    @Nested
    @DisplayName("shouldGrantEquipmentChance() - 装备生成概率计算")
    class EquipmentChanceTests {

        @Test
        @DisplayName("基础概率 - 主手")
        void testBaseChanceMainhand() {
            float chance = EnchantmentScalingHandler.shouldGrantEquipmentChance(1.0, true, 0.15f, 0.10f);
            assertEquals(0.30f, chance, 0.001, "主手基础概率应为30%");
        }

        @Test
        @DisplayName("基础概率 - 非主手")
        void testBaseChanceNonMainhand() {
            float chance = EnchantmentScalingHandler.shouldGrantEquipmentChance(1.0, false, 0.15f, 0.10f);
            assertEquals(0.15f, chance, 0.001, "非主手基础概率应为15%");
        }

        @ParameterizedTest(name = "难度{0}倍, 主手={1} → {2}")
        @CsvSource({
            "1.0,  true,  0.30",
            "1.0,  false, 0.15",
            "3.0,  true,  0.70",  // (0.15 + 2*0.10) * 2 = 0.70
            "3.0,  false, 0.35",  // 0.15 + 2*0.10 = 0.35
            "5.0,  true,  1.00",  // (0.15 + 4*0.10) * 2 = 1.10, cap=1.0
            "5.0,  false, 0.55",  // 0.15 + 4*0.10 = 0.55
        })
        @DisplayName("概率随难度增加")
        void testEquipmentChanceScales(double difficulty, boolean mainhand, float expected) {
            assertEquals(expected,
                EnchantmentScalingHandler.shouldGrantEquipmentChance(difficulty, mainhand, 0.15f, 0.10f),
                0.001,
                "装备生成概率应随难度增加"
            );
        }

        @Test
        @DisplayName("概率上限1.0")
        void testCapAtOne() {
            float chance = EnchantmentScalingHandler.shouldGrantEquipmentChance(100.0, true, 0.15f, 0.10f);
            assertEquals(1.0f, chance, 0.001, "装备生成概率不能超过1.0");
        }
    }

    @Nested
    @DisplayName("isDangerousEnchantmentKey() - 危险附魔检测")
    class DangerousEnchantmentTests {

        @ParameterizedTest(name = "「{0}」→ 危险({1})")
        @CsvSource({
            // 英文关键词
            "damage_immunity,      true",
            "immune_to_all,        true",
            "invulnerable,         true",
            "invincible,           true",
            "immunity,             true",
            "no_damage,            true",
            "god,                  true",
            "divine_protection,    true",
            "damage_proof,         true",
            // 模拟那个出问题的模组附魔注册名
            "irons_spellbooks:immune,  true",
            "somanyenchants:invulnerable, true",
            "apotheosis:immune,        true",
            "extraenchants:god_armor,  true",
            // 中文关键词
            "免伤,                 true",
            "无敌,                 true",
            "免疫,                 true",
            "伤害免疫,             true",
            // 正常附魔（不被误杀）
            "power,                false",
            "sharpness,            false",
            "protection,           false",
            "fire_aspect,          false",
            "minecraft:power,      false",
            "minecraft:sharpness,  false",
        })
        @DisplayName("已知关键词检测")
        void testKnownKeywords(String key, boolean expected) {
            assertEquals(expected,
                EnchantmentScalingHandler.isDangerousEnchantmentKey(key),
                "关键词 '" + key + "' 的危险性判断错误"
            );
        }

        @Test
        @DisplayName("普通原版附魔不受影响")
        void testVanillaEnchantmentsAreSafe() {
            assertAll(
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("minecraft:power")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("minecraft:sharpness")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("minecraft:protection")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("minecraft:fire_protection")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("minecraft:feather_falling")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("minecraft:thorns")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("minecraft:unbreaking")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("minecraft:mending")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("minecraft:respiration")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("minecraft:aqua_affinity"))
            );
        }

        @Test
        @DisplayName("普通模组附魔不受影响")
        void testModEnchantmentsAreSafe() {
            assertAll(
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("irons_spellbooks:lightning_bolt")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("epicfight:damage")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("apotheosis:berserk")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("soulsweapons:dark_estus")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("spell_power:soulfrost")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("spell_engine:spell_power"))
            );
        }

        @Test
        @DisplayName("大小写不影响检测")
        void testCaseInsensitive() {
            assertAll(
                () -> assertTrue(EnchantmentScalingHandler.isDangerousEnchantmentKey("DAMAGE_IMMUNITY")),
                () -> assertTrue(EnchantmentScalingHandler.isDangerousEnchantmentKey("Invulnerable")),
                () -> assertTrue(EnchantmentScalingHandler.isDangerousEnchantmentKey("免疫一切伤害")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("PROTECTION"))
            );
        }

        @Test
        @DisplayName("边界情况：null/空字符串")
        void testEdgeCases() {
            assertAll(
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey(null)),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("")),
                () -> assertFalse(EnchantmentScalingHandler.isDangerousEnchantmentKey("   "))
            );
        }

        @ParameterizedTest(name = "敌人的伤害免疫附魔「{0}」→ 被拦截({1})")
        @CsvSource({
            // 模拟用户遇到的那个"伤害免疫"附魔的各种可能ID
            "somanyenchants:immune,                    true",
            "somanyenchants:damage_immunity,            true",
            "apotheosis:immune,                         true",
            "enchdesc:damage_immunity,                  true",
            "extrautils:invulnerable,                    true",
            "spartan:god,                               true",
        })
        @DisplayName("模拟用户报告的伤害免疫附魔场景")
        void testReportedDamageImmunityScenarios(String enchantId, boolean expected) {
            assertEquals(expected,
                EnchantmentScalingHandler.isDangerousEnchantmentKey(enchantId),
                "敌人伤害免疫附魔 '" + enchantId + "' 应被拦截"
            );
        }
    }
}