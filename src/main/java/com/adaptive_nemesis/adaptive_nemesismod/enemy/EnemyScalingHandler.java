package com.adaptive_nemesis.adaptive_nemesismod.enemy;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.compat.ModCompatManager;
import com.adaptive_nemesis.adaptive_nemesismod.kubejs.KubeJSEventTrigger;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthData;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
     * 随机数生成器 - 用于属性随机分布
     */
    private final Random random = new Random();

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

        if (!(mob instanceof Enemy)) {
            return;
        }

        // 检查是否已经强化过
        if (mob.getPersistentData().getBoolean(SCALED_TAG)) {
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
            return;
        }

        // 使用 KubeJS 修改后的倍率
        multiplier = modifiedMultiplier;

        // 应用属性加成
        applyAttributeBonuses(mob, multiplier);

        // 标记为已强化
        mob.getPersistentData().putBoolean(SCALED_TAG, true);
        mob.getPersistentData().putDouble(SCALE_MULTIPLIER_TAG, multiplier);

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
        // 基础倍率 = 1 + (玩家强度 * 难度系数 / 50)
        // 将除数从100改为50，使加成更强
        double baseMultiplier = 1.0 + (playerStrength * Config.DIFFICULTY_BASE_MULTIPLIER.get() / 50.0);

        // 应用浮动调整
        double floatMultiplier = AdaptiveFloatSystem.getInstance().getFloatMultiplier();

        // 应用难度缓动
        double easedMultiplier = DifficultyTracker.getInstance().getEasedMultiplier(baseMultiplier * floatMultiplier);

        // 应用世界阶段加成
        double worldStageMultiplier = 1.0;
        if (Config.ENABLE_WORLD_STAGE.get()) {
            worldStageMultiplier = WorldStageManager.getInstance().getWorldStageMultiplier();
        }

        double finalMultiplier = easedMultiplier * worldStageMultiplier;

        // 应用上限
        if (Config.ENABLE_ENEMY_BONUS_CAP.get()) {
            finalMultiplier = Math.min(finalMultiplier, Config.MAX_HEALTH_MULTIPLIER.get());
        }

        return Math.max(1.0, finalMultiplier);
    }

    /**
     * 应用属性加成到生物
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     */
    private void applyAttributeBonuses(Mob mob, double multiplier) {
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
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     * @param randomFactor 随机分布因子
     */
    private void applyHealthBonus(Mob mob, double multiplier, double randomFactor) {
        AttributeInstance healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            double originalMax = healthAttr.getBaseValue();
            double effectiveMultiplier = multiplier * randomFactor;
            double newMax = originalMax * Math.min(effectiveMultiplier, Config.MAX_HEALTH_MULTIPLIER.get());
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
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     * @param randomFactor 随机分布因子
     */
    private void applyDamageBonus(Mob mob, double multiplier, double randomFactor) {
        AttributeInstance damageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            double originalDamage = damageAttr.getBaseValue();
            double effectiveMultiplier = multiplier * randomFactor;
            double damageMultiplier = Math.min(effectiveMultiplier, Config.MAX_DAMAGE_MULTIPLIER.get());
            damageAttr.setBaseValue(originalDamage * damageMultiplier);
        }
    }

    /**
     * 应用护甲加成（带随机分布）
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     * @param randomFactor 随机分布因子
     */
    private void applyArmorBonus(Mob mob, double multiplier, double randomFactor) {
        AttributeInstance armorAttr = mob.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            double originalArmor = armorAttr.getBaseValue();
            double effectiveMultiplier = multiplier * randomFactor;
            double armorMultiplier = Math.min(effectiveMultiplier, Config.MAX_ARMOR_MULTIPLIER.get());
            armorAttr.setBaseValue(originalArmor * armorMultiplier);
        }
    }

    /**
     * 应用护甲韧性加成（带随机分布）
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     * @param randomFactor 随机分布因子
     */
    private void applyToughnessBonus(Mob mob, double multiplier, double randomFactor) {
        AttributeInstance toughnessAttr = mob.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughnessAttr != null) {
            double originalToughness = toughnessAttr.getBaseValue();
            double effectiveMultiplier = multiplier * randomFactor;
            toughnessAttr.setBaseValue(originalToughness * effectiveMultiplier);
        }
    }

    /**
     * 应用移动速度加成（原版，不带随机分布）
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     */
    private void applySpeedBonus(Mob mob, double multiplier) {
        AttributeInstance speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            double originalSpeed = speedAttr.getBaseValue();
            // 速度加成：基础 + (倍率-1) * 0.3
            double speedMultiplier = 1.0 + (multiplier - 1.0) * 0.3;
            speedAttr.setBaseValue(originalSpeed * Math.min(speedMultiplier, 2.0));
        }
    }

    /**
     * 应用攻击速度加成 - 防止史诗战斗无限硬直（带随机分布）
     *
     * @param mob 目标生物
     * @param multiplier 强化倍率
     * @param randomFactor 随机分布因子
     */
    private void applyAttackSpeedBonus(Mob mob, double multiplier, double randomFactor) {
        // 攻击速度加成，让敌人能更快反击，防止被玩家无限硬直
        AttributeInstance attackSpeedAttr = mob.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            double originalSpeed = attackSpeedAttr.getBaseValue();
            double effectiveMultiplier = multiplier * randomFactor;
            // 攻击速度随倍率增加，最高增加100%
            double attackSpeedMultiplier = 1.0 + (effectiveMultiplier - 1.0) * 0.25;
            attackSpeedAttr.setBaseValue(originalSpeed * Math.min(attackSpeedMultiplier, 2.0));
        }
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
