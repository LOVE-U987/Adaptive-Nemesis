package com.adaptive_nemesis.adaptive_nemesismod.memory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 宿敌档案世界存档（SavedData，F-05）
 *
 * 挂载在 overworld 的 DimensionDataStorage 上，随世界自动保存/加载，
 * 解决宿敌档案重启即丢失的问题。
 *
 * 持久层与运行层共享同一 NemesisProfile 对象引用：
 * 记忆系统在创建/写入档案时即把对象放入本存档并 setDirty，
 * 停服时由 vanilla 保存流程统一写盘，登出只需从内存缓存移除条目。
 *
 * 获取方式：
 * <pre>
 * NemesisProfileSavedData savedData =
 *     NemesisProfileSavedData.get(server.overworld());
 * </pre>
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class NemesisProfileSavedData extends SavedData {

    /**
     * 存档文件名（data/ 目录下的 SavedData 标识）
     */
    public static final String DATA_NAME = "adaptive_nemesis_nemesis_profiles";

    /** 档案条目列表键 */
    private static final String KEY_PROFILES = "Profiles";

    /** 条目内玩家 UUID 键 */
    private static final String KEY_PLAYER_ID = "PlayerId";

    /** 条目内档案数据键 */
    private static final String KEY_PROFILE = "Profile";

    /**
     * 全部玩家宿敌档案
     * Key: 玩家UUID, Value: 宿敌档案（与运行缓存共享对象引用）
     */
    private final Map<UUID, NemesisProfile> profiles = new HashMap<>();

    /**
     * 从（或向）overworld 的 DimensionDataStorage 获取存档实例
     * 不存在时创建新的空存档
     *
     * @param overworld 主世界（SavedData 挂载维度）
     * @return 存档实例
     */
    public static NemesisProfileSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(NemesisProfileSavedData::new, NemesisProfileSavedData::load),
            DATA_NAME
        );
    }

    /**
     * 获取玩家宿敌档案
     *
     * @param playerId 玩家UUID
     * @return 宿敌档案，无档案时返回 null
     */
    public NemesisProfile getProfile(UUID playerId) {
        return profiles.get(playerId);
    }

    /**
     * 写入玩家宿敌档案并标记落盘
     * 幂等操作：重复写入同一档案只更新引用
     *
     * @param playerId 玩家UUID
     * @param profile 宿敌档案
     */
    public void putProfile(UUID playerId, NemesisProfile profile) {
        profiles.put(playerId, profile);
        setDirty();
    }

    /**
     * 序列化全部档案到 NBT
     *
     * @param tag 目标 NBT 标签
     * @param registries 注册表查找提供器（本存档未使用，仅满足接口签名）
     * @return 写入后的 NBT 标签
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, NemesisProfile> entry : profiles.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID(KEY_PLAYER_ID, entry.getKey());
            item.put(KEY_PROFILE, entry.getValue().writeToNbt(new CompoundTag()));
            list.add(item);
        }
        tag.put(KEY_PROFILES, list);
        return tag;
    }

    /**
     * 从 NBT 反序列化档案存档
     * 单条档案损坏（缺 UUID 等）时跳过该条并 warn，不影响其余档案
     *
     * @param tag 来源 NBT 标签
     * @param registries 注册表查找提供器（本存档未使用，仅满足接口签名）
     * @return 恢复后的存档实例
     */
    public static NemesisProfileSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        NemesisProfileSavedData data = new NemesisProfileSavedData();

        ListTag list = tag.getList(KEY_PROFILES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            if (!item.hasUUID(KEY_PLAYER_ID)) {
                AdaptiveNemesisMod.LOGGER.warn("宿敌档案存档第 {} 条缺少玩家 UUID，已跳过", i);
                continue;
            }
            UUID playerId = item.getUUID(KEY_PLAYER_ID);
            data.profiles.put(playerId, NemesisProfile.readFromNbt(item.getCompound(KEY_PROFILE)));
        }

        AdaptiveNemesisMod.LOGGER.info("已从世界存档加载 {} 份宿敌档案", data.profiles.size());
        return data;
    }
}
