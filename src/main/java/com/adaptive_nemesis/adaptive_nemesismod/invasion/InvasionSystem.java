package com.adaptive_nemesis.adaptive_nemesismod.invasion;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.data.WorldStageDataConfig;
import com.adaptive_nemesis.adaptive_nemesismod.data.WorldStageDataLoader;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EnchantmentScalingHandler;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.WorldStageManager;
import com.adaptive_nemesis.adaptive_nemesismod.kubejs.KubeJSEventTrigger;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthData;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * 亡灵入侵核心系统
 * 
 * 参考Majrusz's Progressive Difficulty模组的Undead Army实现
 * 使用状态机管理和Boss血条UI，逐步生成敌人：
 * 
 * 状态机：CREATED → STARTED(6.4s闪电特效) → WAVE_PREPARING(10s倒计时) → WAVE_ONGOING(战斗) → UNDEAD_DEFEATED → FINISHED
 *                                                                                  → UNDEAD_WON(1200s超时) → FINISHED
 * 
 * Boss血条 UI：
 * - 白色(NOTCHED_10)波次进度条：准备阶段显示倒计时，战斗阶段显示剩余血量
 * - 红色(NOTCHED_6)Boss血条：显示Boss血量（如有）
 * 
 * 生成机制：每秒从选定方向边缘生成1只敌人
 * 
 * @author Adaptive Nemesis Team
 * @version 3.0.0
 */
public class InvasionSystem {

    private static InvasionSystem INSTANCE;

    public static InvasionSystem getInstance() {
        return INSTANCE;
    }

    /** 活动中的入侵事件列表 */
    private final Map<UUID, ActiveInvasion> activeInvasions = new HashMap<>();

    /** 玩家击杀亡灵生物计数 */
    private final Map<UUID, Integer> playerUndeadKills = new HashMap<>();

    /** 是否已完成首次入侵 */
    private final Map<UUID, Boolean> hasCompletedFirstInvasion = new HashMap<>();


    /** 玩家入侵冷却时间记录（UUID -> 冷却结束时间戳） */
    private final Map<UUID, Long> playerInvasionCooldowns = new HashMap<>();

    /** 玩家进度持久化实例（ServerStartedEvent 时挂载 overworld SavedData，F-15） */
    private InvasionProgressSavedData progressSavedData;

    /** 涉水敌人援助计时器（每 20 tick 扫描一次） */
    private int waterAssistTimer = 0;

    /** 首次入侵所需击杀数 */
    private static final int FIRST_INVASION_KILLS_REQUIRED = 25;

    /** 后续入侵所需击杀数 */
    private static final int SUBSEQUENT_INVASION_KILLS_REQUIRED = 100;

    /** 入侵区域半径（生成位置缺省半径；数据包 spawn_distance 优先，F-12） */
    public static final int INVASION_AREA_RADIUS = 70;

    // ======================== 初始化 ========================

    public InvasionSystem() {
        INSTANCE = this;
        NeoForge.EVENT_BUS.register(this);
    }

    // ======================== Tick处理 ========================

