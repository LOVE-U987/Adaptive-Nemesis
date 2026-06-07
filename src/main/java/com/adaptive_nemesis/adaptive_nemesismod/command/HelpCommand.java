package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * 帮助命令
 *
 * 命令: /an help
 * 功能: 显示所有可用命令的帮助信息
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class HelpCommand {

    /**
     * 注册帮助命令
     *
     * @return 命令构建器
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("help")
            .executes(HelpCommand::execute);
    }

    /**
     * 执行帮助
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.title"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.basic"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.status"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.strength"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.difficulty"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.true_damage"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.protection"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.memory"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.test"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.test_modules"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.nemesis"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.reload"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.help"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.footer"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.help.tip"
        ), false);

        return 1;
    }
}
