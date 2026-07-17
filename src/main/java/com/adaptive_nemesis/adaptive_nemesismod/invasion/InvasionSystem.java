package com.adaptive_nemesis.adaptive_nemesismod.invasion;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.WorldStageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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

    public static void setInstance(InvasionSystem instance) {
        INSTANCE = instance;
    }

    /** 活动中的入侵事件列表 */
    private final Map<UUID, ActiveInvasion> activeInvasions = new HashMap<>();

    /** 玩家击杀亡灵生物计数 */
    private final Map<UUID, Integer> playerUndeadKills = new HashMap<>();

    /** 是否已完成首次入侵 */
    private final Map<UUID, Boolean> hasCompletedFirstInvasion = new HashMap<>();

    /** 随机数生成器 */
    private final Random random = new Random();

    /** 首次入侵所需击杀数 */
    private static final int FIRST_INVASION_KILLS_REQUIRED = 25;

    /** 后续入侵所需击杀数 */
    private static final int SUBSEQUENT_INVASION_KILLS_REQUIRED = 100;

    /** 入侵区域半径 */
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
     */
    private void processActiveInvasions() {
        Iterator<Map.Entry<UUID, ActiveInvasion>> iterator = activeInvasions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ActiveInvasion> entry = iterator.next();
            ActiveInvasion invasion = entry.getValue();

            if (invasion.isCompleted()) {
                invasion.removeAllBossBars();
                iterator.remove();
                continue;
            }

            invasion.tick();

            // 状态机转换
            switch (invasion.phase.state) {
                case CREATED -> {
                    // 触发时立即进入STARTED状态
                    invasion.setState(ActiveInvasion.Phase.State.STARTED, ActiveInvasion.START_DURATION);
                    invasion.setWaveInfoText(Component.literal("亡灵军团正在逼近...").withStyle(ChatFormatting.WHITE));
                    invasion.setWaveInfoVisible(true);
                    triggerLightningEffect(invasion);
                    if (Config.ENABLE_DEBUG_LOG.get()) {
                        AdaptiveNemesisMod.LOGGER.debug("[入侵状态] {} -> STARTED (6.4秒)", invasion.getPlayerName());
                    }
                }
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
                case WAVE_ONGOING -> {
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
                        invasion.setWaveInfoText(Component.literal("亡灵军团获胜！").withStyle(ChatFormatting.DARK_RED));
                        if (Config.ENABLE_DEBUG_LOG.get()) {
                            AdaptiveNemesisMod.LOGGER.debug("[入侵状态] {} 波次超时", invasion.getPlayerName());
                        }
                        break;
                    }

                    // 检查是否所有怪物已死亡
                    if (invasion.areAllMobsDead()) {
                        invasion.setState(ActiveInvasion.Phase.State.UNDEAD_DEFEATED, 0);
                        invasion.setWaveInfoText(Component.literal(
                            String.format("第 %s 波已清除！", getRomanNumeral(invasion.currentWave))
                        ).withStyle(ChatFormatting.GREEN));

                        notifyAllPlayersInArea(invasion, Component.literal(
                            String.format("第 %s 波已被清除！", getRomanNumeral(invasion.currentWave))
                        ).withStyle(ChatFormatting.GREEN));

                        if (Config.ENABLE_DEBUG_LOG.get()) {
                            AdaptiveNemesisMod.LOGGER.debug("[入侵状态] {} 波次{}完成", invasion.getPlayerName(), invasion.currentWave);
                        }
                    }
                }
                case UNDEAD_DEFEATED -> {
                    // 非最后一波：继续下一波，startWavePreparation内部已调用startNextWave
                    if (!invasion.isLastWave()) {
                        startWavePreparation(invasion);
                    } else {
                        // 最后一波：胜利
                        invasion.setState(ActiveInvasion.Phase.State.FINISHED, 0);
                        invasion.setCompleted(true);
                        invasion.setWaveInfoText(Component.literal("亡灵军团已被击败！").withStyle(ChatFormatting.GREEN));
                        handleInvasionVictory(invasion);
                        hasCompletedFirstInvasion.put(invasion.getPlayerUUID(), true);
                        if (Config.ENABLE_DEBUG_LOG.get()) {
                            AdaptiveNemesisMod.LOGGER.debug("[入侵状态] {} 全部波次完成，胜利！", invasion.getPlayerName());
                        }
                    }
                }
                case UNDEAD_WON -> {
                    // 超时失败
                    invasion.setState(ActiveInvasion.Phase.State.FINISHED, 0);
                    invasion.setCompleted(true);
                    invasion.setDefeated(true);

                    notifyAllPlayersInArea(invasion, Component.literal(
                        "亡灵军团击溃了防线..."
                    ).withStyle(ChatFormatting.DARK_RED));

                    if (Config.ENABLE_DEBUG_LOG.get()) {
                        AdaptiveNemesisMod.LOGGER.debug("[入侵状态] {} 超时失败", invasion.getPlayerName());
                    }
                }
                case FINISHED -> {
                    invasion.removeAllBossBars();
                    iterator.remove();
                }
            }
        }
    }

    // ======================== 波次管理 ========================

    /**
     * 开始波次准备阶段
     * 设置10秒倒计时，生成MobInfo列表
     */
    private void startWavePreparation(ActiveInvasion invasion) {
        invasion.startNextWave();
        invasion.setState(ActiveInvasion.Phase.State.WAVE_PREPARING, ActiveInvasion.PREPARING_DURATION);

        invasion.setWaveInfoText(Component.literal(
            String.format("亡灵军团 - 第 %s 波 (准备中...)", getRomanNumeral(invasion.currentWave))
        ).withStyle(ChatFormatting.WHITE));

        // 生成MobInfo列表
        List<EnemySpawnConfig> configs = getWaveEnemyConfig(invasion.currentWave);
        invasion.mobsLeft.clear();
        invasion.boss = null;
        invasion.hideBossBar();

        int totalHealth = 0;
        for (EnemySpawnConfig config : configs) {
            for (int i = 0; i < config.count(); i++) {
                boolean isBoss = config.entityType() == EntityType.GIANT;
                ActiveInvasion.MobInfo mobInfo = new ActiveInvasion.MobInfo(
                    config.entityType(),
                    null,
                    isBoss
                );
                invasion.mobsLeft.add(mobInfo);
                totalHealth += getBaseHealth(config.entityType());
            }
        }

        invasion.phase.healthTotal = totalHealth;

        // 每波随机选择方向
        ActiveInvasion.Direction[] dirs = ActiveInvasion.Direction.values();
        invasion.direction = dirs[random.nextInt(dirs.length)];

        triggerLightningEffect(invasion);

        notifyAllPlayersInArea(invasion, Component.literal(
            String.format("第 %s 波敌人即将来袭！准备战斗！", getRomanNumeral(invasion.currentWave))
        ).withStyle(ChatFormatting.GOLD));

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] {} 第{}波准备中，方向={}，怪物数={}",
                invasion.getPlayerName(), invasion.currentWave, invasion.direction, invasion.mobsLeft.size());
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

        invasion.setWaveInfoText(Component.literal(
            String.format("亡灵军团 - 第 %s 波", getRomanNumeral(invasion.currentWave))
        ).withStyle(ChatFormatting.WHITE));

        notifyAllPlayersInArea(invasion, Component.literal(
            String.format("第 %s 波已降临！", getRomanNumeral(invasion.currentWave))
        ).withStyle(ChatFormatting.RED));

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] {} 第{}波开始战斗", invasion.getPlayerName(), invasion.currentWave);
        }
    }

    // ======================== 怪物生成 ========================

    /**
     * 每秒生成一只怪物
     * 从mobsLeft中取下一个未生成的MobInfo，创建实体
     */
    private void spawnOneMob(ActiveInvasion invasion) {
        ActiveInvasion.MobInfo mobInfo = invasion.getNextMobToSpawn();
        if (mobInfo == null) {
            return;
        }

        Level level = invasion.getLevel();
        BlockPos center = invasion.getCenterPos();

        // 计算生成位置
        Vec3 offset = invasion.buildSpawnOffset(INVASION_AREA_RADIUS);
        BlockPos spawnPos = findSpawnPos(level, center, offset);
        if (spawnPos == null) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug("[入侵] 找不到有效生成位置，跳过: {}", mobInfo.type);
            }
            return;
        }

        // 创建实体
        Entity entity;
        try {
            entity = mobInfo.type.create(level);
            if (!(entity instanceof LivingEntity living)) {
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug("[入侵] 实体类型不是LivingEntity: {}", mobInfo.type);
                }
                return;
            }
        } catch (Exception e) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug("[入侵] 创建实体失败 {}: {}", mobInfo.type, e.getMessage());
            }
            return;
        }

        LivingEntity enemy = (LivingEntity) entity;
        mobInfo.position = spawnPos;
        mobInfo.uuid = null; // 先在设置位置后再获取UUID

        // 应用增强
        applyInvasionEnhancements(enemy, invasion.getDifficultyMultiplier());

        if (enemy instanceof Mob mob) {
            equipInvasionEnemy(mob, invasion.currentWave);
            addCustomAI(mob, center);
        }

        applyGlowingEffect(enemy);
        enemy.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        level.addFreshEntity(enemy);
        mobInfo.uuid = enemy.getUUID();

        // 如果是BOSS，设置Boss血条
        if (mobInfo.isBoss) {
            invasion.boss = enemy;
            invasion.showBossBar();
            invasion.setBossInfoText(Component.literal("巨人 (BOSS)"));
        }

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] 生成怪物: {} 于 {} (方向={})", mobInfo.type, spawnPos, invasion.direction);
        }
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
     */
    private static class UndeadArmyForgiveTeammateGoal extends Goal {

        /** 拥有该AI的怪物 */
        private final PathfinderMob mob;

        /** 怒气等级 */
        private float angerLevel = 0.0f;

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
            return isUndead(target);
        }

        @Override
        public void start() {
            // 清除目标，防止亡灵打亡灵
            mob.setTarget(null);
            mob.setLastHurtMob(null);
            this.angerLevel = 0.0f;
        }

        @Override
        public void tick() {
            // 累积怒气增加移动速度
            this.angerLevel += 0.1f;
            if (mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) != null) {
                double baseSpeed = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getBaseValue();
                mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                    .setBaseValue(baseSpeed * (1.0 + angerLevel * 0.05));
            }
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = mob.getTarget();
            return target != null && isUndead(target) && angerLevel < 5.0f;
        }

        /**
         * 判断实体是否为亡灵生物
         * 
         * @param entity 实体
         * @return 是否为亡灵
         */
        private boolean isUndead(LivingEntity entity) {
            return entity.getType() == EntityType.ZOMBIE ||
                   entity.getType() == EntityType.SKELETON ||
                   entity.getType() == EntityType.HUSK ||
                   entity.getType() == EntityType.STRAY ||
                   entity.getType() == EntityType.WITHER_SKELETON ||
                   entity.getType() == EntityType.ZOMBIE_VILLAGER ||
                   entity.getType() == EntityType.ZOGLIN ||
                   entity.getType() == EntityType.GIANT ||
                   entity.getType() == EntityType.IRON_GOLEM;
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

        if (killer != null && isUndeadMob(entity)) {
            incrementUndeadKillCount(killer);
        }
    }

    /**
     * 判断是否为亡灵生物
     * 
     * @param entity 实体
     * @return 是否为亡灵
     */
    private boolean isUndeadMob(LivingEntity entity) {
        return entity.getType() == EntityType.ZOMBIE ||
               entity.getType() == EntityType.SKELETON ||
               entity.getType() == EntityType.HUSK ||
               entity.getType() == EntityType.STRAY ||
               entity.getType() == EntityType.WITHER_SKELETON ||
               entity.getType() == EntityType.ZOMBIE_VILLAGER;
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

        int remaining = requiredKills - currentKills;
        if (remaining <= 5 && remaining > 0 && Config.INVASION.ENABLE_PLAYER_NOTIFICATION.get()) {
            player.sendSystemMessage(Component.literal(
                String.format("亡灵军团即将来袭！还需击杀 %d 只亡灵生物！", remaining)
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
     * 触发入侵事件
     */
    public boolean triggerInvasion(Level level, Player player, InvasionType type) {
        if (level.isClientSide()) {
            return false;
        }

        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }

        if (activeInvasions.containsKey(player.getUUID())) {
            return false;
        }

        int waveCount = calculateWaveCount();
        double difficultyMultiplier = getDifficultyMultiplier();

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

        notifyAllPlayersInArea(invasion, Component.literal(
            "亡灵军团来袭！"
        ).withStyle(ChatFormatting.DARK_RED));

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] 玩家 {} 触发了入侵事件，共{}波", player.getName().getString(), waveCount);
        }

        return true;
    }

    /**
     * 手动触发入侵（通过命令）
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

        int waveCount = customWaves != null ? customWaves : calculateWaveCount();
        double difficultyMultiplier = customDifficulty != null ? customDifficulty : getDifficultyMultiplier();

        ActiveInvasion invasion = new ActiveInvasion(
            player.getUUID(),
            player.level(),
            player.blockPosition(),
            type,
            waveCount,
            difficultyMultiplier
        );

        activeInvasions.put(player.getUUID(), invasion);
        setThunderstormWeather(player.level());

        notifyAllPlayersInArea(invasion, Component.literal(
            "☠️ 亡灵军团来袭！"
        ).withStyle(ChatFormatting.DARK_RED));

        return true;
    }

    // ======================== 辅助方法 ========================

    /**
     * 通知区域内的所有玩家
     */
    private void notifyAllPlayersInArea(ActiveInvasion invasion, Component message) {
        if (!(invasion.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos center = invasion.getCenterPos();

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center.getX(), center.getY(), center.getZ()) <= INVASION_AREA_RADIUS * INVASION_AREA_RADIUS) {
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
     */
    private int calculateWaveCount() {
        int worldStage = WorldStageManager.getInstance().getWorldStage();

        int maxWaves = switch (worldStage) {
            case 0 -> 3;
            case 1 -> 5;
            default -> 6;
        };

        return Math.min(maxWaves, Config.INVASION.MAX_WAVE_COUNT.get());
    }

    /**
     * 获取难度倍率
     * 未启用自定义难度时，根据世界阶段使用动态倍率
     */
    private double getDifficultyMultiplier() {
        if (Config.INVASION.ENABLE_CUSTOM_DIFFICULTY.get()) {
            return Config.INVASION.CUSTOM_DIFFICULTY_MULTIPLIER.get();
        }

        // 动态难度：基于世界阶段
        int worldStage = WorldStageManager.getInstance().getWorldStage();
        return switch (worldStage) {
            case 0 -> 1.0;   // Normal Mode
            case 1 -> 1.5;   // Expert Mode (进入下界)
            case 2 -> 2.0;   // Master Mode (击败末影龙)
            default -> 2.5;  // 更高阶段
        };
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
     */
    private void triggerLightningEffect(ActiveInvasion invasion) {
        if (!(invasion.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos center = invasion.getCenterPos();

        for (int i = 0; i < 3; i++) {
            int dx = random.nextInt(30) - 15;
            int dz = random.nextInt(30) - 15;
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
     */
    private void handleInvasionVictory(ActiveInvasion invasion) {
        if (!(invasion.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos center = invasion.getCenterPos();

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center.getX(), center.getY(), center.getZ()) <= INVASION_AREA_RADIUS * INVASION_AREA_RADIUS) {
                giveTreasureBag(player);
            }
        }

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] 胜利: 玩家={}", invasion.getPlayerName());
        }
    }

    /**
     * 给予宝藏袋
     * 直接给予随机战利品 + 一个潜影盒用于收纳
     */
    private void giveTreasureBag(Player player) {
        player.sendSystemMessage(Component.literal(
            "获得亡灵军团宝藏袋！"
        ).withStyle(ChatFormatting.YELLOW));

        // 给予随机战利品
        int lootCount = 3 + random.nextInt(4); // 3-6组物品
        for (int i = 0; i < lootCount; i++) {
            ItemStack loot = generateRandomLoot();
            if (!loot.isEmpty()) {
                player.getInventory().add(loot);
            }
        }

        // 额外给一个潜影盒用于收纳
        player.getInventory().add(new ItemStack(Items.SHULKER_BOX));
    }

    /**
     * 生成随机战利品
     * 
     * @return 战利品物品栈
     */
    private ItemStack generateRandomLoot() {
        float roll = random.nextFloat();
        if (roll < 0.2f) { // 20% - 稀有好物
            return switch (random.nextInt(4)) {
                case 0 -> new ItemStack(Items.DIAMOND, 1 + random.nextInt(3));
                case 1 -> new ItemStack(Items.EMERALD, 3 + random.nextInt(5));
                case 2 -> new ItemStack(Items.GOLDEN_APPLE, 1);
                case 3 -> new ItemStack(Items.ENCHANTED_BOOK, 1);
                default -> ItemStack.EMPTY;
            };
        } else if (roll < 0.5f) { // 30% - 实用物资
            return switch (random.nextInt(5)) {
                case 0 -> new ItemStack(Items.IRON_INGOT, 3 + random.nextInt(5));
                case 1 -> new ItemStack(Items.GOLD_INGOT, 2 + random.nextInt(4));
                case 2 -> new ItemStack(Items.ARROW, 8 + random.nextInt(16));
                case 3 -> new ItemStack(Items.EXPERIENCE_BOTTLE, 3 + random.nextInt(5));
                case 4 -> new ItemStack(Items.COOKED_BEEF, 5 + random.nextInt(6));
                default -> ItemStack.EMPTY;
            };
        } else { // 50% - 常见掉落
            return switch (random.nextInt(4)) {
                case 0 -> new ItemStack(Items.BONE, 5 + random.nextInt(10));
                case 1 -> new ItemStack(Items.ROTTEN_FLESH, 5 + random.nextInt(10));
                case 2 -> new ItemStack(Items.COAL, 3 + random.nextInt(6));
                case 3 -> new ItemStack(Items.GRAVEL, 8 + random.nextInt(16));
                default -> ItemStack.EMPTY;
            };
        }
    }

    // ======================== 敌人数值 ========================

    /**
     * 获取实体类型的基础血量
     * 
     * @param type 实体类型
     * @return 基础血量
     */
    private int getBaseHealth(EntityType<?> type) {
        if (type == EntityType.ZOMBIE_VILLAGER) return 40;
        if (type == EntityType.WITHER_SKELETON) return 20;
        if (type == EntityType.ZOGLIN) return 40;
        if (type == EntityType.GIANT) return 100;
        if (type == EntityType.IRON_GOLEM) return 100;
        return 20; // 僵尸、骷髅等基础亡灵
    }

    // ======================== 增强与装备 ========================

    /**
     * 应用入侵增强
     */
    private void applyInvasionEnhancements(LivingEntity enemy, double multiplier) {
        var maxHealthAttr = enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(maxHealthAttr.getBaseValue() * multiplier);
            enemy.setHealth(enemy.getMaxHealth());
        }

        var attackDamageAttr = enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if (attackDamageAttr != null) {
            attackDamageAttr.setBaseValue(attackDamageAttr.getBaseValue() * multiplier);
        }

        var movementSpeedAttr = enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (movementSpeedAttr != null) {
            movementSpeedAttr.setBaseValue(movementSpeedAttr.getBaseValue() * (0.8 + multiplier * 0.2));
        }
    }

    /**
     * 给入侵敌人装备护甲和武器
     */
    private void equipInvasionEnemy(Mob enemy, int waveNumber) {
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

        // 给僵尸和尸壳小概率装备铁剑
        if ((type == EntityType.ZOMBIE || type == EntityType.HUSK) && random.nextFloat() < 0.3f) {
            enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        }

        int armorChance = switch (waveNumber) {
            case 1 -> 20;
            case 2 -> 30;
            case 3 -> 50;
            case 4 -> 50;
            case 5 -> 50;
            case 6 -> 50;
            default -> 50;
        };

        if (random.nextInt(100) >= armorChance) {
            return;
        }

        int armorTier = switch (waveNumber) {
            case 1 -> random.nextInt(2);
            case 2 -> random.nextInt(3);
            case 3 -> random.nextInt(4);
            case 4 -> random.nextInt(4);
            case 5 -> random.nextInt(4);
            case 6 -> random.nextInt(4);
            default -> random.nextInt(4);
        };

        equipArmor(enemy, armorTier);
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
     * 装备护甲
     */
    private void equipArmor(Mob enemy, int tier) {
        ItemStack helmet = getArmorItem(tier, "helmet");
        ItemStack chestplate = getArmorItem(tier, "chestplate");
        ItemStack leggings = getArmorItem(tier, "leggings");
        ItemStack boots = getArmorItem(tier, "boots");

        if (random.nextBoolean()) enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);
        if (random.nextBoolean()) enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chestplate);
        if (random.nextBoolean()) enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, leggings);
        if (random.nextBoolean()) enemy.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, boots);
    }

    /**
     * 获取护甲物品
     */
    private ItemStack getArmorItem(int tier, String type) {
        return switch (tier) {
            case 0 -> switch (type) {
                case "helmet" -> new ItemStack(Items.LEATHER_HELMET);
                case "chestplate" -> new ItemStack(Items.LEATHER_CHESTPLATE);
                case "leggings" -> new ItemStack(Items.LEATHER_LEGGINGS);
                case "boots" -> new ItemStack(Items.LEATHER_BOOTS);
                default -> ItemStack.EMPTY;
            };
            case 1 -> switch (type) {
                case "helmet" -> new ItemStack(Items.CHAINMAIL_HELMET);
                case "chestplate" -> new ItemStack(Items.CHAINMAIL_CHESTPLATE);
                case "leggings" -> new ItemStack(Items.CHAINMAIL_LEGGINGS);
                case "boots" -> new ItemStack(Items.CHAINMAIL_BOOTS);
                default -> ItemStack.EMPTY;
            };
            case 2 -> switch (type) {
                case "helmet" -> new ItemStack(Items.IRON_HELMET);
                case "chestplate" -> new ItemStack(Items.IRON_CHESTPLATE);
                case "leggings" -> new ItemStack(Items.IRON_LEGGINGS);
                case "boots" -> new ItemStack(Items.IRON_BOOTS);
                default -> ItemStack.EMPTY;
            };
            case 3 -> switch (type) {
                case "helmet" -> new ItemStack(Items.DIAMOND_HELMET);
                case "chestplate" -> new ItemStack(Items.DIAMOND_CHESTPLATE);
                case "leggings" -> new ItemStack(Items.DIAMOND_LEGGINGS);
                case "boots" -> new ItemStack(Items.DIAMOND_BOOTS);
                default -> ItemStack.EMPTY;
            };
            default -> ItemStack.EMPTY;
        };
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
     * | 波数 | 僵尸 | 骷髅 | 尸壳 | 流浪者 | Tank(僵尸村民) | 凋灵骷髅 | 地狱犬(Zoglin) | 巨人(BOSS) |
     * | --- | --- | --- | --- | --- | --- | --- | --- | --- |
     * | 1 | 4 | 4 | - | - | - | - | - | - |
     * | 2 | 5 | 5 | - | - | - | - | - | - |
     * | 3 | 3 | 3 | 3 | 3 | 1 | - | - | - |
     * | 4 | 2 | 2 | 3 | 3 | 1 | 4 | - | - |
     * | 5 | 1 | 1 | 3 | 3 | 1 | 4 | 1 | - |
     * | 6 | 1 | 1 | 1 | 1 | 1 | 1 | 1 | 1 |
     */
    private List<EnemySpawnConfig> getWaveEnemyConfig(int waveNumber) {
        List<EnemySpawnConfig> configs = new ArrayList<>();

        switch (waveNumber) {
            case 1 -> {
                configs.add(new EnemySpawnConfig(EntityType.ZOMBIE, 4));
                configs.add(new EnemySpawnConfig(EntityType.SKELETON, 4));
            }
            case 2 -> {
                configs.add(new EnemySpawnConfig(EntityType.ZOMBIE, 5));
                configs.add(new EnemySpawnConfig(EntityType.SKELETON, 5));
            }
            case 3 -> {
                configs.add(new EnemySpawnConfig(EntityType.ZOMBIE, 3));
                configs.add(new EnemySpawnConfig(EntityType.SKELETON, 3));
                configs.add(new EnemySpawnConfig(EntityType.HUSK, 3));
                configs.add(new EnemySpawnConfig(EntityType.STRAY, 3));
                configs.add(new EnemySpawnConfig(EntityType.ZOMBIE_VILLAGER, 1));
            }
            case 4 -> {
                configs.add(new EnemySpawnConfig(EntityType.ZOMBIE, 2));
                configs.add(new EnemySpawnConfig(EntityType.SKELETON, 2));
                configs.add(new EnemySpawnConfig(EntityType.HUSK, 3));
                configs.add(new EnemySpawnConfig(EntityType.STRAY, 3));
                configs.add(new EnemySpawnConfig(EntityType.ZOMBIE_VILLAGER, 1));
                configs.add(new EnemySpawnConfig(EntityType.WITHER_SKELETON, 4));
            }
            case 5 -> {
                configs.add(new EnemySpawnConfig(EntityType.ZOMBIE, 1));
                configs.add(new EnemySpawnConfig(EntityType.SKELETON, 1));
                configs.add(new EnemySpawnConfig(EntityType.HUSK, 3));
                configs.add(new EnemySpawnConfig(EntityType.STRAY, 3));
                configs.add(new EnemySpawnConfig(EntityType.ZOMBIE_VILLAGER, 1));
                configs.add(new EnemySpawnConfig(EntityType.WITHER_SKELETON, 4));
                configs.add(new EnemySpawnConfig(EntityType.ZOGLIN, 1));
            }
            case 6 -> {
                configs.add(new EnemySpawnConfig(EntityType.ZOMBIE, 1));
                configs.add(new EnemySpawnConfig(EntityType.SKELETON, 1));
                configs.add(new EnemySpawnConfig(EntityType.HUSK, 1));
                configs.add(new EnemySpawnConfig(EntityType.STRAY, 1));
                configs.add(new EnemySpawnConfig(EntityType.ZOMBIE_VILLAGER, 1));
                configs.add(new EnemySpawnConfig(EntityType.WITHER_SKELETON, 1));
                configs.add(new EnemySpawnConfig(EntityType.ZOGLIN, 1));
                configs.add(new EnemySpawnConfig(EntityType.GIANT, 1));
            }
            default -> {
                configs.add(new EnemySpawnConfig(EntityType.ZOMBIE, 5));
                configs.add(new EnemySpawnConfig(EntityType.SKELETON, 5));
            }
        }

        return configs;
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

    public enum InvasionType {
        UNDEAD
    }

    private record EnemySpawnConfig(EntityType<?> entityType, int count) {}
}
