package com.adaptive_nemesis.adaptive_nemesismod.nemesis;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.boss.BossIdentificationService;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisDataLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 宿敌日常生成系统
 *
 * 捕获原版自然生成的敌人，将其转化为宿敌
 * 宿敌拥有自定义名称、强化属性，并受全局难度影响
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class NemesisSystem {

    /**
     * 宿敌NBT标记键
     * 标记被宿敌系统转化的生物，供铁魔法等兼容层判断是否应用法术类加成。
     * 普通怪物（非宿敌）不再获得法术抗性/法术强度加成，避免法师打群怪被过度限制。
     */
    public static final String NEMESIS_TAG = "adaptive_nemesis_nemesis";

    /**
     * 宿敌生成时间 NBT 标记键（F-22）
     * 写入实体 persistentData 随实体持久化，服务器重启后计时不重置；
     * nemesisSpawnTimes 内存 Map 仅是运行缓存
     */
    public static final String NEMESIS_SPAWN_TIME_TAG = "adaptive_nemesis_spawn_time";

    /**
     * 检查实体是否为宿敌（带 NBT 标记）
     *
     * @param entity 待检查实体
     * @return true 表示该实体是宿敌
     */
    public static boolean isNemesis(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(NEMESIS_TAG);
    }

    /**
     * Boss类型实体列表（不会被转化为宿敌）
     */
    private static final EntityType<?>[] BOSS_TYPES = {
        EntityType.WITHER,
        EntityType.ENDER_DRAGON,
        EntityType.ELDER_GUARDIAN
    };

    private final NemesisNameGenerator nameGenerator;
    private final Random random;

    /**
     * 宿敌生成时间运行缓存（F-22）
     * 权威数据在实体 persistentData（NEMESIS_SPAWN_TIME_TAG，随实体持久化），
     * 此缓存仅用于加速运行期读取；宿敌死亡/离开世界时清理，保证内存有界
     * Key: 实体 UUID (字符串形式), Value: 生成时间戳 (毫秒)
     */
    private final Map<String, Long> nemesisSpawnTimes;

    /**
     * 构造函数
     * 注册事件监听器
     */
    public NemesisSystem() {
        this.nameGenerator = new NemesisNameGenerator();
        this.random = new Random();
        this.nemesisSpawnTimes = new HashMap<>();
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * 服务器启动事件处理（F-40）
     *
     * 语言环境可能随资源重载或客户端语言切换而变化，
     * 每次服务器启动失效称号翻译计数缓存，首次生成名称时重新探测一次。
     *
     * @param event 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        nameGenerator.invalidateTranslationCache();
    }

    /**
     * 实体加入世界事件处理
     * 捕获原版生成的敌人并尝试转化为宿敌
     *
     * 防重复转化守卫（F-04）：
     * - loadedFromDisk：区块卸载重载、跨维度往返时实体从磁盘加载，跳过转化
     * - isNemesis：已带宿敌标记的实体直接跳过，避免属性二次叠加
     *
     * @param event 实体加入事件
     */
    @SubscribeEvent
    public void onEntitySpawn(EntityJoinLevelEvent event) {
        if (!Config.ENABLE_NEMESIS_SPAWN.get()) {
            return;
        }

        // 客户端判断前置：服务端以外的分支无需消耗随机数与后续检查
        if (event.getLevel().isClientSide()) {
            return;
        }

        // 磁盘加载守卫：仅处理新生成的实体
        if (event.loadedFromDisk()) {
            return;
        }

        // 敌对生物判定与 EnemyScalingHandler.onEntityJoinLevel 口径对齐：
        // Mob + Enemy（接口），而非 Monster（类）。
        // 铁魔法等模组的施法怪（如 AbstractSpellCastingMob 系）继承 PathfinderMob 而非 Monster，
        // 旧判定会导致这类怪永远无法转化为宿敌，进而永远拿不到法术类加成。
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        // 已是宿敌的实体不再重复转化（配合 loadedFromDisk 双保险）
        if (isNemesis(mob)) {
            return;
        }

        if (!(mob instanceof Enemy)) {
            return;
        }

        if (isBoss(mob)) {
            return;
        }

        if (!shouldConvertToNemesis()) {
            return;
        }

        if (!hasRequiredAttributes(mob)) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "宿敌生成跳过: {} 缺少攻击力属性",
                    mob.getType().getDescriptionId()
                );
            }
            return;
        }

        convertToNemesis(mob);
    }

    /**
     * 实体离开世界事件处理（F-22）
     *
     * 宿敌死亡、被移除或区块卸载时清理生成时间运行缓存；
     * 权威时间戳保存在实体 persistentData 中，缓存清理不影响已持久化的计时。
     *
     * @param event 实体离开事件
     */
    @SubscribeEvent
    public void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity entity) || !isNemesis(entity)) {
            return;
        }

        nemesisSpawnTimes.remove(entity.getUUID().toString());
    }

    /**
     * 判断是否应该将敌人转化为宿敌
     * 根据配置的生成概率决定
     *
     * @return 是否转化
     */
    private boolean shouldConvertToNemesis() {
        double chance = Config.NEMESIS_SPAWN_CHANCE.get();
        return random.nextDouble() < chance;
    }

    /**
     * 检查敌人是否具有宿敌转化所需的属性
     * 当配置要求攻击力属性时，缺少 generic.attack_damage 的生物会被剔除
     *
     * @param monster 敌人实体
     * @return 是否具有所需属性
     */
    private boolean hasRequiredAttributes(Mob monster) {
        if (!Config.NEMESIS_REQUIRE_ATTACK_DAMAGE.get()) {
            return true;
        }
        return monster.getAttribute(Attributes.ATTACK_DAMAGE) != null;
    }

    /**
     * 判断敌人是否为Boss
     * Boss不会被转化为宿敌
     *
     * 判定链：原硬编码 Boss 名单（凋灵/末影龙/远古守卫者）优先，
     * 追加全局 Boss 识别服务（标签/类型/名称/血量阈值策略链），
     * 覆盖死灵之王、火焰领主等模组 Boss，防止其被转化为宿敌。
     *
     * @param entity 实体
     * @return 是否为Boss
     */
    private boolean isBoss(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        for (EntityType<?> bossType : BOSS_TYPES) {
            if (type == bossType) {
                return true;
            }
        }

        // 全局 Boss 识别服务可能未初始化（配置未加载时 getInstance 返回 null），
        // 此时退回硬编码名单判定，保证 null 安全
        BossIdentificationService bossService = BossIdentificationService.getInstance();
        return bossService != null && bossService.isBoss(entity);
    }

    /**
     * 将敌人转化为宿敌
     * 包括：
     * 1. 计算强化倍率（受全局难度影响）
     * 2. 应用属性强化
     * 3. 设置自定义名称
     * 4. 打宿敌标记并记录生成时间（F-22：随实体持久化）
     * 5. 通知附近玩家（F-48：单次扫描）
     *
     * @param monster 要转化的敌人（Mob 级，兼容非 Monster 类的模组施法怪）
     */
    private void convertToNemesis(Mob monster) {
        double multiplier = calculateNemesisMultiplier();

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "宿敌生成: {} 强化倍率: {}",
                monster.getType().getDescriptionId(),
                String.format("%.2f", multiplier)
            );
        }

        applyStatsMultiplier(monster, multiplier);

        Component nemesisName = nameGenerator.generateNemesisName(monster, multiplier);
        monster.setCustomName(nemesisName);
        monster.setCustomNameVisible(Config.NEMESIS_NAME_ALWAYS_VISIBLE.get());

        markNemesis(monster);

        notifyNearbyPlayers(monster, nemesisName);
    }

    /**
     * 应用宿敌公共标记（自然生成与手动转化的共用尾部流程）
     *
     * 1. 打宿敌 NBT 标记（供铁魔法等兼容层识别，宿敌应用法术抗性加成，施法怪另获法术强度加成）
     * 2. 发光效果受配置开关控制
     * 3. 记录生成时间（F-22）：写入实体 persistentData 随实体持久化，
     *    内存 Map 仅作运行缓存；自动消失功能关闭时不写入
     *
     * @param monster 要标记的敌人
     */
    private void markNemesis(Mob monster) {
        monster.getPersistentData().putBoolean(NEMESIS_TAG, true);

        // 发光效果受配置开关控制
        if (Config.ENABLE_NEMESIS_GLOW.get()) {
            monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE));
        }

        // 仅在自动消失功能开启时记录生成时间（关闭时不写入）
        if (Config.NEMESIS.ENABLE_NEMESIS_AUTO_DISAPPEAR.get()) {
            long now = System.currentTimeMillis();
            monster.getPersistentData().putLong(NEMESIS_SPAWN_TIME_TAG, now);
            nemesisSpawnTimes.put(monster.getUUID().toString(), now);
        }
    }

    /**
     * 通知附近玩家宿敌出现（F-48：单次扫描复用；F-44：半径走数据包配置）
     *
     * @param monster 宿敌实体
     * @param nemesisName 宿敌名称组件
     */
    private void notifyNearbyPlayers(Mob monster, Component nemesisName) {
        double radius = NemesisDataLoader.getInstance().getConfig().getNotificationRadius();
        Player player = monster.level().getNearestPlayer(monster, radius);
        if (player == null) {
            return;
        }
        player.sendSystemMessage(Component.translatable("adaptive_nemesis.nemesis.appearance_warning").withStyle(ChatFormatting.RED));
        player.sendSystemMessage(nemesisName.copy().withStyle(ChatFormatting.YELLOW));
    }

    /**
     * 计算宿敌强化倍率
     * 基础倍率 + 全局难度加成
     *
     * @return 最终强化倍率
     */
    private double calculateNemesisMultiplier() {
        double baseMultiplier = Config.NEMESIS_BASE_MULTIPLIER.get();
        double globalDifficulty = Config.DIFFICULTY_BASE_MULTIPLIER.get();

        double finalMultiplier = baseMultiplier * globalDifficulty;

        double minMultiplier = Config.NEMESIS_MIN_MULTIPLIER.get();
        double maxMultiplier = Config.NEMESIS_MAX_MULTIPLIER.get();

        finalMultiplier = Math.max(minMultiplier, Math.min(maxMultiplier, finalMultiplier));

        return finalMultiplier;
    }

    /**
     * 应用属性强化
     * 包括生命值、攻击力、护甲等
     * 对缺失的属性进行空指针保护，避免非攻击型生物或模组生物导致崩溃
     *
     * @param monster 敌人实体
     * @param multiplier 强化倍率
     */
    private void applyStatsMultiplier(Mob monster, double multiplier) {
        double effectiveMultiplier = Math.max(1.0, multiplier);

        var maxHealthAttr = monster.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double originalHealth = maxHealthAttr.getBaseValue();
            maxHealthAttr.setBaseValue(Math.max(originalHealth, originalHealth * effectiveMultiplier));
            monster.setHealth(monster.getMaxHealth());
        }

        var attackDamageAttr = monster.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamageAttr != null) {
            double originalDamage = attackDamageAttr.getBaseValue();
            attackDamageAttr.setBaseValue(Math.max(originalDamage, originalDamage * effectiveMultiplier));
        }

        var armorAttr = monster.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            double originalArmor = armorAttr.getBaseValue();
            armorAttr.setBaseValue(Math.max(originalArmor, originalArmor * (0.5 + effectiveMultiplier * 0.5)));
        }

        var movementSpeedAttr = monster.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeedAttr != null) {
            double originalSpeed = movementSpeedAttr.getBaseValue();
            movementSpeedAttr.setBaseValue(Math.max(originalSpeed, originalSpeed * (0.8 + effectiveMultiplier * 0.2)));
        }

        var knockbackResistanceAttr = monster.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackResistanceAttr != null) {
            double originalKnockback = knockbackResistanceAttr.getBaseValue();
            knockbackResistanceAttr.setBaseValue(Math.min(0.9, originalKnockback + (effectiveMultiplier - 1.0) * 0.3));
        }
    }

    /**
     * 获取宿敌名称生成器
     *
     * @return 名称生成器
     */
    public NemesisNameGenerator getNameGenerator() {
        return nameGenerator;
    }

    /**
     * 手动将敌人转化为宿敌（用于命令或脚本）
     *
     * @param monster 敌人实体
     * @param customMultiplier 自定义倍率（null则使用默认计算）
     * @return 是否转化成功
     */
    public boolean convertToNemesisManual(Monster monster, Double customMultiplier) {
        if (isBoss(monster)) {
            return false;
        }

        if (monster.level().isClientSide()) {
            return false;
        }

        double multiplier = customMultiplier != null ? customMultiplier : calculateNemesisMultiplier();
        applyStatsMultiplier(monster, multiplier);

        Component nemesisName = nameGenerator.generateNemesisName(monster, multiplier);
        monster.setCustomName(nemesisName);
        monster.setCustomNameVisible(Config.NEMESIS_NAME_ALWAYS_VISIBLE.get());

        markNemesis(monster);

        return true;
    }

    /**
     * 实体tick事件处理
     * 定期检查宿敌是否存在时间过长，如果是则将其从世界中移除（F-41：骨架拆分）
     *
     * @param event 实体 tick 事件
     */
    @SubscribeEvent
    public void onEntityTick(EntityTickEvent.Post event) {
        if (!Config.NEMESIS.ENABLE_NEMESIS_AUTO_DISAPPEAR.get() || event.getEntity().level().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof Monster monster) || !isNemesis(monster)) {
            return;
        }

        long elapsedSeconds = resolveAliveSeconds(monster);
        if (elapsedSeconds < Config.NEMESIS.NEMESIS_AUTO_DISAPPEAR_SECONDS.get()) {
            return;
        }

        discardNemesis(monster, elapsedSeconds);
    }

    /**
     * 解析宿敌已存活秒数（F-41 自 onEntityTick 拆分；F-22 计时随实体持久化）
     *
     * 读取优先级：内存运行缓存 → 实体 persistentData（服务器重启后由此恢复，
     * 计时不重置）→ 当前时间作为起点（无任何记录的兜底路径，并回写两处记录）。
     *
     * @param monster 宿敌实体
     * @return 已存活秒数
     */
    private long resolveAliveSeconds(Monster monster) {
        String entityUuid = monster.getUUID().toString();

        Long cachedSpawnTime = nemesisSpawnTimes.get(entityUuid);
        if (cachedSpawnTime != null) {
            return (System.currentTimeMillis() - cachedSpawnTime) / 1000;
        }

        long persistedTime = monster.getPersistentData().getLong(NEMESIS_SPAWN_TIME_TAG);
        long spawnTime = persistedTime > 0 ? persistedTime : System.currentTimeMillis();

        // 同步内存缓存与持久化标记，保证下次加载仍可恢复
        nemesisSpawnTimes.put(entityUuid, spawnTime);
        monster.getPersistentData().putLong(NEMESIS_SPAWN_TIME_TAG, spawnTime);

        return (System.currentTimeMillis() - spawnTime) / 1000;
    }

    /**
     * 宿敌超时消失处理（F-41 自 onEntityTick 拆分）
     * 广播消失消息（半径走数据包配置）、清理运行缓存并移除实体
     *
     * @param monster 宿敌实体
     * @param elapsedSeconds 已存活秒数（仅用于日志）
     */
    private void discardNemesis(Monster monster, long elapsedSeconds) {
        if (Config.NEMESIS.NEMESIS_DISAPPEAR_MESSAGE.get()) {
            double radius = NemesisDataLoader.getInstance().getConfig().getNotificationRadius();
            Player nearestPlayer = monster.level().getNearestPlayer(monster, radius);
            if (nearestPlayer != null) {
                Component disappearMessage = Component.translatable("adaptive_nemesis.nemesis.disappear")
                    .append(" ")
                    .append(monster.getDisplayName())
                    .withStyle(ChatFormatting.GRAY);
                nearestPlayer.sendSystemMessage(disappearMessage);
            }
        }

        // 从内存运行缓存移除（实体移除时 EntityLeaveLevelEvent 兜底清理）
        nemesisSpawnTimes.remove(monster.getUUID().toString());

        monster.discard();

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "宿敌自动消失：{} 存活时间：{} 秒",
                monster.getType().getDescriptionId(),
                elapsedSeconds
            );
        }
    }

    /**
     * 宿敌死亡事件处理 - 发放额外自定义掉落（F-41：骨架拆分）
     *
     * 触发条件（全部满足）：
     * 1. 配置启用额外掉落（nemesisLootEnabled）
     * 2. 死亡实体带宿敌 NBT 标记
     * 3. 服务端环境
     *
     * 掉落逻辑：
     * 1. 收集 toml 配置的战利品表与数据包 nemesis_loot 配置的战利品表（合并去重）
     * 2. 逐表 roll 掉落，产物掉落在宿敌死亡位置（原版掉落基础上额外掉落）
     * 3. 单表解析/roll 异常不影响其他表
     *
     * @param event 实体死亡事件
     */
    @SubscribeEvent
    public void onNemesisDeath(LivingDeathEvent event) {
        // 服务端环境
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 功能开关（与自动消失功能一致，经 Config.NEMESIS 访问）
        if (!Config.NEMESIS.NEMESIS_LOOT_ENABLED.get()) {
            return;
        }

        // 仅处理宿敌
        LivingEntity deadEntity = event.getEntity();
        if (!isNemesis(deadEntity)) {
            return;
        }

        // 收集全部战利品表（toml 配置 + 数据包配置合并）
        List<ResourceLocation> lootTables = collectLootTables();
        if (lootTables.isEmpty()) {
            return;
        }

        int totalItems = rollLootTables(serverLevel, deadEntity, event.getSource(), lootTables);

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "[宿敌] {} 死亡，额外掉落: 战利品表={} 张, 物品={} 件",
                deadEntity.getName().getString(), lootTables.size(), totalItems
            );
        }
    }

    /**
     * 逐表 roll 宿敌额外掉落（F-41 自 onNemesisDeath 拆分）
     *
     * @param serverLevel 服务端世界
     * @param deadEntity 死亡的宿敌
     * @param source 致死伤害来源
     * @param lootTables 战利品表列表
     * @return 掉落物品总数
     */
    private int rollLootTables(ServerLevel serverLevel, LivingEntity deadEntity,
                               DamageSource source, List<ResourceLocation> lootTables) {
        int totalItems = 0;
        for (ResourceLocation tableId : lootTables) {
            totalItems += rollSingleLootTable(serverLevel, deadEntity, source, tableId);
        }
        return totalItems;
    }

    /**
     * roll 单张战利品表并掉落产物（F-41 自 onNemesisDeath 拆分）
     * 单表解析/roll 失败返回 0，不影响其他表
     *
     * @param serverLevel 服务端世界
     * @param deadEntity 死亡的宿敌
     * @param source 致死伤害来源
     * @param tableId 战利品表 ID
     * @return 该表掉落的物品数
     */
    private int rollSingleLootTable(ServerLevel serverLevel, LivingEntity deadEntity,
                                    DamageSource source, ResourceLocation tableId) {
        try {
            ResourceKey<LootTable> lootKey = ResourceKey.create(Registries.LOOT_TABLE, tableId);
            LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(lootKey);
            if (lootTable == LootTable.EMPTY) {
                AdaptiveNemesisMod.LOGGER.warn("[宿敌] 找不到额外掉落战利品表: {}", tableId);
                return 0;
            }

            // 使用 ENTITY 参数集构建掉落上下文（实体掉落语义）
            LootParams lootParams = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.THIS_ENTITY, deadEntity)
                .withParameter(LootContextParams.ORIGIN, deadEntity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withLuck(0.0F)
                .create(LootContextParamSets.ENTITY);

            int droppedCount = 0;
            for (ItemStack stack : lootTable.getRandomItems(lootParams)) {
                if (!stack.isEmpty()) {
                    deadEntity.spawnAtLocation(stack);
                    droppedCount++;
                }
            }
            return droppedCount;
        } catch (Exception e) {
            // 单张战利品表解析/roll 失败不影响其他表
            AdaptiveNemesisMod.LOGGER.error(
                "[宿敌] 处理额外掉落战利品表 {} 时发生异常: {} - {}",
                tableId, e.getClass().getSimpleName(), e.getMessage()
            );
            return 0;
        }
    }

    /**
     * 收集宿敌额外掉落的全部战利品表
     *
     * 合并两个来源：
     * 1. toml 配置 nemesisLootTables（逗号分隔 ID 列表）
     * 2. 数据包 data/<namespace>/nemesis_loot/<name>.json 定义的表
     *
     * @return 去重后的战利品表列表（可能为空）
     */
    private List<ResourceLocation> collectLootTables() {
        List<ResourceLocation> tables = new ArrayList<>();

        // 来源 1: toml 配置（逗号分隔，经 Config.NEMESIS 访问与自动消失功能一致）
        String configValue = Config.NEMESIS.NEMESIS_LOOT_TABLES.get();
        if (configValue != null && !configValue.isBlank()) {
            for (String id : configValue.split(",")) {
                String trimmed = id.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                ResourceLocation tableId = ResourceLocation.tryParse(trimmed);
                if (tableId != null && !tables.contains(tableId)) {
                    tables.add(tableId);
                } else if (tableId == null) {
                    AdaptiveNemesisMod.LOGGER.warn("[宿敌] 配置中的战利品表 ID 无效: {}", trimmed);
                }
            }
        }

        // 来源 2: 数据包 nemesis_loot 配置
        for (ResourceLocation tableId : NemesisLootDataLoader.getInstance().getLootTables()) {
            if (!tables.contains(tableId)) {
                tables.add(tableId);
            }
        }

        return tables;
    }
}
