package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * 重载命令
 *
 * 命令: /an reload
 * 功能: 重新加载模组数据和缓存
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class ReloadCommand {

    /**
     * 注册重载命令
     *
     * @return 命令构建器
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("reload")
            .executes(ReloadCommand::execute);
    }

    /**
     * 执行重载
     *
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.literal(
            "§6🔄 正在重新加载 Adaptive Nemesis..."
        ), true);

        // 清除玩家强度缓存
        PlayerStrengthEvaluator.getInstance().clearAllCache();

        // 记录日志
        AdaptiveNemesisMod.LOGGER.info("🔄 通过命令重新加载 Adaptive Nemesis");

        source.sendSuccess(() -> Component.literal(
            "§a✅ 重载完成！"
        ), true);

        source.sendSuccess(() -> Component.literal(
            "§e- 玩家强度缓存已清除"
        ), false);

        source.sendSuccess(() -> Component.literal(
            "§e- 配置将在下次读取时更新"
        ), false);

        return 1;
    }
}
