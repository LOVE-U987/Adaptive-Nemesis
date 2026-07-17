package com.adaptive_nemesis.adaptive_nemesismod.invasion;

import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionSystem.InvasionType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 活动入侵事件类
 * 
 * 参考Majrusz's Progressive Difficulty模组实现
 * 使用状态机管理亡灵军团的生命周期：
 * CREATED → STARTED(闪电特效) → WAVE_PREPARING(准备倒计时) → WAVE_ONGOING(战斗) → UNDEAD_DEFEATED → FINISHED
 *                                                                         → UNDEAD_WON(超时) → FINISHED
 * 
 * @author Adaptive Nemesis Team
 * @version 2.0.0
 */
public class ActiveInvasion {

    // ======================== 核心字段 ========================

    /** 玩家UUID */
    private final UUID playerUUID;

    /** 世界 */
    private final Level level;

    /** 入侵中心位置（玩家触发时的位置） */
    private final BlockPos centerPos;

    /** 入侵类型 */
    private final InvasionType type;

    /** 总波次数量 */
    private final int totalWaves;

    /** 难度倍率 */
    private final double difficultyMultiplier;

    /** 玩家名称 */
    private final String playerName;

    /** 是否完成 */
    private boolean completed;

    /** 是否失败（玩家死亡等） */
    private boolean defeated;

    /** 是否玩家离线 */
    private boolean playerOffline;

    // ======================== 状态机字段 ========================

    /** 当前状态 */
    public Phase phase = new Phase();

    /** 当前波次编号（从1开始） */
    public int currentWave = 0;

    /** 是否正在处理波次生成 */
    public boolean isProcessingWave = false;

    // ======================== 敌对实体追踪 ========================

    /** 波次中剩余的怪物信息列表 */
    public List<MobInfo> mobsLeft = new ArrayList<>();

    /** BOSS实体（如果有） */
    public Entity boss = null;

    // ======================== 生成方向 ========================

    /** 敌人出现方向 */
    public Direction direction;

    // ======================== Boss血条UI ========================

    /** 波次进度条（白色，10段） */
    public final ServerBossEvent waveInfo;

    /** BOSS血条（红色，6段） */
    public final ServerBossEvent bossInfo;

    /** 参与战斗的玩家列表 */
    public final List<ServerPlayer> participants = new ArrayList<>();

    // ======================== 生成计时 ========================

    /** 生成间隔计时器（每20tick生成一只） */
    private int spawnTickCounter = 0;

    // ======================== 常量 ========================

    /** 准备阶段时长（秒） */
    public static final float PREPARING_DURATION = 10.0f;

    /** 波次最大持续时长（秒） */
    public static final float WAVE_MAX_DURATION = 1200.0f;

    /** 开始阶段特效时长（秒） */
    public static final float START_DURATION = 6.4f;

    /** 生成间隔（tick数） */
    public static final int SPAWN_INTERVAL_TICKS = 20;

    // ======================== 枚举 ========================

    /**
     * 敌人的生成方向
     * 从选定方向的区域边缘生成
     */
    public enum Direction {
        WEST(-1, 0),
        EAST(1, 0),
        NORTH(0, -1),
        SOUTH(0, 1);

        /** X轴方向系数 */
        public final int x;

        /** Z轴方向系数 */
        public final int z;

