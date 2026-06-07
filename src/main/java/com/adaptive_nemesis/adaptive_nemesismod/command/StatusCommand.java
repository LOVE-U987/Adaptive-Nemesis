package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.adaptive_nemesis.adaptive_nemesismod.enemy.AdaptiveFloatSystem;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 状态查看命令
 *
 * 命令: /an status [玩家]
 * 功能: 查看模组当前运行状态和玩家相关信息
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class StatusCommand {

    /**
     * 注册状态命令
     *
     * @return 命令构建器
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("status")
            .executes(StatusCommand::executeSelf) // /an status
            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                .executes(StatusCommand::executeTarget) // /an status <玩家>
            );
    }

    /**
     * 执行状态查看（查看自己）
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

        return showPlayerStatus(source, player);
    }

    /**
     * 执行状态查看（查看指定玩家）
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeTarget(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
            return showPlayerStatus(source, target);
        } catch (Exception e) {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.error.player_not_found"));
            return 0;
        }
    }

    /**
     * 显示玩家状态信息
     *
     * @param source 命令来源
     * @param player 目标玩家
     * @return 命令执行结果
     */
    private static int showPlayerStatus(CommandSourceStack source, ServerPlayer player) {
        var strengthData = PlayerStrengthEvaluator.getInstance().getPlayerStrength(player);
        double floatMultiplier = AdaptiveFloatSystem.getInstance().getFloatMultiplier(player.getUUID());

        // 发送状态信息
        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.status.title"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.status.player", player.getName().getString()
        ), false);

        if (strengthData != null) {
            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.status.total_strength", String.format("%.2f", strengthData.getTotalStrength())
            ), false);
            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.status.defense_damage",
                    String.format("%.2f", strengthData.getDefenseStrength()),
                    String.format("%.2f", strengthData.getDamageStrength())
            ), false);
            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.status.mod_breakdown",
                    String.format("%.2f", strengthData.getApotheosisStrength()),
                    String.format("%.2f", strengthData.getIronsSpellsStrength()),
                    String.format("%.2f", strengthData.getEpicFightStrength())
            ), false);
        } else {
            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.status.no_strength"
            ), false);
        }

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.status.float_multiplier", String.format("%.2f", floatMultiplier)
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.status.difficulty_base", String.format("%.2f", Config.DIFFICULTY_BASE_MULTIPLIER.get())
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.status.true_damage",
                Component.translatable(Config.ENABLE_TRUE_DAMAGE.get() ? "adaptive_nemesis.command.enabled" : "adaptive_nemesis.command.disabled")
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.status.boss_cap",
                Component.translatable(Config.ENABLE_BOSS_DAMAGE_CAP.get() ? "adaptive_nemesis.command.enabled" : "adaptive_nemesis.command.disabled")
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.status.newbie_protection",
                Component.translatable(Config.ENABLE_NEWBIE_PROTECTION.get() ? "adaptive_nemesis.command.enabled" : "adaptive_nemesis.command.disabled")
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.status.footer"
        ), false);

        return 1;
    }
}
