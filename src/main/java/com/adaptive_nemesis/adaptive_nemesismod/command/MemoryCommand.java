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
            source.sendFailure(Component.literal("❌ 此命令只能由玩家执行"));
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
            source.sendFailure(Component.literal("❌ 无法找到指定玩家"));
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

        source.sendSuccess(() -> Component.literal(
            "§5===== 宿敌记忆档案 ====="
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§e玩家: §f" + player.getName().getString()
        ), false);

        if (profile == null) {
            source.sendSuccess(() -> Component.literal(
                "§7暂无战斗记录"
            ), false);
            source.sendSuccess(() -> Component.literal(
                "§5========================"
            ), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(
            String.format("§e总击杀: §f%d §e| 总死亡: §f%d §e| KDA: §f%.2f",
                profile.getTotalKills(), profile.getTotalDeaths(), profile.getKdaRatio())
        ), false);

        source.sendSuccess(() -> Component.literal(
            String.format("§e主要战斗风格: §f%s", profile.getDominantStyle().getDisplayName())
        ), false);

        source.sendSuccess(() -> Component.literal(
            String.format("§e近战击杀: §f%d §e| 远程击杀: §f%d §e| 法术击杀: §f%d",
                profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.MELEE),
                profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.RANGED),
                profile.getKillStyleCount(NemesisMemorySystem.CombatStyle.MAGIC))
        ), false);

        source.sendSuccess(() -> Component.literal(
            String.format("§e常用武器: §f%s", profile.getMostUsedWeapon())
        ), false);

        source.sendSuccess(() -> Component.literal(
            String.format("§e击杀最多: §f%s", profile.getMostKilledEntity())
        ), false);

        source.sendSuccess(() -> Component.literal(
            String.format("§e常见死因: §f%s", profile.getMostCommonDeathSource())
        ), false);

        source.sendSuccess(() -> Component.literal(
            String.format("§e常见杀手: §f%s", profile.getMostCommonKiller())
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§5========================"
        ), false);

        return 1;
    }
}
