package com.adaptive_nemesis.adaptive_nemesismod.data;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.WorldStageManager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * 世界阶段数据保存工具类
 * （简化版本 - 暂不实现完整持久化）
 */
public class WorldStageSavedData {

    /**
     * 加载世界阶段数据
     */
    public static void load(ServerLevel level) {
        // 暂不实现持久化加载
        AdaptiveNemesisMod.LOGGER.info("World stage data loading skipped (persistence not fully implemented yet)");
    }

    /**
     * 保存世界阶段数据
     */
    public static void save(ServerLevel level) {
        // 暂不实现持久化保存
        AdaptiveNemesisMod.LOGGER.info("World stage data saving skipped (persistence not fully implemented yet)");
    }
}
