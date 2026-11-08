package com.adaptive_nemesis.adaptive_nemesismod.invasion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 入侵数据加载容错测试（T-04）
 *
 * 验证入侵数据包加载与难度倍率相关的容错逻辑：
 * - F-14：难度倍率下限钳制（0、负数、极大值）与速度增强系数钳制
 * - F-38：药水效果缺 id 时安全跳过（不抛 NPE）
 * - F-12：数据包 max_waves 的钳制
 * - T-04：坏 JSON 单文件失败不影响其他文件的解析隔离
 *
 * 测试的均为无 Minecraft 运行时依赖的纯逻辑静态方法。
 */
@DisplayName("入侵数据加载容错测试")
class InvasionDataLoaderToleranceTest {

    @Nested
    @DisplayName("clampMultiplier() - F-14 倍率下限钳制")
    class ClampMultiplierTests {

        @ParameterizedTest(name = "原始倍率 {0} → 钳制后 {1}")
        @CsvSource({
            // 正常值原样通过
            "0.1,   0.1",
            "1.0,   1.0",
            "2.5,   2.5",
            "0.15,  0.15",
            // 边界与非法值取下限
            "0.0,   0.1",
            "-1.0,  0.1",
            "-100.0, 0.1"
        })
        @DisplayName("倍率下限钳制")
        void testClampMultiplier(double input, double expected) {
            assertEquals(expected, InvasionFixConfig.clampMultiplier(input), 0.0001,
                "倍率 " + input + " 的钳制结果错误");
        }

        @Test
        @DisplayName("极大值保留（仅做下限钳制，不上限截断）")
        void testHugeMultiplierPreserved() {
            double huge = 1.0e9;
            assertEquals(huge, InvasionFixConfig.clampMultiplier(huge), 1.0,
                "极大值倍率不应被截断");
        }

        @Test
        @DisplayName("NaN 输入返回 NaN（不放大为非法属性实体）")
        void testNaNPropagation() {
            // NaN 参与 Math.max 比较恒为 false，结果保持 NaN；
            // 属性设置侧 setBaseValue(NaN) 会抛异常而非生成 0 血实体，属可接受行为
            double result = InvasionFixConfig.clampMultiplier(Double.NaN);
            assertTrue(Double.isNaN(result), "NaN 输入应保持 NaN 语义");
        }

        @ParameterizedTest(name = "倍率 {0} → 速度增强系数 {1}")
        @CsvSource({
            // 0.8 + 钳制后倍率 * 0.2
            "0.0,  0.82",
            "-5.0, 0.82",
            "0.1,  0.82",
            "1.0,  1.0",
            "2.0,  1.2",
            "5.0,  1.8"
        })
        @DisplayName("速度增强系数同步钳制")
        void testSpeedEnhancementFactor(double multiplier, double expected) {
            assertEquals(expected, InvasionFixConfig.speedEnhancementFactor(multiplier), 0.0001,
                "倍率 " + multiplier + " 的速度增强系数错误");
        }
    }

    @Nested
    @DisplayName("clampWaveCount() - max_waves 钳制")
    class ClampWaveCountTests {

        @ParameterizedTest(name = "数据包波数 {0}，配置上限 {1} → {2}")
        @CsvSource({
            // 正常值
            "3,  6,  3",
            "6,  6,  6",
            "1,  6,  1",
            // 数据包 0/负数 → 下限 1
            "0,  6,  1",
            "-3, 6,  1",
            // 超过配置上限 → 取上限
            "50, 6,  6",
            "7,  6,  6",
            // 配置上限非法时按 1 处理
            "3,  0,  1",
            "3,  -1, 1"
        })
        @DisplayName("波次数上下限钳制")
        void testClampWaveCount(int maxWaves, int configLimit, int expected) {
            assertEquals(expected, InvasionFixConfig.clampWaveCount(maxWaves, configLimit),
                "波数钳制结果错误");
        }

