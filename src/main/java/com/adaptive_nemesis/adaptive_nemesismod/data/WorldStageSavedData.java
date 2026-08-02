package com.adaptive_nemesis.adaptive_nemesismod.data;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.WorldStageManager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

/**
 * 世界阶段数据持久化 (Forge 1.20.1 SavedData)
 *
 * 全服共享：挂在 overworld 的 DimensionDataStorage 上。
 */
public class WorldStageSavedData extends SavedData {

    public static final String DATA_NAME = AdaptiveNemesisMod.MODID + "_world_stage";

    public WorldStageSavedData() {}

    public static WorldStageSavedData load(CompoundTag tag) {
        WorldStageSavedData data = new WorldStageSavedData();
        try {
            if (tag.contains("adaptive_nemesis_world_stage")) {
                WorldStageManager.getInstance().load(tag.getCompound("adaptive_nemesis_world_stage"));
            } else {
                // 兼容直接写入根 tag 的格式
                WorldStageManager.getInstance().load(tag);
            }
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("加载世界阶段 SavedData 失败: {}", e.getMessage());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        try {
            CompoundTag payload = WorldStageManager.getInstance().save();
            tag.put("adaptive_nemesis_world_stage", payload);
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("保存世界阶段 SavedData 失败: {}", e.getMessage());
        }
        return tag;
    }

    /**
     * 从 overworld 存储加载并灌入 WorldStageManager
     */
    public static void load(ServerLevel level) {
        if (level == null) {
            AdaptiveNemesisMod.LOGGER.warn("WorldStageSavedData.load 收到 null level，跳过加载");
            return;
        }
        try {
            ServerLevel overworld = level.getServer() != null ? level.getServer().overworld() : level;
            DimensionDataStorage storage = overworld.getDataStorage();
            WorldStageSavedData data = storage.computeIfAbsent(
                    WorldStageSavedData::load,
                    WorldStageSavedData::new,
                    DATA_NAME
            );
            // computeIfAbsent 已在 load() 工厂里调用了 manager.load
            WorldStageManager.getInstance().setServerLevel(overworld);
            AdaptiveNemesisMod.LOGGER.debug(
                    "世界阶段 SavedData 已加载，当前阶段: {}",
                    WorldStageManager.getInstance().getWorldStage()
            );
            // touch to avoid unused warning if compiler strips
            if (data != null) {
                data.setDirty(false);
            }
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("WorldStageSavedData.load 失败: {}", e.getMessage());
        }
    }

    /**
     * 将 WorldStageManager 状态标记脏并写入存储
     */
    public static void save(ServerLevel level) {
        if (level == null) {
            AdaptiveNemesisMod.LOGGER.warn("WorldStageSavedData.save 收到 null level，跳过保存");
            return;
        }
        try {
            ServerLevel overworld = level.getServer() != null ? level.getServer().overworld() : level;
            DimensionDataStorage storage = overworld.getDataStorage();
            WorldStageSavedData data = storage.computeIfAbsent(
                    WorldStageSavedData::load,
                    WorldStageSavedData::new,
                    DATA_NAME
            );
            data.setDirty();
            // 立即写出：Forge/MC 会在世界保存时 flush；这里额外触发一次 setDirty 即可
            AdaptiveNemesisMod.LOGGER.debug(
                    "世界阶段 SavedData 已标记保存，当前阶段: {}",
                    WorldStageManager.getInstance().getWorldStage()
            );
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("WorldStageSavedData.save 失败: {}", e.getMessage());
        }
    }
}
