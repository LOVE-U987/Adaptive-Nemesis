package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisProfile;
import com.adaptive_nemesis.adaptive_nemesismod.nemesis.NemesisSystem;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.neoforged.neoforge.event.EventHooks;

/**
 * 宿敌召唤命令
 *
 * 命令: /an nemesis [zombie|skeleton|creeper|spider|witch]
 * 功能: 召唤一个针对玩家战斗风格的宿敌
 *
 * 加成约束：所有倍率类加成经 {@code ENABLE_ENEMY_BONUS_CAP} 与对应
 * {@code MAX_*_MULTIPLIER} 配置钳制，防止命令召唤绕过全局加成上限；
 * 实体创建后走 {@code finalizeSpawn}（COMMAND 类型）补齐原版生成流程
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class SummonNemesisCommand {

    /**
     * 可用的宿敌类型
     */
    private static final String[] NEMESIS_TYPES = {
        "zombie", "skeleton", "creeper", "spider", "witch", "random"
    };

    /**
     * 随机选择时的候选类型数
     */
    private static final String[] RANDOM_CANDIDATES = {
        "zombie", "skeleton", "creeper", "spider", "witch"
    };

    // ==================== 档案加成基础倍率与权重 ====================

    /** 有档案时血量基础倍率 */
    private static final double PROFILE_HEALTH_BASE = 3.0;
    /** 档案血量加成系数权重 */
    private static final double PROFILE_HEALTH_BONUS_WEIGHT = 5.0;
    /** 有档案时攻击基础倍率 */
    private static final double PROFILE_ATTACK_BASE = 2.5;
    /** 档案攻击加成系数权重 */
    private static final double PROFILE_ATTACK_BONUS_WEIGHT = 3.0;
    /** 有档案时速度基础倍率 */
    private static final double PROFILE_SPEED_BASE = 1.2;
    /** 有档案时护甲固定加成 */
    private static final double PROFILE_ARMOR_BONUS = 8.0;
    /** 有档案时护甲韧性固定加成 */
    private static final double PROFILE_TOUGHNESS_BONUS = 4.0;
    /** 有档案时攻速倍率 */
    private static final double PROFILE_ATTACK_SPEED_MULTIPLIER = 1.8;
    /** 有档案时击退抗性 */
    private static final double PROFILE_KNOCKBACK_RESISTANCE = 0.6;

    /** 近战风格护甲加成权重 */
    private static final double MELEE_ARMOR_BONUS_WEIGHT = 30.0;
    /** 近战风格韧性加成权重 */
    private static final double MELEE_TOUGHNESS_BONUS_WEIGHT = 15.0;
    /** 远程风格速度加成权重 */
    private static final double RANGED_SPEED_BONUS_WEIGHT = 2.0;
    /** 法师风格血量加成权重 */
    private static final double MAGIC_HEALTH_BONUS_WEIGHT = 2.0;

    // ==================== 基础加成（无档案） ====================

    /** 无档案时血量基础倍率 */
    private static final double BASIC_HEALTH_MULTIPLIER = 5.0;
    /** 无档案时攻击基础倍率 */
    private static final double BASIC_ATTACK_MULTIPLIER = 3.0;
    /** 无档案时护甲固定加成 */
    private static final double BASIC_ARMOR_BONUS = 10.0;
    /** 无档案时护甲韧性固定加成 */
    private static final double BASIC_TOUGHNESS_BONUS = 5.0;
    /** 无档案时攻速倍率 */
    private static final double BASIC_ATTACK_SPEED_MULTIPLIER = 2.0;
    /** 无档案时击退抗性 */
    private static final double BASIC_KNOCKBACK_RESISTANCE = 0.8;

    /** 宿敌生成在玩家前方几格 */
    private static final double SPAWN_DISTANCE_AHEAD = 3.0;

    /**
     * 命令建议提供者
     */
    private static final SuggestionProvider<CommandSourceStack> TYPE_SUGGESTIONS = (context, builder) -> {
        for (String type : NEMESIS_TYPES) {
            builder.suggest(type);
        }
        return builder.buildFuture();
    };

    /**
     * 注册宿敌召唤命令
     *
     * @return 命令构建器
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("nemesis")
            .then(Commands.argument("type", StringArgumentType.word())
                .suggests(TYPE_SUGGESTIONS)
                .executes(SummonNemesisCommand::executeSummon))
            .executes(SummonNemesisCommand::executeSummonRandom);
    }

    /**
     * 随机召唤宿敌
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeSummonRandom(CommandContext<CommandSourceStack> context) {
        return summonNemesis(context, "random");
    }

    /**
     * 执行召唤宿敌
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeSummon(CommandContext<CommandSourceStack> context) {
        String type = StringArgumentType.getString(context, "type").toLowerCase();
        return summonNemesis(context, type);
    }

    /**
     * 召唤宿敌的核心逻辑
     *
     * @param context 命令上下文
     * @param type 宿敌类型
     * @return 命令执行结果
     */
    private static int summonNemesis(CommandContext<CommandSourceStack> context, String type) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.summon.player_only"));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        // 确定实体类型（level.random 为公共 RandomSource 字段）
        EntityType<? extends Mob> entityType = getEntityType(type, level.random);
        if (entityType == null) {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.summon.unknown_type", type));
            return 0;
        }

        // 在玩家附近生成宿敌
        Mob nemesis = entityType.create(level);
        if (nemesis == null) {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.summon.failed"));
            return 0;
        }

        placeNemesis(player, pos, nemesis);
        applyNemesisBuffs(nemesis, player, level.random);
        level.addFreshEntity(nemesis);

        // 发送通知与命令反馈（名称以 Component 传递，保留客户端本地化）
        Component nemesisName = getNemesisName(entityType);
        sendNemesisNotification(player, nemesisName);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.summon.arrived", nemesisName
        ), true);

        AdaptiveNemesisMod.LOGGER.debug(
            "玩家 {} 召唤了宿敌: {}",
            player.getName().getString(),
            nemesisName.getString()
        );

        return 1;
    }

    /**
     * 设置宿敌位置并执行原版生成收尾流程
     *
     * @param player 发起召唤的玩家
     * @param pos 玩家所在位置
     * @param nemesis 宿敌实体
     */
    private static void placeNemesis(ServerPlayer player, BlockPos pos, Mob nemesis) {
        double offsetX = player.getLookAngle().x * SPAWN_DISTANCE_AHEAD;
        double offsetZ = player.getLookAngle().z * SPAWN_DISTANCE_AHEAD;
        nemesis.setPos(pos.getX() + offsetX, pos.getY(), pos.getZ() + offsetZ);

        // 走 NeoForge 收尾入口：触发 FinalizeSpawnEvent 并调用 finalizeSpawn(COMMAND)
        EventHooks.finalizeMobSpawn(
            nemesis,
            player.serverLevel(),
            player.serverLevel().getCurrentDifficultyAt(pos),
            MobSpawnType.COMMAND,
            null
        );
    }

    /**
     * 应用宿敌加成
     *
     * @param nemesis 宿敌实体
     * @param player 目标玩家
     * @param random 世界随机源，用于随机分布
     */
    private static void applyNemesisBuffs(Mob nemesis, ServerPlayer player, RandomSource random) {
        NemesisProfile profile = NemesisMemorySystem.getInstance().getProfile(player.getUUID());

        if (profile == null) {
            // 如果没有档案，使用基础加成
            applyBasicBuffs(nemesis, random);
            return;
        }

        applyProfileCoreBuffs(nemesis, profile, random);
        applyProfileStyleBuffs(nemesis, profile);
        applyNemesisIdentity(nemesis);
    }

    /**
     * 应用档案核心加成（血量/攻击/速度/防御/攻速/击退抗性）
     *
     * @param nemesis 宿敌实体
     * @param profile 玩家宿敌档案
     * @param random 世界随机源，用于随机分布
     */
    private static void applyProfileCoreBuffs(Mob nemesis, NemesisProfile profile, RandomSource random) {
        // 血量加成：基础3倍 + 档案加成，经配置上限钳制
        double healthMultiplier = PROFILE_HEALTH_BASE + profile.getHealthBonus() * PROFILE_HEALTH_BONUS_WEIGHT;
        healthMultiplier = clampByCap(healthMultiplier, Config.MAX_HEALTH_MULTIPLIER.get());
        applyHealthWithDistribution(nemesis, healthMultiplier, random);

        // 攻击加成：基础2.5倍 + 档案加成，经配置上限钳制
        AttributeInstance damageAttr = nemesis.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            double attackMultiplier = PROFILE_ATTACK_BASE + profile.getAttackBonus() * PROFILE_ATTACK_BONUS_WEIGHT;
            attackMultiplier = clampByCap(attackMultiplier, Config.MAX_DAMAGE_MULTIPLIER.get());
            damageAttr.setBaseValue(damageAttr.getBaseValue() * attackMultiplier);
        }

        // 速度加成：基础1.2倍 + 档案加成
        AttributeInstance speedAttr = nemesis.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * (PROFILE_SPEED_BASE + profile.getSpeedBonus()));
        }

        applyFlatDefenseBuffs(nemesis, PROFILE_ARMOR_BONUS, PROFILE_TOUGHNESS_BONUS);

        // 增加攻击速度
        AttributeInstance attackSpeedAttr = nemesis.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            attackSpeedAttr.setBaseValue(attackSpeedAttr.getBaseValue() * PROFILE_ATTACK_SPEED_MULTIPLIER);
        }

        // 增加击退抗性
        AttributeInstance knockbackAttr = nemesis.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(PROFILE_KNOCKBACK_RESISTANCE);
        }
    }

    /**
     * 根据玩家战斗风格应用特殊抗性加成
     *
     * @param nemesis 宿敌实体
     * @param profile 玩家宿敌档案
     */
    private static void applyProfileStyleBuffs(Mob nemesis, NemesisProfile profile) {
        switch (profile.getDominantStyle()) {
            case MELEE -> applyMeleeStyleBuffs(nemesis, profile);
            case RANGED -> applyRangedStyleBuffs(nemesis, profile);
            case MAGIC -> applyMagicStyleBuffs(nemesis, profile);
        }
    }

    /**
     * 近战玩家风格：宿敌获得大量近战抗性（护甲+韧性）
     *
     * @param nemesis 宿敌实体
     * @param profile 玩家宿敌档案
     */
    private static void applyMeleeStyleBuffs(Mob nemesis, NemesisProfile profile) {
        AttributeInstance armorAttr = nemesis.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            double bonus = profile.getMeleeResistanceBonus() * MELEE_ARMOR_BONUS_WEIGHT;
            armorAttr.setBaseValue(clampAdditiveByCap(armorAttr.getBaseValue(), bonus,
                Config.MAX_ARMOR_MULTIPLIER.get()));
        }
        AttributeInstance toughnessAttr = nemesis.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughnessAttr != null) {
            toughnessAttr.setBaseValue(toughnessAttr.getBaseValue()
                + profile.getMeleeResistanceBonus() * MELEE_TOUGHNESS_BONUS_WEIGHT);
        }
    }

    /**
     * 远程玩家风格：宿敌获得极高速度（更容易接近）+ 完全免疫击退
     *
     * @param nemesis 宿敌实体
     * @param profile 玩家宿敌档案
     */
    private static void applyRangedStyleBuffs(Mob nemesis, NemesisProfile profile) {
        AttributeInstance speedAttr = nemesis.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue()
                * (1.0 + profile.getRangedResistanceBonus() * RANGED_SPEED_BONUS_WEIGHT));
        }
        AttributeInstance knockbackAttr = nemesis.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(1.0); // 完全免疫击退
        }
    }

    /**
     * 法师玩家风格：宿敌获得巨额血量
     *
     * @param nemesis 宿敌实体
     * @param profile 玩家宿敌档案
     */
    private static void applyMagicStyleBuffs(Mob nemesis, NemesisProfile profile) {
        AttributeInstance healthAttr = nemesis.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            double multiplier = 1.0 + profile.getMagicResistanceBonus() * MAGIC_HEALTH_BONUS_WEIGHT;
            multiplier = clampByCap(multiplier, Config.MAX_HEALTH_MULTIPLIER.get());
            healthAttr.setBaseValue(healthAttr.getBaseValue() * multiplier);
        }
    }

    /**
     * 应用基础加成（无档案时）
     *
     * @param nemesis 宿敌实体
     * @param random 世界随机源，用于随机分布
     */
    private static void applyBasicBuffs(Mob nemesis, RandomSource random) {
        // 大幅提升血量 - 基础5倍，加上随机波动，经配置上限钳制
        applyHealthWithDistribution(nemesis,
            clampByCap(BASIC_HEALTH_MULTIPLIER, Config.MAX_HEALTH_MULTIPLIER.get()), random);

        // 大幅提升攻击力 - 3倍基础攻击，经配置上限钳制
        AttributeInstance damageAttr = nemesis.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            double attackMultiplier = clampByCap(BASIC_ATTACK_MULTIPLIER, Config.MAX_DAMAGE_MULTIPLIER.get());
            damageAttr.setBaseValue(damageAttr.getBaseValue() * attackMultiplier);
        }

        applyFlatDefenseBuffs(nemesis, BASIC_ARMOR_BONUS, BASIC_TOUGHNESS_BONUS);

        // 增加攻击速度 - 让宿敌攻击更频繁
        AttributeInstance attackSpeedAttr = nemesis.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            attackSpeedAttr.setBaseValue(attackSpeedAttr.getBaseValue() * BASIC_ATTACK_SPEED_MULTIPLIER);
        }

        // 增加击退抗性 - 防止被无限击退
        AttributeInstance knockbackAttr = nemesis.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(BASIC_KNOCKBACK_RESISTANCE);
        }

        applyNemesisIdentity(nemesis);
    }

    /**
     * 应用护甲与护甲韧性固定加成（档案与基础路径共用）
     *
     * 护甲加成经 ENABLE_ENEMY_BONUS_CAP 与 MAX_ARMOR_MULTIPLIER 钳制
     *
     * @param nemesis 宿敌实体
     * @param armorBonus 护甲固定加成
     * @param toughnessBonus 护甲韧性固定加成
     */
    private static void applyFlatDefenseBuffs(Mob nemesis, double armorBonus, double toughnessBonus) {
        AttributeInstance armorAttr = nemesis.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(clampAdditiveByCap(armorAttr.getBaseValue(), armorBonus,
                Config.MAX_ARMOR_MULTIPLIER.get()));
        }
        AttributeInstance toughnessAttr = nemesis.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughnessAttr != null) {
            toughnessAttr.setBaseValue(toughnessAttr.getBaseValue() + toughnessBonus);
        }
    }

    /**
     * 按随机分布应用血量加成并同步当前血量
     *
     * @param nemesis 宿敌实体
     * @param multiplier 血量倍率（已钳制）
     * @param random 世界随机源
     */
    private static void applyHealthWithDistribution(Mob nemesis, double multiplier, RandomSource random) {
        AttributeInstance healthAttr = nemesis.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) {
            return;
        }
        double newHealth = healthAttr.getBaseValue() * multiplier;

        // 应用随机分布
        if (Config.ENABLE_RANDOM_DISTRIBUTION.get()) {
            double minFactor = Config.RANDOM_MIN_FACTOR.get();
            double maxFactor = Config.RANDOM_MAX_FACTOR.get();
            newHealth = newHealth * (minFactor + (maxFactor - minFactor) * random.nextDouble());
        }

        healthAttr.setBaseValue(newHealth);
        nemesis.setHealth((float) newHealth);
    }

    /**
     * 设置宿敌自定义名称并打上宿敌标记（档案与基础路径共用）
     *
     * @param nemesis 宿敌实体
     */
    private static void applyNemesisIdentity(Mob nemesis) {
        // 设置自定义名称（使用 Component 拼接，保留客户端本地化）
        nemesis.setCustomName(Component.translatable("adaptive_nemesis.command.summon.name_prefix")
            .append(nemesis.getName().copy()));
        nemesis.setCustomNameVisible(true);

        // 打上宿敌标记，供铁魔法等兼容层识别（宿敌应用法术抗性加成，施法怪另获法术强度加成）
        nemesis.getPersistentData().putBoolean(NemesisSystem.NEMESIS_TAG, true);
    }

    /**
     * 倍率类加成按配置上限钳制
     *
     * @param multiplier 待钳制的倍率
     * @param capValue 配置的上限值
     * @return 未开启上限时原样返回，否则返回不超过上限的倍率
     */
    private static double clampByCap(double multiplier, double capValue) {
        if (!Config.ENABLE_ENEMY_BONUS_CAP.get()) {
            return multiplier;
        }
        return Math.min(multiplier, capValue);
    }

    /**
     * 固定值加成按配置上限钳制
     *
     * @param baseValue 属性基础值
     * @param bonus 待附加的固定加成
     * @param capMultiplier 配置的上限倍率（相对基础值）
     * @return 未开启上限时返回 baseValue + bonus，否则不超过 baseValue * capMultiplier
     */
    private static double clampAdditiveByCap(double baseValue, double bonus, double capMultiplier) {
        if (!Config.ENABLE_ENEMY_BONUS_CAP.get()) {
            return baseValue + bonus;
        }
        return Math.min(baseValue + bonus, baseValue * capMultiplier);
    }

    /**
     * 发送宿敌通知
     *
     * @param player 目标玩家
     * @param nemesisName 宿敌名称组件（保留客户端本地化）
     */
    private static void sendNemesisNotification(ServerPlayer player, Component nemesisName) {
        // 发送标题
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
            Component.translatable("adaptive_nemesis.command.summon.title_arrived")
        ));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
            Component.translatable("adaptive_nemesis.command.summon.subtitle", nemesisName)
        ));

        // 发送聊天消息
        player.sendSystemMessage(Component.translatable(
            "adaptive_nemesis.command.summon.divider"
        ));
        player.sendSystemMessage(Component.translatable(
            "adaptive_nemesis.command.summon.chat_arrived", nemesisName
        ));
        player.sendSystemMessage(Component.translatable(
            "adaptive_nemesis.command.summon.strengthened"
        ));
        player.sendSystemMessage(Component.translatable(
            "adaptive_nemesis.command.summon.remember"
        ));
        player.sendSystemMessage(Component.translatable(
            "adaptive_nemesis.command.summon.divider"
        ));
    }

    /**
     * 获取实体类型
     *
     * @param type 类型字符串
     * @param random 世界随机源，用于随机选择
     * @return 实体类型
     */
    @SuppressWarnings("unchecked")
    private static EntityType<? extends Mob> getEntityType(String type, RandomSource random) {
        if (type.equals("random")) {
            type = RANDOM_CANDIDATES[random.nextInt(RANDOM_CANDIDATES.length)];
        }

        return switch (type) {
            case "zombie" -> (EntityType<? extends Mob>) EntityType.ZOMBIE;
            case "skeleton" -> (EntityType<? extends Mob>) EntityType.SKELETON;
            case "creeper" -> (EntityType<? extends Mob>) EntityType.CREEPER;
            case "spider" -> (EntityType<? extends Mob>) EntityType.SPIDER;
            case "witch" -> (EntityType<? extends Mob>) EntityType.WITCH;
            default -> null;
        };
    }

    /**
     * 获取宿敌名称（返回组件以保留客户端本地化）
     *
     * @param entityType 实体类型
     * @return 宿敌名称组件
     */
    private static Component getNemesisName(EntityType<? extends Mob> entityType) {
        if (entityType == EntityType.ZOMBIE) return Component.translatable("adaptive_nemesis.command.summon.name_zombie");
        if (entityType == EntityType.SKELETON) return Component.translatable("adaptive_nemesis.command.summon.name_skeleton");
        if (entityType == EntityType.CREEPER) return Component.translatable("adaptive_nemesis.command.summon.name_creeper");
        if (entityType == EntityType.SPIDER) return Component.translatable("adaptive_nemesis.command.summon.name_spider");
        if (entityType == EntityType.WITCH) return Component.translatable("adaptive_nemesis.command.summon.name_witch");
        return Component.translatable("adaptive_nemesis.command.summon.name_unknown");
    }
}
