package com.adaptive_nemesis.adaptive_nemesismod.memory;

import java.util.HashMap;
import java.util.Map;

import com.adaptive_nemesis.adaptive_nemesismod.memory.NemesisMemorySystem.CombatStyle;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * 宿敌档案类
 *
 * 存储单个玩家的战斗历史记录和偏好分析
 * 根据玩家行为计算敌人获得的加成
 *
 * 档案支持 NBT 序列化（writeToNbt / readFromNbt），
 * 由 {@link NemesisProfileSavedData} 挂载到世界存档实现持久化（F-05）。
 *
 * @author Adaptive Nemesis Team
 * @version 1.1.0
 */
public class NemesisProfile {

    // ==================== NBT 序列化键 ====================

    /** 击杀风格计数列表键 */
    private static final String KEY_KILL_STYLES = "KillStyles";

    /** 使用武器计数列表键 */
    private static final String KEY_WEAPONS = "Weapons";

    /** 击杀敌人类型计数列表键 */
    private static final String KEY_KILLED_ENTITIES = "KilledEntities";

    /** 死亡来源计数列表键 */
    private static final String KEY_DEATH_SOURCES = "DeathSources";

    /** 击杀者类型计数列表键 */
    private static final String KEY_KILLER_TYPES = "KillerTypes";

    /** 总击杀数键 */
    private static final String KEY_TOTAL_KILLS = "TotalKills";

    /** 总死亡数键 */
    private static final String KEY_TOTAL_DEATHS = "TotalDeaths";

    /** 档案创建时间键 */
    private static final String KEY_CREATION_TIME = "CreationTime";

    /** 计数条目键名（列表元素内） */
    private static final String KEY_ENTRY_NAME = "Key";

    /** 计数条目值名（列表元素内） */
    private static final String KEY_ENTRY_COUNT = "Count";

    /** 击杀风格条目风格名（列表元素内） */
    private static final String KEY_ENTRY_STYLE = "Style";

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
     * 非 final：反序列化时需要从 NBT 恢复原创建时间
     */
    private long creationTime;

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
     * 获取当前生效的宿敌全局配置
     *
     * @return 宿敌配置数据
     */
    private NemesisConfigData getConfig() {
        return NemesisDataLoader.getInstance().getConfig();
    }

