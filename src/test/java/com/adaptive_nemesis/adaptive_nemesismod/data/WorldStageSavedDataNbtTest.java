package com.adaptive_nemesis.adaptive_nemesismod.data;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/**
 * WorldStageSavedData 世界阶段 NBT 序列化测试（T-01）
 *
 * 验证世界阶段与已击败 Boss 集合的 NBT round-trip、空集合、
 * 阶段边界值与脏标记语义，无需 Minecraft 运行时环境
 * （SavedData 序列化仅依赖 CompoundTag 纯 NBT 类与空注册表访问器）。
 *
 * 覆盖点：
 * - F-06：世界阶段持久化真实现（stage + defeatedBosses 无损序列化）
 * - F-06：反序列化阶段负值钳制为 0，防异常数据破坏缩放计算
 * - F-06：writeState 内容未变化时跳过 setDirty，避免周期保存无效写盘
 */
@DisplayName("世界阶段 SavedData NBT 序列化测试")
class WorldStageSavedDataNbtTest {

    /** 与 WorldStageSavedData 序列化契约一致的键名（该常量为私有，此处按契约字符串构造） */
    private static final String KEY_STAGE = "stage";
    private static final String KEY_DEFEATED_BOSSES = "defeated_bosses";

    /** 空注册表访问器：本数据不含注册表对象，作为 HolderLookup.Provider 占位传入 */
    private static final RegistryAccess PROVIDER = RegistryAccess.EMPTY;

    /**
     * 构造一个已写入指定状态的 SavedData 实例
     *
     * @param stage 世界阶段
     * @param advanceTime 最近阶段推进时间戳
     * @param bosses 已击败 Boss 集合
     * @return 已写入状态的实例
     */
    private WorldStageSavedData dataWithState(int stage, long advanceTime, Set<String> bosses) {
        WorldStageSavedData data = new WorldStageSavedData();
        data.writeState(stage, advanceTime, bosses);
        return data;
    }

    @Test
    @DisplayName("完整数据 round-trip：阶段、时间戳与多 Boss 集合无损")
    void testRoundTripPreservesAllData() {
        WorldStageSavedData source = dataWithState(3, 1712345678901L,
            Set.of("minecraft:ender_dragon", "minecraft:wither", "minecraft:warden"));

        CompoundTag tag = source.save(new CompoundTag(), PROVIDER);
        WorldStageSavedData restored = WorldStageSavedData.load(tag, PROVIDER);

        assertEquals(3, restored.getStage(), "阶段应无损");
        assertEquals(1712345678901L, restored.getLastStageAdvanceTime(), "阶段推进时间戳应无损");
        assertEquals(3, restored.getDefeatedBosses().size(), "Boss 集合大小应无损");
        assertTrue(restored.getDefeatedBosses().contains("minecraft:ender_dragon"), "末影龙记录应无损");
        assertTrue(restored.getDefeatedBosses().contains("minecraft:wither"), "凋灵记录应无损");
        assertTrue(restored.getDefeatedBosses().contains("minecraft:warden"), "坚守者记录应无损");
    }

    @Test
    @DisplayName("空集合 round-trip：保持默认阶段与空 Boss 集合")
    void testRoundTripEmptyBosses() {
        WorldStageSavedData source = dataWithState(0, 0L, Set.of());

        CompoundTag tag = source.save(new CompoundTag(), PROVIDER);
        WorldStageSavedData restored = WorldStageSavedData.load(tag, PROVIDER);

        assertEquals(0, restored.getStage(), "初始阶段应为 0");
        assertEquals(0, restored.getLastStageAdvanceTime(), "未推进过时间戳应为 0");
        assertTrue(restored.getDefeatedBosses().isEmpty(), "空 Boss 集合 round-trip 后应为空");
    }

    @Test
    @DisplayName("空 NBT 反序列化：全部回退到默认值")
    void testLoadFromEmptyTagYieldsDefaults() {
        WorldStageSavedData restored = WorldStageSavedData.load(new CompoundTag(), PROVIDER);

        assertEquals(0, restored.getStage(), "缺 stage 键时阶段应为 0");
        assertEquals(0, restored.getLastStageAdvanceTime(), "缺时间戳键时应为 0");
        assertTrue(restored.getDefeatedBosses().isEmpty(), "缺 Boss 列表键时应为空集合");
    }

    @Test
    @DisplayName("阶段边界值：0 与 Integer.MAX_VALUE round-trip 无损")
    void testStageBoundaryValues() {
        WorldStageSavedData zeroStage = dataWithState(0, 0L, Set.of("minecraft:wither"));
        WorldStageSavedData restoredZero = WorldStageSavedData.load(
            zeroStage.save(new CompoundTag(), PROVIDER), PROVIDER);
        assertEquals(0, restoredZero.getStage(), "阶段 0 应无损");

        WorldStageSavedData maxStage = dataWithState(Integer.MAX_VALUE, 0L, Set.of("minecraft:wither"));
        WorldStageSavedData restoredMax = WorldStageSavedData.load(
            maxStage.save(new CompoundTag(), PROVIDER), PROVIDER);
        assertEquals(Integer.MAX_VALUE, restoredMax.getStage(), "int 上限阶段应无损");
    }

    @Test
    @DisplayName("阶段负值钳制：反序列化时负阶段回退为 0")
    void testNegativeStageClampedToZero() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_STAGE, -5);

        WorldStageSavedData restored = WorldStageSavedData.load(tag, PROVIDER);

        assertEquals(0, restored.getStage(), "负阶段应钳制为 0，防止破坏缩放计算");
    }

    @Test
    @DisplayName("Boss 集合集合语义：重复 ID 仅保留一份")
    void testDuplicateBossIdsDeduplicated() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(KEY_STAGE, 1);
        ListTag bossList = new ListTag();
        bossList.add(StringTag.valueOf("minecraft:ender_dragon"));
        bossList.add(StringTag.valueOf("minecraft:ender_dragon"));
        tag.put(KEY_DEFEATED_BOSSES, bossList);

        WorldStageSavedData restored = WorldStageSavedData.load(tag, PROVIDER);

        assertEquals(1, restored.getDefeatedBosses().size(), "重复 Boss ID 应去重为一份");
    }

    @Test
    @DisplayName("writeState 脏标记语义：内容未变化不 setDirty，变化时标记")
    void testWriteStateDirtySemantics() {
        Set<String> bosses = Set.of("minecraft:ender_dragon");
        WorldStageSavedData data = dataWithState(1, 100L, bosses);
        data.setDirty(false);

        // 内容完全一致：不产生脏标记
        assertFalse(data.writeState(1, 100L, Set.of("minecraft:ender_dragon")),
            "内容一致时 writeState 应返回 false");
        assertFalse(data.isDirty(), "内容一致时不应标记脏数据");

        // 内容变化（新增 Boss）：返回 true 并标记脏数据
        assertTrue(data.writeState(1, 100L, Set.of("minecraft:ender_dragon", "minecraft:wither")),
            "内容变化时 writeState 应返回 true");
        assertTrue(data.isDirty(), "内容变化时应标记脏数据等待落盘");
    }
}
