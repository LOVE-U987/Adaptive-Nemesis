// ================================================
// Adaptive Nemesis - KubeJS 入侵事件示例脚本
// ================================================
// 将此文件复制到你的世界存档的 kubejs/server_scripts/ 目录下即可使用

// 监听服务器加载完成
ServerEvents.loaded(event => {
    console.log('§6[Adaptive Nemesis] §eKubeJS 入侵事件脚本已加载');
});

// ================================================
// 示例 1: 手动触发亡灵入侵
// ================================================
// 使用方法: /kubejs run adaptiveNemesis.triggerUndeadInvasion(player)
// 或者在游戏中通过命令触发

// 创建自定义命令来手动触发入侵
ServerEvents.commandRegistry(event => {
    event.register('trigger_invasion', command => {
        command
            .requires(source => source.hasPermission(2))
            .then(command.literal('undead').executes(ctx => {
                const player = ctx.getSource().getPlayerOrException();
                // 使用 Adaptive Nemesis 的 KubeJS API 触发入侵
                if (typeof adaptiveNemesis !== 'undefined') {
                    adaptiveNemesis.triggerUndeadInvasion(player);
                    player.sendMessage('§c⚔️ §e亡灵入侵已触发！');
                } else {
                    player.sendMessage('§c❌ §eAdaptive Nemesis KubeJS API 不可用');
                }
                return 1;
            }));
    });
});

// ================================================
// 示例 2: 带自定义参数触发入侵
// ================================================
// 使用方法: /kubejs run adaptiveNemesis.triggerUndeadInvasion(player, 5, 2.0)

ServerEvents.commandRegistry(event => {
    event.register('trigger_custom_invasion', command => {
        command
            .requires(source => source.hasPermission(2))
            .then(command.literal('undead')
                .then(command.argument('waves', command.INTEGER.create())
                    .then(command.argument('difficulty', command.FLOAT.create())
                        .executes(ctx => {
                            const player = ctx.getSource().getPlayerOrException();
                            const waves = ctx.getArgument('waves', command.INTEGER);
                            const difficulty = ctx.getArgument('difficulty', command.FLOAT);
                            
                            if (typeof adaptiveNemesis !== 'undefined') {
                                adaptiveNemesis.triggerUndeadInvasion(player, waves, difficulty);
                                player.sendMessage(`§c⚔️ §e自定义亡灵入侵已触发！波次: ${waves}, 难度倍率: ${difficulty}`);
                            } else {
                                player.sendMessage('§c❌ §eAdaptive Nemesis KubeJS API 不可用');
                            }
                            return 1;
                        })));
    });
});

// ================================================
// 示例 3: 检查玩家是否正在经历入侵
// ================================================
// 可以在其他脚本中使用此功能来限制某些行为

function checkInvasionStatus(player) {
    if (typeof adaptiveNemesis !== 'undefined') {
        const isInInvasion = adaptiveNemesis.isInInvasion(player);
        const progress = adaptiveNemesis.getInvasionProgress(player);
        
        if (isInInvasion) {
            console.log(`玩家 ${player.getName().getString()} 正在经历入侵，进度: ${progress}`);
            return true;
        }
    }
    return false;
}

// ================================================
// 示例 4: 击杀入侵敌人时给予额外奖励
// ================================================
EntityEvents.death(event => {
    const entity = event.getEntity();
    const killer = event.getEntity().getLastHurtByMob();
    
    if (killer && killer instanceof Player) {
        // 检查是否是入侵事件中的敌人（通过发光效果判断）
        if (entity.isGlowing()) {
            // 给予额外经验奖励
            entity.spawnAtLocation('minecraft:experience_bottle', 3);
            
            // 有几率掉落额外物品
            if (Math.random() < 0.3) {
                entity.spawnAtLocation('minecraft:diamond', 1);
            }
            
            killer.sendMessage('§a✨ §e你击败了一个入侵敌人，获得额外奖励！');
        }
    }
});

// ================================================
// 示例 5: 在入侵期间增强玩家能力
// ================================================
PlayerEvents.tick(event => {
    const player = event.getEntity();
    
    if (typeof adaptiveNemesis !== 'undefined' && adaptiveNemesis.isInInvasion(player)) {
        // 入侵期间给予玩家力量增益
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20, 1));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20, 0));
    }
});

// ================================================
// 示例 6: 自定义入侵触发条件
// ================================================
// 例如：玩家在夜晚时触发入侵的概率更高

ServerEvents.tick(event => {
    // 每分钟检查一次
    if (event.getServer().getTickCount() % 1200 !== 0) return;
    
    event.getServer().getPlayerList().getPlayers().forEach(player => {
        const level = player.level();
        
        // 只有在夜晚时才可能触发
        if (level.isNight()) {
            // 自定义触发概率（夜晚时翻倍）
            if (Math.random() < 0.1) {
                if (typeof adaptiveNemesis !== 'undefined') {
                    // 检查玩家是否已经在入侵中
                    if (!adaptiveNemesis.isInInvasion(player)) {
                        adaptiveNemesis.triggerUndeadInvasion(player);
                        player.sendMessage('§c🌙 §e夜幕降临，亡灵大军来袭！');
                    }
                }
            }
        }
    });
});

// ================================================
// 示例 7: 入侵结束时给予胜利奖励
// ================================================
// 注意：此示例使用自定义事件监听
// 实际使用时可能需要根据模组的事件系统进行调整

// 监听玩家获得物品（用于检测入侵奖励）
PlayerEvents.crafting(event => {
    // 可以在这里添加入侵奖励相关的逻辑
});

console.log('§6[Adaptive Nemesis] §eKubeJS 示例脚本加载完成');
