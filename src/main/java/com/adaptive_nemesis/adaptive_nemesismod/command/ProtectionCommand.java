package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.protection.NewbieProtectionHandler;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 新手保护命令
 *
 * 命令: /an protection [玩家] [get|enable|disable]
 * 功能: 查看和管理新手保护状态
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class ProtectionCommand {

    /**
     * 注册保护命令
     *
     * @return 命令构建器
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("protection")
            .then(Commands.literal("get")
                .executes(ProtectionCommand::executeGetSelf)
                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                    .executes(ProtectionCommand::executeGetTarget)))
            .then(Commands.literal("enable")
                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                    .executes(ProtectionCommand::executeEnable)))
            .then(Commands.literal("disable")
                .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                    .executes(ProtectionCommand::executeDisable)));
    }

    /**
     * 执行获取自己的保护状态
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeGetSelf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("❌ 此命令只能由玩家执行"));
            return 0;
        }

        return showProtectionStatus(source, player);
    }

    /**
     * 执行获取指定玩家的保护状态
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeGetTarget(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
            return showProtectionStatus(source, target);
        } catch (Exception e) {
            source.sendFailure(Component.literal("❌ 无法找到指定玩家"));
            return 0;
        }
    }

    /**
     * 显示保护状态
     *
     * @param source 命令来源
     * @param player 目标玩家
     * @return 命令执行结果
     */
    private static int showProtectionStatus(CommandSourceStack source, ServerPlayer player) {
        boolean isProtected = NewbieProtectionHandler.getInstance().isProtected(player.getUUID());
        double reduction = NewbieProtectionHandler.getInstance().getProtectionReduction(player.getUUID());

        source.sendSuccess(() -> Component.literal(
            "§6===== 新手保护状态 ====="
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§e玩家: §f" + player.getName().getString()
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§e保护状态: " + (isProtected ? "§a已启用" : "§c未启用")
        ), false);

        if (isProtected) {
            source.sendSuccess(() -> Component.literal(
                String.format("§e属性减免: §f%.0f%%", reduction * 100)
            ), false);
        }

        source.sendSuccess(() -> Component.literal(
            String.format("§e强度阈值: §f%.2f", Config.NEWBIE_STRENGTH_THRESHOLD.get())
        ), false);

        source.sendSuccess(() -> Component.literal(
            String.format("§e默认持续时间: §f%d分钟", Config.NEWBIE_PROTECTION_DURATION.get())
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§6========================="
        ), false);

        return 1;
    }

    /**
     * 执行启用保护
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeEnable(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");

            source.sendSuccess(() -> Component.literal(
                "§a已为玩家 §f" + target.getName().getString() + " §a启用新手保护"
            ), true);

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("❌ 无法找到指定玩家"));
            return 0;
        }
    }

    /**
     * 执行禁用保护
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeDisable(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
            NewbieProtectionHandler.getInstance().clearProtectionData(target.getUUID());

            source.sendSuccess(() -> Component.literal(
                "§c已为玩家 §f" + target.getName().getString() + " §c禁用新手保护"
            ), true);

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("❌ 无法找到指定玩家"));
            return 0;
        }
    }
}
