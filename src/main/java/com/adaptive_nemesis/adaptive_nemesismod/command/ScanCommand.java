package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.EnemyScalingHandler;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
 * 输出格式：不再使用服务端拼接的翻译字符串与 § 格式码，
 * 全部通过 Component + ChatFormatting 构造，保留客户端本地化
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
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
     * @return 成功返回 1，执行者非玩家返回 0
     */
    private static int executeScan(CommandContext<CommandSourceStack> context, double range) {
        CommandSourceStack source = context.getSource();

        // 确保执行者是玩家
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.scan.player_only"));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        List<Mob> enemies = findNearbyEnemies(level, player.position(), range);

        if (enemies.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.scan.no_enemies", String.valueOf(range)
            ), false);
            return 1;
        }

        EnemyScalingHandler handler = EnemyScalingHandler.getInstance();
        sendScanHeader(source, range, enemies.size());

        int scaledCount = scanEnemies(source, enemies, handler);
        sendScanSummary(source, enemies.size(), scaledCount);

        return 1;
    }

    /**
     * 搜索玩家周围范围内的存活敌对生物
     *
     * @param level 服务端世界
     * @param playerPos 玩家位置
     * @param range 搜索范围（格）
     * @return 范围内的敌对生物列表
     */
    private static List<Mob> findNearbyEnemies(ServerLevel level, Vec3 playerPos, double range) {
        AABB searchBox = new AABB(
            playerPos.x - range, playerPos.y - range, playerPos.z - range,
            playerPos.x + range, playerPos.y + range, playerPos.z + range
        );
        return level.getEntitiesOfClass(
            Mob.class,
            searchBox,
            entity -> entity instanceof Enemy && entity.isAlive()
        );
    }

    /**
     * 发送扫描标题与统计头
     *
     * @param source 命令源
     * @param range 扫描范围
     * @param enemyCount 找到的敌对生物数量
     */
    private static void sendScanHeader(CommandSourceStack source, double range, int enemyCount) {
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.scan.title"
        ), false);
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.scan.info", String.valueOf(range), String.valueOf(enemyCount)
        ), false);
    }

    /**
     * 逐个扫描并输出怪物强化信息
     *
     * @param source 命令源
     * @param enemies 敌对生物列表
     * @param handler 敌人缩放处理器
     * @return 已强化的数量
     */
    private static int scanEnemies(CommandSourceStack source, List<Mob> enemies, EnemyScalingHandler handler) {
        int scaledCount = 0;
        for (Mob mob : enemies) {
            boolean isScaled = handler.isScaled(mob);
            if (isScaled) {
                scaledCount++;
            }
            sendMobInfo(source, mob, isScaled, handler.getScaleMultiplier(mob));
        }
        return scaledCount;
    }

    /**
     * 发送扫描统计信息
     *
     * @param source 命令源
     * @param totalEnemies 敌对生物总数
     * @param scaledCount 已强化的数量
     */
    private static void sendScanSummary(CommandSourceStack source, int totalEnemies, int scaledCount) {
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.scan.summary_title"
        ), false);
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.scan.summary",
            String.valueOf(totalEnemies), String.valueOf(scaledCount),
            String.valueOf(totalEnemies - scaledCount)
        ), false);
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
        // 基础信息行：名称（黄） + 状态 + 倍率（黄）
        MutableComponent nameLine = Component.literal(mob.getName().getString())
            .withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" "))
            .append(Component.translatable(isScaled
                ? "adaptive_nemesis.command.scan.status_scaled"
                : "adaptive_nemesis.command.scan.status_unscaled").withStyle(ChatFormatting.YELLOW));
        if (isScaled) {
            nameLine.append(Component.literal(" "
                + Component.translatable("adaptive_nemesis.command.scan.multiplier_prefix").getString()
                + DF.format(multiplier) + "x").withStyle(ChatFormatting.YELLOW));
        }
        source.sendSuccess(() -> nameLine, false);

        if (isScaled) {
            sendAttributeDetails(source, mob, multiplier);
        }
    }

    /**
     * 发送已强化怪物的详细属性对比
     *
     * @param source 命令源
     * @param mob 目标怪物
     * @param multiplier 强化倍率
     */
    private static void sendAttributeDetails(CommandSourceStack source, Mob mob, double multiplier) {
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

        double currentAttackSpeed = attackSpeedAttr != null ? attackSpeedAttr.getValue() : 0;
        // 攻击速度的基础值通常是 0（怪物没有基础攻击速度属性），所以显示当前值即可
        double originalAttackSpeed = attackSpeedAttr != null && attackSpeedAttr.getBaseValue() > 0
            ? currentAttackSpeed / multiplier : 0;

        double currentToughness = toughnessAttr != null ? toughnessAttr.getValue() : 0;
        // 韧性的基础值通常也是 0
        double originalToughness = toughnessAttr != null && toughnessAttr.getBaseValue() > 0
            ? currentToughness / multiplier : 0;

        // 发送详细属性对比
        sendAttributeLine(source, "adaptive_nemesis.command.scan.attr_hp", originalHealth, currentHealth, true);
        sendAttributeLine(source, "adaptive_nemesis.command.scan.attr_damage", originalDamage, currentDamage, true);
        sendAttributeLine(source, "adaptive_nemesis.command.scan.attr_armor", originalArmor, currentArmor, true);
        sendSpeedLine(source, currentSpeed);
        sendAttributeLine(source, "adaptive_nemesis.command.scan.attr_attack_speed",
            originalAttackSpeed, currentAttackSpeed, false);
        sendAttributeLine(source, "adaptive_nemesis.command.scan.attr_toughness",
            originalToughness, currentToughness, false);

        // 显示当前生命值
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.scan.current_hp", DF.format(mob.getHealth()), DF.format(currentHealth)
        ), false);
    }

    /**
     * 发送移动速度行（速度不做倍率估算，仅展示当前值）
     *
     * @param source 命令源
     * @param currentSpeed 当前移速
     */
    private static void sendSpeedLine(CommandSourceStack source, double currentSpeed) {
        MutableComponent speedLine = Component.literal("  ")
            .append(Component.translatable("adaptive_nemesis.command.scan.attr_speed")
                .withStyle(ChatFormatting.GRAY))
            .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(DF.format(currentSpeed)).withStyle(ChatFormatting.WHITE));
        if (Config.FIX_SPEED_BONUS_TO_ZERO.get()) {
            speedLine.append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("adaptive_nemesis.command.scan.speed_fixed")
                    .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(")").withStyle(ChatFormatting.GRAY));
        }
        source.sendSuccess(() -> speedLine, false);
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
    private static void sendAttributeLine(CommandSourceStack source, String attrKey,
                                          double original, double current, boolean showPercent) {
        if (original <= 0 && current <= 0) {
            source.sendSuccess(() -> Component.translatable("adaptive_nemesis.command.scan.attr_none",
                Component.translatable(attrKey)), false);
        } else if (original <= 0) {
            source.sendSuccess(() -> Component.translatable("adaptive_nemesis.command.scan.attr_new",
                Component.translatable(attrKey), DF.format(current)), false);
        } else {
            final MutableComponent percentPart = buildPercentPart(original, current, showPercent);
            source.sendSuccess(() -> Component.translatable("adaptive_nemesis.command.scan.attr_scaled",
                Component.translatable(attrKey), DF.format(original), DF.format(current), percentPart), false);
        }
    }

    /**
     * 构造属性对比行的百分比部分
     *
     * @param original 原始值
     * @param current 当前值
     * @param showPercent 是否显示百分比
     * @return 百分比样式组件（不显示时为空串组件）
     */
    private static MutableComponent buildPercentPart(double original, double current, boolean showPercent) {
        if (!showPercent) {
            return Component.literal("");
        }
        double percent = (current / original - 1) * 100;
        String sign = percent >= 0 ? "+" : "";
        return Component.literal(" (").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(sign + DF.format(percent) + "%")
                .withStyle(ChatFormatting.WHITE))
            .append(Component.literal(")").withStyle(ChatFormatting.GRAY));
    }
}
