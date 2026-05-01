package com.adaptive_nemesis.adaptive_nemesismod.player;

/**
 * 玩家强度数据类
 * 
 * 存储玩家各维度的强度评估结果
 * 
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class PlayerStrengthData {
    
    /**
     * 综合强度值
     */
    private final double totalStrength;
    
    /**
     * 防御能力强度
     */
    private final double defenseStrength;
    
    /**
     * 输出能力强度
     */
    private final double damageStrength;
    
    /**
     * 神话词条强度
     */
    private final double apotheosisStrength;
    
    /**
     * 铁魔法强度
     */
    private final double ironsSpellsStrength;
    
    /**
     * 史诗战斗强度
     */
    private final double epicFightStrength;
    
    /**
     * 数据创建时间戳
     */
    private final long timestamp;

    /**
     * 构造函数
     * 
     * @param totalStrength 综合强度值
     * @param defenseStrength 防御能力强度
     * @param damageStrength 输出能力强度
     * @param apotheosisStrength 神话词条强度
     * @param ironsSpellsStrength 铁魔法强度
     * @param epicFightStrength 史诗战斗强度
     */
    public PlayerStrengthData(double totalStrength, double defenseStrength, 
                              double damageStrength, double apotheosisStrength,
                              double ironsSpellsStrength, double epicFightStrength) {
        this.totalStrength = totalStrength;
        this.defenseStrength = defenseStrength;
        this.damageStrength = damageStrength;
        this.apotheosisStrength = apotheosisStrength;
        this.ironsSpellsStrength = ironsSpellsStrength;
        this.epicFightStrength = epicFightStrength;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * 获取综合强度值
     * 
     * @return 综合强度值
     */
    public double getTotalStrength() {
        return totalStrength;
    }

    /**
     * 获取防御能力强度
     * 
     * @return 防御能力强度
     */
    public double getDefenseStrength() {
        return defenseStrength;
    }

    /**
     * 获取输出能力强度
     * 
     * @return 输出能力强度
     */
    public double getDamageStrength() {
        return damageStrength;
    }

    /**
     * 获取神话词条强度
     * 
     * @return 神话词条强度
     */
    public double getApotheosisStrength() {
        return apotheosisStrength;
    }

    /**
     * 获取铁魔法强度
     * 
     * @return 铁魔法强度
     */
    public double getIronsSpellsStrength() {
        return ironsSpellsStrength;
    }

    /**
     * 获取史诗战斗强度
     * 
     * @return 史诗战斗强度
     */
    public double getEpicFightStrength() {
        return epicFightStrength;
    }

    /**
     * 获取数据创建时间戳
     * 
     * @return 时间戳（毫秒）
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 检查数据是否过期
     * 
     * @param maxAgeMs 最大有效时间（毫秒）
     * @return 如果已过期返回true
     */
    public boolean isExpired(long maxAgeMs) {
        return System.currentTimeMillis() - timestamp > maxAgeMs;
    }

    @Override
    public String toString() {
        return String.format(
            "PlayerStrengthData[total=%.2f, defense=%.2f, damage=%.2f, apotheosis=%.2f, ironsSpells=%.2f, epicFight=%.2f]",
            totalStrength, defenseStrength, damageStrength, 
            apotheosisStrength, ironsSpellsStrength, epicFightStrength
        );
    }
}
