package com.adaptive_nemesis.adaptive_nemesismod.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.WorldStageManager;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 世界阶段 SavedData（F-06）
 *
 * 挂载于 overworld 的 DimensionDataStorage，持久化以下全服共享数据：
 * - 当前世界阶段（int，决定永久难度倍率）
 * - 最近一次阶段推进的时间戳（毫秒，仅用于展示与调试）
 * - 已击败 Boss 类型集合（字符串，Boss 类型标识由 BossIdentificationService 给出）
 *
 * 数据流：内存真值保存在 WorldStageManager；本类提供 writeState 将内存态
 * 同步进 SavedData 并 setDirty，实际落盘由 overworld 自动存档完成。
 * 服务器启动（ServerStartedEvent）时通过静态 load(server) 将 SavedData
 * 内容回灌 WorldStageManager，实现重启后世界阶段与击败记录保留。
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public final class WorldStageSavedData extends SavedData {

    /** SavedData 存储键名 */
    private static final String DATA_NAME = "adaptive_nemesis_world_stage";

    /** 世界阶段 NBT 键 */
    private static final String KEY_STAGE = "stage";

    /** 最近阶段推进时间戳 NBT 键 */
    private static final String KEY_LAST_ADVANCE_TIME = "last_stage_advance_time";

    /** 已击败 Boss 集合列表的根 NBT 键 */
    private static final String KEY_DEFEATED_BOSSES = "defeated_bosses";

    /** 当前世界阶段 */
    private int stage = 0;

    /** 最近一次阶段推进的时间戳（毫秒） */
    private long lastStageAdvanceTime = 0L;

    /** 已击败 Boss 类型集合 */
    private final Set<String> defeatedBosses = new HashSet<>();

    /**
     * 获取挂载于 overworld 的 SavedData 实例（不存在时创建）
     *
     * @param server 服务端实例
     * @return SavedData 实例
     */
    public static WorldStageSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(WorldStageSavedData::new, WorldStageSavedData::load, null),
            DATA_NAME
        );
    }

    /**
     * 从 WorldStageManager 内存态同步数据
     * 内容与当前一致时不重复 setDirty，避免周期性自动保存产生无效写盘
     *
     * @param newStage 当前世界阶段
     * @param newLastAdvanceTime 最近阶段推进时间戳（毫秒）
     * @param bosses 已击败 Boss 类型集合
     * @return 本次是否发生了数据变更（即是否标记了脏数据）
     */
    public boolean writeState(int newStage, long newLastAdvanceTime, Set<String> bosses) {
        if (this.stage == newStage
                && this.lastStageAdvanceTime == newLastAdvanceTime
                && this.defeatedBosses.equals(bosses)) {
            return false;
        }
        this.stage = newStage;
        this.lastStageAdvanceTime = newLastAdvanceTime;
        this.defeatedBosses.clear();
        this.defeatedBosses.addAll(bosses);
        setDirty();
        return true;
    }

    /**
     * 获取当前世界阶段
     *
     * @return 世界阶段
     */
    public int getStage() {
        return stage;
    }

    /**
     * 获取最近一次阶段推进的时间戳
     *
     * @return 时间戳（毫秒）
     */
    public long getLastStageAdvanceTime() {
        return lastStageAdvanceTime;
    }

    /**
     * 获取已击败 Boss 集合快照（只读）
     *
     * @return 不可修改的 Boss 类型集合
     */
    public Set<String> getDefeatedBosses() {
        return Collections.unmodifiableSet(defeatedBosses);
    }

    /**
     * 从 SavedData 加载世界阶段数据到 WorldStageManager 内存态（F-06 接线）
     * 服务器启动完成（ServerStartedEvent）时调用
     *
     * @param server 服务端实例，传 null 时跳过加载并 warn
     */
    public static void load(MinecraftServer server) {
        if (server == null) {
            AdaptiveNemesisMod.LOGGER.warn("WorldStageSavedData.load 收到 null server，跳过加载");
            return;
        }
        WorldStageSavedData data = get(server);
        WorldStageManager.getInstance().restoreFrom(data);
        AdaptiveNemesisMod.LOGGER.info("已加载世界阶段数据：阶段 {}，已击败 Boss {} 种",
            data.getStage(), data.getDefeatedBosses().size());
    }

    /**
     * 将 WorldStageManager 内存态写入 SavedData（F-06 接线）
     * 仅在 overworld 上操作，数据变更时 setDirty，实际落盘由世界自动存档完成
     *
     * @param server 服务端实例，传 null 时跳过保存并 warn
     */
    public static void save(MinecraftServer server) {
        if (server == null) {
            AdaptiveNemesisMod.LOGGER.warn("WorldStageSavedData.save 收到 null server，跳过保存");
            return;
        }
        WorldStageManager manager = WorldStageManager.getInstance();
        WorldStageSavedData data = get(server);
        boolean changed = data.writeState(
            manager.getWorldStage(),
            manager.getLastStageAdvanceTime(),
            manager.getDefeatedBosses()
        );
        if (changed) {
            AdaptiveNemesisMod.LOGGER.debug(
                "世界阶段数据已写入 SavedData（阶段: {}, Boss {} 种），将随世界存档落盘",
                data.getStage(), data.getDefeatedBosses().size()
            );
        }
    }

    /**
     * 序列化到 NBT
     *
     * @param tag 目标 NBT
     * @param registries 注册表访问器（本数据不含注册表对象，未使用）
     * @return 序列化后的 NBT
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(KEY_STAGE, stage);
        tag.putLong(KEY_LAST_ADVANCE_TIME, lastStageAdvanceTime);

        ListTag bossList = new ListTag();
        for (String boss : defeatedBosses) {
            bossList.add(StringTag.valueOf(boss));
        }
        tag.put(KEY_DEFEATED_BOSSES, bossList);
        return tag;
    }

    /**
     * 从 NBT 反序列化
     * 阶段为负值时钳制为 0，防止异常数据破坏缩放计算
     *
     * @param tag 来源 NBT
     * @param registries 注册表访问器（本数据不含注册表对象，未使用）
     * @return 反序列化后的 SavedData 实例
     */
    public static WorldStageSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldStageSavedData data = new WorldStageSavedData();
        data.stage = Math.max(0, tag.getInt(KEY_STAGE));
        data.lastStageAdvanceTime = tag.getLong(KEY_LAST_ADVANCE_TIME);

        ListTag bossList = tag.getList(KEY_DEFEATED_BOSSES, Tag.TAG_STRING);
        for (int i = 0; i < bossList.size(); i++) {
            data.defeatedBosses.add(bossList.getString(i));
        }
        return data;
    }
}
