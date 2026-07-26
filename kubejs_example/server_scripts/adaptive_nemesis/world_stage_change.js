// ============================================================
// 自适应宿敌模组 - KubeJS 世界阶段变化示例
// 演示如何监听世界阶段提升并执行自定义逻辑
// ============================================================

/**
 * 监听世界阶段变化事件
 *
 * 当玩家击杀 Boss 导致世界阶段提升时触发。
 * 可以在此发送通知、调整倍率或发放阶段奖励。
 */
adaptive_nemesis.world_stage_change(event => {
    const player = event.getPlayer();
    const playerName = event.getPlayerName();
    const oldStage = event.getOldStage();
    const newStage = event.getNewStage();
    const stageDelta = event.getStageDelta();

    // 示例1: 广播阶段提升消息
    player.server.tell(
        Component.literal(`§6[世界阶段] §r${playerName} 击杀了 Boss！世界阶段从 ${oldStage} 提升到 ${newStage}`)
    );

    // 示例2: 根据提升幅度调整倍率
    if (stageDelta > 1) {
        // 连续跨阶段时，额外提升难度倍率
        event.setStageMultiplier(event.getStageMultiplier() * 1.2);
        player.tell(Component.literal(`§c跨阶段提升，难度额外增加 20%`));
    }

    // 示例3: 达到特定阶段时给予玩家奖励
    if (newStage === 3) {
        player.tell(Component.literal(`§a世界阶段达到 3，全体玩家获得挑战者荣耀！`));
        // 可以在这里执行奖励命令或修改玩家数据
    }

    // 示例4: 记录日志
    console.log(`世界阶段变化: ${oldStage} -> ${newStage}, 触发玩家: ${playerName}, 已击杀 Boss 种类: ${event.getDefeatedBossCount()}`);

    // 示例5: 取消本次倍率调整（保留原倍率）
    // event.cancelEvent();
});

console.log("自适应宿敌模组 - 世界阶段变化示例已加载！");
