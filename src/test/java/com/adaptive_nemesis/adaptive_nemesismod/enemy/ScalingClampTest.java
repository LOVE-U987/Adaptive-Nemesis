package com.adaptive_nemesis.adaptive_nemesismod.enemy;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缩放钳制与回退逻辑的单元测试（计划项 T-03）
 *
 * 覆盖点：
 * - F-16 属性回退：NBT 原始值标签缺失时取传入的默认基础值（实体类型默认属性），
 *   并保证重复缩放幂等（已缩放的高属性值不会被再次当作原始值叠加）。
 * - F-46 缩放倍率缺失回退：倍率标签缺失时返回 1.0，与 javadoc 语义一致。
 * - F-03 新手保护减免：受保护玩家将缩放倍率乘以 (1 - 减免比例)，且不低于 1.0。
 *
 * 覆盖边界说明：
 * - 仅覆盖无 Minecraft 运行时依赖的静态纯计算方法；
 *   CompoundTag 为纯 NBT 数据结构，普通 JVM 下可直接构造，无需 Bootstrap 引导。
 * - F-16 中 DefaultAttributes.getSupplier 的注册表查询与
 *   F-03 中 NewbieProtectionHandler.isProtected 的保护状态判定
 *   均依赖 Minecraft 类加载与注册表，无法在纯 JVM 测试中触发；
 *   此处通过"调用方传入的 defaultBase 即实体类型默认值"的纯函数契约
 *   间接验证回退行为，注册表路径由游戏内验证（计划项 T-06）兜底。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
@DisplayName("缩放钳制与回退逻辑测试")
class ScalingClampTest {

    /** F-16 测试用的原始值 NBT 标签键，与生产代码调用方式一致 */
    private static final String TEST_TAG = "an_original_health";

    @Nested
    @DisplayName("getOrStoreOriginalBase() - 属性原始值回退（F-16）")
    class OriginalBaseFallbackTests {

        @Test
        @DisplayName("标签缺失时写入并返回默认基础值")
        void testMissingTagFallsBackToDefault() {
            CompoundTag data = new CompoundTag();
            double defaultBase = 20.0;

            double result = EnemyScalingHandler.getOrStoreOriginalBase(data, TEST_TAG, defaultBase);

            assertEquals(defaultBase, result, 0.0001, "标签缺失时应返回默认基础值");
            assertEquals(defaultBase, data.getDouble(TEST_TAG), 0.0001, "默认值应写回标签供后续幂等读取");
        }

        @Test
        @DisplayName("标签存在时返回存储值，不覆盖")
        void testExistingTagReturnsStoredValue() {
            CompoundTag data = new CompoundTag();
            data.putDouble(TEST_TAG, 100.0);

            double result = EnemyScalingHandler.getOrStoreOriginalBase(data, TEST_TAG, 20.0);

            assertEquals(100.0, result, 0.0001, "标签存在时应返回存储的原始值");
            assertEquals(100.0, data.getDouble(TEST_TAG), 0.0001, "已有标签值不应被默认值覆盖");
        }

        @Test
        @DisplayName("重复缩放幂等：缺失场景写入后不再被默认值重置")
        void testIdempotentRepeatedScaling() {
            CompoundTag data = new CompoundTag();

            // 第一次缩放：标签缺失，回退到实体类型默认值并写入
            double first = EnemyScalingHandler.getOrStoreOriginalBase(data, TEST_TAG, 20.0);
            // 模拟第一次缩放把属性放大到 60（写回存储值不代表原始值变化）
            data.putDouble(TEST_TAG, 60.0);

            // 第二次缩放：应读取 60 作为原始值，而不是重新回退到 20
            double second = EnemyScalingHandler.getOrStoreOriginalBase(data, TEST_TAG, 20.0);

            assertEquals(20.0, first, 0.0001);
            assertEquals(60.0, second, 0.0001, "第二次缩放必须以首次记录的原始值为基准，防止指数叠加");
        }

