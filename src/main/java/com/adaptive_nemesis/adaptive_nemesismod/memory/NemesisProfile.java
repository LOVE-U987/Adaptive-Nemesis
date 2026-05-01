package com.adaptive_nemesis.adaptive_nemesismod.memory;

import java.util.HashMap;
import java.util.Map;

import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem.CombatStyle;

/**
 * 宿敌档案类
 *
 * 存储单个玩家的战斗历史记录和偏好分析
 * 根据玩家行为计算敌人获得的加成
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class NemesisProfile {

    /**
     * 击杀风格计数
     */
    private final Map<CombatStyle, Integer> killStyleCounts = new HashMap<>();

    /**
     * 使用武器类型计数
     */
    private final Map<String, Integer> weaponUsageCounts = new HashMap<>();

    /**
     * 击杀敌人类型计数
     */
    private final Map<String, Integer> killedEntityCounts = new HashMap<>();

    /**
     * 死亡来源计数
     */
    private final Map<String, Integer> deathSourceCounts = new HashMap<>();

    /**
     * 击杀者类型计数
     */
    private final Map<String, Integer> killerTypeCounts = new HashMap<>();

    /**
     * 总击杀数
     */
    private int totalKills = 0;

    /**
     * 总死亡数
     */
    private int totalDeaths = 0;

    /**
     * 档案创建时间
     */
    private final long creationTime;

    /**
     * 默认构造函数
     */
    public NemesisProfile() {
        this.creationTime = System.currentTimeMillis();

        // 初始化击杀风格计数
        for (CombatStyle style : CombatStyle.values()) {
            killStyleCounts.put(style, 0);
        }
    }

    /**
     * 记录击杀风格
     *
     * @param style 战斗风格
     */
    public void recordKillStyle(CombatStyle style) {
        killStyleCounts.merge(style, 1, Integer::sum);
        totalKills++;
    }

    /**
     * 记录使用的武器
     *
     * @param weaponType 武器类型
     */
    public void recordWeaponUsed(String weaponType) {
        weaponUsageCounts.merge(weaponType, 1, Integer::sum);
    }

    /**
     * 记录击杀的敌人类型
     *
     * @param entityType 实体类型
     */
    public void recordKilledEntity(String entityType) {
        killedEntityCounts.merge(entityType, 1, Integer::sum);
    }

    /**
     * 记录死亡来源
     *
     * @param source 伤害来源
     */
    public void recordDeathSource(String source) {
        deathSourceCounts.merge(source, 1, Integer::sum);
        totalDeaths++;
    }

    /**
     * 记录击杀者类型
     *
     * @param killerType 击杀者类型
     */
    public void recordKillerType(String killerType) {
        killerTypeCounts.merge(killerType, 1, Integer::sum);
    }

    /**
     * 获取主要战斗风格
     *
     * @return 使用次数最多的战斗风格
     */
    public CombatStyle getDominantStyle() {
        CombatStyle dominant = CombatStyle.MELEE;
        int maxCount = 0;

        for (Map.Entry<CombatStyle, Integer> entry : killStyleCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                dominant = entry.getKey();
            }
        }

        return dominant;
    }

    /**
     * 获取指定风格的击杀次数
     *
     * @param style 战斗风格
     * @return 击杀次数
     */
    public int getKillStyleCount(CombatStyle style) {
        return killStyleCounts.getOrDefault(style, 0);
    }

    /**
     * 获取最常用的武器
     *
     * @return 使用次数最多的武器类型
     */
    public String getMostUsedWeapon() {
        return getMaxEntry(weaponUsageCounts);
    }

    /**
     * 获取击杀最多的敌人类型
     *
     * @return 击杀次数最多的敌人类型
     */
    public String getMostKilledEntity() {
        return getMaxEntry(killedEntityCounts);
    }

    /**
     * 获取最常见的死亡来源
     *
     * @return 造成死亡次数最多的来源
     */
    public String getMostCommonDeathSource() {
        return getMaxEntry(deathSourceCounts);
    }

    /**
     * 获取最常见的击杀者类型
     *
     * @return 击杀玩家次数最多的实体类型
     */
    public String getMostCommonKiller() {
        return getMaxEntry(killerTypeCounts);
    }

    /**
     * 获取总击杀数
     *
     * @return 总击杀数
     */
    public int getTotalKills() {
        return totalKills;
    }

    /**
     * 获取总死亡数
     *
     * @return 总死亡数
     */
    public int getTotalDeaths() {
        return totalDeaths;
    }

    /**
     * 获取宿敌等级
     * 基于击杀数和死亡数计算
     *
     * @return 宿敌等级
     */
    public int getNemesisLevel() {
        // 基础等级由击杀数决定
        int baseLevel = totalKills / 10;
        // 死亡数增加额外等级（敌人学会了更多）
        int deathBonus = totalDeaths / 5;
        return Math.min(baseLevel + deathBonus, 50); // 最高50级
    }

    /**
     * 计算KDA比率
     *
     * @return 击杀/死亡比率
     */
    public double getKdaRatio() {
        if (totalDeaths == 0) {
            return totalKills;
        }
        return (double) totalKills / totalDeaths;
    }

    /**
     * 获取档案创建时间
     *
     * @return 创建时间戳
     */
    public long getCreationTime() {
        return creationTime;
    }

    // ==================== 宿敌加成计算 ====================

    /**
     * 获取近战抗性加成（针对近战玩家）
     *
     * @return 抗性加成百分比 (0.0 - 0.3)
     */
    public double getMeleeResistanceBonus() {
        int meleeKills = getKillStyleCount(CombatStyle.MELEE);
        if (totalKills == 0) return 0.0;
        double ratio = (double) meleeKills / totalKills;
        return Math.min(ratio * 0.3, 0.3); // 最高30%
    }

    /**
     * 获取远程抗性加成（针对远程玩家）
     *
     * @return 抗性加成百分比 (0.0 - 0.3)
     */
    public double getRangedResistanceBonus() {
        int rangedKills = getKillStyleCount(CombatStyle.RANGED);
        if (totalKills == 0) return 0.0;
        double ratio = (double) rangedKills / totalKills;
        return Math.min(ratio * 0.3, 0.3); // 最高30%
    }

    /**
     * 获取魔法抗性加成（针对法师玩家）
     *
     * @return 抗性加成百分比 (0.0 - 0.3)
     */
    public double getMagicResistanceBonus() {
        int magicKills = getKillStyleCount(CombatStyle.MAGIC);
        if (totalKills == 0) return 0.0;
        double ratio = (double) magicKills / totalKills;
        return Math.min(ratio * 0.3, 0.3); // 最高30%
    }

    /**
     * 获取攻击加成（基于玩家死亡记录）
     *
     * @return 攻击加成百分比 (0.0 - 0.25)
     */
    public double getAttackBonus() {
        if (totalDeaths == 0) return 0.0;
        // 死亡越多，敌人攻击加成越高（敌人学会了玩家的弱点）
        return Math.min(totalDeaths * 0.02, 0.25); // 最高25%
    }

    /**
     * 获取速度加成（基于玩家KDA）
     *
     * @return 速度加成百分比 (0.0 - 0.2)
     */
    public double getSpeedBonus() {
        double kda = getKdaRatio();
        if (kda > 3.0) {
            // KDA高的玩家，敌人获得速度加成以应对
            return Math.min((kda - 3.0) * 0.03, 0.2); // 最高20%
        }
        return 0.0;
    }

    /**
     * 获取生命加成（基于玩家总击杀数）
     *
     * @return 生命加成百分比 (0.0 - 0.5)
     */
    public double getHealthBonus() {
        return Math.min(totalKills * 0.01, 0.5); // 最高50%
    }

    /**
     * 获取宿敌总加成描述
     *
     * @return 加成描述字符串
     */
    public String getBonusDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("近战抗性+").append(String.format("%.0f%%", getMeleeResistanceBonus() * 100));
        sb.append(", 远程抗性+").append(String.format("%.0f%%", getRangedResistanceBonus() * 100));
        sb.append(", 魔法抗性+").append(String.format("%.0f%%", getMagicResistanceBonus() * 100));
        sb.append(", 攻击+").append(String.format("%.0f%%", getAttackBonus() * 100));
        sb.append(", 速度+").append(String.format("%.0f%%", getSpeedBonus() * 100));
        sb.append(", 生命+").append(String.format("%.0f%%", getHealthBonus() * 100));
        return sb.toString();
    }

    /**
     * 获取Map中值最大的键
     *
     * @param map 目标Map
     * @return 值最大的键，如果Map为空返回"无"
     */
    private String getMaxEntry(Map<String, Integer> map) {
        if (map.isEmpty()) {
            return "无";
        }

        String maxKey = "无";
        int maxValue = 0;

        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (entry.getValue() > maxValue) {
                maxValue = entry.getValue();
                maxKey = entry.getKey();
            }
        }

        return maxKey;
    }

    @Override
    public String toString() {
        return String.format(
            "NemesisProfile[kills=%d, deaths=%d, kda=%.2f, dominantStyle=%s, mostUsedWeapon=%s]",
            totalKills,
            totalDeaths,
            getKdaRatio(),
            getDominantStyle().getDisplayName(),
            getMostUsedWeapon()
        );
    }
}
