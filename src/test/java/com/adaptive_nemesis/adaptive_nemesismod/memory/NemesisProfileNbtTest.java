package com.adaptive_nemesis.adaptive_nemesismod.memory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * NemesisProfile 宿敌档案 NBT 序列化与加成钳制测试（T-02）
 *
 * 验证档案 NBT round-trip、KDA 极端值容错与加成计算钳制，
 * 无需 Minecraft 运行时环境（仅使用纯 NBT 类与纯逻辑方法）。
 *
 * 覆盖点：
 * - F-05：档案可序列化持久化（round-trip 无损）
 * - F-39：数据包配置解析后的下限钳制
 * - F-44：三系数（每死亡攻击 / KDA 速度 / 每击杀生命）经配置生效并被上限钳制
 */
@DisplayName("宿敌档案 NBT 序列化与加成钳制测试")
class NemesisProfileNbtTest {

    /** 与 NemesisProfile 序列化契约一致的键名（该常量为私有，此处按契约字符串构造） */
    private static final String KEY_KILL_STYLES = "KillStyles";
    private static final String KEY_ENTRY_STYLE = "Style";
    private static final String KEY_ENTRY_COUNT = "Count";
    private static final String KEY_TOTAL_KILLS = "TotalKills";
    private static final String KEY_TOTAL_DEATHS = "TotalDeaths";

