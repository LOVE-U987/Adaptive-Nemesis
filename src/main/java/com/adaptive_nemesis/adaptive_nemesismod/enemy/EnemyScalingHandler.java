package com.adaptive_nemesis.adaptive_nemesismod.enemy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.compat.L2HostilityCompat;
import com.adaptive_nemesis.adaptive_nemesismod.compat.ModCompatManager;
import com.adaptive_nemesis.adaptive_nemesismod.kubejs.KubeJSEventTrigger;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthData;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;
import com.adaptive_nemesis.adaptive_nemesismod.protection.NewbieProtectionHandler;
import com.adaptive_nemesis.adaptive_nemesismod.watchdog.WatchdogService;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * 敌人动态强化系统
 *
 * 基于玩家综合强度，为敌人提供自适应属性加成：
 * - 血量提升
 * - 伤害提升
 * - 护甲提升
 * - 受击抗性/击倒抗性（史诗战斗兼容）
 * - 攻击速度（史诗战斗兼容 - 防止无限硬直）
 * - 法术强度/抗性（铁魔法兼容）
 * - 耐力值（史诗战斗兼容）
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class EnemyScalingHandler {

    /**
     * 单例实例
     */
    private static EnemyScalingHandler INSTANCE;

    /** 已强化实体的NBT标签键 */
    public static final String SCALED_TAG = "adaptive_nemesis_scaled";

    /**
     * 缩放超时NBT标签键
     * 如果实体缩放处理时间超过阈值，打上此标记，
     * 下次该实体加入世界时直接跳过缩放，
     * 避免因 Cataclysm 等模组结构生成导致的区块加载死锁
     */
    public static final String SCALE_TIMEOUT_TAG = "adaptive_nemesis_scale_timeout";

    /**
     * 强化倍率NBT标签键
     */
    public static final String SCALE_MULTIPLIER_TAG = "adaptive_nemesis_multiplier";

    /**
     * 原始属性值前缀 - 用于防重复缩放
     * 存储缩放前的原始值，确保每次缩放都从原始值开始
     */
    private static final String ORIGINAL_PREFIX = "an_original_";

    /**
     * 原始血量NBT标签键 - 存储缩放前的原始基础血量
     * public 以便 {@link com.adaptive_nemesis.adaptive_nemesismod.boss.BossDamageCapHandler}
     * 等其他处理器读取，确保 Boss 血量计算基于同一原始值，避免倍率双重叠加
     */
    public static final String ORIGINAL_HEALTH_TAG = ORIGINAL_PREFIX + "health";

    /**
     * 原始伤害NBT标签键 - 存储缩放前的原始基础伤害
     * public 以便其他处理器读取，语义同 {@link #ORIGINAL_HEALTH_TAG}
     */
    public static final String ORIGINAL_DAMAGE_TAG = ORIGINAL_PREFIX + "damage";
    private static final String ORIGINAL_ARMOR_TAG = ORIGINAL_PREFIX + "armor";
    private static final String ORIGINAL_TOUGHNESS_TAG = ORIGINAL_PREFIX + "toughness";
    private static final String ORIGINAL_SPEED_TAG = ORIGINAL_PREFIX + "speed";
    private static final String ORIGINAL_ATTACK_SPEED_TAG = ORIGINAL_PREFIX + "attack_speed";

    /**
     * 血量修复调度标记 - 防止重复调度延迟setHealth
     */
    private static final String HEALTH_FIX_SCHEDULED_TAG = "an_health_fix_scheduled";

    /**
     * 总倍率上限 - 防止属性爆炸
     * 在应用各属性独立上限前的总关卡
     */
    private static final double MAX_TOTAL_MULTIPLIER = 20.0;

    /**
     * 缩放超时阈值（纳秒）— 超过此时间认为可能触发了区块加载死锁。
     * 如果 `applyAttributeBonuses()` 执行超过此阈值，自动标记实体为问题实体
     * 并跳过尚未应用的加成阶段，防止服务端因区块加载死锁。
     */
    private static final long SCALE_TIMEOUT_NANOS = 30_000_000_000L; // 30秒

    /**
     * 玩家空间索引
     *
     * 按维度 + 区块分桶缓存玩家位置，避免每次实体生成时遍历全服玩家。
     * ServerLevel 使用 WeakHashMap 键，维度卸载后索引自动释放。
     */
    private final Map<ServerLevel, Map<ChunkPos, List<ServerPlayer>>> playerSpatialIndex = new WeakHashMap<>();

    /**
     * 各属性随机分布因子容器
     *
     * @param health      血量随机因子
     * @param damage      伤害随机因子
     * @param armor       护甲随机因子
     * @param toughness   韧性随机因子
     * @param attackSpeed 攻击速度随机因子
     */
    private record ScalingFactors(double health, double damage, double armor, double toughness, double attackSpeed) {
    }

    /**
     * 灵魂石标记 - LUCK属性的特征值
     * 灵魂石会清空NBT但保留实体核心属性（包括LUCK），
     * 我们用LUCK属性存一个特征值作为布尔标记，
     * 表示该实体已被缩放系统处理过。
     *
     * 注意：LUCK属性只承载"是否已缩放"这一布尔信息，
     * 不编码具体倍率。灵魂石释放后实体保留缩放后的属性值，
     * 因此无需恢复旧倍率，只需避免重复缩放即可。
     *
     * 容差说明：LUCK 属性默认基值为 0，标记值取 0.0420。
     * {@link #hasScaledMarker(Mob)} 以 0.001 容差比对特征值，
     * 若其他模组恰好将 LUCK 基值设为 0.041-0.043 区间内的数值，
     * 存在被误判为已缩放的理论窗口；该窗口极窄且此类用法罕见，
     * 概率极低，属可接受的已知边界（计划明确不修项）。
     */
    private static final double LUCK_MARKER_VALUE = 0.0420;

    /**
     * 对生物应用灵魂石标记
     * 将LUCK属性设为一个特征值，
     * 这样灵魂石释放后我们也能识别出这是被缩放过的实体
     *
     * @param mob 目标生物
     */
    private void applyScaledMarker(Mob mob) {
        AttributeInstance luckAttr = mob.getAttribute(Attributes.LUCK);
        if (luckAttr != null) {
            luckAttr.setBaseValue(LUCK_MARKER_VALUE);
        }
    }

    /**
     * 检查生物是否已被灵魂石标记
     * 通过检测LUCK属性的特征值来判断
     *
     * @param mob 目标生物
     * @return true表示该实体此前已被缩放系统处理过
     */
    private boolean hasScaledMarker(Mob mob) {
        AttributeInstance luckAttr = mob.getAttribute(Attributes.LUCK);
        return luckAttr != null && Math.abs(luckAttr.getBaseValue() - LUCK_MARKER_VALUE) < 0.001;
    }

    /**
     * 私有构造函数 - 单例模式
     */
    private EnemyScalingHandler() {}

    /**
     * 获取单例实例
     *
     * @return EnemyScalingHandler 实例
     */
    public static synchronized EnemyScalingHandler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EnemyScalingHandler();
        }
        return INSTANCE;
    }

    /**
     * 实体加入世界事件 - 对新生成的敌人应用强化
     *
     * 本方法仅负责流程编排：前置过滤、已缩放检测、缩放执行与异常兜底
     * 分别拆分到各子方法中。
     *
     * 检测链路（按可靠性排序）：
     * 1. NBT标记 SCALED_TAG（最快，NBT存活时）
     * 2. LUCK属性标记（灵魂石释放后NBT被清，但属性值存活）
     *
     * @param event 实体加入世界事件
     */
    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // 只在服务端处理
        if (event.getLevel().isClientSide()) {
            return;
        }

        // 只处理敌对生物（非敌对 Mob 直接返回，避免后续黑名单/超时/看门狗等检查浪费性能）
        if (!(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)) {
            return;
        }

        try {
            // 前置过滤：黑名单、缩放超时标记、看门狗问题实体集
            if (shouldSkipScaling(mob)) {
                return;
            }

            // 已缩放检测：NBT 标记与 LUCK 属性标记
            if (hasScalingMarkers(mob)) {
                return;
            }

            // 应用强化（含看门狗注入与入口/完成日志）
            applyScalingWithWatchdog(mob);
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException | ArithmeticException e) {
            // 业务异常：属性/NBT/状态等已知业务逻辑错误
            // 记录后继续处理下一个实体，避免单个问题实体导致服务端崩溃
            logScalingBusinessException(mob, e);
        } catch (Exception e) {
            // 未知异常：第三方模组实体可能触发未预期错误
            // 默认记录完整堆栈并吞掉，防止异常传播导致服务器崩溃
            logScalingUnexpectedException(mob, e);
        }
        // 注：缩放超时（如 Cataclysm 触发的区块加载死锁）由 ScalingContext.checkStage()
        // 内部检测并处理，会设置 NBT 超时标记 SCALE_TIMEOUT_TAG，不会到达此处 catch。
    }

    /**
     * 缩放前置过滤：判断该实体是否应跳过缩放
     *
     * 依次检查：
     * 1. 实体黑名单（被 ban 的实体跳过所有自适应缩放）
     * 2. 缩放超时标记（此前缩放中可能触发了区块加载死锁）
     * 3. 看门狗问题实体集（前一轮卡死中被标记为"可能引起来源"）
     *
     * @param mob 目标生物
     * @return 如果应跳过缩放返回 true
     */
    private boolean shouldSkipScaling(Mob mob) {
        // 检查黑名单 - 被ban的实体跳过所有自适应缩放
        if (EntityFilterHelper.getInstance().isBlocked(mob)) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "实体 {} 在黑名单中，跳过自适应缩放",
                    mob.getType().getDescriptionId()
                );
            }
            return true;
        }

        // 检查缩放超时标记 - 此前缩放中可能触发了区块加载死锁
        //（如 Cataclysm 炽燃遗魂在低版本中会导致服务端 getChunk 死锁）
        if (mob.getPersistentData().getBoolean(SCALE_TIMEOUT_TAG)) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "实体 {} 有缩放超时标记，跳过缩放处理（防止死锁）",
                    mob.getType().getDescriptionId()
                );
            }
            return true;
        }

        // 检查看门狗问题实体集 — 前一轮卡死中该实体被标记为"可能引起来源"
        if (Config.ENABLE_WATCHDOG.get()) {
            String entityKey = WatchdogService.makeEntityKey(
                mob.getName().getString(), mob.getX(), mob.getY(), mob.getZ()
            );
            if (WatchdogService.getInstance().isEntityProblematic(entityKey)) {
                AdaptiveNemesisMod.LOGGER.warn(
                    "实体 {} 在看门狗问题实体集中，跳过缩放防止死锁",
                    mob.getType().getDescriptionId()
                );
                // 同时标记 NBT，确保后续世界加入也跳过
                mob.getPersistentData().putBoolean(SCALE_TIMEOUT_TAG, true);
                return true;
            }
        }

        return false;
    }

    /**
     * 检测实体是否已被缩放（NBT 标记或 LUCK 属性标记）
     *
     * 命中 LUCK 标记（灵魂石场景）时会恢复 NBT 标记，
     * 后续不再经过该检测路径。
     *
     * @param mob 目标生物
     * @return 如果已被缩放过返回 true
     */
    private boolean hasScalingMarkers(Mob mob) {
        // === 灵魂石调试日志：打印每次检测的详细数值 ===
        if (Config.ENABLE_DEBUG_LOG.get()) {
            logSoulstoneDetection(mob);
        }

        // 检测链路 1: NBT标记（标准路径）
        if (mob.getPersistentData().getBoolean(SCALED_TAG)) {
            return true;
        }

        // 检测链路 2: LUCK属性标记（灵魂石路径 — 属性值穿越了NBT清除）
        if (hasScaledMarker(mob)) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "敌人 {} 命中LUCK属性标记（灵魂石场景），跳过重复缩放",
                    mob.getName().getString()
                );
            }
            // 恢复NBT标记，后续不再经过此路径
            mob.getPersistentData().putBoolean(SCALED_TAG, true);
            return true;
        }

        return false;
    }

    /**
     * 输出灵魂石检测的详细调试日志
     *
     * @param mob 目标生物
     */
    private void logSoulstoneDetection(Mob mob) {
        AttributeInstance luckAttr = mob.getAttribute(Attributes.LUCK);
        double luckValue = luckAttr != null ? luckAttr.getBaseValue() : Double.NaN;
        boolean hasScaledTag = mob.getPersistentData().getBoolean(SCALED_TAG);
        boolean hasLuckMarker = hasScaledMarker(mob);
        AdaptiveNemesisMod.LOGGER.debug(
            "[灵魂石检测] 实体={}, 类型={}, UUID={}, UUIDhex={}, "
                + "NBT-SCALED_TAG存在={}, SCALED_TAG值={}, "
                + "LUCK属性值={}, LUCK标记值={}, hasLuckMarker={}, "
                + "当前血量={}, 最终判定={}",
            mob.getName().getString(),
            mob.getType().getDescriptionId(),
            mob.getUUID(),
            mob.getUUID().toString(),
            mob.getPersistentData().contains(SCALED_TAG),
            mob.getPersistentData().getBoolean(SCALED_TAG),
            String.format("%.6f", luckValue),
            LUCK_MARKER_VALUE,
            hasLuckMarker,
            String.format("%.1f", mob.getAttribute(Attributes.MAX_HEALTH) != null
                ? mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue() : -1.0),
            hasScaledTag || hasLuckMarker ? "跳过(已缩放)" : "首次缩放"
        );
    }

    /**
     * 应用强化并注入看门狗上下文
     *
     * @param mob 目标生物
     */
    private void applyScalingWithWatchdog(Mob mob) {
        if (Config.ENABLE_DEBUG_LOG.get()) {
            // 记录实体模组来源（用于排查第三方模组兼容性问题，如 Cataclysm）
            ResourceLocation entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            String modNamespace = entityId != null ? entityId.getNamespace() : "unknown";
            long tickTime = mob.level().getGameTime();
            AdaptiveNemesisMod.LOGGER.debug(
                "[缩放入口] 实体={}, 类型={}, 模组来源={}, 维度={}, " +
                "当前gameTick={}, 当前位置=[{}, {}, {}]",
                mob.getName().getString(),
                mob.getType().getDescriptionId(),
                modNamespace,
                mob.level().dimension().location(),
                tickTime,
                mob.getX(), mob.getY(), mob.getZ()
            );
        }

        // 注入看门狗：记录正在处理的实体信息
        if (Config.ENABLE_WATCHDOG.get()) {
            WatchdogService.getInstance().updateEntityProcessing(
                mob.getName().getString(),
                mob.getType().getDescriptionId(),
                "applyScaling",
                String.format("[%.0f, %.0f, %.0f]", mob.getX(), mob.getY(), mob.getZ()),
                mob.level().dimension().location().toString()
            );
        }

        applyScaling(mob);

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "[缩放完成] 实体={}, 缩放结束",
                mob.getName().getString()
            );
        }
    }

    /**
     * 记录缩放业务异常
     *
     * @param mob 目标生物
     * @param e 业务异常
     */
    private void logScalingBusinessException(Mob mob, Exception e) {
        AdaptiveNemesisMod.LOGGER.error(
            "缩放实体 {} (类型: {}) 时发生业务异常: {} - {}",
            mob.getName().getString(),
            mob.getType().getDescriptionId(),
            e.getClass().getSimpleName(),
            e.getMessage()
        );
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.error("业务异常堆栈:", e);
        }
    }

    /**
     * 记录缩放未知异常
     *
     * @param mob 目标生物
     * @param e 未知异常
     */
    private void logScalingUnexpectedException(Mob mob, Exception e) {
        AdaptiveNemesisMod.LOGGER.error(
            "缩放实体 {} (类型: {}) 时发生未知异常: {} - {}",
            mob.getName().getString(),
            mob.getType().getDescriptionId(),
            e.getClass().getSimpleName(),
            e.getMessage(),
            e
        );
    }

    /**
     * 对指定生物应用属性强化
     *
     * 本方法仅负责流程编排：玩家强度采集、倍率计算、KubeJS 事件介入
     * 与标记落盘分别拆分到各子方法中。
     *
     * @param mob 目标生物
     */
    public void applyScaling(Mob mob) {
        if (mob.level().isClientSide()) {
            return;
        }

        if (Config.ENABLE_DEBUG_LOG.get()) {
            ResourceLocation dimId = mob.level().dimension().location();
            AdaptiveNemesisMod.LOGGER.debug(
                "[applyScaling] 开始处理实体={}, 类型={}, 维度={}, 位置=[{}, {}, {}]",
                mob.getName().getString(),
                mob.getType().getDescriptionId(),
                dimId,
                mob.getX(), mob.getY(), mob.getZ()
            );
        }

        // 获取附近玩家与平均强度
        List<ServerPlayer> nearbyPlayers = getNearbyPlayersInRange(mob);
        double avgStrength = computeAverageStrength(mob, nearbyPlayers);

        if (avgStrength <= 0) {
            return;
        }

        // 计算强化倍率（含新手保护减免）
        double multiplier = calculateMultiplier(mob, avgStrength, nearbyPlayers);

        // 触发 KubeJS 实体强化事件；返回负值表示事件取消了强化
        double modifiedMultiplier = KubeJSEventTrigger.triggerEntityScale(mob, multiplier);
        if (modifiedMultiplier < 0) {
            markScalingCancelled(mob);
            return;
        }
        multiplier = modifiedMultiplier;

        // 应用属性加成
        applyAttributeBonuses(mob, multiplier);

        // 标记为已强化
        markScaled(mob, multiplier);

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "敌人 {} 已强化: 倍率={}, 玩家强度={}",
                mob.getName().getString(),
                String.format("%.2f", multiplier),
                String.format("%.2f", avgStrength)
            );
        }
    }

    /**
     * 标记 KubeJS 取消的缩放
     *
     * 仍标记为已处理，避免重复触发事件。
     *
     * @param mob 目标生物
     */
    private void markScalingCancelled(Mob mob) {
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "敌人 {} 的强化被 KubeJS 事件取消",
                mob.getName().getString()
            );
        }
        mob.getPersistentData().putBoolean(SCALED_TAG, true);
        mob.getPersistentData().putDouble(SCALE_MULTIPLIER_TAG, 1.0);
        applyScaledMarker(mob);
    }

    /**
     * 缩放完成后写入已处理标记与倍率
     *
     * @param mob 目标生物
     * @param multiplier 最终应用的倍率
     */
    private void markScaled(Mob mob, double multiplier) {
        mob.getPersistentData().putBoolean(SCALED_TAG, true);
        mob.getPersistentData().putDouble(SCALE_MULTIPLIER_TAG, multiplier);

        // 打上LUCK属性标记（灵魂石穿越标记）
        // 这个标记会随着实体的核心属性一起被灵魂石保存和释放
        applyScaledMarker(mob);
    }

    /**
     * 计算装备难度倍率（供装备/附魔缩放系统使用的公共出口）
     *
     * 与敌人属性缩放完全同源：共享玩家空间索引、玩家强度评估、
     * 浮动层、缓动层、世界阶段加成、新手保护减免与硬上限/配置上限，
     * 保证装备难度与属性难度公式不脱节。
     *
     * @param mob 目标怪物
     * @return 难度倍率（附近无玩家时返回 1.0，下限 1.0）
     */
    public double calculateDifficultyMultiplier(Mob mob) {
        List<ServerPlayer> nearbyPlayers = getNearbyPlayersInRange(mob);
        double avgStrength = computeAverageStrength(mob, nearbyPlayers);
        if (avgStrength <= 0) {
            return 1.0;
        }
        return calculateMultiplier(mob, avgStrength, nearbyPlayers);
    }

    /**
     * 服务端维度 tick 后置事件 - 重建玩家空间索引
     *
     * 每 tick 按维度 + 区块重新分桶一次，供后续实体生成时快速查询附近玩家，
     * 避免每次生成都遍历全服玩家列表。
     *
     * @param event 维度 tick 事件
     */
    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        rebuildSpatialIndex(serverLevel);
    }

    /**
     * 重建指定维度的玩家空间索引
     *
     * 将玩家按当前所在区块分桶，存入 WeakHashMap 管理的维度索引中。
     *
     * @param level 服务端世界
     */
    private void rebuildSpatialIndex(ServerLevel level) {
        Map<ChunkPos, List<ServerPlayer>> index = new HashMap<>();
        for (ServerPlayer player : level.players()) {
            ChunkPos chunkPos = player.chunkPosition();
            index.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(player);
        }
        playerSpatialIndex.put(level, index);
    }

    /**
     * 从空间索引中查询附近玩家
     *
     * 先按区块网格快速筛选，再用距离平方做精确过滤。
     * 如果索引尚未建立（如维度首次 tick 前），回退到全量扫描。
     *
     * @param serverLevel 服务端世界
     * @param center      中心位置
     * @param rangeBlocks 范围（格数）
     * @return 附近玩家列表（可能为空）
     */
    private List<ServerPlayer> getNearbyPlayersFromIndex(ServerLevel serverLevel, Vec3 center, double rangeBlocks) {
        Map<ChunkPos, List<ServerPlayer>> index = playerSpatialIndex.get(serverLevel);
        if (index == null) {
            return getNearbyPlayers(serverLevel, center, rangeBlocks);
        }

        List<ServerPlayer> nearbyPlayers = new ArrayList<>();
        double rangeSq = rangeBlocks * rangeBlocks;

        int centerChunkX = Mth.floor(center.x) >> 4;
        int centerChunkZ = Mth.floor(center.z) >> 4;
        int chunkRadius = Mth.ceil(rangeBlocks / 16.0);

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkPos chunkPos = new ChunkPos(centerChunkX + dx, centerChunkZ + dz);
                List<ServerPlayer> players = index.get(chunkPos);
                if (players == null) {
                    continue;
                }
                for (ServerPlayer player : players) {
                    if (player.distanceToSqr(center) <= rangeSq) {
                        nearbyPlayers.add(player);
                    }
                }
            }
        }

        return nearbyPlayers;
    }

    /**
     * 获取指定位置附近的玩家列表（全量扫描回退）
     *
     * 使用服务端玩家列表遍历 + 距离平方检查，避免 AABB 扫描触发区块加载死锁。
     *
     * @param serverLevel 服务端世界
     * @param center      中心位置
     * @param rangeBlocks 范围（格数）
     * @return 附近玩家列表（可能为空）
     */
    private List<ServerPlayer> getNearbyPlayers(ServerLevel serverLevel, Vec3 center, double rangeBlocks) {
        double rangeSq = rangeBlocks * rangeBlocks;
        List<ServerPlayer> nearbyPlayers = new ArrayList<>();
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (player.level() == serverLevel && player.distanceToSqr(center) <= rangeSq) {
                nearbyPlayers.add(player);
            }
        }
        return nearbyPlayers;
    }

    /**
     * 获取生物附近范围内的玩家列表
     *
     * 使用玩家空间索引 + 距离平方检查，不触发区块加载：
     * AABB + getEntitiesOfClass 会触发范围内所有区块的加载，
     * 在 EntityJoinLevelEvent / FinalizeSpawnEvent 中可能导致区块加载死锁。
     *
     * @param mob 目标生物
     * @return 附近玩家列表（可能为空）
     */
    private List<ServerPlayer> getNearbyPlayersInRange(Mob mob) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }

        Vec3 pos = mob.position();
        double range = Config.AREA_SYNC_RANGE.get() * 16; // 区块转格数

        long scanStart = System.nanoTime();
        List<ServerPlayer> nearbyPlayers = getNearbyPlayersFromIndex(serverLevel, pos, range);
        long scanElapsedUs = (System.nanoTime() - scanStart) / 1000;

        if (nearbyPlayers.isEmpty() && Config.ENABLE_DEBUG_LOG.get() && scanElapsedUs > 1000) {
            // 扫描耗时 > 1ms 时才记录，避免刷屏
            AdaptiveNemesisMod.LOGGER.debug(
                "[Player扫描] 实体={}, 范围={}格, 耗时={}微秒, 附近无玩家",
                mob.getName().getString(),
                String.format("%.0f", range),
                scanElapsedUs
            );
        }

        return nearbyPlayers;
    }

    /**
     * 计算玩家列表的平均强度
     *
     * @param mob 目标生物（用于调试日志）
     * @param nearbyPlayers 附近玩家列表
     * @return 平均强度值，无有效玩家时返回 0.0
     */
    private double computeAverageStrength(Mob mob, List<ServerPlayer> nearbyPlayers) {
        if (nearbyPlayers.isEmpty()) {
            return 0.0;
        }

        // 计算平均强度
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

        double avgStrength = validPlayers > 0 ? totalStrength / validPlayers : 0.0;

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "[Player扫描] 实体={}, 附近玩家={}人, 有效玩家={}人, 平均强度={}",
                mob.getName().getString(),
                nearbyPlayers.size(),
                validPlayers,
                String.format("%.2f", avgStrength)
            );
        }

        return avgStrength;
    }

    /**
     * 计算强化倍率
     *
     * 流程：基础倍率 → 浮动层 → 缓动层 → 世界阶段 → 硬上限/配置上限 → 新手保护减免。
     * 本方法仅负责流程编排，各层计算拆分到子方法。
     *
     * @param mob 目标生物
     * @param playerStrength 玩家平均强度
     * @param nearbyPlayers 附近玩家列表（用于新手保护判定）
     * @return 强化倍率
     */
    private double calculateMultiplier(Mob mob, double playerStrength, List<ServerPlayer> nearbyPlayers) {
        // 基础倍率 = 1 + (玩家强度 * 难度系数 / 100)
        double baseMultiplier = 1.0 + (playerStrength * Config.DIFFICULTY_BASE_MULTIPLIER.get() / 100.0);

        // 应用附近玩家浮动调整，与玩家强度评估使用同一空间粒度
        double floatMultiplier = mob.level() instanceof ServerLevel serverLevel
            ? AdaptiveFloatSystem.getInstance().getFloatMultiplier(serverLevel, mob.position())
            : AdaptiveFloatSystem.getInstance().getFloatMultiplier();

        // 应用难度缓动
        double easedMultiplier = DifficultyTracker.getInstance().getEasedMultiplier(baseMultiplier * floatMultiplier);

        // 应用世界阶段加成
        double worldStageMultiplier = resolveWorldStageMultiplier();

        // 硬上限与配置上限
        double cappedMultiplier = applyMultiplierCaps(easedMultiplier * worldStageMultiplier);

        // 新手保护减免：附近任一玩家受保护时降低倍率
        double finalMultiplier = applyNewbieProtection(cappedMultiplier, nearbyPlayers);

        logMultiplierBreakdown(playerStrength, baseMultiplier, floatMultiplier, easedMultiplier,
            worldStageMultiplier, cappedMultiplier, finalMultiplier);

        return finalMultiplier;
    }

    /**
     * 解析世界阶段加成倍率
     *
     * @return 世界阶段倍率，功能未启用时返回 1.0
     */
    private double resolveWorldStageMultiplier() {
        if (!Config.ENABLE_WORLD_STAGE.get()) {
            return 1.0;
        }
        return WorldStageManager.getInstance().getWorldStageMultiplier();
    }

    /**
     * 应用倍率硬上限与配置上限
     *
     * @param preCap 上限应用前的倍率
     * @return 钳制后的倍率（下限 1.0）
     */
    private double applyMultiplierCaps(double preCap) {
        // 硬上限
        double afterHardCap = Math.min(preCap, MAX_TOTAL_MULTIPLIER);

        // 应用配置上限
        double finalMultiplier = afterHardCap;
        if (Config.ENABLE_ENEMY_BONUS_CAP.get()) {
            finalMultiplier = Math.min(finalMultiplier, Config.MAX_HEALTH_MULTIPLIER.get());
        }

        return Math.max(1.0, finalMultiplier);
    }

    /**
     * 应用新手保护倍率减免
     *
     * 附近任一玩家处于新手保护状态（自动阈值触发或命令强制开启）时，
     * 将缩放倍率乘以 (1 - NEWBIE_PROTECTION_REDUCTION)。
     *
     * @param multiplier 减免前倍率
     * @param nearbyPlayers 附近玩家列表
     * @return 减免后倍率
     */
    private double applyNewbieProtection(double multiplier, List<ServerPlayer> nearbyPlayers) {
        boolean anyProtected = hasProtectedNearbyPlayer(nearbyPlayers);
        return applyNewbieProtectionMultiplier(
            multiplier, anyProtected, Config.NEWBIE_PROTECTION_REDUCTION.get()
        );
    }

    /**
     * 检查附近玩家中是否存在处于新手保护状态的玩家
     *
     * @param nearbyPlayers 附近玩家列表
     * @return 如果任一玩家受保护返回 true
     */
    private boolean hasProtectedNearbyPlayer(List<ServerPlayer> nearbyPlayers) {
        NewbieProtectionHandler protectionHandler = NewbieProtectionHandler.getInstance();
        for (ServerPlayer player : nearbyPlayers) {
            if (protectionHandler.isProtected(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 应用新手保护倍率减免 - 纯计算版本，可测试
     *
     * 受保护时倍率乘以 (1 - 减免比例)，并钳制不低于 1.0
     * （保护只降低缩放幅度，不产生低于"不缩放"的负缩放）。
     *
     * @param multiplier 原倍率
     * @param anyProtected 是否存在受保护玩家
     * @param reduction 减免比例（0.0-1.0）
     * @return 调整后的倍率
     */
    static double applyNewbieProtectionMultiplier(double multiplier, boolean anyProtected, double reduction) {
        if (!anyProtected) {
            return multiplier;
        }
        return Math.max(1.0, multiplier * (1.0 - reduction));
    }

    /**
     * 输出倍率计算过程的调试日志
     *
     * @param playerStrength 玩家平均强度
     * @param baseMultiplier 基础倍率
     * @param floatMultiplier 浮动倍率
     * @param easedMultiplier 缓动后倍率
     * @param worldStageMultiplier 世界阶段倍率
     * @param cappedMultiplier 上限钳制后倍率
     * @param finalMultiplier 最终倍率
     */
    private void logMultiplierBreakdown(
        double playerStrength,
        double baseMultiplier,
        double floatMultiplier,
        double easedMultiplier,
        double worldStageMultiplier,
        double cappedMultiplier,
        double finalMultiplier
    ) {
        if (!Config.ENABLE_DEBUG_LOG.get()) {
            return;
        }
        AdaptiveNemesisMod.LOGGER.debug(
            "[缩放日志] playerStrength={}, DIFFICULTY_BASE_MULTIPLIER={}, " +
            "baseMultiplier={}, floatMultiplier={}, easedMultiplier={}, " +
            "worldStageMultiplier={}, MAX_TOTAL_MULTIPLIER={}, ENABLE_BONUS_CAP={}, " +
            "MAX_HEALTH_MULTIPLIER={}, finalMultiplier={}",
            String.format("%.2f", playerStrength),
            String.format("%.2f", Config.DIFFICULTY_BASE_MULTIPLIER.get()),
            String.format("%.2f", baseMultiplier),
            String.format("%.2f", floatMultiplier),
            String.format("%.2f", easedMultiplier),
            String.format("%.2f", worldStageMultiplier),
            MAX_TOTAL_MULTIPLIER,
            Config.ENABLE_ENEMY_BONUS_CAP.get(),
            String.format("%.2f", Config.MAX_HEALTH_MULTIPLIER.get()),
            String.format("%.2f", finalMultiplier)
        );
    }

    /**
     * 应用属性加成到生物
     *
     * 本方法仅负责阶段编排，具体属性计算拆分到各子方法中。
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     */
    private void applyAttributeBonuses(Mob mob, double multiplier) {
        long nanoStart = System.nanoTime();
        ScalingContext context = new ScalingContext(mob);

        logScalingEntry(mob, multiplier);

        ScalingFactors factors = new ScalingFactors(
            getRandomFactor(), getRandomFactor(), getRandomFactor(),
            getRandomFactor(), getRandomFactor()
        );

        // 主属性加成阶段
        if (applyMainAttributeBonuses(mob, multiplier, factors, context)) {
            return;
        }

        // 模组兼容加成阶段
        if (applyModCompatBonuses(mob, multiplier, context)) {
            return;
        }

        // 宿敌记忆加成
        applyNemesisBonuses(mob);
        if (context.checkStage(nanoStart, "afterNemesisBonuses", "nemesis")) {
            return;
        }

        // 最终状态验证
        validateMobState(mob);
        if (context.checkStage(nanoStart, "afterValidate", "validate")) {
            return;
        }

        logScalingResult(mob, nanoStart, factors);
    }

    /**
     * 记录缩放入口调试日志
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     */
    private void logScalingEntry(Mob mob, double multiplier) {
        if (!Config.ENABLE_DEBUG_LOG.get()) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        boolean alreadyScaled = data.contains(ORIGINAL_HEALTH_TAG);
        AttributeInstance healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        double currentHealth = healthAttr != null ? healthAttr.getBaseValue() : -1;
        double originalHealth = alreadyScaled ? data.getDouble(ORIGINAL_HEALTH_TAG) : currentHealth;

        AdaptiveNemesisMod.LOGGER.debug(
            "[缩放入口] mob={}({}), UUID={}, alreadyScaled={}, " +
            "currentHealth={}, originalHealth={}, multiplier={}, " +
            "SCALED_TAG={}, dim={}",
            mob.getName().getString(),
            mob.getType().getDescriptionId(),
            mob.getUUID(),
            alreadyScaled,
            String.format("%.2f", currentHealth),
            String.format("%.2f", originalHealth),
            String.format("%.2f", multiplier),
            data.getBoolean(SCALED_TAG),
            mob.level().dimension().location()
        );
    }

    /**
     * 应用主属性加成（生命、伤害、护甲、速度、攻速、韧性）
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     * @param factors 随机分布因子
     * @param context 缩放上下文（用于超时检测与看门狗）
     * @return 如果触发超时则返回 true，调用方应终止后续阶段
     */
    private boolean applyMainAttributeBonuses(Mob mob, double multiplier, ScalingFactors factors, ScalingContext context) {
        applyHealthBonus(mob, multiplier, factors.health());
        if (context.checkStage(System.nanoTime(), "afterHealthBonus", "health")) {
            return true;
        }

        applyDamageBonus(mob, multiplier, factors.damage());
        if (context.checkStage(System.nanoTime(), "afterDamageBonus", "damage")) {
            return true;
        }

        applyArmorBonus(mob, multiplier, factors.armor());
        if (context.checkStage(System.nanoTime(), "afterArmorBonus", "armor")) {
            return true;
        }

        if (Config.FIX_SPEED_BONUS_TO_ZERO.get()) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "敌人 {} 速度加成已固定为0（防止跑得太快）",
                    mob.getName().getString()
                );
            }
        } else {
            applySpeedBonus(mob, multiplier);
        }

        applyAttackSpeedBonus(mob, multiplier, factors.attackSpeed());
        if (context.checkStage(System.nanoTime(), "afterAttackSpeedBonus", "attackSpeed")) {
            return true;
        }

        applyToughnessBonus(mob, multiplier, factors.toughness());
        if (context.checkStage(System.nanoTime(), "afterToughnessBonus", "toughness")) {
            return true;
        }

        return false;
    }

    /**
     * 应用模组兼容属性加成（Epic Fight / Iron's Spells）
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     * @param context 缩放上下文
     * @return 如果触发超时则返回 true
     */
    private boolean applyModCompatBonuses(Mob mob, double multiplier, ScalingContext context) {
        if (ModCompatManager.isEpicFightLoaded()) {
            double epicFightRandomFactor = getRandomFactor();
            ModCompatManager.getEpicFightCompat().applyMobBuffs(mob, multiplier * epicFightRandomFactor);
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "敌人 {} 已应用 Epic Fight 属性加成，基础倍率={}, 随机因子={}",
                    mob.getName().getString(),
                    String.format("%.2f", multiplier),
                    String.format("%.2f", epicFightRandomFactor)
                );
            }
        }
        if (context.checkStage(System.nanoTime(), "afterEpicFight", "epicFight")) {
            return true;
        }

        if (ModCompatManager.isIronsSpellsLoaded()) {
            double ironsSpellsRandomFactor = getRandomFactor();
            ModCompatManager.getIronsSpellsCompat().applyMobBuffs(mob, multiplier * ironsSpellsRandomFactor);
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "敌人 {} 已应用 Iron's Spells 属性加成，基础倍率={}, 随机因子={}",
                    mob.getName().getString(),
                    String.format("%.2f", multiplier),
                    String.format("%.2f", ironsSpellsRandomFactor)
                );
            }
        }
        if (context.checkStage(System.nanoTime(), "afterIronsSpells", "ironsSpells")) {
            return true;
        }

        return false;
    }

    /**
     * 记录缩放结果调试日志
     *
     * @param mob 目标生物
     * @param nanoStart 缩放开始时间戳
     * @param factors 随机分布因子
     */
    private void logScalingResult(Mob mob, long nanoStart, ScalingFactors factors) {
        if (!Config.ENABLE_DEBUG_LOG.get()) {
            return;
        }
        long elapsedUs = (System.nanoTime() - nanoStart) / 1000;
        AttributeInstance finalHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance finalDamage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance finalArmor = mob.getAttribute(Attributes.ARMOR);
        CompoundTag data = mob.getPersistentData();
        double origH = data.contains(ORIGINAL_HEALTH_TAG) ? data.getDouble(ORIGINAL_HEALTH_TAG) : -1;

        AdaptiveNemesisMod.LOGGER.debug(
            "[缩放结果] mob={}, origHealth={}, finalHealth={}, " +
            "finalDamage={}, finalArmor={}, healthRF={}, damageRF={}, " +
            "armorRF={}, hasOriginalValues={}, 耗时={}微秒",
            mob.getName().getString(),
            String.format("%.2f", origH),
            finalHealth != null ? String.format("%.2f", finalHealth.getBaseValue()) : "N/A",
            finalDamage != null ? String.format("%.2f", finalDamage.getBaseValue()) : "N/A",
            finalArmor != null ? String.format("%.2f", finalArmor.getBaseValue()) : "N/A",
            String.format("%.2f", factors.health()),
            String.format("%.2f", factors.damage()),
            String.format("%.2f", factors.armor()),
            data.contains(ORIGINAL_HEALTH_TAG),
            elapsedUs
        );
    }

    /**
     * 缩放阶段上下文
     *
     * 封装实体键、看门狗阶段更新与超时检测，避免主流程中重复代码。
     */
    private class ScalingContext {
        private final String entityKey;
        private final Mob mob;

        ScalingContext(Mob mob) {
            this.mob = mob;
            this.entityKey = WatchdogService.makeEntityKey(
                mob.getName().getString(),
                mob.getX(), mob.getY(), mob.getZ()
            );
        }

        /**
         * 检查阶段是否超时，同时更新看门狗状态
         *
         * @param nanoNow 当前纳秒时间
         * @param stageName 看门狗阶段名
         * @param timeoutLabel 超时标记名
         * @return 如果超时返回 true
         */
        boolean checkStage(long nanoNow, String stageName, String timeoutLabel) {
            if (Config.ENABLE_WATCHDOG.get()) {
                WatchdogService.getInstance().updateStage(stageName);
            }
            return checkScalingTimeout(nanoNow, entityKey, mob, timeoutLabel);
        }
    }

    /**
     * 获取或存储原始属性基础值 - 纯计算版本，可测试
     *
     * NBT 标签存在时直接读取（保证幂等，重复缩放不叠加）；
     * 标签丢失时（灵魂石、魂符等工具重置 NBT）使用实体类型默认属性值
     * 作为"真·原始值"并写入标签，防止已缩放的高属性值被当作原始值
     * 再次缩放导致指数级爆炸。
     *
     * @param data 实体持久化数据
     * @param tagKey 原始值存储的NBT标签键
     * @param defaultBase 实体类型的默认属性值（标签缺失时使用）
     * @return 原始属性基础值
     */
    static double getOrStoreOriginalBase(CompoundTag data, String tagKey, double defaultBase) {
        if (data.contains(tagKey)) {
            return data.getDouble(tagKey);
        }
        data.putDouble(tagKey, defaultBase);
        return defaultBase;
    }

    /**
     * 获取实体类型的默认属性基础值
     *
     * 通过 DefaultAttributes 查询该实体类型注册时的默认属性值。
     * 用于 NBT 标记丢失时（如灵魂石抓取后重置）获取真正的原始值，
     * 防止已缩放的高血量被当作"原始值"再次缩放导致指数级爆炸。
     *
     * @param mob           目标生物
     * @param attribute     要查询的属性
     * @param fallbackValue 查询失败时的回退值
     * @return 实体类型的默认属性值
     */
    public static double getDefaultAttributeBase(LivingEntity mob, Holder<Attribute> attribute, double fallbackValue) {
        String attributeKey = attribute.getRegisteredName();
        String mobTypeDesc = mob.getType().getDescriptionId();

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "[DefaultAttributes] 开始查询: 实体={}, 类型={}, 属性={}, 回退值={}",
                mob.getName().getString(), mobTypeDesc, attributeKey,
                String.format("%.2f", fallbackValue)
            );
        }

        try {
            // DefaultAttributes.getSupplier 需要 EntityType<? extends LivingEntity>
            // mob.getType() 返回的是 EntityType<? extends Entity>，需要安全转型
            @SuppressWarnings("unchecked")
            var entityType = (EntityType<? extends LivingEntity>) mob.getType();
            var supplier = DefaultAttributes.getSupplier(entityType);
            if (supplier != null) {
                double result = supplier.getBaseValue(attribute);
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug(
                        "[DefaultAttributes] 查询成功: 实体={}, 属性={}, 原始值={}",
                        mob.getName().getString(), attributeKey,
                        String.format("%.2f", result)
                    );
                }
                return result;
            } else {
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug(
                        "[DefaultAttributes] 未找到 Supplier: 实体={}, 类型={}, 使用回退值={}",
                        mob.getName().getString(), mobTypeDesc,
                        String.format("%.2f", fallbackValue)
                    );
                }
            }
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.warn(
                "获取实体 {} (类型: {}) 的默认属性 {} 失败: {}",
                mob.getName().getString(), mobTypeDesc, attributeKey, e.getMessage()
            );
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.warn("异常堆栈:", e);
            }
        }
        return fallbackValue;
    }

    /**
     * 检查缩放处理是否超时
     * <p>
     * 如果缩放处理时间超过 {@link #SCALE_TIMEOUT_NANOS}（30 秒），
     * 认为可能触发了区块加载死锁（如 Cataclysm 模组的结构生成）。
     * 将实体标记为问题实体并设置 NBT 超时标记，
     * 后续该实体再次加入世界时会直接跳过缩放处理。
     *
     * @param nanoStart 缩放开始时间戳（纳秒）
     * @param entityKey 实体标识键
     * @param mob       目标生物
     * @param stage     当前处理阶段名称（用于日志）
     * @return 如果超时返回 true，调用方应立即 return 跳过后续阶段
     */
    private boolean checkScalingTimeout(long nanoStart, String entityKey, Mob mob, String stage) {
        long elapsedNanos = System.nanoTime() - nanoStart;
        if (elapsedNanos > SCALE_TIMEOUT_NANOS) {
            long elapsedMs = elapsedNanos / 1_000_000;
            AdaptiveNemesisMod.LOGGER.error(
                "[缩放超时] 实体 {} (阶段: {}) 处理耗时 {}ms，超过阈值 {}ms！" +
                "标记为问题实体并跳过后续缩放阶段",
                mob.getName().getString(), stage, elapsedMs,
                SCALE_TIMEOUT_NANOS / 1_000_000
            );

            // 标记看门狗问题实体集
            if (Config.ENABLE_WATCHDOG.get()) {
                WatchdogService.getInstance().markEntityProblematic(entityKey);
            }

            // 设置 NBT 超时标记，确保后续世界加入也跳过
            mob.getPersistentData().putBoolean(SCALE_TIMEOUT_TAG, true);

            return true;
        }
        return false;
    }

    /**
     * 缩放完成后进行怪物状态验证
     * 检查并修复可能导致怪物无法被攻击的异常状态：
     * - 清除 Invulnerable 标签
     * - 修复 NaN/Infinity 属性值
     * - 验证血量有效性
     *
     * 死亡或未存活的实体直接跳过，禁止把 0 血实体拉满复活。
     *
     * @param mob 目标生物
     */
    private void validateMobState(Mob mob) {
        // 0 血/死亡实体防护：不存活时禁止修复，避免把 0 血实体拉满
        if (!mob.isAlive()) {
            return;
        }

        sanitizeInvulnerableFlag(mob);
        sanitizeAttributeValues(mob);
        ensureCurrentHealthValid(mob);
    }

    /**
     * 清除实体真正的无敌标志（isInvulnerable/setInvulnerable 操作 Entity.invulnerable 字段）
     *
     * 注意：getPersistentData().getBoolean("Invulnerable") 是模组自定义数据，和实体无敌无关
     *
     * @param mob 目标生物
     */
    private void sanitizeInvulnerableFlag(Mob mob) {
        if (mob.isInvulnerable()) {
            mob.setInvulnerable(false);
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.warn(
                    "缩放后修复怪物 {} 的 Invulnerable 标志",
                    mob.getName().getString()
                );
            }
        }
    }

    /**
     * 修复关键属性的非法值（NaN/Infinity/越界）
     *
     * @param mob 目标生物
     */
    private void sanitizeAttributeValues(Mob mob) {
        sanitizeHealthAttribute(mob);
        sanitizeDamageAttribute(mob);
        sanitizeArmorAttribute(mob);
    }

    /**
     * 修复血量上限属性的非法值
     *
     * @param mob 目标生物
     */
    private void sanitizeHealthAttribute(Mob mob) {
        var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) {
            return;
        }
        double health = healthAttr.getBaseValue();
        if (Double.isNaN(health) || Double.isInfinite(health) || health < 1.0) {
            healthAttr.setBaseValue(20.0); // 直接设为默认20点血量
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.warn(
                    "修复怪物 {} 的无效 MaxHealth: {}",
                    mob.getName().getString(), health
                );
            }
        }
    }

    /**
     * 修复攻击伤害属性的非法值
     *
     * @param mob 目标生物
     */
    private void sanitizeDamageAttribute(Mob mob) {
        var damageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr == null) {
            return;
        }
        double dmg = damageAttr.getBaseValue();
        if (Double.isNaN(dmg) || Double.isInfinite(dmg)) {
            damageAttr.setBaseValue(1.0); // 默认1点伤害
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.warn(
                    "修复怪物 {} 的无效 AttackDamage: {}",
                    mob.getName().getString(), dmg
                );
            }
        }
    }

    /**
     * 修复护甲属性的非法值
     *
     * @param mob 目标生物
     */
    private void sanitizeArmorAttribute(Mob mob) {
        var armorAttr = mob.getAttribute(Attributes.ARMOR);
        if (armorAttr == null) {
            return;
        }
        double armor = armorAttr.getBaseValue();
        if (Double.isNaN(armor) || Double.isInfinite(armor) || armor < 0) {
            armorAttr.setBaseValue(0.0); // 默认0护甲
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.warn(
                    "修复怪物 {} 的无效 Armor: {}",
                    mob.getName().getString(), armor
                );
            }
        }
    }

    /**
     * 确保怪物当前血量有效（NaN/Infinity/非正值时拉满至最大血量）
     *
     * @param mob 目标生物
     */
    private void ensureCurrentHealthValid(Mob mob) {
        float currentHealth = mob.getHealth();
        if (Double.isNaN(currentHealth) || Double.isInfinite(currentHealth) || currentHealth <= 0) {
            mob.setHealth(mob.getMaxHealth());
        }
    }

    /**
     * 属性计算器函数式接口
     * 用于自定义属性加成的计算逻辑
     */
    @FunctionalInterface
    private interface AttributeCalculator {
        /**
         * 计算新的属性值
         *
         * @param originalValue 原始属性值
         * @param effectiveMultiplier 有效倍率（基础倍率 * 随机因子）
         * @return 新的属性值
         */
        double calculate(double originalValue, double effectiveMultiplier);
    }

    /**
     * 通用的属性加成方法
     *
     * 原始值来源：NBT 标签存在时读取标签（幂等）；
     * 标签丢失时以实体类型默认属性值回退（{@link #getDefaultAttributeBase}），
     * 防止清 NBT 后二次缩放叠加。
     *
     * @param mob 目标生物
     * @param attribute 属性类型
     * @param multiplier 基础倍率
     * @param randomFactor 随机因子
     * @param maxMultiplier 上限倍率（null 表示无上限）
     * @param calculator 属性计算器（null 表示使用默认的乘法计算）
     * @param originalTag 原始值存储的NBT标签键
     */
    private void applyAttributeBonus(
        Mob mob,
        Holder<Attribute> attribute,
        double multiplier,
        double randomFactor,
        Double maxMultiplier,
        AttributeCalculator calculator,
        String originalTag
    ) {
        AttributeInstance attrInstance = mob.getAttribute(attribute);
        if (attrInstance == null) {
            return;
        }

        CompoundTag data = mob.getPersistentData();
        double originalValue = getOrStoreOriginalBase(
            data, originalTag,
            getDefaultAttributeBase(mob, attribute, attrInstance.getBaseValue())
        );
        double effectiveMultiplier = multiplier * randomFactor;

        if (maxMultiplier != null) {
            effectiveMultiplier = Math.min(effectiveMultiplier, maxMultiplier);
        }

        double newValue;
        if (calculator != null) {
            newValue = calculator.calculate(originalValue, effectiveMultiplier);
        } else {
            newValue = originalValue * effectiveMultiplier;
        }

        if (newValue < originalValue) {
            newValue = originalValue;
        }

        attrInstance.setBaseValue(newValue);
    }

    /**
     * 获取随机分布因子
     * 如果禁用了随机分布，返回1.0
     *
     * @return 随机因子，范围在 [minFactor, maxFactor] 之间
     */
    private double getRandomFactor() {
        if (!Config.ENABLE_RANDOM_DISTRIBUTION.get()) {
            return 1.0;
        }
        double minFactor = Config.RANDOM_MIN_FACTOR.get();
        double maxFactor = Config.RANDOM_MAX_FACTOR.get();
        return minFactor + ThreadLocalRandom.current().nextDouble() * (maxFactor - minFactor);
    }

    /**
     * 应用血量加成（带随机分布）
     *
     * 本方法仅负责流程编排：L2Hostility 兼容检查、原始血量解析、
     * 属性写入与延迟填满血量分别拆分到各子方法中。
     * 使用原始值确保幂等性 - 防止重复缩放。
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     * @param randomFactor 随机因子
     */
    private void applyHealthBonus(Mob mob, double multiplier, double randomFactor) {
        // 当 L2Hostility 加载且兼容模式启用时，其 TANK 特质使用 ADD_MULTIPLIED_TOTAL 修改 MAX_HEALTH，
        // 与我们修改基础值会形成乘法叠加导致血量爆炸，因此跳过我们的血量缩放
        if (L2HostilityCompat.shouldSkipHealthAndSpeedScaling()) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AttributeInstance healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
                double currentHealth = healthAttr != null ? healthAttr.getBaseValue() : -1;
                AdaptiveNemesisMod.LOGGER.debug(
                    "L2Hostility 兼容: 跳过 {} 的血量加成, 当前基础血量={}",
                    mob.getName().getString(), currentHealth
                );
            }
            return;
        }

        AttributeInstance healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) {
            return;
        }

        CompoundTag data = mob.getPersistentData();
        double originalBase = resolveOriginalHealthBase(mob, data, healthAttr);

        double effectiveMultiplier = Math.min(multiplier * randomFactor, Config.MAX_HEALTH_MULTIPLIER.get());
        double newMax = originalBase * effectiveMultiplier;
        if (newMax < originalBase) {
            newMax = originalBase;
        }
        healthAttr.setBaseValue(newMax);

        // 立即尝试设置满血
        mob.setHealth(mob.getMaxHealth());

        // 延迟到下一个 tick 再填满血量（关键！）
        scheduleDelayedHealthFix(mob, data);

        if (Config.ENABLE_DEBUG_LOG.get() && randomFactor != 1.0) {
            AdaptiveNemesisMod.LOGGER.debug(
                "敌人 {} 血量加成: 基础倍率={}, 随机因子={}, 最终倍率={}",
                mob.getName().getString(),
                String.format("%.2f", multiplier),
                String.format("%.2f", randomFactor),
                String.format("%.2f", effectiveMultiplier)
            );
        }
    }

    /**
     * 解析原始基础血量
     *
     * NBT 标记存在时直接读取（幂等）；标记丢失（灵魂石、魂符、生物套索等
     * 工具抓取后重置 NBT）时使用 DefaultAttributes 查询实体类型注册时的默认血量
     * 作为"真·原始值"，防止已经缩放过的高血量被当作原始值再次缩放导致指数级爆炸。
     *
     * @param mob 目标生物
     * @param data 实体持久化数据
     * @param healthAttr 血量属性实例
     * @return 原始基础血量
     */
    private double resolveOriginalHealthBase(Mob mob, CompoundTag data, AttributeInstance healthAttr) {
        boolean tagMissing = !data.contains(ORIGINAL_HEALTH_TAG);
        double originalBase = getOrStoreOriginalBase(
            data, ORIGINAL_HEALTH_TAG,
            getDefaultAttributeBase(mob, Attributes.MAX_HEALTH, healthAttr.getBaseValue())
        );

        if (tagMissing && Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "防血量爆炸: {} 的 NBT 标记丢失，使用实体类型默认血量 {} (当前基础血量={})",
                mob.getName().getString(),
                String.format("%.2f", originalBase),
                String.format("%.2f", healthAttr.getBaseValue())
            );
        }

        return originalBase;
    }

    /**
     * 延迟到下一个 tick 填满血量
     *
     * 解决 setBaseValue 后属性未立即传播，setHealth 被 clamp 在旧最大值的问题。
     *
     * @param mob 目标生物
     * @param data 实体持久化数据
     */
    private void scheduleDelayedHealthFix(Mob mob, CompoundTag data) {
        if (mob.level().isClientSide() || !(mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (data.getBoolean(HEALTH_FIX_SCHEDULED_TAG)) {
            return;
        }
        data.putBoolean(HEALTH_FIX_SCHEDULED_TAG, true);
        serverLevel.getServer().execute(() -> {
            data.putBoolean(HEALTH_FIX_SCHEDULED_TAG, false);
            if (!mob.isRemoved() && mob.isAlive()) {
                mob.setHealth(mob.getMaxHealth());
            }
        });
    }

    /**
     * 应用伤害加成（带随机分布）
     * 使用原始值确保幂等性 - 防止重复缩放
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     * @param randomFactor 随机因子
     */
    private void applyDamageBonus(Mob mob, double multiplier, double randomFactor) {
        applyAttributeBonus(
            mob,
            Attributes.ATTACK_DAMAGE,
            multiplier,
            randomFactor,
            Config.MAX_DAMAGE_MULTIPLIER.get(),
            null,
            ORIGINAL_DAMAGE_TAG
        );
    }

    /**
     * 应用护甲加成（带随机分布）
     * 使用原始值确保幂等性 - 防止重复缩放
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     * @param randomFactor 随机因子
     */
    private void applyArmorBonus(Mob mob, double multiplier, double randomFactor) {
        applyAttributeBonus(
            mob,
            Attributes.ARMOR,
            multiplier,
            randomFactor,
            Config.MAX_ARMOR_MULTIPLIER.get(),
            null,
            ORIGINAL_ARMOR_TAG
        );
    }

    /**
     * 应用护甲韧性加成（带随机分布）
     * 使用原始值确保幂等性 - 防止重复缩放。
     * 韧性作为防御属性，上限复用护甲倍率上限（MAX_ARMOR_MULTIPLIER），
     * 避免无上限的韧性在极端倍率下失控。
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     * @param randomFactor 随机因子
     */
    private void applyToughnessBonus(Mob mob, double multiplier, double randomFactor) {
        applyAttributeBonus(
            mob,
            Attributes.ARMOR_TOUGHNESS,
            multiplier,
            randomFactor,
            Config.MAX_ARMOR_MULTIPLIER.get(),
            null,
            ORIGINAL_TOUGHNESS_TAG
        );
    }

    /**
     * 应用移动速度加成（原版，不带随机分布）
     * 使用原始值确保幂等性
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     */
    private void applySpeedBonus(Mob mob, double multiplier) {
        // L2Hostility 加载且兼容模式启用时，其 SPEEDY 特质使用 ADD_MULTIPLIED_TOTAL 修改 MOVEMENT_SPEED，
        // 与我们修改基础值会形成乘法叠加，跳过我们的速度缩放
        if (L2HostilityCompat.shouldSkipHealthAndSpeedScaling()) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AttributeInstance speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
                double currentSpeed = speedAttr != null ? speedAttr.getBaseValue() : -1;
                AdaptiveNemesisMod.LOGGER.debug(
                    "L2Hostility 兼容: 跳过 {} 的速度加成, 当前基础速度={}",
                    mob.getName().getString(), currentSpeed
                );
            }
            return;
        }

        applyAttributeBonus(
            mob,
            Attributes.MOVEMENT_SPEED,
            multiplier,
            1.0,
            2.0,
            (original, effective) -> {
                // 速度加成：基础 + (倍率-1) * 0.3
                double speedMultiplier = 1.0 + (effective - 1.0) * 0.3;
                return original * speedMultiplier;
            },
            ORIGINAL_SPEED_TAG
        );
    }

    /**
     * 应用攻击速度加成 - 防止史诗战斗无限硬直（带随机分布）
     * 使用原始值确保幂等性
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     * @param randomFactor 随机因子
     */
    private void applyAttackSpeedBonus(Mob mob, double multiplier, double randomFactor) {
        applyAttributeBonus(
            mob,
            Attributes.ATTACK_SPEED,
            multiplier,
            randomFactor,
            2.0,
            (original, effective) -> {
                // 攻击速度随倍率增加，最高增加100%
                double attackSpeedMultiplier = 1.0 + (effective - 1.0) * 0.25;
                return original * attackSpeedMultiplier;
            },
            ORIGINAL_ATTACK_SPEED_TAG
        );
    }

    /**
     * 应用宿敌记忆加成
     *
     * @param mob 目标生物
     */
    private void applyNemesisBonuses(Mob mob) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 获取附近的玩家，应用距离最近玩家的宿敌档案加成
        Vec3 pos = mob.position();
        double range = Config.AREA_SYNC_RANGE.get() * 16;

        // 使用玩家列表迭代替代 AABB 扫描，避免触发区块加载死锁
        List<ServerPlayer> nearbyPlayers = getNearbyPlayersFromIndex(serverLevel, pos, range);
        if (nearbyPlayers.isEmpty()) {
            return;
        }

        // 选择距离最近的玩家应用宿敌加成，避免玩家列表顺序导致的不公平
        ServerPlayer nearestPlayer = nearbyPlayers.stream()
            .min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(pos)))
            .orElse(null);
        if (nearestPlayer == null) {
            return;
        }

        var profile = NemesisMemorySystem.getInstance().getProfile(nearestPlayer.getUUID());
        if (profile == null) {
            return;
        }

        applyNemesisProfileBonuses(mob, profile);
    }

    /**
     * 将宿敌档案加成写入属性
     *
     * @param mob 目标生物
     * @param profile 宿敌档案（最近玩家的）
     */
    private void applyNemesisProfileBonuses(Mob mob, com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisProfile profile) {
        // 应用攻击加成
        double attackBonus = profile.getAttackBonus();
        var damageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null && attackBonus > 0) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() * (1.0 + attackBonus));
        }

        // 应用速度加成
        double speedBonus = profile.getSpeedBonus();
        var speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null && speedBonus > 0) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * (1.0 + speedBonus));
        }

        // 应用生命加成
        double healthBonus = profile.getHealthBonus();
        var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null && healthBonus > 0) {
            double newHealth = healthAttr.getBaseValue() * (1.0 + healthBonus);
            healthAttr.setBaseValue(newHealth);
        }
    }

    /**
     * 检查生物是否已经被强化
     *
     * @param mob 目标生物
     * @return 如果已强化返回true
     */
    public boolean isScaled(Mob mob) {
        return mob.getPersistentData().getBoolean(SCALED_TAG);
    }

    /**
     * 获取生物的强化倍率
     *
     * @param mob 目标生物
     * @return 强化倍率，未强化或 NBT 标签缺失时返回 1.0
     */
    public double getScaleMultiplier(Mob mob) {
        return getScaleMultiplierFromTag(mob.getPersistentData());
    }

    /**
     * 从 NBT 读取强化倍率 - 纯计算版本，可测试
     *
     * @param data 实体持久化数据
     * @return 强化倍率，标签缺失时返回 1.0
     */
    static double getScaleMultiplierFromTag(CompoundTag data) {
        return data.contains(SCALE_MULTIPLIER_TAG) ? data.getDouble(SCALE_MULTIPLIER_TAG) : 1.0;
    }
}
