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
            source.sendFailure(Component.literal("❌ 此命令只能由玩家执行"));
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
            source.sendFailure(Component.literal("❌ 无法找到指定玩家"));
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
        source.sendSuccess(() -> Component.literal(
            "§6===== Adaptive Nemesis 状态 ====="
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§e玩家: §f" + player.getName().getString()
        ), false);

        if (strengthData != null) {
            source.sendSuccess(() -> Component.literal(
                String.format("§e综合强度: §f%.2f", strengthData.getTotalStrength())
            ), false);
            source.sendSuccess(() -> Component.literal(
                String.format("§e  └ 防御: §f%.2f §e| 输出: §f%.2f", 
                    strengthData.getDefenseStrength(), strengthData.getDamageStrength())
            ), false);
            source.sendSuccess(() -> Component.literal(
                String.format("§e  └ 神话: §f%.2f §e| 铁魔法: §f%.2f §e| 史诗: §f%.2f",
                    strengthData.getApotheosisStrength(), 
                    strengthData.getIronsSpellsStrength(),
                    strengthData.getEpicFightStrength())
            ), false);
        } else {
            source.sendSuccess(() -> Component.literal(
                "§e综合强度: §7尚未计算"
            ), false);
        }

        source.sendSuccess(() -> Component.literal(
            String.format("§e浮动倍率: §f%.2f", floatMultiplier)
        ), false);

        source.sendSuccess(() -> Component.literal(
            String.format("§e难度基准: §f%.2f", Config.DIFFICULTY_BASE_MULTIPLIER.get())
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§e真实伤害: " + (Config.ENABLE_TRUE_DAMAGE.get() ? "§a已启用" : "§c已禁用")
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§eBoss上限: " + (Config.ENABLE_BOSS_DAMAGE_CAP.get() ? "§a已启用" : "§c已禁用")
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§e新手保护: " + (Config.ENABLE_NEWBIE_PROTECTION.get() ? "§a已启用" : "§c已禁用")
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§6================================"
        ), false);

        return 1;
    }
}