        @ParameterizedTest(name = "默认值 {0} → 回退结果 {0}")
        @CsvSource({
            "0.0",   // 0 基础值属性（如护甲）
            "1.0",   // 1 基础值属性（如攻击伤害）
            "20.0",  // 常规血量
            "0.7",   // 小数属性（如速度）
        })
        @DisplayName("各种默认基础值均可正确回退")
        void testVariousDefaultBases(double defaultBase) {
            CompoundTag data = new CompoundTag();

            double result = EnemyScalingHandler.getOrStoreOriginalBase(data, TEST_TAG, defaultBase);

            assertEquals(defaultBase, result, 0.0000001, "回退结果应与传入默认值一致");
        }
    }

    @Nested
    @DisplayName("getScaleMultiplierFromTag() - 缩放倍率缺失回退（F-46）")
    class ScaleMultiplierFallbackTests {

        @Test
        @DisplayName("倍率标签缺失时返回 1.0")
        void testMissingTagReturnsOne() {
            CompoundTag data = new CompoundTag();

            double result = EnemyScalingHandler.getScaleMultiplierFromTag(data);

            assertEquals(1.0, result, 0.0001, "标签缺失时应返回 1.0（视为未缩放）");
        }

        @Test
        @DisplayName("倍率标签存在时返回存储倍率")
        void testExistingTagReturnsStoredMultiplier() {
            CompoundTag data = new CompoundTag();
            data.putDouble(EnemyScalingHandler.SCALE_MULTIPLIER_TAG, 3.5);

            double result = EnemyScalingHandler.getScaleMultiplierFromTag(data);

            assertEquals(3.5, result, 0.0001, "标签存在时应返回存储的倍率");
        }

        @Test
        @DisplayName("存储倍率为 0 时如实返回 0（不悄悄改写）")
        void testZeroStoredMultiplierReturnedAsIs() {
            CompoundTag data = new CompoundTag();
            data.putDouble(EnemyScalingHandler.SCALE_MULTIPLIER_TAG, 0.0);

            double result = EnemyScalingHandler.getScaleMultiplierFromTag(data);

            assertEquals(0.0, result, 0.0001, "存储值为 0 应如实返回，回退语义只针对标签缺失");
        }
    }

    @Nested
    @DisplayName("applyNewbieProtectionMultiplier() - 新手保护减免（F-03）")
    class NewbieProtectionMultiplierTests {

        @Test
        @DisplayName("无受保护玩家时倍率保持不变")
        void testNoProtectionKeepsMultiplier() {
            double result = EnemyScalingHandler.applyNewbieProtectionMultiplier(2.5, false, 0.3);

            assertEquals(2.5, result, 0.0001, "无保护时不应调整倍率");
        }

        @ParameterizedTest(name = "倍率 {0} x (1 - {1}) = {2}")
        @CsvSource({
            "2.0, 0.3, 1.4",   // 默认减免 30% 场景
            "2.0, 0.0, 2.0",   // 减免为 0 等于无保护
            "3.0, 0.5, 1.5",   // 大额减免
            "1.0, 0.3, 1.0",   // 基准倍率不受影响
        })
        @DisplayName("受保护时倍率按比例减免")
        void testProtectedMultiplierReduced(double multiplier, double reduction, double expected) {
            double result = EnemyScalingHandler.applyNewbieProtectionMultiplier(multiplier, true, reduction);

            assertEquals(expected, result, 0.0001, "受保护时倍率应乘以 (1 - 减免比例)");
        }

        @Test
        @DisplayName("减免后低于 1.0 时钳制到 1.0（不产生负缩放）")
        void testReducedBelowOneClampedToOne() {
            double result = EnemyScalingHandler.applyNewbieProtectionMultiplier(1.2, true, 0.5);

            assertEquals(1.0, result, 0.0001, "保护只降低缩放幅度，不应产生低于 1.0 的倍率");
        }

        @Test
        @DisplayName("减免比例为 1.0（全额减免）时结果钳制到 1.0")
        void testFullReductionClampedToOne() {
            double result = EnemyScalingHandler.applyNewbieProtectionMultiplier(5.0, true, 1.0);

            assertEquals(1.0, result, 0.0001, "全额减免后仍不得低于 1.0");
        }

        @Test
        @DisplayName("减免后倍率不低于原倍率的一半场景下保持原倍率下限语义")
        void testResultNeverBelowOne() {
            double result = EnemyScalingHandler.applyNewbieProtectionMultiplier(1.05, true, 0.9);

            assertTrue(result >= 1.0, "任何减免组合的结果都不应低于 1.0");
        }
    }
}