        /**
         * 构造函数
         * @param x X轴方向系数
         * @param z Z轴方向系数
         */
        Direction(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    // ======================== 构造函数 ========================

    /**
     * 构造函数
     * 
     * @param playerUUID 玩家UUID
     * @param level 世界
     * @param centerPos 中心位置
     * @param type 入侵类型
     * @param totalWaves 总波次
     * @param difficultyMultiplier 难度倍率
     */
    public ActiveInvasion(UUID playerUUID, Level level, BlockPos centerPos,
                          InvasionType type, int totalWaves, double difficultyMultiplier) {
        this.playerUUID = playerUUID;
        this.level = level;
        this.centerPos = centerPos;
        this.type = type;
        this.totalWaves = totalWaves;
        this.difficultyMultiplier = difficultyMultiplier;
        this.completed = false;
        this.defeated = false;

        Player player = getPlayer();
        this.playerName = player != null ? player.getName().getString() : "Unknown";

        this.waveInfo = new ServerBossEvent(
            Component.literal(""),
            BossEvent.BossBarColor.WHITE,
            BossEvent.BossBarOverlay.NOTCHED_10
        );
        this.waveInfo.setVisible(false);

        this.bossInfo = new ServerBossEvent(
            Component.literal(""),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.NOTCHED_6
        );
        this.bossInfo.setVisible(false);
    }

    // ======================== 状态管理 ========================

    /**
     * 设置状态并更新计时器
     * 
     * @param state 目标状态
     * @param durationLeft 持续时间（秒）
     */
    public void setState(Phase.State state, float durationLeft) {
        this.phase.state = state;
        this.phase.ticksLeft = (int) (durationLeft * 20.0f);
        this.phase.ticksTotal = Math.max(this.phase.ticksLeft, 1);
    }

    // ======================== 核心方法 ========================

    /**
     * Tick更新
     */
    public void tick() {
        if (completed) {
            return;
        }

        Player player = getPlayer();

        if (player == null) {
            if (!playerOffline) {
                playerOffline = true;
            }
            return;
        }

        if (playerOffline) {
            playerOffline = false;
        }

        if (player.isDeadOrDying()) {
            defeated = true;
            completed = true;
            return;
        }

        // 状态机计时
        if (phase.ticksLeft > 0) {
            phase.ticksLeft--;
        }

        // 更新Boss血条
        updateBossBars();

        // 更新参与者
        updateParticipants();
    }

    /**
     * 是否应该生成下一波次
     * 在WAVE_ONGOING状态且所有怪物已被击杀时返回true
     * 
     * @return 是否生成下一波
     */
    public boolean shouldStartNextWave() {
        return phase.state == Phase.State.UNDEAD_DEFEATED && !isLastWave();
    }

    /**
     * 是否已完成所有波次
     * 
     * @return 是否最后一波
     */
    public boolean isLastWave() {
        return currentWave >= totalWaves;
    }

    /**
     * 开始下一波次
     */
    public void startNextWave() {
        currentWave++;
        mobsLeft.clear();
        boss = null;
        spawnTickCounter = 0;
        isProcessingWave = false;
    }

    /**
     * 添加敌人到追踪列表
     * 
     * @param enemy 敌人实体
     */
    public void addEnemy(LivingEntity enemy) {
        // 兼容旧接口：将实体添加到mobsLeft
        MobInfo mobInfo = new MobInfo(enemy.getType(), enemy.blockPosition(), false);
        mobInfo.uuid = enemy.getUUID();
        mobsLeft.add(mobInfo);
    }

    /**
     * 判断实体是否为入侵敌人
     * 
     * @param entity 实体
     * @return 是否为入侵敌人
     */
    public boolean isInvasionEnemy(LivingEntity entity) {
        return mobsLeft.stream().anyMatch(m -> m.uuid != null && m.uuid.equals(entity.getUUID()));
    }

    /**
     * 敌人死亡处理
     * 
     * @param enemy 死亡的敌人
     */
    public void onEnemyDeath(LivingEntity enemy) {
        mobsLeft.removeIf(m -> m.uuid != null && m.uuid.equals(enemy.getUUID()));
    }

    /**
     * 简化版敌人死亡处理
     */
    public void onEnemyDeath() {
        // 简化版：移除第一个未指定UUID的敌人
        mobsLeft.removeIf(m -> m.uuid == null);
    }

    /**
     * 更新Boss血条
     */
    private void updateBossBars() {
        switch (phase.state) {
            case STARTED -> {
                waveInfo.setProgress(0.0f);
                bossInfo.setProgress(0.0f);
            }
            case WAVE_PREPARING -> {
                waveInfo.setProgress(phase.getRatio());
                bossInfo.setProgress(0.0f);
            }
            case WAVE_ONGOING -> {
                waveInfo.setProgress(getHealthRatioLeft());
                // 检查BOSS是否还活着，死了就隐藏血条
                if (boss != null && boss instanceof LivingEntity living && living.isAlive()) {
                    bossInfo.setProgress(getBossHealthRatioLeft());
                    if (!bossInfo.isVisible()) {
                        bossInfo.setVisible(true);
                    }
                } else {
                    bossInfo.setProgress(0.0f);
                    bossInfo.setVisible(false);
                }
            }
            case UNDEAD_DEFEATED -> {
                waveInfo.setProgress(0.0f);
                bossInfo.setProgress(0.0f);
                bossInfo.setVisible(false);
            }
            case UNDEAD_WON -> {
                waveInfo.setProgress(1.0f);
                bossInfo.setProgress(0.0f);
                bossInfo.setVisible(false);
            }
            default -> {
                waveInfo.setProgress(0.0f);
                bossInfo.setProgress(0.0f);
                bossInfo.setVisible(false);
            }
        }
    }

    /**
     * 更新参与者列表
     */
    private void updateParticipants() {
        if (phase.state == Phase.State.FINISHED) {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 检查范围内的玩家是否已加入
        for (ServerPlayer player : serverLevel.players()) {
            if (!participants.contains(player) && isInRange(player)) {
                participants.add(player);
                waveInfo.addPlayer(player);
                bossInfo.addPlayer(player);
            }
        }

        // 移除离开范围的玩家
        participants.removeIf(player -> {
            if (!player.isAlive() || !isInRange(player)) {
                waveInfo.removePlayer(player);
                bossInfo.removePlayer(player);
                return true;
            }
            return false;
        });
    }

    /**
     * 检查玩家是否在入侵范围内
     * 
     * @param player 玩家
     * @return 是否在范围内
     */
    public boolean isInRange(ServerPlayer player) {
        return player.distanceToSqr(centerPos.getX(), centerPos.getY(), centerPos.getZ())
            <= 70 * 70;
    }

    /**
     * 设置波次血条文本
     * 
     * @param component 文本组件
     */
    public void setWaveInfoText(Component component) {
        waveInfo.setName(component);
    }

    /**
     * 设置BOSS血条文本
     * 
     * @param component 文本组件
     */
    public void setBossInfoText(Component component) {
        bossInfo.setName(component.copy().withStyle(style -> style.withColor(
            net.minecraft.ChatFormatting.RED
        )));
    }

    /**
     * 显示Boss血条
     */
    public void showBossBar() {
        bossInfo.setVisible(true);
    }

    /**
     * 隐藏Boss血条
     */
    public void hideBossBar() {
        bossInfo.setVisible(false);
    }

    /**
     * 清理所有Boss血条（完成时调用）
     */
    public void removeAllBossBars() {
        waveInfo.removeAllPlayers();
        waveInfo.setVisible(false);
        bossInfo.removeAllPlayers();
        bossInfo.setVisible(false);
    }

    /**
     * 设置波次血条可见性
     * 
     * @param visible 是否可见
     */
    public void setWaveInfoVisible(boolean visible) {
        waveInfo.setVisible(visible);
    }

    // ======================== 血量计算 ========================

    /**
     * 获取当前波次剩余血量比例
     * 
     * @return 0.0 ~ 1.0
     */
    private float getHealthRatioLeft() {
        if (hasNooneSpawnedYet()) {
            return 1.0f;
        }

        float healthLeft = 0.0f;
        float healthTotal = Math.max(phase.healthTotal, 1.0f);

        if (!(level instanceof ServerLevel serverLevel)) {
            return 0.0f;
        }

        for (MobInfo mobInfo : mobsLeft) {
            healthLeft += mobInfo.getHealth(serverLevel);
        }

        return Mth.clamp(healthLeft / healthTotal, 0.0f, 1.0f);
    }

    /**
     * 是否还没有任何怪物被生成
     * 
     * @return 是否都没有UUID
     */
    private boolean hasNooneSpawnedYet() {
        return mobsLeft.stream().allMatch(m -> m.uuid == null);
    }

    /**
     * 获取BOSS剩余血量比例
     * 
     * @return 0.0 ~ 1.0
     */
    private float getBossHealthRatioLeft() {
        return boss instanceof LivingEntity living
            ? Mth.clamp(living.getHealth() / living.getMaxHealth(), 0.0f, 1.0f)
            : 0.0f;
    }

    /**
     * 计算指定方向上的生成偏移
     * 从选定方向的区域边缘（半径-15格）生成，并沿垂直轴随机散布
     * 
     * @param areaRadius 区域半径
     * @return 偏移向量（相对centerPos）
     */
    public net.minecraft.world.phys.Vec3 buildSpawnOffset(int areaRadius) {
        int spawnRadius = areaRadius - 15;
        int dx = direction.z != 0 ? 24 : 8;  // Z轴方向时沿X轴散布
        int dz = direction.x != 0 ? 24 : 8;  // X轴方向时沿Z轴散布
        java.util.Random random = new java.util.Random();

        return new net.minecraft.world.phys.Vec3(
            direction.x * spawnRadius + (random.nextDouble() * 2.0 - 1.0) * dx,
            0,
            direction.z * spawnRadius + (random.nextDouble() * 2.0 - 1.0) * dz
        );
    }

    // ======================== 波次切换管理 ========================

    /**
     * 检查是否应该结束当前波次（所有怪物已生成且被消灭）
     * 
     * @return 是否所有怪物已死亡
     */
    public boolean areAllMobsDead() {
        if (!isProcessingWave) {
            return false;
        }

        // 检查是否所有怪物都已生成
        boolean allSpawned = mobsLeft.stream().allMatch(m -> m.uuid != null);
        if (!allSpawned) {
            return false;
        }

        // 检查是否所有已生成的怪物都已死亡
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        return mobsLeft.stream().noneMatch(m -> {
            Entity entity = serverLevel.getEntity(m.uuid);
            return entity instanceof LivingEntity living && living.isAlive();
        });
    }

    /**
     * 获取下一个待生成的怪物信息
     * 
     * @return 下一个待生成的怪物，如果没有则返回null
     */
    public MobInfo getNextMobToSpawn() {
        return mobsLeft.stream()
            .filter(m -> m.uuid == null)
            .findFirst()
            .orElse(null);
    }

    /**
     * 是否还有待生成的怪物
     * 
     * @return 是否有
     */
    public boolean hasMobsToSpawn() {
        return mobsLeft.stream().anyMatch(m -> m.uuid == null);
    }

    /**
     * 获取生成计时器当前值
     * 
     * @return 计时器tick数
     */
    public int getSpawnTickCounter() {
        return spawnTickCounter;
    }

    /**
     * 增加生成计时器
     */
    public void incrementSpawnTickCounter() {
        this.spawnTickCounter++;
    }

    /**
     * 重置生成计时器
     */
    public void resetSpawnTickCounter() {
        this.spawnTickCounter = 0;
    }

    /**
     * 检查生成计时器是否到达间隔
     * 
     * @return 是否到达
     */
    public boolean isSpawnReady() {
        return spawnTickCounter >= SPAWN_INTERVAL_TICKS;
    }

    // ======================== Getter方法 ========================

    /**
     * 获取玩家
     * 
     * @return 玩家，或null
     */
    public Player getPlayer() {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getPlayerByUUID(playerUUID);
        }
        return null;
    }

    /**
     * 获取玩家名称
     * 
     * @return 玩家名称
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * 获取世界
     * 
     * @return 世界
     */
    public Level getLevel() {
        return level;
    }

    /**
     * 获取中心位置
     * 
     * @return 中心位置
     */
    public BlockPos getCenterPos() {
        return centerPos;
    }

    /**
     * 获取入侵类型
     * 
     * @return 入侵类型
     */
    public InvasionType getType() {
        return type;
    }

    /**
     * 获取总波次数量
     * 
     * @return 总波次
     */
    public int getTotalWaves() {
        return totalWaves;
    }

    /**
     * 获取当前波次
     * 
     * @return 当前波次
     */
    public int getCurrentWave() {
        return currentWave;
    }

    /**
     * 获取难度倍率
     * 
     * @return 难度倍率
     */
    public double getDifficultyMultiplier() {
        return difficultyMultiplier;
    }

    /**
     * 是否完成
     * 
     * @return 是否完成
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * 设置完成状态
     * 
     * @param completed 是否完成
     */
    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    /**
     * 是否胜利
     * 
     * @return 是否胜利
     */
    public boolean isVictory() {
        return phase.state == Phase.State.FINISHED && !defeated;
    }

    /**
     * 是否失败
     * 
     * @return 是否失败
     */
    public boolean isDefeated() {
        return defeated;
    }

    /**
     * 设置失败状态
     * 
     * @param defeated 是否失败
     */
    public void setDefeated(boolean defeated) {
        this.defeated = defeated;
    }

    /**
     * 是否玩家离线
     * 
     * @return 是否离线
     */
    public boolean isPlayerOffline() {
        return playerOffline;
    }

    /**
     * 获取当前波次进度文本
     * 
     * @return 进度字符串
     */
    public String getWaveProgress() {
        return String.format("%d/%d",
            mobsLeft.stream().filter(m -> m.uuid == null).count(),
            mobsLeft.size());
    }

    /**
     * 获取剩余敌人数量
     * 
     * @return 剩余敌人数量
     */
    public int getRemainingEnemies() {
        return (int) mobsLeft.stream()
            .filter(m -> {
                if (!(level instanceof ServerLevel serverLevel)) return false;
                Entity entity = serverLevel.getEntity(m.uuid);
                return entity instanceof LivingEntity && entity.isAlive();
            })
            .count();
    }

    /**
     * 获取玩家UUID
     * 
     * @return 玩家UUID
     */
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    // ======================== 内部类 ========================

    /**
     * 入侵阶段
     * 管理状态、计时和血量
     */
    public static class Phase {
        /** 当前状态 */
        public State state = State.CREATED;
        /** 剩余tick数 */
        public int ticksLeft = 0;
        /** 总tick数 */
        public int ticksTotal = 1;
        /** 波次总血量（用于进度条） */
        public int healthTotal = 0;

        /**
         * 获取进度比例
         * 准备阶段：倒计时进度
         * 战斗阶段：剩余血量比例
         * 
         * @return 0.0 ~ 1.0
         */
        public float getRatio() {
            return Mth.clamp(1.0f - (float) ticksLeft / ticksTotal, 0.0f, 1.0f);
        }

        /**
         * 获取已过去的tick数
         * 
         * @return 已过去的tick数
         */
        public float getTicksActive() {
            return ticksTotal - ticksLeft;
        }

        /**
         * 入侵阶段状态枚举
         */
        public enum State {
            /** 刚创建 */
            CREATED,
            /** 已开始（闪电特效） */
            STARTED,
            /** 波次准备中（倒计时） */
            WAVE_PREPARING,
            /** 波次进行中（战斗） */
            WAVE_ONGOING,
            /** 亡灵被击败 */
            UNDEAD_DEFEATED,
            /** 亡灵获胜（超时） */
            UNDEAD_WON,
            /** 已完成 */
            FINISHED
        }
    }

    /**
     * 怪物信息
     * 用于追踪每个怪物的类型、生成位置、装备和状态
     */
    public static class MobInfo {
        /** 实体类型 */
        public EntityType<?> type;
        /** 装备战利品表位置 */
        public ResourceLocation equipment;
        /** 生成位置 */
        public BlockPos position;
        /** 是否为BOSS */
        public boolean isBoss = false;
        /** 实体UUID（null表示尚未生成） */
        public UUID uuid = null;

        /**
         * 构造函数
         * 
         * @param type 实体类型
         * @param position 生成位置
         * @param isBoss 是否为BOSS
         */
        public MobInfo(EntityType<?> type, BlockPos position, boolean isBoss) {
            this.type = type;
            this.position = position;
            this.isBoss = isBoss;
        }

        /**
         * 获取对应的实体
         * 
         * @param level 服务端世界
         * @return 实体，或null
         */
        public Entity toEntity(ServerLevel level) {
            if (uuid == null) {
                return null;
            }
            Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity living && living.deathTime >= 20) {
                return null;
            }
            return entity;
        }

        /**
         * 获取当前血量
         * 
         * @param level 服务端世界
         * @return 当前血量
         */
        public float getHealth(ServerLevel level) {
            return toEntity(level) instanceof LivingEntity living ? living.getHealth() : 0.0f;
        }

        /**
         * 获取最大血量
         * 
         * @param level 服务端世界
         * @return 最大血量
         */
        public float getMaxHealth(ServerLevel level) {
            return toEntity(level) instanceof LivingEntity living ? living.getMaxHealth() : 0.0f;
        }
    }
}
