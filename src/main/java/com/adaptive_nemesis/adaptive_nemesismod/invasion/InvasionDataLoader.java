package com.adaptive_nemesis.adaptive_nemesismod.invasion;

import com.adaptive_nemesis.adaptive_nemesismod.AdaptiveNemesisMod;
import com.adaptive_nemesis.adaptive_nemesismod.Config;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 入侵配置数据包加载器
 *
 * 从数据包路径 {@code data/<namespace>/invasions/<name>.json} 读取入侵策略配置。
 * 当数据包重载时自动刷新，支持多数据包覆盖与合并。
 *
 * @author Adaptive Nemesis Team
 * @version 1.0.0
 */
public class InvasionDataLoader extends SimpleJsonResourceReloadListener {

    /**
     * 数据包资源目录名
     */
    public static final String DIRECTORY = "invasions";

    /**
     * JSON 解析器
     */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * 已加载的入侵配置映射
     */
    private Map<ResourceLocation, InvasionData> invasions = ImmutableMap.of();

    /**
     * 单例实例
     */
    private static InvasionDataLoader INSTANCE;

    /**
     * 私有构造函数
     */
    private InvasionDataLoader() {
        super(GSON, DIRECTORY);
    }

    /**
     * 获取单例实例
     *
     * @return 加载器实例
     */
    public static synchronized InvasionDataLoader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new InvasionDataLoader();
        }
        return INSTANCE;
    }

    /**
     * 根据标识符获取入侵配置
     *
     * @param id 入侵配置标识符
     * @return 入侵配置，不存在时返回 null
     */
    public InvasionData getInvasion(ResourceLocation id) {
        return invasions.get(id);
    }

    /**
     * 获取所有已加载的入侵配置
     *
     * @return 不可修改的入侵配置映射
     */
    public Map<ResourceLocation, InvasionData> getAllInvasions() {
        return invasions;
    }

    /**
     * 资源重载回调
     *
     * @param object 解析后的 JSON 对象映射
     * @param resourceManager 资源管理器
     * @param profiler 性能分析器
     */
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, InvasionData> loaded = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                InvasionData data = parseInvasion(id, entry.getValue().getAsJsonObject());
                if (data != null) {
                    loaded.put(id, data);
                    AdaptiveNemesisMod.LOGGER.debug("已加载入侵配置: {}", id);
                }
            } catch (Exception e) {
                AdaptiveNemesisMod.LOGGER.error("解析入侵配置 {} 失败: {}", id, e.getMessage());
                if (Config.ENABLE_DEBUG_LOG.get()) {
                    AdaptiveNemesisMod.LOGGER.error("异常堆栈:", e);
                }
            }
        }

        this.invasions = ImmutableMap.copyOf(loaded);
        AdaptiveNemesisMod.LOGGER.info("已加载 {} 个入侵数据包配置", loaded.size());
    }

    /**
     * 解析单个入侵配置 JSON
     *
     * @param id 配置标识符
     * @param json JSON 对象
     * @return 解析后的入侵配置
     */
    private InvasionData parseInvasion(ResourceLocation id, JsonObject json) {
        String nameKey = json.has("name") ? json.get("name").getAsString() : null;
        int maxWaves = json.has("max_waves") ? json.get("max_waves").getAsInt() : 6;
        int spawnDistance = json.has("spawn_distance") ? json.get("spawn_distance").getAsInt() : 60;

        List<InvasionData.WaveData> waves = new ArrayList<>();
        if (json.has("waves") && json.get("waves").isJsonArray()) {
            JsonArray waveArray = json.getAsJsonArray("waves");
            for (JsonElement element : waveArray) {
                if (element.isJsonObject()) {
                    InvasionData.WaveData wave = parseWave(element.getAsJsonObject());
                    if (wave != null) {
                        waves.add(wave);
                    }
                }
            }
        }

        if (waves.isEmpty()) {
            AdaptiveNemesisMod.LOGGER.warn("入侵配置 {} 没有定义任何波次，将使用硬编码配置", id);
        }

        InvasionRewardData rewards = parseRewards(json);

        return new InvasionData(id, nameKey, maxWaves, spawnDistance, waves, rewards);
    }

    /**
     * 解析奖励配置
     *
     * @param json JSON 对象
     * @return 奖励配置
     */
    private InvasionRewardData parseRewards(JsonObject json) {
        InvasionRewardData rewards = new InvasionRewardData();

        if (!json.has("rewards") || !json.get("rewards").isJsonObject()) {
            return rewards;
        }

        JsonObject rewardsJson = json.getAsJsonObject("rewards");

        // 战利品表
        if (rewardsJson.has("loot_tables") && rewardsJson.get("loot_tables").isJsonArray()) {
            JsonArray lootArray = rewardsJson.getAsJsonArray("loot_tables");
            for (JsonElement element : lootArray) {
                ResourceLocation lootTable = ResourceLocation.tryParse(element.getAsString());
                if (lootTable != null) {
                    rewards.addLootTable(lootTable);
                }
            }
        }

        // 单一战利品表（兼容简写）
        if (rewardsJson.has("loot_table")) {
            ResourceLocation lootTable = ResourceLocation.tryParse(rewardsJson.get("loot_table").getAsString());
            if (lootTable != null) {
                rewards.addLootTable(lootTable);
            }
        }

        // 经验值
        if (rewardsJson.has("experience")) {
            rewards.setExperience(rewardsJson.get("experience").getAsInt());
        }

        // 药水效果
        if (rewardsJson.has("effects") && rewardsJson.get("effects").isJsonArray()) {
            JsonArray effectArray = rewardsJson.getAsJsonArray("effects");
            for (JsonElement element : effectArray) {
                if (element.isJsonObject()) {
                    JsonObject effectObj = element.getAsJsonObject();
                    String effectId = effectObj.has("id") ? effectObj.get("id").getAsString() : null;
                    int duration = effectObj.has("duration") ? effectObj.get("duration").getAsInt() : 30;
                    int amplifier = effectObj.has("amplifier") ? effectObj.get("amplifier").getAsInt() : 0;
                    MobEffectInstance effect = InvasionRewardData.parseEffect(effectId, duration, amplifier);
                    if (effect != null) {
                        rewards.addEffect(effect.getEffect().value(), effect.getDuration(), effect.getAmplifier());
                    }
                }
            }
        }

        return rewards;
    }

    /**
     * 解析单个波次配置
     *
     * @param json JSON 对象
     * @return 解析后的波次配置
     */
    private InvasionData.WaveData parseWave(JsonObject json) {
        int waveNumber = json.has("wave") ? json.get("wave").getAsInt() : 1;
        double difficultyMultiplier = json.has("difficulty_multiplier")
            ? json.get("difficulty_multiplier").getAsDouble() : 1.0;

        List<InvasionData.EnemyData> enemies = new ArrayList<>();
        if (json.has("enemies") && json.get("enemies").isJsonArray()) {
            JsonArray enemyArray = json.getAsJsonArray("enemies");
            for (JsonElement element : enemyArray) {
                if (element.isJsonObject()) {
                    InvasionData.EnemyData enemy = parseEnemy(element.getAsJsonObject());
                    if (enemy != null) {
                        enemies.add(enemy);
                    }
                }
            }
        }

        if (enemies.isEmpty()) {
            throw new JsonParseException("波次 " + waveNumber + " 没有定义任何敌人");
        }

        return new InvasionData.WaveData(waveNumber, difficultyMultiplier, enemies);
    }

    /**
     * 解析单个敌人配置
     *
     * @param json JSON 对象
     * @return 解析后的敌人配置
     */
    private InvasionData.EnemyData parseEnemy(JsonObject json) {
        if (!json.has("entity_type")) {
            throw new JsonParseException("敌人配置缺少 entity_type 字段");
        }

        ResourceLocation entityId = ResourceLocation.tryParse(json.get("entity_type").getAsString());
        if (entityId == null) {
            throw new JsonParseException("无效的 entity_type: " + json.get("entity_type").getAsString());
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(entityId);
        if (entityType == null || entityType == EntityType.PIG) {
            // 未注册的实体类型默认返回 PIG，需要额外判断
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entityId)) {
                throw new JsonParseException("未知的实体类型: " + entityId);
            }
        }

        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        int weight = json.has("weight") ? json.get("weight").getAsInt() : 1;
        boolean isBoss = json.has("is_boss") && json.get("is_boss").getAsBoolean();
        boolean glowing = json.has("glowing") && json.get("glowing").getAsBoolean();
        boolean frostWalker = json.has("frost_walker") && json.get("frost_walker").getAsBoolean();
        String spawnDirection = json.has("spawn_direction") ? json.get("spawn_direction").getAsString() : null;
        String customNameKey = json.has("custom_name") ? json.get("custom_name").getAsString() : null;
        double healthMultiplier = json.has("health_multiplier") ? json.get("health_multiplier").getAsDouble() : 1.0;
        double damageMultiplier = json.has("damage_multiplier") ? json.get("damage_multiplier").getAsDouble() : 1.0;

        ResourceLocation equipmentLootTable = null;
        if (json.has("equipment_loot_table")) {
            equipmentLootTable = ResourceLocation.tryParse(json.get("equipment_loot_table").getAsString());
        }

        List<MobEffectInstance> effects = new ArrayList<>();
        if (json.has("effects") && json.get("effects").isJsonArray()) {
            JsonArray effectArray = json.getAsJsonArray("effects");
            for (JsonElement element : effectArray) {
                if (element.isJsonObject()) {
                    JsonObject effectObj = element.getAsJsonObject();
                    String effectId = effectObj.has("id") ? effectObj.get("id").getAsString() : null;
                    int duration = effectObj.has("duration") ? effectObj.get("duration").getAsInt() : 30;
                    int amplifier = effectObj.has("amplifier") ? effectObj.get("amplifier").getAsInt() : 0;
                    MobEffectInstance effect = InvasionRewardData.parseEffect(effectId, duration, amplifier);
                    if (effect != null) {
                        effects.add(effect);
                    }
                }
            }
        }

        return new InvasionData.EnemyData(entityType, count, weight, isBoss, equipmentLootTable,
            effects, glowing, frostWalker, spawnDirection, customNameKey,
            healthMultiplier, damageMultiplier);
    }
}
