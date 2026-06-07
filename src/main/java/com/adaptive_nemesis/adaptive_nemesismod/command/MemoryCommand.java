package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem;
import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 宿敌记忆命令
 *
 * 命令: /an memory [玩家]
 * 功能: 查看玩家的宿敌记忆档案
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class MemoryCommand {

    /**
     * 注册记忆命令
     *
     * @return 命令构建器
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("memory")
            .executes(MemoryCommand::executeSelf)
            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                .executes(MemoryCommand::executeTarget));
    }

    /**
     * 执行查看自己的记忆
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

        return showMemory(source, player);
    }

    /**
     * 执行查看指定玩家的记忆
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeTarget(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
            return showMemory(source, target);
        } catch (Exception e) {
            source.sendFailure(Component.translatable("adaptive_nemesis.command.error.player_not_found"));

            return 0;
        }
    }

    /**
     * 显示宿敌记忆档案
     *
     * @param source 命令来源
     * @param player 目标玩家
     * @return 命令执行结果
     */
    private static int showMemory(CommandSourceStack source, ServerPlayer player) {
        NemesisProfile profile = NemesisMemorySystem.getInstance().getProfile(player.getUUID());

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.memory.title"
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.memory.player",
            player.getName().getString()
        ), false);

        if (profile == null) {
            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.memory.no_records"
            ), false);
            source.sendSuccess(() -> Component.translatable(
                "adaptive_nemesis.command.memory.footer"
            ), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.memory.stats",
            String.valueOf(profile.getTotalKills()),
            String.valueOf(profile.getTotalDeaths()),
            String.format("%.2f", profile.getKdaRatio())
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.memory.dominant_style",
            profile.getDominantStyle().getDisplayName()
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.memory.style_counts",
            String.valueOf(profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.MELEE)),
            String.valueOf(profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.RANGED)),
            String.valueOf(profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.MAGIC))
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.memory.most_used_weapon",
            profile.getMostUsedWeapon()
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.memory.most_killed",
            profile.getMostKilledEntity()
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.memory.common_death",
            profile.getMostCommonDeathSource()
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.memory.common_killer",
            profile.getMostCommonKiller()
        ), false);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.memory.footer"
        ), false);

        return 1;
    }
}
