# 自适应宿敌模组 - KubeJS 集成指南

## 简介

自适应宿敌模组 (Adaptive Nemesis) 提供了完整的 KubeJS 集成支持，允许服务器管理员和整合包作者通过 JavaScript 脚本自定义模组的各项功能。

## 安装

1. 确保已安装 KubeJS 模组
2. 将 `server_scripts` 文件夹中的脚本复制到你的整合包的 `kubejs/server_scripts` 目录
3. 重启服务器或执行 `/kubejs reload` 命令

## 可用事件

### 1. 实体强化事件 (`adaptive_nemesis.entity_scale`)

当自适应宿敌模组对实体进行属性强化时触发。

```javascript
adaptive_nemesis.entity_scale(event => {
    // 获取实体信息
    const entity = event.entity;        // 实体对象
    const entityId = event.entityId;    // 实体ID (如 "minecraft:zombie")
    const entityName = event.entityName; // 实体名称
    
    // 获取/修改强化倍率
    const multiplier = event.getMultiplier();      // 获取当前倍率
    event.setMultiplier(2.0);                      // 设置倍率为2.0
    event.addMultiplier(0.5);                      // 增加0.5倍率
    event.multiplyMultiplier(1.5);                 // 乘以1.5
    
    // 取消强化
    event.cancelEvent();
    
    // 获取实体属性
    const health = event.getHealth();              // 当前生命值
    const maxHealth = event.getMaxHealth();        // 最大生命值
    const attackDamage = event.getAttackDamage();  // 攻击力
    const armor = event.getArmor();                // 护甲值
    
    // 设置实体属性
    event.setAttackDamage(20.0);                   // 设置攻击力
    event.setMaxHealth(100.0);                     // 设置生命值
});
```

### 2. 伤害计算事件 (`adaptive_nemesis.damage_calculation`)

当计算真实伤害时触发。

```javascript
adaptive_nemesis.damage_calculation(event => {
    // 获取攻击信息
    const attacker = event.attacker;               // 攻击者
    const target = event.target;                   // 目标
    const originalDamage = event.originalDamage;   // 原始伤害
    const calculatedDamage = event.calculatedDamage; // 计算后的伤害
    const armorMultiplier = event.armorMultiplier;  // 护甲倍率
    
    // 修改伤害
    event.setCalculatedDamage(10.0);               // 设置伤害为10
    event.addDamage(5.0);                          // 增加5点伤害
    
    // 检查类型
    const isPlayerAttacker = event.isAttackerPlayer(); // 攻击者是否是玩家
    const isPlayerTarget = event.isTargetPlayer();     // 目标是否是玩家
    
    // 获取名称
    const attackerName = event.attackerName;       // 攻击者名称
    const targetName = event.targetName;           // 目标名称
    
    // 取消真实伤害转换
    event.cancelEvent();
});
```

### 3. 玩家强度评估事件 (`adaptive_nemesis.player_strength_evaluation`)

当评估玩家强度时触发。

```javascript
adaptive_nemesis.player_strength_evaluation(event => {
    // 获取玩家信息
    const player = event.player;                   // 玩家对象
    const playerName = event.playerName;           // 玩家名称
    const playerUUID = event.playerUUID;           // 玩家UUID
    
    // 获取强度信息
    const baseStrength = event.baseStrength;       // 基础强度
    const finalStrength = event.finalStrength;     // 最终强度
    const defenseStrength = event.defenseStrength; // 防御强度
    const attackStrength = event.attackStrength;   // 攻击强度
    const magicStrength = event.magicStrength;     // 魔法强度
    const combatStrength = event.combatStrength;   // 战斗强度
    
    // 修改强度
    event.setFinalStrength(100.0);                 // 设置最终强度
    event.addStrength(50.0);                       // 增加50强度
    event.multiplyStrength(1.5);                   // 乘以1.5
    
    // 玩家信息
    const hasItem = event.hasItem("minecraft:diamond_sword"); // 是否有特定物品
    const level = event.playerLevel;               // 玩家等级
    const health = event.playerHealth;             // 玩家生命值
    const maxHealth = event.playerMaxHealth;       // 玩家最大生命值
});
```

### 4. 宿敌记忆更新事件 (`adaptive_nemesis.nemesis_memory_update`)

当宿敌记忆数据更新时触发。

```javascript
adaptive_nemesis.nemesis_memory_update(event => {
    // 获取玩家信息
    const playerUUID = event.playerUUID;           // 玩家UUID
    const playerName = event.playerName;           // 玩家名称
    
    // 获取战斗统计
    const killCount = event.killCount;             // 击杀数
    const deathCount = event.deathCount;           // 死亡数
    const kdaRatio = event.kDRatio;                // KDA比率
    
    // 获取宿敌等级
    const nemesisLevel = event.nemesisLevel;       // 宿敌等级
    const isHighLevel = event.isAtLeastLevel(10);  // 是否达到10级
    const isMilestone = event.isMilestone();       // 是否是里程碑(每10杀)
    
    // 获取加成信息
    const attackBonus = event.attackBonus;         // 攻击加成
    const speedBonus = event.speedBonus;           // 速度加成
    const healthBonus = event.healthBonus;         // 生命加成
    const totalBonus = event.totalBonus;           // 总加成
});
```

## 实用示例

### 示例1: 僵尸额外强化

```javascript
// 僵尸获得额外50%强化
adaptive_nemesis.entity_scale(event => {
    if (event.entityId.includes("zombie")) {
        event.multiplyMultiplier(1.5);
        console.log(`僵尸 ${event.entityName} 获得额外50%强化`);
    }
});
```

### 示例2: Boss双倍强化

```javascript
// Boss获得双倍强化
adaptive_nemesis.entity_scale(event => {
    if (event.entityId.includes("wither") || event.entityId.includes("ender_dragon")) {
        event.multiplyMultiplier(2.0);
    }
});
```

### 示例3: 根据玩家等级调整难度

```javascript
// 高等级玩家面临更强敌人
adaptive_nemesis.player_strength_evaluation(event => {
    const level = event.playerLevel;
    if (level > 30) {
        event.addStrength(level * 0.5);
    }
});
```

### 示例4: 里程碑奖励

```javascript
// 每10杀给予玩家奖励
adaptive_nemesis.nemesis_memory_update(event => {
    if (event.isMilestone()) {
        console.log(`玩家 ${event.playerName} 达到 ${event.killCount} 击杀！`);
        // 可以在这里添加奖励逻辑
    }
});
```

### 示例5: 自定义全局难度

```javascript
// 创建自定义难度系统
const globalDifficulty = 1.5; // 1.5倍全局难度

adaptive_nemesis.entity_scale(event => {
    event.multiplyMultiplier(globalDifficulty);
});

adaptive_nemesis.player_strength_evaluation(event => {
    event.multiplyStrength(globalDifficulty);
});
```

## 注意事项

1. **性能考虑**: 事件处理函数应该尽量高效，避免复杂的计算
2. **兼容性**: 确保 KubeJS 版本与模组兼容
3. **错误处理**: 建议添加 try-catch 块来处理可能的错误
4. **调试**: 使用 `console.log()` 输出调试信息

## 技术支持

如有问题，请查看模组的 GitHub 仓库或联系开发团队。
