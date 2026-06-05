package com.adaptive_nemesis.adaptive_nemesismod.enemy;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.compat.L2HostilityCompat;
import com.adaptive_nemesis.adaptive_nemesismod.compat.ModCompatManager;
import com.adaptive_nemesis.adaptive_nemesismod.kubejs.KubeJSEventTrigger;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthData;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
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

    /**
     * 已强化实体的NBT标签键
     */
    public static final String SCALED_TAG = "adaptive_nemesis_scaled";

    /**
     * 强化倍率NBT标签键
     */
    public static final String SCALE_MULTIPLIER_TAG = "adaptive_nemesis_multiplier";

    /**
     * 原始属性值前缀 - 用于防重复缩放
     * 存储缩放前的原始值，确保每次缩放都从原始值开始
     */
    private static final String ORIGINAL_PREFIX = "an_original_";
    private static final String ORIGINAL_HEALTH_TAG = ORIGINAL_PREFIX + "health";
    private static final String ORIGINAL_DAMAGE_TAG = ORIGINAL_PREFIX + "damage";
    private static final String ORIGINAL_ARMOR_TAG = ORIGINAL_PREFIX + "armor";
    private static final String ORIGINAL_TOUGHNESS_TAG = ORIGINAL_PREFIX + "toughness";
    private static final String ORIGINAL_SPEED_TAG = ORIGINAL_PREFIX + "speed";
    private static final String ORIGINAL_ATTACK_SPEED_TAG = ORIGINAL_PREFIX + "attack_speed";

    /**
     * 总倍率上限 - 防止属性爆炸
     * 在应用各属性独立上限前的总关卡
     */
    private static final double MAX_TOTAL_MULTIPLIER = 20.0;

    /**
     * 随机数生成器 - 用于属性随机分布
     */
    private final Random random = new Random();

    /**
     * 灵魂石标记 - LUCK属性的特征值
     * 灵魂石会清空NBT但保留实体核心属性（包括LUCK），
     * 我们用LUCK属性存一个特征值来标记已缩放实体，
     * 这是唯一能穿越灵魂石抓捕释放的标记手段
     */
    private static final double LUCK_MARKER_VALUE = 0.0420;

    /**
     * 对生物应用灵魂石标记
     * 将LUCK属性设为一个特征值 + 标记倍率编码，
     * 这样灵魂石释放后我们也能识别出这是被缩放过的实体
     */
    private void applyScaledMarker(Mob mob, double multiplier) {
        AttributeInstance luckAttr = mob.getAttribute(Attributes.LUCK);
        if (luckAttr != null) {
            luckAttr.setBaseValue(LUCK_MARKER_VALUE);
        }
    }

    /**
     * 检查生物是否已被灵魂石标记
     * 通过检测LUCK属性的特征值来判断
     *
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

        // 只处理敌对生物
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        // 检查黑名单 - 被ban的实体跳过所有自适应缩放
        if (EntityFilterHelper.getInstance().isBlocked(mob)) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "🚫 实体 {} 在黑名单中，跳过自适应缩放",
                    mob.getType().getDescriptionId()
                );
            }
            return;
        }

        if (!(mob instanceof Enemy)) {
            return;
        }

        // === 灵魂石调试日志：打印每次检测的详细数值 ===
        if (Config.ENABLE_DEBUG_LOG.get()) {
            AttributeInstance luckAttr = mob.getAttribute(Attributes.LUCK);
            double luckValue = luckAttr != null ? luckAttr.getBaseValue() : Double.NaN;
            boolean hasScaledTag = mob.getPersistentData().getBoolean(SCALED_TAG);
            boolean hasLuckMarker = hasScaledMarker(mob);
            double scaledTagRaw = mob.getPersistentData().contains(SCALED_TAG)
                ? (mob.getPersistentData().getBoolean(SCALED_TAG) ? 1.0 : 0.0) : -1.0;
            AdaptiveNemesisMod.LOGGER.debug(
                "🔍 [灵魂石检测] 实体={}, 类型={}, UUID={}, UUIDhex={}, "
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
        // === 调试日志结束 ===

        // 检测链路 1: NBT标记（标准路径）
        if (mob.getPersistentData().getBoolean(SCALED_TAG)) {
            return;
        }

        // 检测链路 2: LUCK属性标记（灵魂石路径 — 属性值穿越了NBT清除）
        if (hasScaledMarker(mob)) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "🔮 敌人 {} 命中LUCK属性标记（灵魂石场景），跳过重复缩放",
                    mob.getName().getString()
                );
            }
            // 恢复NBT标记，后续不再经过此路径
            mob.getPersistentData().putBoolean(SCALED_TAG, true);
            return;
        }

        // 应用强化
        applyScaling(mob);
    }

    /**
     * 对指定生物应用属性强化
     *
     * @param mob 目标生物
     */
    public void applyScaling(Mob mob) {
        if (mob.level().isClientSide()) {
            return;
        }

        // 获取附近玩家的平均强度
        double avgStrength = getNearbyPlayerStrength(mob);

        if (avgStrength <= 0) {
            return;
        }

        // 计算强化倍率
        double multiplier = calculateMultiplier(avgStrength);

        // 触发 KubeJS 实体强化事件
        double modifiedMultiplier = KubeJSEventTrigger.triggerEntityScale(mob, multiplier);

        // 如果事件取消了强化，则直接返回
        if (modifiedMultiplier < 0) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "敌人 {} 的强化被 KubeJS 事件取消",
                    mob.getName().getString()
                );
            }
            // 仍然标记为已处理，避免重复触发
            mob.getPersistentData().putBoolean(SCALED_TAG, true);
            mob.getPersistentData().putDouble(SCALE_MULTIPLIER_TAG, 1.0);
            applyScaledMarker(mob, 1.0);
            return;
        }

        // 使用 KubeJS 修改后的倍率
        multiplier = modifiedMultiplier;

        // 应用属性加成
        applyAttributeBonuses(mob, multiplier);

        // 标记为已强化
        mob.getPersistentData().putBoolean(SCALED_TAG, true);
        mob.getPersistentData().putDouble(SCALE_MULTIPLIER_TAG, multiplier);

        // 打上LUCK属性标记（灵魂石穿越标记）
        // 这个标记会随着实体的核心属性一起被灵魂石保存和释放
        applyScaledMarker(mob, multiplier);

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
     * 获取生物附近玩家的平均强度
     *
     * @param mob 目标生物
     * @return 平均强度值
     */
    private double getNearbyPlayerStrength(Mob mob) {
        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return 0.0;
        }

        Vec3 pos = mob.position();
        double range = Config.AREA_SYNC_RANGE.get() * 16; // 区块转格数

        // 获取范围内的玩家
        AABB searchBox = new AABB(
            pos.x - range, pos.y - range, pos.z - range,
            pos.x + range, pos.y + range, pos.z + range
        );

        List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(
            ServerPlayer.class,
            searchBox
        );

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

        return validPlayers > 0 ? totalStrength / validPlayers : 0.0;
    }

    /**
     * 计算强化倍率
     *
     * @param playerStrength 玩家平均强度
     * @return 强化倍率
     */
    private double calculateMultiplier(double playerStrength) {
        // 基础倍率 = 1 + (玩家强度 * 难度系数 / 100)
        double baseMultiplier = 1.0 + (playerStrength * Config.DIFFICULTY_BASE_MULTIPLIER.get() / 100.0);

        // 应用浮动调整
        double floatMultiplier = AdaptiveFloatSystem.getInstance().getFloatMultiplier();

        double preEase = baseMultiplier * floatMultiplier;

        // 应用难度缓动
        double easedMultiplier = DifficultyTracker.getInstance().getEasedMultiplier(preEase);

        // 应用世界阶段加成
        double worldStageMultiplier = 1.0;
        if (Config.ENABLE_WORLD_STAGE.get()) {
            worldStageMultiplier = WorldStageManager.getInstance().getWorldStageMultiplier();
        }

        double preCap = easedMultiplier * worldStageMultiplier;

        // 硬上限
        double afterHardCap = Math.min(preCap, MAX_TOTAL_MULTIPLIER);

        // 应用配置上限
        double finalMultiplier = afterHardCap;
        if (Config.ENABLE_ENEMY_BONUS_CAP.get()) {
            finalMultiplier = Math.min(finalMultiplier, Config.MAX_HEALTH_MULTIPLIER.get());
        }

        finalMultiplier = Math.max(1.0, finalMultiplier);

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AdaptiveNemesisMod.LOGGER.debug(
                "📊 [缩放日志] playerStrength={}, DIFFICULTY_BASE_MULTIPLIER={}, " +
                "baseMultiplier={}, floatMultiplier={}, preEase={}, " +
                "easedMultiplier={}, worldStageMultiplier={}, preCap={}, " +
                "afterHardCap={}, MAX_TOTAL_MULTIPLIER={}, ENABLE_BONUS_CAP={}, " +
                "MAX_HEALTH_MULTIPLIER={}, finalMultiplier={}",
                String.format("%.2f", playerStrength),
                String.format("%.2f", Config.DIFFICULTY_BASE_MULTIPLIER.get()),
                String.format("%.2f", baseMultiplier),
                String.format("%.2f", floatMultiplier),
                String.format("%.2f", preEase),
                String.format("%.2f", easedMultiplier),
                String.format("%.2f", worldStageMultiplier),
                String.format("%.2f", preCap),
                String.format("%.2f", afterHardCap),
                MAX_TOTAL_MULTIPLIER,
                Config.ENABLE_ENEMY_BONUS_CAP.get(),
                String.format("%.2f", Config.MAX_HEALTH_MULTIPLIER.get()),
                String.format("%.2f", finalMultiplier)
            );
        }

        return finalMultiplier;
    }

    /**
     * 应用属性加成到生物
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     */
    private void applyAttributeBonuses(Mob mob, double multiplier) {
        if (Config.ENABLE_DEBUG_LOG.get()) {
            CompoundTag data = mob.getPersistentData();
            boolean alreadyScaled = data.contains(ORIGINAL_HEALTH_TAG);
            double currentHealth = mob.getAttribute(Attributes.MAX_HEALTH) != null ?
                mob.getAttribute(Attributes.MAX_HEALTH).getBaseValue() : -1;
            double originalHealth = alreadyScaled ? data.getDouble(ORIGINAL_HEALTH_TAG) : currentHealth;
            String mobName = mob.getName().getString();
            String mobType = mob.getType().getDescriptionId();

            AdaptiveNemesisMod.LOGGER.debug(
                "🔍 [缩放入口] mob={}({}), UUID={}, alreadyScaled={}, " +
                "currentHealth={}, originalHealth={}, multiplier={}, " +
                "SCALED_TAG={}, dim={}",
                mobName, mobType, mob.getUUID(),
                alreadyScaled,
                String.format("%.2f", currentHealth),
                String.format("%.2f", originalHealth),
                String.format("%.2f", multiplier),
                data.getBoolean(SCALED_TAG),
                mob.level().dimension().location()
            );
        }

        // 计算各属性的随机分布因子
        double healthRandomFactor = getRandomFactor();
        double damageRandomFactor = getRandomFactor();
        double armorRandomFactor = getRandomFactor();
        double toughnessRandomFactor = getRandomFactor();
        double attackSpeedRandomFactor = getRandomFactor();

        // 血量加成（带随机分布）
        applyHealthBonus(mob, multiplier, healthRandomFactor);

        // 伤害加成（带随机分布）
        applyDamageBonus(mob, multiplier, damageRandomFactor);

        // 护甲加成（带随机分布）
        applyArmorBonus(mob, multiplier, armorRandomFactor);

        // 移动速度加成 - 根据配置决定是否固定为0
        if (Config.FIX_SPEED_BONUS_TO_ZERO.get()) {
            // 速度固定为0，不应用任何加成
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.debug(
                    "敌人 {} 速度加成已固定为0（防止跑得太快）",
                    mob.getName().getString()
                );
            }
        } else {
            applySpeedBonus(mob, multiplier);
        }

        // 攻击速度加成（防止史诗战斗无限硬直，带随机分布）
        applyAttackSpeedBonus(mob, multiplier, attackSpeedRandomFactor);

        // 护甲韧性加成（带随机分布）
        applyToughnessBonus(mob, multiplier, toughnessRandomFactor);

        // 应用 Epic Fight 属性加成（受击抗性、冲击力、破甲、连击等）
        if (ModCompatManager.isEpicFightLoaded()) {
            // Epic Fight 属性也应用随机因子
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

        // 应用 Iron's Spells 属性加成（法术强度、法力、冷却缩减、魔法抗性等）
        if (ModCompatManager.isIronsSpellsLoaded()) {
            // Iron's Spells 属性也应用随机因子
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

        // 应用宿敌记忆加成（如果有玩家档案）
        applyNemesisBonuses(mob);

        // 缩放完成后进行状态验证
        validateMobState(mob);

        if (Config.ENABLE_DEBUG_LOG.get()) {
            AttributeInstance finalHealth = mob.getAttribute(Attributes.MAX_HEALTH);
            AttributeInstance finalDamage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            AttributeInstance finalArmor = mob.getAttribute(Attributes.ARMOR);
            CompoundTag data = mob.getPersistentData();
            double origH = data.contains(ORIGINAL_HEALTH_TAG) ? data.getDouble(ORIGINAL_HEALTH_TAG) : -1;

            AdaptiveNemesisMod.LOGGER.debug(
                "✅ [缩放结果] mob={}, origHealth={}, finalHealth={}, " +
                "finalDamage={}, finalArmor={}, healthRF={}, damageRF={}, " +
                "armorRF={}, hasOriginalValues={}",
                mob.getName().getString(),
                String.format("%.2f", origH),
                finalHealth != null ? String.format("%.2f", finalHealth.getBaseValue()) : "N/A",
                finalDamage != null ? String.format("%.2f", finalDamage.getBaseValue()) : "N/A",
                finalArmor != null ? String.format("%.2f", finalArmor.getBaseValue()) : "N/A",
                String.format("%.2f", healthRandomFactor),
                String.format("%.2f", damageRandomFactor),
                String.format("%.2f", armorRandomFactor),
                data.contains(ORIGINAL_HEALTH_TAG)
            );
        }
    }

    /**
     * 获取或存储原始属性值
     * 确保每次缩放都从原始值开始，防止重复缩放导致属性爆炸
     * 
     * 注意：此方法不处理 NBT 标记丢失的情况（灵魂石等工具抓取后重置），
     * 对于血量属性请使用 applyHealthBonus 中的 DefaultAttributes 回退机制
     */
    private double getOrStoreOriginal(CompoundTag data, String tagKey, double currentValue) {
        if (data.contains(tagKey)) {
            return data.getDouble(tagKey);
        }
        data.putDouble(tagKey, currentValue);
        return currentValue;
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
    private double getDefaultAttributeBase(Mob mob, Holder<Attribute> attribute, double fallbackValue) {
        try {
            // DefaultAttributes.getSupplier 需要 EntityType<? extends LivingEntity>
            // mob.getType() 返回的是 EntityType<? extends Entity>，需要安全转型
            @SuppressWarnings("unchecked")
            var entityType = (EntityType<? extends LivingEntity>) mob.getType();
            var supplier = DefaultAttributes.getSupplier(entityType);
            if (supplier != null) {
                return supplier.getBaseValue(attribute);
            }
        } catch (Exception e) {
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.warn(
                    "获取实体 {} 的默认属性失败: {}",
                    mob.getType().getDescriptionId(), e.getMessage()
                );
            }
        }
        return fallbackValue;
    }

    /**
     * 缩放完成后进行怪物状态验证
     * 检查并修复可能导致怪物无法被攻击的异常状态：
     * - 清除 Invulnerable 标签
     * - 修复 NaN/Infinity 属性值
     * - 验证血量有效性
     */
    private void validateMobState(Mob mob) {
        // 检查并清除实体真正的无敌标志（isInvulnerable/setInvulnerable 操作 Entity.invulnerable 字段）
        // ⚠️ 注意：getPersistentData().getBoolean("Invulnerable") 是模组自定义数据，和实体无敌无关！
        if (mob.isInvulnerable()) {
            mob.setInvulnerable(false);
            if (Config.ENABLE_DEBUG_LOG.get()) {
                AdaptiveNemesisMod.LOGGER.warn(
                    "🔧 缩放后修复怪物 {} 的 Invulnerable 标志",
                    mob.getName().getString()
                );
            }
        }

        // 检查关键属性是否为 NaN/Infinity，修复为安全值
        var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            double health = healthAttr.getBaseValue();
            if (Double.isNaN(health) || Double.isInfinite(health) || health < 1.0) {
                healthAttr.setBaseValue(20.0); // 直接设为默认20点血量
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.warn(
                        "🔧 修复怪物 {} 的无效 MaxHealth: {}",
                        mob.getName().getString(), health
                    );
                }
            }
        }

        var damageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            double dmg = damageAttr.getBaseValue();
            if (Double.isNaN(dmg) || Double.isInfinite(dmg)) {
                damageAttr.setBaseValue(1.0); // 默认1点伤害
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.warn(
                        "🔧 修复怪物 {} 的无效 AttackDamage: {}",
                        mob.getName().getString(), dmg
                    );
                }
            }
        }

        var armorAttr = mob.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            double armor = armorAttr.getBaseValue();
            if (Double.isNaN(armor) || Double.isInfinite(armor) || armor < 0) {
                armorAttr.setBaseValue(0.0); // 默认0护甲
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.warn(
                        "🔧 修复怪物 {} 的无效 Armor: {}",
                        mob.getName().getString(), armor
                    );
                }
            }
        }

        // 确保怪物当前血量有效
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
        double originalValue = getOrStoreOriginal(data, originalTag, attrInstance.getBaseValue());
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
        return minFactor + random.nextDouble() * (maxFactor - minFactor);
    }

    /**
     * 应用血量加成（带随机分布）
     * 使用原始值确保幂等性 - 防止重复缩放
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
        if (healthAttr != null) {
            CompoundTag data = mob.getPersistentData();

            // 🛡️ 防血量爆炸核心逻辑：
            //
            // 原始血量计算分两步：
            // 1. 如果已有 NBT 标记（ORIGINAL_HEALTH_TAG），直接从标记中读取
            //    —— 这是正常情况，保证幂等性（重复触发不重复加成）
            // 2. 如果 NBT 标记丢失（灵魂石、魂符、生物套索等工具抓取后重置 NBT），
            //    使用 DefaultAttributes 查询该实体类型注册时的默认血量作为"真·原始值"
            //    —— 防止已经缩放过的高血量被当作原始值再次缩放，导致指数级爆炸 💥
            double originalBase;
            if (data.contains(ORIGINAL_HEALTH_TAG)) {
                originalBase = data.getDouble(ORIGINAL_HEALTH_TAG);
            } else {
                originalBase = getDefaultAttributeBase(mob, Attributes.MAX_HEALTH, healthAttr.getBaseValue());
                data.putDouble(ORIGINAL_HEALTH_TAG, originalBase);
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.debug(
                        "🛡️ 防血量爆炸: {} 的 NBT 标记丢失，" +
                        "使用实体类型默认血量 {} (当前基础血量={})",
                        mob.getName().getString(),
                        String.format("%.2f", originalBase),
                        String.format("%.2f", healthAttr.getBaseValue())
                    );
                }
            }

            double effectiveMultiplier = Math.min(multiplier * randomFactor, Config.MAX_HEALTH_MULTIPLIER.get());
            double newMax = originalBase * effectiveMultiplier;
            if (newMax < originalBase) {
                newMax = originalBase;
            }
            healthAttr.setBaseValue(newMax);

            // 立即尝试设置满血
            mob.setHealth(mob.getMaxHealth());

            // 延迟1tick再填满血量（关键！）
            // 解决 setBaseValue 后属性未立即传播，setHealth 被 clamp 在旧最大值的问题
            if (!mob.level().isClientSide() && mob.level() instanceof ServerLevel serverLevel) {
                serverLevel.getServer().execute(() -> {
                    if (!mob.isRemoved() && mob.isAlive()) {
                        mob.setHealth(mob.getMaxHealth());
                    }
                });
            }

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
    }

    /**
     * 应用伤害加成（带随机分布）
     * 使用原始值确保幂等性 - 防止重复缩放
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
     * 使用原始值确保幂等性 - 防止重复缩放
     */
    private void applyToughnessBonus(Mob mob, double multiplier, double randomFactor) {
        applyAttributeBonus(
            mob,
            Attributes.ARMOR_TOUGHNESS,
            multiplier,
            randomFactor,
            null,
            null,
            ORIGINAL_TOUGHNESS_TAG
        );
    }

    /**
     * 应用移动速度加成（原版，不带随机分布）
     * 使用原始值确保幂等性
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

        // 获取附近的玩家，应用其宿敌档案加成
        Vec3 pos = mob.position();
        double range = Config.AREA_SYNC_RANGE.get() * 16;

        AABB searchBox = new AABB(
            pos.x - range, pos.y - range, pos.z - range,
            pos.x + range, pos.y + range, pos.z + range
        );

        List<ServerPlayer> nearbyPlayers = serverLevel.getEntitiesOfClass(ServerPlayer.class, searchBox);

        for (ServerPlayer player : nearbyPlayers) {
            var profile = NemesisMemorySystem.getInstance().getProfile(player.getUUID());
            if (profile == null) continue;

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

            break; // 只应用第一个玩家的宿敌加成
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
     * @return 强化倍率，如果未强化返回1.0
     */
    public double getScaleMultiplier(Mob mob) {
        return mob.getPersistentData().getDouble(SCALE_MULTIPLIER_TAG);
    }
}
