// ============================================================
// 自适应宿敌模组 - KubeJS 入侵事件完整示例
// 演示 invasion_start / invasion_wave_start / invasion_end 的用法
// ============================================================

/**
 * 监听入侵开始事件
 *
 * 在入侵即将开始时触发，可修改总波次数和难度倍率，或取消入侵。
 */
adaptive_nemesis.invasion_start(event => {
    const player = event.getPlayer();
    const playerName = event.getPlayerName();
    const type = event.getType();

    // 示例1: 根据玩家装备调整难度
    if (event.getPlayer().inventory.contains(item => item.id === 'minecraft:netherite_sword')) {
        event.setDifficultyMultiplier(event.getDifficultyMultiplier() * 1.3);
        player.tell(Component.literal(`§c检测到下界合金剑，入侵难度提升 30%！`));
    }

    // 示例2: 限制最大波次数
    if (event.getTotalWaves() > 8) {
        event.setTotalWaves(8);
    }

    // 示例3: 广播入侵开始
    player.server.tell(
        Component.literal(`§4[入侵警报] §r${playerName} 触发了 ${type} 入侵！共 ${event.getTotalWaves()} 波`)
    );

    console.log(`入侵开始: 玩家=${playerName}, 类型=${type}, 波次数=${event.getTotalWaves()}, 难度=${event.getDifficultyMultiplier().toFixed(2)}`);
});

/**
 * 监听入侵波次开始事件
 *
 * 每一波入侵开始时触发，适合记录进度或发送倒计时提示。
 */
adaptive_nemesis.invasion_wave_start(event => {
    const player = event.getPlayer();
    const currentWave = event.getCurrentWave();
    const totalWaves = event.getTotalWaves();

    // 示例: 给玩家发送当前波次提示
    player.tell(
        Component.literal(`§6[入侵] §r第 ${currentWave}/${totalWaves} 波敌人正在逼近！`)
    );

    // 示例: Boss 波次特殊提示
    if (currentWave === totalWaves) {
        player.server.tell(Component.literal(`§4最终波次！小心 Boss 登场！`));
    }

    console.log(`入侵波次开始: ${event.getProgress()}, 玩家=${event.getPlayerName()}`);
});

/**
 * 监听入侵结束事件
 *
 * 入侵结束时触发（无论胜利或失败），可修改奖励配置。
 */
adaptive_nemesis.invasion_end(event => {
    const player = event.getPlayer();
    const playerName = event.getPlayerName();
    const victory = event.isVictory();
    const wavesCompleted = event.getWavesCompleted();
    const totalWaves = event.getTotalWaves();

    // 示例1: 根据胜负发送不同消息
    if (victory) {
        player.server.tell(
            Component.literal(`§a[入侵胜利] §r${playerName} 成功抵御了 ${wavesCompleted}/${totalWaves} 波入侵！`)
        );
    } else {
        player.tell(Component.literal(`§7[入侵失败] 你坚持了 ${wavesCompleted} 波，下次再接再厉。`));
    }

    // 示例2: 胜利时追加奖励
    if (victory && wavesCompleted >= totalWaves) {
        event.addExperience(1000);
        event.addLoot("minecraft:chests/end_city_treasure");

        // 添加药水效果奖励（需要 MobEffect 对象）
        const MobEffects = Java.loadClass('net.minecraft.world.effect.MobEffects');
        event.addEffect(MobEffects.REGENERATION, 60, 1);
    }

    // 示例3: 完美通关时额外奖励
    if (victory && wavesCompleted === totalWaves) {
        event.addLoot("minecraft:chests/ancient_city");
        player.tell(Component.literal(`§6完美通关！获得额外古代城市战利品！`));
    }

    // 示例4: 完全禁用数据包默认奖励，改由脚本自定义
    // event.setRewardsEnabled(false);

    console.log(`入侵结束: 玩家=${playerName}, 胜利=${victory}, 完成波次=${wavesCompleted}/${totalWaves}`);
});

console.log("自适应宿敌模组 - 入侵事件示例已加载！");
