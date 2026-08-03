package com.adaptive_nemesis.adaptive_nemesismod.nemesis;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

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

    private final NemesisNameGenerator nameGenerator;
    private final Random random;

    /**
     * 构造函数
     * 注册事件监听器
     */
    public NemesisSystem() {
        this.nameGenerator = new NemesisNameGenerator();
        this.random = new Random();
        NeoForge.EVENT_BUS.register(this);
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

        Component nemesisName = nameGenerator.generateNemesisName(monster, multiplier);
        monster.setCustomName(nemesisName);
        monster.setCustomNameVisible(Config.NEMESIS_NAME_ALWAYS_VISIBLE.get());

        monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE));

        return true;
    }
}
