package com.adaptive_nemesis.adaptive_nemesismod.invasion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 数据包入侵配置数据类
 *
 * 保存从数据包 JSON 解析出的入侵策略信息，
 * 包括波次、敌人类型、数量、难度倍率等。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class InvasionData {

    /**
     * 入侵类型标识符
     */
    private final ResourceLocation id;

    /**
     * 显示名称翻译键（可选）
     */
    private final String nameKey;

    /**
     * 入侵总波次数
     */
    private final int maxWaves;

    /**
     * 生成距离玩家多远的位置
     */
    private final int spawnDistance;

    /**
     * 每个波次的配置
     */
    private final List<WaveData> waves;

    /**
     * 入侵结束奖励
     */
    private final InvasionRewardData rewards;

    /**
     * 默认构造函数
     *
     * @param id 入侵类型标识符
     */
    public InvasionData(ResourceLocation id) {
        this(id, null, 6, 60, new ArrayList<>(), new InvasionRewardData());
    }

    /**
     * 完整构造函数
     *
     * @param id 入侵类型标识符
     * @param nameKey 显示名称翻译键
     * @param maxWaves 总波次数
     * @param spawnDistance 生成距离
     * @param waves 波次配置列表
     * @param rewards 奖励配置
     */
    public InvasionData(ResourceLocation id, String nameKey, int maxWaves,
                        int spawnDistance, List<WaveData> waves, InvasionRewardData rewards) {
        this.id = id;
        this.nameKey = nameKey;
        this.maxWaves = Math.max(1, maxWaves);
        this.spawnDistance = Math.max(20, spawnDistance);
        this.waves = waves != null ? new ArrayList<>(waves) : new ArrayList<>();
        this.rewards = rewards != null ? rewards : new InvasionRewardData();
    }

    /**
     * 获取入侵标识符
     *
     * @return 资源位置
     */
    public ResourceLocation getId() {
        return id;
    }

    /**
     * 获取显示名称翻译键
     *
     * @return 翻译键，可能为 null
     */
    public String getNameKey() {
        return nameKey;
    }

    /**
     * 获取总波次数
     *
     * @return 总波次数
     */
    public int getMaxWaves() {
        return maxWaves;
    }

    /**
     * 获取生成距离
     *
     * @return 生成距离
     */
    public int getSpawnDistance() {
        return spawnDistance;
    }

    /**
     * 获取所有波次配置
     *
     * @return 不可修改的波次列表
     */
    public List<WaveData> getWaves() {
        return Collections.unmodifiableList(waves);
    }

    /**
     * 获取入侵结束奖励
     *
     * @return 奖励配置
     */
    public InvasionRewardData getRewards() {
        return rewards;
    }

    /**
     * 获取指定波次的配置
     *
     * @param waveNumber 波次编号（从1开始）
     * @return 波次配置，不存在时返回 null
     */
    public WaveData getWave(int waveNumber) {
        for (WaveData wave : waves) {
            if (wave.getWaveNumber() == waveNumber) {
                return wave;
            }
        }
        return null;
    }

    /**
     * 单个波次配置
     */
    public static class WaveData {

        private final int waveNumber;
        private final double difficultyMultiplier;
        private final List<EnemyData> enemies;

        /**
         * 构造函数
         *
         * @param waveNumber 波次编号
         * @param difficultyMultiplier 难度倍率
         * @param enemies 敌人列表
         */
        public WaveData(int waveNumber, double difficultyMultiplier, List<EnemyData> enemies) {
            this.waveNumber = Math.max(1, waveNumber);
            this.difficultyMultiplier = Math.max(1.0, difficultyMultiplier);
            this.enemies = enemies != null ? new ArrayList<>(enemies) : new ArrayList<>();
        }

        public int getWaveNumber() {
            return waveNumber;
        }

        public double getDifficultyMultiplier() {
            return difficultyMultiplier;
        }

        public List<EnemyData> getEnemies() {
            return Collections.unmodifiableList(enemies);
        }
    }

    /**
     * 单个敌人配置
     */
    public static class EnemyData {

        private final EntityType<?> entityType;
        private final int count;
        private final int weight;
        private final boolean isBoss;
        private final ResourceLocation equipmentLootTable;
        private final List<MobEffectInstance> effects;
        private final boolean glowing;
        private final boolean frostWalker;
        private final String spawnDirection;
        private final String customNameKey;
        private final double healthMultiplier;
        private final double damageMultiplier;

        /**
         * 构造函数
         *
         * @param entityType 实体类型
         * @param count 数量
         * @param weight 权重
         * @param isBoss 是否为 BOSS
         * @param equipmentLootTable 装备战利品表
         * @param effects 药水效果列表
         * @param glowing 是否发光
         * @param frostWalker 是否冰霜行者
         * @param spawnDirection 出生方向
         * @param customNameKey 自定义名称翻译键
         * @param healthMultiplier 血量倍率
         * @param damageMultiplier 攻击倍率
         */
        public EnemyData(EntityType<?> entityType, int count, int weight,
                         boolean isBoss, ResourceLocation equipmentLootTable,
                         List<MobEffectInstance> effects, boolean glowing, boolean frostWalker,
                         String spawnDirection, String customNameKey,
                         double healthMultiplier, double damageMultiplier) {
            this.entityType = entityType;
            this.count = Math.max(1, count);
            this.weight = Math.max(1, weight);
            this.isBoss = isBoss;
            this.equipmentLootTable = equipmentLootTable;
            this.effects = effects != null ? new ArrayList<>(effects) : new ArrayList<>();
            this.glowing = glowing;
            this.frostWalker = frostWalker;
            this.spawnDirection = spawnDirection;
            this.customNameKey = customNameKey;
            this.healthMultiplier = Math.max(0.1, healthMultiplier);
            this.damageMultiplier = Math.max(0.1, damageMultiplier);
        }

        public EntityType<?> getEntityType() {
            return entityType;
        }

        public int getCount() {
            return count;
        }

        public int getWeight() {
            return weight;
        }

        public boolean isBoss() {
            return isBoss;
        }

        public ResourceLocation getEquipmentLootTable() {
            return equipmentLootTable;
        }

        public List<MobEffectInstance> getEffects() {
            return Collections.unmodifiableList(effects);
        }

        public boolean isGlowing() {
            return glowing;
        }

        public boolean isFrostWalker() {
            return frostWalker;
        }

        public String getSpawnDirection() {
            return spawnDirection;
        }

        public String getCustomNameKey() {
            return customNameKey;
        }

        public double getHealthMultiplier() {
            return healthMultiplier;
        }

        public double getDamageMultiplier() {
            return damageMultiplier;
        }
    }
}
