package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 强度评估命令
 *
 * 命令: /an strength [玩家]
 * 功能: 查看玩家的详细强度评估数据
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class StrengthCommand {

    /**
     * 注册强度命令
     *
     * @return 命令构建器
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("strength")
            .executes(StrengthCommand::executeSelf)
            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                .executes(StrengthCommand::executeTarget)
            );
    }

    /**
     * 执行强度查看（自己）
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeSelf(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.error.player_only"));
            return 0;
        }

        return showStrengthDetails(source, player);
    }

    /**
     * 执行强度查看（指定玩家）
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeTarget(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
            return showStrengthDetails(source, target);
        } catch (Exception e) {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.error.player_not_found"));
            return 0;
        }
    }

    /**
     * 显示玩家强度详情
     *
     * @param source 命令来源
     * @param player 目标玩家
     * @return 命令执行结果
     */
    private static int showStrengthDetails(CommandSourceStack source, ServerPlayer player) {
        // 强制重新计算强度
        var strengthData = PlayerStrengthEvaluator.getInstance().updatePlayerStrength(player);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.strength.title"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.strength.player",
            player.getName().getString()
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.strength.total",
            String.format("%.2f", strengthData.getTotalStrength())
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.strength.breakdown_title"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.strength.defense",
            String.format("%.2f", strengthData.getDefenseStrength())
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.strength.damage",
            String.format("%.2f", strengthData.getDamageStrength())
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.strength.apotheosis",
            String.format("%.2f", strengthData.getApotheosisStrength())
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.strength.irons_spells",
            String.format("%.2f", strengthData.getIronsSpellsStrength())
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.strength.epic_fight",
            String.format("%.2f", strengthData.getEpicFightStrength())
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.strength.footer"
        ), false);

        return 1;
    }
}