        @Test
        @DisplayName("极大波数值受配置上限约束")
        void testHugeWaveCountClampedByLimit() {
            assertEquals(20, InvasionFixConfig.clampWaveCount(Integer.MAX_VALUE, 20),
                "极大波数值应被配置上限截断");
        }
    }

    @Nested
    @DisplayName("isValidEffectId() - F-38 药水效果缺 id 容错")
    class EffectIdValidationTests {

        @ParameterizedTest(name = "非法 id 用例 {index}")
        @NullAndEmptySource
        @DisplayName("缺 id 或空 id 判定不可用")
        void testInvalidEffectId(String effectId) {
            assertFalse(InvasionRewardData.isValidEffectId(effectId),
                "缺 id / 空 id 应判定为不可用");
        }

        @ParameterizedTest(name = "合法 id: \"{0}\"")
        @ValueSource(strings = {"minecraft:speed", "minecraft:fire_resistance", "x"})
        @DisplayName("合法 id 判定可用")
        void testValidEffectId(String effectId) {
            assertTrue(InvasionRewardData.isValidEffectId(effectId),
                "非空 id 应判定为可用");
        }

        @Test
        @DisplayName("parseEffect 缺 id 时返回 null 且不抛 NPE")
        void testParseEffectNullIdNoNpe() {
            // 修复前：ResourceLocation.tryParse(null) 会抛 NPE 拖垮整份配置
            assertEquals(null, InvasionRewardData.parseEffect(null, 30, 0),
                "缺 id 应返回 null 而非抛出 NPE");
        }
    }

    @Nested
    @DisplayName("parseEachSafely() - 坏 JSON 单文件隔离")
    class ParseEachSafelyTests {

        /**
         * 构造一个 JSON 包装元素
         *
         * @param key 键
         * @param value 值
         * @return 只含单键的 JSON 对象元素
         */
        private JsonElement singleEntry(String key, String value) {
            JsonObject object = new JsonObject();
            object.add(key, new JsonPrimitive(value));
            return object;
        }

        @Test
        @DisplayName("坏条目抛异常时其余条目正常解析")
        void testBadEntryDoesNotAffectOthers() {
            Map<String, JsonElement> input = new LinkedHashMap<>();
            input.put("a:good", singleEntry("max_waves", "3"));
            input.put("b:bad", new JsonPrimitive("not-an-object")); // getAsJsonObject 抛异常
            input.put("c:good2", singleEntry("name", "demo"));

            Map<String, JsonObject> parsed = InvasionDataLoader.parseEachSafely(
                input, (id, json) -> json);

            assertEquals(2, parsed.size(), "坏条目失败不应影响其他条目");
            assertTrue(parsed.containsKey("a:good"), "好条目 a:good 应被保留");
            assertTrue(parsed.containsKey("c:good2"), "好条目 c:good2 应被保留");
            assertFalse(parsed.containsKey("b:bad"), "坏条目 b:bad 应被跳过");
        }

        @Test
        @DisplayName("全部条目失败时返回空映射且不抛异常")
        void testAllEntriesFailedReturnsEmpty() {
            Map<String, JsonElement> input = new LinkedHashMap<>();
            input.put("a:bad", new JsonPrimitive("x"));
            input.put("b:bad", null);

            Map<String, JsonObject> parsed = InvasionDataLoader.parseEachSafely(
                input, (id, json) -> json);

            assertTrue(parsed.isEmpty(), "全部失败时应返回空映射");
        }

        @Test
        @DisplayName("解析结果为 null 的条目被跳过")
        void testNullResultSkipped() {
            Map<String, JsonElement> input = new LinkedHashMap<>();
            input.put("a:null", singleEntry("k", "v"));
            input.put("b:ok", singleEntry("k", "v"));

            Map<String, JsonObject> parsed = InvasionDataLoader.parseEachSafely(
                input, (id, json) -> id.equals("a:null") ? null : json);

            assertEquals(1, parsed.size(), "解析为 null 的条目不应进入结果");
            assertTrue(parsed.containsKey("b:ok"), "正常条目应被保留");
        }
    }
}
