// ================================================
// Adaptive Nemesis - KubeJS 联动测试脚本
// ================================================
// 用途：验证本模组的 8 个自定义 KubeJS 事件是否正常注册并触发。
// 本脚本为只读观察模式，不修改任何游戏数值，可安全用于正式存档。
//
// 安装方法：
//   1. 确保已安装 KubeJS（2101.x，NeoForge 1.21.1）。
//   2. 将本文件复制到 <世界存档>/kubejs/server_scripts/ 目录。
//   3. 进入世界后执行 /reload 或重启服务器。
//   4. 聊天栏出现 "[AN-KubeJS-TEST] 测试脚本已加载" 即表示脚本加载成功。
//
// 判定标准：
//   - 脚本加载时输出 1 行 "测试脚本已加载"。
//   - 触发对应游戏行为后，日志/聊天栏出现对应事件的 "触发" 行。
//   - 若某事件始终不输出，说明该事件未注册或触发条件未满足。
//
// 日志查看：单人游戏按 F3+` 打开日志，或查看 logs/latest.log，
//           搜索前缀 [AN-KubeJS-TEST] 即可过滤全部测试输出。

console.info('[AN-KubeJS-TEST] ========================================');
console.info('[AN-KubeJS-TEST] 测试脚本已加载，等待事件触发...');
console.info('[AN-KubeJS-TEST] 事件组: adaptive_nemesis (8 个 server 事件)');
console.info('[AN-KubeJS-TEST] ========================================');

// ================================================
// 1. entity_scale - 实体强化事件
// 触发方式：任意怪物被模组强化时（夜晚自然刷怪、/an nemesis 召唤宿敌等）。
// 预期输出：实体 ID、实体名称、当前强化倍率。
// ================================================
adaptive_nemesis.entity_scale(event => {
    console.info(`[AN-KubeJS-TEST] [1/8] entity_scale 触发 | 实体=${event.getEntityId()} | 名称=${event.getEntityName()} | 倍率=${event.getMultiplier().toFixed(2)} | 生命=${event.getMaxHealth().toFixed(1)} | 攻击=${event.getAttackDamage().toFixed(2)}`);
});

// ================================================
// 2. damage_calculation - 伤害计算事件
// 触发方式：模组进行真实伤害换算时（强化怪/宿敌攻击玩家）。
// 预期输出：攻击者、目标、原始伤害、计算伤害、护甲倍率。
// ================================================
adaptive_nemesis.damage_calculation(event => {
    console.info(`[AN-KubeJS-TEST] [2/8] damage_calculation 触发 | ${event.getAttackerName()} -> ${event.getTargetName()} | 原始=${event.getOriginalDamage().toFixed(2)} | 计算=${event.getCalculatedDamage().toFixed(2)} | 护甲倍率=${event.getArmorMultiplier().toFixed(2)}`);
});

// ================================================
// 3. player_strength_evaluation - 玩家强度评估事件
// 触发方式：模组重算玩家强度时（装备变化、/an strength 查询等）。
// 预期输出：玩家名、基础强度、最终强度、各项分项强度。
// ================================================
adaptive_nemesis.player_strength_evaluation(event => {
    console.info(`[AN-KubeJS-TEST] [3/8] player_strength_evaluation 触发 | 玩家=${event.getPlayerName()} | 基础=${event.getBaseStrength().toFixed(2)} | 最终=${event.getFinalStrength().toFixed(2)} | 防御=${event.getDefenseStrength().toFixed(2)} | 攻击=${event.getAttackStrength().toFixed(2)} | 魔法=${event.getMagicStrength().toFixed(2)} | 战斗=${event.getCombatStrength().toFixed(2)}`);
});

// ================================================
// 4. world_stage_change - 世界阶段变化事件
// 触发方式：击败 Boss 导致世界阶段推进时。
// 预期输出：玩家名、旧阶段、新阶段、阶段倍率、已击杀 Boss 数。
// ================================================
adaptive_nemesis.world_stage_change(event => {
    console.info(`[AN-KubeJS-TEST] [4/8] world_stage_change 触发 | 玩家=${event.getPlayerName()} | 阶段=${event.getOldStage()} -> ${event.getNewStage()} | 阶段倍率=${event.getStageMultiplier().toFixed(2)} | 已击杀Boss=${event.getDefeatedBossCount()}`);
});

// ================================================
// 5. nemesis_memory_update - 宿敌记忆更新事件
// 触发方式：玩家击杀宿敌或被宿敌击杀后记忆数据更新时。
// 预期输出：玩家名、击杀数、死亡数、宿敌等级、各项加成。
// ================================================
adaptive_nemesis.nemesis_memory_update(event => {
    console.info(`[AN-KubeJS-TEST] [5/8] nemesis_memory_update 触发 | 玩家=${event.getPlayerName()} | 击杀=${event.getKillCount()} | 死亡=${event.getDeathCount()} | KDA=${event.getKDRatio().toFixed(2)} | 宿敌等级=${event.getNemesisLevel()} | 攻击加成=${event.getAttackBonus().toFixed(2)} | 速度加成=${event.getSpeedBonus().toFixed(2)} | 生命加成=${event.getHealthBonus().toFixed(2)}`);
});

// ================================================
// 6. invasion_start - 入侵开始事件
// 触发方式：/an invasion trigger 或满足自然触发条件时。
// 预期输出：玩家名、入侵类型、总波次、难度倍率。
// 验证修改链路（可选）：取消下面三行的注释后，
//   入侵总波次会被脚本改写为 3，用于确认返回值生效。
// ================================================
adaptive_nemesis.invasion_start(event => {
    console.info(`[AN-KubeJS-TEST] [6/8] invasion_start 触发 | 玩家=${event.getPlayerName()} | 类型=${event.getType()} | 总波次=${event.getTotalWaves()} | 难度倍率=${event.getDifficultyMultiplier().toFixed(2)}`);
    // event.setTotalWaves(3);
    // console.info(`[AN-KubeJS-TEST] [6/8] invasion_start 已将总波次改写为 ${event.getTotalWaves()}`);
});

// ================================================
// 7. invasion_wave_start - 入侵波次开始事件
// 触发方式：入侵进行中，每一波开始战斗时自动触发。
// 预期输出：玩家名、入侵类型、当前波次/总波次、难度倍率。
// ================================================
adaptive_nemesis.invasion_wave_start(event => {
    console.info(`[AN-KubeJS-TEST] [7/8] invasion_wave_start 触发 | 玩家=${event.getPlayerName()} | 类型=${event.getType()} | 波次=${event.getCurrentWave()}/${event.getTotalWaves()} | 难度倍率=${event.getDifficultyMultiplier().toFixed(2)}`);
});

// ================================================
// 8. invasion_end - 入侵结束事件
// 触发方式：入侵胜利（打完所有波次）或失败时。
// 预期输出：玩家名、入侵类型、胜负、完成波次、难度倍率。
// 验证奖励修改链路（可选）：取消下面两行的注释后，
//   入侵结算时会额外发放 100 点经验，用于确认奖励对象可被脚本修改。
// ================================================
adaptive_nemesis.invasion_end(event => {
    console.info(`[AN-KubeJS-TEST] [8/8] invasion_end 触发 | 玩家=${event.getPlayerName()} | 类型=${event.getType()} | 胜利=${event.isVictory()} | 完成波次=${event.getWavesCompleted()}/${event.getTotalWaves()} | 难度倍率=${event.getDifficultyMultiplier().toFixed(2)}`);
    // event.addExperience(100);
    // console.info('[AN-KubeJS-TEST] [8/8] invasion_end 已追加 100 点经验奖励');
});
