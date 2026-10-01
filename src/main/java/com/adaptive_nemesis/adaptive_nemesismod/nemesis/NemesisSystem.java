package com.adaptive_nemesis.adaptive_nemesismod.nemesis;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 宿敌日常生成系统
 * 
 * 捕获原版自然生成的敌人，将其转化为宿敌
 * 宿敌拥有自定义名称、强化属性，并受全局难度影响
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class NemesisSystem {

    /**
     * Boss类型实体列表（不会被转化为宿敌）
     */
    private static final EntityType<?>[] BOSS_TYPES = {
        EntityType.WITHER,
        EntityType.ENDER_DRAGON,
        EntityType.ELDER_GUARDIAN
    };

    /**
     * 宿敌NBT标记键 - 转化成功后写入实体持久化数据
     * 用于宿敌死亡时可靠识别（自定义名称可被改名/清除，NBT标记更稳定）
     */
    public static final String NEMESIS_TAG = "adaptive_nemesis_nemesis";

    private final NemesisNameGenerator nameGenerator;
    private final Random random;

    /**
     * 构造函数
     * 注册事件监听器
     */
    public NemesisSystem() {
        this.nameGenerator = new NemesisNameGenerator();
        this.random = new Random();
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * 实体加入世界事件处理
     * 捕获原版生成的敌人并尝试转化为宿敌
     * 
     * @param event 实体加入事件
     */
    @SubscribeEvent
    public void onEntitySpawn(EntityJoinLevelEvent event) {
        if (!Config.ENABLE_NEMESIS_SPAWN.get()) {
            return;
        }

        if (!(event.getEntity() instanceof Monster)) {
            return;
        }

        Monster monster = (Monster) event.getEntity();

        if (isBoss(monster)) {
            return;
        }

        if (!shouldConvertToNemesis()) {
            return;
        }

        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!hasRequiredAttributes(monster)) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "宿敌生成跳过: {} 缺少攻击力属性",
                    monster.getType().getDescriptionId()
                );
            }
            return;
        }

        convertToNemesis(monster);
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
        return false;
    }

    /**
     * 将敌人转化为宿敌
     * 包括：
     * 1. 计算强化倍率（受全局难度影响）
     * 2. 应用属性强化
     * 3. 设置自定义名称
     * 4. 添加发光效果
     * 
     * @param monster 要转化的敌人
     */
    private void convertToNemesis(Monster monster) {
        double multiplier = calculateNemesisMultiplier();

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "宿敌生成: {} 强化倍率: {}",
                monster.getType().getDescriptionId(),
                String.format("%.2f", multiplier)
            );
        }

        applyStatsMultiplier(monster, multiplier);

        // 打上宿敌NBT标记，供死亡掉落等后续逻辑可靠识别
        markAsNemesis(monster);

        Component nemesisName = nameGenerator.generateNemesisName(monster, multiplier);
        monster.setCustomName(nemesisName);
        monster.setCustomNameVisible(Config.NEMESIS_NAME_ALWAYS_VISIBLE.get());

        monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE));

        if (monster.level().getNearestPlayer(monster, 32.0) != null) {
            Player player = monster.level().getNearestPlayer(monster, 32.0);
            player.sendSystemMessage(Component.translatable("adaptive_nemesis.nemesis.appearance_warning").withStyle(ChatFormatting.RED));
            player.sendSystemMessage(nemesisName.copy().withStyle(ChatFormatting.YELLOW));
        }
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

        // 打上宿敌NBT标记，确保命令召唤的宿敌同样受额外掉落等功能控制
        markAsNemesis(monster);

        Component nemesisName = nameGenerator.generateNemesisName(monster, multiplier);
        monster.setCustomName(nemesisName);
        monster.setCustomNameVisible(Config.NEMESIS_NAME_ALWAYS_VISIBLE.get());

        monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE));

        return true;
    }

    /**
     * 对实体打上宿敌NBT标记
     *
     * @param monster 目标敌人
     */
    private void markAsNemesis(Mob monster) {
        monster.getPersistentData().putBoolean(NEMESIS_TAG, true);
    }

    /**
     * 检查实体是否为宿敌（带NBT标记）
     *
     * @param entity 待检查实体
     * @return true表示该实体是宿敌
     */
    public static boolean isNemesis(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(NEMESIS_TAG);
    }

    /**
     * 宿敌死亡事件处理 - 发放额外自定义掉落
     *
     * 触发条件（全部满足）：
     * 1. 配置启用额外掉落（nemesisLootEnabled）
     * 2. 死亡实体带宿敌NBT标记
     * 3. 服务端环境
     *
     * 掉落逻辑：
     * 1. 收集 toml 配置的战利品表与数据包 nemesis_loot 配置的战利品表（合并）
     * 2. 逐表 roll 掉落，产物掉落在宿敌死亡位置（原版掉落基础上额外掉落）
     *
     * @param event 实体死亡事件
     */
    @SubscribeEvent
    public void onNemesisDeath(LivingDeathEvent event) {
        // 服务端环境
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 功能开关
        if (!Config.NEMESIS_LOOT_ENABLED.get()) {
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

        DamageSource source = event.getSource();
        int totalItems = 0;

        for (ResourceLocation tableId : lootTables) {
            try {
                LootTable lootTable = serverLevel.getServer().getLootData().getLootTable(tableId);
                if (lootTable == LootTable.EMPTY) {
                    AdaptiveNemesisMod.LOGGER.warn("[宿敌] 找不到额外掉落战利品表: {}", tableId);
                    continue;
                }

                // 使用 ENTITY 参数集构建掉落上下文（实体掉落语义）
                LootParams lootParams = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.THIS_ENTITY, deadEntity)
                    .withParameter(LootContextParams.ORIGIN, deadEntity.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                    .withOptionalParameter(LootContextParams.KILLER_ENTITY, source.getEntity())
                    .withLuck(0.0F)
                    .create(LootContextParamSets.ENTITY);

                List<ItemStack> items = lootTable.getRandomItems(lootParams);
                for (ItemStack stack : items) {
                    if (!stack.isEmpty()) {
                        deadEntity.spawnAtLocation(stack);
                        totalItems++;
                    }
                }
            } catch (Exception e) {
                // 单张战利品表解析/roll 失败不影响其他表
                AdaptiveNemesisMod.LOGGER.error(
                    "[宿敌] 处理额外掉落战利品表 {} 时发生异常: {} - {}",
                    tableId, e.getClass().getSimpleName(), e.getMessage()
                );
            }
        }

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "[宿敌] {} 死亡，额外掉落: 战利品表={} 张, 物品={} 件",
                deadEntity.getName().getString(), lootTables.size(), totalItems
            );
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

        // 来源 1: toml 配置（逗号分隔）
        String configValue = Config.NEMESIS_LOOT_TABLES.get();
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
