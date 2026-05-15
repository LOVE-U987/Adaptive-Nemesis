package com.adaptive_nemesis.adaptive_nemesismod.enemy;

import java.util.HashSet;
import java.util.Set;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.data.WorldStageSavedData;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 世界阶段性难度提升管理器
 *
 * 当玩家击杀世界Boss（末影龙、凋零、坚守者）时，提升世界阶段。
 * 每个阶段都会提供永久的、不会衰减的难度加成，
 * 在动态难度的基础上额外增加固定难度。
 *
 * 阶段说明：
 * - 阶段 0：初始状态
 * - 阶段 1：击杀任意一个 Boss 后
 * - 阶段 2：击杀两个不同的 Boss 后
 * - 阶段 3+：击杀更多 Boss 后继续提升
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class WorldStageManager {

    private static WorldStageManager INSTANCE;

    /**
     * Boss 类型标识
     */
    private static final String BOSS_DRAGON = "ender_dragon";
    private static final String BOSS_WITHER = "wither";
    private static final String BOSS_WARDEN = "warden";

    /**
     * 全服共享的世界阶段数据
     */
    private WorldStageData stageData = new WorldStageData();

    /**
     * 已击杀的Boss记录（全局共享）
     */
    private Set<String> defeatedBosses = new HashSet<>();

    /**
     * 用于自动保存的服务器实例引用
     */
    private ServerLevel serverLevelRef = null;

    private WorldStageManager() {}

    public static synchronized WorldStageManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new WorldStageManager();
        }
        return INSTANCE;
    }

    /**
     * 设置服务器实例引用（用于自动保存）
     */
    public void setServerLevel(ServerLevel level) {
        this.serverLevelRef = level;
    }

    /**
     * 获取当前世界阶段（全服共享）
     */
    public int getWorldStage() {
        return stageData.stage;
    }

    /**
     * 获取当前世界阶段倍率（全服共享）
     */
    public double getWorldStageMultiplier() {
        int stage = getWorldStage();
        return 1.0 + stage * Config.WORLD_STAGE_MULTIPLIER_PER_STAGE.get();
    }

    /**
     * 获取已击杀的Boss种类数量（全局）
     */
    public int getDefeatedBossCount() {
        return defeatedBosses.size();
    }

    /**
     * 获取已击杀的Boss列表（全局）
     */
    public Set<String> getDefeatedBosses() {
        return new HashSet<>(defeatedBosses);
    }

    /**
     * 模拟Boss击杀（用于测试）
     */
    public void simulateBossKill(String bossType) {
        if (!Config.ENABLE_WORLD_STAGE.get()) {
            return;
        }
        onBossKilled(bossType, false);
    }

    /**
     * Boss 死亡事件 - 检测并提升世界阶段
     */
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!Config.ENABLE_WORLD_STAGE.get()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }

        // 只有玩家击杀才计数
        if (!(event.getSource().getEntity() instanceof ServerPlayer)) {
            return;
        }

        String bossType = identifyBossType(entity);
        if (bossType == null) {
            return;
        }

        // 保存ServerLevel引用用于后续保存
        if (entity.level() instanceof ServerLevel level) {
            setServerLevel(level);
        }

        onBossKilled(bossType, true);
    }

    /**
     * Boss击杀公共处理逻辑
     */
    private void onBossKilled(String bossType, boolean isRealKill) {
        defeatedBosses.add(bossType);
        int bossCount = getDefeatedBossCount();

        int newStage = Math.min(bossCount, Config.WORLD_STAGE_MAX_STAGE.get());

        if (newStage > stageData.stage) {
            int oldStage = stageData.stage;
            stageData.stage = newStage;
            stageData.lastStageAdvanceTime = System.currentTimeMillis();

            String logPrefix = isRealKill ? "" : "[测试] ";
            AdaptiveNemesisMod.LOGGER.info(
                "{}世界阶段提升! 阶段: {} -> {}, 已击杀Boss种类: {}",
                logPrefix, oldStage, newStage, bossCount
            );

            // 阶段提升时立即保存数据
            saveIfPossible();
        }
    }

    /**
     * 尝试自动保存数据
     */
    private void saveIfPossible() {
        if (serverLevelRef != null) {
            try {
                WorldStageSavedData.save(serverLevelRef);
            } catch (Exception e) {
                AdaptiveNemesisMod.LOGGER.warn("自动保存世界阶段数据失败", e);
            }
        }
    }

    /**
     * 识别Boss类型
     */
    private String identifyBossType(LivingEntity entity) {
        if (entity instanceof EnderDragon) {
            return BOSS_DRAGON;
        }
        if (entity instanceof WitherBoss) {
            return BOSS_WITHER;
        }
        if (entity instanceof Warden) {
            return BOSS_WARDEN;
        }

        EntityType<?> type = entity.getType();
        ResourceLocation id = EntityType.getKey(type);
        String path = id.getPath().toLowerCase();

        if (path.contains("ender_dragon") || path.contains("dragon")) {
            return BOSS_DRAGON;
        }
        if (path.contains("wither")) {
            return BOSS_WITHER;
        }
        if (path.contains("warden")) {
            return BOSS_WARDEN;
        }

        return null;
    }

    /**
     * 重置所有世界阶段
     */
    public void resetAll() {
        stageData = new WorldStageData();
        defeatedBosses.clear();
        saveIfPossible();
    }

    /**
     * 保存数据到NBT（供持久化使用）
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("stage", stageData.stage);
        tag.putLong("lastStageAdvanceTime", stageData.lastStageAdvanceTime);
        tag.putInt("defeatedBossCount", defeatedBosses.size());

        int index = 0;
        for (String boss : defeatedBosses) {
            tag.putString("defeatedBoss_" + index, boss);
            index++;
        }
        return tag;
    }

    /**
     * 从NBT加载数据
     */
    public void load(CompoundTag tag) {
        int loadedStage = tag.getInt("stage");
        long loadedTime = tag.getLong("lastStageAdvanceTime");
        int loadedBossCount = tag.getInt("defeatedBossCount");

        HashSet<String> loadedBosses = new HashSet<>();
        for (int i = 0; i < loadedBossCount; i++) {
            String bossKey = "defeatedBoss_" + i;
            if (tag.contains(bossKey)) {
                loadedBosses.add(tag.getString(bossKey));
            }
        }

        stageData.stage = loadedStage;
        stageData.lastStageAdvanceTime = loadedTime;
        defeatedBosses = loadedBosses;
    }

    /**
     * 世界阶段数据（包装类）
     */
    private static class WorldStageData {
        int stage = 0;
        long lastStageAdvanceTime = 0;
    }
}
