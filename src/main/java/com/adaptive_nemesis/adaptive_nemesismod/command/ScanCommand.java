package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EnemyScalingHandler;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.text.DecimalFormat;
import java.util.List;

/**
 * 扫描周围敌人指令
 *
 * 显示玩家周围所有敌对生物的强化数据：
 * - 是否已被强化
 * - 强化倍率
 * - 血量（加成前/后）
 * - 攻击力（加成前/后）
 * - 护甲（加成前/后）
 * - 移动速度
 * - 攻击速度
 * - 护甲韧性
 *
 * 用法: /an scan [范围]
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class ScanCommand {

    /**
     * 默认扫描范围（格数）
     */
    private static final double DEFAULT_RANGE = 32.0;

    /**
     * 数值格式化器
     */
    private static final DecimalFormat DF = new DecimalFormat("0.00");

    /**
     * 注册扫描命令
     *
     * @return 命令构建器
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("scan")
            .executes(context -> executeScan(context, DEFAULT_RANGE))
            .then(Commands.argument("range", DoubleArgumentType.doubleArg(1.0, 128.0))
                .executes(context -> executeScan(
                    context,
                    DoubleArgumentType.getDouble(context, "range")
                ))
            );
    }

    /**
     * 执行扫描命令
     *
     * @param context 命令上下文
     * @param range 扫描范围
     * @return 命令执行结果
     */
    private static int executeScan(CommandContext<CommandSourceStack> context, double range) {
        CommandSourceStack source = context.getSource();

        // 确保执行者是玩家
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.scan.player_only"));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        Vec3 playerPos = player.position();

        // 创建搜索范围
        AABB searchBox = new AABB(
            playerPos.x - range, playerPos.y - range, playerPos.z - range,
            playerPos.x + range, playerPos.y + range, playerPos.z + range
        );

        // 获取范围内的敌对生物
        List<Mob> enemies = level.getEntitiesOfClass(
            Mob.class,
            searchBox,
            entity -> entity instanceof Enemy && entity.isAlive()
        );

        if (enemies.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.scan.no_enemies", String.valueOf(range)
            ), false);
            return 1;
        }

        EnemyScalingHandler handler = EnemyScalingHandler.getInstance();

        // 发送标题
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.scan.title"
        ), false);
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.scan.info", String.valueOf(range), String.valueOf(enemies.size())
        ), false);

        final int[] scaledCount = {0};
        for (Mob mob : enemies) {
            boolean isScaled = handler.isScaled(mob);
            if (isScaled) {
                scaledCount[0]++;
            }

            // 发送怪物信息
            sendMobInfo(source, mob, isScaled, handler.getScaleMultiplier(mob));
        }

        // 发送统计信息
        final int totalEnemies = enemies.size();
        final int finalScaledCount = scaledCount[0];
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.scan.summary_title"
        ), false);
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.scan.summary",
            String.valueOf(totalEnemies), String.valueOf(finalScaledCount), String.valueOf(totalEnemies - finalScaledCount)
        ), false);

        return enemies.size();
    }

    /**
     * 发送单个怪物的信息
     *
     * @param source 命令源
     * @param mob 目标怪物
     * @param isScaled 是否已强化
     * @param multiplier 强化倍率
     */
    private static void sendMobInfo(CommandSourceStack source, Mob mob, boolean isScaled, double multiplier) {
        String name = mob.getName().getString();
        String status = isScaled ? 
            Component.translatable("adaptive_nemesis.command.scan.status_scaled").getString() : 
            Component.translatable("adaptive_nemesis.command.scan.status_unscaled").getString();

        // 基础信息行
        source.sendSuccess(() -> Component.literal(
            "§e" + name + " " + status +
            (isScaled ? " " + Component.translatable("adaptive_nemesis.command.scan.multiplier_prefix").getString() + DF.format(multiplier) + "x" : "")
        ), false);

        if (!isScaled) {
            return;
        }

        // 获取各项属性
        AttributeInstance healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance damageAttr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance armorAttr = mob.getAttribute(Attributes.ARMOR);
        AttributeInstance speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance attackSpeedAttr = mob.getAttribute(Attributes.ATTACK_SPEED);
        AttributeInstance toughnessAttr = mob.getAttribute(Attributes.ARMOR_TOUGHNESS);

        // 计算原始值（通过当前值除以倍率来估算）
        // 注意：这只是一个估算，因为随机分布和上限会影响实际值
        double currentHealth = healthAttr != null ? healthAttr.getValue() : 0;
        double originalHealth = healthAttr != null ? currentHealth / multiplier : 0;

        double currentDamage = damageAttr != null ? damageAttr.getValue() : 0;
        double originalDamage = damageAttr != null ? currentDamage / multiplier : 0;

        double currentArmor = armorAttr != null ? armorAttr.getValue() : 0;
        double originalArmor = armorAttr != null ? currentArmor / multiplier : 0;

        double currentSpeed = speedAttr != null ? speedAttr.getValue() : 0;
        double originalSpeed = speedAttr != null ? currentSpeed : 0;

        double currentAttackSpeed = attackSpeedAttr != null ? attackSpeedAttr.getValue() : 0;
        // 攻击速度的基础值通常是 0（怪物没有基础攻击速度属性），所以显示当前值即可
        double originalAttackSpeed = attackSpeedAttr != null && attackSpeedAttr.getBaseValue() > 0 ? currentAttackSpeed / multiplier : 0;

        double currentToughness = toughnessAttr != null ? toughnessAttr.getValue() : 0;
        // 韧性的基础值通常也是 0
        double originalToughness = toughnessAttr != null && toughnessAttr.getBaseValue() > 0 ? currentToughness / multiplier : 0;

        // 发送详细属性对比
        sendAttributeLine(source, "adaptive_nemesis.command.scan.attr_hp", originalHealth, currentHealth, true);
        sendAttributeLine(source, "adaptive_nemesis.command.scan.attr_damage", originalDamage, currentDamage, true);
        sendAttributeLine(source, "adaptive_nemesis.command.scan.attr_armor", originalArmor, currentArmor, true);

        // 移速特殊处理
        source.sendSuccess(() -> {
            String speedText = "  §7" + Component.translatable("adaptive_nemesis.command.scan.attr_speed").getString() + ": §f" + DF.format(currentSpeed);
            if (Config.FIX_SPEED_BONUS_TO_ZERO.get()) {
                speedText += " §7(§e" + Component.translatable("adaptive_nemesis.command.scan.speed_fixed").getString() + "§7)";
            }
            return Component.literal(speedText);
        }, false);

        // 攻速和韧性可能为0，特殊显示
        sendAttributeLine(source, "adaptive_nemesis.command.scan.attr_attack_speed", originalAttackSpeed, currentAttackSpeed, false);
        sendAttributeLine(source, "adaptive_nemesis.command.scan.attr_toughness", originalToughness, currentToughness, false);

        // 显示当前生命值
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.scan.current_hp", DF.format(mob.getHealth()), DF.format(currentHealth)
        ), false);
    }

    /**
     * 发送属性对比行
     *
     * @param source 命令源
     * @param attrKey 属性翻译键
     * @param original 原始值
     * @param current 当前值
     * @param showPercent 是否显示百分比
     */
    private static void sendAttributeLine(CommandSourceStack source, String attrKey, double original, double current, boolean showPercent) {
        if (original <= 0 && current <= 0) {
            source.sendSuccess(() -> Component.translatable("adaptive_nemesis.command.scan.attr_none",
                Component.translatable(attrKey)), false);
        } else if (original <= 0) {
            source.sendSuccess(() -> Component.translatable("adaptive_nemesis.command.scan.attr_new",
                Component.translatable(attrKey), DF.format(current)), false);
        } else {
            final String percentStr;
            if (showPercent) {
                double percent = (current / original - 1) * 100;
                percentStr = " §7(§f" + (percent >= 0 ? "+" : "") + DF.format(percent) + "%§7)";
            } else {
                percentStr = "";
            }
            source.sendSuccess(() -> Component.translatable("adaptive_nemesis.command.scan.attr_scaled",
                Component.translatable(attrKey), DF.format(original), DF.format(current), percentStr), false);
        }
    }
}
