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

        source.sendSuccess(() -> Component.literal(
            "§6===== Adaptive Nemesis Help ====="
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§e基础命令/Basic Commands:"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an status [player] §7- 查看模组状态和玩家信息 §7[View mod status and player info]"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an strength [player] §7- 查看玩家详细强度评估 §7[View detailed player strength assessment]"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an difficulty [get|set <value>] §7- 查看/调整难度 §7[View/Adjust difficulty]"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an difficulty true_damage [get|set <true|false>] §7- 真实伤害设置 §7[True damage settings]"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an protection [player] [get|enable|disable] §7- 新手保护管理 §7[Newbie protection management]"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an memory [player] §7- 查看宿敌记忆档案 §7[View nemesis memory profile]"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an test [module] §7- 测试模组各模块运行状态 §7[Test mod module status]"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§7   可用模块/Available modules: all, player, enemy, damage, boss, float, memory, protection, compat, config"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an nemesis [type] §7- 召唤宿敌 §7[Summon nemesis] (zombie/skeleton/creeper/spider/witch/random)"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an reload §7- 重新加载模组数据 §7[Reload mod data]"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an help §7- 显示此帮助信息 §7[Show this help]"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§6=================================="
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§e提示/Tip: 所有命令需要OP权限(等级2) §7[All commands require OP permission (level 2)]"
        ), false);

        return 1;
    }
}
