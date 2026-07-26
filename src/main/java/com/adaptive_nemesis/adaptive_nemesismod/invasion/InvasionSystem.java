package com.adaptive_nemesis.adaptive_nemesismod.invasion;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
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
                    invasion.setWaveInfoText(Component.translatable("adaptive_nemesis.invasion.approaching").withStyle(ChatFormatting.WHITE));
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
                        invasion.setWaveInfoText(Component.translatable("adaptive_nemesis.invasion.won").withStyle(ChatFormatting.DARK_RED));
                        if (Config.ENABLE_DEBUG_LOG.get()) {
                            AdaptiveNemesisMod.LOGGER.debug("[入侵状态] {} 波次超时", invasion.getPlayerName());
                        }
                        break;
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
                case UNDEAD_DEFEATED -> {
                    // 非最后一波：继续下一波，startWavePreparation内部已调用startNextWave
                    if (!invasion.isLastWave()) {
                        startWavePreparation(invasion);
                    } else {
                        // 最后一波：胜利
                        invasion.setState(ActiveInvasion.Phase.State.FINISHED, 0);
                        invasion.setCompleted(true);
                        invasion.setWaveInfoText(Component.translatable("adaptive_nemesis.invasion.defeated").withStyle(ChatFormatting.GREEN));
                        handleInvasionVictory(invasion);
                        hasCompletedFirstInvasion.put(invasion.getPlayerUUID(), true);
                        triggerInvasionEndEvent(invasion, true);
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

                    notifyAllPlayersInArea(invasion, Component.translatable(
                        "adaptive_nemesis.invasion.won"
                    ).withStyle(ChatFormatting.DARK_RED));

                    triggerInvasionEndEvent(invasion, false);
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

        invasion.setWaveInfoText(Component.translatable(
            "adaptive_nemesis.invasion.wave_preparing", getRomanNumeral(invasion.currentWave)
        ).withStyle(ChatFormatting.WHITE));

        // 生成MobInfo列表
        List<EnemySpawnConfig> configs = getWaveEnemyConfig(invasion.currentWave, invasion.getType());
        invasion.mobsLeft.clear();
        invasion.boss = null;
        invasion.hideBossBar();

        int totalHealth = 0;
        for (EnemySpawnConfig config : configs) {
            for (int i = 0; i < config.count(); i++) {
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
                invasion.mobsLeft.add(mobInfo);
                totalHealth += getBaseHealth(config.entityType());
            }
        }

        invasion.phase.healthTotal = totalHealth;

        // 每波随机选择方向
        ActiveInvasion.Direction[] dirs = ActiveInvasion.Direction.values();
        invasion.direction = dirs[random.nextInt(dirs.length)];

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
        applyInvasionEnhancements(enemy, invasion.getDifficultyMultiplier(),
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

        applyGlowingEffect(enemy);
        enemy.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        level.addFreshEntity(enemy);
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
        Registry<net.minecraft.world.item.enchantment.Enchantment> enchantmentRegistry =
            serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder.Reference<net.minecraft.world.item.enchantment.Enchantment> holder =
            enchantmentRegistry.getHolder(ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath("minecraft", "frost_walker"))).orElse(null);
        if (holder == null) {
            return;
        }
        ItemStack boots = new ItemStack(Items.IRON_BOOTS);
        boots.enchant(holder, 1);
        mob.setItemSlot(EquipmentSlot.FEET, boots);
        mob.setDropChance(EquipmentSlot.FEET, 0.0f);
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

        int waveCount = calculateWaveCount(type);
        double difficultyMultiplier = getDifficultyMultiplier();

        // 触发 KubeJS 入侵开始事件
        if (player instanceof ServerPlayer serverPlayer) {
            KubeJSEventTrigger.InvasionStartResult result = KubeJSEventTrigger.triggerInvasionStart(
                serverPlayer, type, waveCount, difficultyMultiplier
            );
            if (result == null) {
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug("[入侵] KubeJS 取消了玩家 {} 的入侵事件", player.getName().getString());
                }
                return false;
            }
            waveCount = result.totalWaves;
            difficultyMultiplier = result.difficultyMultiplier;
        }

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
            "adaptive_nemesis.invasion.triggered"
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

        int waveCount = customWaves != null ? customWaves : calculateWaveCount(type);
        double difficultyMultiplier = customDifficulty != null ? customDifficulty : getDifficultyMultiplier();

        // 触发 KubeJS 入侵开始事件
        if (player instanceof ServerPlayer serverPlayer) {
            KubeJSEventTrigger.InvasionStartResult result = KubeJSEventTrigger.triggerInvasionStart(
                serverPlayer, type, waveCount, difficultyMultiplier
            );
            if (result == null) {
                return false;
            }
            waveCount = result.totalWaves;
            difficultyMultiplier = result.difficultyMultiplier;
        }

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

        notifyAllPlayersInArea(invasion, Component.translatable(
            "adaptive_nemesis.invasion.triggered_manual"
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
     * 如果启用了数据包支持且存在对应数据包配置，优先使用数据包中的 max_waves
     */
    private int calculateWaveCount(InvasionType type) {
        int worldStage = WorldStageManager.getInstance().getWorldStage();

        int maxWaves = switch (worldStage) {
            case 0 -> 3;
            case 1 -> 5;
            default -> 6;
        };

        if (Config.INVASION.ENABLE_DATA_PACK_SUPPORT.get()) {
            InvasionData data = getDataPackInvasion(type);
            if (data != null) {
                maxWaves = data.getMaxWaves();
            }
        }

        return Math.min(maxWaves, Config.INVASION.MAX_WAVE_COUNT.get());
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
        notifyAllPlayersInArea(invasion, Component.translatable(
            "adaptive_nemesis.invasion.defeated"
        ).withStyle(ChatFormatting.GREEN));

        Player player = invasion.getPlayer();
        if (player instanceof ServerPlayer serverPlayer) {
            InvasionRewardData rewards = getInvasionRewards(invasion.getType());
            int wavesCompleted = invasion.getTotalWaves();
            rewards = KubeJSEventTrigger.triggerInvasionEnd(
                serverPlayer, invasion.getType(), true, invasion.getTotalWaves(),
                invasion.getDifficultyMultiplier(), wavesCompleted, rewards
            );
            grantRewards(serverPlayer, rewards);
        }

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug("[入侵] 胜利: 玩家={}", invasion.getPlayerName());
        }
    }

    /**
     * 触发入侵结束 KubeJS 事件（失败时仅触发事件，不发奖励）
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
        KubeJSEventTrigger.triggerInvasionEnd(
            serverPlayer,
            invasion.getType(),
            victory,
            invasion.getTotalWaves(),
            invasion.getDifficultyMultiplier(),
            wavesCompleted,
            rewards
        );
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
     *
     * @param enemy 目标实体
     * @param multiplier 基础难度倍率
     */
    private void applyInvasionEnhancements(LivingEntity enemy, double multiplier) {
        applyInvasionEnhancements(enemy, multiplier, 1.0, 1.0);
    }

    /**
     * 应用入侵增强（支持数据包自定义倍率）
     *
     * @param enemy 目标实体
     * @param multiplier 基础难度倍率
     * @param healthMultiplier 血量额外倍率
     * @param damageMultiplier 攻击额外倍率
     */
    private void applyInvasionEnhancements(LivingEntity enemy, double multiplier,
                                            double healthMultiplier, double damageMultiplier) {
        double effectiveHealth = multiplier * healthMultiplier;
        double effectiveDamage = multiplier * damageMultiplier;

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
            movementSpeedAttr.setBaseValue(movementSpeedAttr.getBaseValue() * (0.8 + multiplier * 0.2));
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

        // 给僵尸和尸壳小概率装备铁剑
        if ((type == EntityType.ZOMBIE || type == EntityType.HUSK) && random.nextFloat() < 0.3f) {
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
     * 优先从数据包读取，未启用或不存在时回退到硬编码配置：
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
        if (Config.INVASION.ENABLE_DATA_PACK_SUPPORT.get()) {
            InvasionData data = getDataPackInvasion(type);
            if (data != null) {
                InvasionData.WaveData waveData = data.getWave(waveNumber);
                if (waveData != null) {
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
                            enemy.getDamageMultiplier()
                        ));
                    }
                    if (Config.ENABLE_DEBUG_LOG.get()) {
                        AdaptiveNemesisMod.LOGGER.debug(
                            "[入侵] 从数据包读取第 {} 波配置，敌人类型数={}",
                            waveNumber, configs.size()
                        );
                    }
                    return configs;
                }
            }
        }

        // 硬编码回退配置
        switch (waveNumber) {
            case 1 -> {
                configs.add(fallbackConfig(EntityType.ZOMBIE, 4));
                configs.add(fallbackConfig(EntityType.SKELETON, 4));
            }
            case 2 -> {
                configs.add(fallbackConfig(EntityType.ZOMBIE, 5));
                configs.add(fallbackConfig(EntityType.SKELETON, 5));
            }
            case 3 -> {
                configs.add(fallbackConfig(EntityType.ZOMBIE, 3));
                configs.add(fallbackConfig(EntityType.SKELETON, 3));
                configs.add(fallbackConfig(EntityType.HUSK, 3));
                configs.add(fallbackConfig(EntityType.STRAY, 3));
                configs.add(fallbackConfig(EntityType.ZOMBIE_VILLAGER, 1));
            }
            case 4 -> {
                configs.add(fallbackConfig(EntityType.ZOMBIE, 2));
                configs.add(fallbackConfig(EntityType.SKELETON, 2));
                configs.add(fallbackConfig(EntityType.HUSK, 3));
                configs.add(fallbackConfig(EntityType.STRAY, 3));
                configs.add(fallbackConfig(EntityType.ZOMBIE_VILLAGER, 1));
                configs.add(fallbackConfig(EntityType.WITHER_SKELETON, 4));
            }
            case 5 -> {
                configs.add(fallbackConfig(EntityType.ZOMBIE, 1));
                configs.add(fallbackConfig(EntityType.SKELETON, 1));
                configs.add(fallbackConfig(EntityType.HUSK, 3));
                configs.add(fallbackConfig(EntityType.STRAY, 3));
                configs.add(fallbackConfig(EntityType.ZOMBIE_VILLAGER, 1));
                configs.add(fallbackConfig(EntityType.WITHER_SKELETON, 4));
                configs.add(fallbackConfig(EntityType.ZOGLIN, 1));
            }
            case 6 -> {
                configs.add(fallbackConfig(EntityType.ZOMBIE, 1));
                configs.add(fallbackConfig(EntityType.SKELETON, 1));
                configs.add(fallbackConfig(EntityType.HUSK, 1));
                configs.add(fallbackConfig(EntityType.STRAY, 1));
                configs.add(fallbackConfig(EntityType.ZOMBIE_VILLAGER, 1));
                configs.add(fallbackConfig(EntityType.WITHER_SKELETON, 1));
                configs.add(fallbackConfig(EntityType.ZOGLIN, 1));
                configs.add(fallbackConfig(EntityType.GIANT, 1, true));
            }
            default -> {
                configs.add(fallbackConfig(EntityType.ZOMBIE, 5));
                configs.add(fallbackConfig(EntityType.SKELETON, 5));
            }
        }

        return configs;
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
            null, null, 1.0, 1.0
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
        double damageMultiplier
    ) {}
}
