package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.ActiveInvasion;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionSystem;
import com.adaptive_nemesis.adaptive_nemesismod.invasion.InvasionSystem.InvasionType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 入侵事件命令
 * 
 * 命令: /an invasion [trigger|status|setdifficulty]
 * 功能: 管理入侵事件
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class InvasionCommand {

    /**
     * 入侵系统实例
     */
    private static InvasionSystem invasionSystem;

    /**
     * 设置入侵系统实例
     * 
     * @param system 入侵系统实例
     */
    public static void setInvasionSystem(InvasionSystem system) {
        invasionSystem = system;
    }

    /**
     * 注册入侵命令
     * 
     * @return 命令构建器
     */
    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("invasion")
            .then(Commands.literal("trigger")
                .then(Commands.argument("waves", IntegerArgumentType.integer(1, 20))
                    .then(Commands.argument("difficulty", DoubleArgumentType.doubleArg(0.1, 10.0))
                        .executes(InvasionCommand::executeTriggerCustom)))
                .executes(InvasionCommand::executeTriggerDefault))
            .then(Commands.literal("status")
                .executes(InvasionCommand::executeStatus))
            .then(Commands.literal("setdifficulty")
                .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.1, 10.0))
                    .executes(InvasionCommand::executeSetDifficulty)));
    }

    /**
     * 执行触发入侵（默认参数）
     * 
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeTriggerDefault(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        if (invasionSystem == null) {
            source.sendFailure(Component.literal("入侵系统尚未初始化"));
            return 0;
        }

        boolean success = invasionSystem.triggerInvasion(player.level(), player, InvasionType.UNDEAD);
        
        if (success) {
            source.sendSuccess(() -> Component.literal("入侵事件已触发！"), true);
            AdaptiveNemesisMod.LOGGER.debug("玩家 {} 通过命令触发了入侵事件", player.getName().getString());
            return 1;
        } else {
            source.sendFailure(Component.literal("无法触发入侵事件（可能正在进行中）"));
            return 0;
        }
    }

    /**
     * 执行触发入侵（自定义参数）
     * 
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeTriggerCustom(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        if (invasionSystem == null) {
            source.sendFailure(Component.literal("入侵系统尚未初始化"));
            return 0;
        }

        int waves = IntegerArgumentType.getInteger(context, "waves");
        double difficulty = DoubleArgumentType.getDouble(context, "difficulty");

        boolean success = invasionSystem.triggerInvasionManual(player, InvasionType.UNDEAD, waves, difficulty);
        
        if (success) {
            source.sendSuccess(() -> Component.literal(
                String.format("入侵事件已触发！波次: %d, 难度倍率: %.2f", waves, difficulty)
            ), true);
            AdaptiveNemesisMod.LOGGER.debug(
                "玩家 {} 通过命令触发了入侵事件: 波次={}, 难度倍率={}",
                player.getName().getString(), waves, difficulty
            );
            return 1;
        } else {
            source.sendFailure(Component.literal("无法触发入侵事件（可能正在进行中）"));
            return 0;
        }
    }

    /**
     * 执行查看入侵状态
     * 
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("此命令只能由玩家执行"));
            return 0;
        }

        if (invasionSystem == null) {
            source.sendFailure(Component.literal("入侵系统尚未初始化"));
            return 0;
        }

        ActiveInvasion invasion = invasionSystem.getActiveInvasion(player);
        
        if (invasion == null) {
            source.sendSuccess(() -> Component.literal("当前没有正在进行的入侵事件"), true);
        } else {
            source.sendSuccess(() -> Component.literal(
                String.format("入侵状态: 进行中 | 波次: %d/%d | 难度倍率: %.2f",
                    invasion.getCurrentWave(),
                    invasion.getTotalWaves(),
                    invasion.getDifficultyMultiplier()
                )
            ), true);
            
            source.sendSuccess(() -> Component.literal(
                String.format("剩余敌人: %d", invasion.getRemainingEnemies())
            ), false);
        }

        return 1;
    }

    /**
     * 执行设置入侵难度
     * 
     * @param context 命令上下文
     * @return 命令执行结果
     */
    private static int executeSetDifficulty(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        double multiplier = DoubleArgumentType.getDouble(context, "multiplier");

        source.sendSuccess(() -> Component.literal(
            String.format("入侵自定义难度已设置为: %.2f", multiplier)
        ), true);

        AdaptiveNemesisMod.LOGGER.debug(
            "管理员设置入侵自定义难度倍率为: {}", multiplier
        );

        return 1;
    }
}
