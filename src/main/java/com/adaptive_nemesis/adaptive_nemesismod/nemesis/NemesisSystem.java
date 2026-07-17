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
            player.sendSystemMessage(Component.literal("⚠ 宿敌出现！").withStyle(ChatFormatting.RED));
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
     * 
     * @param monster 敌人实体
     * @param multiplier 强化倍率
     */
    private void applyStatsMultiplier(Mob monster, double multiplier) {
        monster.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
            .setBaseValue(monster.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue() * multiplier);
        monster.setHealth(monster.getMaxHealth());

        monster.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
            .setBaseValue(monster.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getBaseValue() * multiplier);

        monster.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
            .setBaseValue(monster.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR).getBaseValue() * (0.5 + multiplier * 0.5));

        monster.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
            .setBaseValue(monster.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getBaseValue() * (0.8 + multiplier * 0.2));

        if (monster.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) != null) {
            monster.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)
                .setBaseValue(Math.min(0.9, monster.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).getBaseValue() + (multiplier - 1.0) * 0.3));
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
