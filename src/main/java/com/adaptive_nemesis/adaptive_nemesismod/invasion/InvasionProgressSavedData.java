package com.adaptive_nemesis.adaptive_nemesismod.invasion;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 入侵玩家进度 SavedData（F-15）
 *
 * 挂载于 overworld 的 DimensionDataStorage，持久化以下玩家进度数据：
 * - 玩家击杀亡灵计数（用于入侵触发进度）
 * - 首次入侵完成标记（决定后续入侵所需击杀数）
 * - 玩家入侵冷却结束时间戳（墙钟时间，重启后仍然有效）
 *
 * 活动入侵状态机（ActiveInvasion）不持久化：入侵是临时战斗事件，
 * 停服即清理（由 InvasionSystem 在 ServerStoppingEvent 中主动清理内存态），
 * 重启后玩家从进度断点继续（击杀进度、首侵标记与冷却均保留）。
 *
 * 冷却条目按墙钟时间自动过期：写入与加载时均剔除已过期条目，
 * 避免长期运行的服务器随登录玩家数无界增长。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public final class InvasionProgressSavedData extends SavedData {

    /** SavedData 存储键名 */
    private static final String DATA_NAME = "adaptive_nemesis_invasion_progress";

    /** 玩家 UUID 的 NBT 键 */
    private static final String KEY_UUID = "uuid";

    /** 击杀计数 NBT 键 */
    private static final String KEY_KILLS = "kills";

    /** 冷却结束时间戳 NBT 键 */
    private static final String KEY_COOLDOWN_END = "cooldown_end";

    /** 击杀计数列表的根 NBT 键 */
    private static final String KEY_UNDEAD_KILLS = "undead_kills";

    /** 首侵完成列表的根 NBT 键 */
    private static final String KEY_FIRST_INVASION = "first_invasion_done";

    /** 冷却列表的根 NBT 键 */
    private static final String KEY_COOLDOWNS = "invasion_cooldowns";

    /** 玩家击杀亡灵计数 */
    private final Map<UUID, Integer> undeadKills = new HashMap<>();

    /** 已完成首次入侵的玩家集合 */
    private final Set<UUID> firstInvasionDone = new HashSet<>();

    /** 玩家入侵冷却结束时间戳 */
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    /**
     * 获取挂载于 overworld 的 SavedData 实例（不存在时创建）
     *
     * @param server 服务端实例
     * @return SavedData 实例
     */
    public static InvasionProgressSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(InvasionProgressSavedData::new, InvasionProgressSavedData::load, null),
            DATA_NAME
        );
    }

    /**
     * 写入单个玩家的进度数据
     * 传入 null 的字段不做更新；击杀数与冷却为非正值时移除对应条目
     *
     * @param playerId 玩家 UUID
     * @param kills 击杀计数，null 表示不更新
     * @param firstDone 首侵完成标记，null 表示不更新
     * @param cooldownEnd 冷却结束时间戳（毫秒），null 表示不更新
     */
    public void writePlayer(UUID playerId, Integer kills, Boolean firstDone, Long cooldownEnd) {
        if (kills != null) {
            if (kills > 0) {
                undeadKills.put(playerId, kills);
            } else {
                undeadKills.remove(playerId);
            }
        }
        if (firstDone != null && firstDone) {
            firstInvasionDone.add(playerId);
        }
        if (cooldownEnd != null) {
            if (cooldownEnd > System.currentTimeMillis()) {
                cooldowns.put(playerId, cooldownEnd);
            } else {
                cooldowns.remove(playerId);
            }
        }
        setDirty();
    }

    /**
     * 清除玩家的入侵冷却（胜利时调用）
     *
     * @param playerId 玩家 UUID
     */
    public void clearCooldown(UUID playerId) {
        if (cooldowns.remove(playerId) != null) {
            setDirty();
        }
    }

    /**
     * 获取击杀计数快照（只读）
     *
     * @return 不可修改的击杀计数映射
     */
    public Map<UUID, Integer> getUndeadKills() {
        return Collections.unmodifiableMap(undeadKills);
    }

    /**
     * 获取首侵完成玩家集合（只读）
     *
     * @return 不可修改的玩家 UUID 集合
     */
    public Set<UUID> getFirstInvasionDone() {
        return Collections.unmodifiableSet(firstInvasionDone);
    }

    /**
     * 获取冷却映射快照（只读）
     *
     * @return 不可修改的冷却映射
     */
    public Map<UUID, Long> getCooldowns() {
        return Collections.unmodifiableMap(cooldowns);
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
        ListTag killList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : undeadKills.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID(KEY_UUID, entry.getKey());
            item.putInt(KEY_KILLS, entry.getValue());
            killList.add(item);
        }
        tag.put(KEY_UNDEAD_KILLS, killList);

        ListTag firstList = new ListTag();
        for (UUID playerId : firstInvasionDone) {
            CompoundTag item = new CompoundTag();
            item.putUUID(KEY_UUID, playerId);
            firstList.add(item);
        }
        tag.put(KEY_FIRST_INVASION, firstList);

        ListTag cooldownList = new ListTag();
        for (Map.Entry<UUID, Long> entry : cooldowns.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putUUID(KEY_UUID, entry.getKey());
            item.putLong(KEY_COOLDOWN_END, entry.getValue());
            cooldownList.add(item);
        }
        tag.put(KEY_COOLDOWNS, cooldownList);

        return tag;
    }

    /**
     * 从 NBT 反序列化
     * 冷却条目已过期时直接丢弃
     *
     * @param tag 来源 NBT
     * @param registries 注册表访问器（本数据不含注册表对象，未使用）
     * @return 反序列化后的 SavedData 实例
     */
    public static InvasionProgressSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        InvasionProgressSavedData data = new InvasionProgressSavedData();

        ListTag killList = tag.getList(KEY_UNDEAD_KILLS, Tag.TAG_COMPOUND);
        for (int i = 0; i < killList.size(); i++) {
            CompoundTag item = killList.getCompound(i);
            if (item.hasUUID(KEY_UUID)) {
                int kills = Math.max(0, item.getInt(KEY_KILLS));
                if (kills > 0) {
                    data.undeadKills.put(item.getUUID(KEY_UUID), kills);
                }
            }
        }

        ListTag firstList = tag.getList(KEY_FIRST_INVASION, Tag.TAG_COMPOUND);
        for (int i = 0; i < firstList.size(); i++) {
            CompoundTag item = firstList.getCompound(i);
            if (item.hasUUID(KEY_UUID)) {
                data.firstInvasionDone.add(item.getUUID(KEY_UUID));
            }
        }

        ListTag cooldownList = tag.getList(KEY_COOLDOWNS, Tag.TAG_COMPOUND);
        for (int i = 0; i < cooldownList.size(); i++) {
            CompoundTag item = cooldownList.getCompound(i);
            if (item.hasUUID(KEY_UUID)) {
                long cooldownEnd = item.getLong(KEY_COOLDOWN_END);
                if (cooldownEnd > System.currentTimeMillis()) {
                    data.cooldowns.put(item.getUUID(KEY_UUID), cooldownEnd);
                }
            }
        }

        AdaptiveNemesisMod.LOGGER.info("已加载入侵玩家进度：击杀记录 {} 条，首侵标记 {} 条，冷却 {} 条",
            data.undeadKills.size(), data.firstInvasionDone.size(), data.cooldowns.size());
        return data;
    }
}
