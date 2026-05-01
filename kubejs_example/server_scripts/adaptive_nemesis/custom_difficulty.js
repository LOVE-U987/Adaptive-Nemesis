// ============================================================
// 自适应宿敌模组 - KubeJS 自定义难度系统
// 提供动态难度调整和自定义规则
// ============================================================

// 配置对象
const ANConfig = {
    // 难度等级配置
    difficultyLevels: {
        PEACEFUL: { name: "和平", multiplier: 0.5, color: "§a" },
        EASY: { name: "简单", multiplier: 0.8, color: "§b" },
        NORMAL: { name: "普通", multiplier: 1.0, color: "§e" },
        HARD: { name: "困难", multiplier: 1.5, color: "§c" },
        NIGHTMARE: { name: "噩梦", multiplier: 2.5, color: "§4" },
        APOCALYPSE: { name: "天启", multiplier: 5.0, color: "§5" }
    },
    
    // 当前全局难度
    globalDifficulty: "NORMAL",
    
    // 玩家个人难度覆盖
    playerDifficulty: {}
};

// 监听实体强化事件 - 应用全局难度倍率
adaptive_nemesis.entity_scale(event => {
    const globalMultiplier = ANConfig.difficultyLevels[ANConfig.globalDifficulty].multiplier;
    
    // 应用全局难度倍率
    event.multiplyMultiplier(globalMultiplier);
    
    // 检查是否有玩家个人难度设置
    // 注意：这里需要获取附近玩家的UUID，实际实现可能需要调整
    // const playerMultiplier = getPlayerDifficultyMultiplier(playerUUID);
    // if (playerMultiplier) {
    //     event.multiplyMultiplier(playerMultiplier);
    // }
});

// 监听玩家强度评估事件 - 根据难度调整
adaptive_nemesis.player_strength_evaluation(event => {
    const difficulty = ANConfig.difficultyLevels[ANConfig.globalDifficulty];
    
    // 高难度下增加玩家强度评估（让敌人更强）
    if (difficulty.multiplier > 1.5) {
        event.multiplyStrength(difficulty.multiplier);
    }
    // 低难度下降低玩家强度评估（让敌人更弱）
    else if (difficulty.multiplier < 1.0) {
        event.multiplyStrength(difficulty.multiplier);
    }
});

// 注册命令 - 难度管理
ServerEvents.commandRegistry(event => {
    const Commands = event.commands;
    const StringArgumentType = Java.loadClass('com.mojang.brigadier.arguments.StringArgumentType');
    const Component = Java.loadClass('net.minecraft.network.chat.Component');
    
    // /an_difficulty 命令
    event.register(
        Commands.literal("an_difficulty")
            // 查看当前难度
            .executes(context => {
                const difficulty = ANConfig.difficultyLevels[ANConfig.globalDifficulty];
                context.source.sendSuccess(
                    () => Component.literal(`${difficulty.color}当前全局难度: ${difficulty.name} (倍率: ${difficulty.multiplier}x)`),
                    false
                );
                return 1;
            })
            // 设置难度
            .then(Commands.argument("level", StringArgumentType.word())
                .suggests((context, builder) => {
                    Object.keys(ANConfig.difficultyLevels).forEach(level => {
                        builder.suggest(level.toLowerCase());
                    });
                    return builder.buildFuture();
                })
                .executes(context => {
                    const level = StringArgumentType.getString(context, "level").toUpperCase();
                    
                    if (ANConfig.difficultyLevels[level]) {
                        ANConfig.globalDifficulty = level;
                        const difficulty = ANConfig.difficultyLevels[level];
                        
                        // 广播难度变更
                        const server = context.source.server;
                        server.playerList.broadcastSystemMessage(
                            Component.literal(`§6[自适应宿敌] §r全局难度已变更为: ${difficulty.color}${difficulty.name} (倍率: ${difficulty.multiplier}x)`),
                            false
                        );
                        
                        return 1;
                    } else {
                        context.source.sendFailure(
                            Component.literal(`§c无效的难度等级: ${level}`)
                        );
                        return 0;
                    }
                })
            )
            // 查看难度列表
            .then(Commands.literal("list")
                .executes(context => {
                    context.source.sendSuccess(
                        () => Component.literal("§6=== 可用难度等级 ==="),
                        false
                    );
                    
                    Object.entries(ANConfig.difficultyLevels).forEach(([key, value]) => {
                        const marker = key === ANConfig.globalDifficulty ? "§l> " : "  ";
                        context.source.sendSuccess(
                            () => Component.literal(`${marker}${value.color}${value.name} §7(${key}) - 倍率: ${value.multiplier}x`),
                            false
                        );
                    });
                    
                    return 1;
                })
            )
    );
});

// 监听玩家登录事件 - 发送难度信息
PlayerEvents.loggedIn(event => {
    const player = event.player;
    const difficulty = ANConfig.difficultyLevels[ANConfig.globalDifficulty];
    
    player.tell(Component.literal(`§6[自适应宿敌] §r欢迎！当前世界难度: ${difficulty.color}${difficulty.name}`));
    player.tell(Component.literal(`§7使用 /an_difficulty 查看或修改难度设置`));
});

console.log("自适应宿敌模组 - 自定义难度系统已加载！");
