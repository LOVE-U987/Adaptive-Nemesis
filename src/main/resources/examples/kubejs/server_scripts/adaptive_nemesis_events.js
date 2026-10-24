// ================================================
// Adaptive Nemesis - 自定义 KubeJS 事件示例脚本
// ================================================
// 将此文件复制到你的世界存档的 kubejs/server_scripts/ 目录下即可使用。
//
// 前提：本模组的 KubeJS 插件已正确注册（kubejs.plugins.txt → KubeJSInitializer）。
// 事件组绑定名为 adaptive_nemesis（EventGroup.of("adaptive_nemesis")），
// 全部为 server 类型事件，需写在 server_scripts 中。

// ================================================
// 示例 1: 实体强化事件（entity_scale）
// ================================================
// 模组强化怪物时触发，可按实体类型覆写强化倍率
adaptive_nemesis.entity_scale(event => {
    const entity = event.getEntity();
    // 对苦力怕额外强化 1.5 倍
    if (entity.type == 'minecraft:creeper') {
        event.setMultiplier(event.getMultiplier() * 1.5);
    }
    // 取消对特定实体的强化
    if (entity.type == 'minecraft:bat') {
        event.cancelEvent();
    }
});

// ================================================
// 示例 2: 伤害计算事件（damage_calculation）
// ================================================
// 真实伤害换算时触发，可调整或取消转化
adaptive_nemesis.damage_calculation(event => {
    // 上限：真实伤害不超过 50
    if (event.getCalculatedDamage() > 50) {
        event.setCalculatedDamage(50);
    }
});

// ================================================
// 示例 3: 玩家强度评估事件（player_strength_evaluation）
// ================================================
// 模组重算玩家强度时触发，可修改最终评分
adaptive_nemesis.player_strength_evaluation(event => {
    // 全员强度 +10%
    event.setFinalStrength(event.getFinalStrength() * 1.1);
});

// ================================================
// 示例 4: 世界阶段变化事件（world_stage_change）
// ================================================
// 全局进度阶段推进时触发
adaptive_nemesis.world_stage_change(event => {
    console.log(`[Adaptive Nemesis] 世界阶段推进: ${event.getOldStage()} -> ${event.getNewStage()}`);
});

// ================================================
// 示例 5: 入侵开始事件（invasion_start）
// ================================================
// 入侵开始时触发，可修改波次/难度或取消入侵
adaptive_nemesis.invasion_start(event => {
    // 难度减半（调试用）
    event.setDifficultyMultiplier(event.getDifficultyMultiplier() * 0.5);
});

// ================================================
// 示例 6: 宿敌记忆更新事件（nemesis_memory_update）
// ================================================
// 宿敌档案里程碑更新时触发
adaptive_nemesis.nemesis_memory_update(event => {
    if (event.getNemesisLevel() >= 5) {
        console.log('[Adaptive Nemesis] 某位玩家的宿敌达到了 5 级！');
    }
});