    /**
     * 构造一个只含击杀/死亡统计的档案 NBT（KDA 极端值用例）
     *
     * @param totalKills 总击杀数
     * @param totalDeaths 总死亡数
     * @return 仅含统计字段的 NBT
     */
    private CompoundTag tagWithCounts(int totalKills, int totalDeaths) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_TOTAL_KILLS, totalKills);
        tag.putInt(KEY_TOTAL_DEATHS, totalDeaths);
        return tag;
    }

    @Test
    @DisplayName("完整档案 round-trip 保留全部记忆数据")
    void testRoundTripPreservesAllData() {
        NemesisProfile source = new NemesisProfile();
        source.recordKillStyle(NemesisMemorySystem.CombatStyle.MELEE);
        source.recordKillStyle(NemesisMemorySystem.CombatStyle.MELEE);
        source.recordKillStyle(NemesisMemorySystem.CombatStyle.RANGED);
        source.recordWeaponUsed("minecraft:bow");
        source.recordWeaponUsed("minecraft:bow");
        source.recordWeaponUsed("minecraft:sword");
        source.recordKilledEntity("minecraft:zombie");
        source.recordDeathSource("mob");
        source.recordKillerType("minecraft:zombie");

        CompoundTag tag = source.writeToNbt(new CompoundTag());
        NemesisProfile restored = NemesisProfile.readFromNbt(tag);

        assertEquals(source.getTotalKills(), restored.getTotalKills(), "总击杀数应无损");
        assertEquals(source.getTotalDeaths(), restored.getTotalDeaths(), "总死亡数应无损");
        assertEquals(2, restored.getKillStyleCount(NemesisMemorySystem.CombatStyle.MELEE), "近战击杀计数应无损");
        assertEquals(1, restored.getKillStyleCount(NemesisMemorySystem.CombatStyle.RANGED), "远程击杀计数应无损");
        assertEquals(0, restored.getKillStyleCount(NemesisMemorySystem.CombatStyle.MAGIC), "未记录风格计数应为 0");
        assertEquals("minecraft:bow", restored.getMostUsedWeapon(), "最常用武器应无损");
        assertEquals("minecraft:zombie", restored.getMostKilledEntity(), "最多击杀敌人应无损");
        assertEquals("mob", restored.getMostCommonDeathSource(), "最常见死亡来源应无损");
        assertEquals("minecraft:zombie", restored.getMostCommonKiller(), "最常见击杀者应无损");
        assertEquals(source.getCreationTime(), restored.getCreationTime(), "创建时间应无损");
    }

    @Test
    @DisplayName("空档案 round-trip 保持默认值")
    void testRoundTripEmptyProfile() {
        NemesisProfile source = new NemesisProfile();

        CompoundTag tag = source.writeToNbt(new CompoundTag());
        NemesisProfile restored = NemesisProfile.readFromNbt(tag);

        assertEquals(0, restored.getTotalKills(), "空档案击杀数应为 0");
        assertEquals(0, restored.getTotalDeaths(), "空档案死亡数应为 0");
        assertEquals(0, restored.getKillStyleCount(NemesisMemorySystem.CombatStyle.MELEE), "空档案各风格计数应为 0");
        assertEquals(NemesisMemorySystem.CombatStyle.MELEE, restored.getDominantStyle(), "无记录时默认近战风格");
    }

    @Test
    @DisplayName("KDA 极端值：击杀数达到 int 上限且无死亡")
    void testKdaExtremeValueMaxKillsZeroDeaths() {
        NemesisProfile profile = NemesisProfile.readFromNbt(tagWithCounts(Integer.MAX_VALUE, 0));

        assertEquals(Integer.MAX_VALUE, profile.getTotalKills(), "int 上限击杀数应保留");
        assertEquals(0, profile.getTotalDeaths(), "死亡数应为 0");
        assertEquals((double) Integer.MAX_VALUE, profile.getKdaRatio(), 0.0001, "无死亡时 KDA 等于总击杀数");
    }

    @Test
    @DisplayName("KDA 常规比率与零记录")
    void testKdaNormalAndZero() {
        assertEquals(0.5, NemesisProfile.readFromNbt(tagWithCounts(5, 10)).getKdaRatio(), 0.0001, "5 杀 10 死 KDA 为 0.5");
        assertEquals(0.0, NemesisProfile.readFromNbt(tagWithCounts(0, 0)).getKdaRatio(), 0.0001, "零记录 KDA 为 0");
    }

    @Test
    @DisplayName("配置钳制：负值上限归零，等级参数下限为 1")
    void testBuilderClamps() {
        NemesisConfigData config = NemesisConfigData.builder()
            .meleeResistanceCap(-1.0)
            .rangedResistanceCap(-0.5)
            .magicResistanceCap(-100.0)
            .attackBonusCap(-2.0)
            .speedBonusCap(-1.0)
            .healthBonusCap(-3.0)
            .killsPerLevel(0)
            .deathsPerLevel(-5)
            .maxLevel(0)
            .attackBonusPerDeath(-0.01)
            .speedBonusPerKda(-0.02)
            .healthBonusPerKill(-0.03)
            .kdaSpeedThreshold(-1.0)
            .notificationRadius(-32.0)
            .build();

        assertEquals(0.0, config.getMeleeResistanceCap(), 0.0001, "负近战抗性上限应钳制为 0");
        assertEquals(0.0, config.getRangedResistanceCap(), 0.0001, "负远程抗性上限应钳制为 0");
        assertEquals(0.0, config.getMagicResistanceCap(), 0.0001, "负魔法抗性上限应钳制为 0");
        assertEquals(0.0, config.getAttackBonusCap(), 0.0001, "负攻击加成上限应钳制为 0");
        assertEquals(0.0, config.getSpeedBonusCap(), 0.0001, "负速度加成上限应钳制为 0");
        assertEquals(0.0, config.getHealthBonusCap(), 0.0001, "负生命加成上限应钳制为 0");
        assertEquals(1, config.getKillsPerLevel(), "killsPerLevel 应钳制为不小于 1");
        assertEquals(1, config.getDeathsPerLevel(), "deathsPerLevel 应钳制为不小于 1");
        assertEquals(1, config.getMaxLevel(), "maxLevel 应钳制为不小于 1");
        assertEquals(0.0, config.getAttackBonusPerDeath(), 0.0001, "负攻击系数应钳制为 0");
        assertEquals(0.0, config.getSpeedBonusPerKda(), 0.0001, "负速度系数应钳制为 0");
        assertEquals(0.0, config.getHealthBonusPerKill(), 0.0001, "负生命系数应钳制为 0");
        assertEquals(0.0, config.getKdaSpeedThreshold(), 0.0001, "负 KDA 阈值应钳制为 0");
        assertEquals(0.0, config.getNotificationRadius(), 0.0001, "负通知半径应钳制为 0");
    }

    @Test
    @DisplayName("攻击加成：随死亡线性增长并被上限钳制")
    void testAttackBonusClamp() {
        NemesisConfigData config = NemesisConfigData.builder()
            .attackBonusCap(0.25)
            .attackBonusPerDeath(0.02)
            .build();

        NemesisProfile fewDeaths = new NemesisProfile();
        for (int i = 0; i < 10; i++) {
            fewDeaths.recordDeathSource("mob");
        }
        assertEquals(0.2, fewDeaths.getAttackBonus(config), 0.0001, "10 死 x 2% = 20% 攻击加成");

        NemesisProfile manyDeaths = new NemesisProfile();
        for (int i = 0; i < 100; i++) {
            manyDeaths.recordDeathSource("mob");
        }
        assertEquals(0.25, manyDeaths.getAttackBonus(config), 0.0001, "100 死加成超出上限时应被钳制为 25%");
    }

    @Test
    @DisplayName("攻击加成：无死亡时为 0")
    void testAttackBonusZeroDeaths() {
        NemesisConfigData config = NemesisConfigData.builder()
            .attackBonusCap(0.25)
            .attackBonusPerDeath(0.02)
            .build();

        NemesisProfile profile = new NemesisProfile();
        profile.recordKillStyle(NemesisMemorySystem.CombatStyle.MELEE);
        assertEquals(0.0, profile.getAttackBonus(config), 0.0001, "无死亡时不提供攻击加成");
    }

    @Test
    @DisplayName("速度加成：KDA 阈值触发与上限钳制")
    void testSpeedBonusThresholdAndClamp() {
        NemesisConfigData config = NemesisConfigData.builder()
            .speedBonusCap(0.2)
            .speedBonusPerKda(0.03)
            .kdaSpeedThreshold(3.0)
            .build();

        NemesisProfile belowThreshold = new NemesisProfile();
        belowThreshold.recordKillStyle(NemesisMemorySystem.CombatStyle.MELEE);
        belowThreshold.recordKillStyle(NemesisMemorySystem.CombatStyle.MELEE);
        belowThreshold.recordKillStyle(NemesisMemorySystem.CombatStyle.MELEE);
        assertEquals(0.0, belowThreshold.getSpeedBonus(config), 0.0001, "KDA 等于阈值时不提供速度加成");

        NemesisProfile aboveThreshold = new NemesisProfile();
        aboveThreshold.recordKillStyle(NemesisMemorySystem.CombatStyle.MELEE);
        aboveThreshold.recordKillStyle(NemesisMemorySystem.CombatStyle.MELEE);
        aboveThreshold.recordKillStyle(NemesisMemorySystem.CombatStyle.MELEE);
        aboveThreshold.recordKillStyle(NemesisMemorySystem.CombatStyle.MELEE);
        assertEquals(0.03, aboveThreshold.getSpeedBonus(config), 0.0001, "KDA 4.0 超阈值 1.0，速度加成为 3%");

        NemesisProfile capped = new NemesisProfile();
        for (int i = 0; i < 20; i++) {
            capped.recordKillStyle(NemesisMemorySystem.CombatStyle.MAGIC);
        }
        assertEquals(0.2, capped.getSpeedBonus(config), 0.0001, "KDA 20 的加成超出上限时应被钳制为 20%");
    }

    @Test
    @DisplayName("生命加成：随击杀线性增长并被上限钳制")
    void testHealthBonusClamp() {
        NemesisConfigData config = NemesisConfigData.builder()
            .healthBonusCap(0.5)
            .healthBonusPerKill(0.01)
            .build();

        NemesisProfile fewKills = new NemesisProfile();
        for (int i = 0; i < 10; i++) {
            fewKills.recordKillStyle(NemesisMemorySystem.CombatStyle.MELEE);
        }
        assertEquals(0.1, fewKills.getHealthBonus(config), 0.0001, "10 杀 x 1% = 10% 生命加成");

        NemesisProfile manyKills = new NemesisProfile();
        for (int i = 0; i < 100; i++) {
            manyKills.recordKillStyle(NemesisMemorySystem.CombatStyle.MELEE);
        }
        assertEquals(0.5, manyKills.getHealthBonus(config), 0.0001, "100 杀加成超出上限时应被钳制为 50%");
    }

    @Test
    @DisplayName("加成系数为 0（负值钳制后）时全部加成为 0")
    void testZeroCoefficientsYieldZeroBonus() {
        NemesisConfigData config = NemesisConfigData.builder()
            .attackBonusPerDeath(-0.02)
            .speedBonusPerKda(-0.03)
            .healthBonusPerKill(-0.01)
            .attackBonusCap(0.25)
            .speedBonusCap(0.2)
            .healthBonusCap(0.5)
            .build();

        NemesisProfile profile = new NemesisProfile();
        for (int i = 0; i < 50; i++) {
            profile.recordKillStyle(NemesisMemorySystem.CombatStyle.MELEE);
            profile.recordDeathSource("mob");
        }

        assertEquals(0.0, profile.getAttackBonus(config), 0.0001, "系数钳制为 0 后攻击加成为 0");
        assertEquals(0.0, profile.getHealthBonus(config), 0.0001, "系数钳制为 0 后生命加成为 0");
    }

    @Test
    @DisplayName("NBT 中未知战斗风格条目被安全跳过")
    void testUnknownStyleSkippedOnLoad() {
        CompoundTag tag = new CompoundTag();
        ListTag styles = new ListTag();
        CompoundTag badEntry = new CompoundTag();
        badEntry.putString(KEY_ENTRY_STYLE, "NOT_A_STYLE");
        badEntry.putInt(KEY_ENTRY_COUNT, 5);
        styles.add(badEntry);
        tag.put(KEY_KILL_STYLES, styles);

        NemesisProfile profile = assertDoesNotThrow(() -> NemesisProfile.readFromNbt(tag),
            "未知风格条目不应导致读取失败");

        assertEquals(0, profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.MELEE), "未知风格不应污染合法计数");
        assertEquals(0, profile.getTotalKills(), "未知风格不应计入总击杀");
    }
}
