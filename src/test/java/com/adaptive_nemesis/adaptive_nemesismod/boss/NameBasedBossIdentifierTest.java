package com.adaptive_nemesis.adaptive_nemesismod.boss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NameBasedBossIdentifier 关键词匹配测试
 *
 * 验证基于实体注册名的 Boss 识别逻辑，无需构造 Minecraft 实体。
 */
@DisplayName("基于名称的 Boss 识别测试")
class NameBasedBossIdentifierTest {

    private static final Set<String> KEYWORDS = Set.of(
        "boss", "elite", "champion", "cataclysm", "ignis", "ender_dragon"
    );

    @ParameterizedTest(name = "「{0}」→ {1}")
    @CsvSource({
        "cataclysm:ignis,            true",
        "minecraft:ender_dragon,     true",
        "some_mod:elite_zombie,      true",
        "unknown:champion_skeleton,  true",
        "minecraft:zombie,           false",
        "minecraft:skeleton,         false",
        "some_mod:creeper,           false",
    })
    @DisplayName("关键词匹配")
    void testMatchesBossKeywords(String entityKey, boolean expected) {
        assertEquals(expected, NameBasedBossIdentifier.matchesBossKeywords(entityKey, KEYWORDS));
    }

    @Test
    @DisplayName("空输入安全处理")
    void testEdgeCases() {
        assertFalse(NameBasedBossIdentifier.matchesBossKeywords(null, KEYWORDS));
        assertFalse(NameBasedBossIdentifier.matchesBossKeywords("cataclysm:ignis", null));
        assertFalse(NameBasedBossIdentifier.matchesBossKeywords("cataclysm:ignis", Set.of()));
    }

    @ParameterizedTest(name = "「{0}」→ {1}")
    @CsvSource({
        "minecraft:ender_dragon, ender_dragon",
        "minecraft:wither,       wither",
        "minecraft:warden,       warden",
        "cataclysm:ignis,        ignis"
    })
    @DisplayName("Boss 类型推断")
    void testInferBossType(String entityKey, String expected) {
        assertEquals(expected, NameBasedBossIdentifier.inferBossType(entityKey, KEYWORDS));
    }
}
