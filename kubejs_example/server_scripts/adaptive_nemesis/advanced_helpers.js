// ============================================================
// 自适应宿敌模组 - KubeJS 高级辅助方法示例
// 演示如何通过 Java.loadClass 调用模组暴露的静态方法
// ============================================================

// 加载模组的 KubeJS 初始化器类
const KubeJSInitializer = Java.loadClass(
    'com.adaptive_nemesis.adaptive_nemesismod.kubejs.KubeJSInitializer'
);

/**
 * 注册一个自定义命令 /an_trigger，方便管理员手动触发入侵
 *
 * 参数:
 *   - 无参数: 对执行玩家触发默认亡灵入侵
 *   - waves difficulty: 自定义波次数和难度
 */
ServerEvents.commandRegistry(event => {
    const Commands = event.commands;
    const IntegerArgumentType = Java.loadClass('com.mojang.brigadier.arguments.IntegerArgumentType');
    const DoubleArgumentType = Java.loadClass('com.mojang.brigadier.arguments.DoubleArgumentType');
    const Component = Java.loadClass('net.minecraft.network.chat.Component');

    event.register(
        Commands.literal("an_trigger")
            .executes(context => {
                const player = context.source.player;
                if (player) {
                    KubeJSInitializer.triggerUndeadInvasion(player);
                    context.source.sendSuccess(() => Component.literal("§6已触发默认亡灵入侵"), true);
                    return 1;
                }
                return 0;
            })
            .then(Commands.argument("waves", IntegerArgumentType.integer(1, 20))
                .then(Commands.argument("difficulty", DoubleArgumentType.doubleArg(0.1, 10.0))
                    .executes(context => {
                        const player = context.source.player;
                        if (player) {
                            const waves = IntegerArgumentType.getInteger(context, "waves");
                            const difficulty = DoubleArgumentType.getDouble(context, "difficulty");
                            KubeJSInitializer.triggerUndeadInvasion(player, waves, difficulty);
                            context.source.sendSuccess(
                                () => Component.literal(`§6已触发 ${waves} 波、倍率 ${difficulty} 的亡灵入侵`),
                                true
                            );
                            return 1;
                        }
                        return 0;
                    })
                )
            )
    );
});

/**
 * 监听玩家登录事件，显示当前入侵状态
 */
PlayerEvents.loggedIn(event => {
    const player = event.player;
    const inInvasion = KubeJSInitializer.isInInvasion(player);

    if (inInvasion) {
        const progress = KubeJSInitializer.getInvasionProgress(player);
        player.tell(Component.literal(`§4[入侵进行中] §r当前进度: ${progress}`));
    } else {
        player.tell(Component.literal(`§7当前没有进行中的入侵事件`));
    }
});

console.log("自适应宿敌模组 - 高级辅助方法示例已加载！");
