package com.adaptive_nemesis.adaptive_nemesismod.damage;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TrueDamageHandler 真实伤害机制测试
 *
 * 验证三项内容，均无需 Minecraft bootstrap 环境：
 * 1. 真实伤害比例计算：分段阈值与线性插值逻辑是否正确；
 * 2. 初始化门槛与再入排除：通过静态状态与 DamageSource 键匹配判定，
 *    DamageSource 使用未绑定注册表的 Holder.Reference 承载类型键，
 *    仅依赖 is(ResourceKey) 的键比较路径，不触碰注册表引导；
 * 3. 钳制用例：分段结果的边界行为与区间单调性。
 *
 * 执行顺序说明：initialized 为单向静态标志（只能置 true 无法复位），
 * 本类是测试源集中唯一操控该标志的位置，初始化门槛用例通过
 * @Order 保证在 markInitialized 用例之前运行。
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
@DisplayName("真实伤害机制测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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

    @Test
    @Order(1)
    @DisplayName("初始化门槛：未初始化时 isInitialized 返回 false")
    void testNotInitializedBeforeMark() {
        // 本用例必须先于 markInitialized 用例执行（@Order 保证），
        // 验证静态初始化门槛的默认关闭状态，未初始化时事件处理入口直接返回
        assertFalse(TrueDamageHandler.isInitialized(), "配置加载前初始化标志应为 false");
    }

    @Test
    @Order(2)
    @DisplayName("初始化门槛：markInitialized 后 isInitialized 返回 true")
    void testInitializedAfterMark() {
        // 对应 F-01：主类 onModConfigEvent 的 Loading/Reloading 分支接线后生效
        TrueDamageHandler.markInitialized();
        assertTrue(TrueDamageHandler.isInitialized(), "配置加载接线后初始化标志应为 true");
    }

    @Test
    @DisplayName("再入排除：true_damage 类型源被识别，直接跳过防二次转化")
    void testTrueDamageSourceDetected() {
        // 对应 F-02(b)：延迟任务施加的真实伤害再次进入事件时会命中此判定并 return
        DamageSource trueDamageSource = buildDamageSource(ModDamageTypes.TRUE_DAMAGE);
        assertTrue(TrueDamageHandler.isTrueDamageSource(trueDamageSource),
            "true_damage 类型源应被识别为真实伤害源");
    }

    @Test
    @DisplayName("再入排除：非 true_damage 类型源不误判")
    void testVanillaSourceNotTrueDamage() {
        // 以原版 arrow 类型键构造同构伤害源，键不同则必须返回 false
        ResourceKey<DamageType> arrowKey = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.withDefaultNamespace("arrow")
        );
        DamageSource arrowSource = buildDamageSource(arrowKey);
        assertFalse(TrueDamageHandler.isTrueDamageSource(arrowSource),
            "arrow 类型源不应被识别为真实伤害源");
    }

    @Test
    @DisplayName("再入排除：null 源安全返回 false")
    void testNullSourceNotTrueDamage() {
        assertFalse(TrueDamageHandler.isTrueDamageSource(null),
            "null 源不应被识别为真实伤害源");
    }

    @Test
    @DisplayName("钳制：负护甲按最低护甲区钳制到低区比例")
    void testNegativeArmorClampedToLowPercent() {
        double result = TrueDamageHandler.calculateTrueDamagePercent(
            -5.0,
            LOW_THRESHOLD, LOW_PERCENT,
            MEDIUM_THRESHOLD, MEDIUM_PERCENT,
            HIGH_THRESHOLD, HIGH_PERCENT,
            TURTLE_PERCENT
        );
        assertEquals(LOW_PERCENT, result, 0.001, "负护甲应钳制到低区比例");
    }

    @Test
    @DisplayName("钳制：高阈值之上直接跳变到乌龟比例，不做越界插值")
    void testAboveHighThresholdJumpsToTurtlePercent() {
        double result = TrueDamageHandler.calculateTrueDamagePercent(
            HIGH_THRESHOLD + 0.0001,
            LOW_THRESHOLD, LOW_PERCENT,
            MEDIUM_THRESHOLD, MEDIUM_PERCENT,
            HIGH_THRESHOLD, HIGH_PERCENT,
            TURTLE_PERCENT
        );
        assertEquals(TURTLE_PERCENT, result, 0.001, "超过高阈值应取乌龟比例上限");
    }

    @Test
    @DisplayName("钳制：全护甲区间比例始终落在 [低区比例, 乌龟比例] 且单调不减")
    void testPercentRangeClampedAndMonotonic() {
        double previous = -1.0;
        for (double armor = 0.0; armor <= 300.0; armor += 5.0) {
            double result = TrueDamageHandler.calculateTrueDamagePercent(
                armor,
                LOW_THRESHOLD, LOW_PERCENT,
                MEDIUM_THRESHOLD, MEDIUM_PERCENT,
                HIGH_THRESHOLD, HIGH_PERCENT,
                TURTLE_PERCENT
            );
            assertTrue(result >= LOW_PERCENT - 0.001,
                "护甲 " + armor + " 的比例低于下限钳制值 " + LOW_PERCENT);
            assertTrue(result <= TURTLE_PERCENT + 0.001,
                "护甲 " + armor + " 的比例高于上限钳制值 " + TURTLE_PERCENT);
            assertTrue(result >= previous - 0.001,
                "护甲 " + armor + " 的比例出现非单调回落");
            previous = result;
        }
    }

    /**
     * 构建指定伤害类型的 DamageSource（测试专用）
     *
     * 使用 Holder.Reference.createStandAlone 承载类型键：该 Holder 只绑定键不绑定值，
     * DamageSource.is(ResourceKey) 仅做键引用比较，不会触发注册表查询或 Minecraft bootstrap，
     * 保证测试在纯 JVM 环境可运行。
     *
     * @param typeKey 伤害类型键
     * @return 携带该类型键、无归因实体的伤害源
     */
    private static DamageSource buildDamageSource(ResourceKey<DamageType> typeKey) {
        Holder<DamageType> holder = Holder.Reference.createStandAlone(
            new HolderOwner<DamageType>() {}, typeKey
        );
        return new DamageSource(holder, null, null);
    }
}
