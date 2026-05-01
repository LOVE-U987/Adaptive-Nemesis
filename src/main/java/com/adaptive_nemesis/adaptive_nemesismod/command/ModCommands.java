package com.adaptive_nemesis.adaptive_nemesismod.command;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 模组命令注册器
 *
 * 注册所有Adaptive Nemesis相关的游戏内命令：
 * - /an status - 查看当前状态
 * - /an strength [玩家] - 查看玩家强度
 * - /an difficulty - 查看/调整难度设置
 * - /an protection [玩家] - 查看/管理新手保护
 * - /an memory [玩家] - 查看宿敌记忆
 * - /an test [模块] - 测试模组各模块
 * - /an nemesis [类型] - 召唤宿敌
 * - /an scan [范围] - 扫描周围敌人加成数据
 * - /an reload - 重新加载配置
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class ModCommands {

    /**
     * 命令基础名称
     */
    public static final String COMMAND_BASE = "an";

    /**
     * 默认构造函数
     */
    public ModCommands() {}

    /**
     * 注册命令事件处理器
     *
     * @param event 注册命令事件
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // 注册基础命令 /an
        dispatcher.register(
            Commands.literal(COMMAND_BASE)
                .requires(source -> source.hasPermission(2)) // 需要OP权限等级2
                .then(StatusCommand.register())
                .then(StrengthCommand.register())
                .then(DifficultyCommand.register())
                .then(ProtectionCommand.register())
                .then(MemoryCommand.register())
                .then(TestCommand.register())
                .then(SummonNemesisCommand.register())
                .then(ScanCommand.register())
                .then(ReloadCommand.register())
                .then(HelpCommand.register())
        );

        AdaptiveNemesisMod.LOGGER.info("⌨️ 命令系统已注册");
    }
}
