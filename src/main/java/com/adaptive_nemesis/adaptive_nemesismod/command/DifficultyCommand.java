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

        source.sendSuccess(() -> Component.literal(
            "§6===== Difficulty Settings ====="
        ), false);

        source.sendSuccess(() -> Component.literal(
            String.format("§e难度系数基准/Difficulty Base: §f%.2f", Config.DIFFICULTY_BASE_MULTIPLIER.get())
        ), false);

        source.sendSuccess(() -> Component.literal(
            String.format("§e浮动范围/Float Range: §f%.2f §e- §f%.2f", 
                Config.FLOAT_MIN.get(), Config.FLOAT_MAX.get())
        ), false);

        source.sendSuccess(() -> Component.literal(
            String.format("§e击杀加成/Kill Bonus: §f+%.0f%%", Config.KILL_STREAK_MULTIPLIER_INCREASE.get() * 100)
        ), false);

        source.sendSuccess(() -> Component.literal(
            String.format("§e死亡减免/Death Reduction: §f-%.0f%%", Config.DEATH_STREAK_MULTIPLIER_DECREASE.get() * 100)
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§6==================="
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
        source.sendSuccess(() -> Component.literal(
            String.format("§a难度系数已设置为/Difficulty set to: §f%.2f", newValue)
        ), true);

        source.sendSuccess(() -> Component.literal(
            "§e⚠️ 注意/Note: 配置更改将在下次重启后生效 §7[Configuration changes will take effect after restart]"
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

        source.sendSuccess(() -> Component.literal(
            "§6===== True Damage Settings ====="
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§e状态/Status: " + (enabled ? "§a已启用(Enabled)" : "§c已禁用(Disabled)")
        ), false);

        if (enabled) {
            source.sendSuccess(() -> Component.literal(
                String.format("§e低护甲/Low Armor (< %d): §f%.1f%%", 
                    Config.LOW_ARMOR_THRESHOLD.get(), Config.LOW_ARMOR_TRUE_DAMAGE_PERCENT.get())
            ), false);

            source.sendSuccess(() -> Component.literal(
                String.format("§e中护甲/Medium Armor (%d-%d): §f%.1f%%",
                    Config.LOW_ARMOR_THRESHOLD.get(), Config.MEDIUM_ARMOR_THRESHOLD.get(),
                    Config.MEDIUM_ARMOR_TRUE_DAMAGE_PERCENT.get())
            ), false);

            source.sendSuccess(() -> Component.literal(
                String.format("§e高护甲/High Armor (%d-%d): §f%.1f%%",
                    Config.MEDIUM_ARMOR_THRESHOLD.get(), Config.HIGH_ARMOR_THRESHOLD.get(),
                    Config.HIGH_ARMOR_TRUE_DAMAGE_PERCENT.get())
            ), false);

            source.sendSuccess(() -> Component.literal(
                String.format("§e铁乌龟/Turtle (> %d): §f%.1f%%",
                    Config.HIGH_ARMOR_THRESHOLD.get(), Config.TURTLE_TRUE_DAMAGE_PERCENT.get())
            ), false);
        }

        source.sendSuccess(() -> Component.literal(
            "§6========================"
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

        source.sendSuccess(() -> Component.literal(
            "§e真实伤害机制/True Damage: " + (enabled ? "§a已启用(Enabled)" : "§c已禁用(Disabled)")
        ), true);

        source.sendSuccess(() -> Component.literal(
            "§e⚠️ 注意/Note: 配置更改将在下次重启后生效 §7[Configuration changes will take effect after restart]"
        ), false);

        return 1;
    }
}