    /**
     * 获取宿敌等级
     * 基于击杀数和死亡数计算
     *
     * @return 宿敌等级
     */
    public int getNemesisLevel() {
        NemesisConfigData config = getConfig();
        // 基础等级由击杀数决定
        int baseLevel = totalKills / Math.max(1, config.getKillsPerLevel());
        // 死亡数增加额外等级（敌人学会了更多）
        int deathBonus = totalDeaths / Math.max(1, config.getDeathsPerLevel());
        return Math.min(baseLevel + deathBonus, config.getMaxLevel());
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
     * @return 抗性加成百分比
     */
    public double getMeleeResistanceBonus() {
        return getStyleResistanceBonus(CombatStyle.MELEE, getConfig().getMeleeResistanceCap());
    }

    /**
     * 获取远程抗性加成（针对远程玩家）
     *
     * @return 抗性加成百分比
     */
    public double getRangedResistanceBonus() {
        return getStyleResistanceBonus(CombatStyle.RANGED, getConfig().getRangedResistanceCap());
    }

    /**
     * 获取魔法抗性加成（针对法师玩家）
     *
     * @return 抗性加成百分比
     */
    public double getMagicResistanceBonus() {
        return getStyleResistanceBonus(CombatStyle.MAGIC, getConfig().getMagicResistanceCap());
    }

    /**
     * 计算指定战斗风格的抗性加成
     * 加成与该风格击杀占比成正比，且不超过上限
     *
     * @param style 战斗风格
     * @param cap 抗性加成上限
     * @return 抗性加成百分比
     */
    private double getStyleResistanceBonus(CombatStyle style, double cap) {
        if (totalKills == 0) {
            return 0.0;
        }
        double ratio = (double) getKillStyleCount(style) / totalKills;
        return Math.min(ratio * cap, cap);
    }

    /**
     * 获取攻击加成（基于玩家死亡记录）
     *
     * @return 攻击加成百分比
     */
    public double getAttackBonus() {
        return getAttackBonus(getConfig());
    }

    /**
     * 获取攻击加成（可注入配置版本，供测试与内部计算使用）
     *
     * @param config 宿敌全局配置
     * @return 攻击加成百分比
     */
    double getAttackBonus(NemesisConfigData config) {
        if (totalDeaths == 0) {
            return 0.0;
        }
        // 死亡越多，敌人攻击加成越高（敌人学会了玩家的弱点）
        return Math.min(totalDeaths * config.getAttackBonusPerDeath(), config.getAttackBonusCap());
    }

    /**
     * 获取速度加成（基于玩家KDA）
     *
     * @return 速度加成百分比
     */
    public double getSpeedBonus() {
        return getSpeedBonus(getConfig());
    }

    /**
     * 获取速度加成（可注入配置版本，供测试与内部计算使用）
     *
     * @param config 宿敌全局配置
     * @return 速度加成百分比
     */
    double getSpeedBonus(NemesisConfigData config) {
        double cap = config.getSpeedBonusCap();
        double kda = getKdaRatio();
        double threshold = config.getKdaSpeedThreshold();
        if (kda <= threshold) {
            return 0.0;
        }
        // KDA 高于阈值的玩家，敌人获得速度加成以应对
        return Math.min((kda - threshold) * config.getSpeedBonusPerKda(), cap);
    }

    /**
     * 获取生命加成（基于玩家总击杀数）
     *
     * @return 生命加成百分比
     */
    public double getHealthBonus() {
        return getHealthBonus(getConfig());
    }

    /**
     * 获取生命加成（可注入配置版本，供测试与内部计算使用）
     *
     * @param config 宿敌全局配置
     * @return 生命加成百分比
     */
    double getHealthBonus(NemesisConfigData config) {
        return Math.min(totalKills * config.getHealthBonusPerKill(), config.getHealthBonusCap());
    }

    /**
     * 获取宿敌总加成描述
     *
     * @return 加成描述组件（使用语言文件翻译键，支持多语言）
     */
    public Component getBonusDescription() {
        return Component.empty()
            .append(Component.translatable("adaptive_nemesis.profile.bonus.melee_resist", String.format("%.0f%%", getMeleeResistanceBonus() * 100)))
            .append(", ")
            .append(Component.translatable("adaptive_nemesis.profile.bonus.ranged_resist", String.format("%.0f%%", getRangedResistanceBonus() * 100)))
            .append(", ")
            .append(Component.translatable("adaptive_nemesis.profile.bonus.magic_resist", String.format("%.0f%%", getMagicResistanceBonus() * 100)))
            .append(", ")
            .append(Component.translatable("adaptive_nemesis.profile.bonus.attack", String.format("%.0f%%", getAttackBonus() * 100)))
            .append(", ")
            .append(Component.translatable("adaptive_nemesis.profile.bonus.speed", String.format("%.0f%%", getSpeedBonus() * 100)))
            .append(", ")
            .append(Component.translatable("adaptive_nemesis.profile.bonus.health", String.format("%.0f%%", getHealthBonus() * 100)));
    }

    // ==================== NBT 序列化（F-05） ====================

    /**
     * 将档案数据写入 NBT
     *
     * @param tag 目标 NBT 标签（会写入计数列表与统计字段）
     * @return 写入后的同一 tag 实例（便于链式调用）
     */
    public CompoundTag writeToNbt(CompoundTag tag) {
        tag.put(KEY_KILL_STYLES, writeKillStyles());
        putCountMap(tag, KEY_WEAPONS, weaponUsageCounts);
        putCountMap(tag, KEY_KILLED_ENTITIES, killedEntityCounts);
        putCountMap(tag, KEY_DEATH_SOURCES, deathSourceCounts);
        putCountMap(tag, KEY_KILLER_TYPES, killerTypeCounts);
        tag.putInt(KEY_TOTAL_KILLS, totalKills);
        tag.putInt(KEY_TOTAL_DEATHS, totalDeaths);
        tag.putLong(KEY_CREATION_TIME, creationTime);
        return tag;
    }

    /**
     * 从 NBT 恢复档案数据
     * 缺失字段使用默认值；无法识别的战斗风格条目跳过
     *
     * @param tag 来源 NBT 标签
     * @return 恢复后的档案
     */
    public static NemesisProfile readFromNbt(CompoundTag tag) {
        NemesisProfile profile = new NemesisProfile();

        ListTag styleList = tag.getList(KEY_KILL_STYLES, Tag.TAG_COMPOUND);
        for (int i = 0; i < styleList.size(); i++) {
            CompoundTag entry = styleList.getCompound(i);
            try {
                CombatStyle style = CombatStyle.valueOf(entry.getString(KEY_ENTRY_STYLE));
                profile.killStyleCounts.put(style, entry.getInt(KEY_ENTRY_COUNT));
            } catch (IllegalArgumentException e) {
                // 未知战斗风格条目跳过，不拖垮整份档案
            }
        }

        readCountMap(tag, KEY_WEAPONS, profile.weaponUsageCounts);
        readCountMap(tag, KEY_KILLED_ENTITIES, profile.killedEntityCounts);
        readCountMap(tag, KEY_DEATH_SOURCES, profile.deathSourceCounts);
        readCountMap(tag, KEY_KILLER_TYPES, profile.killerTypeCounts);

        profile.totalKills = tag.getInt(KEY_TOTAL_KILLS);
        profile.totalDeaths = tag.getInt(KEY_TOTAL_DEATHS);
        if (tag.contains(KEY_CREATION_TIME)) {
            profile.creationTime = tag.getLong(KEY_CREATION_TIME);
        }
        return profile;
    }

    /**
     * 序列化击杀风格计数（枚举键名写出）
     *
     * @return 风格计数列表（仅包含非零条目）
     */
    private ListTag writeKillStyles() {
        ListTag list = new ListTag();
        for (Map.Entry<CombatStyle, Integer> entry : killStyleCounts.entrySet()) {
            if (entry.getValue() == 0) {
                continue;
            }
            CompoundTag item = new CompoundTag();
            item.putString(KEY_ENTRY_STYLE, entry.getKey().name());
            item.putInt(KEY_ENTRY_COUNT, entry.getValue());
            list.add(item);
        }
        return list;
    }

    /**
     * 序列化字符串键计数 Map
     *
     * @param tag 目标 NBT 标签
     * @param key 列表键名
     * @param map 计数 Map
     */
    private static void putCountMap(CompoundTag tag, String key, Map<String, Integer> map) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString(KEY_ENTRY_NAME, entry.getKey());
            item.putInt(KEY_ENTRY_COUNT, entry.getValue());
            list.add(item);
        }
        tag.put(key, list);
    }

    /**
     * 从 NBT 恢复字符串键计数 Map
     *
     * @param tag 来源 NBT 标签
     * @param key 列表键名
     * @param target 恢复目标 Map
     */
    private static void readCountMap(CompoundTag tag, String key, Map<String, Integer> target) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag item = list.getCompound(i);
            target.put(item.getString(KEY_ENTRY_NAME), item.getInt(KEY_ENTRY_COUNT));
        }
    }

    /**
     * 获取Map中值最大的键
     *
     * @param map 目标Map
     * @return 值最大的键，如果Map为空返回null（由调用方决定如何显示"无"）
     */
    private String getMaxEntry(Map<String, Integer> map) {
        if (map.isEmpty()) {
            return null;
        }

        String maxKey = null;
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
            getDominantStyle().getDisplayName().getString(),
            getMostUsedWeapon()
        );
    }
}
