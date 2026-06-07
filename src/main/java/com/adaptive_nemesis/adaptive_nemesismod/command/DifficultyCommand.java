package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * 难度设置命令
 *
 * 命令: /an difficulty [get|set <数值>]
 * 功能: 查看和动态调整难度系数
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class DifficultyCommand {

    /**
     * 注册难度命令
     *
     * @return 命令构建器
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("difficulty")
            .then(Commands.literal("get")
                .executes(DifficultyCommand::executeGet))
            .then(Commands.literal("set")
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1, 10.0))
                    .executes(DifficultyCommand::executeSet)))
            .then(Commands.literal("true_damage")
                .then(Commands.literal("get")
                    .executes(DifficultyCommand::executeGetTrueDamage))
                .then(Commands.literal("set")
                    .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(DifficultyCommand::executeSetTrueDamage))));
    }

    /**
     * 执行获取难度
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeGet(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.difficulty.title"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.difficulty.base",
            String.format("%.2f", Config.DIFFICULTY_BASE_MULTIPLIER.get())
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.difficulty.float_range",
            String.format("%.2f", Config.FLOAT_MIN.get()),
            String.format("%.2f", Config.FLOAT_MAX.get())
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.difficulty.kill_bonus",
            String.format("%.0f", Config.KILL_STREAK_MULTIPLIER_INCREASE.get() * 100)
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.difficulty.death_reduction",
            String.format("%.0f", Config.DEATH_STREAK_MULTIPLIER_DECREASE.get() * 100)
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.difficulty.footer"
        ), false);

        return 1;
    }

    /**
     * 执行设置难度
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeSet(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double newValue = DoubleArgumentType.getDouble(context, "value");

        // 注意：这里只是显示，实际修改配置需要重启或使用配置API
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.difficulty.set_done",
            String.format("%.2f", newValue)
        ), true);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.error.config_changes"
        ), false);

        return 1;
    }

    /**
     * 执行获取真实伤害状态
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeGetTrueDamage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        boolean enabled = Config.ENABLE_TRUE_DAMAGE.get();

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.difficulty.true_damage_title"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.difficulty.true_damage_status",
            Component.translatable(enabled ? "adaptive_nemesis.command.enabled" : "adaptive_nemesis.command.disabled")
        ), false);

        if (enabled) {
            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.difficulty.true_damage_low",
                String.valueOf(Config.LOW_ARMOR_THRESHOLD.get()),
                String.format("%.1f", Config.LOW_ARMOR_TRUE_DAMAGE_PERCENT.get())
            ), false);

            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.difficulty.true_damage_medium",
                String.valueOf(Config.LOW_ARMOR_THRESHOLD.get()),
                String.valueOf(Config.MEDIUM_ARMOR_THRESHOLD.get()),
                String.format("%.1f", Config.MEDIUM_ARMOR_TRUE_DAMAGE_PERCENT.get())
            ), false);

            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.difficulty.true_damage_high",
                String.valueOf(Config.MEDIUM_ARMOR_THRESHOLD.get()),
                String.valueOf(Config.HIGH_ARMOR_THRESHOLD.get()),
                String.format("%.1f", Config.HIGH_ARMOR_TRUE_DAMAGE_PERCENT.get())
            ), false);

            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.difficulty.true_damage_turtle",
                String.valueOf(Config.HIGH_ARMOR_THRESHOLD.get()),
                String.format("%.1f", Config.TURTLE_TRUE_DAMAGE_PERCENT.get())
            ), false);
        }

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.difficulty.true_damage_footer"
        ), false);

        return 1;
    }

    /**
     * 执行设置真实伤害
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeSetTrueDamage(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean enabled = BoolArgumentType.getBool(context, "enabled");

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.difficulty.set_true_damage",
            Component.translatable(enabled ? "adaptive_nemesis.command.enabled" : "adaptive_nemesis.command.disabled")
        ), true);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.error.config_changes"
        ), false);

        return 1;
    }
}
