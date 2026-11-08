package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.player.PlayerStrengthEvaluator;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

/**
 * 重载命令
 *
 * 命令: /an reload
 * 功能: 清空运行时缓存并触发数据包重载
 * - 配置侧：清空玩家强度等运行时缓存；配置值本身每次 .get() 现读，无需重载
 * - 数据包侧：触发服务器资源重载，由 AddReloadListenerEvent 注册的
 *   InvasionDataLoader / WorldStageDataLoader / NemesisLootDataLoader 等重新解析
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
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

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.reload.reloading"
        ), true);

        // 配置侧：清除玩家强度缓存（配置值每次现读，无需额外重载）
        PlayerStrengthEvaluator.getInstance().clearAllCache();
        AdaptiveNemesisMod.LOGGER.debug("通过命令重新加载 Adaptive Nemesis：运行时缓存已清除");

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.reload.cache_cleared"
        ), false);

        // 数据包侧：触发服务器资源重载，重跑所有 SimpleJsonResourceReloadListener
        boolean datapackReloaded = triggerDatapackReload(source);

        source.sendSuccess(() -> Component.translatable(
            "adaptive_nemesis.command.reload.config_update"
        ), false);

        return datapackReloaded ? 1 : 0;
    }

    /**
     * 触发服务器数据包重载
     *
     * 与原版 /reload 相同入口：MinecraftServer.reloadResources 会按注册的
     * SimpleJsonResourceReloadListener 重新解析入侵、世界阶段与宿敌战利品数据包
     *
     * @param source 命令来源
     * @return 重载已成功触发返回 true，服务器不可用或触发失败返回 false
     */
    private static boolean triggerDatapackReload(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            return false;
        }
        try {
            CompletableFuture<Void> reloadFuture =
                server.reloadResources(server.getPackRepository().getSelectedIds());
            reloadFuture.exceptionally(throwable -> {
                AdaptiveNemesisMod.LOGGER.error("数据包重载失败", throwable);
                source.sendFailure(Component.literal(
                    "数据包重载失败: " + throwable.getMessage()
                ));
                return null;
            });
            AdaptiveNemesisMod.LOGGER.debug("已通过命令触发数据包重载");
            return true;
        } catch (Exception e) {
            AdaptiveNemesisMod.LOGGER.error("触发数据包重载失败", e);
            return false;
        }
    }
}
