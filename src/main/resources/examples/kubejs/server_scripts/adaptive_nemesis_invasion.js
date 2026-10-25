// ================================================
// Adaptive Nemesis - KubeJS 自定义命令示例
// ================================================
// 演示在 KubeJS 中注册自定义命令的标准写法（KubeJS 6 / Forge 1.20.1），
// 并通过转发模组原生命令实现触发入侵。
// 将此文件复制到世界存档的 kubejs/server_scripts/ 目录下即可使用。
//
// 说明：
//   - 触发入侵推荐直接使用原生命令 /an invasion trigger [类型] [波次] [难度]
//   - 事件监听示例见 adaptive_nemesis_events.js 与 adaptive_nemesis_kubejs_test.js
//
// 命令：
//   /trigger_invasion                    以默认参数触发亡灵入侵（需要权限等级 2）
//   /trigger_invasion_custom <波次> <难度倍率>
//
// KubeJS 6 无自动导入，参数类型需显式加载 Java 类：

const IntegerArgumentType = Java.loadClass('com.mojang.brigadier.arguments.IntegerArgumentType');
const DoubleArgumentType = Java.loadClass('com.mojang.brigadier.arguments.DoubleArgumentType');

// ================================================
// 示例 1: /trigger_invasion —— 默认参数触发亡灵入侵
// ================================================
// 通过 performPrefixedCommand 转发到模组原生命令 /an invasion trigger

ServerEvents.commandRegistry(event => {
    const Commands = event.commands;
    event.register(
        Commands.literal('trigger_invasion')
            .requires(source => source.hasPermission(2))
            .executes(ctx => {
                const source = ctx.source;
                source.getServer().getCommands().performPrefixedCommand(
                    source,
                    'an invasion trigger undead_invasion'
                );
                return 1;
            })
    );
});

// ================================================
// 示例 2: /trigger_invasion_custom <波次> <难度倍率> —— 自定义参数触发
// ================================================

ServerEvents.commandRegistry(event => {
    const Commands = event.commands;
    event.register(
        Commands.literal('trigger_invasion_custom')
            .requires(source => source.hasPermission(2))
            .then(Commands.argument('waves', IntegerArgumentType.integer(1, 20))
                .then(Commands.argument('difficulty', DoubleArgumentType.doubleArg(0.1, 10.0))
                    .executes(ctx => {
                        const source = ctx.source;
                        const waves = IntegerArgumentType.getInteger(ctx, 'waves');
                        const difficulty = DoubleArgumentType.getDouble(ctx, 'difficulty');
                        source.getServer().getCommands().performPrefixedCommand(
                            source,
                            'an invasion trigger undead_invasion ' + waves + ' ' + difficulty
                        );
                        return 1;
                    })))
    );
});

console.log('[Adaptive Nemesis] KubeJS 命令示例脚本已加载');
