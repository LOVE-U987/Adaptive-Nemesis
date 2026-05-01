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
            "§6===== Adaptive Nemesis 帮助 ====="
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§e基础命令:"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an status [玩家] §7- 查看模组状态和玩家信息"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an strength [玩家] §7- 查看玩家详细强度评估"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an difficulty [get|set <数值>] §7- 查看/调整难度"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an difficulty true_damage [get|set <true|false>] §7- 真实伤害设置"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an protection [玩家] [get|enable|disable] §7- 新手保护管理"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an memory [玩家] §7- 查看宿敌记忆档案"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an test [模块] §7- 测试模组各模块运行状态"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§7   可用模块: all, player, enemy, damage, boss, float, memory, protection, compat, config"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an nemesis [类型] §7- 召唤宿敌 (zombie/skeleton/creeper/spider/witch/random)"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an reload §7- 重新加载模组数据"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§f/an help §7- 显示此帮助信息"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§6=================================="
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§e提示: 所有命令需要OP权限（等级2）"
        ), false);

        return 1;
    }
}
