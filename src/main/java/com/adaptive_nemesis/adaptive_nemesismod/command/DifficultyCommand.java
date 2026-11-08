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
     * 难度倍率允许的最小值，与 GeneralConfig 中 DIFFICULTY_BASE_MULTIPLIER 的下限一致
     */
    private static final double MIN_DIFFICULTY = 0.1;

    /**
     * 难度倍率允许的最大值，与 GeneralConfig 中 DIFFICULTY_BASE_MULTIPLIER 的上限一致
     */
    private static final double MAX_DIFFICULTY = 20.0;

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
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(MIN_DIFFICULTY, MAX_DIFFICULTY))
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

        // 直接写入内存配置并落盘，立即对缩放管线生效
        Config.DIFFICULTY_BASE_MULTIPLIER.set(newValue);
        Config.saveToFile();

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.difficulty.set_done",
            String.format("%.2f", newValue)
        ), true);

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

        // 直接写入内存配置并落盘，立即对真实伤害转化链路生效
        Config.ENABLE_TRUE_DAMAGE.set(enabled);
        Config.saveToFile();

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.difficulty.set_true_damage",
            Component.translatable(enabled ? "adaptive_nemesis.command.enabled" : "adaptive_nemesis.command.disabled")
        ), true);

        return 1;
    }
}