    /**
     * 服务端Tick事件
     * 每tick处理所有活动的入侵事件
     */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (!Config.INVASION.ENABLE_INVASION.get()) {
            return;
        }
        processActiveInvasions();
    }

    /**
     * 服务器启动事件（F-15）
     * 挂载 overworld 的玩家进度 SavedData，并把持久化数据读回内存
     *
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        progressSavedData = InvasionProgressSavedData.get(event.getServer());
        loadProgressFromSavedData();
    }

    /**
     * 服务器停止事件（F-15）
     * 全量落盘玩家进度；活动入侵状态机不持久化，清理内存态与 Boss 血条
     *
     * @param event 服务器停止事件
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        saveAllProgress(event.getServer());
        for (ActiveInvasion invasion : activeInvasions.values()) {
            invasion.removeAllBossBars();
        }
        activeInvasions.clear();
    }

    /**
     * 从 SavedData 读回玩家进度（F-15）
     * 覆盖内存中的击杀计数、首侵标记与冷却记录
     */
    private void loadProgressFromSavedData() {
        if (progressSavedData == null) {
            return;
        }
        playerUndeadKills.clear();
        playerUndeadKills.putAll(progressSavedData.getUndeadKills());
        hasCompletedFirstInvasion.clear();
        for (UUID playerId : progressSavedData.getFirstInvasionDone()) {
            hasCompletedFirstInvasion.put(playerId, true);
        }
        playerInvasionCooldowns.clear();
        playerInvasionCooldowns.putAll(progressSavedData.getCooldowns());
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] 玩家进度已从 SavedData 读回内存");
        }
    }

    /**
     * 停服前全量落盘玩家进度（F-15）
     * SavedData 未挂载时（异常路径）按需补挂
     *
     * @param server 服务端实例
     */
    private void saveAllProgress(MinecraftServer server) {
        if (progressSavedData == null) {
            progressSavedData = InvasionProgressSavedData.get(server);
        }
        Set<UUID> trackedPlayers = new HashSet<>();
        trackedPlayers.addAll(playerUndeadKills.keySet());
        trackedPlayers.addAll(hasCompletedFirstInvasion.keySet());
        trackedPlayers.addAll(playerInvasionCooldowns.keySet());
        for (UUID playerId : trackedPlayers) {
            writePlayerProgress(playerId);
        }
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] 停服前已全量落盘玩家进度：{} 名玩家", trackedPlayers.size());
        }
    }

    /**
     * 把单个玩家的进度写入 SavedData（F-15）
     * SavedData 未挂载时跳过（停服时会全量兜底落盘）
     *
     * @param playerId 玩家 UUID
     */
    private void syncPlayerProgress(UUID playerId) {
        if (progressSavedData != null) {
            writePlayerProgress(playerId);
        }
    }

    /**
     * 将内存中该玩家的三项进度写入 SavedData 并标记脏（F-15）
     *
     * @param playerId 玩家 UUID
     */
    private void writePlayerProgress(UUID playerId) {
        progressSavedData.writePlayer(
            playerId,
            playerUndeadKills.get(playerId),
            hasCompletedFirstInvasion.get(playerId),
            playerInvasionCooldowns.get(playerId)
        );
    }

    /**
     * 处理所有活动入侵事件的状态机
     *
     * 状态转换逻辑：
     * - CREATED → STARTED：刚触发时
     * - STARTED → WAVE_PREPARING：6.4秒后
     * - WAVE_PREPARING → WAVE_ONGOING：10秒后，开始每秒生成怪物
     * - WAVE_ONGOING → UNDEAD_DEFEATED：怪物全部被消灭
     * - WAVE_ONGOING → UNDEAD_WON：波次超时
     * - UNDEAD_DEFEATED → WAVE_PREPARING：非最后一波，准备下一波
     * - UNDEAD_DEFEATED → FINISHED：最后一波，胜利
     * - UNDEAD_WON → FINISHED：失败
     * - tick 内检测到玩家死亡：立即按失败结算（F-08）
     * - 玩家连续离线超过阈值：判失败并走失败清理路径（F-11）
     */
    private void processActiveInvasions() {
        Iterator<Map.Entry<UUID, ActiveInvasion>> iterator = activeInvasions.entrySet().iterator();
        while (iterator.hasNext()) {
            ActiveInvasion invasion = iterator.next().getValue();

            if (invasion.isCompleted()) {
                removeInvasion(invasion, iterator);
                continue;
            }

            invasion.tick();

            if (handlePostTickAbort(invasion, iterator)) {
                continue;
            }

            // 状态机转换
            switch (invasion.phase.state) {
                case CREATED -> handleCreatedState(invasion);
                case STARTED -> {
                    // STARTED结束后进入WAVE_PREPARING
                    if (invasion.phase.ticksLeft <= 0) {
                        startWavePreparation(invasion);
                    }
                }
                case WAVE_PREPARING -> {
                    // WAVE_PREPARING结束后进入WAVE_ONGOING
                    if (invasion.phase.ticksLeft <= 0) {
                        startWaveOngoing(invasion);
                    }
                }
                case WAVE_ONGOING -> handleWaveOngoing(invasion);
                case UNDEAD_DEFEATED -> handleWaveDefeated(invasion);
                case UNDEAD_WON -> handleWaveTimeout(invasion);
                case FINISHED -> {
                    invasion.removeAllBossBars();
                    iterator.remove();
                }
            }
        }

        // 每 20 tick 一次：为路径涉水的入侵敌人补穿冰霜行者并冻结脚下水面
        if (++waterAssistTimer >= 20) {
            waterAssistTimer = 0;
            assistWaterloggedMobs();
        }
    }

    /**
     * tick 返回后的中止检查（F-08/F-11）
     * 处理两类直接终结路径：玩家死亡结算、离线超时判失败；命中时移除入侵并返回 true
     *
     * @param invasion 活动入侵
     * @param iterator 活动入侵迭代器
     * @return 本轮已终结并移除该入侵时返回 true
     */
    private boolean handlePostTickAbort(ActiveInvasion invasion, Iterator<Map.Entry<UUID, ActiveInvasion>> iterator) {
        // F-08：tick 返回后立即检查，玩家死亡等直接完成路径按失败结算并跳过本轮状态机
        if (invasion.isCompleted()) {
            concludeInvasion(invasion, !invasion.isDefeated());
            removeInvasion(invasion, iterator);
            return true;
        }

        // F-11：目标玩家连续离线超过时限，判失败并走失败清理路径
        if (invasion.getOfflineTicks() >= InvasionFixConfig.OFFLINE_ABORT_TICKS) {
            abortOfflineInvasion(invasion);
            removeInvasion(invasion, iterator);
            return true;
        }
        return false;
    }

    /**
     * 离线超时的失败结算（F-11）
     * 标记失败、广播失败消息并统一走失败清理
     *
     * @param invasion 活动入侵
     */
    private void abortOfflineInvasion(ActiveInvasion invasion) {
        AdaptiveNemesisMod.LOGGER.info("[入侵] 玩家 {} 离线超过 {} tick，入侵按失败结算",
            invasion.getPlayerName(), InvasionFixConfig.OFFLINE_ABORT_TICKS);
        invasion.setDefeated(true);
        invasion.setState(ActiveInvasion.Phase.State.FINISHED, 0);
        notifyAllPlayersInArea(invasion, Component.translatable(
            "adaptive_nemesis.invasion.won"
        ).withStyle(ChatFormatting.DARK_RED));
        concludeInvasion(invasion, false);
    }

    /**
     * 移除活动入侵并清理 Boss 血条
     *
     * @param invasion 活动入侵
     * @param iterator 活动入侵迭代器
     */
    private void removeInvasion(ActiveInvasion invasion, Iterator<Map.Entry<UUID, ActiveInvasion>> iterator) {
        invasion.removeAllBossBars();
        iterator.remove();
    }

    /**
     * 处理 CREATED 状态：触发时立即进入 STARTED 并播放闪电特效
     *
     * @param invasion 活动入侵
     */
    private void handleCreatedState(ActiveInvasion invasion) {
        invasion.setState(ActiveInvasion.Phase.State.STARTED, ActiveInvasion.START_DURATION);
        invasion.setWaveInfoText(Component.translatable("adaptive_nemesis.invasion.approaching").withStyle(ChatFormatting.WHITE));
        invasion.setWaveInfoVisible(true);
        triggerLightningEffect(invasion);
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵状态] {} -> STARTED (6.4秒)", invasion.getPlayerName());
        }
    }

    /**
     * 处理 WAVE_ONGOING 状态：每秒生成怪物，并检查波次超时与清空
     *
     * @param invasion 活动入侵
     */
    private void handleWaveOngoing(ActiveInvasion invasion) {
        // 每秒生成一只怪物
        if (invasion.hasMobsToSpawn()) {
            invasion.incrementSpawnTickCounter();
            if (invasion.isSpawnReady()) {
                spawnOneMob(invasion);
                invasion.resetSpawnTickCounter();
            }
        }

        // 检查波次是否超时
        float elapsedTicks = invasion.phase.getTicksActive();
        float maxTicks = ActiveInvasion.WAVE_MAX_DURATION * 20.0f;
        if (elapsedTicks >= maxTicks) {
            invasion.setState(ActiveInvasion.Phase.State.UNDEAD_WON, 0);
            invasion.setWaveInfoText(Component.translatable("adaptive_nemesis.invasion.won").withStyle(ChatFormatting.DARK_RED));
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug("[入侵状态] {} 波次超时", invasion.getPlayerName());
            }
            return;
        }

        // 检查是否所有怪物已死亡
        if (invasion.areAllMobsDead()) {
            invasion.setState(ActiveInvasion.Phase.State.UNDEAD_DEFEATED, 0);
            invasion.setWaveInfoText(Component.translatable(
                "adaptive_nemesis.invasion.wave_cleared", getRomanNumeral(invasion.currentWave)
            ).withStyle(ChatFormatting.GREEN));

            notifyAllPlayersInArea(invasion, Component.translatable(
                "adaptive_nemesis.invasion.wave_cleared_broadcast", getRomanNumeral(invasion.currentWave)
            ).withStyle(ChatFormatting.GREEN));

            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug("[入侵状态] {} 波次{}完成", invasion.getPlayerName(), invasion.currentWave);
            }
        }
    }

    /**
     * 处理 UNDEAD_DEFEATED 状态：非最后一波准备下一波，最后一波结算胜利（F-08 统一结算入口）
     *
     * @param invasion 活动入侵
     */
    private void handleWaveDefeated(ActiveInvasion invasion) {
        // 非最后一波：继续下一波，startWavePreparation内部已调用startNextWave
        if (!invasion.isLastWave()) {
            startWavePreparation(invasion);
            return;
        }

        // 最后一波：胜利
        invasion.setState(ActiveInvasion.Phase.State.FINISHED, 0);
        invasion.setCompleted(true);
        invasion.setWaveInfoText(Component.translatable("adaptive_nemesis.invasion.defeated").withStyle(ChatFormatting.GREEN));
        concludeInvasion(invasion, true);
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵状态] {} 全部波次完成，胜利！", invasion.getPlayerName());
        }
    }

    /**
     * 处理 UNDEAD_WON 状态：波次超时，统一按失败结算（F-08）
     *
     * @param invasion 活动入侵
     */
    private void handleWaveTimeout(ActiveInvasion invasion) {
        invasion.setState(ActiveInvasion.Phase.State.FINISHED, 0);
        invasion.setCompleted(true);
        invasion.setDefeated(true);

        notifyAllPlayersInArea(invasion, Component.translatable(
            "adaptive_nemesis.invasion.won"
        ).withStyle(ChatFormatting.DARK_RED));

        concludeInvasion(invasion, false);
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵状态] {} 超时失败", invasion.getPlayerName());
        }
    }

    /**
     * 统一结算入侵结果（F-08）
     * 胜利路径：通知、清除冷却、记录首侵标记、触发 KubeJS 结束事件并发放奖励
     * 失败路径：按配置清理存活入侵怪、触发 KubeJS 结束事件（不发奖励）
     * 通过 endEventFired 标志保证死亡、超时、离线超时等多条完成路径只结算一次
     *
     * @param invasion 活动入侵
     * @param victory 是否胜利
     */
    private void concludeInvasion(ActiveInvasion invasion, boolean victory) {
        if (invasion.isEndEventFired()) {
            return;
        }
        invasion.markEndEventFired();

        if (victory) {
            handleInvasionVictory(invasion);
            hasCompletedFirstInvasion.put(invasion.getPlayerUUID(), true);
            syncPlayerProgress(invasion.getPlayerUUID());
            triggerInvasionEndEvent(invasion, true);
        } else {
            clearMobsOnFailure(invasion);
            triggerInvasionEndEvent(invasion, false);
        }
    }

    /**
     * 失败或终止时清理存活入侵怪（F-08）
     * 受配置开关控制：逐个 discard 追踪到的存活实体，不产生掉落与死亡消息
     *
     * @param invasion 活动入侵
     */
    private void clearMobsOnFailure(ActiveInvasion invasion) {
        if (!InvasionFixConfig.CLEAR_MOBS_ON_FAILURE) {
            return;
        }
        if (!(invasion.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        int cleared = 0;
        for (ActiveInvasion.MobInfo mobInfo : invasion.mobsLeft) {
            if (mobInfo.uuid == null) {
                continue;
            }
            if (serverLevel.getEntity(mobInfo.uuid) instanceof LivingEntity living && living.isAlive()) {
                living.discard();
                cleared++;
            }
        }
        invasion.mobsLeft.clear();
        if (cleared > 0 && Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] 失败结算：已清理 {} 只存活入侵怪", cleared);
        }
    }

    // ======================== 波次管理 ========================

    /**
     * 开始波次准备阶段
     * 设置10秒倒计时，生成MobInfo列表并按数据包权重随机排序（F-12）
     *
     * @param invasion 活动入侵
     */
    private void startWavePreparation(ActiveInvasion invasion) {
        invasion.startNextWave();
        invasion.setState(ActiveInvasion.Phase.State.WAVE_PREPARING, ActiveInvasion.PREPARING_DURATION);

        invasion.setWaveInfoText(Component.translatable(
            "adaptive_nemesis.invasion.wave_preparing", getRomanNumeral(invasion.currentWave)
        ).withStyle(ChatFormatting.WHITE));

        // 生成MobInfo列表
        List<EnemySpawnConfig> configs = getWaveEnemyConfig(invasion.currentWave, invasion.getType());
        invasion.boss = null;
        invasion.hideBossBar();

        int totalHealth = 0;
        List<ActiveInvasion.MobInfo> pool = new ArrayList<>();
        for (EnemySpawnConfig config : configs) {
            for (int i = 0; i < config.count(); i++) {
                pool.add(buildMobInfo(config));
                totalHealth += getBaseHealth(config.entityType());
            }
        }

        // F-12：按 EnemyData.weight 加权随机决定波内生成顺序
        orderMobsByWeight(invasion, pool);
        invasion.mobsLeft.addAll(pool);
        invasion.phase.healthTotal = totalHealth;

        // 每波随机选择方向（F-49：使用世界随机源）
        ActiveInvasion.Direction[] dirs = ActiveInvasion.Direction.values();
        invasion.direction = dirs[invasion.getLevel().random.nextInt(dirs.length)];

        triggerLightningEffect(invasion);

        notifyAllPlayersInArea(invasion, Component.translatable(
            "adaptive_nemesis.invasion.wave_starting", getRomanNumeral(invasion.currentWave)
        ).withStyle(ChatFormatting.GOLD));

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] {} 第{}波准备中，方向={}，怪物数={}",
                invasion.getPlayerName(), invasion.currentWave, invasion.direction, invasion.mobsLeft.size());
        }
    }

    /**
     * 根据波次敌人配置构建单个待生成怪物信息（F-12）
     * 携带数据包的血量/攻击倍率、波次难度倍率与加权选择权重
     *
     * @param config 波次敌人配置
     * @return 怪物信息
     */
    private ActiveInvasion.MobInfo buildMobInfo(EnemySpawnConfig config) {
        ActiveInvasion.MobInfo mobInfo = new ActiveInvasion.MobInfo(
            config.entityType(),
            null,
            config.isBoss()
        );
        mobInfo.equipment = config.equipmentLootTable();
        mobInfo.effects = config.effects() != null ? new ArrayList<>(config.effects()) : new ArrayList<>();
        mobInfo.glowing = config.glowing();
        mobInfo.frostWalker = config.frostWalker();
        mobInfo.customNameKey = config.customNameKey();
        mobInfo.healthMultiplier = config.healthMultiplier();
        mobInfo.damageMultiplier = config.damageMultiplier();
        mobInfo.waveMultiplier = config.waveMultiplier();
        mobInfo.weight = config.weight();
        return mobInfo;
    }

    /**
     * 按 weight 加权随机打乱生成队列（F-12）
     * 权重越高的敌人类型越早出场；权重小于 1 时按 1 处理
     *
     * @param invasion 活动入侵（提供世界随机源）
     * @param pool 待排序的怪物信息列表（原位重排）
     */
    private void orderMobsByWeight(ActiveInvasion invasion, List<ActiveInvasion.MobInfo> pool) {
        List<ActiveInvasion.MobInfo> remaining = new ArrayList<>(pool);
        pool.clear();
        while (!remaining.isEmpty()) {
            int totalWeight = 0;
            for (ActiveInvasion.MobInfo mobInfo : remaining) {
                totalWeight += Math.max(1, mobInfo.weight);
            }
            int roll = invasion.getLevel().random.nextInt(totalWeight);
            ActiveInvasion.MobInfo selected = null;
            for (ActiveInvasion.MobInfo mobInfo : remaining) {
                roll -= Math.max(1, mobInfo.weight);
                if (roll < 0) {
                    selected = mobInfo;
                    break;
                }
            }
            if (selected == null) {
                selected = remaining.get(remaining.size() - 1);
            }
            remaining.remove(selected);
            pool.add(selected);
        }
    }

    /**
     * 开始波次战斗阶段
     * 逐步每秒生成一只怪物
     */
    private void startWaveOngoing(ActiveInvasion invasion) {
        invasion.setState(ActiveInvasion.Phase.State.WAVE_ONGOING, ActiveInvasion.WAVE_MAX_DURATION);
        invasion.isProcessingWave = true;
        invasion.resetSpawnTickCounter();

        // 触发 KubeJS 波次开始事件
        Player player = invasion.getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            KubeJSEventTrigger.triggerInvasionWaveStart(
                serverPlayer,
                invasion.getType(),
                invasion.currentWave,
                invasion.getTotalWaves(),
                invasion.getDifficultyMultiplier()
            );
        }

        invasion.setWaveInfoText(Component.translatable(
            "adaptive_nemesis.invasion.wave_ongoing", getRomanNumeral(invasion.currentWave)
        ).withStyle(ChatFormatting.WHITE));

        notifyAllPlayersInArea(invasion, Component.translatable(
            "adaptive_nemesis.invasion.wave_arrived", getRomanNumeral(invasion.currentWave)
        ).withStyle(ChatFormatting.RED));

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] {} 第{}波开始战斗", invasion.getPlayerName(), invasion.currentWave);
        }
    }

    // ======================== 怪物生成 ========================

    /**
     * 每秒生成一只怪物
     * 从mobsLeft中取下一个未生成的MobInfo，创建实体并放置到生成点（F-41 拆分）
     *
     * @param invasion 活动入侵
     */
    private void spawnOneMob(ActiveInvasion invasion) {
        ActiveInvasion.MobInfo mobInfo = invasion.getNextMobToSpawn();
        if (mobInfo == null) {
            return;
        }

        Level level = invasion.getLevel();
        BlockPos center = invasion.getCenterPos();

        // 计算生成位置（F-12：生成距离优先取数据包 spawn_distance）
        Vec3 offset = invasion.buildSpawnOffset(getSpawnDistance(invasion));
        BlockPos spawnPos = findSpawnPos(level, center, offset);
        if (spawnPos == null) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug("[入侵] 找不到有效生成位置，跳过: {}", mobInfo.type);
            }
            return;
        }

        LivingEntity enemy = createInvasionEntity(level, mobInfo, invasion, center, spawnPos);
        if (enemy == null) {
            return;
        }

        placeInvasionEnemy(invasion, mobInfo, enemy, spawnPos);
    }

    /**
     * 获取当前入侵的生成距离（F-12）
     * 数据包启用且存在对应配置时取数据包 spawn_distance，否则回退到缺省区域半径
     *
     * @param invasion 活动入侵
     * @return 生成距离（方块）
     */
    private int getSpawnDistance(ActiveInvasion invasion) {
        if (Config.INVASION.ENABLE_DATA_PACK_SUPPORT.get()) {
            InvasionData data = getDataPackInvasion(invasion.getType());
            if (data != null) {
                return data.getSpawnDistance();
            }
        }
        return INVASION_AREA_RADIUS;
    }

    /**
     * 创建入侵敌人实体并应用全部增强（F-41 拆分）
     * 失败（类型异常、非 LivingEntity）时返回 null
     *
     * @param level 世界
     * @param mobInfo 待生成的怪物信息
     * @param invasion 活动入侵
     * @param center 入侵中心位置
     * @param spawnPos 生成点
     * @return 增强后的敌人实体，失败时返回 null
     */
    private LivingEntity createInvasionEntity(Level level, ActiveInvasion.MobInfo mobInfo,
                                              ActiveInvasion invasion, BlockPos center, BlockPos spawnPos) {
        Entity entity;
        try {
            entity = mobInfo.type.create(level);
        } catch (Exception e) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug("[入侵] 创建实体失败 {}: {}", mobInfo.type, e.getMessage());
            }
            return null;
        }
        if (!(entity instanceof LivingEntity living)) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug("[入侵] 实体类型不是LivingEntity: {}", mobInfo.type);
            }
            return null;
        }

        LivingEntity enemy = living;
        mobInfo.position = spawnPos;
        mobInfo.uuid = null; // 先在设置位置后再获取UUID

        // 应用增强（F-12：波次倍率与全局倍率相乘；F-14：入口钳制下限）
        double baseMultiplier = invasion.getDifficultyMultiplier() * mobInfo.waveMultiplier;
        applyInvasionEnhancements(enemy, baseMultiplier,
            mobInfo.healthMultiplier, mobInfo.damageMultiplier);

        if (enemy instanceof Mob mob) {
            // 分配基础武器（骷髅弓、凋灵骷髅石剑等）
            equipInvasionEnemy(mob);

            // 接入动态难度：根据附近玩家强度计算装备倍率并应用装备/附魔
            if (level instanceof ServerLevel serverLevel) {
                double dynamicMultiplier = calculateDynamicDifficultyMultiplier(serverLevel, center, invasion.getDifficultyMultiplier());
                EnchantmentScalingHandler.getInstance().applyEquipmentScaling(mob, dynamicMultiplier, serverLevel);
            }

            addCustomAI(mob, center);
        }

        // 应用数据包自定义效果
        applyMobInfoEnhancements(enemy, mobInfo);

        applyWaterSurfaceFallback(level, enemy, spawnPos);
        applyGlowingEffect(enemy);
        return enemy;
    }

    /**
     * 水面生成兜底（F-41 拆分）
     * 按配置为生成在水面上方的敌人铺设临时冰面并补穿冰霜行者靴子
     *
     * @param level 世界
     * @param enemy 敌人实体
     * @param spawnPos 生成点
     */
    private void applyWaterSurfaceFallback(Level level, LivingEntity enemy, BlockPos spawnPos) {
        if (!Config.INVASION.ENABLE_FROST_WALKER_ON_WATER.get()
            || !(enemy instanceof Mob mob)
            || !isWaterSpawn(level, spawnPos)) {
            return;
        }
        preFreezeWaterSurface(level, spawnPos);
        applyFrostWalkerBoots(mob);
    }

    /**
     * 放置入侵敌人到生成点并登记追踪（F-41 拆分）
     *
     * @param invasion 活动入侵
     * @param mobInfo 怪物信息
     * @param enemy 敌人实体
     * @param spawnPos 生成点
     */
    private void placeInvasionEnemy(ActiveInvasion invasion, ActiveInvasion.MobInfo mobInfo,
                                    LivingEntity enemy, BlockPos spawnPos) {
        enemy.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        invasion.getLevel().addFreshEntity(enemy);
        mobInfo.uuid = enemy.getUUID();

        // 如果是BOSS，设置Boss血条
        if (mobInfo.isBoss) {
            invasion.boss = enemy;
            invasion.showBossBar();
            invasion.setBossInfoText(Component.translatable("adaptive_nemesis.invasion.boss_name"));
        }

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] 生成怪物: {} 于 {} (方向={})", mobInfo.type, spawnPos, invasion.direction);
        }
    }

    /**
     * 应用 MobInfo 中的自定义增强效果
     *
     * @param enemy 目标实体
     * @param mobInfo 怪物信息
     */
    private void applyMobInfoEnhancements(LivingEntity enemy, ActiveInvasion.MobInfo mobInfo) {
        // 自定义名称
        if (mobInfo.customNameKey != null && !mobInfo.customNameKey.isEmpty()) {
            enemy.setCustomName(Component.translatable(mobInfo.customNameKey));
            enemy.setCustomNameVisible(true);
        }

        // 药水效果
        for (MobEffectInstance effect : mobInfo.effects) {
            enemy.addEffect(new MobEffectInstance(effect));
        }

        // 发光
        if (mobInfo.glowing) {
            enemy.setGlowingTag(true);
        }

        // 冰霜行者：通过装备靴子实现
        if (mobInfo.frostWalker && enemy instanceof Mob mob) {
            applyFrostWalkerBoots(mob);
        }

        // 数据包自定义装备战利品表
        if (mobInfo.equipment != null && enemy instanceof Mob mob && mob.level() instanceof ServerLevel serverLevel) {
            applyEquipmentLootTable(mob, mobInfo.equipment, serverLevel);
        }
    }

    /**
     * 从战利品表生成装备并给怪物穿上
     *
     * @param mob 目标怪物
     * @param lootTableId 装备战利品表资源位置
     * @param serverLevel 服务端世界
     */
    private void applyEquipmentLootTable(Mob mob, ResourceLocation lootTableId, ServerLevel serverLevel) {
        ResourceKey<LootTable> lootKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableId);
        LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(lootKey);
        if (lootTable == LootTable.EMPTY) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.warn("[入侵] 找不到装备战利品表: {}", lootTableId);
            }
            return;
        }

        LootParams lootParams = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.THIS_ENTITY, mob)
            .withParameter(LootContextParams.ORIGIN, mob.position())
            .withLuck(0.0F)
            .create(LootContextParamSets.GIFT);

        List<ItemStack> items = lootTable.getRandomItems(lootParams);
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            EquipmentSlot slot = resolveEquipmentSlot(stack);
            ItemStack existing = mob.getItemBySlot(slot);
            // 仅当对应槽位为空或当前物品为护甲时才装备（避免覆盖已有的主手武器）
            if (existing.isEmpty() || stack.getItem() instanceof ArmorItem) {
                mob.setItemSlot(slot, stack);
                mob.setDropChance(slot, 0.0F);
            }
        }

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] 从战利品表 {} 为 {} 生成了 {} 件装备", lootTableId, mob.getName().getString(), items.size());
        }
    }

    /**
     * 根据物品类型判断应装备的槽位
     *
     * @param stack 物品堆
     * @return 对应的装备槽位
     */
    private static EquipmentSlot resolveEquipmentSlot(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ArmorItem armorItem) {
            return armorItem.getEquipmentSlot();
        }
        return EquipmentSlot.MAINHAND;
    }

    /**
     * 给怪物装备冰霜行者靴子
     *
     * @param mob 目标怪物
     */
    private void applyFrostWalkerBoots(Mob mob) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Holder<net.minecraft.world.item.enchantment.Enchantment> holder =
            getFrostWalkerHolder(serverLevel);
        if (holder == null) {
            AdaptiveNemesisMod.LOGGER.warn("Frost Walker Holder 无效，无法为 {} 装备冰霜行者",
                EntityType.getKey(mob.getType()));
            return;
        }
        ItemStack boots = new ItemStack(Items.IRON_BOOTS);
        boots.enchant(holder, 1);
        mob.setItemSlot(EquipmentSlot.FEET, boots);
        mob.setDropChance(EquipmentSlot.FEET, 0.0f);
    }

    /**
     * 解析冰霜行者附魔的 Holder
     *
     * @param serverLevel 服务端世界
     * @return 附魔 Holder，注册表缺失或未绑定时返回 null
     */
    private Holder<net.minecraft.world.item.enchantment.Enchantment> getFrostWalkerHolder(ServerLevel serverLevel) {
        Registry<net.minecraft.world.item.enchantment.Enchantment> enchantmentRegistry =
            serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder.Reference<net.minecraft.world.item.enchantment.Enchantment> holder =
            enchantmentRegistry.getHolder(ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath("minecraft", "frost_walker"))).orElse(null);
        if (holder == null || !holder.isBound()) {
            return null;
        }
        return holder;
    }

    /**
     * 查找地面生成位置
     * 用MOTION_BLOCKING_NO_LEAVES高度图找到地面高度
     */
    private BlockPos findSpawnPos(Level level, BlockPos center, Vec3 offset) {
        int x = center.getX() + (int) offset.x;
        int z = center.getZ() + (int) offset.z;

        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        if (!serverLevel.hasChunk(x >> 4, z >> 4)) {
            return null;
        }

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (surfaceY <= -64) {
            return null;
        }

        // 在Y轴上找实心方块
        for (int y = surfaceY; y > Math.max(surfaceY - 15, -64); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isSolid() && level.isEmptyBlock(pos.above())) {
                return pos.above();
            }
        }

        return new BlockPos(x, surfaceY + 1, z);
    }

    /**
     * 判断生成点是否位于水面上方
     *
     * 生成点位于水面上方时，脚下间隔最多一格空气即为水面（ getHeight 返回第一个
     * 空闲格，水面兜底落点再高 1 格），向下扫描至多 3 格，遇到水判定为水面生成，
     * 遇到实心方块判定为正常地面生成。
     *
     * @param level 服务端世界
     * @param spawnPos 生成点位置
     * @return 生成点位于水面上方时返回 true
     */
    private boolean isWaterSpawn(Level level, BlockPos spawnPos) {
        BlockPos below = spawnPos.below();
        for (int i = 0; i < 3; i++) {
            if (level.getFluidState(below).is(FluidTags.WATER)) {
                return true;
            }
            if (!level.isEmptyBlock(below)) {
                return false;
            }
            below = below.below();
        }
        return false;
    }

    /**
     * 在生成点下方水面上铺设临时冰面
     *
     * 使用霜冰（会自然融化回水，不永久改变地形），在生成点正下方水面及
     * 周围 3x3 范围内将表面水方块替换为霜冰，确保敌人落地即站立在实心
     * 方块上（onGround），避免下落穿过水面落水后冰霜行者结冰盘够不到脚下。
     *
     * @param level 服务端世界
     * @param spawnPos 生成点位置
     */
    private void preFreezeWaterSurface(Level level, BlockPos spawnPos) {
        BlockPos surface = spawnPos.below(2);
        for (BlockPos pos : BlockPos.betweenClosed(surface.offset(-1, 0, -1), surface.offset(1, 0, 1))) {
            if (level.getFluidState(pos).is(FluidTags.WATER)
                && level.getFluidState(pos.above()).isEmpty()) {
                level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
            }
        }
    }

    /**
     * 为路径涉水的入侵敌人提供冰霜行者援助
     *
     * 每 20 tick 由入侵 tick 调度：遍历所有活动入侵的已生成敌人，
     * 凡身体处于水中或脚下为水的敌人，补穿冰霜行者靴子并冻结脚下
     * 一层水面，使其尽快恢复 onGround 站上冰面；站上冰面后由冰霜
     * 行者的结冰盘自持扩展，解决"陆地生成、路径遇水落水"的缺口。
     */
    private void assistWaterloggedMobs() {
        if (!Config.INVASION.ENABLE_FROST_WALKER_ON_WATER.get()) {
            return;
        }
        for (ActiveInvasion invasion : activeInvasions.values()) {
            Level level = invasion.getLevel();
            if (!(level instanceof ServerLevel serverLevel)) {
                continue;
            }
            for (ActiveInvasion.MobInfo mobInfo : invasion.mobsLeft) {
                if (mobInfo.uuid == null) {
                    continue;
                }
                if (serverLevel.getEntity(mobInfo.uuid) instanceof Mob mob && isMobInWater(mob)) {
                    assistWaterloggedMob(mob);
                }
            }
        }
    }

    /**
     * 判断怪物是否处于水中或脚下为水
     *
     * @param mob 目标怪物
     * @return 身体在水中或脚下格为水时返回 true
     */
    private boolean isMobInWater(Mob mob) {
        return mob.isInWater()
            || mob.level().getFluidState(mob.blockPosition()).is(FluidTags.WATER)
            || mob.level().getFluidState(mob.blockPosition().below()).is(FluidTags.WATER);
    }

    /**
     * 为单只涉水怪物施加援助
     *
     * 脚下无冰霜行者附魔时补穿靴子（避免覆盖装备缩放的既有附魔），
     * 随后冻结怪物脚下一层 3x3 水面；只冻结脚下层、不冻结身体
     * 所在格，防止实体被卡入实心方块。
     *
     * @param mob 目标怪物
     */
    private void assistWaterloggedMob(Mob mob) {
        ItemStack boots = mob.getItemBySlot(EquipmentSlot.FEET);
        if (boots.isEmpty() || !bootsHasFrostWalker(mob, boots)) {
            applyFrostWalkerBoots(mob);
        }

        BlockPos floor = mob.blockPosition().below();
        for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-1, 0, -1), floor.offset(1, 0, 1))) {
            if (mob.level().getFluidState(pos).is(FluidTags.WATER)) {
                mob.level().setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
            }
        }
    }

    /**
     * 检查靴子是否已附带冰霜行者附魔
     *
     * @param mob 目标怪物（用于解析附魔注册表）
     * @param boots 待检查的靴子
     * @return 已附带冰霜行者时返回 true
     */
    private boolean bootsHasFrostWalker(Mob mob, ItemStack boots) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Holder<net.minecraft.world.item.enchantment.Enchantment> holder =
            getFrostWalkerHolder(serverLevel);
        return holder != null
            && EnchantmentHelper.getItemEnchantmentLevel(holder, boots) > 0;
    }

    /**
     * 添加自定义AI目标
     * - UndeadArmyAttackPositionGoal：无目标时向攻击中心移动
     * - UndeadArmyForgiveTeammateGoal：防止亡灵自相残杀
     */
    private void addCustomAI(Mob mob, BlockPos center) {
        if (!(mob instanceof PathfinderMob pathfinderMob)) {
            return;
        }

        // 添加攻击位置目标（优先级4）
        pathfinderMob.goalSelector.addGoal(4, new UndeadArmyAttackPositionGoal(pathfinderMob, center));

        // 添加原谅队友目标（优先级0，最高）
        pathfinderMob.targetSelector.addGoal(0, new UndeadArmyForgiveTeammateGoal(pathfinderMob));
    }

    // ======================== 自定义AI目标 ========================

    /**
     * 亡灵军团 - 攻击位置目标
     * 当怪物没有目标时，向入侵中心位置移动
     * 参考Majrusz's Progressive Difficulty Mod实现
     */
    private static class UndeadArmyAttackPositionGoal extends Goal {

        /** 拥有该AI的怪物 */
        private final PathfinderMob mob;

        /** 目标攻击位置 */
        private final BlockPos attackPosition;

        /** 导航器 */
        private final PathNavigation navigation;
        
        /** 是否正在移动 */
        private boolean isMoving = false;

        /** 计时器 */
        private int delayCounter = 0;

        /**
         * 构造函数
         * 
         * @param mob 怪物
         * @param attackPosition 攻击位置
         */
        public UndeadArmyAttackPositionGoal(PathfinderMob mob, BlockPos attackPosition) {
            this.mob = mob;
            this.attackPosition = attackPosition;
            this.navigation = mob.getNavigation();
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getTarget() == null && isMoving && !navigation.isDone();
        }

        @Override
        public void start() {
            this.isMoving = true;
            this.delayCounter = 0;
            this.navigation.moveTo(attackPosition.getX() + 0.5, attackPosition.getY(), attackPosition.getZ() + 0.5, 1.0);
        }

        @Override
        public void stop() {
            this.isMoving = false;
        }

        @Override
        public void tick() {
            this.delayCounter++;
            
            if (this.delayCounter > 100 && mob.getTarget() == null) {
                this.navigation.moveTo(attackPosition.getX() + 0.5, attackPosition.getY(), attackPosition.getZ() + 0.5, 1.0);
                this.delayCounter = 0;
            }

            if (mob.getTarget() == null && mob.hurtTime <= 0) {
                mob.getLookControl().setLookAt(
                    attackPosition.getX() + 0.5,
                    attackPosition.getY(),
                    attackPosition.getZ() + 0.5,
                    10.0f, 40.0f
                );
            }
        }
    }

    /**
     * 亡灵军团 - 原谅队友目标
     * 防止亡灵生物攻击彼此
     * 参考Majrusz's Progressive Difficulty Mod实现
     * 不可达的怒气提速死逻辑已移除（F-34），名单统一由 isUndeadType 提供
     */
    private static class UndeadArmyForgiveTeammateGoal extends Goal {

        /** 拥有该AI的怪物 */
        private final PathfinderMob mob;

        /**
         * 构造函数
         *
         * @param mob 怪物
         */
        public UndeadArmyForgiveTeammateGoal(PathfinderMob mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            // 当没有目标或目标是亡灵生物时触发
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return false;
            }
            return isUndeadType(target.getType());
        }

        @Override
        public void start() {
            // 清除目标，防止亡灵打亡灵
            mob.setTarget(null);
            mob.setLastHurtMob(null);
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = mob.getTarget();
            return target != null && isUndeadType(target.getType());
        }
    }

    // ======================== 事件监听 ========================

    /**
     * 实体死亡事件
     * 处理亡灵击杀计数和入侵敌人追踪
     */
    @SubscribeEvent
    public void onEntityDeath(LivingDeathEvent event) {
        if (!Config.INVASION.ENABLE_INVASION.get()) {
            return;
        }

        LivingEntity entity = event.getEntity();

        // 检查是否为入侵敌人
        for (ActiveInvasion invasion : activeInvasions.values()) {
            if (invasion.isInvasionEnemy(entity)) {
                invasion.onEnemyDeath(entity);
                return;
            }
        }

        // 检查是否为击杀亡灵生物计数
        if (!(entity instanceof Mob)) {
            return;
        }

        Player killer = event.getSource().getEntity() instanceof Player ?
            (Player) event.getSource().getEntity() : null;

        if (killer != null && isUndeadType(entity.getType())) {
            incrementUndeadKillCount(killer);
        }
    }

    /**
     * 判断实体类型是否属于入侵体系的亡灵类型（F-34 单一来源名单）
     * 同时服务于击杀计数与亡灵互不攻击判定；
     * 铁傀儡不属于亡灵，已按修复计划从名单移除
     *
     * @param type 实体类型
     * @return 是否为亡灵类型
     */
    private static boolean isUndeadType(EntityType<?> type) {
        return type == EntityType.ZOMBIE ||
               type == EntityType.SKELETON ||
               type == EntityType.HUSK ||
               type == EntityType.STRAY ||
               type == EntityType.WITHER_SKELETON ||
               type == EntityType.ZOMBIE_VILLAGER ||
               type == EntityType.ZOGLIN ||
               type == EntityType.GIANT;
    }

    /**
     * 增加亡灵击杀计数
     */
    private void incrementUndeadKillCount(Player player) {
        if (player.level().isClientSide()) {
            return;
        }

        if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }

        if (!isPlayerOnSurface(player)) {
            return;
        }

        UUID playerId = player.getUUID();
        playerUndeadKills.put(playerId, playerUndeadKills.getOrDefault(playerId, 0) + 1);

        int currentKills = playerUndeadKills.get(playerId);
        int requiredKills = getRequiredKillsForInvasion(playerId);

        if (currentKills >= requiredKills && !activeInvasions.containsKey(playerId)) {
            triggerInvasion(player.level(), player, InvasionType.UNDEAD);
            playerUndeadKills.put(playerId, 0);
        }

        // F-15：击杀计数变更实时同步到 SavedData
        syncPlayerProgress(playerId);

        int remaining = requiredKills - currentKills;
        if (remaining <= 5 && remaining > 0 && Config.INVASION.ENABLE_PLAYER_NOTIFICATION.get()) {
            player.sendSystemMessage(Component.translatable(
                "adaptive_nemesis.invasion.kill_count_warning", remaining
            ).withStyle(ChatFormatting.YELLOW));
        }
    }

    /**
     * 判断玩家是否在地表
     */
    private boolean isPlayerOnSurface(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        int surfaceY = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, player.blockPosition()).getY();
        return player.getY() >= surfaceY - 5;
    }

    /**
     * 获取所需击杀数
     */
    private int getRequiredKillsForInvasion(UUID playerId) {
        return hasCompletedFirstInvasion.getOrDefault(playerId, false)
            ? SUBSEQUENT_INVASION_KILLS_REQUIRED
            : FIRST_INVASION_KILLS_REQUIRED;
    }

    // ======================== 触发入侵 ========================

    /**
     * 触发入侵事件（F-41 拆分为条件检查、KubeJS 应用与注册三个环节）
     *
     * @param level 世界
     * @param player 目标玩家
     * @param type 入侵类型
     * @return 是否成功触发
     */
    public boolean triggerInvasion(Level level, Player player, InvasionType type) {
        if (level.isClientSide()) {
            return false;
        }

        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }

        // 检查玩家入侵冷却时间
        if (isPlayerInInvasionCooldown(player)) {
            return false;
        }

        if (activeInvasions.containsKey(player.getUUID())) {
            return false;
        }

        int waveCount = calculateWaveCount(type);
        double difficultyMultiplier = getDifficultyMultiplier();

        // 触发 KubeJS 入侵开始事件
        if (player instanceof ServerPlayer serverPlayer) {
            KubeJSEventTrigger.InvasionStartResult result = applyKubeJsInvasionStart(
                serverPlayer, type, waveCount, difficultyMultiplier);
            if (result == null) {
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug("[入侵] KubeJS 取消了玩家 {} 的入侵事件", player.getName().getString());
                }
                return false;
            }
            waveCount = result.totalWaves;
            difficultyMultiplier = result.difficultyMultiplier;
        }

        registerInvasion(player, level, type, waveCount, difficultyMultiplier,
            "adaptive_nemesis.invasion.triggered");
        return true;
    }

    /**
     * 手动触发入侵（通过命令，F-41 拆分）
     *
     * @param player 目标玩家
     * @param type 入侵类型
     * @param customWaves 自定义波次数，null 时按默认规则计算
     * @param customDifficulty 自定义难度倍率，null 时按默认规则计算
     * @return 是否成功触发
     */
    public boolean triggerInvasionManual(Player player, InvasionType type, Integer customWaves, Double customDifficulty) {
        if (player.level().isClientSide()) {
            return false;
        }

        if (player.level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }

        if (activeInvasions.containsKey(player.getUUID())) {
            return false;
        }

        int waveCount = customWaves != null ? customWaves : calculateWaveCount(type);
        double difficultyMultiplier = customDifficulty != null ? customDifficulty : getDifficultyMultiplier();

        // 触发 KubeJS 入侵开始事件
        if (player instanceof ServerPlayer serverPlayer) {
            KubeJSEventTrigger.InvasionStartResult result = applyKubeJsInvasionStart(
                serverPlayer, type, waveCount, difficultyMultiplier);
            if (result == null) {
                return false;
            }
            waveCount = result.totalWaves;
            difficultyMultiplier = result.difficultyMultiplier;
        }

        registerInvasion(player, player.level(), type, waveCount, difficultyMultiplier,
            "adaptive_nemesis.invasion.triggered_manual");
        return true;
    }

    /**
     * 检查玩家是否处于入侵冷却中（F-41 拆分）
     *
     * @param player 目标玩家
     * @return 冷却中返回 true
     */
    private boolean isPlayerInInvasionCooldown(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        Long cooldownEndTime = playerInvasionCooldowns.get(serverPlayer.getUUID());
        if (cooldownEndTime == null || System.currentTimeMillis() >= cooldownEndTime) {
            return false;
        }
        long remainingSeconds = (cooldownEndTime - System.currentTimeMillis()) / 1000;
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] 玩家 {} 入侵冷却中，剩余{}秒",
                serverPlayer.getName().getString(), remainingSeconds);
        }
        return true;
    }

    /**
     * 触发 KubeJS 入侵开始事件并应用其返回的波次/倍率覆盖（F-41 拆分）
     *
     * @param serverPlayer 目标玩家
     * @param type 入侵类型
     * @param waveCount 当前波次数
     * @param difficultyMultiplier 当前难度倍率
     * @return KubeJS 处理结果，事件被取消时返回 null
     */
    private KubeJSEventTrigger.InvasionStartResult applyKubeJsInvasionStart(ServerPlayer serverPlayer, InvasionType type,
                                                                           int waveCount, double difficultyMultiplier) {
        return KubeJSEventTrigger.triggerInvasionStart(serverPlayer, type, waveCount, difficultyMultiplier);
    }

    /**
     * 创建并登记入侵事件，设置雷暴天气、通知与冷却（F-41 拆分）
     *
     * @param player 目标玩家
     * @param level 世界
     * @param type 入侵类型
     * @param waveCount 总波次数
     * @param difficultyMultiplier 难度倍率
     * @param triggerMessageKey 触发通知的翻译键
     */
    private void registerInvasion(Player player, Level level, InvasionType type,
                                  int waveCount, double difficultyMultiplier, String triggerMessageKey) {
        ActiveInvasion invasion = new ActiveInvasion(
            player.getUUID(),
            level,
            player.blockPosition(),
            type,
            waveCount,
            difficultyMultiplier
        );

        activeInvasions.put(player.getUUID(), invasion);
        setThunderstormWeather(level);

        notifyAllPlayersInArea(invasion, Component.translatable(
            triggerMessageKey
        ).withStyle(ChatFormatting.DARK_RED));

        // 设置玩家入侵冷却时间（F-15：变更同步到 SavedData）
        if (player instanceof ServerPlayer serverPlayer) {
            long cooldownMinutes = Config.INVASION.INVASION_COOLDOWN_MINUTES.get();
            long cooldownEndTime = System.currentTimeMillis() + (cooldownMinutes * 60 * 1000);
            playerInvasionCooldowns.put(serverPlayer.getUUID(), cooldownEndTime);
            syncPlayerProgress(serverPlayer.getUUID());
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug("[入侵] 玩家 {} 入侵冷却时间已设置：{}分钟", serverPlayer.getName().getString(), cooldownMinutes);
            }
        }

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] 玩家 {} 触发了入侵事件，共{}波", player.getName().getString(), waveCount);
        }
    }

    // ======================== 辅助方法 ========================

    /**
     * 通知区域内的所有玩家
     * 通知半径使用独立配置项（F-44），与生成区域半径解耦
     */
    private void notifyAllPlayersInArea(ActiveInvasion invasion, Component message) {
        if (!(invasion.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos center = invasion.getCenterPos();
        double radiusSq = InvasionFixConfig.NOTIFICATION_RADIUS * InvasionFixConfig.NOTIFICATION_RADIUS;

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center.getX(), center.getY(), center.getZ()) <= radiusSq) {
                player.sendSystemMessage(message);
            }
        }
    }

    /**
     * 设置雷暴天气
     */
    private void setThunderstormWeather(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.setWeatherParameters(0, 18000, true, true);
    }

    /**
     * 计算波次数量
     * 优先级：配置阶段表（F-13 迁移默认值）→ world_stages 数据包 invasion_max_waves（F-12）
     * → invasions 数据包 max_waves（F-12，入侵类型级配置优先）→ 配置上限钳制
     *
     * @param type 入侵类型
     * @return 波次数量
     */
    private int calculateWaveCount(InvasionType type) {
        int worldStage = WorldStageManager.getInstance().getWorldStage();

        int maxWaves = InvasionFixConfig.stageWaveCount(worldStage);

        // world_stages 数据包覆盖（F-12：invasion_max_waves 接线）
        int stageMaxWaves = WorldStageManager.getInstance().getStageInvasionMaxWaves();
        if (stageMaxWaves > 0) {
            maxWaves = stageMaxWaves;
        }

        // invasions 数据包覆盖（F-12：max_waves 接线）
        if (Config.INVASION.ENABLE_DATA_PACK_SUPPORT.get()) {
            InvasionData data = getDataPackInvasion(type);
            if (data != null) {
                maxWaves = data.getMaxWaves();
            }
        }

        return InvasionFixConfig.clampWaveCount(maxWaves, Config.INVASION.MAX_WAVE_COUNT.get());
    }

    /**
     * 根据入侵类型获取对应的数据包配置
     *
     * @param type 入侵类型
     * @return 数据包入侵配置，不存在时返回 null
     */
    private InvasionData getDataPackInvasion(InvasionType type) {
        return InvasionDataLoader.getInstance().getInvasion(type.getId());
    }

    /**
     * 获取难度倍率
     * 未启用自定义难度时：数据包阶段倍率优先（与 WorldStageManager.getWorldStageMultiplier
     * 保持一致来源，F-13），数据包未配置时回退到配置阶段表（F-13 迁移默认值）
     */
    private double getDifficultyMultiplier() {
        if (Config.INVASION.ENABLE_CUSTOM_DIFFICULTY.get()) {
            return Config.INVASION.CUSTOM_DIFFICULTY_MULTIPLIER.get();
        }

        // 动态难度：基于世界阶段
        int worldStage = WorldStageManager.getInstance().getWorldStage();
        WorldStageDataConfig stageConfig = WorldStageDataLoader.getInstance().getStageConfig(worldStage);
        if (stageConfig.hasMultiplier()) {
            return stageConfig.getMultiplier();
        }
        return InvasionFixConfig.stageDifficultyMultiplier(worldStage);
    }

    /**
     * 获取罗马数字
     */
    private String getRomanNumeral(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> String.valueOf(number);
        };
    }

    /**
     * 触发闪电特效
     * 随机源使用世界随机源（F-49）
     */
    private void triggerLightningEffect(ActiveInvasion invasion) {
        if (!(invasion.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos center = invasion.getCenterPos();

        for (int i = 0; i < 3; i++) {
            int dx = level.random.nextInt(30) - 15;
            int dz = level.random.nextInt(30) - 15;
            BlockPos lightningPos = new BlockPos(
                center.getX() + dx,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX() + dx, center.getZ() + dz),
                center.getZ() + dz
            );
            net.minecraft.world.entity.LightningBolt lightning = new net.minecraft.world.entity.LightningBolt(
                EntityType.LIGHTNING_BOLT,
                level
            );
            lightning.moveTo(lightningPos.getX() + 0.5, lightningPos.getY(), lightningPos.getZ() + 0.5, 0, 0);
            lightning.setVisualOnly(true);
            level.addFreshEntity(lightning);
        }
    }

    /**
     * 处理入侵胜利
     * 仅发送胜利通知，奖励与 KubeJS 事件由 concludeInvasion/triggerInvasionEndEvent 统一处理
     */
    private void handleInvasionVictory(ActiveInvasion invasion) {
        notifyAllPlayersInArea(invasion, Component.translatable(
            "adaptive_nemesis.invasion.defeated"
        ).withStyle(ChatFormatting.GREEN));

        // 清除玩家入侵冷却时间，允许再次触发（F-15：同步到 SavedData）
        UUID playerId = invasion.getPlayerUUID();
        playerInvasionCooldowns.remove(playerId);
        if (progressSavedData != null) {
            progressSavedData.clearCooldown(playerId);
        }
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] 胜利：玩家={}, 冷却时间已清除", invasion.getPlayerName());
        }
    }

    /**
     * 触发入侵结束 KubeJS 事件（胜利时同时发放奖励）
     *
     * @param invasion 活动入侵
     * @param victory 是否胜利
     */
    private void triggerInvasionEndEvent(ActiveInvasion invasion, boolean victory) {
        Player player = invasion.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        int wavesCompleted = victory ? invasion.getTotalWaves() : Math.max(0, invasion.currentWave - 1);
        InvasionRewardData rewards = victory ? getInvasionRewards(invasion.getType()) : new InvasionRewardData();
        rewards = KubeJSEventTrigger.triggerInvasionEnd(
            serverPlayer,
            invasion.getType(),
            victory,
            invasion.getTotalWaves(),
            invasion.getDifficultyMultiplier(),
            wavesCompleted,
            rewards
        );
        if (victory && rewards != null) {
            grantRewards(serverPlayer, rewards);
        }
    }

    /**
     * 根据入侵类型获取奖励配置
     *
     * @param type 入侵类型
     * @return 奖励配置
     */
    private InvasionRewardData getInvasionRewards(InvasionType type) {
        if (!Config.INVASION.ENABLE_DATA_PACK_SUPPORT.get()) {
            return new InvasionRewardData();
        }
        InvasionData data = InvasionDataLoader.getInstance().getInvasion(type.getId());
        return data != null ? data.getRewards() : new InvasionRewardData();
    }

    /**
     * 发放入侵奖励
     *
     * @param player 获奖玩家
     * @param rewards 奖励配置
     */
    private void grantRewards(ServerPlayer player, InvasionRewardData rewards) {
        if (rewards == null || !rewards.isEnabled() || !rewards.hasRewards()) {
            return;
        }

        ServerLevel level = player.serverLevel();

        // 发放战利品表
        for (ResourceLocation lootTableId : rewards.getLootTables()) {
            ResourceKey<LootTable> lootKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableId);
            LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootKey);
            if (lootTable == LootTable.EMPTY) {
                AdaptiveNemesisMod.LOGGER.warn("入侵奖励战利品表不存在: {}", lootTableId);
                continue;
            }
            LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.GIFT);
            List<ItemStack> loot = lootTable.getRandomItems(params);
            for (ItemStack stack : loot) {
                giveItem(player, stack);
            }
        }

        // 发放额外物品
        for (ItemStack stack : rewards.getExtraItems()) {
            giveItem(player, stack.copy());
        }

        // 发放经验值
        if (rewards.getExperience() > 0) {
            player.giveExperiencePoints(rewards.getExperience());
        }

        // 施加药水效果
        for (MobEffectInstance effect : rewards.getEffects()) {
            player.addEffect(new MobEffectInstance(effect));
        }

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "[入侵] 已发放奖励给玩家 {}: 战利品表={}, 经验={}, 效果={}",
                player.getName().getString(),
                rewards.getLootTables().size(),
                rewards.getExperience(),
                rewards.getEffects().size()
            );
        }
    }

    /**
     * 安全地给予玩家物品，背包满时掉落地面
     *
     * @param player 玩家
     * @param stack 物品
     */
    private void giveItem(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        if (!inventory.add(stack)) {
            player.drop(stack, false);
        }
    }

    // ======================== 敌人数值 ========================

    /**
     * 获取实体类型的基础血量（F-35）
     * 改用实体类型注册的默认属性值，删除硬编码表（含僵尸村民 40 的修正，F-33 并入）；
     * 未注册默认属性的类型回退到 20
     *
     * @param type 实体类型
     * @return 基础血量
     */
    @SuppressWarnings("unchecked")
    private int getBaseHealth(EntityType<?> type) {
        net.minecraft.world.entity.ai.attributes.AttributeSupplier supplier =
            net.minecraft.world.entity.ai.attributes.DefaultAttributes.getSupplier(
                (EntityType<? extends net.minecraft.world.entity.LivingEntity>) type);
        if (supplier == null) {
            return 20;
        }
        return (int) supplier.getValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
    }

    // ======================== 增强与装备 ========================

    /**
     * 应用入侵增强（支持数据包自定义倍率）
     * 入口对基础倍率做下限钳制（F-14），防止 0/负数倍率产生 0 血或非法属性实体；
     * 速度增强系数同步使用钳制后的倍率
     *
     * @param enemy 目标实体
     * @param multiplier 基础难度倍率（全局倍率与波次倍率之积）
     * @param healthMultiplier 血量额外倍率
     * @param damageMultiplier 攻击额外倍率
     */
    private void applyInvasionEnhancements(LivingEntity enemy, double multiplier,
                                            double healthMultiplier, double damageMultiplier) {
        double clamped = InvasionFixConfig.clampMultiplier(multiplier);
        double effectiveHealth = clamped * healthMultiplier;
        double effectiveDamage = clamped * damageMultiplier;

        var maxHealthAttr = enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(maxHealthAttr.getBaseValue() * effectiveHealth);
            enemy.setHealth(enemy.getMaxHealth());
        }

        var attackDamageAttr = enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (attackDamageAttr != null) {
            attackDamageAttr.setBaseValue(attackDamageAttr.getBaseValue() * effectiveDamage);
        }

        var movementSpeedAttr = enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (movementSpeedAttr != null) {
            movementSpeedAttr.setBaseValue(movementSpeedAttr.getBaseValue() * InvasionFixConfig.speedEnhancementFactor(clamped));
        }
    }

    /**
     * 给入侵敌人装备基础武器
     * 护甲由动态难度系统（EnchantmentScalingHandler）统一生成
     *
     * @param enemy 目标怪物
     */
    private void equipInvasionEnemy(Mob enemy) {
        EntityType<?> type = enemy.getType();

        if (type == EntityType.ZOMBIE_VILLAGER) {
            equipTank(enemy);
            return;
        }

        // 给骷髅和流浪者装备弓
        if (type == EntityType.SKELETON || type == EntityType.STRAY) {
            enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        }

        // 给凋灵骷髅装备石剑（确保主手有武器）
        if (type == EntityType.WITHER_SKELETON) {
            enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        }

        // 给僵尸和尸壳小概率装备铁剑（F-49：使用实体自带随机源）
        if ((type == EntityType.ZOMBIE || type == EntityType.HUSK) && enemy.getRandom().nextFloat() < 0.3f) {
            enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        }
    }

    /**
     * 计算动态难度倍率
     * 基于入侵中心附近玩家的平均强度，与常规怪物缩放逻辑保持一致
     *
     * @param serverLevel 服务端世界
     * @param center 入侵中心位置
     * @param baseMultiplier 基础倍率（来自世界阶段）
     * @return 动态难度倍率
     */
    private double calculateDynamicDifficultyMultiplier(ServerLevel serverLevel, BlockPos center, double baseMultiplier) {
        double range = Config.AREA_SYNC_RANGE.get() * 16;
        double rangeSq = range * range;

        List<ServerPlayer> nearbyPlayers = new ArrayList<>();
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (player.level() == serverLevel && player.distanceToSqr(center.getX(), center.getY(), center.getZ()) <= rangeSq) {
                nearbyPlayers.add(player);
            }
        }

        if (nearbyPlayers.isEmpty()) {
            return Math.max(1.0, baseMultiplier);
        }

        double totalStrength = 0.0;
        int validPlayers = 0;
        PlayerStrengthEvaluator evaluator = PlayerStrengthEvaluator.getInstance();

        for (ServerPlayer player : nearbyPlayers) {
            PlayerStrengthData data = evaluator.getPlayerStrength(player);
            if (data != null) {
                totalStrength += data.getTotalStrength();
                validPlayers++;
            }
        }

        if (validPlayers == 0) {
            return Math.max(1.0, baseMultiplier);
        }

        double avgStrength = totalStrength / validPlayers;
        double strengthMultiplier = 1.0 + (avgStrength * Config.DIFFICULTY_BASE_MULTIPLIER.get() / 100.0);

        if (Config.ENABLE_WORLD_STAGE.get()) {
            strengthMultiplier *= WorldStageManager.getInstance().getWorldStageMultiplier();
        }

        double finalMultiplier = Math.max(baseMultiplier, Math.max(1.0, strengthMultiplier));

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "[入侵动态难度] 平均玩家强度={}, 基础倍率={}, 最终倍率={}",
                String.format("%.2f", avgStrength),
                String.format("%.2f", baseMultiplier),
                String.format("%.2f", finalMultiplier)
            );
        }

        return finalMultiplier;
    }

    /**
     * 装备Tank（僵尸村民）
     */
    private void equipTank(Mob enemy) {
        enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
    }

    /**
     * 应用发光效果
     */
    private void applyGlowingEffect(LivingEntity enemy) {
        if (Config.INVASION.ENABLE_GLOWING_EFFECT.get()) {
            enemy.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.GLOWING,
                Integer.MAX_VALUE
            ));
        }
    }

    // ======================== 波次配置 ========================

    /**
     * 获取指定波次的敌人配置
     *
     * 优先从数据包读取（含波次难度倍率与权重，F-12），未启用或不存在时回退到硬编码配置：
     *
     * | 波数 | 僵尸 | 骷髅 | 尸壳 | 流浪者 | Tank(僵尸村民) | 凋灵骷髅 | 地狱犬(Zoglin) | 巨人(BOSS) |
     * | --- | --- | --- | --- | --- | --- | --- | --- | --- |
     * | 1 | 4 | 4 | - | - | - | - | - | - |
     * | 2 | 5 | 5 | - | - | - | - | - | - |
     * | 3 | 3 | 3 | 3 | 3 | 1 | - | - | - |
     * | 4 | 2 | 2 | 3 | 3 | 1 | 4 | - | - |
     * | 5 | 1 | 1 | 3 | 3 | 1 | 4 | 1 | - |
     * | 6 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
     *
     * @param waveNumber 波次编号
     * @param type 入侵类型
     * @return 敌人生成配置列表
     */
    private List<EnemySpawnConfig> getWaveEnemyConfig(int waveNumber, InvasionType type) {
        List<EnemySpawnConfig> configs = new ArrayList<>();

        // 尝试从数据包读取配置
        if (Config.INVASION.ENABLE_DATA_PACK_SUPPORT.get() && loadDataPackWaveConfig(configs, waveNumber, type)) {
            return configs;
        }

        // 硬编码回退配置
        buildFallbackWaveConfig(configs, waveNumber);
        return configs;
    }

    /**
     * 从数据包读取指定波次的敌人配置（F-41 拆分）
     *
     * @param configs 输出配置列表
     * @param waveNumber 波次编号
     * @param type 入侵类型
     * @return 数据包存在该波次配置时返回 true
     */
    private boolean loadDataPackWaveConfig(List<EnemySpawnConfig> configs, int waveNumber, InvasionType type) {
        InvasionData data = getDataPackInvasion(type);
        if (data == null) {
            return false;
        }
        InvasionData.WaveData waveData = data.getWave(waveNumber);
        if (waveData == null) {
            return false;
        }
        for (InvasionData.EnemyData enemy : waveData.getEnemies()) {
            configs.add(new EnemySpawnConfig(
                enemy.getEntityType(),
                enemy.getCount(),
                enemy.isBoss(),
                enemy.getEquipmentLootTable(),
                enemy.getEffects(),
                enemy.isGlowing(),
                enemy.isFrostWalker(),
                enemy.getSpawnDirection(),
                enemy.getCustomNameKey(),
                enemy.getHealthMultiplier(),
                enemy.getDamageMultiplier(),
                waveData.getDifficultyMultiplier(),
                enemy.getWeight()
            ));
        }
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "[入侵] 从数据包读取第 {} 波配置，敌人类型数={}",
                waveNumber, configs.size()
            );
        }
        return true;
    }

    /**
     * 构建硬编码回退波次配置（F-41 拆分）
     *
     * @param configs 输出配置列表
     * @param waveNumber 波次编号
     */
    private void buildFallbackWaveConfig(List<EnemySpawnConfig> configs, int waveNumber) {
        switch (waveNumber) {
            case 1, 2 -> buildEarlyWave(configs, waveNumber);
            case 3, 4, 5 -> buildMidWave(configs, waveNumber);
            case 6 -> buildFinalWave(configs);
            default -> {
                configs.add(fallbackConfig(EntityType.ZOMBIE, 5));
                configs.add(fallbackConfig(EntityType.SKELETON, 5));
            }
        }
    }

    /**
     * 构建前期波次（第 1、2 波：僵尸与骷髅对等编队）
     *
     * @param configs 输出配置列表
     * @param waveNumber 波次编号
     */
    private void buildEarlyWave(List<EnemySpawnConfig> configs, int waveNumber) {
        int count = waveNumber + 3;
        configs.add(fallbackConfig(EntityType.ZOMBIE, count));
        configs.add(fallbackConfig(EntityType.SKELETON, count));
    }

    /**
     * 构建中期波次（第 3-5 波：引入尸壳、流浪者、凋灵骷髅与地狱犬）
     *
     * @param configs 输出配置列表
     * @param waveNumber 波次编号
     */
    private void buildMidWave(List<EnemySpawnConfig> configs, int waveNumber) {
        // 第 3/4/5 波僵尸与骷髅数量分别为 3/2/1（6 - 波次编号）
        configs.add(fallbackConfig(EntityType.ZOMBIE, 6 - waveNumber));
        configs.add(fallbackConfig(EntityType.SKELETON, 6 - waveNumber));
        configs.add(fallbackConfig(EntityType.HUSK, 3));
        configs.add(fallbackConfig(EntityType.STRAY, 3));
        configs.add(fallbackConfig(EntityType.ZOMBIE_VILLAGER, 1));
        if (waveNumber >= 4) {
            configs.add(fallbackConfig(EntityType.WITHER_SKELETON, 4));
        }
        if (waveNumber >= 5) {
            configs.add(fallbackConfig(EntityType.ZOGLIN, 1));
        }
    }

    /**
     * 构建最终波（第 6 波：全类型小队 + 巨人 BOSS）
     *
     * @param configs 输出配置列表
     */
    private void buildFinalWave(List<EnemySpawnConfig> configs) {
        configs.add(fallbackConfig(EntityType.ZOMBIE, 1));
        configs.add(fallbackConfig(EntityType.SKELETON, 1));
        configs.add(fallbackConfig(EntityType.HUSK, 1));
        configs.add(fallbackConfig(EntityType.STRAY, 1));
        configs.add(fallbackConfig(EntityType.ZOMBIE_VILLAGER, 1));
        configs.add(fallbackConfig(EntityType.WITHER_SKELETON, 1));
        configs.add(fallbackConfig(EntityType.ZOGLIN, 1));
        configs.add(fallbackConfig(EntityType.GIANT, 1, true));
    }

    /**
     * 创建默认回退配置
     *
     * @param entityType 实体类型
     * @param count 数量
     * @return 敌人生成配置
     */
    private EnemySpawnConfig fallbackConfig(EntityType<?> entityType, int count) {
        return fallbackConfig(entityType, count, entityType == EntityType.GIANT);
    }

    /**
     * 创建默认回退配置
     *
     * @param entityType 实体类型
     * @param count 数量
     * @param isBoss 是否为 BOSS
     * @return 敌人生成配置
     */
    private EnemySpawnConfig fallbackConfig(EntityType<?> entityType, int count, boolean isBoss) {
        return new EnemySpawnConfig(
            entityType, count, isBoss, null,
            new ArrayList<>(), false, false,
            null, null, 1.0, 1.0, 1.0, 1
        );
    }

    // ======================== 公开方法 ========================

    public ActiveInvasion getActiveInvasion(Player player) {
        return activeInvasions.get(player.getUUID());
    }

    public int getPlayerUndeadKills(Player player) {
        return playerUndeadKills.getOrDefault(player.getUUID(), 0);
    }

    public int getRequiredKills(Player player) {
        return getRequiredKillsForInvasion(player.getUUID());
    }

    public boolean hasCompletedFirstInvasion(Player player) {
        return hasCompletedFirstInvasion.getOrDefault(player.getUUID(), false);
    }

    /**
     * 入侵类型
     *
     * 由资源位置标识，支持数据包自定义新的入侵类型。
     */
    public static final class InvasionType {

        /**
         * 亡灵军团
         */
        public static final InvasionType UNDEAD =
            new InvasionType(ResourceLocation.fromNamespaceAndPath(AdaptiveNemesisMod.MODID, "undead_invasion"));

        private final ResourceLocation id;

        private InvasionType(ResourceLocation id) {
            this.id = id;
        }

        /**
         * 根据资源位置获取入侵类型
         *
         * @param id 入侵配置标识符
         * @return 入侵类型
         */
        public static InvasionType of(ResourceLocation id) {
            if (UNDEAD.id.equals(id)) {
                return UNDEAD;
            }
            return new InvasionType(id);
        }

        /**
         * 获取入侵类型标识符
         *
         * @return 资源位置
         */
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public String toString() {
            return id.toString();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof InvasionType other)) return false;
            return id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    /**
     * 波次敌人生成配置
     * spawnDirection 为数据包死字段（F-12：加载时 warn 不支持），仅透传存档
     *
     * @param entityType 实体类型
     * @param count 数量
     * @param isBoss 是否为 BOSS
     * @param equipmentLootTable 装备战利品表
     * @param effects 药水效果列表
     * @param glowing 是否发光
     * @param frostWalker 是否冰霜行者
     * @param spawnDirection 出生方向（暂不支持）
     * @param customNameKey 自定义名称翻译键
     * @param healthMultiplier 血量倍率
     * @param damageMultiplier 攻击倍率
     * @param waveMultiplier 波次难度倍率（数据包 WaveData.difficulty_multiplier）
     * @param weight 波内加权随机选择权重（数据包 EnemyData.weight）
     */
    private record EnemySpawnConfig(
        EntityType<?> entityType,
        int count,
        boolean isBoss,
        ResourceLocation equipmentLootTable,
        List<MobEffectInstance> effects,
        boolean glowing,
        boolean frostWalker,
        String spawnDirection,
        String customNameKey,
        double healthMultiplier,
        double damageMultiplier,
        double waveMultiplier,
        int weight
    ) {}
}
