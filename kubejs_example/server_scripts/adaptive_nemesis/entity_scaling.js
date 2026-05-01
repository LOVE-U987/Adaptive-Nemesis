// ============================================================
// 自适应宿敌模组 - KubeJS 集成示例脚本
// 实体强化事件处理
// ============================================================

// 监听实体强化事件
adaptive_nemesis.entity_scale(event => {
    const entity = event.entity;
    const entityId = event.entityId;
    const entityName = event.entityName;
    
    // 示例1: 对特定实体增加额外倍率
    if (entityId.includes("zombie")) {
        // 僵尸获得额外50%强化
        event.multiplyMultiplier(1.5);
        console.log(`僵尸 ${entityName} 获得额外50%强化，当前倍率: ${event.getMultiplier().toFixed(2)}`);
    }
    
    // 示例2: 对Boss级实体增加更多强化
    if (entityId.includes("wither") || entityId.includes("ender_dragon")) {
        event.multiplyMultiplier(2.0);
        console.log(`Boss ${entityName} 获得双倍强化，当前倍率: ${event.getMultiplier().toFixed(2)}`);
    }
    
    // 示例3: 取消特定实体的强化（例如：动物、村民）
    if (entityId.includes("villager") || entityId.includes("animal")) {
        event.cancel();
        console.log(`取消了 ${entityName} 的强化`);
        return;
    }
    
    // 示例4: 根据实体当前生命值动态调整
    const healthPercent = event.getHealth() / event.getMaxHealth();
    if (healthPercent < 0.5) {
        // 生命值低于50%的实体获得额外强化
        event.addMultiplier(0.5);
        console.log(`虚弱实体 ${entityName} 获得额外强化，当前倍率: ${event.getMultiplier().toFixed(2)}`);
    }
    
    // 示例5: 设置自定义属性
    if (event.getMultiplier() > 3.0) {
        // 高倍率实体增加攻击力
        const newDamage = event.getAttackDamage() * 1.5;
        event.setAttackDamage(newDamage);
        console.log(`高倍率实体 ${entityName} 攻击力提升至 ${newDamage.toFixed(2)}`);
    }
});

// 监听玩家强度评估事件
adaptive_nemesis.player_strength_evaluation(event => {
    const player = event.player;
    const playerName = event.playerName;
    
    // 示例1: 根据玩家等级调整强度
    const playerLevel = event.getPlayerLevel();
    if (playerLevel > 30) {
        // 高等级玩家额外增加强度
        event.addStrength(playerLevel * 0.5);
        console.log(`高等级玩家 ${playerName} 获得等级加成，当前强度: ${event.getFinalStrength().toFixed(2)}`);
    }
    
    // 示例2: 检查玩家是否持有特定物品
    if (event.hasItem("minecraft:diamond_sword")) {
        event.multiplyStrength(1.2);
        console.log(`玩家 ${playerName} 持有钻石剑，强度提升20%`);
    }
    
    // 示例3: 根据玩家生命值调整
    const healthPercent = event.getPlayerHealth() / event.getPlayerMaxHealth();
    if (healthPercent < 0.3) {
        // 低生命值玩家降低强度评估（更公平）
        event.multiplyStrength(0.8);
        console.log(`低生命值玩家 ${playerName} 强度评估降低`);
    }
    
    // 示例4: 根据各维度强度进行自定义加权
    const defenseStrength = event.getDefenseStrength();
    const attackStrength = event.getAttackStrength();
    const magicStrength = event.getMagicStrength();
    const combatStrength = event.getCombatStrength();
    
    // 如果玩家主要使用魔法，增加魔法强度的权重
    if (magicStrength > attackStrength && magicStrength > combatStrength) {
        event.addStrength(magicStrength * 0.3);
        console.log(`法师玩家 ${playerName} 获得魔法加成`);
    }
});

// 监听伤害计算事件
adaptive_nemesis.damage_calculation(event => {
    const attacker = event.attacker;
    const target = event.target;
    const originalDamage = event.originalDamage;
    
    // 示例1: 对玩家攻击者增加真实伤害
    if (event.isAttackerPlayer()) {
        // 玩家攻击时，增加10%真实伤害
        event.addDamage(originalDamage * 0.1);
        console.log(`玩家 ${event.getAttackerName()} 获得额外真实伤害`);
    }
    
    // 示例2: 对Boss目标减少真实伤害
    if (event.targetName.includes("wither") || event.targetName.includes("dragon")) {
        event.setCalculatedDamage(event.getCalculatedDamage() * 0.5);
        console.log(`Boss ${event.targetName} 受到的真实伤害减半`);
    }
    
    // 示例3: 根据护甲倍率调整
    const armorMultiplier = event.getArmorMultiplier();
    if (armorMultiplier > 5.0) {
        // 超高护甲玩家受到更多真实伤害
        event.multiplyMultiplier(1.5);
        console.log(`超高护甲玩家 ${event.getTargetName()} 受到更多真实伤害`);
    }
    
    // 示例4: 完全取消特定情况的真实伤害
    if (event.getTargetName().includes("newbie")) {
        event.cancel();
        console.log(`取消了新手玩家的真实伤害`);
    }
});

// 监听宿敌记忆更新事件
adaptive_nemesis.nemesis_memory_update(event => {
    const playerName = event.playerName;
    const killCount = event.killCount;
    const deathCount = event.deathCount;
    const nemesisLevel = event.nemesisLevel;
    
    // 示例1: 达到里程碑时给予奖励
    if (event.isMilestone()) {
        console.log(`玩家 ${playerName} 达到里程碑！击杀数: ${killCount}`);
        // 可以在这里执行其他奖励逻辑
    }
    
    // 示例2: 根据KDA比率调整
    const kdaRatio = event.getKDRatio();
    if (kdaRatio > 5.0) {
        console.log(`玩家 ${playerName} KDA极高 (${kdaRatio.toFixed(2)})，宿敌等级快速提升`);
    }
    
    // 示例3: 高宿敌等级时触发特殊事件
    if (event.isAtLeastLevel(10)) {
        console.log(`玩家 ${playerName} 宿敌等级达到 ${nemesisLevel}，世界难度提升！`);
        // 可以在这里触发全局难度提升
    }
    
    // 示例4: 记录统计信息
    console.log(`宿敌记忆更新 - 玩家: ${playerName}, 击杀: ${killCount}, 死亡: ${deathCount}, 等级: ${nemesisLevel}, 总加成: ${(event.getTotalBonus() * 100).toFixed(1)}%`);
});

console.log("自适应宿敌模组 KubeJS 集成脚本已加载！");
