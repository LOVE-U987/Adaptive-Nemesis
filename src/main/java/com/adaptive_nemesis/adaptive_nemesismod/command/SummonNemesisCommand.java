package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisProfile;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * 宿敌召唤命令
 *
 * 命令: /an nemesis [zombie|skeleton|creeper|spider|witch]
 * 功能: 召唤一个针对玩家战斗风格的宿敌
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class SummonNemesisCommand {

    /**
     * 可用的宿敌类型
     */
    private static final String[] NEMESIS_TYPES = {
        "zombie", "skeleton", "creeper", "spider", "witch", "random"
    };

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
            source.sendFailure(Component.literal("❌ 此命令只能由玩家执行"));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        // 确定实体类型
        EntityType<? extends Mob> entityType = getEntityType(type);
        if (entityType == null) {
            source.sendFailure(Component.literal("§c❌ 未知的宿敌类型: " + type));
            return 0;
        }

        // 在玩家附近生成宿敌
        Mob nemesis = entityType.create(level);
        if (nemesis == null) {
            source.sendFailure(Component.literal("§c❌ 无法生成宿敌"));
            return 0;
        }

        // 设置位置（玩家前方）
        double offsetX = player.getLookAngle().x * 3;
        double offsetZ = player.getLookAngle().z * 3;
        nemesis.setPos(pos.getX() + offsetX, pos.getY(), pos.getZ() + offsetZ);

        // 应用宿敌加成
        applyNemesisBuffs(nemesis, player);

        // 添加到世界
        level.addFreshEntity(nemesis);

        // 发送通知
        String nemesisName = getNemesisName(entityType);
        sendNemesisNotification(player, nemesisName);

        // 发送命令反馈
        source.sendSuccess(() -> Component.literal(
            "§c⚔️ 宿敌 " + nemesisName + " 已降临！"
        ), true);

        AdaptiveNemesisMod.LOGGER.info(
            "玩家 {} 召唤了宿敌: {}",
            player.getName().getString(),
            nemesisName
        );

        return 1;
    }

    /**
     * 应用宿敌加成
     *
     * @param nemesis 宿敌实体
     * @param player 目标玩家
     */
    private static void applyNemesisBuffs(Mob nemesis, ServerPlayer player) {
        NemesisProfile profile = NemesisMemorySystem.getInstance().getProfile(player.getUUID());

        if (profile == null) {
            // 如果没有档案，使用基础加成
            applyBasicBuffs(nemesis);
            return;
        }

        // 应用宿敌档案中的加成 - 大幅提升基础倍率
        double healthBonus = profile.getHealthBonus();
        double attackBonus = profile.getAttackBonus();
        double speedBonus = profile.getSpeedBonus();

        // 血量加成 - 基础3倍 + 档案加成
        var healthAttr = nemesis.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            double newHealth = healthAttr.getBaseValue() * (3.0 + healthBonus * 5.0);
            
            // 应用随机分布
            if (Config.ENABLE_RANDOM_DISTRIBUTION.get()) {
                double minFactor = Config.RANDOM_MIN_FACTOR.get();
                double maxFactor = Config.RANDOM_MAX_FACTOR.get();
                newHealth = newHealth * (minFactor + (maxFactor - minFactor) * Math.random());
            }
            
            healthAttr.setBaseValue(newHealth);
            nemesis.setHealth((float) newHealth);
        }

        // 攻击加成 - 基础2.5倍 + 档案加成
        var damageAttr = nemesis.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() * (2.5 + attackBonus * 3.0));
        }

        // 速度加成 - 基础1.2倍 + 档案加成
        var speedAttr = nemesis.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * (1.2 + speedBonus));
        }

        // 基础属性加成 - 所有宿敌都获得
        // 增加护甲
        var armorAttr = nemesis.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(armorAttr.getBaseValue() + 8.0);
        }

        // 增加护甲韧性
        var toughnessAttr = nemesis.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughnessAttr != null) {
            toughnessAttr.setBaseValue(toughnessAttr.getBaseValue() + 4.0);
        }

        // 增加攻击速度
        var attackSpeedAttr = nemesis.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            attackSpeedAttr.setBaseValue(attackSpeedAttr.getBaseValue() * 1.8);
        }

        // 增加击退抗性
        var knockbackAttr = nemesis.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(0.6);
        }

        // 根据玩家战斗风格添加特殊抗性
        switch (profile.getDominantStyle()) {
            case MELEE -> {
                // 近战玩家 -> 宿敌获得大量近战抗性（护甲+韧性）
                if (armorAttr != null) {
                    armorAttr.setBaseValue(armorAttr.getBaseValue() + profile.getMeleeResistanceBonus() * 30);
                }
                if (toughnessAttr != null) {
                    toughnessAttr.setBaseValue(toughnessAttr.getBaseValue() + profile.getMeleeResistanceBonus() * 15);
                }
            }
            case RANGED -> {
                // 远程玩家 -> 宿敌获得极高速度（更容易接近）+ 击退抗性
                if (speedAttr != null) {
                    speedAttr.setBaseValue(speedAttr.getBaseValue() * (1.0 + profile.getRangedResistanceBonus() * 2.0));
                }
                if (knockbackAttr != null) {
                    knockbackAttr.setBaseValue(1.0); // 完全免疫击退
                }
            }
            case MAGIC -> {
                // 法师玩家 -> 宿敌获得巨额血量 + 生命恢复
                if (healthAttr != null) {
                    healthAttr.setBaseValue(healthAttr.getBaseValue() * (1.0 + profile.getMagicResistanceBonus() * 2.0));
                }
            }
        }

        // 设置自定义名称
        nemesis.setCustomName(Component.literal(
            "§c☠ 宿敌 " + nemesis.getName().getString()
        ));
        nemesis.setCustomNameVisible(true);
    }

    /**
     * 应用基础加成（无档案时）
     *
     * @param nemesis 宿敌实体
     */
    private static void applyBasicBuffs(Mob nemesis) {
        var healthAttr = nemesis.getAttribute(Attributes.MAX_HEALTH);
        double newHealth = 0;
        if (healthAttr != null) {
            // 大幅提升血量 - 基础5倍，加上随机波动
            newHealth = healthAttr.getBaseValue() * 5.0;

            // 应用随机分布
            if (Config.ENABLE_RANDOM_DISTRIBUTION.get()) {
                double minFactor = Config.RANDOM_MIN_FACTOR.get();
                double maxFactor = Config.RANDOM_MAX_FACTOR.get();
                newHealth = newHealth * (minFactor + (maxFactor - minFactor) * Math.random());
            }

            healthAttr.setBaseValue(newHealth);
            nemesis.setHealth((float) newHealth);
        }

        // 大幅提升攻击力 - 3倍基础攻击
        var damageAttr = nemesis.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() * 3.0);
        }

        // 增加护甲 - 让宿敌更耐打
        var armorAttr = nemesis.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(armorAttr.getBaseValue() + 10.0);
        }

        // 增加护甲韧性
        var toughnessAttr = nemesis.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughnessAttr != null) {
            toughnessAttr.setBaseValue(toughnessAttr.getBaseValue() + 5.0);
        }

        // 增加攻击速度 - 让宿敌攻击更频繁
        var attackSpeedAttr = nemesis.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeedAttr != null) {
            attackSpeedAttr.setBaseValue(attackSpeedAttr.getBaseValue() * 2.0);
        }

        // 增加击退抗性 - 防止被无限击退
        var knockbackAttr = nemesis.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackAttr != null) {
            knockbackAttr.setBaseValue(0.8);
        }

        nemesis.setCustomName(Component.literal(
            "§c☠ 宿敌 " + nemesis.getName().getString()
        ));
        nemesis.setCustomNameVisible(true);
    }

    /**
     * 发送宿敌通知
     *
     * @param player 目标玩家
     * @param nemesisName 宿敌名称
     */
    private static void sendNemesisNotification(ServerPlayer player, String nemesisName) {
        // 发送标题
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
            Component.literal("§c⚔️ 宿敌降临！")
        ));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
            Component.literal("§e" + nemesisName + " 已响应你的召唤")
        ));

        // 发送聊天消息
        player.sendSystemMessage(Component.literal(
            "§4═══════════════════════════════"
        ));
        player.sendSystemMessage(Component.literal(
            "§c⚔️ 宿敌 " + nemesisName + " 已降临！"
        ));
        player.sendSystemMessage(Component.literal(
            "§e这个敌人针对你的战斗风格进行了强化！"
        ));
        player.sendSystemMessage(Component.literal(
            "§7小心应对，它会记住你的每一次攻击..."
        ));
        player.sendSystemMessage(Component.literal(
            "§4═══════════════════════════════"
        ));
    }

    /**
     * 获取实体类型
     *
     * @param type 类型字符串
     * @return 实体类型
     */
    @SuppressWarnings("unchecked")
    private static EntityType<? extends Mob> getEntityType(String type) {
        if (type.equals("random")) {
            // 随机选择
            java.util.Random random = new java.util.Random();
            String[] types = {"zombie", "skeleton", "creeper", "spider", "witch"};
            type = types[random.nextInt(types.length)];
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
     * 获取宿敌名称
     *
     * @param entityType 实体类型
     * @return 宿敌名称
     */
    private static String getNemesisName(EntityType<? extends Mob> entityType) {
        if (entityType == EntityType.ZOMBIE) return "僵尸宿敌";
        if (entityType == EntityType.SKELETON) return "骷髅宿敌";
        if (entityType == EntityType.CREEPER) return "苦力怕宿敌";
        if (entityType == EntityType.SPIDER) return "蜘蛛宿敌";
        if (entityType == EntityType.WITCH) return "女巫宿敌";
        return "未知宿敌";
    }
}
